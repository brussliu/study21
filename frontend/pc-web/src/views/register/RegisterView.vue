<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, useToast } from '@study21/web-shared'
import { registerAccount } from '@/api/register'
import '@/features/register/register.css'

type Step = 'account' | 'student' | 'done'

const router = useRouter()
const toast = useToast()

const step = ref<Step>('account')

/* ---------- アカウント情報 ---------- */
const email = ref('')
const password = ref('')
const password2 = ref('')
const agreed = ref(false)
const errors = reactive({ email: '', password: '', password2: '', terms: '' })

/* ---------- 生徒情報 ---------- */
const sei = ref('')
const mei = ref('')
const seiKana = ref('')
const meiKana = ref('')
const grade = ref('')
// お子さま専用のログインアカウント（保護者とは完全に別アカウント）
const studentEmail = ref('')
const studentPassword = ref('')
const studentPassword2 = ref('')
const studentErrors = reactive({
  sei: '',
  mei: '',
  seiKana: '',
  meiKana: '',
  grade: '',
  studentEmail: '',
  studentPassword: '',
  studentPassword2: ''
})

const submitting = ref(false)
const submitError = ref('')

const GRADE_GROUPS: { label: string; items: string[] }[] = [
  {
    label: '小学校',
    items: ['小学1年生', '小学2年生', '小学3年生', '小学4年生', '小学5年生', '小学6年生']
  },
  { label: '中学校', items: ['中学1年生', '中学2年生', '中学3年生'] },
  { label: '高校', items: ['高校1年生', '高校2年生', '高校3年生'] }
]

/* ---------- バリデーション ---------- */
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PASS_RE = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/

function validateAccount(): boolean {
  let ok = true
  if (!EMAIL_RE.test(email.value.trim())) {
    errors.email = 'メールアドレスの形式が正しくありません。'
    ok = false
  } else {
    errors.email = ''
  }
  if (!PASS_RE.test(password.value)) {
    errors.password = '8文字以上・英数字をそれぞれ1文字以上含めてください。'
    ok = false
  } else {
    errors.password = ''
  }
  if (password2.value !== password.value || password2.value === '') {
    errors.password2 = 'パスワードが一致しません。'
    ok = false
  } else {
    errors.password2 = ''
  }
  if (!agreed.value) {
    errors.terms = '利用規約への同意が必要です。'
    ok = false
  } else {
    errors.terms = ''
  }
  return ok
}

function next(): void {
  if (!validateAccount()) return
  toast.success('アカウント情報を確認しました')
  step.value = 'student'
}

function validateStudent(): boolean {
  let ok = true
  const required: [keyof typeof studentErrors, string][] = [
    ['sei', '姓を入力してください。'],
    ['mei', '名を入力してください。'],
    ['seiKana', 'ふりがな（せい）を入力してください。'],
    ['meiKana', 'ふりがな（めい）を入力してください。']
  ]
  const values: Record<string, string> = {
    sei: sei.value,
    mei: mei.value,
    seiKana: seiKana.value,
    meiKana: meiKana.value
  }
  required.forEach(([key, message]) => {
    if (!values[key].trim()) {
      studentErrors[key] = message
      ok = false
    } else {
      studentErrors[key] = ''
    }
  })
  if (!grade.value) {
    studentErrors.grade = '学年を選択してください。'
    ok = false
  } else {
    studentErrors.grade = ''
  }

  // ---- お子さまのログインアカウント ----
  if (!EMAIL_RE.test(studentEmail.value.trim())) {
    studentErrors.studentEmail = 'メールアドレスの形式が正しくありません。'
    ok = false
  } else if (studentEmail.value.trim().toLowerCase() === email.value.trim().toLowerCase()) {
    studentErrors.studentEmail = '保護者アカウントと同じメールアドレスは使用できません。'
    ok = false
  } else {
    studentErrors.studentEmail = ''
  }
  if (!PASS_RE.test(studentPassword.value)) {
    studentErrors.studentPassword = '8文字以上・英数字をそれぞれ1文字以上含めてください。'
    ok = false
  } else {
    studentErrors.studentPassword = ''
  }
  if (studentPassword2.value !== studentPassword.value || studentPassword2.value === '') {
    studentErrors.studentPassword2 = 'パスワードが一致しません。'
    ok = false
  } else {
    studentErrors.studentPassword2 = ''
  }
  return ok
}

/* ---------- 完了画面 ---------- */
const studentName = computed(() => `${sei.value.trim()} ${mei.value.trim()}`)
const studentSummary = computed(() => `${studentName.value}（${grade.value}）`)

