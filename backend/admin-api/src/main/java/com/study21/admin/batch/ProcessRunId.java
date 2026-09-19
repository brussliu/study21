package com.study21.admin.batch;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * **このプロセス（admin-api の 1 回の起動）の識別子**。
 *
 * <p>再起動の復旧は「前のプロセスが残した実行」だけを扱わなければならない。実行記録を
 * 「起動時に読んだ最大の実行ID より古い行」で選ぶと、最初の読み込みが失敗して再試行する間に
 * 現在のプロセスが作った実行まで拾ってしまい（＝自分の実行を遺留と誤認）、
 * **実行中の自分の実行を閉じたり、二重に走らせたり**する。</p>
 *
 * <p>そこで実行記録に**帰属**（どの起動が作ったか）を刻む。この値は
 * {@link BatchExecutionRunIdInterceptor} が
 * <b>すべての挿入</b>（自動スケジューラ・起動時バッチ・手動実行・復旧のやり直し）に自動で入れる。
 * 復旧は「この値と違う行（NULL＝この列が無かった頃の行を含む）」だけを遺留として扱う。</p>
 *
 * <p>形式: {@code yyyyMMdd'T'HHmmss-<8桁の16進>}（例 {@code 20260920T021530-3f9a1c2b}）。
 * 人がログで見て「いつ起動したプロセスか」が分かるようにしつつ、同時刻の別プロセスとも
 * 衝突しないように乱数を足す（admin-api は単一インスタンス運用なので、これで十分）。</p>
 */
@Component
public class ProcessRunId {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private final String value;

    public ProcessRunId() {
        this(Clock.system(ZONE));
    }

    /** テスト用（時計を差し替える）。 */
    public ProcessRunId(Clock clock) {
        this.value = LocalDateTime.now(clock).format(STAMP) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /** このプロセスの識別子（起動中は変わらない）。 */
    public String value() {
        return value;
    }
}
