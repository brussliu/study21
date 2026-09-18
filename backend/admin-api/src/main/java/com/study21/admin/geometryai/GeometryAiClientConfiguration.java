package com.study21.admin.geometryai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI（LLM）を呼ぶ {@link GeometryAiClient} の実装を 1 つだけ決める。
 *
 * <ul>
 *   <li>`study21.geometry-ai.stub=true`（E2E・検証）… この構成は何も作らない。
 *       {@link GeometryAiStubClient}（固定応答）だけが使われる</li>
 *   <li>`study21.geometry-ai.client=langchain4j`（**既定**）… {@link GeometryAiLangChain4jClient}</li>
 *   <li>`study21.geometry-ai.client=http` … 従来の {@link GeometryAiHttpClient}
 *       （切り戻し用。設定を変えて再起動するだけで戻せる）</li>
 * </ul>
 *
 * <p>どの場合も {@code GeometryAiClient} の Bean は 1 つだけにする（工程は実装を選ばない）。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "study21.geometry-ai.stub", havingValue = "false", matchIfMissing = true)
public class GeometryAiClientConfiguration {

    /** AI 呼び出しの既定（LangChain4j）。 */
    @Bean
    @ConditionalOnProperty(name = "study21.geometry-ai.client", havingValue = "langchain4j", matchIfMissing = true)
    GeometryAiClient geometryAiClient() {
        return new GeometryAiLangChain4jClient();
    }

    /** 従来実装への切り戻し（`study21.geometry-ai.client=http`）。 */
    @Bean
    @ConditionalOnProperty(name = "study21.geometry-ai.client", havingValue = "http")
    GeometryAiClient geometryAiHttpClient(
            @Value("${study21.geometry-ai.connect-timeout-seconds:10}") int connectTimeoutSeconds) {
        return new GeometryAiHttpClient(connectTimeoutSeconds);
    }
}
