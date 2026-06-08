package io.quarkiverse.qute.web.image.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.qute.image")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface ImageConfig {

    /**
     * Preset definitions.
     * If empty, the default configuration is used.
     */
    Map<String, PresetConfig> presets();

}
