package io.quarkiverse.qute.web.image.runtime.model;

import static io.quarkiverse.qute.web.image.runtime.ImageUtils.imageTagKey;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Map;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.qute.web.image.runtime.ImageUtils;

/**
 * The processed tags and images. Recorded as a serialized resource rather than as bytecode constants, since a site
 * with thousands of images exceeds the constant pool of a recorded class.
 */
@Vetoed
public class Images implements Serializable {

    public static final Images EMPTY = new Images(Map.of(), Map.of());

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
        ImageTag tag = tags.get(imageTagKey(templateId, declaredURI, preset));
        if (tag == null) {
            tag = tags.get(imageTagKey(ImageUtils.GLOB_TEMPLATE_ID, declaredURI, preset));
        }
        return tag;
    }

    public byte[] toBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(this);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize the processed images", e);
        }
        return bytes.toByteArray();
    }

    public static Images read(InputStream in) {
        try (ObjectInputStream objects = new ObjectInputStream(in)) {
            return (Images) objects.readObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the processed images", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to read the processed images", e);
        }
    }

    /** Reads the resource the build recorded, from the context classloader. */
    public static Images read(String resource) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Processed images resource not found: " + resource);
            }
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the processed images", e);
        }
    }
}
