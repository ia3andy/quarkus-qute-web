package io.quarkiverse.qute.web.image.deployment.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class QuteVipsImageTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(new Customiser())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Endpoint.class)
                    .addAsResource("roq")
                    .addAsResource("web"));

    public static class MyBuildStep implements BuildStep {

        @Override
        public void execute(BuildContext context) {
            try {
                byte[] bytes = Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream("/roq/index.html").readAllBytes();
                context.produce(TemplatePathBuildItem.builder()
                        .path("index.html")
                        .source(URI.create("target/test-classes/roq/index.html"))
                        .content(new String(bytes, StandardCharsets.UTF_8))
                        .extensionInfo("Roq")
                        .build());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            context.produce(ImagesDirBuildItem.of("web"));
            context.produce(
                    ImagesDirBuildItem.of("roq-public", java.nio.file.Path.of("target/test-classes/roq-public")));
        }
    }

    public static class Customiser implements Consumer<BuildChainBuilder> {

        @Override
        public void accept(BuildChainBuilder buildChainBuilder) {
            buildChainBuilder.addBuildStep(new MyBuildStep())
                    .produces(TemplatePathBuildItem.class)
                    .produces(ImagesDirBuildItem.class)
                    .build();
        }
    }

    @Test
    public void testImageQuteWeb() {

        final String body = RestAssured.given()
                .get("/images.html")
                .then()
                .statusCode(200)
                .log()
                .body()
                .extract().body().asString();

        Document doc = Jsoup.parse(body);
        Elements imgs = doc.select("img");
        assertThat(imgs, hasSize(2));

        String[] urls = {
                "/static/images/generated/1b139664/relative-1920-68cb06dc.jpg",
                "/static/images/generated/1b139664/relative-1024-68cb06dc.jpg",
                "/static/images/generated/1b139664/relative-640-68cb06dc.jpg"
        };

        checkImageSrc(imgs.get(0), urls);

        String[] urls1 = {
                "/static/images/generated/1b139664/white_1920_1080-1920-7840f5f2.jpg",
                "/static/images/generated/1b139664/white_1920_1080-1024-7840f5f2.jpg",
                "/static/images/generated/1b139664/white_1920_1080-640-7840f5f2.jpg"
        };
        checkImageSrc(imgs.get(1), urls1);

        for (String url : urls) {
            RestAssured.given().get(url).then().statusCode(200);
        }

    }

    private static void checkImageSrc(Element img, String[] urls) {
        assertThat(img.attr("src"), is(urls[0]));
        String srcset = img.attr("srcset");
        for (String entry : urls) {
            assertThat(srcset, containsString(entry));
        }
    }

    @Test
    public void testImageRunTime() {
        RestAssured.given()
                .get("/rest")
                .then()
                .statusCode(200)
                .log().body();
    }

    @Path("/rest")
    public static class Endpoint {
        @Inject
        @Location("index.html")
        Template index;

        @GET
        public String get() {
            return index.instance().render();
        }
    }
}
