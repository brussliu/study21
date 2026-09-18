package com.study21.admin.geometryai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.geometryai.AiResponseDtoParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A〜D の作図モード・結果種別・出力 DTO の契約（**DTO が AI 出力構造の唯一の定義**）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>モード（A〜D）と結果種別（AUTO/GEOMETRY/GRAPH/MIXED）の語彙と、バッチコード（batC51-A〜D）</li>
 *   <li>4 つの DTO が {@link AiResponseDtos} に登録され、**共通項目**とモード固有項目を schema に持つ</li>
 *   <li>AI の JSON（日本語キー・英語エイリアス）を DTO へ読み込める</li>
 *   <li>結果種別 → 作図種別（GeoGebra の appName）の対応</li>
 * </ol>
 */
class FigureOutputDtosTest {

    private static String schemaOf(Class<?> dtoClass) {
        return new AiResponseSchemaService(new ObjectMapper()).schemaJsonOf(dtoClass);
    }

    /** すべてのモード DTO が持つ共通項目（設計 §7 の公共字段）。 */
    private static final List<String> COMMON_FIELDS = List.of(
            "判定", "要求図種", "確定図種", "タイトル", "説明",
            "明確条件", "導出結果", "近似仮定", "警告", "質問", "検証目標", "作図オブジェクト", "コマンド");

    private static final List<Class<?>> MODE_DTOS = List.of(
            BatC51AResultDto.class, BatC51BResultDto.class, BatC51CResultDto.class, BatC51DResultDto.class);

    // ------------------------------------------------------------------ 語彙

    @Test
    @DisplayName("モードは A〜D の 4 つで、それぞれバッチコード batC51-A〜D を持つ")
    void modeVocabulary() {
        assertThat(FigureMode.values()).containsExactly(
                FigureMode.A, FigureMode.B, FigureMode.C, FigureMode.D);
        assertThat(FigureMode.A.taskCode()).isEqualTo("batC51-A");
        assertThat(FigureMode.B.taskCode()).isEqualTo("batC51-B");
        assertThat(FigureMode.C.taskCode()).isEqualTo("batC51-C");
        assertThat(FigureMode.D.taskCode()).isEqualTo("batC51-D");
        assertThat(FigureMode.A.label()).isEqualTo("画像をもとに再現");
        assertThat(FigureMode.B.label()).isEqualTo("数式からグラフを作成");
        assertThat(FigureMode.C.label()).isEqualTo("文章の条件から作図");
        assertThat(FigureMode.D.label()).isEqualTo("文章と図を合わせて作図");
        // 結果種別を選ばせるモード（A・C・D）と、GRAPH に固定するモード（B）を区別する
        assertThat(FigureMode.A.asksOutputType()).isTrue();
        assertThat(FigureMode.B.asksOutputType()).isFalse();
        assertThat(FigureMode.C.asksOutputType()).isTrue();
        assertThat(FigureMode.D.asksOutputType()).isTrue();
        assertThat(FigureMode.B.fixedOutputType()).contains(FigureOutputType.GRAPH);
        assertThat(FigureMode.A.fixedOutputType()).isEmpty();
        assertThat(FigureMode.C.fixedOutputType()).isEmpty();
        assertThat(FigureMode.D.fixedOutputType()).isEmpty();
    }

    @Test
    @DisplayName("モードは画面の値（A〜D・小文字）とバッチコード（batC51 系）の両方から解決できる")
    void modeResolution() {
        assertThat(FigureMode.of("A")).contains(FigureMode.A);
        assertThat(FigureMode.of("b")).contains(FigureMode.B);
        assertThat(FigureMode.of(" batC51-C ")).contains(FigureMode.C);
        assertThat(FigureMode.of("batC51-D")).contains(FigureMode.D);
        // 歴史的な batC51（モードが無い要求）は A として扱う（利用者の指示）
        assertThat(FigureMode.of("batC51")).contains(FigureMode.A);
        assertThat(FigureMode.of(null)).isEmpty();
        assertThat(FigureMode.of("X")).isEmpty();
    }

