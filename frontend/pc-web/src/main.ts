import { createApp } from 'vue'
import { createPinia } from 'pinia'
// 公共设计变量/样式的唯一来源：ui-demo 忠实副本（assets/prototype/*.css）
import './assets/prototype/tokens.css'
import './assets/prototype/base.css'
import './assets/prototype/layout.css'
import './assets/prototype/components.css'
import './assets/prototype/forms.css'
import './assets/prototype/tables.css'
import './assets/prototype/dialogs.css'
import './assets/prototype/themes.css'
import './assets/prototype/responsive.css'
// アプリ固有の追加・調整スタイル（危険操作アイコン、検索条件と一覧の間隔など）。
import './assets/app/app.css'
import App from './App.vue'
import { createAppRouter } from './router'

const app = createApp(App)
app.use(createPinia())
app.use(createAppRouter())
app.mount('#app')
