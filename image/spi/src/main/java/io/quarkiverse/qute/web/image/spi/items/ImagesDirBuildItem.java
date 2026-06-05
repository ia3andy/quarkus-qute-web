package io.quarkiverse.qute.web.image.spi.items;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

/**
 *
 * <p>
 * Represents an additional directory that extensions can register for image lookups
 * using absolute paths.
 * </p>
 */
public final class ImagesDirBuildItem extends MultiBuildItem {

    private final ImagesDir dir;

    public ImagesDirBuildItem(ImagesDir dir) {
        this.dir = dir;
    }

    public static ImagesDirBuildItem resource(String prefix) {
        return new ImagesDirBuildItem(new ImagesDir.ResourceDir(prefix));
    }

    public static ImagesDirBuildItem localDir(Path path) {
        return new ImagesDirBuildItem(new ImagesDir.LocalDir(path));
    }

    public ImagesDir dir() {
        return dir;
    }

    @Override
    public String toString() {
        return dir.toString();
    }
}
