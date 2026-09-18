<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from '@study21/web-shared'
import { loginAccount } from '@/api/register'
import { useAuthStore } from '@/stores/auth'

const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const toast = useToast()
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

// 新規登録完了後に遷移してきた場合はメールアドレスを事前入力する
onMounted(() => {
  const q = route.query.email
  if (typeof q === 'string' && q.trim() !== '') {
    username.value = q.trim()
  }
})

async function onSubmit(): Promise<void> {
  if (submitting.value) return

  const loginId = username.value.trim()
  if (loginId === '' || password.value === '') {
    errorMessage.value = 'メールアドレスとパスワードを入力してください。'
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    const response = await loginAccount({ loginId, password: password.value })
    const account = response.data
    auth.login(account.accountType, account.displayName || account.loginId)
    toast.success('ログインしました')
    // 管理者（2026-09-14 の決定 Q8 で user-api でも認証できる）は管理画面へ
    await router.push(account.accountType === 'ADMIN'
      ? '/admin/home'
      : account.accountType === 'GUARDIAN' ? '/parent/home' : '/student/home')
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
      <h1 class="login__title">ログイン</h1>
      <p class="login__hint">学生・保護者の方はこちらからログインしてください。</p>
      <label class="login__field">
        <span class="login__label">メールアドレス</span>
        <input v-model="username" type="email" autocomplete="username" placeholder="example@example.com" required />
      </label>
      <label class="login__field">
        <span class="login__label">パスワード</span>
        <input v-model="password" type="password" autocomplete="current-password" required />
      </label>
      <p v-if="errorMessage" class="login__error" role="alert">{{ errorMessage }}</p>
      <RouterLink to="/forgot-password" class="login__link">パスワードをお忘れの方</RouterLink>
      <button type="submit" class="login__submit" :disabled="submitting">
        {{ submitting ? 'ログイン中…' : 'ログイン' }}
      </button>
      <div class="login__divider">または</div>
      <button type="button" class="btn btn--secondary login__register-btn" @click="router.push('/register')">
        新規登録（保護者）
      </button>
      <p class="login__register-hint">メールアドレスで30日間無料体験できます。</p>
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
  cursor: wait;
  opacity: 0.65;
}
.login__error {
  margin: 0;
  color: var(--color-danger, #c62828);
  font-size: var(--fs-sm);
}
.login__divider {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  color: var(--color-text-subtle);
  font-size: var(--fs-xs);
  margin: var(--sp-1) 0;
}
.login__divider::before,
.login__divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--color-border);
}
.login__register-hint {
  margin: 0;
  text-align: center;
  color: var(--color-text-subtle);
  font-size: var(--fs-xs);
}
.login__link {
  text-align: center;
  font-size: var(--fs-sm);
}
.login__register-btn {
  width: 100%;
}
</style>
