package com.study21.admin.schedule;

import com.study21.admin.setting.SettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **設定の読み込みが 1 つのスナップショットで行われる**こと（実 DB）。
 *
 * <p>設定値（COM_設定情報）・有効／無効（BAT_バッチコントロール情報）・適用時刻と計画バージョン
 * （BAT_スケジュール状態情報）は別の表にある。読み込みの途中で設定の保存（コミット）が入ると、
 * 既定の {@code READ COMMITTED} では**文ごと**にスナップショットを取るため
 * 「設定値は新しい・適用時刻は古い」という**混ざった版**を読んでしまいうる。</p>
 *
 * <p>このテストは、読み込みの**1 回目のクエリのあと**に別トランザクションで設定を保存（コミット）し、
 * そのあとの読み取りが**古い版のまま**であること（途中で混ざらないこと）を確かめる。
 * 同時に、読み込みのトランザクションの中で {@code REPEATABLE READ} が効いていることも見る
 * （本番と同じ DB・同じ Bean の分離規則で確かめる。モックの戻り値では確かめない）。</p>
 *
 * <p>テストは自分が書いた値を最後に元へ戻す（実データは変えない）。</p>
 */
@SpringBootTest
@ActiveProfiles("testdb")
@EnabledIfEnvironmentVariable(named = "STUDY21_TEST_DATASOURCE_URL", matches = ".+",
        disabledReason = "専用のテスト DB（STUDY21_TEST_DATASOURCE_URL）が未設定のためスキップします"
                + "（tmp/tools/study21-batchtestdb.sh start で用意できます）")
class ScheduleConfigSnapshotConsistencyTest {

    private static final String END_TIME_FIELD = "netControlEndTime";
    private static final String END_TIME_KEY = "NET_CONTROL_END_TIME";
    private static final String TASK = "batR03";

    /** 設定の保存を読み取りの途中に挟むための仕掛け。 */
    private static final CountDownLatch FIRST_QUERY_DONE = new CountDownLatch(1);
    private static final CountDownLatch WRITE_COMMITTED = new CountDownLatch(1);
    private static final AtomicReference<Integer> ISOLATION_IN_LOAD = new AtomicReference<>();
    private static final AtomicReference<Boolean> TRANSACTION_ACTIVE_IN_LOAD = new AtomicReference<>();
    private static final AtomicBoolean HOOK_ONCE = new AtomicBoolean();

