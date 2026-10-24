#!/usr/bin/env python3
"""Build standalone projects and collect verified production JARs locally."""

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from zipfile import BadZipFile, ZipFile

ROOT = Path(__file__).resolve().parents[1]
LEDGER = ROOT / 'build/reports/ports/artifacts.json'


def source_digest(target):
    project = ROOT / target['loader'] / target['minecraft']
    files = [project / name for name in ['build.gradle', 'settings.gradle', 'gradle.properties',
                                        'gradlew', 'gradlew.bat', 'LICENSE']]
    for directory in ['src', 'gradle', 'licenses']:
        files.extend(path for path in (project / directory).rglob('*') if path.is_file())
    digest = hashlib.sha256()
    for path in sorted(files):
        digest.update(path.relative_to(project).as_posix().encode())
        digest.update(b'\0')
        digest.update(path.read_bytes())
        digest.update(b'\0')
    return digest.hexdigest()


def verify(target, mod_version):
    artifact = ROOT / target['artifact']
    if not artifact.is_file():
        raise ValueError('Missing production JAR: ' + str(artifact))
    with ZipFile(artifact) as jar:
        names = jar.namelist()
        if any('RatingSmoke' in name or 'RatingProbe' in name for name in names):
            raise ValueError('Test driver included in ' + artifact.name)
        for name in ['dev/eyecrasher/socialrating/RatingStore.class',
                     'dev/eyecrasher/socialrating/ui/NearbyScreen.class',
                     'assets/socialrating/lang/en_us.json',
                     'assets/socialrating/lang/ru_ru.json']:
            if name not in names:
                raise ValueError('Missing ' + name + ' in ' + artifact.name)
        expected_icon = ROOT / 'fabric/26.3/src/main/resources/assets/socialrating/icon.png'
        if jar.read('assets/socialrating/icon.png') != expected_icon.read_bytes():
            raise ValueError('Incorrect icon in ' + artifact.name)
        if not any(name.startswith('LICENSE') for name in names):
            raise ValueError('Missing license in ' + artifact.name)
        minecraft = tuple(map(int, target['minecraft'].split('.')))
        mixins = json.loads(jar.read('socialrating.mixins.json'))
        if ((target['loader'] == 'forge' and minecraft <= (1, 20, 4))
                or (target['loader'] == 'neoforge' and minecraft == (1, 20, 1))):
            refmap = mixins.get('refmap')
            if refmap != 'socialrating.refmap.json' or not json.loads(jar.read(refmap)).get('mappings'):
                raise ValueError('Missing legacy Forge mixin mappings in ' + artifact.name)
            if 'main.refmap.json' in names:
                raise ValueError('Unscoped mixin mappings in ' + artifact.name)
        for name in names:
            if name.endswith('.class'):
                data = jar.read(name)
                if int.from_bytes(data[6:8], 'big') > target['java'] + 44:
                    raise ValueError('Bytecode requires a newer Java: ' + artifact.name)
        if target['loader'] == 'fabric':
            metadata = json.loads(jar.read('fabric.mod.json'))
            if metadata['id'] != 'socialrating' or metadata['version'] != mod_version:
                raise ValueError('Incorrect mod identity in ' + artifact.name)
        else:
            metadata_name = ('META-INF/mods.toml' if target['loader'] == 'forge'
                             or tuple(map(int, target['minecraft'].split('.'))) <= (1, 20, 4)
                             else 'META-INF/neoforge.mods.toml')
            metadata = jar.read(metadata_name).decode()
            if 'modId="socialrating"' not in metadata or 'socialrating_smoke' in metadata or 'socialrating_probe' in metadata:
                raise ValueError('Incorrect mod identity in ' + artifact.name)
            version = re.search(r'(?m)^version\s*=\s*"([^"]+)"', metadata)
            if not version or version.group(1) != mod_version:
                raise ValueError('Incorrect mod version in ' + artifact.name)
    return hashlib.sha256(artifact.read_bytes()).hexdigest()


