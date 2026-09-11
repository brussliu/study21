<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
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
    await router.push(account.accountType === 'GUARDIAN' ? '/parent/home' : '/student/home')
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
.login__submit:disabled {
  cursor: wait;
  opacity: 0.65;
}
.login__error {
  margin: 0;
  color: var(--color-danger, #c62828);
  font-size: var(--fs-sm);
}
.login__link {
  text-align: center;
  font-size: var(--fs-sm);
}
</style>
