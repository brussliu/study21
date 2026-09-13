package com.study21.admin.batch;

import com.study21.admin.proxy.ProxyServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * batS01（プロキシサービス）の業務処理。
 *
 * <p>2.0 の BatL01Task と同じく {@code ProxyServerService#startIfNeeded()} を呼ぶだけで、
 * 「プロキシが起きていることを保証する」のが仕事。すでに動いていれば何もしない。
 * 2.0 は実行後に batL01（プロキシ再起動）を別タスクとして起動していたが、2.1 は
 * プロキシ自身が admin-api の中で動くため、その連鎖は無い。</p>
 */
@Component
public class ProxyStartBatchHandler implements BatchTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(ProxyStartBatchHandler.class);

    /** 2.0 の batL01 を 2.1 で改名したコード。 */
    public static final String BATCH_CODE = "batS01";

    private final ProxyServerService proxyServerService;

    public ProxyStartBatchHandler(ProxyServerService proxyServerService) {
        this.proxyServerService = proxyServerService;
    }

    @Override
    public String taskCode() {
        return BATCH_CODE;
    }

    @Override
    public String execute(BatchExecutionEntity execution) {
        String message = proxyServerService.startIfNeeded();
        log.info("batS01 proxy start result. executionId={} port={} message={}",
                execution.getExecutionId(), proxyServerService.getPort(), message);
        return message;
    }
}
