<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
import '@/features/user-profile/user-profile.css'

/**
 * ユーザー情報の変更ダイアログ（**デモ表示のみ**）。
 * 入力チェックは動くが、更新 API・DB 保存は未実装で、保存しても何も変わらない。
 * 実装するときは save() から API を呼び、成功後に auth の表示名を更新する。
 */
type Section = 'profile' | 'password'

const props = withDefaults(defineProps<{
  open: boolean
  /** 開いたときにパスワード欄を展開するか（メニューの「パスワードの変更」用）。 */
  focus?: Section
}>(), {
  focus: 'profile'
})

const emit = defineEmits<{ close: [] }>()

const auth = useAuthStore()
const toast = useToast()

const roleLabel = computed(() =>
  auth.role === 'ADMIN' ? '管理者' : auth.role === 'GUARDIAN' ? '保護者' : '学生'
)

const form = reactive({
  displayName: '',
  kana: '',
  email: '',
  phone: '',
  changePassword: false,
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
  notifyEmail: true,
  notifyReminder: false
})

/** 送信を一度でも試したか（未入力エラーはその後に出す）。 */
const submitted = ref(false)
const showPassword = reactive({ current: false, next: false, confirm: false })

const PASSWORD_MIN = 8
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** デモ用の初期値（本来は API から取得する）。 */
function resetForm(): void {
  const name = auth.currentUser ?? ''
  form.displayName = name
  form.kana = ''
  form.email = name === '' ? '' : `${name.replace(/\s+/g, '.').toLowerCase()}@example.com`
  form.phone = '090-0000-0000'
  form.changePassword = props.focus === 'password'
  form.currentPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  form.notifyEmail = true
  form.notifyReminder = false
  submitted.value = false
}

// 開いたタイミング（とメニューの選択内容）で初期化する。
watch(() => [props.open, props.focus] as const, () => {
  if (props.open) resetForm()
}, { immediate: true })

const errors = computed<Record<string, string>>(() => {
  const result: Record<string, string> = {}
  const name = form.displayName.trim()
  if (name === '') result.displayName = '表示名を入力してください。'
  else if (name.length > 50) result.displayName = '表示名は 50 文字以内で入力してください。'

  const email = form.email.trim()
  if (email === '') result.email = 'メールアドレスを入力してください。'
  else if (!EMAIL_PATTERN.test(email)) result.email = 'メールアドレスの形式が正しくありません。'

  if (form.changePassword) {
    if (form.currentPassword === '') result.currentPassword = '現在のパスワードを入力してください。'
    if (form.newPassword.length < PASSWORD_MIN) {
      result.newPassword = `新しいパスワードは ${PASSWORD_MIN} 文字以上で入力してください。`
    } else if (form.newPassword === form.currentPassword) {
      result.newPassword = '現在のパスワードとは違うものを入力してください。'
    }
    if (form.confirmPassword !== form.newPassword) {
      result.confirmPassword = '新しいパスワードが一致しません。'
    }
  }
  return result
})

/** 送信後にだけエラーを表示する。 */
function errorOf(field: string): string {
  return submitted.value ? (errors.value[field] ?? '') : ''
}

function invalid(field: string): boolean {
  return errorOf(field) !== ''
}

const initial = computed(() => {
  const source = form.displayName.trim() || auth.currentUser || '?'
  return source.charAt(0).toUpperCase()
})

function notifyDemoImage(): void {
  toast.info('デモ表示のため、画像は選択できません。')
}