const termLabel = ref('')
function fmt(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}/${m}/${day}`
}
/** サーバーが返す有効期限（yyyy-MM-dd）を Date に変換する。 */
function parseExpiry(value: string): Date {
  const [y, m, d] = value.split('-').map(Number)
  return new Date(y, m - 1, d)
}

/** サーバーエラーメッセージの「field: message」形式から項目エラーへ反映する。 */
const FIELD_MAP: Record<string, { step: 'account' | 'student'; key: string }> = {
  parentEmail: { step: 'account', key: 'email' },
  parentPassword: { step: 'account', key: 'password' },
  agreed: { step: 'account', key: 'terms' },
  sei: { step: 'student', key: 'sei' },
  mei: { step: 'student', key: 'mei' },
  seiKana: { step: 'student', key: 'seiKana' },
  meiKana: { step: 'student', key: 'meiKana' },
  grade: { step: 'student', key: 'grade' },
  studentEmail: { step: 'student', key: 'studentEmail' },
  studentPassword: { step: 'student', key: 'studentPassword' }
}

function applyFieldError(field: string, message: string): boolean {
  const ref = FIELD_MAP[field]
  if (!ref) return false
  if (ref.step === 'account') {
    errors[ref.key as keyof typeof errors] = message
    step.value = 'account'
  } else {
    studentErrors[ref.key as keyof typeof studentErrors] = message
  }
  return true
}

function handleRegisterError(err: unknown): void {
  if (err instanceof ApiError) {
    if (err.code === 'CONFLICT') {
      // 409: メール重複等。data.field でどの項目かを判定し、該当ステップへ戻す
      const field = (err.data as { field?: string } | undefined)?.field
      if (field && applyFieldError(field, err.message)) {
        submitError.value = err.message
        if (step.value === 'account') toast.danger(err.message)
        return
      }
      submitError.value = err.message
      return
    }
    if (err.code === 'VALIDATION_ERROR') {
      // 400: 「field: message」形式 → 項目エラーへ
      const idx = err.message.indexOf(': ')
      const field = idx > 0 ? err.message.slice(0, idx) : ''
      const message = idx > 0 ? err.message.slice(idx + 2) : err.message
      if (field && applyFieldError(field, message)) return
      submitError.value = err.message
      return
    }
    submitError.value = err.message
    return
  }
  submitError.value = '通信に失敗しました。しばらくしてからもう一度お試しください。'
}

async function submit(): Promise<void> {
  if (submitting.value) return
  submitError.value = ''
  if (!validateStudent()) return
  submitting.value = true
  try {
    const res = await registerAccount({
      parentEmail: email.value.trim(),
      parentPassword: password.value,
      sei: sei.value.trim(),
      mei: mei.value.trim(),
      seiKana: seiKana.value.trim(),
      meiKana: meiKana.value.trim(),
      grade: grade.value,
      studentEmail: studentEmail.value.trim(),
      studentPassword: studentPassword.value,
      agreed: agreed.value
    })
    // 有効期限はサーバーが返した値を正とする（登録日 + 1ヶ月・月末クランプ済み）
    const start = new Date()
    const end = parseExpiry(res.data.expiryDate)
    termLabel.value = `${fmt(start)} 〜 ${fmt(end)}`
    toast.success('登録処理を完了しました')
    step.value = 'done'
  } catch (err) {
    handleRegisterError(err)
  } finally {
    submitting.value = false
  }
}

function finishRegister(): void {
  router.push({ path: '/login', query: { email: email.value.trim() } })
}

/* ---------- ステッパー ---------- */
const STEP_LABELS = ['アカウント情報', '生徒情報', '完了'] as const
const STEP_INDEX: Record<Step, number> = { account: 0, student: 1, done: 2 }

function stepState(index: number): 'done' | 'active' | 'pending' {
  const current = STEP_INDEX[step.value]
  if (index < current) return 'done'
  if (index === current) return 'active'
  return 'pending'
}
</script>

<template>
  <div class="register-page">
    <!-- ================= ステップ1：アカウント情報 ================= -->
    <form v-if="step === 'account'" class="register-card" @submit.prevent="next">
      <div class="stepper" aria-label="登録ステップ">
        <template v-for="(label, index) in STEP_LABELS" :key="label">
          <div v-if="index > 0" class="stepper__line" :class="{ 'is-done': stepState(index - 1) === 'done' }"></div>
          <div class="stepper__step" :class="stepState(index)">
            <span class="stepper__dot">{{ stepState(index) === 'done' ? '✓' : index + 1 }}</span>
            <span class="stepper__label">{{ label }}</span>
          </div>
        </template>
      </div>

      <h1 class="register-card__title">新規登録</h1>
      <p class="register-card__hint">
        保護者アカウントをメールアドレスで作成します。登録時にお子さま（生徒）1名もあわせて登録します。
      </p>

      <div class="field">
        <span class="field__label">メールアドレス<span class="req">必須</span></span>
        <input
          v-model="email"
          class="input"
          :class="{ 'is-invalid': !!errors.email }"
          type="email"
          autocomplete="email"
          placeholder="example@example.com"
        />
        <span class="field__hint">ログインIDとして使用します。</span>
        <span v-if="errors.email" class="field__error">{{ errors.email }}</span>
      </div>

      <div class="form-grid">
        <div class="field">
          <span class="field__label">パスワード<span class="req">必須</span></span>
          <input
            v-model="password"
            class="input"
            :class="{ 'is-invalid': !!errors.password }"
            type="password"
            autocomplete="new-password"
            placeholder="8文字以上・英数字"
          />
          <span class="field__hint">8文字以上、半角英数字をそれぞれ1文字以上含めてください。</span>
          <span v-if="errors.password" class="field__error">{{ errors.password }}</span>
        </div>
        <div class="field">
          <span class="field__label">パスワード（確認）<span class="req">必須</span></span>
          <input
            v-model="password2"
            class="input"
            :class="{ 'is-invalid': !!errors.password2 }"
            type="password"
            autocomplete="new-password"
            placeholder="もう一度入力"
          />
          <span v-if="errors.password2" class="field__error">{{ errors.password2 }}</span>
        </div>
      </div>

      <div class="alert alert--info">
        <div class="alert__body">
          <span class="alert__title">無料期間について</span><br />
          新規アカウントは<strong>登録日から1ヶ月間</strong>無料でご利用いただけます。期間終了後は更新手続き（有料）が必要です。お支払い方法は登録後、保護者画面から確認できます。
        </div>
      </div>

      <label class="check" :class="{ 'is-invalid': !!errors.terms }">
        <input v-model="agreed" type="checkbox" />
        <span>
          <a href="#" @click.prevent="toast.info('（設計案）利用規約ページを開きます')">利用規約</a>と
          <a href="#" @click.prevent="toast.info('（設計案）プライバシーポリシーページを開きます')">プライバシーポリシー</a>に同意する
        </span>
      </label>
      <span v-if="errors.terms" class="field__error field__error--standalone">{{ errors.terms }}</span>

      <div class="form-actions">
        <button type="button" class="btn btn--ghost" @click="router.push('/login')">戻る</button>
        <button type="submit" class="btn btn--primary">次へ</button>
      </div>
    </form>

    <!-- ================= ステップ2：生徒情報 ================= -->
    <form v-else-if="step === 'student'" class="register-card" @submit.prevent="submit">
      <div class="stepper" aria-label="登録ステップ">
        <template v-for="(label, index) in STEP_LABELS" :key="label">
          <div v-if="index > 0" class="stepper__line" :class="{ 'is-done': stepState(index - 1) === 'done' }"></div>
          <div class="stepper__step" :class="stepState(index)">
            <span class="stepper__dot">{{ stepState(index) === 'done' ? '✓' : index + 1 }}</span>
            <span class="stepper__label">{{ label }}</span>
          </div>
        </template>
      </div>

      <h1 class="register-card__title">お子さま（生徒）の情報</h1>
      <p class="register-card__hint">
        登録と同時にお子さま1名を登録します。登録後、保護者画面から<b>追加の生徒を登録</b>できます。
      </p>

      <div class="form-grid">
        <div class="field">
          <span class="field__label">お子さまの姓<span class="req">必須</span></span>
          <input
            v-model="sei"
            class="input"
            :class="{ 'is-invalid': !!studentErrors.sei }"
            type="text"
            placeholder="山田"
          />
          <span v-if="studentErrors.sei" class="field__error">{{ studentErrors.sei }}</span>
        </div>
        <div class="field">
          <span class="field__label">お子さまの名<span class="req">必須</span></span>
          <input
            v-model="mei"
            class="input"
            :class="{ 'is-invalid': !!studentErrors.mei }"
            type="text"
            placeholder="太郎"
          />
          <span v-if="studentErrors.mei" class="field__error">{{ studentErrors.mei }}</span>
        </div>
        <div class="field">
          <span class="field__label">ふりがな（せい）<span class="req">必須</span></span>
          <input
            v-model="seiKana"
            class="input"
            :class="{ 'is-invalid': !!studentErrors.seiKana }"
            type="text"
            placeholder="やまだ"
          />
          <span v-if="studentErrors.seiKana" class="field__error">{{ studentErrors.seiKana }}</span>
        </div>
        <div class="field">
          <span class="field__label">ふりがな（めい）<span class="req">必須</span></span>
          <input
            v-model="meiKana"
            class="input"
            :class="{ 'is-invalid': !!studentErrors.meiKana }"
            type="text"
            placeholder="たろう"
          />
          <span v-if="studentErrors.meiKana" class="field__error">{{ studentErrors.meiKana }}</span>
        </div>
      </div>

      <div class="field">
        <span class="field__label">学年<span class="req">必須</span></span>
        <select v-model="grade" class="select" :class="{ 'is-invalid': !!studentErrors.grade }">
          <option value="">選択してください</option>
          <optgroup v-for="group in GRADE_GROUPS" :key="group.label" :label="group.label">
            <option v-for="item in group.items" :key="item" :value="item">{{ item }}</option>
          </optgroup>
        </select>
        <span v-if="studentErrors.grade" class="field__error">{{ studentErrors.grade }}</span>
      </div>

      <h2 class="register-card__section">お子さまのログイン情報</h2>
      <p class="register-card__hint">
        お子さま専用の<strong>ログインID（メールアドレス）とパスワード</strong>を設定します。
        保護者アカウントとは完全に別のアカウントとして管理され、お子さまはこのアカウントで単独ログインできます。
      </p>

      <div class="field">
        <span class="field__label">お子さまのメールアドレス<span class="req">必須</span></span>
        <input
          v-model="studentEmail"
          class="input"
          :class="{ 'is-invalid': !!studentErrors.studentEmail }"
          type="email"
          autocomplete="email"
          placeholder="kodomo@example.com"
        />
        <span class="field__hint">お子さまのログインIDです。保護者と同じメールアドレスは使用できません。</span>
        <span v-if="studentErrors.studentEmail" class="field__error">{{ studentErrors.studentEmail }}</span>
      </div>

      <div class="form-grid">
        <div class="field">
          <span class="field__label">パスワード<span class="req">必須</span></span>
          <input
            v-model="studentPassword"
            class="input"
            :class="{ 'is-invalid': !!studentErrors.studentPassword }"
            type="password"
            autocomplete="new-password"
            placeholder="8文字以上・英数字"
          />
          <span class="field__hint">8文字以上、半角英数字をそれぞれ1文字以上含めてください。</span>
          <span v-if="studentErrors.studentPassword" class="field__error">{{ studentErrors.studentPassword }}</span>
        </div>
        <div class="field">
          <span class="field__label">パスワード（確認）<span class="req">必須</span></span>
          <input
            v-model="studentPassword2"
            class="input"
            :class="{ 'is-invalid': !!studentErrors.studentPassword2 }"
            type="password"
            autocomplete="new-password"
            placeholder="もう一度入力"
          />
          <span v-if="studentErrors.studentPassword2" class="field__error">{{ studentErrors.studentPassword2 }}</span>
        </div>
      </div>

      <div v-if="submitError" class="alert alert--danger" role="alert">
        <div class="alert__body">{{ submitError }}</div>
      </div>

      <div class="alert alert--warning">
        <div class="alert__body">
          <span class="alert__title">ご注意</span> 生徒情報は登録後、保護者画面からいつでも変更できます。
        </div>
      </div>

      <div class="form-actions">
        <button type="button" class="btn btn--secondary" :disabled="submitting" @click="step = 'account'">戻る</button>
        <button type="submit" class="btn btn--primary" :disabled="submitting">
          {{ submitting ? '登録中...' : '登録する' }}
        </button>
      </div>
    </form>

    <!-- ================= 登録完了 ================= -->
    <div v-else class="done-card">
      <div class="done-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="20 6 9 17 4 12" />
        </svg>
      </div>
      <h1 class="done-card__title">登録が完了しました</h1>
      <p class="done-card__hint">
        ご登録ありがとうございます。以下の内容でアカウントを作成しました。<br />
        保護者・お子さまの両方が、それぞれのログインIDで単独ログインできます。
      </p>

      <div class="summary">
        <div class="summary__row">
          <span class="summary__label">メールアドレス</span>
          <span class="summary__value">{{ email.trim() }}</span>
        </div>
        <div class="summary__row">
          <span class="summary__label">初期生徒</span>
          <span class="summary__value">{{ studentSummary }}</span>
        </div>
        <div class="summary__row">
          <span class="summary__label">お子さまのログインID</span>
          <span class="summary__value">{{ studentEmail.trim() }}</span>
        </div>
        <div class="summary__row">
          <span class="summary__label">ご利用期間（無料）</span>
          <span class="summary__value">
            <span>{{ termLabel }}</span>
            <span class="badge badge--info">体験期間</span>
          </span>
        </div>
      </div>

      <div class="alert alert--info">
        <div class="alert__body">
          <span class="alert__title">ご利用期限のお知らせ</span><br />
          無料期間は <strong>{{ termLabel.split(' 〜 ')[1] }}</strong> までです。期間終了の1週間前にメールでお知らせします。期間終了後も学習データは保持され、更新手続きによりそのまま継続利用できます。
        </div>
      </div>

      <div class="form-actions">
        <button type="button" class="btn btn--primary btn--lg btn--block" @click="finishRegister">
          ログイン画面へ
        </button>
      </div>
    </div>
  </div>
</template>
