package com.seashare.seasharem3.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class CorsPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CorsConfiguration.class);

    private final ApplicationContextRunner profilesRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().getPropertySources()
                    .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(CorsConfiguration.class);

    @Test
    void devUsesConfirmedLocalFrontend() {
        profilesRunner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CorsProperties.class).allowedOrigins())
                    .containsExactly("http://localhost:5173");
        });
    }

    @Test
    void prodDoesNotAuthorizeOriginsWithoutConfiguration() {
        profilesRunner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CorsProperties.class).allowedOrigins()).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void environmentVariableOverridesOriginsInEitherProfile(String profile) {
        profilesRunner.withPropertyValues("spring.profiles.active=" + profile,
                        "CORS_ALLOWED_ORIGINS=https://frontend.example.com, http://localhost:5173")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CorsProperties.class).allowedOrigins())
                            .containsExactly("https://frontend.example.com", "http://localhost:5173");
                });
    }

    @Test
    void bindsCommaSeparatedOrigins() {
        runner.withPropertyValues(
                        "seashare.cors.allowed-origins=http://localhost:5173,https://frontend.example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CorsProperties.class).allowedOrigins())
                            .containsExactly("http://localhost:5173", "https://frontend.example.com");
                });
    }

    @Test
    void missingOriginsDenyBrowserAccessByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CorsProperties.class).allowedOrigins()).isEmpty();
        });
    }

    @Test
    void emptyOriginsAreSupported() {
        runner.withPropertyValues("seashare.cors.allowed-origins=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CorsProperties.class).allowedOrigins()).isEmpty();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "https://*.example.com", "localhost:5173", "ftp://frontend.example.com",
            "https://frontend.example.com/path", "https://frontend.example.com/",
            "https://frontend.example.com?query=1", "https://frontend.example.com#fragment",
            "https://user@frontend.example.com", "http://localhost:99999", "${CORS_ALLOWED_ORIGINS}"})
    void rejectsValuesThatAreNotExactHttpOrigins(String origin) {
        runner.withPropertyValues("seashare.cors.allowed-origins=" + origin)
                .run(context -> assertThat(context).hasFailed());
    }
}
