package com.study21.user.classroom;

import com.study21.common.core.exception.ValidationException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 授業録音の音声（分塊を追記した 1 本のファイル）を保存・配信する。
 *
 * <p>音声を DB に持たないのは、長時間の録音（最大 120 分）が数十 MB になるため
 * （AI 生図の画像と同じ「パス + ファイル名」方式）。置き場は {@code study21.classroom.storage-root}。
 * 規約: {@code classroom/{登録者アカウントID}/{yyyyMM}/{uuid}.{ext}}。この規約を
 * **所有者チェックの根拠**にもする（他人のディレクトリは触れない）。</p>
 *
 * <p>ブラウザの {@code MediaRecorder}（webm/opus）の分塊は同じ EBML ストリームのバイト列なので、
 * **到着順に追記すれば再生可能な 1 本の webm になる**（分塊は 1 リクエスト数 MB なので、
 * `multipart.max-file-size` は余裕）。パストラバーサル対策は {@code GeometryAiStorage} と同じ
 * {@link #requireUnder} / {@link #safeStoredName} を使う。</p>
 *
 * <p><b>2026-09-19 改修</b>: 分塊は**1 塊 1 ファイル**（{@code chunk-000001.webm}）で保存し、
 * 再生用の 1 本は{@link ClassroomRecordingAssembler}が**連番順に組立てる**。追記をやめた理由は
 * 「ファイルの追記は成功したのに DB のトランザクションが失敗した」再送で同じ音が二重に入ること、
 * 一時停止・画面の開き直しで `MediaRecorder` を作り直すと**コンテナのヘッダが途中に入り、
 * 単純な連結では最初の回しか再生できない**こと（{@link RecordingContainerCutter} を参照）。</p>
 */
@Component
public class ClassroomRecordingStorage {

    private static final Logger log = LoggerFactory.getLogger(ClassroomRecordingStorage.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    /** 分塊ファイルの接頭辞（連番は 6 桁ゼロ詰め＝辞書順が連番順になる）。 */
    static final String CHUNK_PREFIX = "chunk-";

    /** 置き場の初期化を直列化する鍵（記録ごとに 1 本）。 */
    private static final Object[] DIRECTORY_LOCKS = new Object[64];

    static {
        for (int index = 0; index < DIRECTORY_LOCKS.length; index += 1) {
            DIRECTORY_LOCKS[index] = new Object();
        }
    }

    private final Path root;

    public ClassroomRecordingStorage(
            @Value("${study21.classroom.storage-root:${user.dir}/data/classroom}") String storageRoot) {
        this.root = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    @PostConstruct
    void logStorageRoot() {
        log.info("授業録音の置き場（user-api）: {} （既存: {}）", root, Files.isDirectory(root));
    }

    public Path getRoot() {
        return root;
    }

    /** 保存するファイル 1 本（パスはストレージルートからの相対パス）。 */
    public record StoredRecording(String relativeDir, String fileName, String mime) {
    }

    /**
     * 録音を保存する相対パス・ファイル名・MIME を決める（実体は最初の分塊で作る）。
     *
     * <p><b>録音 1 件につき 1 ディレクトリ</b>を切る（`…/{yyyyMM}/{uuid}/recording.webm`）。
     * 分塊（{@code chunk-000001.webm}）を同じディレクトリへ置くので、置き場が記録ごとに
     * 分かれていないと**他の記録の分塊と混ざる**（同じ連番の名前が衝突する）。</p>
     */
    public StoredRecording newRecording(long accountId, String mime) {
        return newRecording(accountId, UUID.randomUUID().toString().replace("-", ""), mime);
    }

    /**
     * 録音 1 件の置き場を**記録 ID から決めて**作る（分塊を受け取るとき）。
     *
     * <p><b>なぜ記録 ID で決めるか</b>: 録音の開始直後（`録音ファイル保存先` がまだ空のとき）に
     * 分塊が**同時に複数**届くと、毎回違う置き場を作ってしまい、分塊が別々のディレクトリへ
     * 散らばる（DB が指す置き場と実体の場所が食い違い、欠落として扱われる）。記録 ID から
     * 決めれば、同時に届いても**同じ置き場**になる（`Files.createDirectories` は冪等）。</p>
     *
     * @param recordId 授業記録 ID（まだ置き場が決まっていない記録のとき）
     */
    public StoredRecording newRecording(long accountId, long recordId, String mime) {
        return newRecording(accountId, "rec" + recordId, mime);
    }

    /** 置き場の名前を明示して作る（{@link #newRecording(long, long, String)} と取り込みで使う）。 */
    private StoredRecording newRecording(long accountId, String directoryName, String mime) {
        String normalizedMime = normalizeMime(mime);
        String extension = extensionOf(normalizedMime);
        String relativeDir = "classroom/" + accountId + "/" + LocalDate.now().format(MONTH)
                + "/" + directoryName;
        return new StoredRecording(relativeDir, "recording." + extension, normalizedMime);
    }

    /** 分塊のバイト列を追記する（無ければ新規作成）。 */
    public void append(String relativeDir, String fileName, byte[] bytes) {
        Path target = requireUnder(relativeDir, fileName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes == null ? new byte[0] : bytes,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException cause) {
            log.error("授業録音を保存できませんでした。path={}", target, cause);
            throw new ValidationException("録音を保存できませんでした。時間をおいてもう一度お試しください。");
        }
    }

    /** 実体のパスを解決する（相対パスは DB に入っている値。トラバーサルは弾く）。 */
    public Path resolve(String relativeDir, String fileName) {
        if (relativeDir == null || relativeDir.isBlank() || fileName == null || fileName.isBlank()) {
            return null;
        }
        return requireUnder(relativeDir, fileName);
    }

    /** 置き場（相対ディレクトリ）の実パス。トラバーサルは弾く。 */
    public Path resolveDirectory(String relativeDir) {
        if (relativeDir == null || relativeDir.isBlank()) {
            return null;
        }
        Path base = root.resolve(safeRelativeDir(relativeDir)).normalize();
        if (!base.startsWith(root)) {
            throw new ValidationException("保存先の指定が正しくありません。");
        }
        return base;
    }

    /** 保存済みの分塊 1 つ（連番・ファイル名・大きさ・更新時刻）。 */
    public record StoredChunk(int seq, String fileName, long byteSize, long modifiedAt) {
    }

    /**
     * 分塊 1 つの**書き込み先**（相対ディレクトリ + ファイル名）。
     *
     * <p><b>なぜ「ファイル名だけ」では足りないか</b>: `chunk-{記録ID}-{連番}.webm` のような
     * 連番から決まる名前は、同じ連番が同時に 2 本来たときに**同じ場所**になる。先に書いた側の
     * 中身が後の書き込みで消え、DB は勝った側の行を持っているのに実体は負けた側の中身、という
     * 食い違いが起きる（＝音が入れ替わる）。そこで書き込み先は**分塊ごとに固有**にし、
     * **どの DB の行からも引用されていない実体は音として使わない**（{@link #collectOrphanChunks}）。</p>
     */
    public record ChunkLocation(String relativeDir, String fileName) {
    }

    /**
     * 分塊のファイル名（**記録と連番で決まる**）。
     *
     * <p><b>新しい書き込みには使わない</b>（{@link #newChunkLocation} を使う）。同じ連番で
     * 同じ名前になるため、同時に 2 本来たときに上書きが起きる。<b>既存の置き場</b>
     * （この名前で保存された分塊）を読む・数えるときの互換のために残す。</p>
     */
    public static String chunkFileName(long recordId, int seq, String mimeOrName) {
        return String.format(Locale.ROOT, "%s%06d.%s", chunkPrefix(recordId), seq, extensionOf(mimeOrName));
    }

    /**
     * 分塊 1 つの**固有の書き込み先**を作る（まだ書かない）。
     *
     * <p>ファイル名は `chunk-{記録ID}-{連番}-{UUID}.{拡張子}`。連番を残すのは運用で見分けるためで、
     * **一意にするのは UUID のほう**（同じ連番・同じ記録でも別の場所になる＝上書きが起こり得ない）。
     * どの実体が「その分塊」になるかは**DB の行が決める**（`ON CONFLICT DO NOTHING` に勝った
     * 1 本だけが引用される）。</p>
     */
    public ChunkLocation newChunkLocation(String relativeDir, long recordId, int seq, String mimeOrName) {
        String fileName = String.format(Locale.ROOT, "%s%06d-%s.%s", chunkPrefix(recordId), seq,
                UUID.randomUUID().toString().replace("-", ""), extensionOf(mimeOrName));
        return new ChunkLocation(relativeDir, fileName);
    }

    /**
     * 録音の置き場を用意する（**同時に呼ばれても落ちない**）。
     *
     * <p>初回のアップロードは複数の分塊がほぼ同時に届くので、`Files.createDirectories` が
     * 同時に走る。`createDirectories` は既にあるディレクトリを受け入れるが、**同じ記録の
     * 初期化を 1 回にまとめたい**（DB の更新と組み合わせるときの競合を減らす）ので、
     * 記録ごとのロックで直列化する。</p>
     *
     * @return この呼び出しで実際に作ったとき true（既にあったときは false）
     */
    public boolean prepareRecordingDirectory(String relativeDir) {
        Path base = resolveDirectory(relativeDir);
        if (base == null) {
            throw new ValidationException("保存先の指定が正しくありません。");
        }
        synchronized (directoryLockOf(relativeDir)) {
            boolean created = !Files.isDirectory(base);
            try {
                Files.createDirectories(base);
            } catch (IOException cause) {
                log.error("授業録音の置き場を作れませんでした。dir={}", relativeDir, cause);
                throw new ValidationException("録音の保存先を用意できませんでした。"
                        + "時間をおいてもう一度お試しください。");
            }
            return created;
        }
    }

    /** 置き場を作るための鍵（同じ置き場の初期化を直列化する）。 */
    private Object directoryLockOf(String relativeDir) {
        int index = Math.floorMod(relativeDir == null ? 0 : relativeDir.hashCode(), DIRECTORY_LOCKS.length);
        return DIRECTORY_LOCKS[index];
    }

    /** その記録の分塊の接頭辞（`chunk-{記録ID}-`）。 */
    public static String chunkPrefix(long recordId) {
        return CHUNK_PREFIX + recordId + "-";
    }

    /**
     * ファイル名がその記録の分塊か（連番を取り出す）。違えば null。
     *
     * <p>連番は接頭辞の直後の**数字の並び**（`chunk-{記録ID}-{連番}[-{UUID}].{拡張子}`）。
     * 後ろに UUID が付いても連番が取れるように、**数字が続くところまで**を読む
     * （読めないと、掃除や一覧から取りこぼして置き場に残り続ける）。</p>
     */
    public static Integer chunkSeqOf(String fileName, long recordId) {
        String prefix = chunkPrefix(recordId);
        if (fileName == null || !fileName.startsWith(prefix)) {
            return null;
        }
        int position = prefix.length();
        while (position < fileName.length() && Character.isDigit(fileName.charAt(position))) {
            position += 1;
        }
        if (position == prefix.length()) {
            return null;
        }
        try {
            return Integer.valueOf(fileName.substring(prefix.length(), position));
        } catch (NumberFormatException cause) {
            return null;
        }
    }

    /**
     * 分塊を**書き込み先の指定どおり**に書く（一時名へ書いてから名前を付ける）。
     *
     * <p>書き込み先は {@link #newChunkLocation} が作った**固有の場所**なので、同じ連番が
     * 同時に来ても**互いの実体を壊さない**。どの実体を使うかは DB の行が決める。</p>
     */
    public void writeChunkLocation(ChunkLocation location, byte[] bytes) {
        if (location == null) {
            throw new ValidationException("保存先の指定が正しくありません。");
        }
        writeChunk(location.relativeDir(), location.fileName(), bytes);
    }

    /**
     * 分塊を 1 ファイルで書く（**一時名へ書いてから名前を付ける**）。
     *
     * <p>途中まで書けたファイルを組立てに読ませない（読む側が半端な分塊を掴むと、
     * 再生できない 1 本ができてしまう）。</p>
     */
    public void writeChunk(String relativeDir, String fileName, byte[] bytes) {
        Path target = requireUnder(relativeDir, fileName);
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), fileName + ".", ".part");
            Files.write(temp, bytes == null ? new byte[0] : bytes);
            moveIntoPlace(temp, target);
        } catch (IOException cause) {
            log.error("授業録音の分塊を保存できませんでした。path={}", target, cause);
            deleteQuietly(temp);
            throw new ValidationException("録音を保存できませんでした。時間をおいてもう一度お試しください。");
        }
    }

    /**
     * その記録の分塊（連番順。組立てに使う）。
     *
     * <p>**ファイル名を正体にする**（DB の行ではなく）。行の登録だけが失敗した分塊も
     * ファイルとしては残っており、音を失わないため。</p>
     */
    public List<StoredChunk> listChunks(String relativeDir, long recordId) {
        if (relativeDir == null || relativeDir.isBlank()) {
            return List.of();
        }
        Path base = resolveDirectory(relativeDir);
        if (base == null || !Files.isDirectory(base)) {
            return List.of();
        }
        List<StoredChunk> chunks = new ArrayList<>();
        try (java.util.stream.Stream<Path> entries = Files.list(base)) {
            for (Path entry : entries.toList()) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                String name = entry.getFileName().toString();
                Integer seq = chunkSeqOf(name, recordId);
                if (seq == null) {
                    continue;
                }
                chunks.add(new StoredChunk(seq, name, Files.size(entry),
                        Files.getLastModifiedTime(entry).toMillis()));
            }
        } catch (IOException cause) {
            log.warn("授業録音の分塊を数えられませんでした。dir={}", relativeDir, cause);
            return List.of();
        }
        chunks.sort(java.util.Comparator.comparingInt(StoredChunk::seq));
        return chunks;
    }

    /**
     * **どの DB の行からも引用されていない分塊**を片付ける（孤立ファイルの掃除）。
     *
     * <p>「ファイルは書けたがトランザクションが失敗した」分塊は、行から引用されないまま
     * 残る。放置すると置き場を圧迫し、**組立てに混ざる**と壊れた 1 本ができる。ただし
     * 書いた直後の実体を消してはいけない（まだ DB が確定していない同時実行中の要求が
     * 引用するかもしれない）。そこで**一定時間より古い孤立ファイルだけ**を消す。</p>
     *
     * <p>引用されている実体（記録の再生用の 1 本を含む）には触れない。判定は
     * <b>呼び側が DB の行から作った「引用されているファイル名」</b>で行う
     * （ディレクトリの一覧を正体にしない＝未確定のファイルを音として扱わない）。</p>
     *
     * @param referencedFileNames DB の行が引用しているファイル名（記録の再生用の 1 本も入れる）
     * @param minAgeMillis これより新しい孤立ファイルは残す
     * @return 消した件数
     */
    public int collectOrphanChunks(String relativeDir, long recordId,
                                   java.util.Collection<String> referencedFileNames, long minAgeMillis) {
        if (relativeDir == null || relativeDir.isBlank()) {
            return 0;
        }
        Path base = resolveDirectory(relativeDir);
        if (base == null || !Files.isDirectory(base)) {
            return 0;
        }
        java.util.Set<String> referenced = referencedFileNames == null
                ? java.util.Set.of() : new java.util.HashSet<>(referencedFileNames);
        long deadline = System.currentTimeMillis() - Math.max(0, minAgeMillis);
        int deleted = 0;
        for (StoredChunk chunk : listChunks(relativeDir, recordId)) {
            if (referenced.contains(chunk.fileName()) || chunk.modifiedAt() > deadline) {
                continue;
            }
            if (delete(relativeDir, chunk.fileName())) {
                deleted += 1;
                log.info("引用されていない分塊を片付けました。recordId={} name={} seq={}",
                        recordId, chunk.fileName(), chunk.seq());
            }
        }
        return deleted;
    }

    /** その記録の分塊のファイルを消す（保持期限切れの掃除。記録 ID が名前に入っているものだけ）。 */
    public int deleteChunks(String relativeDir, long recordId) {
        int deleted = 0;
        for (StoredChunk chunk : listChunks(relativeDir, recordId)) {
            if (delete(relativeDir, chunk.fileName())) {
                deleted += 1;
            }
        }
        if (relativeDir != null) {
            removeDirectoryIfEmpty(relativeDir);
        }
        return deleted;
    }

    /**
     * 組立ての作業ファイルを作る（同じ置き場に作る＝同じボリュームなので名前の付け替えが原子的にできる）。
     *
     * <p>**空のファイルを先に作る**。自分で書く作業（分塊を繋ぐ）に使う。</p>
     */
    public Path createWorkFile(String relativeDir, String prefix) {
        Path base = requireWorkDirectory(relativeDir);
        try {
            return Files.createTempFile(base, prefix + ".", ".part");
        } catch (IOException cause) {
            throw new ValidationException("録音を組立てられませんでした。時間をおいてもう一度お試しください。");
        }
    }

    /**
     * 組立ての**出力先**になる新しいパスを決める（**まだ存在しない**）。
     *
     * <p>外部コマンド（ffmpeg）に書かせる出力は、**存在しないパス**でなければならない
     * （既にあるファイルを上書きさせない＝前の結合結果を壊さない）。</p>
     */
    public Path newWorkFilePath(String relativeDir, String prefix) {
        Path base = requireWorkDirectory(relativeDir);
        Path candidate = base.resolve(prefix + "-" + UUID.randomUUID().toString().replace("-", "") + ".part");
        if (Files.exists(candidate)) {
            throw new ValidationException("録音を組立てられませんでした。時間をおいてもう一度お試しください。");
        }
        return candidate;
    }

    private Path requireWorkDirectory(String relativeDir) {
        Path base = resolveDirectory(relativeDir);
        if (base == null) {
            throw new ValidationException("保存先の指定が正しくありません。");
        }
        try {
            Files.createDirectories(base);
            return base;
        } catch (IOException cause) {
            throw new ValidationException("録音を組立てられませんでした。時間をおいてもう一度お試しください。");
        }
    }

    /** 作業ファイルを本番の名前に付け替える（同じボリューム内なので原子的）。 */
    public void moveIntoPlace(Path temp, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException cause) {
            // 置き場が別ボリュームのときだけ（配備では同じ）。読む側が半端を掴まないよう順に置く
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 作業ファイルを黙って消す（後片付け）。 */
    public void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException cause) {
            log.warn("作業ファイルを消せませんでした。path={}", path, cause);
        }
    }

    /**
     * 空になった置き場を消す（録音を消したあとの後片付け）。
     *
     * <p>**中身があるときは消さない**: 置き場は複数の記録で共有し得るので、残っているファイルを
     * 巻き込むと他の記録の録音が消える。</p>
     */
    public boolean removeDirectoryIfEmpty(String relativeDir) {
        Path base;
        try {
            base = resolveDirectory(relativeDir);
        } catch (ValidationException cause) {
            return false;
        }
        if (base == null || !Files.isDirectory(base)) {
            return false;
        }
        try (java.util.stream.Stream<Path> entries = Files.list(base)) {
            if (entries.findAny().isPresent()) {
                return false;
            }
        } catch (IOException cause) {
            return false;
        }
        try {
            Files.deleteIfExists(base);
            return true;
        } catch (IOException cause) {
            log.warn("空の置き場を消せませんでした。dir={}", relativeDir, cause);
            return false;
        }
    }

    /** 録音の置き場ごと消す（録音の削除・保持期限切れの掃除。分塊も一緒に消える）。 */
    public boolean deleteRecordingDirectory(String relativeDir) {
        Path base;
        try {
            base = resolveDirectory(relativeDir);
        } catch (ValidationException cause) {
            log.warn("授業録音のパスが不正です。dir={}", relativeDir);
            return false;
        }
        if (base == null || !Files.isDirectory(base)) {
            return false;
        }
        boolean deletedAny = false;
        try (java.util.stream.Stream<Path> entries = Files.list(base)) {
            for (Path entry : entries.toList()) {
                if (Files.isRegularFile(entry)) {
                    deletedAny |= Files.deleteIfExists(entry);
                }
            }
            Files.deleteIfExists(base);
        } catch (IOException cause) {
            log.warn("授業録音の置き場を消せませんでした。dir={}", relativeDir, cause);
        }
        return deletedAny;
    }

    /** ファイルを消す（録音の削除・保持期限切れの掃除）。無ければ false。 */
    public boolean delete(String relativeDir, String fileName) {
        if (relativeDir == null || relativeDir.isBlank() || fileName == null || fileName.isBlank()) {
            return false;
        }
        Path path;
        try {
            path = requireUnder(relativeDir, fileName);
        } catch (ValidationException cause) {
            log.warn("授業録音のパスが不正です。dir={} name={}", relativeDir, fileName);
            return false;
        }
        try {
            return Files.deleteIfExists(path);
        } catch (IOException cause) {
            log.warn("授業録音を消せませんでした。path={}", path, cause);
            return false;
        }
    }

    /** 配信の Content-Type（拡張子から決める）。 */
    public String mimeOf(String fileName) {
        return switch (extensionOf(fileName)) {
            case "ogg" -> "audio/ogg";
            case "mp4" -> "audio/mp4";
            case "wav" -> "audio/wav";
            default -> "audio/webm";
        };
    }

    // -------------------------------------------------------------------- 内部

    private Path requireUnder(String relativeDir, String fileName) {
        Path base = resolveDirectory(relativeDir);
        if (base == null) {
            throw new ValidationException("保存先の指定が正しくありません。");
        }
        Path target = base.resolve(safeStoredName(fileName)).normalize();
        if (!target.startsWith(base)) {
            throw new ValidationException("ファイル名の指定が正しくありません。");
        }
        return target;
    }

    private static String safeRelativeDir(String relativeDir) {
        String value = relativeDir == null ? "" : relativeDir.trim().replace('\\', '/');
        if (value.startsWith("/") || value.contains("..") || value.contains("\0")) {
            throw new ValidationException("保存先の指定が正しくありません。");
        }
        return value;
    }

    private static String safeStoredName(String fileName) {
        String value = fileName == null ? "" : fileName.trim();
        if (value.isEmpty() || value.contains("/") || value.contains("\\") || value.contains("..")
                || value.contains("\0") || value.startsWith(".")) {
            throw new ValidationException("ファイル名の指定が正しくありません。");
        }
        return value;
    }

    private static String normalizeMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return "audio/webm";
        }
        int semicolon = mime.indexOf(';');
        String value = (semicolon < 0 ? mime : mime.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? "audio/webm" : value;
    }

    private static String extensionOf(String mimeOrName) {
        String value = mimeOrName == null ? "" : mimeOrName.toLowerCase(Locale.ROOT);
        if (value.startsWith("audio/") || value.startsWith("video/")) {
            return switch (value) {
                case "audio/ogg", "application/ogg" -> "ogg";
                case "audio/mp4", "video/mp4", "audio/mp3", "audio/mpeg" -> "mp4";
                case "audio/wav", "audio/x-wav" -> "wav";
                default -> "webm";
            };
        }
        int dot = value.lastIndexOf('.');
        if (dot < 0 || dot == value.length() - 1) {
            return "webm";
        }
        return value.substring(dot + 1);
    }
}
