package io.quarkiverse.qute.web.image.deployment;

import static io.quarkiverse.qute.web.image.deployment.QuteImageScanProcessor.toUnixPath;
import static io.quarkiverse.qute.web.image.runtime.converter.ImageIOConverter.processImage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.image.deployment.items.ImagesBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTargetDirBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem.ImageTagSection;
import io.quarkiverse.qute.web.image.runtime.ImageConfig;
import io.quarkiverse.qute.web.image.runtime.ImageRecorder;
import io.quarkiverse.qute.web.image.runtime.ImageSectionHelperFactory;
import io.quarkiverse.qute.web.image.runtime.ImageTemplateExtension;
import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;
import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkiverse.qute.web.image.runtime.model.ResolvedSourceImage;
import io.quarkiverse.qute.web.image.runtime.model.ScannedImageTag;
import io.quarkiverse.qute.web.image.runtime.model.builder.ImagesBuilder.AddImageResult;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkiverse.qute.web.image.spi.items.WhitelistDirBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;

public class QuteImageProcessor {

    private static final Logger LOGGER = Logger.getLogger(QuteImageProcessor.class);

    // From https://dev.to/razbakov/responsive-images-best-practices-in-2025-4dlb
    private static final int[] DIMENSIONS = new int[] {
            640,
            1024,
            1920,
            2560,
    };

    @BuildStep
    void initBundleBean(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(Images.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(ImageSectionHelperFactory.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(ImageTemplateExtension.class));
    }

    @BuildStep
    ImagesBuildItem processTemplatesWithImages(
            ImageConfig imageConfig,
            List<QuteImageTemplateToScanBuildItem> templateToScan,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            QuteImageTargetDirBuildItem targetDir,
            List<WhitelistDirBuildItem> whitelistDirs,
            List<ImagesDirBuildItem> imageSourceDirs) {
        if (templateToScan.isEmpty()) {
            return null;
        }
        ImagesBuildItem images = new ImagesBuildItem();

        for (QuteImageTemplateToScanBuildItem template : templateToScan) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("Inspecting (run-time) template %s for image tags",
                        template.id);
            }
            // default to null
            Path templatePath = resolveSourcePath(template);
            // we don't want to place images in the templates folder
            for (ImageTagSection resp : template.sectionNodes) {
                collectImage(resp,
                        template.id,
                        templatePath,
                        images,
                        staticResourceProducer,
                        targetDir.path,
                        whitelistPredicate(whitelistDirs),
                        imageSourceDirs);
            }

        }
        return images;
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    void recordImages(ImageRecorder imageRecorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer,
            ImagesBuildItem images) {

        if (images == null) {
            return;
        }

        syntheticBeanProducer.produce(SyntheticBeanBuildItem.configure(Images.class)
                .supplier(imageRecorder.imagesSupplier(images.builder().computeImageTags()))
                .named("images")
                .scope(Singleton.class)
                .unremovable()
                .done());

    }

    private static Predicate<Path> whitelistPredicate(final List<WhitelistDirBuildItem> whitelistDirs) {
        return (p) -> {
            for (WhitelistDirBuildItem whitelistDir : whitelistDirs) {
                if (p.normalize().toAbsolutePath().startsWith(whitelistDir.path().toAbsolutePath())) {
                    return true;
                }
            }
            return false;
        };
    }

    private static Path resolveSourcePath(QuteImageTemplateToScanBuildItem template) {
        if (template == null || template.location == null) {
            return null;
        }
        if (template.location.getScheme() == null) {
            URI baseUri = Paths.get("").toAbsolutePath().toUri();
            return Paths.get(baseUri.resolve(template.location));
        }
        if ("file".equalsIgnoreCase(template.location.getScheme()))
            return Paths.get(template.location);
        return null;
    }