def build(target, args):
    project = ROOT / target['loader'] / target['minecraft']
    wrapper = (project / 'gradle/wrapper/gradle-wrapper.properties').read_text()
    java_home = args.jdk_21 if 'gradle-8.' in wrapper else args.jdk_25
    environment = dict(os.environ)
    if java_home:
        environment['JAVA_HOME'] = str(Path(java_home).expanduser().resolve())
    command = [str(project / ('gradlew.bat' if os.name == 'nt' else 'gradlew')),
               '--console', 'plain', '--max-workers=2']
    if args.offline:
        command.append('--offline')
    paths = [str(Path(path).expanduser().resolve())
             for path in [args.jdk_21, args.jdk_25, *args.toolchain] if path]
    if paths:
        command.append('-Porg.gradle.java.installations.paths=' + ','.join(paths))
    command.append('build')
    logs = ROOT / 'build/reports/ports'
    logs.mkdir(parents=True, exist_ok=True)
    log = logs / (target['id'] + '.log')
    with log.open('w', encoding='utf-8') as output:
        result = subprocess.run(command, cwd=project, env=environment,
                                stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        raise ValueError('Build failed; see ' + str(log))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', action='append', help='Target id, e.g. fabric-26.3; repeat to select several')
    parser.add_argument('--list', action='store_true', help='List available target ids without building')
    parser.add_argument('--collect-only', action='store_true', help='Verify and collect existing JARs without running Gradle')
    parser.add_argument('--changed', action='store_true', help='Build only targets whose sources or verified JAR changed')
    parser.add_argument('--offline', action='store_true', help='Use cached Gradle dependencies only')
    parser.add_argument('--jobs', type=int, default=2, help='Maximum parallel builds (default: 2)')
    parser.add_argument('--jdk-21', help='JDK home for Gradle 8 projects')
    parser.add_argument('--jdk-25', help='JDK home for Gradle 9 projects')
    parser.add_argument('--toolchain', action='append', default=[], help='Additional JDK home; repeat for each installed toolchain')
    parser.add_argument('--output', type=Path, help='Destination directory (default: dist/social-rating-VERSION)')
    args = parser.parse_args()
    manifest = json.loads((ROOT / 'versions.json').read_text())
    available = {target['id'] for target in manifest['targets']}
    if args.target and set(args.target) - available:
        parser.error('Unknown target(s): ' + ', '.join(sorted(set(args.target) - available)))
    if args.jobs < 1:
        parser.error('--jobs must be at least 1')
    if args.changed and args.collect_only:
        parser.error('--changed and --collect-only cannot be combined')
    targets = [target for target in manifest['targets'] if not args.target or target['id'] in args.target]
    if args.list:
        print('\n'.join(target['id'] for target in targets))
        return 0
    records = json.loads(LEDGER.read_text()) if LEDGER.exists() else {}
    verified = {}
    failures = []

    def process(target):
        sources = source_digest(target)
        previous = records.get(target['id'], {})
        reused = False
        if args.changed and sources == previous.get('source_sha256'):
            try:
                reused = verify(target, manifest['mod_version']) == previous.get('sha256')
            except (OSError, ValueError, KeyError, BadZipFile):
                pass
        if not args.collect_only and not reused:
            build(target, args)
        digest = verify(target, manifest['mod_version'])
        if source_digest(target) != sources:
            raise ValueError('Sources changed during the build; rebuild this target')
        if args.collect_only:
            if digest != previous.get('sha256') or sources != previous.get('source_sha256'):
                raise ValueError('JAR or sources differ from the last verified build; rebuild this target first')
        return dict(artifact=target['artifact'], sha256=digest, source_sha256=sources), reused

    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        pending = {pool.submit(process, target): target for target in targets}
        for future in as_completed(pending):
            target = pending[future]
            try:
                verified[target['id']], reused = future.result()
                print('PASS ' + target['id'] + (' (unchanged)' if reused else ''), flush=True)
            except Exception as error:
                failures.append(target['id'])
                print('FAIL ' + target['id'] + ': ' + str(error), file=sys.stderr, flush=True)
    if not args.collect_only:
        records.update(verified)
        LEDGER.parent.mkdir(parents=True, exist_ok=True)
        LEDGER.write_text(json.dumps(records, indent=2) + '\n')
    if failures:
        return 1
    destination = (args.output or ROOT / 'dist' / ('social-rating-' + manifest['mod_version'])).resolve()
    destination.mkdir(parents=True, exist_ok=True)
    collected = []
    checksums = []
    for target in targets:
        relative = Path(target['loader']) / target['minecraft'] / Path(target['artifact']).name
        output = destination / relative
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / target['artifact'], output)
        checksums.append(verified[target['id']]['sha256'] + '  ' + relative.as_posix())
        collected.append(dict(target, **dict(verified[target['id']], artifact=relative.as_posix())))
    (destination / 'SHA256SUMS').write_text('\n'.join(checksums) + '\n')
    (destination / 'manifest.json').write_text(json.dumps(dict(manifest, targets=collected), indent=2) + '\n')
    shutil.copyfile(ROOT / 'LICENSE', destination / 'LICENSE')
    print(str(len(targets)) + ' verified JARs collected in ' + str(destination))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
