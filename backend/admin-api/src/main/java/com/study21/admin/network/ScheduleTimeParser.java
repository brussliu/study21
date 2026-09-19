package com.study21.admin.network;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 実行記録の {@code 予定時刻}（スケジューラが入れる計画実行点）を読む小さな補助。
 *
 * <p>手動実行（画面の【再実行】）では予定時刻が「その瞬間」になるため、
 * 「計画時刻より後に端末が更新されたか」の判定に使っても実害は無い
 * （手動実行は利用者が明示的に押した操作なので、上書きしない規則の対象外にしたいところだが、
 * 予定時刻＝実行時刻なので直前に更新があれば見送る。安全側に倒す）。</p>
 */
final class ScheduleTimeParser {

    private static final DateTimeFormatter[] FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")};

    private ScheduleTimeParser() {
    }

    /** 予定時刻を読む（読めなければ null）。 */
    static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().replace(' ', 'T');
        for (DateTimeFormatter format : FORMATS) {
            try {
                return LocalDateTime.parse(text, format);
            } catch (DateTimeParseException ignored) {
                // 次の形を試す
            }
        }
        return null;
    }
}
