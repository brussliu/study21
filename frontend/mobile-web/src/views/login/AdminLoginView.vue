<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '@study21/web-shared'
import { useAuthStore } from '@/stores/auth'

const username = ref('')
const password = ref('')
const toast = useToast()
const auth = useAuthStore()
const router = useRouter()

// UI 確認用の仮認証。入力内容はサーバーへ送信せず、そのまま管理者画面へ進む。
async function onSubmit(): Promise<void> {
  auth.fakeLogin('ADMIN', username.value)
  toast.success('ログインしました')
  await router.push('/admin/home')
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
      <button type="submit" class="login__submit">ログイン</button>
      <p class="login__notice" role="status">
        UI確認用の仮認証です。入力内容はサーバーへ送信しません。
      </p>
      <RouterLink to="/login" class="login__link">一般ユーザーログイン</RouterLink>
    </form>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-4);
  padding-top: calc(var(--sp-4) + env(safe-area-inset-top));
}
.login__card {
  width: 100%;
  max-width: 400px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--sp-5);
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
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
  min-height: var(--control-lg);
  padding: 0 var(--sp-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  font-size: var(--fs-md);
  color: var(--color-text);
  background: var(--color-surface);
}
.login__submit {
  min-height: var(--control-lg);
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: var(--fs-md);
  font-weight: var(--fw-medium);
  cursor: pointer;
}
.login__submit:hover {
  background: var(--color-primary-hover);
}
.login__notice {
  margin: 0;
  text-align: center;
  color: var(--color-warning);
  font-size: var(--fs-sm);
}
.login__link {
  text-align: center;
  font-size: var(--fs-sm);
}
</style>
