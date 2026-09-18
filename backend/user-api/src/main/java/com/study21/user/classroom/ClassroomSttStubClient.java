package com.study21.user.classroom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * E2E・検証用のスタブ（`study21.classroom-ai.stub=true` のときだけ使う）。
 *
 * <p>外部ネットワークに依存せずに「STT が文字起こしした」ことにして、画面と DB の流れ
 * （録音 → 分塊 → 転写追記 → トリガー → ノート）を通しで確かめるためのもの。
 * 応答は本番と同じ OpenAI 互換の本文を返す経路を通すため、ここでは**セグメントを固定で返す**。</p>
 *
 * <p>**本番の配備では `stub` を有効にしない**（既定は false）。</p>
 */
@Component
@ConditionalOnProperty(name = "study21.classroom-ai.stub", havingValue = "true")
public class ClassroomSttStubClient implements ClassroomSttClient {

    private static final Logger log = LoggerFactory.getLogger(ClassroomSttStubClient.class);

    /** 固定の書き起こし（日本語。トリガーキーワード「宿題」「試験の重点」を含む）。 */
    private static final String TEXT =
            "今日は比例のグラフについて学びます。比例定数が正のときは右上がりの直線になります。"
                    + "次に、宿題として教科書の練習問題を出します。";

    @Override
    public SttResponse transcribe(SttRequest request) {
        log.info("classroom stt stub call. provider={} model={} language={} audioBytes={}",
                request.provider(), request.model(), request.languageCode(),
                request.audio() == null ? 0 : request.audio().length);
        return SttResponse.success(200, List.of(new Segment(TEXT, null, "ja", null, null)));
    }
}
