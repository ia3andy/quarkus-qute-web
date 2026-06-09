package io.quarkiverse.qute.web.image.spi.items;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Declares a directory for image lookups. Extensions register these so the image processor
 * can discover and pre-process images from multiple sources.
 */
public final class ImagesDirBuildItem extends MultiBuildItem {

    private final String prefix;
    private final Path localPath;

    private ImagesDirBuildItem(String prefix, Path localPath) {
        this.prefix = normalizePrefix(prefix);
        this.localPath = localPath;
    }

    /**
     * Classpath resource directory.
     *
     * @param prefix the classpath prefix (e.g. "web/static/images")
     */
    public static ImagesDirBuildItem of(String prefix) {
        return new ImagesDirBuildItem(prefix, null);
    }

    /**
     * Local filesystem directory with an explicit prefix for indexing.
     *
     * @param prefix the prefix used for image lookups (e.g. "roq-public")
     * @param localPath the filesystem path to the directory
     */
    public static ImagesDirBuildItem of(String prefix, Path localPath) {
        return new ImagesDirBuildItem(prefix, localPath);
    }

    public String prefix() {
        return prefix;
    }

    public Path localPath() {
        return localPath;
    }

    public boolean isLocal() {
        return localPath != null;
    }

    @Override
    public String toString() {
        if (localPath != null) {
            return "local:" + localPath + " (prefix=" + prefix + ")";
        }
        return "classpath:" + prefix;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        String normalized = prefix;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
