<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'

type Step = 'email' | 'sent' | 'reset' | 'done'

const step = ref<Step>('email')
const heading = ref<HTMLElement | null>(null)
const email = ref('')
const password = ref('')
const confirmation = ref('')
const errors = reactive({ email: '', password: '', confirmation: '' })
const titles: Record<Step, string> = {
  email: 'パスワードをお忘れの方',
  sent: 'メールをご確認ください',
  reset: '新しいパスワードを設定',
  done: 'パスワードを再設定しました'
}
const currentStep = computed(() => step.value === 'done' ? 3 : step.value === 'reset' ? 2 : 1)

async function goTo(next: Step): Promise<void> {
  step.value = next
  await nextTick()
  heading.value?.focus()
}

function sendEmail(): void {
  email.value = email.value.trim()
  errors.email = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value)
    ? '' : '有効なメールアドレスを入力してください。'
  if (!errors.email) void goTo('sent')
}

function resetPassword(): void {
  errors.password = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(password.value)
    ? '' : '8文字以上で、英字と数字をそれぞれ1文字以上含めてください。'
  errors.confirmation = confirmation.value && confirmation.value === password.value
    ? '' : 'パスワードが一致しません。'
  if (errors.password || errors.confirmation) return
  // 演示のみ。パスワードは送信・保存せず、完了時に入力値を破棄する。
  password.value = ''
  confirmation.value = ''
  void goTo('done')
}
</script>

<template>
  <main class="recovery">
    <section class="recovery__card" aria-labelledby="recovery-title">
      <p class="recovery__eyebrow">アカウントサポート</p>
      <h1 id="recovery-title" ref="heading" tabindex="-1">{{ titles[step] }}</h1>
      <ol class="recovery__steps" aria-label="再設定の手順">
        <li
          v-for="(label, index) in ['メール確認', '再設定', '完了']" :key="label"
          :class="{ 'is-active': currentStep === index + 1, 'is-complete': currentStep > index + 1 }"
          :aria-current="currentStep === index + 1 ? 'step' : undefined"
        >
          <span>{{ index + 1 }}</span>{{ label }}
        </li>
      </ol>

      <form v-if="step === 'email'" novalidate @submit.prevent="sendEmail">
        <p>登録したメールアドレスを入力してください。パスワード再設定の手順をご案内します。</p>
        <label for="recovery-email">メールアドレス</label>
        <input
          id="recovery-email" v-model="email" type="email" autocomplete="email"
          placeholder="example@example.com" required :aria-invalid="!!errors.email"
          :aria-describedby="errors.email ? 'email-error' : undefined"
        />
        <p v-if="errors.email" id="email-error" class="recovery__error" role="alert">{{ errors.email }}</p>
        <button class="recovery__primary" type="submit">再設定メールを送信</button>
      </form>

      <div v-else-if="step === 'sent'" class="recovery__content">
        <p role="status">登録済みの場合、以下のアドレスに再設定メールが届きます。</p>
        <p class="recovery__email">{{ email }}</p>
        <p>メールが見つからない場合は、迷惑メールフォルダや入力したアドレスをご確認ください。</p>
        <div class="recovery__preview">
          <strong>パスワードの再設定</strong>
          <p>下のボタンから新しいパスワードを設定してください。</p>
          <button class="recovery__primary" type="button" @click="goTo('reset')">再設定に進む</button>
        </div>
        <button class="recovery__text-button" type="button" @click="goTo('email')">メールアドレスを修正する</button>
      </div>

      <form v-else-if="step === 'reset'" novalidate @submit.prevent="resetPassword">
        <p class="recovery__email">{{ email }}</p>
        <label for="recovery-password">新しいパスワード</label>
        <input
          id="recovery-password" v-model="password" type="password" autocomplete="new-password"
          required :aria-invalid="!!errors.password" aria-describedby="password-hint password-error"
        />
        <p id="password-hint" class="recovery__hint">8文字以上・英字と数字をそれぞれ1文字以上</p>
        <p v-if="errors.password" id="password-error" class="recovery__error" role="alert">{{ errors.password }}</p>
        <label for="recovery-confirmation">新しいパスワード（確認）</label>
        <input
          id="recovery-confirmation" v-model="confirmation" type="password" autocomplete="new-password"
          required :aria-invalid="!!errors.confirmation"
          :aria-describedby="errors.confirmation ? 'confirmation-error' : undefined"
        />
        <p v-if="errors.confirmation" id="confirmation-error" class="recovery__error" role="alert">{{ errors.confirmation }}</p>
        <button class="recovery__primary" type="submit">パスワードを再設定</button>
      </form>

      <div v-else class="recovery__content" role="status">
        <div class="recovery__success" aria-hidden="true">✓</div>
        <p>新しいパスワードでログインしてください。</p>
      </div>
      <RouterLink to="/login" class="recovery__back">ログイン画面に戻る</RouterLink>
    </section>
  </main>
