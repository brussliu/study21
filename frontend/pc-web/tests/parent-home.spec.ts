import { describe, expect, it, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ParentHomeView from '@/views/parent/ParentHomeView.vue'

/**
 * 保護者ホーム。
 *
 * 2.0 の english.jsp（英語勉強）の内容を保護者ホームにも入れた（生徒ホームと
 * `components/home/StudyOverview.vue` を共用する）。お子さまの情報・アカウント情報と
 * 同じ画面に並ぶこと、学習状況が「お子さまのもの」として出ることを固定する。
 */
beforeEach(() => {
  setActivePinia(createPinia())
  window.sessionStorage.setItem('study21.auth.v2', JSON.stringify({ username: '検証 保護者', role: 'GUARDIAN' }))
})

describe('保護者ホーム', () => {
  it('アカウント情報と生徒一覧を出す', () => {
    const wrapper = mount(ParentHomeView)

    expect(wrapper.text()).toContain('アカウント情報')
    expect(wrapper.text()).toContain('生徒一覧')
    expect(wrapper.text()).toContain('山田 太郎')
    expect(wrapper.text()).toContain('山田 花子')
  })

  it('お子さまの学習状況（2.0 の english.jsp の内容）を出す', () => {
    const wrapper = mount(ParentHomeView)

    expect(wrapper.text()).toContain('お子さまの学習状況')
    // KPI 5 つ・レーダー 2 つ・学習状況の一覧 6 件（生徒ホームと同じ）
    expect(wrapper.findAll('[data-kpi]').length).toBe(5)
    expect(wrapper.findAll('[data-radar]').map((el) => el.attributes('data-radar'))).toEqual(['beginner', 'intermediate'])
    expect(wrapper.findAll('[data-home-item]').length).toBe(6)
  })

  it('誰の学習状況かを名前で示す', () => {
    const wrapper = mount(ParentHomeView)

    // 見出しの「お子さま」と、サンプルデータの注記に名前が入る
    expect(wrapper.get('.panel__meta').text()).toContain('山田 太郎')
    expect(wrapper.get('.home-note').text()).toContain('山田 太郎 さんの学習状況です。')
    expect(wrapper.get('.home-note').text()).toContain('サンプルデータ')
  })
})