    @TestConfiguration
    static class HookingConfig {
        /** 本物の Mapper を包んで、1 回目のクエリのあとに待ち合わせる（読み取りの途中で保存を挟む）。 */
        @Bean
        @Primary
        ScheduleConfigMapper hookingConfigMapper(ScheduleConfigMapper delegate) {
            return new ScheduleConfigMapper() {
                @Override
                public List<Map<String, Object>> findSettingValues(List<String> pageCodes, List<String> settingKeys) {
                    TRANSACTION_ACTIVE_IN_LOAD.set(TransactionSynchronizationManager.isActualTransactionActive());
                    ISOLATION_IN_LOAD.set(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
                    List<Map<String, Object>> rows = delegate.findSettingValues(pageCodes, settingKeys);
                    if (HOOK_ONCE.compareAndSet(false, true)) {
                        FIRST_QUERY_DONE.countDown();   // 1 回目のクエリが終わった
                        try {
                            WRITE_COMMITTED.await(10, TimeUnit.SECONDS);   // 保存のコミットを待つ
                        } catch (InterruptedException cause) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return rows;
                }

                @Override
                public List<Map<String, Object>> findControlStatuses(List<String> taskCodes) {
                    return delegate.findControlStatuses(taskCodes);
                }
            };
        }
    }

    /** 本番のローダ（{@code @Transactional(readOnly = true, REPEATABLE_READ)}）をそのまま使う。 */
    @Autowired
    private ScheduleConfigLoader loader;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private SchedulePlanMapper planMapper;

    @Test
    @DisplayName("読み込みの途中で設定が保存されても、混ざった版を読まない（1 つのスナップショット）")
    void loadReadsOneConsistentSnapshot() throws Exception {
        // 専用テスト DB には計画行が無いことがあるので、この検証に必要な 1 行を自分で用意する
        // （「設定値の保存と一緒に適用時刻と計画バージョンが進む」ことを見るため）
        if (planMapper.findPlan(TASK) == null) {
            planMapper.claim(TASK, "2026-09-01T00:00:00");
        }
        // 適用時刻が未設定だと「混ざっていない」ことを比べられないので、既知の古い値を 1 度入れる
        planMapper.markConfigEffectiveFrom(TASK, "2026-09-01T00:00:00");
        String beforeValue = currentValue();
        String afterValue = "21:00".equals(beforeValue) ? "22:00" : "21:00";
        Map<String, Object> beforePlan = planMapper.findPlan(TASK);
        String beforeEffective = String.valueOf(beforePlan.get("configEffectiveFrom"));
        java.sql.Timestamp beforeEffectiveTimestamp = (java.sql.Timestamp) beforePlan.get("configEffectiveFrom");
        long beforeVersion = ((Number) beforePlan.get("planVersion")).longValue();

        // 別スレッド: 1 回目のクエリのあとに設定を保存（＝設定値と適用時刻・計画バージョンを 1 トランザクションで）
        Thread writer = new Thread(() -> {
            try {
                FIRST_QUERY_DONE.await(10, TimeUnit.SECONDS);
                settingsService.saveGlobalSettingFields("consistency-test", Map.of(END_TIME_FIELD, afterValue));
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
            } finally {
                WRITE_COMMITTED.countDown();
            }
        }, "consistency-writer");
        writer.start();

        try {
            // 読み込み（1 トランザクション）。上の保存コミットが 1 回目と 2 回目のクエリの間に挟まる
            ScheduleConfigLoader.ScheduleSourceData data = loader.load(
                    List.of("NET_CONTROL_START_TIME", END_TIME_KEY,
                            "STUDY_MONITOR_L02_INTERVAL_MINUTES", "STUDY_MONITOR_L02_OFFSET_MINUTES",
                            "STUDY_MONITOR_L03_INTERVAL_MINUTES", "STUDY_MONITOR_L03_OFFSET_MINUTES"),
                    List.of("batR03", "batR04", "batL02", "batL03"));
            writer.join(10_000);

            // 読み込みのトランザクションの中で REPEATABLE READ が効いている（プロキシが効いている証拠）
            assertThat(TRANSACTION_ACTIVE_IN_LOAD.get()).isTrue();
            assertThat(ISOLATION_IN_LOAD.get()).isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);

            // **古い版のまま**: 読み込んだ 1 枚の中では、設定値も適用時刻も保存前の値
            // （片方だけ新しい、という混ざり方をしない）
            assertThat(data.settings().get(END_TIME_KEY)).isEqualTo(beforeValue);
            assertThat(data.configEffectiveFrom().get(TASK)).isEqualTo(beforeEffectiveTimestamp.toLocalDateTime());

            // 保存はコミットされている（新しい読み込みでは新しい版がそろって見える）
            assertThat(currentValue()).isEqualTo(afterValue);
            Map<String, Object> afterPlan = planMapper.findPlan(TASK);
            assertThat(((Number) afterPlan.get("planVersion")).longValue()).isEqualTo(beforeVersion + 1);
            assertThat(String.valueOf(afterPlan.get("configEffectiveFrom"))).isNotEqualTo(beforeEffective);

            ScheduleConfigLoader.ScheduleSourceData fresh = loader.load(
                    List.of(END_TIME_KEY), List.of("batR03"));
            assertThat(fresh.settings().get(END_TIME_KEY)).isEqualTo(afterValue);
            assertThat(fresh.configEffectiveFrom().get(TASK))
                    .isEqualTo(((java.sql.Timestamp) afterPlan.get("configEffectiveFrom")).toLocalDateTime());
        } finally {
            // 失敗しても必ず元に戻す（実データを変えたままにしない）
            settingsService.saveGlobalSettingFields("consistency-test", Map.of(END_TIME_FIELD, beforeValue));
            assertThat(currentValue()).isEqualTo(beforeValue);
        }
    }

    private String currentValue() {
        return settingsService.loadGlobalSettingFields().get(END_TIME_FIELD);
    }
}
