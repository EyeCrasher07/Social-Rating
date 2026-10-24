package dev.eyecrasher.socialrating;
public final class JavaCompat {
    private JavaCompat() {}
    public static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    public static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    @SafeVarargs public static <T> java.util.List<T> list(T... values) { return java.util.Collections.unmodifiableList(new java.util.ArrayList<T>(java.util.Arrays.asList(values))); }
    @SafeVarargs public static <T> java.util.Set<T> set(T... values) { return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<T>(java.util.Arrays.asList(values))); }
    public static <T> java.util.List<T> copyList(java.util.Collection<? extends T> values) { return java.util.Collections.unmodifiableList(new java.util.ArrayList<T>(values)); }
    public static <T> java.util.Set<T> copySet(java.util.Collection<? extends T> values) { return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<T>(values)); }
    public static <K,V> java.util.Map<K,V> copyMap(java.util.Map<? extends K,? extends V> values) { return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<K,V>(values)); }
    public static <K,V> java.util.Map<K,V> map() { return java.util.Collections.emptyMap(); }
    public static <T> java.util.concurrent.CompletableFuture<T> failed(Throwable failure) { var result = new java.util.concurrent.CompletableFuture<T>(); result.completeExceptionally(failure); return result; }
    public static String read(java.nio.file.Path path) throws java.io.IOException { return new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8); }
    public static java.nio.file.Path write(java.nio.file.Path path, CharSequence text, java.nio.file.OpenOption... options) throws java.io.IOException { return java.nio.file.Files.write(path, text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), options); }
    public static String strip(String text) {
        int start=0,end=text.length();
        while(start<end && Character.isWhitespace(text.codePointAt(start))) start+=Character.charCount(text.codePointAt(start));
        while(end>start && Character.isWhitespace(text.codePointBefore(end))) end-=Character.charCount(text.codePointBefore(end));
        return text.substring(start,end);
    }
    public static boolean blank(String text) { return text.codePoints().allMatch(Character::isWhitespace); }
    public static String repeat(String text, int count) { StringBuilder value=new StringBuilder(); for(int i=0;i<count;i++)value.append(text); return value.toString(); }
}