function save(): void {
  submitted.value = true
  if (Object.keys(errors.value).length > 0) {
    toast.warning('入力内容を確認してください。')
    return
  }
  // ここで本来は更新 API を呼ぶ（未実装）。デモのため保存しない。
  toast.info('デモ表示のため、内容は保存していません。')
  emit('close')
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && props.open) emit('close')
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="overlay" @click.self="emit('close')">
      <section class="dialog dialog--md" role="dialog" aria-modal="true" aria-labelledby="upTitle">
        <div class="dialog__head">
          <h2 id="upTitle" class="dialog__title"><AppIcon name="user" size="sm" /> ユーザー情報の変更</h2>
          <span class="badge badge--outline">デモ</span>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="emit('close')">
            <AppIcon name="x" size="sm" />
          </button>
        </div>

        <div class="dialog__body up-body">
          <div class="alert alert--info">
            <AppIcon name="info" size="sm" />
            <div class="alert__body">
              この画面は<b>デモ表示</b>です。入力内容は保存されません（更新処理はこれから実装します）。
            </div>
          </div>

          <section class="up-section">
            <h3 class="up-section__title">基本情報</h3>

            <div class="up-avatar">
              <span class="up-avatar__circle" aria-hidden="true">{{ initial }}</span>
              <span class="up-avatar__meta">
                <button type="button" class="btn btn--secondary btn--sm" @click="notifyDemoImage">
                  <AppIcon name="image" size="sm" /> 画像を変更
                </button>
                <span class="field__hint">JPG / PNG ・ 2MB まで（デモのため選択できません）</span>
              </span>
            </div>

            <div class="up-grid">
              <div class="field">
                <label class="field__label" for="upName">表示名<span class="up-required">必須</span></label>
                <input
                  id="upName" v-model="form.displayName" class="input" type="text"
                  maxlength="50" autocomplete="nickname" :class="{ 'is-invalid': invalid('displayName') }"
                />
                <p v-if="errorOf('displayName')" class="field__error">{{ errorOf('displayName') }}</p>
              </div>

              <div class="field">
                <label class="field__label" for="upKana">ふりがな</label>
                <input
                  id="upKana" v-model="form.kana" class="input" type="text"
                  maxlength="50" placeholder="やまだ たろう"
                />
              </div>

              <div class="field field--wide">
                <label class="field__label" for="upEmail">メールアドレス<span class="up-required">必須</span></label>
                <input
                  id="upEmail" v-model="form.email" class="input" type="email"
                  autocomplete="email" :class="{ 'is-invalid': invalid('email') }"
                />
                <p v-if="errorOf('email')" class="field__error">{{ errorOf('email') }}</p>
                <p v-else class="field__hint">ログイン ID も兼ねています。</p>
              </div>

              <div class="field">
                <label class="field__label" for="upPhone">電話番号</label>
                <input
                  id="upPhone" v-model="form.phone" class="input" type="tel"
                  autocomplete="tel" placeholder="090-0000-0000"
                />
              </div>

              <div class="field">
                <span class="field__label">権限</span>
                <span class="up-readonly">
                  <span class="badge badge--primary">{{ roleLabel }}</span>
                  <span class="field__hint">管理者のみ変更できます。</span>
                </span>
              </div>
            </div>
          </section>

          <section class="up-section">
            <h3 class="up-section__title">パスワード</h3>
            <label class="check">
              <input v-model="form.changePassword" type="checkbox" />
              パスワードを変更する
            </label>

            <template v-if="form.changePassword">
              <div class="up-grid">
                <div class="field field--wide">
                  <label class="field__label" for="upCurrent">現在のパスワード</label>
                  <span class="up-password-row">
                    <input
                      id="upCurrent" v-model="form.currentPassword" class="input"
                      :type="showPassword.current ? 'text' : 'password'" autocomplete="current-password"
                      :class="{ 'is-invalid': invalid('currentPassword') }"
                    />
                    <button
                      type="button" class="btn btn--icon btn--sm"
                      :aria-label="showPassword.current ? '現在のパスワードを隠す' : '現在のパスワードを表示'"
                      @click="showPassword.current = !showPassword.current"
                    >
                      <AppIcon name="eye" size="sm" class="icon--view" />
                    </button>
                  </span>
                  <p v-if="errorOf('currentPassword')" class="field__error">{{ errorOf('currentPassword') }}</p>
                </div>

                <div class="field">
                  <label class="field__label" for="upNew">新しいパスワード</label>
                  <span class="up-password-row">
                    <input
                      id="upNew" v-model="form.newPassword" class="input"
                      :type="showPassword.next ? 'text' : 'password'" autocomplete="new-password"
                      :class="{ 'is-invalid': invalid('newPassword') }"
                    />
                    <button
                      type="button" class="btn btn--icon btn--sm"
                      :aria-label="showPassword.next ? '新しいパスワードを隠す' : '新しいパスワードを表示'"
                      @click="showPassword.next = !showPassword.next"
                    >
                      <AppIcon name="eye" size="sm" class="icon--view" />
                    </button>
                  </span>
                  <p v-if="errorOf('newPassword')" class="field__error">{{ errorOf('newPassword') }}</p>
                  <p v-else class="field__hint">{{ PASSWORD_MIN }} 文字以上。</p>
                </div>

                <div class="field">
                  <label class="field__label" for="upConfirm">新しいパスワード（確認）</label>
                  <span class="up-password-row">
                    <input
                      id="upConfirm" v-model="form.confirmPassword" class="input"
                      :type="showPassword.confirm ? 'text' : 'password'" autocomplete="new-password"
                      :class="{ 'is-invalid': invalid('confirmPassword') }"
                    />
                    <button
                      type="button" class="btn btn--icon btn--sm"
                      :aria-label="showPassword.confirm ? '確認用パスワードを隠す' : '確認用パスワードを表示'"
                      @click="showPassword.confirm = !showPassword.confirm"
                    >
                      <AppIcon name="eye" size="sm" class="icon--view" />
                    </button>
                  </span>
                  <p v-if="errorOf('confirmPassword')" class="field__error">{{ errorOf('confirmPassword') }}</p>
                </div>
              </div>
            </template>
          </section>

          <section class="up-section">
            <h3 class="up-section__title">通知</h3>
            <div class="up-check-list">
              <label class="check">
                <input v-model="form.notifyEmail" type="checkbox" />
                更新のお知らせをメールで受け取る
              </label>
              <label class="check">
                <input v-model="form.notifyReminder" type="checkbox" />
                学習リマインダーを受け取る
              </label>
            </div>
          </section>
        </div>

        <div class="dialog__foot">
          <div class="up-foot">
            <button type="button" class="btn btn--ghost btn--sm" @click="resetForm">
              <AppIcon name="rotate" size="sm" /> 初期値に戻す
            </button>
            <span class="up-foot__actions">
              <button type="button" class="btn btn--secondary" @click="emit('close')">キャンセル</button>
              <button type="button" class="btn btn--primary" @click="save">
                <AppIcon name="check" size="sm" /> 保存
              </button>
            </span>
          </div>
        </div>
      </section>
    </div>
  </Teleport>
</template>
