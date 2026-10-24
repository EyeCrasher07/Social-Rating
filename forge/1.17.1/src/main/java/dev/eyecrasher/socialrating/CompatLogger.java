package dev.eyecrasher.socialrating;
public final class CompatLogger {
    private CompatLogger() {}
    public static org.apache.logging.log4j.Logger getLogger(String name) { return org.apache.logging.log4j.LogManager.getLogger(name); }
}
