package io.quarkiverse.qute.web.image.deployment.converter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.image.deployment.items.model.GeneratedImageOptions;
import io.quarkiverse.qute.web.image.deployment.items.model.ImageBuilder;
import io.quarkiverse.qute.web.image.deployment.items.model.ResolvedSourceImage;
import io.quarkiverse.qute.web.image.deployment.items.model.ScannedImageTag;
import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.OriginalInfo;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

public class ImageIOConverter implements ImageConverter {
    private static final Logger LOGGER = Logger.getLogger(ImageIOConverter.class);

    @Override
    public Map<GeneratedImage, Path> processImage(ScannedImageTag imageTag,
            ResolvedSourceImage resolvedImage,
            ImageBuilder imageBuilder,
            Path targetDist) {
        try {
            final Map<GeneratedImage, Path> images = new HashMap<>();
            final ReadImage image = readImage(resolvedImage);
            final OriginalInfo info = new OriginalInfo(image.format(), image.buffered().getWidth(),
                    image.buffered().getHeight());
            imageBuilder.info(info);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("ImageTag found %s with base width: %s, preset: %s", resolvedImage.id(), info.width(),
                        imageTag.config(),
                        imageTag.config().widths(), imageTag.config());
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
                                    final Path path = generateImage(image, format, dimension, targetHeight, crop,
                                            generatedImage, targetDist);
                                    images.put(generatedImage, path);
                                });

                    } else {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debugf("  Skipping width: %s (larger than base)", dimension);
                        }
                    }
                }
            }
            return images;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static int computeTargetHeight(int targetWidth, OriginalInfo info, PresetConfig.Crop crop) {
        if (crop != null && crop.ratio() != null) {
            String[] parts = crop.ratio().split(":");
            if (parts.length == 2) {
                double ratioW = Double.parseDouble(parts[0]);
                double ratioH = Double.parseDouble(parts[1]);
                return (int) Math.round(targetWidth * ratioH / ratioW);
            }
        }
        return (int) Math.round((double) info.height() * targetWidth / info.width());
    }

    private static Path generateImage(ReadImage image,
            String format,
            int width,
            int height,
            PresetConfig.Crop crop,
            GeneratedImage generatedImage,
            Path targetDist) {
        try {
            Path generatedImagePath = ImageUtils.generatedImagePath(targetDist, generatedImage);
            Files.createDirectories(generatedImagePath.getParent());
            var builder = Thumbnails.of(image.buffered())
                    .size(width, height)
                    .outputFormat(ImageUtils.extensionFromFormat(format));
            if (crop != null) {
                builder.crop(toPosition(crop.keep()));
            }
            builder.toFile(generatedImagePath.toFile());
            return generatedImagePath;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Positions toPosition(PresetConfig.Keep keep) {
        return switch (keep) {
            case CENTER, ATTENTION, ENTROPY -> Positions.CENTER;
            case LOW -> Positions.BOTTOM_CENTER;
            case HIGH -> Positions.TOP_CENTER;
            case NONE, ALL -> Positions.CENTER;
        };
    }

    private static ReadImage readImage(ResolvedSourceImage resolvedImage) throws IOException {
        try (ImageInputStream imageInputStream = ImageIO
                .createImageInputStream(new ByteArrayInputStream(resolvedImage.contents()))) {
            Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);
            while (imageReaders.hasNext()) {
                ImageReader imageReader = imageReaders.next();
                try {
                    imageReader.setInput(imageInputStream);
                    final BufferedImage bufferedImage = imageReader.read(0);
                    return new ReadImage(bufferedImage, imageReader.getFormatName());
                } finally {
                    imageReader.dispose();
                }
            }
        }
        throw new IOException("Could not read image " + resolvedImage.id());
    }

    public record ReadImage(BufferedImage buffered, String format) {
    }

}
