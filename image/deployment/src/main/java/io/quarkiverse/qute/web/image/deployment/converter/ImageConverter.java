package io.quarkiverse.qute.web.image.deployment.converter;

import java.nio.file.Path;
import java.util.Map;

import io.quarkiverse.qute.web.image.deployment.items.model.ImageBuilder;
import io.quarkiverse.qute.web.image.deployment.items.model.ResolvedSourceImage;
import io.quarkiverse.qute.web.image.deployment.items.model.ScannedImageTag;
import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;

public interface ImageConverter {

    Map<GeneratedImage, Path> processImage(ScannedImageTag imageTag,
            ResolvedSourceImage resolvedImage,
            ImageBuilder imageBuilder,
            Path targetDist);

}
