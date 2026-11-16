package io.quarkiverse.qute.web.image.deployment.items;

import java.util.Collection;
import java.util.Map;

import io.quarkiverse.qute.web.image.runtime.model.Image;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * The outcome of the build-time image processing, once every usage has been resolved and converted: one
 * {@link ImageTag} per usage (the template it was found in, or null for an image handed over by another
 * extension or matched by a glob, the path as declared, and the {@link Image} with its generated variants or its
 * passthrough flag) and the images by the path they were registered under.
 * <p>
 * Absent when no template uses images and nothing was handed over.
 */
public final class QuteImageProcessedBuildItem extends SimpleBuildItem {

    private final Map<String, ImageTag> tags;
    private final Map<String, Image> images;

    public QuteImageProcessedBuildItem(Map<String, ImageTag> tags, Map<String, Image> images) {
        this.tags = Map.copyOf(tags);
        this.images = Map.copyOf(images);
    }

    /** The processed usages. */
    public Collection<ImageTag> tags() {
        return tags.values();
    }

    /** The processed usages by their runtime lookup key. */
    public Map<String, ImageTag> tagsByKey() {
        return tags;
    }

    /** The images by the path they were registered under. */
    public Map<String, Image> images() {
        return images;
    }
}
