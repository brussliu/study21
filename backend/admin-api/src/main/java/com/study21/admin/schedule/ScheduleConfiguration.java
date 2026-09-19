package com.study21.admin.schedule;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * バッチのスケジュール検査を有効にする。
 *
 * <p>検査の周期は {@link BatchScheduleScheduler} の {@code @Scheduled}（既定 30 秒）。
 * 検査はメモリのスナップショットだけを見て、業務は {@link BatchScheduleExecutor} に渡す
 * （検査のスレッドを業務で塞がない）。</p>
 */
@Configuration
@EnableScheduling
public class ScheduleConfiguration {
}