</template>

<style scoped>
.recovery {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-4);
  padding-top: calc(var(--sp-4) + env(safe-area-inset-top));
  padding-bottom: calc(var(--sp-4) + env(safe-area-inset-bottom));
}
.recovery__card {
  width: 100%;
  max-width: 440px;
  padding: var(--sp-6);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}
h1 { margin: 0; font-size: var(--fs-2xl); font-weight: var(--fw-semibold); }
p { margin: 0; font-size: var(--fs-sm); line-height: 1.7; color: var(--color-text-muted); }
.recovery__eyebrow { color: var(--color-primary); font-weight: var(--fw-semibold); }
.recovery__preview {
  padding: var(--sp-3);
  border-radius: var(--radius-md);
  background: var(--color-primary-soft);
}
.recovery__steps { display: flex; gap: var(--sp-2); padding: 0; margin: 0; list-style: none; }
.recovery__steps li {
  flex: 1; display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-1);
  color: var(--color-text-muted); font-size: var(--fs-xs);
}
.recovery__steps span {
  display: grid; place-items: center; width: 24px; height: 24px;
  border-radius: 50%; background: var(--color-background);
}
.recovery__steps .is-active { color: var(--color-primary); font-weight: var(--fw-semibold); }
.is-active span, .is-complete span { background: var(--color-primary); color: var(--color-on-primary); }
form, .recovery__content, .recovery__preview { display: flex; flex-direction: column; gap: var(--sp-3); }
label, strong { font-size: var(--fs-sm); }
input {
  width: 100%; min-width: 0; min-height: 44px; padding: 0 var(--sp-3);
  border: 1px solid var(--color-border-strong); border-radius: var(--radius-md);
  font-size: var(--fs-lg); color: var(--color-text); background: var(--color-surface);
}
input[aria-invalid='true'] { border-color: var(--color-danger); }
input:focus-visible, button:focus-visible, a:focus-visible {
  outline: 2px solid var(--color-primary); outline-offset: 3px;
}
.recovery__primary {
  min-height: 44px; padding: var(--sp-2) var(--sp-3); border: 0; border-radius: var(--radius-md);
  background: var(--color-primary); color: var(--color-on-primary);
  font-size: var(--fs-sm); font-weight: var(--fw-medium); cursor: pointer;
}
.recovery__primary:hover { background: var(--color-primary-hover); }
.recovery__text-button, .recovery__back {
  display: block; text-align: center; color: var(--color-primary); font-size: var(--fs-sm);
  padding: var(--sp-2); background: none; border: 0; cursor: pointer; text-decoration: underline;
}
.recovery__hint { font-size: var(--fs-xs); }
.recovery__error { color: var(--color-danger); }
.recovery__email { overflow-wrap: anywhere; color: var(--color-text); font-weight: var(--fw-semibold); }
.recovery__success {
  align-self: center; display: grid; place-items: center; width: 48px; height: 48px;
  border-radius: 50%; color: var(--color-success); background: var(--color-primary-soft); font-size: 24px;
}
@media (max-width: 480px) { .recovery__card { padding: var(--sp-5); } }
</style>
