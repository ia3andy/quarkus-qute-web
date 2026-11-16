package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * An images dir given by its absolute filesystem path is scanned: the scanner indexes local dirs relative to the
 * project root, so the scope must be relative too.
 */
public class QuteImageLocalDirTest {

    static final Path DIR = Path.of("target/test-classes/web/static").toAbsolutePath();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> context.produce(ImagesDirBuildItem.of("local", DIR)))
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    Images images;

    @Test
    public void testAbsoluteLocalDirIsScanned() {
        assertNotNull(images.get(null, "/local/images/white_1920_1080.png", "small"), "local dir not scanned");
    }
}
