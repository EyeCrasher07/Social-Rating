#!/usr/bin/env python3
"""Check Social Rating with a real Minecraft LAN host and a second Minecraft client."""
import argparse
import json
import os
from pathlib import Path
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "fabric" / "26.3"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--display", choices=("offscreen", "desktop"), default="offscreen")
    args = parser.parse_args()
    shared = PROJECT / "run" / "smoke-pair"
    shared.mkdir(parents=True, exist_ok=True)
    for name in ("host-ready.txt", "guest-verified.txt", "guest-repost-ready.txt", "guest-repost-saved.txt", "guest-withdraw-ready.txt", "guest-withdraw-saved.txt", "host-result.txt", "done.txt"):
        (shared / name).unlink(missing_ok=True)
    for role in ("host", "guest"):
        game = shared / role
        game.mkdir(exist_ok=True)
        (game / "options.txt").write_text("lang:ru_ru\nguiScale:2\npauseOnLostFocus:false\nsoundCategory_master:0.0\nrenderDistance:4\nsimulationDistance:5\ntutorialStep:none\n", encoding="utf-8")
        mod = game / "config" / "socialrating"
        mod.mkdir(parents=True, exist_ok=True)
        (mod / "client.json").write_text('{"showNameplates":true,"showInteractionHint":true,"nameplateDistance":32,"language":"auto"}\n')
        if role == "host":
            (mod / "database.json").unlink(missing_ok=True)
            (mod / "server.json").write_text('{"schemaVersion":1,"defaultLanguage":"en_us"}\n')
            (mod / "lang").mkdir(exist_ok=True)
            for language in ("en_us", "ru_ru"):
                source = PROJECT / "src/main/resources/assets/socialrating/lang" / f"{language}.json"
                (mod / "lang" / f"{language}.json").write_text(source.read_text())
    with socket.socket() as candidate:
        candidate.bind(("127.0.0.1", 0))
        port = candidate.getsockname()[1]
    env = dict(os.environ)
    if args.display == "offscreen":
        env["SDL_VIDEODRIVER"] = "offscreen"
        env["LIBGL_ALWAYS_SOFTWARE"] = "1"
    command = [str(PROJECT / "gradlew"), "--offline", "-PsmokeTest", f"-PsmokePort={port}"]
    subprocess.run(command + ["smokeClasses"], cwd=PROJECT, env=env, check=True)
    processes, logs = [], []
    try:
        for role, task in (("host", "runHostSmoke"), ("guest", "runGuestSmoke")):
            if role == "guest":
                deadline = time.monotonic() + 100
                while not (shared / "host-ready.txt").exists():
                    if processes[0].poll() is not None or time.monotonic() > deadline:
                        raise RuntimeError("Minecraft host did not become ready")
                    time.sleep(0.25)
            log = (shared / f"{role}.log").open("w")
            logs.append(log)
            processes.append(subprocess.Popen(command + [task], cwd=PROJECT, env=env, stdout=log, stderr=subprocess.STDOUT))
            print(f"Started {role}; log: {shared / (role + '.log')}", flush=True)
        for process in processes:
            if process.wait(timeout=260):
                raise RuntimeError("Minecraft verification failed")
        result = shared / "host-result.txt"
        if not result.exists() or not (shared / "guest-verified.txt").exists():
            raise RuntimeError("A client did not complete verification")
        database = Path(result.read_text().splitlines()[1])
        data = json.loads(database.read_text())
        assert data["schema"] == 2 and len(data["reviews"]) == 8 and len(data["reports"]) == 2
        assert {report["status"] for report in data["reports"]} == {"kept", "removed"}
        assert len(data["removed"]) == 2
        assert (shared / "guest-withdraw-saved.txt").exists(), "Withdrawal must work through the client UI"
        assert {report["decisionReason"] for report in data["reports"]} == {"Оскорбление в отзыве.", "Проверено: отзыв без нарушений."}
        assert (shared / "guest-repost-saved.txt").exists(), "A deleted review must not prevent a new submission"
        assert database.resolve() == (shared / "host/config/socialrating/database.json").resolve()
        assert not (shared / "guest/config/socialrating/database.json").exists(), "Guest must not create a ratings database"
        print(result.read_text())
        print((shared / "guest-verified.txt").read_text())
        return 0
    except Exception as error:
        print(error)
        for role in ("host", "guest"):
            log = shared / f"{role}.log"
            if log.exists():
                print(f"--- {role} ---\n" + "\n".join(log.read_text(errors="replace").splitlines()[-60:]))
        return 1
    finally:
        for process in processes:
            if process.poll() is None:
                process.terminate()
        for log in logs:
            log.close()


if __name__ == "__main__":
    raise SystemExit(main())
