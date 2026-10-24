package dev.eyecrasher.socialrating;

import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

public final class RatingLanguageTest {
    public static void main(String[] args) {
        Language original = Language.getInstance();
        var overlay = new RatingLanguage();
        try {
            var base = new Base("base"); Language.inject(base);
            Component cached = Component.translatable("socialrating.saved");
            check(cached.getString().equals("base"), "Base language");
            overlay.apply(new RatingData.Translations("ru_ru", Map.of("socialrating.saved", "Первый перевод", "menu.quit", "Unexpected")));
            check(cached.getString().equals("Первый перевод"), "Already-created components must refresh when translations arrive");
            check(Language.getInstance().getOrDefault("menu.quit").equals("base"), "Server files must not override vanilla text");
            overlay.apply(new RatingData.Translations("ru_ru", Map.of("socialrating.saved", "После reload")));
            check(cached.getString().equals("После reload"), "Reload must invalidate translated-component caches");
            Language.inject(new Base("new resources")); overlay.tick();
            check(cached.getString().equals("После reload"), "Server translations must survive a client resource reload");
            check(Language.getInstance().getOrDefault("menu.quit").equals("new resources"), "Resource reload must update unmodified text");
            overlay.clear();
            check(cached.getString().equals("new resources"), "Disconnect must restore local translations");
            System.out.println("PASS: 7 client language overlay checks");
        } finally { overlay.clear(); Language.inject(original); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class Base extends Language {
        private final String value;
        Base(String value) { this.value = value; }
        @Override public String getOrDefault(String key, String fallback) { return value; }
        @Override public boolean has(String key) { return true; }
        @Override public boolean isDefaultRightToLeft() { return false; }
        @Override public FormattedCharSequence getVisualOrder(FormattedText text) { return FormattedCharSequence.EMPTY; }
    }
}
