package com.study21.admin.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * 実 DB に対する端末モードの一括切替と「手動操作」の判定。
 *
 * <p>利用者の指摘: 手動操作の保護は<strong>更新の出所で区別</strong>する必要がある。
 * このバッチ（batR03 / batR04）自身の更新を手動操作と数えると、前回の実行で更新された時刻が
 * 計画時刻より後になり、<strong>次の切替をいつまでも見送ってしまう</strong>
 * （＝ネット状態が切り替わらない）。</p>
 *
 * <p>テストは {@code @Transactional} でロールバックするので実データは汚れない
 * （端末モードも元に戻る）。DB アクセスは MyBatis の Mapper 経由のみ（SQL ログの対象）。</p>
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class NetTerminalModeMapperTest {

    @Autowired
    private NetTerminalModeMapper terminalMapper;

    /**
     * このバッチ自身の切替は「手動操作」に数えない。
     *
     * <p>切替を実行すると端末の更新日時は「いま」になるが、{@link NetTerminalModeMapper#findLatestManualUpdatedAt()}
     * は {@code 更新元コード = 'batR03' / 'batR04'} の行を除くので、値は変わらない。</p>
     */
    @Test
    void batchOwnUpdateIsNotCountedAsAManualChange() {
        LocalDateTime manualBefore = terminalMapper.findLatestManualUpdatedAt();

        // いったん T に寄せてから S へ切り替える（必ず「更新される行」を作る）
        terminalMapper.updateAllTerminalModes("T", "batR03");
        int switched = terminalMapper.updateAllTerminalModes("S", "batR04");
        assumeThat(switched).as("有効な端末が無い環境では検証できない").isPositive();
        // 切替が実際に効いていること（＝更新日時が動く条件が成立していること）
        assertThat(terminalMapper.countActiveTerminalsWithMode("S"))
                .isEqualTo(terminalMapper.countActiveTerminals());

        // 切替は「バッチ自身の更新」なので、利用者の最終更新は**新しくならない**
        // （除外が無いと切替時刻＝いまになる。それを見落とすと次の切替をいつまでも見送る）
        LocalDateTime manualAfter = terminalMapper.findLatestManualUpdatedAt();
        assertThat(manualAfter == null || (manualBefore != null && !manualAfter.isAfter(manualBefore)))
                .as("バッチ自身の切替を手動操作として数えている（before=%s after=%s）", manualBefore, manualAfter)
                .isTrue();
    }

    /** 既に同じモードの端末は更新しない（更新日時を無駄に動かさない＝手動操作の判定に影響させない）。 */
    @Test
    void switchingToTheSameModeDoesNotTouchTheRows() {
        terminalMapper.updateAllTerminalModes("T", "batR03");
        assertThat(terminalMapper.updateAllTerminalModes("T", "batR03")).isZero();
    }

    /** 端末数の集計は有効な端末（状態='1'）を数える。 */
    @Test
    void terminalCountsAreConsistent() {
        int all = terminalMapper.countActiveTerminals();
        assertThat(all).isGreaterThanOrEqualTo(0);
        assertThat(terminalMapper.countActiveTerminalsWithMode("S")).isLessThanOrEqualTo(all);
        assertThat(terminalMapper.countActiveTerminalsWithMode("T")).isLessThanOrEqualTo(all);
    }
}
