package io.quarkiverse.qute.web.image.deployment;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.image.deployment.converter.ImageConverter;
import io.quarkiverse.qute.web.image.deployment.converter.ImageIOConverter;
import io.quarkiverse.qute.web.image.deployment.items.ImageConverterBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.ImagesBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTargetDirBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem.ImageTagSection;
import io.quarkiverse.qute.web.image.deployment.items.model.ImagesBuilder;
import io.quarkiverse.qute.web.image.deployment.items.model.ResolvedSourceImage;
import io.quarkiverse.qute.web.image.deployment.items.model.ScannedImageTag;
import io.quarkiverse.qute.web.image.runtime.ImageConfig;
import io.quarkiverse.qute.web.image.runtime.ImageRecorder;
import io.quarkiverse.qute.web.image.runtime.ImageSectionHelperFactory;
import io.quarkiverse.qute.web.image.runtime.ImageTemplateExtension;
import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;
import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkiverse.qute.web.image.spi.items.ImagesDir;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;

public class QuteImageProcessor {

    private static final Logger LOGGER = Logger.getLogger(QuteImageProcessor.class);

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
            Optional<ImageConverterBuildItem> converter,
            List<QuteImageTemplateToScanBuildItem> templateToScan,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            QuteImageTargetDirBuildItem targetDir,
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
            final ImageConverter conv = converter.map(ImageConverterBuildItem::get).orElse(new ImageIOConverter());
            // we don't want to place images in the templates folder
            for (ImageTagSection resp : template.sectionNodes) {

                collectImage(staticResourceProducer, conv, imageSourceDirs, images, resp,
                        templatePath, template.id,
                        targetDir.path);
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
                .supplier(imageRecorder.imagesSupplier(images.builder().computeImageTags(), images.builder().computeImages()))
                .named("images")
                .scope(Singleton.class)
                .unremovable()
                .done());

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

    private static void collectImage(BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            ImageConverter converter, List<ImagesDirBuildItem> imageDirs, ImagesBuildItem images, ImageTagSection tag,
            Path templatePath, String templateName,
            Path targetDist) {

        Path imagePath = Path.of(tag.fileParam());
        ResolvedSourceImage resolvedImage;
        // We can't use Path.isAbsolute on Windows, and our paths are expected to be URIs anyways
        if (tag.fileParam().startsWith("/")) {
            // We resolve absolute from provided image dirs
            resolvedImage = resolveFromImagesDir(images, imageDirs,
                    tag.fileParam());
        } else if (templatePath != null) {
            // TODO see how resources basePath are handled
            final ImagesDirBuildItem dir = ImagesDirBuildItem.localDir(templatePath.getParent());
            final ImagesDirResolver resolver = ImagesDirResolver.of(dir);
            if (resolver.exists(tag.fileParam())) {
                resolvedImage = resolveImage(images, resolver, tag.fileParam());
            } else {
                throw new RuntimeException("Image does not exist or is not a file: " + imagePath + " (looked up at "
                        + dir + ")");
            }
        } else {
            throw new RuntimeException(
                    "Cannot refer to relative files from template when we do not know the template basePath: "
                            + templateName);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf(" Found image tag for image: %s", imagePath);
        }
        ImagesBuilder.AddImageResult collectedImageResult = images.builder().addImage(resolvedImage);
        final ScannedImageTag imageTag = images.builder().scannedImageTag(tag.section().getOrigin().getTemplateId(),
                tag.fileParam(),
                tag.presetConfig(),
                collectedImageResult.image());
        if (collectedImageResult.created()) {
            final Map<GeneratedImage, Path> generatedImages = converter.processImage(imageTag, resolvedImage,
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

    private static ResolvedSourceImage resolveFromImagesDir(ImagesBuildItem images,
            List<ImagesDirBuildItem> imagesDirs,
            String path) {
        final String relativePath = path.substring(1);
        for (ImagesDirBuildItem dir : imagesDirs) {
            ImagesDirResolver resolver = ImagesDirResolver.of(dir);
            if (resolver.exists(path)) {
                return resolveImage(images, resolver, path);
            }
        }
        final List<String> sources = imagesDirs.stream().map(ImagesDirBuildItem::dir)
                .map(ImagesDir::toString).toList();
        throw new RuntimeException(
                "Image does not exist or is not a file: " + relativePath + " (looked up at " + sources + ")");
    }

    private static void checkParentDir(Path parentDir, Path resolvedImagePath) {
        if (!resolvedImagePath.normalize().startsWith(parentDir.normalize())) {
            throw new RuntimeException("Relative image basePath outside parent directory: '%s' (parent: '%s') "
                    .formatted(resolvedImagePath.normalize(), parentDir.normalize()));
        }
    }

    private static ResolvedSourceImage resolveImage(ImagesBuildItem images, ImagesDirResolver resolver,
            String path) {
        final byte[] contents = resolver.readFile(path);
        final String normalized = resolver.normalizedPath(path);
        final String name = resolver.name(path);
        ImageId id = images.builder().getImageId(normalized, name, contents);
        return new ResolvedSourceImage(path, id, contents);
    }

}
