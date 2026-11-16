package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A glob match in an images dir served under a url prefix is recorded under its url, the same key a template
 * lookup uses, so it is found at render time.
 */
public class QuteImageGlobPrefixTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> context.produce(ImagesDirBuildItem.of("web/static", "assets")))
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new File("src/test/resources/web/static/images"), "web/static/images"));

    @Inject
    Images images;

    @Test
    public void testGlobMatchIsKeyedByItsUrl() {
        ImageTag tag = images.get(null, "/assets/images/white_1920_1080.png", "small");
        assertNotNull(tag, "glob match not found under its url");
        assertEquals("/foo/bar/assets/images/white_1920_1080.png", tag.originalUrl());
    }
}
