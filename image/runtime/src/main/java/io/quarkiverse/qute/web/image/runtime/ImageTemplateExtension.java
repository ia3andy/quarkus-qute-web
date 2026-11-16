package io.quarkiverse.qute.web.image.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkus.qute.TemplateData;
import io.quarkus.qute.TemplateExtension;

@TemplateExtension
public class ImageTemplateExtension {

    public static boolean isDirectUrl(ImageTag it) {
        return it.config().directUrl();
    }

    public static boolean isPicture(ImageTag it) {
        return sources(it).size() > 1;
    }

    /**
     * The url of the {@code <img>} source: the best variant of the preset under the HTTP root path, or the
     * original when the source is passed through or no variant exists.
     */
    public static String imgSrc(ImageTag it) {
        if (it.image().passThrough()) {
            return it.originalUrl();
        }
        return bestVariant(it).map(variant -> it.urlPrefix() + variant.outputPath()).orElse(it.originalUrl());
    }

    /**
     * The largest variant of the preset, in its fallback format when one exists: the one {@code src}, {@code width}
     * and {@code height} come from. Empty for a passed-through source.
     */
    public static Optional<GeneratedImage> bestVariant(ImageTag it) {
        if (it.image().passThrough()) {
            return Optional.empty();
        }
        Set<Integer> widths = effectiveWidths(it);
        List<GeneratedImage> candidates = it.image().generated().stream()
                .filter(gi -> ofPreset(gi, it) && (widths.isEmpty() || widths.contains(gi.width())))
                .toList();
        Comparator<GeneratedImage> byWidth = Comparator.comparingInt(GeneratedImage::width);
        String originalFormat = it.image().info().format();
        return candidates.stream()
                .filter(gi -> matchesFormat(gi, it.config().fallbackFormat(), originalFormat))
                .max(byWidth)
                .or(() -> candidates.stream().max(byWidth));
    }

    /** The widths the preset produces for this source, see {@link ImageUtils#effectiveWidths(List, int)}. */
    private static Set<Integer> effectiveWidths(ImageTag it) {
        return ImageUtils.effectiveWidths(it.config().widths(), it.image().info().width());
    }

    /** Variants are shared by every preset using the image: a preset renders those produced with its settings. */
    private static boolean ofPreset(GeneratedImage gi, ImageTag it) {
        return gi.settings().equals(it.config().variantSettings());
    }

    public static String directUrl(ImageTag it) {
        return imgSrc(it);
    }

    public static String imgSrcset(ImageTag it) {
        String fmt = singleFormat(it);
        if (fmt == null)
            return "";
        return buildSrcset(it, fmt);
    }

    public static boolean hasImgSrcset(ImageTag it) {
        return !imgSrcset(it).isBlank();
    }

    public static List<Source> sources(ImageTag it) {
        List<String> formats = it.config().normalizedFormats();
        if (formats == null || it.image().passThrough())
            return List.of();

        String originalFormat = it.image().info().format();
        List<Source> list = new ArrayList<>();
        for (String fmt : formats) {
            String srcset = buildSrcset(it, fmt);
            if (!srcset.isBlank()) {
                String type = format(fmt, originalFormat);
                list.add(new Source(srcset, type));
            }
        }
        return list;
    }

    private static String buildSrcset(ImageTag it, String fmt) {
        Set<Integer> widths = effectiveWidths(it);
        String originalFormat = it.image().info().format();
        List<GeneratedImage> candidates = it.image().generated().stream()
                .filter(gi -> ofPreset(gi, it) && matchesFormat(gi, fmt, originalFormat))
                .filter(gi -> widths.isEmpty() || widths.contains(gi.width()))
                .sorted(Comparator.comparingInt(GeneratedImage::width))
                .toList();

        if (candidates.isEmpty())
            return "";

        ResolvedPresetConfig.PixelRatioConfig pixelRatio = it.config().pixelRatio();
        if (pixelRatio != null) {
            int base = pixelRatio.baseWidth();
            return joinWithComma(
                    candidates.stream()
                            .map(gi -> it.urlPrefix() + gi.outputPath() + " "
                                    + trimRatio((double) gi.width() / (double) base) + "x")
                            .distinct()
                            .collect(Collectors.toList()));
        } else {
            return joinWithComma(
                    candidates.stream()
                            .map(gi -> it.urlPrefix() + gi.outputPath() + " " + gi.width() + "w")
                            .distinct()
                            .collect(Collectors.toList()));
        }
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
        String s = String.format(Locale.ROOT, "%.2f", r);
        s = s.indexOf('.') >= 0 ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s;
    }

    private static String singleFormat(ImageTag it) {
        List<Source> srcs = sources(it);
        if (srcs.size() != 1)
            return null;
        return srcs.get(0).type;
    }

    public static String format(String fmt, String originalFormat) {
        return fmt.equalsIgnoreCase("original") ? originalFormat : fmt;
    }

    @TemplateData
    public static final class Source {
        public final String srcset;
        public final String type;

        public Source(String srcset, String type) {
            this.srcset = srcset;
            this.type = type;
        }

        public String getSrcset() {
            return srcset;
        }

        public String getType() {
            return type;
        }
    }

}
