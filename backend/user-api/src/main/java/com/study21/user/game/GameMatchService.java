package com.study21.user.game;

import com.study21.user.security.UserPrincipal;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * ゲーム対戦の業務処理（五子棋・黑白棋）。
 *
 * <p>盤面・手番・勝敗の判定はすべてサーバー側（{@link GameRules}）で行う。
 * 画面は表示と入力だけを担当する。</p>
 */
public interface GameMatchService {

    /** 対戦できる相手（自分の子ども／自分の親）。 */
    GameMatchModels.OpponentList listOpponents(UserPrincipal user);

    /** 自分の対戦一覧（新しい順）。 */
    GameMatchModels.MatchList listMatches(UserPrincipal user);

    /** 対戦の詳細（盤面と指し手）。自分が関わっていない対戦は 404。 */
    GameMatchModels.MatchDetail getMatch(UserPrincipal user, long matchId);

    /** 招待する（状態は WAITING。挑戦者が先手＝黒）。 */
    GameMatchModels.MatchDetail createMatch(UserPrincipal user, GameMatchModels.CreateRequest request);

    /** 招待を承諾する（相手のみ）。 */
    GameMatchModels.MatchDetail acceptMatch(UserPrincipal user, long matchId, Integer version);

    /** 招待を断る（相手のみ）。 */
    GameMatchModels.MatchDetail declineMatch(UserPrincipal user, long matchId, Integer version);

    /** 招待を取り消す（挑戦者のみ・招待中のみ）。 */
    GameMatchModels.MatchDetail cancelMatch(UserPrincipal user, long matchId, Integer version);

    /**
     * 相手が不在のときに対局を終わらせる（終了）。
     * 相手の接続が切れている（ゲーム画面を閉じた）ときだけ許可し、残った側の勝ちにする。
     */
    GameMatchModels.MatchDetail timeoutMatch(UserPrincipal user, long matchId, Integer version);

    /** 投了する（対戦者のみ・対戦中のみ）。 */
    GameMatchModels.MatchDetail resignMatch(UserPrincipal user, long matchId, Integer version);

    /** 着手する（不正な手は 400、版が合わなければ 409）。 */
    GameMatchModels.MatchDetail move(UserPrincipal user, long matchId, GameMatchModels.MoveRequest request);

    /** SSE の接続。 */
    SseEmitter subscribe(UserPrincipal user);
}
