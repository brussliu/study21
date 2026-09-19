package com.study21.user.classroom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 保存した分塊から**再生用の 1 本**を組立てる。
 *
 * <p>録音は「1 本の音声として再生できる」必要がある（画面は {@code <audio>} で 1 本を読む）。
 * 一方で保存は分塊ごとのファイルにしてある（追記だと、追記は成功したのに DB の
 * トランザクションが失敗した再送で**同じ音が二重に入る**）。そこで再生のときに
 * **連番順に連結**して 1 本を作る。</p>
 *
 * <p>連結で気をつけるのは 2 点:</p>
 * <ol>
 *   <li><b>コンテナのヘッダ</b>。同じ `MediaRecorder` が `timeslice` で切った分塊は
 *       そのまま繋げるが、一時停止・画面の開き直しで作り直した回の分塊はヘッダから始まる。
 *       ヘッダを残したまま繋ぐと、プレイヤーは**最初の回の音しか再生しない**
 *       （{@link RecordingContainerCutter} が落とす位置を決める）。</li>
 *   <li><b>途中まで書けた 1 本を読ませない</b>。作業ファイルに書いてから名前を付ける
 *       （{@link ClassroomRecordingStorage#moveIntoPlace}）。</li>
 * </ol>
 *
 * <p>組立てに失敗しても**分塊は 1 つも失われない**（ログに残して、再生は今ある 1 本を配信）。
 * 分塊の行が無い（ファイルの追記だけ成功して DB が失敗した）分塊も、
 * **ファイルを見て組立てる**ので音として残る。</p>
 */
@Component
public class ClassroomRecordingAssembler {

    private static final Logger log = LoggerFactory.getLogger(ClassroomRecordingAssembler.class);

    /** ヘッダを調べるために読む先頭の長さ（EBML の Info + Tracks は数 KB に収まる）。 */
    private static final int HEAD_WINDOW = 256 * 1024;
    /** 同時に組立てないための鍵の数（記録ごとに 1 本。記録 ID で振り分ける）。 */
    private static final int LOCK_STRIPES = 64;

    private final ClassroomRecordingStorage storage;
    private final Object[] locks = new Object[LOCK_STRIPES];

    public ClassroomRecordingAssembler(ClassroomRecordingStorage storage) {
        this.storage = storage;
        for (int index = 0; index < locks.length; index += 1) {
            locks[index] = new Object();
        }
    }

    /**
     * 分塊があれば再生用の 1 本を（必要なら）作り直す。
     *
     * @return 実際に組立てたとき true（分塊が無い・既に新しい・失敗したときは false）
     */
    public boolean assembleIfNeeded(ClassroomRecordEntity record) {
        if (record == null || record.getAudioPath() == null || record.getAudioName() == null) {
            // 置き場が決まっていない（分塊が 1 つも届いていない・取り込み音声）: 何もしない
            return false;
        }
        String dir = record.getAudioPath();
        List<ClassroomRecordingStorage.StoredChunk> chunks = storage.listChunks(dir, record.getRecordId());
        if (chunks.isEmpty()) {
            // 分塊が 1 つも無い（改修前の録音・取り込み）: 今までどおり 1 本をそのまま配信する
            return false;
        }
        Path target = storage.resolve(dir, record.getAudioName());
        if (target == null) {
            return false;
        }
        if (isUpToDate(target, chunks)) {
            return false;
        }
        synchronized (lockOf(record.getRecordId())) {
            // 待っている間に別の要求が組立て終えているかもしれない
            if (isUpToDate(target, chunks)) {
                return false;
            }
            return assemble(record, dir, chunks, target);
        }
    }

    /** 再生用の 1 本が、いまある分塊より新しいか（＝組立て直す必要が無いか）。 */
    private boolean isUpToDate(Path target, List<ClassroomRecordingStorage.StoredChunk> chunks) {
        long newest = 0;
        for (ClassroomRecordingStorage.StoredChunk chunk : chunks) {
            newest = Math.max(newest, chunk.modifiedAt());
        }
        try {
            if (!Files.isRegularFile(target) || Files.size(target) == 0) {
                return false;
            }
            // 「以上」ではなく「より新しい」で見る: 同じミリ秒に書かれた分塊を取りこぼさない
            return Files.getLastModifiedTime(target).toMillis() > newest;
        } catch (IOException cause) {
            return false;
        }
    }

    private boolean assemble(ClassroomRecordEntity record, String dir,
                             List<ClassroomRecordingStorage.StoredChunk> chunks, Path target) {
        Path temp = null;
        long written = 0;
        int unstripped = 0;
        try {
            temp = storage.createWorkFile(dir, "assemble-" + record.getRecordId());
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp))) {
                for (int index = 0; index < chunks.size(); index += 1) {
                    ClassroomRecordingStorage.StoredChunk chunk = chunks.get(index);
                    Path source = storage.resolve(dir, chunk.fileName());
                    if (source == null || !Files.isRegularFile(source)) {
                        // 行はあるのに実体が無い（掃除の途中など）: その分塊だけ飛ばして残りを活かす
                        log.warn("授業録音の分塊の実体がありません。recordId={} seq={} name={}",
                                record.getRecordId(), chunk.seq(), chunk.fileName());
                        continue;
                    }
                    long offset = 0;
                    if (index > 0) {
                        /*
                         * 2 塊目以降: 新しいコンテナのヘッダなら落とす。
                         * 1 塊目は**そのまま**（録音の先頭のヘッダはここにしかない）。
                         */
                        byte[] head = readHead(source);
                        offset = RecordingContainerCutter.bodyOffset(head, head.length);
                        if (offset == RecordingContainerCutter.NOT_CUT) {
                            // 落とし方が分からない: 音は捨てずに繋ぐ（その回の頭が壊れることはログに残す）
                            unstripped += 1;
                            offset = 0;
                            log.warn("授業録音の分塊のコンテナヘッダを外せませんでした。"
                                            + "recordId={} seq={} head={} そのまま連結します",
                                    record.getRecordId(), chunk.seq(), describeHead(head));
                        } else if (offset > 0) {
                            log.info("授業録音の分塊のコンテナヘッダを外して連結します。"
                                            + "recordId={} seq={} 外した={}バイト",
                                    record.getRecordId(), chunk.seq(), offset);
                        }
                    }
                    written += copyFrom(source, offset, out);
                }
            }
            if (written == 0) {
                storage.deleteQuietly(temp);
                return false;
            }
            storage.moveIntoPlace(temp, target);
            log.info("授業録音を組立てました。recordId={} chunks={} bytes={} ヘッダを外せず={}",
                    record.getRecordId(), chunks.size(), written, unstripped);
            return true;
        } catch (IOException | RuntimeException cause) {
            log.error("授業録音を組立てられませんでした（分塊は残っています）。recordId={}",
                    record.getRecordId(), cause);
            storage.deleteQuietly(temp);
            return false;
        }
    }

    /** 分塊の先頭を読む（ヘッダの判定用。ファイル全体は読まない）。 */
    private byte[] readHead(Path source) throws IOException {
        try (InputStream in = Files.newInputStream(source)) {
            byte[] buffer = new byte[HEAD_WINDOW];
            int read = 0;
            while (read < buffer.length) {
                int count = in.read(buffer, read, buffer.length - read);
                if (count < 0) {
                    break;
                }
                read += count;
            }
            byte[] head = new byte[read];
            System.arraycopy(buffer, 0, head, 0, read);
            return head;
        }
    }

    /** offset バイト目から最後までを書き出す。 */
    private long copyFrom(Path source, long offset, OutputStream out) throws IOException {
        try (InputStream in = Files.newInputStream(source)) {
            long remaining = offset;
            while (remaining > 0) {
                long skipped = in.skip(remaining);
                if (skipped <= 0) {
                    // skip が効かない実装（ストリームの種類による）: 読み飛ばす
                    if (in.read() < 0) {
                        return 0;
                    }
                    skipped = 1;
                }
                remaining -= skipped;
            }
            return in.transferTo(out);
        }
    }

    private Object lockOf(Long recordId) {
        int index = recordId == null ? 0 : (int) Math.floorMod(recordId, (long) LOCK_STRIPES);
        return locks[index];
    }

    /** ログ用に先頭の数バイトを 16 進で表す。 */
    private static String describeHead(byte[] head) {
        int length = Math.min(8, head.length);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < length; index += 1) {
            builder.append(String.format("%02X", head[index]));
        }
        return builder.toString();
    }
}
