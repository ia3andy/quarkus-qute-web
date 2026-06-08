package io.quarkiverse.qute.web.image.deployment.converter;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import app.photofox.vipsffm.VipsError;
import app.photofox.vipsffm.VipsOption;
import app.photofox.vipsffm.enums.VipsInteresting;
import io.quarkiverse.qute.web.image.deployment.items.model.GeneratedImageOptions;
import io.quarkiverse.qute.web.image.deployment.items.model.ImageBuilder;
import io.quarkiverse.qute.web.image.deployment.items.model.ResolvedSourceImage;
import io.quarkiverse.qute.web.image.deployment.items.model.ScannedImageTag;
import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.OriginalInfo;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VipsConverter implements ImageConverter {
    private static final Logger LOGGER = Logger.getLogger(VipsConverter.class);

    @Override
    public Map<GeneratedImage, Path> processImage(ScannedImageTag imageTag,
            ResolvedSourceImage resolvedImage,
            ImageBuilder imageBuilder,
            Path targetDist) {
        final Map<GeneratedImage, Path> images = new HashMap<>();
        Vips.run(
                arena -> {
                    try {

                        final ReadImage image = readImage(arena, resolvedImage);
                        final OriginalInfo info = new OriginalInfo(image.format(), image.buffered().getWidth(),
                                image.buffered().getHeight());
                        imageBuilder.info(info);

                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debugf("ImageTag found %s with base width: %s, preset: %s", resolvedImage.id(), info.width(),
                                    imageTag.config(),
                                    imageTag.config().widths(), imageTag.config());
                        }

                        final List<String> formats = imageTag.config().normalizedFormats();

                        for (String format : formats) {
                            for (int dimension : imageTag.config().widths()) {
                                if (info.width() >= dimension) {
                                    if (LOGGER.isDebugEnabled()) {
                                        LOGGER.debugf("  Generating width: %s format: %s", dimension, format);
                                    }
                                    imageBuilder.addGeneratedImage(new GeneratedImageOptions(dimension, format,
                                            imageTag.config().crop().orElse(null), imageTag.config().quality()),
                                            generatedImage -> {
                                                final Path path = generateImage(arena, imageTag, image, format, dimension,
                                                        generatedImage,
                                                        targetDist);
                                                images.put(generatedImage, path);
                                            });

                                } else {
                                    if (LOGGER.isDebugEnabled()) {
                                        LOGGER.debugf("  Skipping width: %s (larger than base)", dimension);
                                    }
                                }
                            }
                        }

                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        return images;
    }

    private static ReadImage readImage(Arena arena, ResolvedSourceImage resolvedImage) throws IOException {
        final VImage image = VImage.newFromBytes(arena, resolvedImage.contents());
        return new ReadImage(image, ImageUtils.normalizeFormat(resolvedImage.id().extension()));
    }

    private static Path generateImage(Arena arena, ScannedImageTag imageTag,
            ReadImage image,
            String format,
            int width,
            GeneratedImage generatedImage,
            Path targetDist) {
        try {
            Path generatedImagePath = ImageUtils.generatedImagePath(targetDist, generatedImage);
            Files.createDirectories(generatedImagePath.getParent());

            VImage resized = image.buffered().thumbnailImage(width,
                    thumbnailOptions(imageTag.config()));
            resized.writeToFile(generatedImagePath.toAbsolutePath().toString(),
                    writeOptions(imageTag.config()));
            return generatedImagePath;
        } catch (IOException | VipsError e) {
            throw new RuntimeException("Failed to generate image: " + generatedImage.outputPath(), e);
        }
    }

    private static VipsOption[] thumbnailOptions(PresetConfig preset) {
        var opts = new ArrayList<VipsOption>();
        preset.crop().map(VipsConverter::getInteresting).ifPresent(c -> {
            opts.add(VipsOption.Enum("crop", c));
        });
        opts.add(VipsOption.Boolean("no-rotate", false));
        return opts.toArray(new VipsOption[0]);
    }

    private static VipsOption[] writeOptions(PresetConfig preset) {
        var opts = new ArrayList<VipsOption>();
        if (preset.quality() != null) {
            opts.add(VipsOption.Int("Q", preset.quality()));
        }
        return opts.toArray(new VipsOption[0]);
    }

    public record ReadImage(VImage buffered, String format) {
    }

    private static VipsInteresting getInteresting(PresetConfig.Crop c) {
        return switch (c.keep()) {
            case NONE -> VipsInteresting.INTERESTING_NONE;
            case ALL -> VipsInteresting.INTERESTING_ALL;
            case ENTROPY -> VipsInteresting.INTERESTING_ENTROPY;
            case CENTER -> VipsInteresting.INTERESTING_CENTRE;
            case ATTENTION -> VipsInteresting.INTERESTING_ATTENTION;
            case LOW -> VipsInteresting.INTERESTING_LOW;
            case HIGH -> VipsInteresting.INTERESTING_HIGH;
        };
    }

}