    private static void collectImage(ImageTagSection tag, String templateName, Path templatePath, ImagesBuildItem images,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer, Path targetDist,
            Predicate<Path> whitelistPredicate, List<ImagesDirBuildItem> imageDirs) {

        Path imagePath = Path.of(tag.fileParam());
        ResolvedSourceImage resolvedImage;
        // We can't use Path.isAbsolute on Windows, and our paths are expected to be URIs anyways
        if (tag.fileParam().startsWith("/")) {
            // We resolve absolute from provided image dirs
            resolvedImage = resolveFromImageDir(images, whitelistPredicate, imageDirs,
                    tag.fileParam());
        } else if (templatePath != null) {
            Path resolvedPath = templatePath.getParent().resolve(imagePath).normalize();
            // all images dirs and src are automatically whitelisted
            final String name = resolvedPath.getFileName().toString();
            resolvedImage = resolveImage(whitelistPredicate, images, resolvedPath, name, false, null);
            if (resolvedImage == null) {
                throw new RuntimeException("Image does not exist or is not a file: " + imagePath + " (looked up at "
                        + resolvedPath + ")");
            }
        } else {
            throw new RuntimeException(
                    "Cannot refer to relative files from template when we do not know the template path: "
                            + templateName);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf(" Found image tag for image: %s", imagePath);
        }
        AddImageResult collectedImageResult = images.builder().addImage(resolvedImage);
        final ScannedImageTag imageTag = images.builder().scannedImageTag(tag.section().getOrigin().getTemplateId(),
                tag.fileParam(),
                tag.presetConfig(),
                collectedImageResult.image());
        if (collectedImageResult.created()) {
            final Map<GeneratedImage, Path> generatedImages = processImage(imageTag, resolvedImage,
                    collectedImageResult.image(), targetDist);
            for (Map.Entry<GeneratedImage, Path> e : generatedImages.entrySet()) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.tracef("Generated '%s' (%s)", e.getValue(),
                            e.getKey());
                }
                staticResourceProducer.produce(new GeneratedStaticResourceBuildItem(
                        e.getKey().outputPath(),
                        e.getValue()));
            }
        }

    }

    private static ResolvedSourceImage resolveFromImageDir(ImagesBuildItem images,
                                                           Predicate<Path> whitelistPredicate,
                                                           List<ImagesDirBuildItem> imagesDirs,
                                                           String path) {
        final String relativePath = path.substring(1);
        for (ImagesDirBuildItem imageSourceDir : imagesDirs) {
            Path resolvedPath = imageSourceDir.basePath().resolve(relativePath).normalize();
            final String publicPath = imageSourceDir.toPublicPath(path);
            ResolvedSourceImage ret = resolveImage(whitelistPredicate, images, resolvedPath,
                    Path.of(publicPath).getFileName().toString(), imageSourceDir.isResource(),
                    imageSourceDir.isServed() ? publicPath : null);
            if (ret != null) {
                return ret;
            }
        }
        final List<String> sources = imagesDirs.stream().map(ImagesDirBuildItem::basePath)
                .map(QuteImageScanProcessor::toUnixPath).toList();
        throw new RuntimeException(
                "Image does not exist or is not a file: " + relativePath + " (looked up at " + sources + ")");
    }

    private static ResolvedSourceImage resolveImage(Predicate<Path> isWhitelist,
            ImagesBuildItem images,
            Path resolvedPath,
            String name,
            Boolean isResource,
            String publicPath) {
        // We split resources and filesystem in different cases for safety
        if (isResource) {
            AtomicReference<ResolvedSourceImage> image = new AtomicReference<>();
            final String unixPath = toUnixPath(resolvedPath);
            String resourcePath = unixPath.startsWith("/") ? unixPath.substring(1)
                    : unixPath;
            QuarkusClassLoader.visitRuntimeResources(resourcePath, c -> {
                try {
                    final byte[] contents = Files.readAllBytes(c.getPath());
                    ImageId id = images.builder().getImageId(c.getPath().toAbsolutePath(), name, contents, publicPath);
                    image.set(new ResolvedSourceImage(resolvedPath, id, publicPath != null, contents));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read image " + resolvedPath + " from the classpath", e);
                }
            });
            if (image.get() != null) {
                return image.get();
            }
        } else {
            final Path absolutePath = resolvedPath.toAbsolutePath();
            if (!isWhitelist.test(absolutePath)) {
                throw new RuntimeException("Image path outside whitelist directories: " + resolvedPath + " ");
            }
            if (Files.isRegularFile(absolutePath)) {
                try {
                    final byte[] contents = Files.readAllBytes(absolutePath);
                    ImageId id = images.builder().getImageId(absolutePath, name, contents, publicPath);
                    return new ResolvedSourceImage(absolutePath, id, publicPath != null, contents);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read image " + resolvedPath + " from the filesystem", e);
                }
            }
        }
        return null;
    }

    private static String pathFromWebRoot(String resource, String root) {
        if (!resource.startsWith(root)) {
            throw new IllegalStateException("Web Bundler must be located under the root: " + root);
        }
        return resource.substring(root.endsWith("/") ? root.length() : root.length() + 1);
    }

}
