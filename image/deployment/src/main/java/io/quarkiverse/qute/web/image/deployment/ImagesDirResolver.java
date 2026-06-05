package io.quarkiverse.qute.web.image.deployment;

import static io.quarkiverse.qute.web.image.deployment.QuteImageScanProcessor.toUnixPath;
import static io.quarkiverse.qute.web.image.deployment.utils.PathUtils.join;
import static io.quarkiverse.qute.web.image.deployment.utils.PathUtils.removeLeadingSlash;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import io.quarkiverse.qute.web.image.spi.items.ImagesDir;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;

public sealed interface ImagesDirResolver {

    byte[] readFile(String path);

    boolean exists(String path);

    String normalizedPath(String path);

    String name(String path);

    static Delegate of(ImagesDirBuildItem item) {
        final ImagesDirResolver resolver = switch (item.dir()) {
            case ImagesDir.ResourceDir r -> new ResourceDir(r.prefix());
            case ImagesDir.LocalDir l -> new LocalDir(l.basePath());
        };
        return new Delegate(resolver);
    }

    final class Delegate implements ImagesDirResolver {

        private final ImagesDirResolver resolver;

        private Delegate(ImagesDirResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public byte[] readFile(String path) {
            return resolver.readFile(path);
        }

        @Override
        public boolean exists(String path) {
            return resolver.exists(path);
        }

        @Override
        public String normalizedPath(String path) {
            return resolver.normalizedPath(path);
        }

        @Override
        public String name(String path) {
            return resolver.name(path);
        }
    }

    static void checkParentDir(Path parentDir, Path resolvedImagePath) {
        if (!resolvedImagePath.normalize().startsWith(parentDir.normalize())) {
            throw new RuntimeException("Relative image basePath outside parent directory: '%s' (parent: '%s') "
                    .formatted(resolvedImagePath.normalize(), parentDir.normalize()));
        }
    }

    record LocalDir(Path basePath) implements ImagesDirResolver {
        @Override
        public byte[] readFile(String path) {
            final Path resolve = resolve(path);
            checkParentDir(this.basePath, resolve);
            try {
                return Files.readAllBytes(resolve);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read image " + resolve + " from the classpath", e);
            }
        }

        @Override
        public boolean exists(String path) {
            return Files.exists(resolve(path));
        }

        @Override
        public String normalizedPath(String path) {
            return resolve(path).toAbsolutePath().toString();
        }

        @Override
        public String name(String path) {
            return Path.of(path).getFileName().toString();
        }

        private Path resolve(String path) {
            return basePath.resolve(removeLeadingSlash(path)).normalize();
        }
    }

    record ResourceDir(String prefix) implements ImagesDirResolver {
        @Override
        public byte[] readFile(String path) {
            if (!exists(path)) {
                return null;
            }
            final String resourcePath = getResourcePath(path);
            AtomicReference<byte[]> image = new AtomicReference<>();
            QuarkusClassLoader.visitRuntimeResources(resourcePath, c -> {
                try {
                    image.set(Files.readAllBytes(c.getPath()));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read image " + resourcePath + " from the classpath", e);
                }
            });
            return image.get();
        }

        @Override
        public boolean exists(String path) {
            final String resourcePath = getResourcePath(path);
            return QuarkusClassLoader.isResourcePresentAtRuntime(resourcePath);
        }

        @Override
        public String normalizedPath(String path) {
            return getResourcePath(path);
        }

        @Override
        public String name(String path) {
            return Path.of(path).getFileName().toString();
        }

        private String getResourcePath(String path) {
            return removeLeadingSlash(toUnixPath(join(prefix, path)));
        }

    }
}
