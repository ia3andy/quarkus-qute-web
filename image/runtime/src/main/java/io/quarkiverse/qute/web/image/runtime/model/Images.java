package io.quarkiverse.qute.web.image.runtime.model;

import static io.quarkiverse.qute.web.image.runtime.ImageUtils.imageTagKey;

import java.util.Map;

import jakarta.enterprise.inject.Vetoed;

@Vetoed
public class Images {

    // map of tags (by key:templateid/file) to image user
    private final Map<String, ImageTag> tags;

    // map of images by normalised path
    private final Map<String, Image> images;

    public Images(Map<String, ImageTag> tags, Map<String, Image> images) {
        this.tags = tags;
        this.images = images;
    }

    public Image image(String path) {
        return images.get(path);
    }

    public ImageTag get(String templateId, String declaredURI, String preset) {
        return tags.get(imageTagKey(templateId, declaredURI, preset));
    }

}
