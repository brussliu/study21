<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import UserProfileDialog from '@/components/account/UserProfileDialog.vue'
import PasswordChangeDialog from '@/components/account/PasswordChangeDialog.vue'
import BrowserPluginMenu from '@/features/browserext/BrowserPluginMenu.vue'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { reloadParentPage } from '@/features/user-profile/reloadPage'
import '@/features/user-profile/user-profile.css'

defineEmits<{
  'toggle-sidebar': []
}>()

const auth = useAuthStore()
const theme = useThemeStore()

const now = ref(new Date())
const weekdays = ['日', '月', '火', '水', '木', '金', '土']
let timer: number | undefined

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

const timeText = computed(() => {
  const d = now.value
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
})

const dayText = computed(() => {
  const d = now.value
  return `${d.getFullYear()}年${pad(d.getMonth() + 1)}月${pad(d.getDate())}日（${weekdays[d.getDay()]}）`
})

const roleText = computed(() =>
  auth.role === 'ADMIN' ? '管理者' : auth.role === 'GUARDIAN' ? '保護者' : '学生'
)

/** 右上のユーザー名メニュー（ユーザー情報の修正 / パスワードの変更）。 */
const anchor = ref<HTMLElement | null>(null)
const menuOpen = ref(false)
/** ユーザー情報の修正 / パスワードの変更（ページ遷移せずダイアログで開く）。 */
const profileDialogOpen = ref(false)
const passwordDialogOpen = ref(false)

/**
 * メニューを出すのは保護者・生徒だけ（管理者はアカウントの系統が別で、
 * この機能の対象外のため出さない）。
 */
const canEditProfile = computed(() => auth.role === 'STUDENT' || auth.role === 'GUARDIAN')

function toggleMenu(): void {
  menuOpen.value = !menuOpen.value
}

/** 情報の修正とパスワードの変更は別ダイアログで開く（ページ遷移しない）。 */
function openProfile(section: 'profile' | 'password'): void {
  menuOpen.value = false
  if (section === 'profile') {
    profileDialogOpen.value = true
  } else {
    passwordDialogOpen.value = true
  }
}

/** ダイアログで保存できたら、開いていた親ページを再読み込みして変更を反映する。 */
function onAccountSaved(): void {
  profileDialogOpen.value = false
  passwordDialogOpen.value = false
  reloadParentPage()
}

/** メニュー外のクリックで閉じる（開くボタン自身のクリックは無視する）。 */
function onDocumentClick(event: MouseEvent): void {
  if (!menuOpen.value) return
  if (anchor.value?.contains(event.target as Node)) return
  menuOpen.value = false
}

function onDocumentKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && menuOpen.value) menuOpen.value = false
}

onMounted(() => {
  timer = window.setInterval(() => {
    now.value = new Date()
  }, 1000)
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onDocumentKeydown)
})

onBeforeUnmount(() => {
  if (timer !== undefined) window.clearInterval(timer)
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onDocumentKeydown)
})
</script>

<template>
  <header class="topbar">
    <div class="topbar__left">
      <button
        type="button"
        class="btn btn--icon btn--sm"
        aria-label="サイドバー切替"
        @click="$emit('toggle-sidebar')"
      >
        <AppIcon name="menu" />
      </button>
      <div class="topbar__datetime">
        <span>{{ dayText }}</span>
        <span class="time">{{ timeText }}</span>
      </div>
      <span class="topbar__inspire">日拱一卒，功不唐捐</span>
    </div>
    <div class="topbar__right">
      <span class="topbar__meta">
        <AppIcon name="user" size="sm" />
        <span>{{ roleText }}</span>
      </span>

      <!-- プラグイン（ブラウザ拡張）のダウンロード。学生・保護者の表示のとなりに置く -->
      <BrowserPluginMenu />

      <div ref="anchor" class="up-anchor">
        <button
          type="button"
          class="topbar__user up-user"
          :class="{ 'is-open': menuOpen }"
          aria-haspopup="menu"
          :aria-expanded="menuOpen ? 'true' : 'false'"
          @click="toggleMenu"
        >
          <AppIcon name="user" />
          <span>{{ auth.currentUser ?? '未ログイン' }}</span>
          <AppIcon name="chevron-down" size="sm" class="up-user__caret" />
        </button>

        <div v-if="menuOpen" class="up-menu" role="menu" aria-label="ユーザーメニュー">
          <template v-if="canEditProfile">
            <button type="button" class="up-menu__item" role="menuitem" @click="openProfile('profile')">
              <AppIcon name="edit" size="sm" class="icon--edit" /> ユーザー情報の修正
            </button>
            <button type="button" class="up-menu__item" role="menuitem" @click="openProfile('password')">
              <AppIcon name="shield" size="sm" /> パスワードの変更
            </button>
          </template>
        </div>
      </div>

      <button
        type="button"
        class="btn btn--icon btn--sm"
        :aria-label="theme.theme === 'dark' ? 'ライトテーマへ切替' : 'ダークテーマへ切替'"
        @click="theme.toggle()"
      >
        <AppIcon :name="theme.theme === 'dark' ? 'sun' : 'moon'" />
      </button>
    </div>

    <UserProfileDialog
      :open="profileDialogOpen"
      @close="profileDialogOpen = false"
      @saved="onAccountSaved"
    />
    <PasswordChangeDialog
      :open="passwordDialogOpen"
      @close="passwordDialogOpen = false"
      @saved="onAccountSaved"
    />
  </header>
</template>
