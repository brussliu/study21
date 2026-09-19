package com.study21.user.geometry;

import com.study21.user.security.UserPrincipal;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 生図（画像から作図）と AI 画図助手の業務処理（user-api）。
 *
 * <p>画面向けの入口がここ。AI を呼ぶ本体（batC51/52/53）は admin-api のバッチで、
 * 両者は**DB の状態列だけで橋渡し**する（`docs/ARCHITECTURE.md` §3 がサービス間の
 * 相互呼び出しを禁じているため）。画面の契約は**送信 → ポーリング**。</p>
 *
 * <p>**他人の要求は見えない**（`登録者アカウントID` が自分の行だけ）。元画像は写真で
 * 図形（教材）より機微なので、家族共有にはしない（設計 §4.4）。</p>
 */
public interface GeometryAiService {

    /** 画面が使う設定（有効／無効・上限・既定値・今日の使用回数）。API Key は返さない。 */
    GeometryAiModels.OptionsResult options(UserPrincipal user);

    /** 画像をアップロードする（AI へはまだ送らない）。 */
    GeometryAiModels.UploadResult upload(UserPrincipal user, MultipartFile file);

    /** リクエストを作る（状態 = QUEUED）。 */
    GeometryAiModels.RequestStatus create(UserPrincipal user, GeometryAiModels.CreateRequest request);

    /** 自分の履歴（新しい順）。 */
    /**
     * 図形管理の一覧に出す AI 生図のタスク（保存済は除く）。
     */
    GeometryAiModels.TaskListResult tasks(UserPrincipal user, Integer limit);

    GeometryAiModels.RequestListResult list(UserPrincipal user, String status, int page, int size);

    /** 1 件（ポーリングの主対象）。 */
    GeometryAiModels.RequestDetail detail(UserPrincipal user, long requestId);

    /** 画像の配信（original / cropped。無ければ null）。 */
    GeometryAiModels.ImageData image(UserPrincipal user, long requestId, String kind);

    /** 【もう一度生成】。AI の成果物を消して前処理済みへ戻す。 */
    GeometryAiModels.RequestStatus retry(UserPrincipal user, long requestId, Integer version);

    /** 取消（QUEUED / PREPROCESSED のみ）。 */
    /**
     * 追加入力待ち・失敗した要求を条件を直して送り直す（同じ要求行を使い回す）。
     */
    GeometryAiModels.RequestStatus resubmit(UserPrincipal user, long requestId,
                                            GeometryAiModels.ResubmitRequest request);

    GeometryAiModels.RequestStatus cancel(UserPrincipal user, long requestId, Integer version);

    /**
     * **タスクを一覧から消す**（状態を取消にする。行は監査のため残す）。
     *
     * <p>【取消】と違って**どの状態でも消せる**（生成済みの作図・失敗したタスク・追加入力待ちを
     * そのまま片付けられる）。図形として保存済みのものは消せない（図形一覧から削除する）。</p>
     */
    GeometryAiModels.RequestStatus discard(UserPrincipal user, long requestId, Integer version);

    /** 図形として登録する（作図画面で確認・調整したあとの保存。登録元コード = 'AI'）。 */
    GeometryAiModels.ConfirmResult confirm(UserPrincipal user, long requestId,
                                           GeometryAiModels.ConfirmRequest request);

    /**
     * AI 画図助手への**依頼を作る**（AI は呼ばない。`生成状態=PENDING` の行を作るだけ）。
     *
     * <p>AI の実行は admin-api のバッチ batC52。画面は返ってきた `runPath` を 1 回だけ叩き、
     * `getAssist` をポーリングする（送信 → ポーリングの契約）。</p>
     */
    GeometryAiModels.AssistStatus assist(UserPrincipal user, GeometryAiModels.AssistRequest request);

    /** 依頼の現在の状態（ポーリングの主対象）。 */
    GeometryAiModels.AssistDetail getAssist(UserPrincipal user, long assistId);

    /**
     * 図形ごとの指示履歴（古い順。**自分が作った行だけ**）。
     *
     * <p>会話ログを端末をまたいで見せるためのもの（`localStorage` だけだと他の端末で
     * 履歴が消える）。`figureId` は必須で、新規作図中（図形になっていない）は履歴も無い。</p>
     */
    GeometryAiModels.AssistHistoryResult assistHistory(UserPrincipal user, Long figureId, Integer limit);

    /** 【反映】（適用区分 = APPLIED。生成状態 = READY の行だけ）。 */
    GeometryAiModels.AssistResult markAssistApplied(UserPrincipal user, long assistId, String mode);

    /**
     * 変更案を**作図に反映しなかった**（適用区分 = REJECTED。生成状態 = READY の行だけ）。
     *
     * @param reason 反映できなかった理由（どの行で失敗したか）。利用者の【破棄】なら null。
     *               `エラーメッセージ` に残して、端末をまたいでも理由が見えるようにする。
     */
    GeometryAiModels.AssistResult markAssistRejected(UserPrincipal user, long assistId, String reason);
}
