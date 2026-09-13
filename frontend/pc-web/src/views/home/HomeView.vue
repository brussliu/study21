<script setup lang="ts">
import { computed } from 'vue'
import StudyOverview from '@/components/home/StudyOverview.vue'
import StudyTimers from '@/components/home/StudyTimers.vue'
import NetUsageChart from '@/components/home/NetUsageChart.vue'
import ActionTimeline from '@/components/home/ActionTimeline.vue'
import WordTestStats from '@/components/home/WordTestStats.vue'
import '@/features/home/home.css'

/**
 * ホーム（生徒・管理者）。
 *
 * 2.0 の english.jsp（英語勉強）が出していた内容をホームにまとめた:
 *   ・KPI（総学習時間 / 学習レベル / 今日の学習時間 / 今日の読書時間 / 連続学習日数）
 *   ・レーダーチャート（初級・中級。現在値と目標値）
 *   ・単語帳・熟語帳・テスト結果・単語の学習状況の一覧
 *
 * さらに 2.0 の home.jsp（ホーム）から次を足した:
 *   ・学習・休憩・ゲームタイマー（`components/home/StudyTimers.vue`。2.1 はブラウザ内で保持）
 *   ・行動タイムライン、最近3日 上網状況、直近単語テスト情報統計
 *
 * 中身は `components/home/StudyOverview.vue` に置き、保護者ホーム（ParentHomeView）と共用する
 * （保護者は「お子さまの学習状況」として同じ内容を見る）。
 */
/** 画面上の表示名（仮認証のセッションから取る）。 */
const displayName = computed(() => {
  try {
    const raw = window.sessionStorage.getItem('study21.auth.v2')
    const parsed = raw ? JSON.parse(raw) : null
    return parsed?.username ? String(parsed.username) : 'ゲスト'
  } catch {
    return 'ゲスト'
  }
})

const today = new Date()
const todayLabel = `${today.getFullYear()}年${today.getMonth() + 1}月${today.getDate()}日（${
  ['日', '月', '火', '水', '木', '金', '土'][today.getDay()]
}）`
</script>

<template>
  <div class="home-page">
    <!-- あいさつ -->
    <div class="home-hello">
      <span class="home-hello__name">{{ displayName }} さん、おはようございます</span>
      <span class="home-hello__date">{{ todayLabel }}</span>
      <span class="home-hello__spacer" />
      <span class="badge badge--success">連続学習 14 日</span>
    </div>

    <!-- 学習・休憩・ゲームタイマー（2.0 home.jsp のタイマーカード） -->
    <StudyTimers />

    <StudyOverview />

    <!-- 行動タイムライン: 学習状況モニターの分析結果（いまはサンプルデータ） -->
    <ActionTimeline />

    <!-- 最近3日 上網状況（時間帯別）: 実データ（プロキシの通信履歴） -->
    <NetUsageChart />

    <!-- 直近単語テスト情報統計: いまはサンプルデータ -->
    <WordTestStats />
  </div>
</template>
