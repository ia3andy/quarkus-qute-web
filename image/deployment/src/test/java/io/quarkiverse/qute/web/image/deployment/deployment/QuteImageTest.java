package io.quarkiverse.qute.web.image.deployment.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

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
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class QuteImageTest {

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
            context.produce(ImagesDirBuildItem.resource("/web"));
            AtomicReference<java.nio.file.Path> root = new AtomicReference<>();
            QuarkusClassLoader.visitRuntimeResources("web/static/images/white_1920_1080.png", p -> {
                root.set(p.getRoot());
            });
            context.produce(
                    ImagesDirBuildItem.localDir(java.nio.file.Path.of("target/test-classes/roq-public")));
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
    public void testDefaultAttributes() {
        Document doc = fetchAndParse("/images.html");
        Elements imgs = doc.select("img");
        assertThat("should have 4 img elements", imgs, hasSize(4));

        Element defaultImg = imgs.get(0);
        assertThat(defaultImg.attr("loading"), is("lazy"));
        assertThat(defaultImg.attr("sizes"), is("auto"));
        assertThat(defaultImg.attr("width"), is(not("")));
        assertThat(defaultImg.attr("height"), is(not("")));
        assertThat(Integer.parseInt(defaultImg.attr("width")), greaterThan(0));
        assertThat(Integer.parseInt(defaultImg.attr("height")), greaterThan(0));
    }

    @Test
    public void testSrcsetGeneration() {
        Document doc = fetchAndParse("/images.html");
        Elements imgs = doc.select("img");

        Element img = imgs.get(0);
        assertThat(img.attr("src"), containsString("/static/images/generated/"));
        assertThat(img.attr("src"), containsString("-1920-"));

        String srcset = img.attr("srcset");
        assertThat(srcset, containsString("640w"));
        assertThat(srcset, containsString("1024w"));
        assertThat(srcset, containsString("1920w"));
    }

    @Test
    public void testCustomPreset() {
        Document doc = fetchAndParse("/images.html");
        Elements imgs = doc.select("img");

        Element smallImg = imgs.get(2);
        String srcset = smallImg.attr("srcset");
        assertThat("small preset should have 320w", srcset, containsString("320w"));
        assertThat("small preset should have 640w", srcset, containsString("640w"));
        assertThat("small preset should NOT have 1024w", srcset, not(containsString("1024w")));
        assertThat("small preset should NOT have 1920w", srcset, not(containsString("1920w")));
    }

    @Test
    public void testTagLevelAttributes() {
        Document doc = fetchAndParse("/images.html");
        Elements imgs = doc.select("img");

        Element withAttrs = imgs.get(2);
        assertThat(withAttrs.attr("alt"), is("Small image"));
        assertThat(withAttrs.attr("class"), is("thumb"));
    }

    @Test
    public void testLoadingOverride() {
        Document doc = fetchAndParse("/images.html");
        Elements imgs = doc.select("img");

        Element eagerImg = imgs.get(3);
        assertThat("loading should be overridden to eager", eagerImg.attr("loading"), is("eager"));
    }

    @Test
    public void testGeneratedImagesAccessible() {
        Document doc = fetchAndParse("/images.html");
        Elements imgs = doc.select("img");

        for (Element img : imgs) {
            String src = img.attr("src");
            if (src.contains("/static/images/generated/")) {
                RestAssured.given().get(src).then().statusCode(200);
            }
        }
    }

    @Test
    public void testGeneratedImageDimensions() throws IOException {
        Document doc = fetchAndParse("/images.html");
        Element img = doc.select("img").get(1);

        String srcset = img.attr("srcset");
        String smallUrl = extractUrlForWidth(srcset, "640w");
        assertThat("should find 640w URL in srcset", smallUrl, is(not("")));

        BufferedImage generated = fetchImage(smallUrl);
        assertThat("generated image width should be 640", generated.getWidth(), is(640));
        assertThat("generated image height should be proportional", generated.getHeight(), greaterThan(0));
    }

    @Test
    public void testImageRunTime() {
        RestAssured.given()
                .get("/rest")
                .then()
                .statusCode(200);
    }

    private Document fetchAndParse(String path) {
        String body = RestAssured.given()
                .get(path)
                .then()
                .statusCode(200)
                .extract().body().asString();
        return Jsoup.parse(body);
    }

    private String extractUrlForWidth(String srcset, String widthDescriptor) {
        for (String entry : srcset.split(",")) {
            entry = entry.trim();
            if (entry.endsWith(widthDescriptor)) {
                return entry.substring(0, entry.length() - widthDescriptor.length()).trim();
            }
        }
        return "";
    }

    private BufferedImage fetchImage(String url) throws IOException {
        try (InputStream is = RestAssured.given().get(url).then().statusCode(200)
                .extract().body().asInputStream()) {
            return ImageIO.read(is);
        }
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
