package io.quarkiverse.qute.web.image.runtime.model;

public record GeneratedImage(
        ImageId original, // Original image id
        int width, // Target width
        String format, // Format baseName (e.g., "webp", "jpeg")
        String extension, // File extension (e.g., "webp", "jpg")
        String hash, // Short hash for uniqueness
        String outputPath // Full path to generated file
) {

}
