package com.study21.admin.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * admin-api の起動時に、種別 S（システム起動時）かつ有効なバッチを 1 回だけ実行する。
 *
 * <p>2.0 は起動時に batL01（プロキシサービス）を実行したうえで、さらに 6 時間ごとの
 * スケジュールでも実行していた（プロキシが落ちていれば起き上がるようにするため）。
 * 2.1 は<b>起動時の 1 回だけ</b>にし、必要ならバッチ管理画面の【再実行】で動かす
 * （プロキシが落ちた場合は再実行、または admin-api の再起動で復旧する）。</p>
 *
 * <p>実行は履歴に 起動種別='S'・依頼元コード='STARTUP' で残る。失敗しても起動は止めない
 * （履歴に異常終了として残り、画面から確認・再実行できる）。</p>
 */
@Component
public class BatchStartupRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchStartupRunner.class);

    private final BatchService batchService;

    public BatchStartupRunner(BatchService batchService) {
        this.batchService = batchService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        List<String> targets;
        try {
            targets = batchService.startupTargets();
        } catch (Exception e) {
            log.error("Failed to resolve startup batch targets.", e);
            return;
        }
        if (targets.isEmpty()) {
            log.info("No startup batch to run.");
            return;
        }
        for (String batchCode : targets) {
            try {
                var result = batchService.runOnStartup(batchCode);
                log.info("Startup batch executed. batchCode={} executionId={} status={} message={}",
                        batchCode, result.get("executionId"), result.get("status"), result.get("message"));
            } catch (Exception e) {
                // 起動は止めない（履歴・画面から確認できる）
                log.error("Startup batch failed. batchCode={}", batchCode, e);
            }
        }
    }
}
