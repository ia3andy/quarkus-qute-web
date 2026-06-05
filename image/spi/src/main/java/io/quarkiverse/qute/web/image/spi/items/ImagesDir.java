package io.quarkiverse.qute.web.image.spi.items;

import java.nio.file.Path;

public sealed interface ImagesDir {

    record ResourceDir(String prefix) implements ImagesDir {
    }

    record LocalDir(Path basePath) implements ImagesDir {
    }

}
