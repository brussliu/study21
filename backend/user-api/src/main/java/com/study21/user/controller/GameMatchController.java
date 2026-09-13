package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.game.GameMatchModels;
import com.study21.user.game.GameMatchService;
import com.study21.user.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * ゲーム対戦 API（user-api）。五子棋・黑白棋を生徒と保護者が同時に対戦する。
 *
 * <p>ログイン必須で、**自分が関わっている対戦だけ**が見える。
 * 対戦の通知は SSE（`/games/stream`）で受け取り、内容は必ず
 * `GET /games/matches/{id}` で読み直す（DB が唯一の正）。</p>
 */
@RestController
@RequestMapping("/api/user/games")
public class GameMatchController {

    private final GameMatchService gameMatchService;

    public GameMatchController(GameMatchService gameMatchService) {
        this.gameMatchService = gameMatchService;
    }

    /** 対戦できる相手（親なら子ども、子なら親）。 */
    @GetMapping("/opponents")
    public ApiResponse<GameMatchModels.OpponentList> opponents(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(gameMatchService.listOpponents(user));
    }

    /** 自分の対戦一覧（新しい順）。 */
    @GetMapping("/matches")
    public ApiResponse<GameMatchModels.MatchList> matches(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(gameMatchService.listMatches(user));
    }

    /** 対戦の詳細（盤面と指し手）。 */
    @GetMapping("/matches/{matchId}")
    public ApiResponse<GameMatchModels.MatchDetail> match(@AuthenticationPrincipal UserPrincipal user,
                                                          @PathVariable long matchId) {
        return ApiResponse.ok(gameMatchService.getMatch(user, matchId));
    }

    /** 招待する（状態は WAITING）。 */
    @PostMapping("/matches")
    public ApiResponse<GameMatchModels.MatchDetail> create(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody GameMatchModels.CreateRequest request) {
        return ApiResponse.ok(gameMatchService.createMatch(user, request));
    }

    /** 招待を承諾する。 */
    @PostMapping("/matches/{matchId}/accept")
    public ApiResponse<GameMatchModels.MatchDetail> accept(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long matchId,
            @RequestBody(required = false) GameMatchModels.StateChangeRequest request) {
        return ApiResponse.ok(gameMatchService.acceptMatch(user, matchId, versionOf(request)));
    }

    /** 招待を断る。 */
    @PostMapping("/matches/{matchId}/decline")
    public ApiResponse<GameMatchModels.MatchDetail> decline(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long matchId,
            @RequestBody(required = false) GameMatchModels.StateChangeRequest request) {
        return ApiResponse.ok(gameMatchService.declineMatch(user, matchId, versionOf(request)));
    }

    /** 招待を取り消す（挑戦者のみ）。 */
    @PostMapping("/matches/{matchId}/cancel")
    public ApiResponse<GameMatchModels.MatchDetail> cancel(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long matchId,
            @RequestBody(required = false) GameMatchModels.StateChangeRequest request) {
        return ApiResponse.ok(gameMatchService.cancelMatch(user, matchId, versionOf(request)));
    }

    /** 相手が不在（ゲーム画面を閉じた）ときに対局を終了する。残った側の勝ち。 */
    @PostMapping("/matches/{matchId}/timeout")
    public ApiResponse<GameMatchModels.MatchDetail> timeout(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long matchId,
            @RequestBody(required = false) GameMatchModels.StateChangeRequest request) {
        return ApiResponse.ok(gameMatchService.timeoutMatch(user, matchId, versionOf(request)));
    }

    /** 投了する。 */
    @PostMapping("/matches/{matchId}/resign")
    public ApiResponse<GameMatchModels.MatchDetail> resign(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long matchId,
            @RequestBody(required = false) GameMatchModels.StateChangeRequest request) {
        return ApiResponse.ok(gameMatchService.resignMatch(user, matchId, versionOf(request)));
    }

    /** 着手する（判定はサーバー。不正な手は 400、版が合わなければ 409）。 */
    @PostMapping("/matches/{matchId}/moves")
    public ApiResponse<GameMatchModels.MatchDetail> move(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long matchId,
            @Valid @RequestBody GameMatchModels.MoveRequest request) {
        return ApiResponse.ok(gameMatchService.move(user, matchId, request));
    }

    /** 対戦の通知（SSE）。invited / accepted / move / finished / declined / cancelled / ping。 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal UserPrincipal user) {
        return gameMatchService.subscribe(user);
    }

    private static Integer versionOf(GameMatchModels.StateChangeRequest request) {
        return request == null ? null : request.version();
    }
}
