package io.quarkiverse.qute.web.image.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.quarkiverse.qute.web.image.runtime.PresetConfig.MarkupMode;
import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.Image;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkus.qute.TemplateExtension;

@TemplateExtension
public class ImageTemplateExtension {

    // ----------- Mode helpers -----------
    public static boolean isDirectUrl(ImageTag it) {
        return it.config().markup() == MarkupMode.DIRECT_URL;
    }

    public static boolean isDataImg(ImageTag it) {
        return it.config().markup() == MarkupMode.DATA_IMG;
    }

    public static boolean isDataMode(ImageTag it) {
        return it.config().markup() == MarkupMode.DATA_AUTO || it.config().markup() == MarkupMode.DATA_IMG;
    }

    public static boolean isPicture(ImageTag it) {
        return it.config().formats() != null && it.config().formats().size() > 1;
    }

    public static boolean noscript(ImageTag it) {
        return Boolean.TRUE.equals(it.config().noscript()) && isDataMode(it);
    }

    // ----------- Attributes passthrough (raw strings) -----------
    public static String parentAttrs(ImageTag it) {
        return attr(it, "parent");
    }

    public static String pictureAttrs(ImageTag it) {
        return attr(it, "picture");
    }

    public static String imgAttrs(ImageTag it) {
        return attr(it, "img");
    }

    public static String anchorAttrs(ImageTag it) {
        return attr(it, "a");
    }

    private static String attr(ImageTag it, String key) {
        Map<String, String> attrs = it.config().attributes();
        return attrs != null ? Objects.toString(attrs.get(key), "") : "";
    }

    // ----------- URL selection -----------
    public static String imgSrc(ImageTag it) {
        // Fallback image for <img> (widest of fallback format)
        return selectBest(it.image(), it.config().fallbackFormat(), it.image().info().format());
    }

    public static String directUrl(ImageTag it) {
        // For DIRECT_URL: just the best fallback URL
        return imgSrc(it);
    }

    // ----------- src/srcset attr names depending on mode -----------
    public static String imgSrcAttrName(ImageTag it) {
        return isDataMode(it) ? "data-src" : "src";
    }

    public static String imgSrcsetAttrName(ImageTag it) {
        return isDataMode(it) ? "data-srcset" : "srcset";
    }

    // srcset for single-format <img> (AUTO/DATA_AUTO when formats.size==1, or DATA_IMG)
    public static String imgSrcset(ImageTag it) {
        String fmt = singleFormat(it);
        if (fmt == null)
            return "";
        return buildSrcset(it.image(), fmt, it.config().pixelRatio().orElse(null), it.image().info().format());
    }

    public static boolean hasImgSrcset(ImageTag it) {
        return !imgSrcset(it).isBlank();
    }

    // ----------- <source> list for <picture> -----------
    public static List<Source> sources(ImageTag it) {
        List<String> formats = it.config().normalizedFormats();
        if (formats == null)
            return List.of();

        String originalFormat = it.image().info().format();
        String attrName = isDataMode(it) ? "data-srcset" : "srcset";

        List<Source> list = new ArrayList<>();
        for (String fmt : formats) {
            String srcset = buildSrcset(it.image(), fmt, it.config().pixelRatio().orElse(null), originalFormat);
            if (!srcset.isBlank()) {
                String type = format(fmt, originalFormat);
                list.add(new Source(attrName, srcset, type));
            }
        }
        return list;
    }

    // ----------- Helpers: srcset building & mime type -----------
    private static String buildSrcset(Image img, String fmt, PresetConfig.PixelRatio pixelRatio, String originalFormat) {
        List<GeneratedImage> candidates = img.generated().stream()
                .filter(gi -> matchesFormat(gi, fmt, originalFormat))
                .sorted(Comparator.comparingInt(GeneratedImage::width))
                .toList();

        if (candidates.isEmpty())
            return "";

        if (pixelRatio != null) {
            int base = pixelRatio.baseWidth();
            // Build multiplier srcset: "{url} {ratio}x"
            return joinWithComma(
                    candidates.stream()
                            .map(gi -> gi.outputPath() + " " + trimRatio((double) gi.width() / (double) base) + "x")
                            .distinct()
                            .collect(Collectors.toList()));
        } else {
            // Width-based srcset: "{url} {width}w"
            return joinWithComma(
                    candidates.stream()
                            .map(gi -> gi.outputPath() + " " + gi.width() + "w")
                            .distinct()
                            .collect(Collectors.toList()));
        }
    }

    private static String selectBest(Image img, String desiredFmt, String originalFormat) {
        return img.generated().stream()
                .filter(gi -> matchesFormat(gi, desiredFmt, originalFormat))
                .max(Comparator.comparingInt(GeneratedImage::width))
                .map(GeneratedImage::outputPath)
                .orElseGet(() ->
                // Fallback: any widest generated file
                img.generated().stream()
                        .max(Comparator.comparingInt(GeneratedImage::width))
                        .map(GeneratedImage::outputPath)
                        .orElse(""));
    }

    private static boolean matchesFormat(GeneratedImage gi, String fmt, String originalFormat) {
        if ("original".equalsIgnoreCase(fmt)) {
            return eq(gi.format(), originalFormat);
        }
        return eq(gi.format(), fmt);
    }

    private static boolean eq(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private static String joinWithComma(List<String> parts) {
        return String.join(", ", parts);
    }

    private static String trimRatio(double r) {
        // Round to 2 decimals, but drop trailing zeros for clean "1x", "1.5x", "2x"
        String s = String.format(Locale.ROOT, "%.2f", r);
        s = s.indexOf('.') >= 0 ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s;
    }

    private static String singleFormat(ImageTag it) {
        List<String> formats = it.config().normalizedFormats();
        if (formats == null || formats.size() != 1)
            return null;
        return formats.get(0);
    }

    public static String format(String fmt, String originalFormat) {
        return fmt.equalsIgnoreCase("original") ? originalFormat : fmt;
    }

    // Minimal POJO for <source> rendering
    public static final class Source {
        public final String attrName; // "srcset" or "data-srcset"
        public final String srcset;
        public final String type;

        public Source(String attrName, String srcset, String type) {
            this.attrName = attrName;
            this.srcset = srcset;
            this.type = type;
        }

        public String getAttrName() {
            return attrName;
        }

        public String getSrcset() {
            return srcset;
        }

        public String getType() {
            return type;
        }
    }

}
