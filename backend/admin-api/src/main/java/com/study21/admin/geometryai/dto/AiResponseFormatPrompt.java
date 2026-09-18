package com.study21.admin.geometryai.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI へ渡す**システムプロンプトへ「出力形式」を足す**（DTO から生成した JSON Schema）。
 *
 * <p>出力データ構造の定義は DTO が唯一なので、プロンプト側に JSON を手書きしない。
 * ここで足す文は {@link AiResponseSchemaService} が DTO から生成したもの。
 * DTO を直せば、次の実行からそのままプロンプトへ反映される（画面の Data TAB も同じ生成結果を見る）。</p>
 *
 * <p>DB の設定（システムプロンプト）はそのまま残す。設定に「コマンド列だけを出力する」等の
 * 形式の指定が昔から入っていても、足した節が優先されるように文面で明示している。</p>
 */
@Component
public class AiResponseFormatPrompt {

    private static final Logger log = LoggerFactory.getLogger(AiResponseFormatPrompt.class);

    private final AiResponseSchemaService schemaService;

    public AiResponseFormatPrompt(AiResponseSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /**
     * システムプロンプトの後ろに「出力形式（JSON Schema）」を足す。
     *
     * <p>バッチコードが {@link AiResponseDtos} に未登録のときは何も足さない（今までどおり動かす）。</p>
     */
    public String appendTo(String systemPrompt, String taskCode) {
        String section = schemaSectionOf(taskCode);
        if (section == null) {
            log.warn("AI 出力 DTO が未登録のため出力形式を足しません。taskCode={}", taskCode);
            return systemPrompt;
        }
        String base = systemPrompt == null ? "" : systemPrompt.stripTrailing();
        return base.isEmpty() ? section : base + "\n\n" + section;
    }

    /**
     * 「出力形式（JSON Schema）」の節**だけ**を返す（null は DTO 未登録）。
     *
     * <p>設定のプロンプトに {@code {outputSchema}} と書いてあるときは、その変数へこの本文を入れる。
     * Schema は DTO から生成するので、**テンプレート側で JSON を二重に持たなくてよい**。</p>
     */
    public String schemaSectionOf(String taskCode) {
        return AiResponseDtos.dtoOf(taskCode)
                .map(schemaService::promptSectionOf)
                .orElse(null);
    }
}
