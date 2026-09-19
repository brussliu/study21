package com.study21.admin.schedule;

import com.study21.admin.setting.SettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * **設定値と「適用時刻＋計画バージョン」は同じトランザクションで保存される**（実 DB）。
 *
 * <p>利用者の指摘: 適用時刻を設定のコミット後に別で書くと、その間でサービスが落ちたときに
 * 「新しい設定＋古い適用時刻」が残り、再起動後に**過去の計画実行点を実行**してしまう。
 * 同じトランザクションで書けば、コミットできたかロールバックしたかのどちらかしか残らない。</p>
 *
 * <p>テストはトランザクションを明示的に切って**ロールバック**するので、実データは変わらない。</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class ScheduleSettingTransactionTest {

    /** 実行設定に関わる設定キー（画面のフィールドキー）。 */
    private static final String END_TIME_FIELD = "netControlEndTime";
    private static final String TASK = "batR03";

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private ScheduleConfigService configService;

    @Autowired
    private SchedulePlanMapper planMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("保存トランザクションの中では、設定値も適用時刻も計画バージョンも変わっている")
    void allThreeAreWrittenInsideTheSameTransaction() {
        String beforeValue = currentValue();
        Map<String, Object> beforePlan = planMapper.findPlan(TASK);
        String beforeEffective = String.valueOf(beforePlan.get("configEffectiveFrom"));
        Number beforeVersion = (Number) beforePlan.get("planVersion");

        // ロールバックするトランザクションの中で保存する
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            settingsService.saveGlobalSettingFields("test", Map.of(END_TIME_FIELD, "23:45"));

            // 同じトランザクションの中では 3 つとも変わっている
            assertThat(currentValue()).isEqualTo("23:45");
            Map<String, Object> plan = planMapper.findPlan(TASK);
            assertThat(plan.get("configEffectiveFrom")).isNotNull();
            assertThat(String.valueOf(plan.get("configEffectiveFrom"))).isNotEqualTo(beforeEffective);
            assertThat(((Number) plan.get("planVersion")).longValue()).isEqualTo(beforeVersion.longValue() + 1);
            throw new RollbackSignal();
        })).isInstanceOf(RollbackSignal.class);

        // ロールバックしたので、設定値も適用時刻も計画バージョンも元のまま（食い違いが残らない）
        assertThat(currentValue()).isEqualTo(beforeValue);
        Map<String, Object> afterPlan = planMapper.findPlan(TASK);
        assertThat(String.valueOf(afterPlan.get("configEffectiveFrom"))).isEqualTo(beforeEffective);
        assertThat(((Number) afterPlan.get("planVersion")).longValue()).isEqualTo(beforeVersion.longValue());
    }

    @Test
    @DisplayName("ロールバックのあと、再読み込みした設定は DB の適用時刻と一致する（再起動でも同じ）")
    void reloadedConfigMatchesTheStoredEffectiveTime() {
        Map<String, Object> plan = planMapper.findPlan(TASK);
        Object stored = plan.get("configEffectiveFrom");

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            settingsService.saveGlobalSettingFields("test", Map.of(END_TIME_FIELD, "22:45"));
            throw new RollbackSignal();
        })).isInstanceOf(RollbackSignal.class);

        // メモリに読み直しても、DB の適用時刻（巻き戻った値）と一致する（新しい設定で過去を走らせない）
        assertThat(configService.refresh("テスト: 再読み込み").published()).isTrue();
        LocalDateTime memory = configService.snapshot().taskOf(TASK).orElseThrow().effectiveFrom();
        LocalDateTime dbValue = stored instanceof java.sql.Timestamp timestamp
                ? timestamp.toLocalDateTime() : null;
        assertThat(memory).isEqualTo(dbValue);
        // 設定の適用時刻は「いま」ではない（保存が巻き戻っているので進んでいない）
        assertThat(memory == null || memory.isBefore(LocalDateTime.now(ScheduleConfigService.ZONE)))
                .isTrue();
    }

    /** いま DB にある設定値（COM_設定情報）。 */
    private String currentValue() {
        return settingsService.loadGlobalSettingFields().get(END_TIME_FIELD);
    }

    /** ロールバックを起こすための印（業務の例外ではない）。 */
    private static final class RollbackSignal extends RuntimeException {
    }
}
