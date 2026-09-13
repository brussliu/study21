package com.study21.user.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.account.AccountEntity;
import com.study21.user.account.AccountMapper;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ゲーム対戦の実装。
 *
 * <p>1 手ごとに「手番か・空きマスか・ルール上置けるか」を確認し、反転・パス・勝敗を
 * サーバーで判定してから DB（対戦情報の盤面と 対戦手情報）へ書き込む。
 * 同時更新は バージョン（楽観的ロック）で検出して 409 を返す。</p>
 */
@Service
public class GameMatchServiceImpl implements GameMatchService {

    private static final String STATUS_WAITING = "WAITING";
    private static final String STATUS_PLAYING = "PLAYING";
    private static final String STATUS_FINISHED = "FINISHED";
    private static final String STONE_BLACK = "BLACK";

    private final GameMatchMapper mapper;
    private final AccountMapper accountMapper;
    private final GameEventPublisher publisher;
    private final ObjectMapper objectMapper;

    public GameMatchServiceImpl(GameMatchMapper mapper, AccountMapper accountMapper,
                                GameEventPublisher publisher, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.accountMapper = accountMapper;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    /* ---------- 照会 ---------- */

    @Override
    public GameMatchModels.OpponentList listOpponents(UserPrincipal user) {
        requireLogin(user);
        List<GameMatchModels.Opponent> items = new ArrayList<>();
        AccountEntity me = accountMapper.findById(user.accountId());
        if (isGuardian(me)) {
            // 保護者 → 子ども
            AccountEntity student = accountMapper.findStudentByGuardianId(user.accountId());
            if (student != null) {
                items.add(toOpponent(student, "STUDENT"));
            }
        } else if (me != null && me.getGuardianId() != null) {
            // 生徒 → 保護者
            AccountEntity guardian = accountMapper.findById(me.getGuardianId());
            if (guardian != null) {
                items.add(toOpponent(guardian, "GUARDIAN"));
            }
        }
        return new GameMatchModels.OpponentList(items);
    }

    /** 相手 1 人（いまゲーム画面を開いているか＝オンラインかも添える）。 */
    private GameMatchModels.Opponent toOpponent(AccountEntity account, String role) {
        return new GameMatchModels.Opponent(
                account.getAccountId(), displayName(account), role, publisher.isOnline(account.getAccountId()));
    }

    @Override
    public GameMatchModels.MatchList listMatches(UserPrincipal user) {
        requireLogin(user);
        List<GameMatchModels.MatchRow> items = mapper.listMyMatches(user.accountId()).stream()
                .map(entity -> new GameMatchModels.MatchRow(
                        entity.getMatchId(),
                        entity.getGameType(),
                        entity.getStatus(),
                        entity.getTurnAccountId() != null && entity.getTurnAccountId() == user.accountId(),
                        entity.getChallengerAccountId() != null
                                && entity.getChallengerAccountId() == user.accountId(),
                        entity.getOpponentName(),
                        myStone(entity, user.accountId()),
                        entity.getMoveCount() == null ? 0 : entity.getMoveCount(),
                        entity.getResultCode(),
                        entity.getWinnerAccountId(),
                        entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toLocalDateTime(),
                        entity.getVersion() == null ? 1 : entity.getVersion()))
                .toList();
        return new GameMatchModels.MatchList(items);
    }

    @Override
    public GameMatchModels.MatchDetail getMatch(UserPrincipal user, long matchId) {
        requireLogin(user);
        return toDetail(requireParticipant(user, matchId), user.accountId());
    }

    /* ---------- 招待と状態変更 ---------- */

    @Override
    @Transactional
    public GameMatchModels.MatchDetail createMatch(UserPrincipal user, GameMatchModels.CreateRequest request) {
        requireLogin(user);
        String gameType = normalizeGameType(request == null ? null : request.gameType());
        Long opponentAccountId = request == null ? null : request.opponentAccountId();
        if (opponentAccountId == null || opponentAccountId <= 0) {
            throw new ValidationException("対戦相手を選択してください。");
        }
        if (opponentAccountId == user.accountId()) {
            throw new ValidationException("自分とは対戦できません。");
        }
        AccountEntity opponent = accountMapper.findById(opponentAccountId);
        if (opponent == null) {
            throw new ValidationException("対戦相手が見つかりません。");
        }
        // 対戦できるのは「自分の子ども」または「自分の親」だけ
        boolean allowed = listOpponents(user).items().stream()
                .anyMatch(item -> item.accountId() == opponentAccountId);
        if (!allowed) {
            throw new ValidationException("対戦できる相手ではありません。");
        }
        // 相手がゲーム画面を開いていない（＝応戦ダイアログを見られない）ときは申し込まない。
        // 画面はこのメッセージをそのまま出す（ユーザーの指定 ③）。
        if (!publisher.isOnline(opponentAccountId)) {
            throw new ValidationException("相手がオンラインではありません。相手がゲーム画面を開いてから申し込んでください。");
        }

        // 先手（黒）か後手（白）かは申し込む人が選ぶ（ユーザーの指定）。
        String challengerStone = normalizeStone(request == null ? null : request.challengerStone());

        GameMatchEntity entity = new GameMatchEntity();
        entity.setGameType(gameType);
        entity.setChallengerAccountId(user.accountId());
        entity.setOpponentAccountId(opponentAccountId);
        entity.setStatus(STATUS_WAITING);
        entity.setChallengerStone(challengerStone);
        // 開始前の手番は「黒を持つ人」＝先手。承諾したときに同じ値で確定する
        entity.setTurnAccountId(STONE_BLACK.equals(challengerStone) ? user.accountId() : opponentAccountId);
        entity.setBoard(toJson(GameBoard.initial(gameType).toJson()));
        entity.setMoveCount(0);
        entity.setCreatedByAccountId(user.accountId());
        entity.setUpdatedByAccountId(user.accountId());
        mapper.insertMatch(entity);

        GameMatchEntity saved = requireParticipant(user, entity.getMatchId());
        publisher.publishAfterCommit(opponentAccountId, "invited", Map.of("matchId", entity.getMatchId()));
        return toDetail(saved, user.accountId());
    }

    @Override
    @Transactional
    public GameMatchModels.MatchDetail acceptMatch(UserPrincipal user, long matchId, Integer version) {
        requireLogin(user);
        GameMatchEntity match = requireParticipant(user, matchId);
        int expected = requireVersion(match, version, false);
        if (!STATUS_WAITING.equals(match.getStatus())) {
            throw new ValidationException("この対戦は招待中ではありません。");
        }
        if (match.getOpponentAccountId() != user.accountId()) {
            throw new ValidationException("招待された本人だけが承諾できます。");
        }
        if (mapper.acceptMatch(matchId, user.accountId(), expected) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        GameMatchEntity saved = requireParticipant(user, matchId);
        publisher.publishToBothAfterCommit(saved.getChallengerAccountId(), saved.getOpponentAccountId(),
                "accepted", Map.of("matchId", matchId, "version", saved.getVersion()));
        return toDetail(saved, user.accountId());
    }

    @Override
    @Transactional
    public GameMatchModels.MatchDetail declineMatch(UserPrincipal user, long matchId, Integer version) {
        requireLogin(user);
        GameMatchEntity match = requireParticipant(user, matchId);
        int expected = requireVersion(match, version, false);
        if (!STATUS_WAITING.equals(match.getStatus())) {
            throw new ValidationException("この対戦は招待中ではありません。");
        }
        if (match.getOpponentAccountId() != user.accountId()) {
            throw new ValidationException("招待された本人だけが断れます。");
        }
        if (mapper.declineMatch(matchId, user.accountId(), expected) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        GameMatchEntity saved = requireParticipant(user, matchId);
        publisher.publishAfterCommit(saved.getChallengerAccountId(), "declined", Map.of("matchId", matchId));
        return toDetail(saved, user.accountId());
    }

    @Override
    @Transactional
    public GameMatchModels.MatchDetail cancelMatch(UserPrincipal user, long matchId, Integer version) {
        requireLogin(user);
        GameMatchEntity match = requireParticipant(user, matchId);
        int expected = requireVersion(match, version, false);
        if (!STATUS_WAITING.equals(match.getStatus())) {
            throw new ValidationException("招待中ではない対戦は取り消せません。");
        }
        if (match.getChallengerAccountId() != user.accountId()) {
            throw new ValidationException("招待した本人だけが取り消せます。");
        }
        if (mapper.cancelMatch(matchId, user.accountId(), expected) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        GameMatchEntity saved = requireParticipant(user, matchId);
        publisher.publishAfterCommit(saved.getOpponentAccountId(), "cancelled", Map.of("matchId", matchId));
        return toDetail(saved, user.accountId());
    }

    @Override
    @Transactional
    public GameMatchModels.MatchDetail timeoutMatch(UserPrincipal user, long matchId, Integer version) {
        requireLogin(user);
        GameMatchEntity match = requireParticipant(user, matchId);
        int expected = requireVersion(match, version, false);
        if (!STATUS_PLAYING.equals(match.getStatus())) {
            throw new ValidationException("対戦中ではありません。");
        }
        long opponentAccountId = otherAccountId(match, user.accountId());
        // 相手がまだゲーム画面を開いている間は終了できない（投了を使う）
        if (publisher.isOnline(opponentAccountId)) {
            throw new ValidationException("相手はまだオンラインです。");
        }
        long winner = user.accountId();
        if (mapper.timeoutMatch(matchId, user.accountId(), winner, expected) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        GameMatchEntity saved = requireParticipant(user, matchId);
        publisher.publishToBothAfterCommit(saved.getChallengerAccountId(), saved.getOpponentAccountId(), "finished",
                Map.of("matchId", matchId, "winnerAccountId", winner, "resultCode", "TIMEOUT"));
        return toDetail(saved, user.accountId());
    }

    @Override
    @Transactional
    public GameMatchModels.MatchDetail resignMatch(UserPrincipal user, long matchId, Integer version) {
        requireLogin(user);
        GameMatchEntity match = requireParticipant(user, matchId);
        int expected = requireVersion(match, version, false);
        if (!STATUS_PLAYING.equals(match.getStatus())) {
            throw new ValidationException("対戦中ではありません。");
        }
        long winner = match.getChallengerAccountId() == user.accountId()
                ? match.getOpponentAccountId()
                : match.getChallengerAccountId();
        if (mapper.resignMatch(matchId, user.accountId(), winner, user.accountId(), expected) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        GameMatchEntity saved = requireParticipant(user, matchId);
        publisher.publishToBothAfterCommit(saved.getChallengerAccountId(), saved.getOpponentAccountId(), "finished",
                Map.of("matchId", matchId, "winnerAccountId", winner, "resultCode", "RESIGN"));
        return toDetail(saved, user.accountId());
    }

    /* ---------- 着手 ---------- */

    @Override
    @Transactional
    public GameMatchModels.MatchDetail move(UserPrincipal user, long matchId, GameMatchModels.MoveRequest request) {
        requireLogin(user);
        GameMatchEntity match = requireParticipant(user, matchId);
        int expected = requireVersion(match, request == null ? null : request.version(), true);
        if (!STATUS_PLAYING.equals(match.getStatus())) {
            throw new ValidationException("対戦中ではありません。");
        }
        if (match.getTurnAccountId() == null || match.getTurnAccountId() != user.accountId()) {
            throw new ValidationException("あなたの手番ではありません。");
        }
        Integer row = request == null ? null : request.row();
        Integer col = request == null ? null : request.col();
        if (row == null || col == null) {
            throw new ValidationException("置くマスを指定してください。");
        }

        String stone = currentStone(match, user.accountId());
        GameBoard board = GameBoard.fromJson(match.getGameType(), parseBoard(match.getBoard()));
        GameRules.MoveResult result = GameRules.apply(board, match.getGameType(), stone, row, col);

        int moveNumber = (match.getMoveCount() == null ? 0 : match.getMoveCount()) + 1;
        insertMove(matchId, moveNumber, user.accountId(), stone, row, col, false,
                result.flipped(), result.line());

        String status = STATUS_PLAYING;
        Long winner = null;
        String resultCode = null;
        Timestamp finishedAt = null;
        Long turnAccountId = turnOf(match, result.nextStone());

        if (result.nextPassed()) {
            // 相手は置けない → 相手のパスを 1 行残して、もう一度同じ人の番
            // （パスの行の石は「打てなかった側」＝相手の石）
            moveNumber += 1;
            insertMove(matchId, moveNumber, otherAccountId(match, user.accountId()),
                    GameRules.otherStone(stone), null, null, true, List.of(), List.of());
        }
        if (result.gameOver()) {
            status = STATUS_FINISHED;
            finishedAt = new Timestamp(System.currentTimeMillis());
            resultCode = result.win() ? "WIN" : "DRAW";
            winner = result.win() ? user.accountId() : null;
            // 終了しても 手番アカウントID は NULL にしない
            // （CK_ゲーム対戦_手番: 対戦中・終了は手番の人がいる）。
            // 勝者（引き分けは最後に指した人）を残す。
            turnAccountId = winner == null ? user.accountId() : winner;
        }

        int updated = mapper.updateAfterMove(matchId, toJson(board.toJson()), moveNumber, turnAccountId, status,
                winner, resultCode, finishedAt, user.accountId(), expected);
        if (updated == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }

        GameMatchEntity saved = requireParticipant(user, matchId);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("matchId", matchId);
        event.put("version", saved.getVersion());
        event.put("accountId", user.accountId());
        if (STATUS_FINISHED.equals(status)) {
            event.put("winnerAccountId", winner);
            event.put("resultCode", resultCode);
            publisher.publishToBothAfterCommit(saved.getChallengerAccountId(), saved.getOpponentAccountId(), "finished", event);
        } else {
            publisher.publishAfterCommit(otherAccountId(match, user.accountId()), "move", event);
        }
        return toDetail(saved, user.accountId());
    }

    @Override
    public SseEmitter subscribe(UserPrincipal user) {
        requireLogin(user);
        return publisher.subscribe(user.accountId());
    }

    /* ---------- 内部 ---------- */

    private void insertMove(long matchId, int moveNumber, long accountId, String stone, Integer row, Integer col,
                            boolean pass, List<List<Integer>> flipped, List<List<Integer>> line) {
        GameMoveEntity move = new GameMoveEntity();
        move.setMatchId(matchId);
        move.setMoveNumber(moveNumber);
        move.setAccountId(accountId);
        move.setStone(stone);
        move.setRowIndex(row);
        move.setColIndex(col);
        move.setPassFlag(pass ? "1" : "0");
        move.setFlipped(toJson(flipped));
        move.setLine(toJson(line));
        mapper.insertMove(move);
    }

    /** 手番の人が持つ石。 */
    private static String currentStone(GameMatchEntity match, long accountId) {
        String challengerStone = match.getChallengerStone() == null ? STONE_BLACK : match.getChallengerStone();
        return match.getChallengerAccountId() == accountId
                ? challengerStone
                : GameRules.otherStone(challengerStone);
    }

    /** 自分から見た石（一覧用）。 */
    private static String myStone(GameMatchEntity match, long accountId) {
        return currentStone(match, accountId);
    }

    /** 石の持ち主のアカウントID。 */
    private static Long turnOf(GameMatchEntity match, String stone) {
        String challengerStone = match.getChallengerStone() == null ? STONE_BLACK : match.getChallengerStone();
        return challengerStone.equals(stone) ? match.getChallengerAccountId() : match.getOpponentAccountId();
    }

    private static long otherAccountId(GameMatchEntity match, long accountId) {
        return match.getChallengerAccountId() == accountId
                ? match.getOpponentAccountId()
                : match.getChallengerAccountId();
    }

    private GameMatchEntity requireParticipant(UserPrincipal user, long matchId) {
        GameMatchEntity match = mapper.findMatchById(matchId, user.accountId());
        if (match == null
                || (match.getChallengerAccountId() != user.accountId()
                    && match.getOpponentAccountId() != user.accountId())) {
            // 他人の対戦は存在を伏せる
            throw new NotFoundException("対戦が見つかりません。");
        }
        return match;
    }

    /**
     * 版の指定を確かめる。
     * 着手（moves）は必須（同時に指したときの取り違えを防ぐため）。
     * 状態変更（accept / decline / cancel / resign）は省略できる
     * （画面は版を送らずに操作する。指定されたときだけ照合する）。
     */
    private static int requireVersion(GameMatchEntity match, Integer version, boolean required) {
        if (version == null) {
            if (required) {
                throw new ValidationException("バージョンを指定してください。");
            }
            return match.getVersion() == null ? 1 : match.getVersion();
        }
        return version;
    }

    private static void requireLogin(UserPrincipal user) {
        if (user == null) {
            throw new ValidationException("ログインが必要です。");
        }
    }

    private static boolean isGuardian(AccountEntity account) {
        return account != null && AccountType.GUARDIAN.name().equals(account.getAccountType());
    }

    private static String displayName(AccountEntity account) {
        String name = ((account.getSei() == null ? "" : account.getSei()) + " "
                + (account.getMei() == null ? "" : account.getMei())).trim();
        return name.isEmpty() ? account.getLoginId() : name;
    }

    private static String normalizeGameType(String value) {
        String text = value == null ? "" : value.trim().toUpperCase();
        if (!GameMatchModels.GAME_TYPES.contains(text)) {
            throw new ValidationException("ゲームの種類が不正です。");
        }
        return text;
    }

    /**
     * 挑戦者が持つ石（先手／後手）を確かめる。
     * 省略・null は先手（黒）。BLACK＝先手 / WHITE＝後手。
     */
    private static String normalizeStone(String value) {
        String text = value == null ? "" : value.trim().toUpperCase();
        if (text.isEmpty()) {
            return STONE_BLACK;
        }
        if (!GameMatchModels.STONES.contains(text)) {
            throw new ValidationException("先手・後手の指定が不正です。");
        }
        return text;
    }

    /** DB の JSONB（文字列）を 2 次元の配列へ。 */
    private List<List<String>> parseBoard(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ValidationException("盤面の保存に失敗しました。");
        }
    }

    private GameMatchModels.MatchDetail toDetail(GameMatchEntity match, long viewerAccountId) {
        AccountEntity challenger = accountMapper.findById(match.getChallengerAccountId());
        AccountEntity opponent = accountMapper.findById(match.getOpponentAccountId());
        // 見ている側から見た「相手」がいまゲーム画面を開いているか
        long otherAccountId = match.getChallengerAccountId() != null
                && match.getChallengerAccountId() == viewerAccountId
                ? match.getOpponentAccountId() : match.getChallengerAccountId();
        List<GameMatchModels.MoveRow> moves = mapper.listMoves(match.getMatchId()).stream()
                .map(move -> new GameMatchModels.MoveRow(
                        move.getMoveNumber(),
                        move.getAccountId(),
                        move.getStone(),
                        move.getRowIndex(),
                        move.getColIndex(),
                        "1".equals(move.getPassFlag()),
                        parseCells(move.getFlipped()),
                        parseCells(move.getLine())))
                .toList();
        return new GameMatchModels.MatchDetail(
                match.getMatchId(),
                match.getGameType(),
                match.getStatus(),
                GameBoard.fromJson(match.getGameType(), parseBoard(match.getBoard())).toJson(),
                currentStone(match, viewerAccountId),
                match.getChallengerStone() == null ? STONE_BLACK : match.getChallengerStone(),
                match.getTurnAccountId(),
                match.getTurnAccountId() != null && match.getTurnAccountId() == viewerAccountId,
                match.getMoveCount() == null ? 0 : match.getMoveCount(),
                match.getWinnerAccountId(),
                match.getResultCode(),
                match.getVersion() == null ? 1 : match.getVersion(),
                match.getChallengerAccountId(),
                match.getOpponentAccountId(),
                challenger == null ? null : displayName(challenger),
                opponent == null ? null : displayName(opponent),
                publisher.isOnline(otherAccountId),
                match.getFinishedAt() == null ? null : match.getFinishedAt().toLocalDateTime(),
                moves);
    }

    /** [[行,列], ...] の JSON を数値の配列へ。 */
    private List<List<Integer>> parseCells(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
