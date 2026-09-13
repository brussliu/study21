<script setup lang="ts">
import StudyOverview from '@/components/home/StudyOverview.vue'
import SystemStatus from '@/components/SystemStatus.vue'
import { useToast } from '@study21/web-shared'

const toast = useToast()

/** 将来の保護者機能の表示プレビュー（モックデータ）。バックエンド接続後に置き換える。 */
interface MockStudent {
  id: string
  avatar: string
  avatarClass?: string
  name: string
  grade: string
  furigana: string
  badge: 'primary' | 'neutral'
  badgeLabel: string
}

const account = {
  email: 'yamada@example.com',
  start: '2026/08/31',
  end: '2026/09/30'
}

const students: MockStudent[] = [
  {
    id: 's1',
    avatar: '山',
    name: '山田 太郎',
    grade: '小学3年生',
    furigana: 'やまだ たろう',
    badge: 'primary',
    badgeLabel: '初期登録'
  },
  {
    id: 's2',
    avatar: '花',
    avatarClass: 'student-avatar--info',
    name: '山田 花子',
    grade: '小学1年生',
    furigana: 'やまだ はなこ',
    badge: 'neutral',
    badgeLabel: '追加'
  }
]
</script>

<template>
  <div class="page">
    <SystemStatus />

    <div class="page-head">
      <h1 class="page-head__title">ホーム</h1>
    </div>

    <section class="panel" aria-label="アカウント情報">
      <h2 class="panel__title">アカウント情報</h2>
      <div class="def-list">
        <div class="def-list__row">
          <span class="def-list__label">メールアドレス</span>
          <span class="def-list__value">{{ account.email }}</span>
        </div>
        <div class="def-list__row">
          <span class="def-list__label">登録日</span>
          <span class="def-list__value">{{ account.start }}</span>
        </div>
        <div class="def-list__row">
          <span class="def-list__label">ご利用期限</span>
          <span class="def-list__value">
            <span>{{ account.end }}</span>
            <span class="badge badge--success">有効（残り30日）</span>
          </span>
        </div>
        <div class="def-list__row">
          <span class="def-list__label">プラン</span>
          <span class="def-list__value">
            <span>無料体験</span>
            <button type="button" class="btn btn--ghost" @click="toast.info('（設計案）更新手続き画面を開きます')">
              更新手続き
            </button>
          </span>
        </div>
      </div>
    </section>

    <section class="panel" aria-label="生徒一覧">
      <div class="panel__head">
        <h2 class="panel__title">
          生徒一覧
          <span class="badge badge--neutral">{{ students.length }}名</span>
        </h2>
        <button type="button" class="btn btn--secondary" @click="toast.info('（設計案）生徒追加画面へ遷移します')">
          ＋ 生徒を追加
        </button>
      </div>
      <div class="student-list">
        <div v-for="student in students" :key="student.id" class="student-row">
          <div class="student-avatar" :class="student.avatarClass">{{ student.avatar }}</div>
          <div class="student-info">
            <span class="student-info__name">
              {{ student.name }}
              <span class="badge" :class="`badge--${student.badge}`">{{ student.badgeLabel }}</span>
            </span>
            <span class="student-info__meta">{{ student.grade }} ・ ふりがな：{{ student.furigana }}</span>
          </div>
          <button type="button" class="btn btn--ghost" @click="toast.info(`（設計案）${student.name} さんの情報編集画面を開きます`)">
            編集
          </button>
        </div>
      </div>
    </section>

    <!-- 学習状況（2.0 の english.jsp の内容。生徒ホームと同じ構成でお子さまの状況を見る） -->
    <section class="panel panel--plain" aria-label="お子さまの学習状況">
      <div class="panel__head">
        <h2 class="panel__title">お子さまの学習状況</h2>
        <span class="panel__meta">{{ students[0]?.name }}（{{ students[0]?.grade }}）</span>
      </div>
      <StudyOverview :learner="students[0]?.name ?? ''" />
    </section>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
  flex-wrap: wrap;
}
.page-head__title {
  margin: 0;
  font-size: var(--fs-2xl);
  font-weight: var(--fw-semibold);
}

.panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: var(--sp-5);
  margin-bottom: var(--sp-4);
}
.panel__title {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: var(--fw-semibold);
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}
.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

.def-list {
  display: flex;
  flex-direction: column;
}
.def-list__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-2) 0;
  font-size: var(--fs-sm);
}
.def-list__row + .def-list__row {
  border-top: 1px dashed var(--color-border);
}
.def-list__label {
  color: var(--color-text-muted);
  flex: 0 0 auto;
}
.def-list__value {
  color: var(--color-text);
  font-weight: var(--fw-medium);
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.student-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  margin-top: var(--sp-4);
}
.student-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}
.student-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  flex: 0 0 auto;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-md);
  font-weight: var(--fw-semibold);
}
.student-avatar--info {
  background: var(--color-info-soft);
  color: var(--color-info);
}
.student-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.student-info__name {
  font-size: var(--fs-md);
  font-weight: var(--fw-semibold);
  color: var(--color-text);
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}
.student-info__meta {
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}

/* 学習状況（StudyOverview）は自前のカードを持つため、外側の panel は枠を持たない。 */
.panel--plain {
  background: transparent;
  border: 0;
  box-shadow: none;
  padding: 0;
}
.panel--plain .panel__head {
  margin-bottom: var(--sp-3);
}
.panel__meta {
  color: var(--color-text-muted);
  font-size: var(--fs-sm);
}
</style>