    @Test
    @DisplayName("結果種別は AUTO が既定で、AI が確定できるのは GEOMETRY / GRAPH / MIXED だけ")
    void outputTypeVocabulary() {
        assertThat(FigureOutputType.defaultType()).isEqualTo(FigureOutputType.AUTO);
        assertThat(FigureOutputType.AUTO.isResolved()).isFalse();
        assertThat(FigureOutputType.GEOMETRY.isResolved()).isTrue();
        assertThat(FigureOutputType.of("graph")).contains(FigureOutputType.GRAPH);
        assertThat(FigureOutputType.of("MIXED")).contains(FigureOutputType.MIXED);
        assertThat(FigureOutputType.of("AUTO")).contains(FigureOutputType.AUTO);
        assertThat(FigureOutputType.of("unknown")).isEmpty();
    }

    @Test
    @DisplayName("結果種別は GeoGebra の作図種別（geometry / function）へ写せる")
    void outputTypeToFigureType() {
        assertThat(FigureOutputType.GEOMETRY.figureType()).isEqualTo("geometry");
        assertThat(FigureOutputType.GRAPH.figureType()).isEqualTo("function");
        // 混在はグラフも図形も出せる Graphing にする（Geometry では関数を描けない）
        assertThat(FigureOutputType.MIXED.figureType()).isEqualTo("function");
    }

    @Test
    @DisplayName("判定（outcome）は GENERATABLE / NEEDS_INPUT / UNSUPPORTED")
    void outcomeVocabulary() {
        assertThat(FigureOutcome.values()).containsExactly(
                FigureOutcome.GENERATABLE, FigureOutcome.NEEDS_INPUT, FigureOutcome.UNSUPPORTED);
        assertThat(FigureOutcome.GENERATABLE.needsCommands()).isTrue();
        assertThat(FigureOutcome.NEEDS_INPUT.needsCommands()).isFalse();
        assertThat(FigureOutcome.UNSUPPORTED.needsCommands()).isFalse();
        assertThat(FigureOutcome.of("needs_input")).contains(FigureOutcome.NEEDS_INPUT);
    }

    // ------------------------------------------------------------------ 登録

    @Test
    @DisplayName("4 つのモード DTO がバッチコードで登録され、歴史的な batC51 も残る")
    void dtoRegistration() {
        assertThat(AiResponseDtos.dtoOf("batC51-A")).contains(BatC51AResultDto.class);
        assertThat(AiResponseDtos.dtoOf("batC51-B")).contains(BatC51BResultDto.class);
        assertThat(AiResponseDtos.dtoOf("batC51-C")).contains(BatC51CResultDto.class);
        assertThat(AiResponseDtos.dtoOf("batC51-D")).contains(BatC51DResultDto.class);
        // 歴史的な要求・履歴（batC51）は今までどおり読める
        assertThat(AiResponseDtos.dtoOf("batC51")).contains(BatC51ResultDto.class);
        assertThat(AiResponseDtos.dtoOf("batC52")).contains(BatC52ResultDto.class);
        for (FigureMode mode : FigureMode.values()) {
            assertThat(AiResponseDtos.dtoOf(mode.taskCode())).contains(mode.dtoClass());
        }
    }

    // ------------------------------------------------------------------ schema

    @Test
    @DisplayName("どのモードの schema にも共通項目が出る（公共字段は 1 か所で定義する）")
    void schemaHasCommonFields() {
        for (Class<?> dtoClass : MODE_DTOS) {
            String schema = schemaOf(dtoClass);
            for (String field : COMMON_FIELDS) {
                assertThat(schema).as("%s の %s", dtoClass.getSimpleName(), field).contains("\"" + field + "\"");
            }
        }
    }

