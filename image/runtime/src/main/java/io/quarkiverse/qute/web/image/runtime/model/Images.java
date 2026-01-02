package io.quarkiverse.qute.web.image.runtime.model;

import static io.quarkiverse.qute.web.image.runtime.ImageUtils.imageTagKey;

import java.util.Map;

import jakarta.enterprise.inject.Vetoed;

@Vetoed
public class Images {

    // map of tags (by key:templateid/file) to image user
    private final Map<String, ImageTag> tags;

    public Images(Map<String, ImageTag> tags) {
        this.tags = tags;
    }

    public ImageTag get(String templateId, String declaredURI) {
        return tags.get(imageTagKey(templateId, declaredURI));
    }

}
