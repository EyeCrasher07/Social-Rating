package dev.eyecrasher.socialrating;

import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Client-side language overlay; removed on disconnect and restored after resource reloads. */
public final class RatingLanguage {
    private Map<String, String> values = dev.eyecrasher.socialrating.JavaCompat.map();
    private Overlay installed;

    public void apply(RatingData.Translations translations) {
        values = translations.values().entrySet().stream().filter(entry -> ServerFiles.allowedKey(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        install();
    }

    public void tick() {
        if (!values.isEmpty() && Language.getInstance() != installed) install();
    }

    public void clear() {
        values = dev.eyecrasher.socialrating.JavaCompat.map();
        if (installed != null) {
            installed.values = dev.eyecrasher.socialrating.JavaCompat.map();
            if (Language.getInstance() == installed) Language.inject(installed.delegate);
            installed = null;
        }
    }

    private void install() {
        Language delegate = Language.getInstance();
        if (delegate == installed) delegate = installed.delegate;
        if (installed != null) installed.values = dev.eyecrasher.socialrating.JavaCompat.map();
        installed = new Overlay(delegate, values);
        Language.inject(installed);
    }

    private static final class Overlay extends Language {
        private final Language delegate;
        private volatile Map<String, String> values;
        Overlay(Language delegate, Map<String, String> values) { this.delegate = delegate; this.values = values; }
        @Override public String getOrDefault(String key) { return values.getOrDefault(key, delegate.getOrDefault(key)); }
        @Override public boolean has(String key) { return values.containsKey(key) || delegate.has(key); }
        @Override public boolean isDefaultRightToLeft() { return delegate.isDefaultRightToLeft(); }
        @Override public FormattedCharSequence getVisualOrder(FormattedText text) { return delegate.getVisualOrder(text); }
    }
}