    @Test
    @DisplayName("モード固有の項目が schema に出る（A・C・D を幾何だけに閉じない）")
    void schemaHasModeSpecificFields() {
        assertThat(schemaOf(BatC51AResultDto.class))
                .contains("画像オブジェクト", "ラベルと配置", "幾何関係", "曲線と軸", "既知の式と点", "近似箇所");
        assertThat(schemaOf(BatC51BResultDto.class))
                .contains("元の式", "式", "変数とパラメータ", "定義域", "表示範囲", "式の曖昧さ");
        assertThat(schemaOf(BatC51CResultDto.class))
                .contains("問題文", "既知条件", "求める内容", "作図目標", "条件不足", "矛盾", "作図手順");
        assertThat(schemaOf(BatC51DResultDto.class))
                .contains("文字条件", "図の情報", "対応関係", "既存オブジェクト", "追加変更", "衝突", "作図手順");
    }

    @Test
    @DisplayName("結果種別と判定の取りうる値が schema の enum に出る")
    void schemaEnumeratesVocabulary() {
        String schema = schemaOf(BatC51AResultDto.class);
        assertThat(schema).contains("AUTO", "GEOMETRY", "GRAPH", "MIXED");
        assertThat(schema).contains("GENERATABLE", "NEEDS_INPUT", "UNSUPPORTED");
    }

    @Test
    @DisplayName("関数・方程式の図にも使う共通構造（作図オブジェクト・曲線・定義域）が schema に出る")
    void schemaHasMathStructures() {
        String schema = schemaOf(BatC51AResultDto.class);
        assertThat(schema).contains("作図オブジェクト", "種類", "定義", "出典", "精度");
        // 出典は「元の図の情報／文字／必要推导／近似示意」を区別できる値を持つ
        assertThat(schema).contains("FROM_IMAGE", "FROM_TEXT", "DERIVED", "ASSUMED");
        assertThat(schema).contains("EXACT", "APPROXIMATE");
        // 数学オブジェクトは幾何だけではない
        assertThat(schema).contains("FUNCTION", "CURVE", "CONIC", "POLYGON", "CIRCLE");
    }

    // ------------------------------------------------------------------ 読み込み

