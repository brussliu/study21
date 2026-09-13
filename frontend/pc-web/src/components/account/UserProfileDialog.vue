<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ApiError, GRADE_GROUPS, PHONE_ERROR, PHONE_HINT, PHONE_RE, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { fetchProfile, updateProfile, type UserProfile } from '@/api/account'
import { useAuthStore } from '@/stores/auth'
import '@/features/user-profile/user-profile.css'

/**
 * ユーザー情報の修正ダイアログ（保護者・生徒）。
 *
 * ページ遷移はせず、右上のユーザー名メニューから開く。
 * 保存すると `saved` を通知し、親ページが再読み込みして変更を反映する。
 * パスワードの変更は別ダイアログ（PasswordChangeDialog）で行う。
 * メールアドレスはログインID を兼ねるため変更できない（参照のみ）。
 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const auth = useAuthStore()
const toast = useToast()

const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const profile = ref<UserProfile | null>(null)
const submitted = ref(false)

const form = reactive({
  sei: '',
  mei: '',
  seiKana: '',
  meiKana: '',
  grade: '',
  phone: '',
  mailNotify: true,
  reminderNotify: false
})

const isStudent = computed(() => profile.value?.accountType === 'STUDENT')
const roleLabel = computed(() => (isStudent.value ? '学生' : '保護者'))

/** 保存済みの値（「初期値に戻す」で使う）。 */
let loaded: UserProfile | null = null

function applyProfile(next: UserProfile): void {
  profile.value = next
  loaded = next
  form.sei = next.sei ?? ''
  form.mei = next.mei ?? ''
  form.seiKana = next.seiKana ?? ''
  form.meiKana = next.meiKana ?? ''
  form.grade = next.grade ?? ''
  form.phone = next.phone ?? ''
  form.mailNotify = next.mailNotify
  form.reminderNotify = next.reminderNotify
  submitted.value = false
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const response = await fetchProfile()
    applyProfile(response.data)
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : 'ユーザー情報を取得できませんでした。'
  } finally {
    loading.value = false
  }
}

// 開くたびに最新の内容を読み直す
watch(() => props.open, (open) => {
  if (open) void load()
}, { immediate: true })

const errors = computed<Record<string, string>>(() => {
  const result: Record<string, string> = {}
  if (form.sei.trim() === '') result.sei = '姓を入力してください。'
  else if (form.sei.trim().length > 100) result.sei = '姓は100文字以内で入力してください。'
  if (form.mei.trim() === '') result.mei = '名を入力してください。'
  else if (form.mei.trim().length > 100) result.mei = '名は100文字以内で入力してください。'

  if (isStudent.value) {
    if (form.seiKana.trim() === '') result.seiKana = 'ふりがな（せい）を入力してください。'
    if (form.meiKana.trim() === '') result.meiKana = 'ふりがな（めい）を入力してください。'
    if (form.grade.trim() === '') result.grade = '学年を選択してください。'
  }
  if (!PHONE_RE.test(form.phone.trim())) result.phone = PHONE_ERROR
  return result
})

/** 送信後にだけエラーを表示する。 */
function errorOf(field: string): string {
  return submitted.value ? (errors.value[field] ?? '') : ''
}

function invalid(field: string): boolean {
  return errorOf(field) !== ''
}

function resetToLoaded(): void {
  if (loaded) applyProfile(loaded)
  submitted.value = false
}

