<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '@study21/web-shared'
import { loginAccount } from '@/api/register'
import { useAuthStore } from '@/stores/auth'

/**
 * 管理者ログイン。
 *
 * 2026-09-14 の決定（Q8）で**実認証**にした（以前は UI 確認用の仮認証で、サーバへ送っていなかった）。
 * 読書管理の【全体書籍】は管理者が管理するため、管理者も user-api のセッションを持つ必要がある
 * （`POST /api/user/login` は管理者（ADMIN）も認証する）。パスワードは保存しない。
 */
const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const toast = useToast()
const auth = useAuthStore()
const router = useRouter()

async function onSubmit(): Promise<void> {
  if (submitting.value) return

  const loginId = username.value.trim()
  if (loginId === '' || password.value === '') {
    errorMessage.value = 'ユーザーIDとパスワードを入力してください。'
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    const response = await loginAccount({ loginId, password: password.value })
    const account = response.data
    if (account.accountType !== 'ADMIN') {
      // 一般ユーザーのアカウントでは管理者画面に入れない（画面のロールも作らない）
      errorMessage.value = '管理者アカウントではありません。'
      toast.danger(errorMessage.value)
      return
    }
    auth.login(account.accountType, account.displayName || account.loginId)
    toast.success('ログインしました')
    await router.push('/admin/home')
  } catch (error) {
    const message = error instanceof Error ? error.message : 'ログインに失敗しました。'
    errorMessage.value = message
    toast.danger(message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login">
    <form class="login__card" @submit.prevent="onSubmit">
      <h1 class="login__title">管理者ログイン</h1>
      <p class="login__hint">管理者専用のログインページです。</p>
      <label class="login__field">
        <span class="login__label">ユーザーID</span>
        <input v-model="username" type="text" autocomplete="username" />
      </label>
      <label class="login__field">
        <span class="login__label">パスワード</span>
        <input v-model="password" type="password" autocomplete="current-password" />
      </label>
      <p v-if="errorMessage" class="login__error" role="alert">{{ errorMessage }}</p>
      <button type="submit" class="login__submit" :disabled="submitting">
        {{ submitting ? 'ログイン中…' : 'ログイン' }}
      </button>
      <RouterLink to="/login" class="login__link">一般ユーザーログイン</RouterLink>
    </form>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-4);
}
.login__card {
  width: 100%;
  max-width: 380px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--sp-6);
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  box-shadow: var(--shadow-md);
}
.login__title {
  margin: 0;
  font-size: var(--fs-2xl);
  font-weight: var(--fw-semibold);
}
.login__hint {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--fs-sm);
}
.login__field {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}
.login__label {
  font-size: var(--fs-sm);
  color: var(--color-text-muted);
}
.login__field input {
  min-height: var(--control-md);
  padding: 0 var(--sp-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  font-size: var(--fs-md);
  color: var(--color-text);
  background: var(--color-surface);
}
.login__submit {
  min-height: var(--control-md);
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: var(--fs-sm);
  font-weight: var(--fw-medium);
  cursor: pointer;
}
.login__submit:hover {
  background: var(--color-primary-hover);
}
.login__submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.login__error {
  margin: 0;
  color: var(--color-danger);
  font-size: var(--fs-sm);
}
.login__link {
  text-align: center;
  font-size: var(--fs-sm);
}
</style>