    @Test
    @DisplayName("AI の JSON（日本語キー・入れ子・リスト）を DTO へ読み込める")
    void parsesJapaneseJson() {
        String json = """
                {
                  "判定": "NEEDS_INPUT",
                  "要求図種": "GRAPH",
                  "確定図種": "GRAPH",
                  "タイトル": "放物線 y = x^2 - 2x",
                  "説明": "式から放物線を描きます。",
                  "明確条件": [{"内容": "y = x^2 - 2x", "出典": "FROM_TEXT", "状態": "GIVEN", "根拠": "問題文"}],
                  "導出結果": [{"内容": "頂点", "値": "(1, -1)", "出典": "DERIVED", "精度": "EXACT"}],
                  "近似仮定": [{"内容": "範囲外は描かない", "理由": "表示範囲の指定なし", "影響": "見た目のみ"}],
                  "警告": ["式が 1 つに定まりません"],
                  "質問": [{"ID": "q1", "質問": "どちらの式ですか？", "選択肢": ["x^2", "2x^2"], "回答の形": "CHOICE"}],
                  "検証目標": [{"対象": "f", "種類": "DOMAIN", "期待": "すべての実数", "許容": "0"}],
                  "作図オブジェクト": [{"名前": "f", "種類": "FUNCTION", "定義": "f(x) = x^2 - 2x",
                       "出典": "FROM_TEXT", "精度": "EXACT", "根拠": "問題文", "依存": []}],
                  "コマンド": ["f(x) = x^2 - 2*x"],
                  "元の式": ["y = x^2 - 2x"],
                  "式": [{"元の式": "y = x^2 - 2x", "正規化": "f(x) = x^2 - 2*x", "種類": "FUNCTION", "変数": ["x"]}],
                  "変数とパラメータ": [{"名前": "a", "種類": "PARAMETER", "値": "1", "制約": "a > 0"}],
                  "定義域": [{"対象": "f", "下限": "-inf", "上限": "inf", "条件": "すべての実数", "備考": ""}],
                  "表示範囲": {"X最小": "-5", "X最大": "5", "Y最小": "-5", "Y最大": "5",
                       "軸の刻み": "1", "グリッドの刻み": "1", "自動": true},
                  "式の曖昧さ": [{"箇所": "x^2", "解釈": ["x の 2 乗"], "採用": "x の 2 乗", "理由": "問題文の表記"}]
                }
                """;
        BatC51BResultDto dto = AiResponseDtoParser.parseContent(json, BatC51BResultDto.class).orElseThrow();

        assertThat(dto.getOutcome()).isEqualTo(FigureOutcome.NEEDS_INPUT);
        assertThat(dto.getRequestedOutputType()).isEqualTo(FigureOutputType.GRAPH);
        assertThat(dto.getResolvedOutputType()).isEqualTo(FigureOutputType.GRAPH);
        assertThat(dto.getTitle()).isEqualTo("放物線 y = x^2 - 2x");
        assertThat(dto.getConditions()).hasSize(1);
        assertThat(dto.getConditions().get(0).source()).isEqualTo(FigureParts.Source.FROM_TEXT);
        assertThat(dto.getQuestions()).hasSize(1);
        assertThat(dto.getQuestions().get(0).options()).containsExactly("x^2", "2x^2");
        assertThat(dto.getObjects()).hasSize(1);
        assertThat(dto.getObjects().get(0).kind()).isEqualTo(FigureParts.ObjectKind.FUNCTION);
        assertThat(dto.getDomains()).hasSize(1);
        assertThat(dto.getView().automatic()).isTrue();
        assertThat(dto.getAmbiguities()).hasSize(1);
        assertThat(dto.getCommands()).containsExactly("f(x) = x^2 - 2*x");
    }

    @Test
    @DisplayName("英語キー（outcome / requestedOutputType など）でも同じ DTO へ読み込める")
    void parsesEnglishAliases() {
        String json = """
                {"outcome":"GENERATABLE","requestedOutputType":"AUTO","resolvedOutputType":"GEOMETRY",
                 "title":"三角形ABC","description":"","commands":["A = (0, 0)","B = (4, 0)","C = (0, 3)",
                 "Polygon(A, B, C)"]}
                """;
        BatC51AResultDto dto = AiResponseDtoParser.parseContent(json, BatC51AResultDto.class).orElseThrow();
        assertThat(dto.getOutcome()).isEqualTo(FigureOutcome.GENERATABLE);
        assertThat(dto.getRequestedOutputType()).isEqualTo(FigureOutputType.AUTO);
        assertThat(dto.getResolvedOutputType()).isEqualTo(FigureOutputType.GEOMETRY);
        assertThat(dto.getCommands()).hasSize(4);
    }

    @Test
    @DisplayName("知らない値・知らないキーがあっても落ちない（コマンドまで捨てない）")
    void tolerantParsing() {
        String json = """
                {"判定":"SOMETHING_NEW","要求図種":"GEOMETRY","未知のキー":123,
                 "コマンド":["A = (0, 0)"],"作図オブジェクト":[{"名前":"A","種類":"NEW_KIND"}]}
                """;
        BatC51AResultDto dto = AiResponseDtoParser.parseContent(json, BatC51AResultDto.class).orElseThrow();
        assertThat(dto.getOutcome()).isNull();
        assertThat(dto.getCommands()).containsExactly("A = (0, 0)");
        assertThat(dto.getObjects()).hasSize(1);
        assertThat(dto.getObjects().get(0).kind()).isNull();
        assertThat(dto.getObjects().get(0).name()).isEqualTo("A");
    }
}