async function save(): Promise<void> {
  if (saving.value) return
  submitted.value = true
  if (Object.keys(errors.value).length > 0) {
    toast.warning('入力内容を確認してください。')
    return
  }
  saving.value = true
  try {
    const response = await updateProfile({
      sei: form.sei.trim(),
      mei: form.mei.trim(),
      seiKana: form.seiKana.trim(),
      meiKana: form.meiKana.trim(),
      grade: isStudent.value ? form.grade : '',
      phone: form.phone.trim(),
      mailNotify: form.mailNotify,
      reminderNotify: form.reminderNotify
    })
    applyProfile(response.data)
    // ヘッダーの表示名もすぐ反映しておく（親ページの再読み込み前の見た目を揃える）
    auth.updateDisplayName(response.data.displayName)
    toast.success(response.message || 'ユーザー情報を更新しました。')
    emit('close')
    // 親ページを再読み込みして、開いていた画面の内容にも反映する
    emit('saved')
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : 'ユーザー情報を更新できませんでした。')
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
      <section class="dialog dialog--md" role="dialog" aria-modal="true" aria-labelledby="upDialogTitle">
        <div class="dialog__head">
          <h2 id="upDialogTitle" class="dialog__title">
            <AppIcon name="user" size="sm" /> ユーザー情報の修正
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="emit('close')">
            <AppIcon name="x" size="sm" />
          </button>
        </div>

        <div class="dialog__body up-body">
          <p v-if="loadError" class="alert alert--danger">{{ loadError }}</p>
          <p v-else-if="loading" class="up-loading">読み込んでいます...</p>

          <template v-else-if="profile">
            <section class="up-section">
              <h3 class="up-section__title">基本情報</h3>
              <div class="up-grid">
                <div class="field">
                  <label class="field__label" for="upSei">姓<span class="up-required">必須</span></label>
                  <input
                    id="upSei" v-model="form.sei" class="input" type="text" maxlength="100"
                    autocomplete="family-name" :class="{ 'is-invalid': invalid('sei') }"
                  />
                  <p v-if="errorOf('sei')" class="field__error">{{ errorOf('sei') }}</p>
                </div>

                <div class="field">
                  <label class="field__label" for="upMei">名<span class="up-required">必須</span></label>
                  <input
                    id="upMei" v-model="form.mei" class="input" type="text" maxlength="100"
                    autocomplete="given-name" :class="{ 'is-invalid': invalid('mei') }"
                  />
                  <p v-if="errorOf('mei')" class="field__error">{{ errorOf('mei') }}</p>
                </div>

                <div class="field">
                  <label class="field__label" for="upSeiKana">
                    ふりがな（せい）<span v-if="isStudent" class="up-required">必須</span>
                  </label>
                  <input
                    id="upSeiKana" v-model="form.seiKana" class="input" type="text" maxlength="100"
                    placeholder="やまだ" :class="{ 'is-invalid': invalid('seiKana') }"
                  />
                  <p v-if="errorOf('seiKana')" class="field__error">{{ errorOf('seiKana') }}</p>
                </div>

                <div class="field">
                  <label class="field__label" for="upMeiKana">
                    ふりがな（めい）<span v-if="isStudent" class="up-required">必須</span>
                  </label>
                  <input
                    id="upMeiKana" v-model="form.meiKana" class="input" type="text" maxlength="100"
                    placeholder="たろう" :class="{ 'is-invalid': invalid('meiKana') }"
                  />
                  <p v-if="errorOf('meiKana')" class="field__error">{{ errorOf('meiKana') }}</p>
                </div>

                <div v-if="isStudent" class="field">
                  <label class="field__label" for="upGrade">学年<span class="up-required">必須</span></label>
                  <select id="upGrade" v-model="form.grade" class="select" :class="{ 'is-invalid': invalid('grade') }">
                    <option value="">選択してください</option>
                    <optgroup v-for="group in GRADE_GROUPS" :key="group.label" :label="group.label">
                      <option v-for="item in group.items" :key="item" :value="item">{{ item }}</option>
                    </optgroup>
                  </select>
                  <p v-if="errorOf('grade')" class="field__error">{{ errorOf('grade') }}</p>
                </div>

                <div class="field">
                  <label class="field__label" for="upPhone">電話番号</label>
                  <input
                    id="upPhone" v-model="form.phone" class="input" type="tel" maxlength="20"
                    autocomplete="tel" placeholder="090-0000-0000" :class="{ 'is-invalid': invalid('phone') }"
                  />
                  <p v-if="errorOf('phone')" class="field__error">{{ errorOf('phone') }}</p>
                  <p v-else class="field__hint">{{ PHONE_HINT }}</p>
                </div>

                <div class="field">
                  <span class="field__label">メールアドレス</span>
                  <span class="up-readonly">
                    <span class="up-readonly__value" data-testid="profile-email">{{ profile.email }}</span>
                    <span class="field__hint">ログインIDを兼ねています。変更できません。</span>
                  </span>
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
              <h3 class="up-section__title">通知</h3>
              <div class="up-check-list">
                <label class="check">
                  <input v-model="form.mailNotify" type="checkbox" />
                  更新のお知らせをメールで受け取る
                </label>
                <label class="check">
                  <input v-model="form.reminderNotify" type="checkbox" />
                  学習リマインダーを受け取る
                </label>
              </div>
            </section>
          </template>
        </div>

        <div class="dialog__foot">
          <div class="up-foot">
            <button type="button" class="btn btn--ghost btn--sm" :disabled="saving" @click="resetToLoaded">
              <AppIcon name="rotate" size="sm" /> 初期値に戻す
            </button>
            <span class="up-foot__actions">
              <button type="button" class="btn btn--secondary" @click="emit('close')">キャンセル</button>
              <button type="button" class="btn btn--primary" :disabled="saving" @click="save">
                <AppIcon name="check" size="sm" /> {{ saving ? '保存中...' : '保存' }}
              </button>
            </span>
          </div>
        </div>
      </section>
    </div>
  </Teleport>
</template>
