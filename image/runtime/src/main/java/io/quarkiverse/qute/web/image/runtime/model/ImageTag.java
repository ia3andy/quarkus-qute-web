package io.quarkiverse.qute.web.image.runtime.model;

import io.quarkiverse.qute.web.image.runtime.PresetConfig;

/**
 * This represents an image tag, pointing to a processed image
 */
public record ImageTag(String templateId, String declaredPath, PresetConfig config,
        Image image) {
    // Eventually, this will list the variants in use

}
