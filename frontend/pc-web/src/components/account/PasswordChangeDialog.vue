<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ApiError, PASSWORD_ERROR, PASSWORD_HINT, PASSWORD_RE, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { changePassword } from '@/api/account'
import '@/features/user-profile/user-profile.css'

/**
 * パスワードの変更ダイアログ（保護者・生徒）。
 *
 * ページ遷移はせず、右上のユーザー名メニューから開く。
 * 保存すると `saved` を通知し、親ページが再読み込みして変更を反映する。
 * ユーザー情報の修正は別ダイアログ（UserProfileDialog）で行う。
 * 現在のパスワードで本人確認し、変更後もログインしたままにする。
 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const toast = useToast()

const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const submitted = ref(false)
const saving = ref(false)
const show = reactive({ current: false, next: false, confirm: false })

// 開くたびに入力内容をクリアする（パスワードを残さない）
watch(() => props.open, (open) => {
  if (open) clearForm()
}, { immediate: true })

const errors = computed<Record<string, string>>(() => {
  const result: Record<string, string> = {}
  if (form.currentPassword === '') {
    result.currentPassword = '現在のパスワードを入力してください。'
  }
  if (form.newPassword === '') {
    result.newPassword = '新しいパスワードを入力してください。'
  } else if (!PASSWORD_RE.test(form.newPassword)) {
    result.newPassword = PASSWORD_ERROR
  } else if (form.newPassword === form.currentPassword) {
    result.newPassword = '新しいパスワードは現在のパスワードと違うものを入力してください。'
  }
  if (form.confirmPassword !== form.newPassword) {
    result.confirmPassword = '新しいパスワードが一致しません。'
  }
  return result
})

function errorOf(field: string): string {
  return submitted.value ? (errors.value[field] ?? '') : ''
}

function invalid(field: string): boolean {
  return errorOf(field) !== ''
}

function clearForm(): void {
  form.currentPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  show.current = false
  show.next = false
  show.confirm = false
  submitted.value = false
}

async function submit(): Promise<void> {
  if (saving.value) return
  submitted.value = true
  if (Object.keys(errors.value).length > 0) {
    toast.warning('入力内容を確認してください。')
    return
  }
  saving.value = true
  try {
    await changePassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword
    })
    clearForm()
    toast.success('パスワードを変更しました。')
    emit('close')
    // 親ページを再読み込みする（ログインは継続する）
    emit('saved')
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : 'パスワードを変更できませんでした。')
  } finally {
    saving.value = false
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && props.open) emit('close')
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="overlay">
      <section class="dialog dialog--md" role="dialog" aria-modal="true" aria-labelledby="pwDialogTitle">
        <div class="dialog__head">
          <h2 id="pwDialogTitle" class="dialog__title">
            <AppIcon name="shield" size="sm" /> パスワードの変更
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="emit('close')">
            <AppIcon name="x" size="sm" />
          </button>
        </div>

        <div class="dialog__body up-body">
          <p class="field__hint up-lead">
            現在のパスワードで本人確認してから変更します。変更後もログインしたままご利用いただけます。
          </p>
          <div class="up-grid">
            <div class="field up-field--wide">
              <label class="field__label" for="pwCurrent">現在のパスワード<span class="up-required">必須</span></label>
              <span class="up-password-row">
                <input
                  id="pwCurrent" v-model="form.currentPassword" class="input"
                  :type="show.current ? 'text' : 'password'" autocomplete="current-password"
                  :class="{ 'is-invalid': invalid('currentPassword') }"
                />
                <button
                  type="button" class="btn btn--icon btn--sm"
                  :aria-label="show.current ? '現在のパスワードを隠す' : '現在のパスワードを表示'"
                  @click="show.current = !show.current"
                >
                  <AppIcon name="eye" size="sm" class="icon--view" />
                </button>
              </span>
              <p v-if="errorOf('currentPassword')" class="field__error">{{ errorOf('currentPassword') }}</p>
            </div>

            <div class="field">
              <label class="field__label" for="pwNew">新しいパスワード<span class="up-required">必須</span></label>
              <span class="up-password-row">
                <input
                  id="pwNew" v-model="form.newPassword" class="input"
                  :type="show.next ? 'text' : 'password'" autocomplete="new-password"
                  :class="{ 'is-invalid': invalid('newPassword') }"
                />
                <button
                  type="button" class="btn btn--icon btn--sm"
                  :aria-label="show.next ? '新しいパスワードを隠す' : '新しいパスワードを表示'"
                  @click="show.next = !show.next"
                >
                  <AppIcon name="eye" size="sm" class="icon--view" />
                </button>
              </span>
              <p v-if="errorOf('newPassword')" class="field__error">{{ errorOf('newPassword') }}</p>
              <p v-else class="field__hint">{{ PASSWORD_HINT }}</p>
            </div>

            <div class="field">
              <label class="field__label" for="pwConfirm">新しいパスワード（確認）<span class="up-required">必須</span></label>
              <span class="up-password-row">
                <input
                  id="pwConfirm" v-model="form.confirmPassword" class="input"
                  :type="show.confirm ? 'text' : 'password'" autocomplete="new-password"
                  :class="{ 'is-invalid': invalid('confirmPassword') }"
                />
                <button
                  type="button" class="btn btn--icon btn--sm"
                  :aria-label="show.confirm ? '確認用パスワードを隠す' : '確認用パスワードを表示'"
                  @click="show.confirm = !show.confirm"
                >
                  <AppIcon name="eye" size="sm" class="icon--view" />
                </button>
              </span>
              <p v-if="errorOf('confirmPassword')" class="field__error">{{ errorOf('confirmPassword') }}</p>
            </div>
          </div>
        </div>

        <div class="dialog__foot">
          <div class="up-foot">
            <button type="button" class="btn btn--ghost btn--sm" :disabled="saving" @click="clearForm">
              <AppIcon name="rotate" size="sm" /> 入力をクリア
            </button>
            <span class="up-foot__actions">
              <button type="button" class="btn btn--secondary" @click="emit('close')">キャンセル</button>
              <button type="button" class="btn btn--primary" :disabled="saving" @click="submit">
                <AppIcon name="shield" size="sm" /> {{ saving ? '変更中...' : 'パスワードを変更' }}
              </button>
            </span>
          </div>
        </div>
      </section>
    </div>
  </Teleport>
</template>
