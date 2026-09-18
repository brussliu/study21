package com.study21.admin.geometryai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 呼び出しの実装の選び方（{@link GeometryAiClientConfiguration}）。
 *
 * <p>`study21.geometry-ai.client` で LangChain4j（既定）と従来の HttpClient を切り替え、
 * `study21.geometry-ai.stub=true` のときは**どちらの実呼び出し実装も作らない**
 * （E2E が外部ネットワークに依存しないための約束。設計 §9.4）。</p>
 */
class GeometryAiClientConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GeometryAiClientConfiguration.class);

    @Test
    void usesLangChain4jByDefault() {
        runner.run(context -> assertThat(context.getBean(GeometryAiClient.class))
                .isInstanceOf(GeometryAiLangChain4jClient.class));
    }

    @Test
    void usesLangChain4jWhenConfigured() {
        runner.withPropertyValues("study21.geometry-ai.client=langchain4j")
                .run(context -> assertThat(context.getBean(GeometryAiClient.class))
                        .isInstanceOf(GeometryAiLangChain4jClient.class));
    }

    @Test
    void fallsBackToTheJdkHttpClientWhenConfigured() {
        runner.withPropertyValues("study21.geometry-ai.client=http")
                .run(context -> assertThat(context.getBean(GeometryAiClient.class))
                        .isInstanceOf(GeometryAiHttpClient.class));
    }

    @Test
    void createsNoRealClientInStubMode() {
        // E2E・検証では固定応答のスタブだけが使われる（実呼び出しの実装は作られない）
        runner.withUserConfiguration(GeometryAiStubClient.class)
                .withPropertyValues("study21.geometry-ai.stub=true")
                .run(context -> {
                    assertThat(context.getBeansOfType(GeometryAiClient.class)).hasSize(1);
                    assertThat(context.getBean(GeometryAiClient.class)).isInstanceOf(GeometryAiStubClient.class);
                });
    }

    @Test
    void createsExactlyOneClient() {
        runner.run(context -> assertThat(context.getBeansOfType(GeometryAiClient.class)).hasSize(1));
    }
}
