package io.quarkiverse.qute.web.image.deployment.converter;

import io.quarkiverse.qute.web.image.deployment.items.ImageConverterBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import org.jboss.logging.Logger;

import java.util.List;

public class VipsProcessor {

    private static final Logger LOGGER = Logger.getLogger(VipsProcessor.class);

    @BuildStep
    ImageConverterBuildItem initImageConverter(List<QuteImageTemplateToScanBuildItem> templateToScan) {
        if (templateToScan.isEmpty()) {
            return null;
        }
        return new ImageConverterBuildItem(new VipsConverter());
    }

}
