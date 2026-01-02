package io.quarkiverse.qute.web.image.runtime.model;

import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkiverse.qute.web.image.runtime.model.builder.ImageBuilder;

public record ScannedImageTag(String templateId, String declaredPath, PresetConfig config, ImageBuilder image) {

    public ImageTag toImageTag() {
        return new ImageTag(templateId, declaredPath, config, image.build());
    }

}
