package io.quarkiverse.qute.web.image.runtime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;

import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkus.qute.Engine;
import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.Expression;
import io.quarkus.qute.ResultNode;
import io.quarkus.qute.Scope;
import io.quarkus.qute.SectionHelper;
import io.quarkus.qute.SectionHelperFactory;

@EngineConfiguration
public class ImageSectionHelperFactory implements SectionHelperFactory<SectionHelper> {

    @Inject
    Images images;

    // Used by CDI for runtime and build-time validation (of runtime templates)
    public ImageSectionHelperFactory() {
        images = null;
    }

    // Used for build-time templates
    public ImageSectionHelperFactory(Images images) {
        this.images = images;
    }

    @Override
    public List<String> getDefaultAliases() {
        return List.of("image");
    }

    @Override
    public ParametersInfo getParameters() {
        return ParametersInfo.builder()
                .addParameter("src")
                .addParameter("preset", "default")
                .build();
    }

    @Override
    public Scope initializeBlock(Scope outerScope, BlockInfo block) {
        if (!block.getLabel().equals("$main")) {
            return outerScope;
        } else {
            for (Map.Entry<String, String> entry : block.getParameters().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                block.addExpression(key, value);
            }

            return outerScope;
        }
    }

    @Override
    public SectionHelper initialize(SectionInitContext context) {
        Map<String, Expression> expressions = Map.of(
                "src", context.getExpression("src"));
        final Engine engine = context.getEngine();
        return new SectionHelper() {
            @Override
            public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
                return context.evaluate(expressions)
                        .thenCompose(resolved -> {
                            ImageTag imageTag = images.get(context.resolutionContext().getTemplate().getId(),
                                    (String) resolved.get("src"));
                            return engine.parse("{#include image.html /}").getRootNode()
                                    .resolve(context.newResolutionContext(Map.of("image", imageTag), null));
                        });
            }
        };
    }
}
