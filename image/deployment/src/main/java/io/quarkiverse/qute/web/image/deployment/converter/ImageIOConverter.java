package io.quarkiverse.qute.web.image.deployment.converter;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.image.converter.ImageIOProcessor;
import io.quarkiverse.qute.web.image.converter.ImageInfo;
import io.quarkiverse.qute.web.image.converter.ImageOptions;
import io.quarkiverse.qute.web.image.converter.ImageProcessor;
import io.quarkiverse.qute.web.image.converter.ImageSizing;
import io.quarkiverse.qute.web.image.deployment.items.model.GeneratedImageOptions;
import io.quarkiverse.qute.web.image.deployment.items.model.ImageBuilder;
import io.quarkiverse.qute.web.image.deployment.items.model.ResolvedSourceImage;
import io.quarkiverse.qute.web.image.deployment.items.model.ScannedImageTag;
import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.OriginalInfo;

public class ImageIOConverter implements ImageConverter {
    private static final Logger LOGGER = Logger.getLogger(ImageIOConverter.class);

    private final ImageProcessor processor = new ImageIOProcessor();

    @Override
    public Map<GeneratedImage, Path> processImage(ScannedImageTag imageTag,
            ResolvedSourceImage resolvedImage,
            ImageBuilder imageBuilder,
            Path targetDist) {
        final Map<GeneratedImage, Path> images = new HashMap<>();
        final ImageInfo info = processor.readInfo(resolvedImage.contents());
        imageBuilder.info(new OriginalInfo(info.format(), info.width(), info.height()));

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("ImageTag found %s with base width: %s, preset: %s", resolvedImage.id(), info.width(),
                    imageTag.config());
        }

        final List<String> formats = imageTag.config().normalizedFormats();
        final PresetConfig.Crop crop = imageTag.config().crop().orElse(null);

        for (String format : formats) {
            for (int dimension : imageTag.config().widths()) {
                if (info.width() >= dimension) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debugf("  Generating width: %s format: %s", dimension, format);
                    }
                    int targetHeight = computeTargetHeight(dimension, info, crop);
                    imageBuilder.addGeneratedImage(
                            new GeneratedImageOptions(dimension, targetHeight, format, crop,
                                    imageTag.config().quality()),
                            generatedImage -> {
                                Path outputPath = ImageUtils.generatedImagePath(targetDist, generatedImage);
                                ImageOptions options = new ImageOptions(
                                        dimension, targetHeight,
                                        ImageUtils.extensionFromFormat(format),
                                        crop != null
                                                ? new ImageOptions.CropOptions(crop.ratio(), toCropPosition(crop.keep()))
                                                : null,
                                        imageTag.config().quality());
                                processor.process(resolvedImage.contents(), options, outputPath);
                                images.put(generatedImage, outputPath);
                            });
                } else {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debugf("  Skipping width: %s (larger than base)", dimension);
                    }
                }
            }
        }
        return images;
    }

    static int computeTargetHeight(int targetWidth, ImageInfo info, PresetConfig.Crop crop) {
        return ImageSizing.computeTargetHeight(targetWidth, info.width(), info.height(),
                crop != null ? crop.ratio() : null);
    }

    private static ImageOptions.CropPosition toCropPosition(PresetConfig.Keep keep) {
        return switch (keep) {
            case LOW -> ImageOptions.CropPosition.BOTTOM;
            case HIGH -> ImageOptions.CropPosition.TOP;
            default -> ImageOptions.CropPosition.CENTER;
        };
    }
}
