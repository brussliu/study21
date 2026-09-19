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
        String normalizedMime = normalizeMime(mime);
        String extension = extensionOf(normalizedMime);
        String relativeDir = "classroom/" + accountId + "/" + LocalDate.now().format(MONTH)
                + "/" + UUID.randomUUID().toString().replace("-", "");
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
     * 分塊のファイル名（**記録と連番で決まる**）。
     *
     * <p>同じ連番の再送は同じ名前になるので、書き直しても増えない（これが冪等の土台）。
     * 連番は 6 桁ゼロ詰めにして、ファイルの並びがそのまま連番順になるようにする。
     * **記録 ID を名前に入れる**のは、改修前から続いている録音の置き場（月ごとの共有
     * ディレクトリ）でも他の記録の分塊と名前が衝突しないようにするため。</p>
     */
    public static String chunkFileName(long recordId, int seq, String mimeOrName) {
        return String.format(Locale.ROOT, "%s%06d.%s", chunkPrefix(recordId), seq, extensionOf(mimeOrName));
    }

    /** その記録の分塊の接頭辞（`chunk-{記録ID}-`）。 */
    public static String chunkPrefix(long recordId) {
        return CHUNK_PREFIX + recordId + "-";
    }

    /** ファイル名がその記録の分塊か（連番を取り出す）。違えば null。 */
    public static Integer chunkSeqOf(String fileName, long recordId) {
        String prefix = chunkPrefix(recordId);
        if (fileName == null || !fileName.startsWith(prefix)) {
            return null;
        }
        int dot = fileName.indexOf('.', prefix.length());
        String digits = fileName.substring(prefix.length(), dot < 0 ? fileName.length() : dot);
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException cause) {
            return null;
        }
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

    /** 組立ての作業ファイルを作る（同じ置き場に作る＝同じボリュームなので名前の付け替えが原子的にできる）。 */
    public Path createWorkFile(String relativeDir, String prefix) {
        Path base = resolveDirectory(relativeDir);
        if (base == null) {
            throw new ValidationException("保存先の指定が正しくありません。");
        }
        try {
            Files.createDirectories(base);
            return Files.createTempFile(base, prefix + ".", ".part");
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
