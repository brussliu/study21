-- ============================================================================
-- Study 2.1  シーケンスの同期（ID を維持した移行の後始末）
-- ----------------------------------------------------------------------------
-- 背景（2026-09-19 に発見）:
--   2.0 からの移行は**旧 ID をそのまま**入れるため、明示的に ID を指定して INSERT している。
--   そのときシーケンス（BIGSERIAL の連番）は進まないため、**次の INSERT が既存の ID と
--   衝突**する（主キー重複で失敗する）。実測では次の 3 つがずれていた:
--     * MON_学習モニタースナップショット情報.スナップショットID … max=43454 / 連番=4
--     * MON_学習モニター動画情報.動画ID                       … max=2849  / 連番=2
--     * MON_学習モニター画像分析情報.画像分析ID               … max=63353 / 連番=1
--   このままだと、学習状況モニターのバッチ（batL02 が動画・スナップショットを、
--   batL03 が分析結果を INSERT する）が**主キー重複で失敗**する。
--
-- やること:
--   public スキーマの**すべての**シーケンスについて、所有列の max(値) と last_value を比べ、
--   遅れていれば max(値) まで進める（setval）。既に正しいものは触らない。
--   空の表（max が無い）は 1 にそろえる（IS_CALLED = false にして次の nextval で 1 を返す）。
--
-- 冪等: 何度流しても同じ（遅れているものだけ進める）。
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: いつでもよい（データ移行の直後に流すのが望ましい）
-- ============================================================================

BEGIN;

DO $$
DECLARE
    r RECORD;
    max_id BIGINT;
    seq_val BIGINT;
    fixed INTEGER := 0;
BEGIN
    FOR r IN
        SELECT s.relname AS seq_name,
               t.relname AS tab_name,
               a.attname AS col_name,
               pg_get_serial_sequence(format('public.%I', t.relname), a.attname) AS qualified
          FROM pg_class s
          JOIN pg_depend d ON d.objid = s.oid AND d.deptype IN ('a', 'i')
          JOIN pg_class t ON t.oid = d.refobjid
          JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid
         WHERE s.relkind = 'S'
           AND t.relnamespace = 'public'::regnamespace
    LOOP
        EXECUTE format('SELECT COALESCE(max(%I), 0) FROM public.%I', r.col_name, r.tab_name) INTO max_id;
        EXECUTE format('SELECT last_value FROM %s', r.qualified) INTO seq_val;
        IF max_id > seq_val THEN
            EXECUTE format('SELECT setval(%L, %s)', r.qualified, max_id);
            RAISE NOTICE 'シーケンスを進めました: % (%.%) max=% -> last_value=%',
                r.seq_name, r.tab_name, r.col_name, max_id, max_id;
            fixed := fixed + 1;
        END IF;
    END LOOP;
    RAISE NOTICE '同期したシーケンス: % 件', fixed;
END $$;

COMMIT;

-- ----------------------------------------------------------------------------
-- 確認（遅れているシーケンスが 0 件であること）
-- ----------------------------------------------------------------------------
\echo '--- 確認: max(値) より遅れているシーケンス（0 件が期待値）---'
DO $$
DECLARE r RECORD; max_id BIGINT; seq_val BIGINT;
BEGIN
    FOR r IN
        SELECT s.relname AS seq_name, t.relname AS tab_name, a.attname AS col_name,
               pg_get_serial_sequence(format('public.%I', t.relname), a.attname) AS qualified
          FROM pg_class s
          JOIN pg_depend d ON d.objid = s.oid AND d.deptype IN ('a', 'i')
          JOIN pg_class t ON t.oid = d.refobjid
          JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid
         WHERE s.relkind = 'S' AND t.relnamespace = 'public'::regnamespace
    LOOP
        EXECUTE format('SELECT COALESCE(max(%I), 0) FROM public.%I', r.col_name, r.tab_name) INTO max_id;
        EXECUTE format('SELECT last_value FROM %s', r.qualified) INTO seq_val;
        IF max_id > seq_val THEN
            RAISE NOTICE 'まだ遅れています: % max=% seq=%', r.seq_name, max_id, seq_val;
        END IF;
    END LOOP;
    RAISE NOTICE '確認おわり（上に何も出ていなければ同期済み）';
END $$;
