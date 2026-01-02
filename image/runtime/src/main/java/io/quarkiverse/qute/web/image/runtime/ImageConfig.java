package io.quarkiverse.qute.web.image.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.qute.image")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface ImageConfig {

    /**
     * Global CSS media queries, referenced by baseName (e.g., mobile, tablet, desktop).
     * Example: desktop -> "max-width: 1200px"
     */
    Map<String, String> mediaQueries();

    /**
     * By default, the Java ImageIO library will be used to process images.
     * Supported formats are limited to the following and cropping is basic:
     *
     * [cols="1,1,1", options="header"]
     * |===
     * | Format | Readable | Writable
     *
     * | JPEG (`jpg`, `jpeg`) | Yes | Yes
     * | PNG (`png`) | Yes | Yes
     * | GIF (`gif`) | Yes | Yes
     * | BMP (`bmp`) | Yes | Yes
     * | WBMP (`wbmp`) | Yes | Yes
     * |===
     *
     * To enable additional formats (e.g., TIFF, WebP, HEIC) and more options, you can use **libvips** native libraries,
     * which requires binaries available in your system path.
     * See: https://github.com/lopcode/vips-ffm?tab=readme-ov-file#native-library-loading
     *
     * @asciidoclet
     */
    @WithDefault("imageio")
    Library library();

    enum Library {
        IMAGEIO,
        LIBVIPS
    }

    /**
     * Preset definitions.
     * If empty, the default configuration is used.
     */
    Map<String, PresetConfig> presets();

}
