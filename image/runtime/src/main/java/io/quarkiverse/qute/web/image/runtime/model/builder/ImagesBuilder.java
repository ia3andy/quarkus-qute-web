package io.quarkiverse.qute.web.image.runtime.model.builder;

import static io.quarkiverse.qute.web.image.runtime.ImageUtils.digest;
import static io.quarkiverse.qute.web.image.runtime.ImageUtils.imageTagKey;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkiverse.qute.web.image.runtime.model.ResolvedSourceImage;
import io.quarkiverse.qute.web.image.runtime.model.ScannedImageTag;

public class ImagesBuilder {


    private final Map<String, Set<>>

    // cache of absolute image path to image digest
    private final Map<Path, ImageId> imageIdsByPath = new HashMap<>();
    // map of image (digest/file) to image
    private final Map<String, ImageBuilder> images = new HashMap<>();
    private final Map<String, ScannedImageTag> scannedImageTags = new HashMap<>();

    public Map<String, ScannedImageTag> scannedImageTags() {
        return scannedImageTags;
    }

    public Map<String, ImageTag> computeImageTags() {
        Map<String, ImageTag> computed = new HashMap<>();
        for (Map.Entry<String, ScannedImageTag> scanned : scannedImageTags.entrySet()) {
            computed.put(scanned.getKey(), scanned.getValue().toImageTag());
        }
        return computed;
    }

    public ImageId getImageId(Path path, String fileName, byte[] content, String publicPath) {
        final Path normalize = path.normalize();
        ImageId id = imageIdsByPath.get(normalize);
        if (fileName.indexOf('.') <= 1) {
            throw new IllegalArgumentException("Invalid image file name: '%s'".formatted(path));
        }
        String[] name = fileName.split("\\.");
        if (id == null) {
            String digest = digest(content);
            id = new ImageId(digest, name[0], name[1]);
            imageIdsByPath.put(normalize, id);
        }
        return id;
    }

    public AddImageResult addImage(ResolvedSourceImage resolvedImage) {
        /*
         * The idea here is that we want to make sure responsives for a unique absolute path end up in the same folder (same
         * digest,
         * same file baseName),
         * and if someone has the same file (same digest) in more than one absolute path, they also end up in the same folder
         * (same
         * digest, same file baseName),
         * and if someone has the same file (same digest) in more than one absolute path under a different file baseName, they
         * also
         * end
         * up in the same
         * folder (same digest), but with different file names.
         */
        AtomicBoolean created = new AtomicBoolean(false);
        final ImageBuilder image = images.computeIfAbsent(resolvedImage.id().key(), key2 -> {
            created.set(true);
            return new ImageBuilder(resolvedImage.id());
        });
        return new AddImageResult(image, created.get());
    }

    public record AddImageResult(ImageBuilder image, boolean created) {

    }

    public ScannedImageTag scannedImageTag(String templateId, String declaredPath, PresetConfig config,
            ImageBuilder image) {
        final ScannedImageTag tag = new ScannedImageTag(templateId, declaredPath, config, image);
        scannedImageTags.put(imageTagKey(templateId, declaredPath),
                tag);
        return tag;
    }

}
