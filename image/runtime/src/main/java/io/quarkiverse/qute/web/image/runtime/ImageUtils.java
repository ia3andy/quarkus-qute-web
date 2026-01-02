package io.quarkiverse.qute.web.image.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;

public interface ImageUtils {
    public static final Map<String, String> FORMAT_EXTENSION = Map.of(
            "jpeg", "jpg",
            "tiff", "tif");

    public static String imageTagKey(String templateId, String declaredURI) {
        return templateId + "|" + declaredURI;
    }

    public static String keyImage(String id, String name) {
        return id + "|" + name;
    }

    public static Path generatedImagePath(Path targetPath,
            GeneratedImage generatedImage) {
        return targetPath.resolve(generatedImage.outputPath().substring(1));
    }

    public static String removeExtension(String path) {
        final int i = path.lastIndexOf(".");
        return i > 0 ? path.substring(0, i) : path;
    }

    public static String computeOutputPath(String baseName, String imageDigest, Integer width, String settingsHash,
            String ext) {
        final String ref = width == null ? "original" : width + "-" + settingsHash;
        String fileName = "%s-%s.%s".formatted(baseName, ref, ext);
        return Path.of("/static", "images/generated/", imageDigest, fileName).toString().replace('\\', '/');
    }

    public static String computeOutputPath(ImageId id, Integer width, String settingsHash, String ext) {
        return computeOutputPath(id.baseName(), id.digest(), width, settingsHash, ext);
    }

    public static String digest(byte[] contents) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            byte[] digest = messageDigest.digest(contents);
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; ++i) {
                sb.append(Integer.toHexString(digest[i] & 255 | 256).substring(1, 3));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String digest(String contents) {
        return digest(contents.getBytes(StandardCharsets.UTF_8));
    }

    public static String getOutputExt(ImageId id, String format) {
        if (format.equalsIgnoreCase("original")) {
            return id.extension();
        }
        if (FORMAT_EXTENSION.containsKey(format.toLowerCase())) {
            return FORMAT_EXTENSION.get(format.toLowerCase());
        }
        return format.toLowerCase();
    }

}
