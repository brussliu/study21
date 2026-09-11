/**
 * Study 2.1 — Design Tokens（TypeScript 版）
 *
 * CSS 変数（styles/design-tokens.css）と対になる、プログラムから参照する
 * ための型付きトークン。チャート等の JS 描画で利用することを想定。
 * ここにはビジネス意味を持たない値のみを置く。
 */
export const designTokens = {
  color: {
    primary: '#25876f',
    primaryHover: '#1c6c58',
    primarySoft: '#eef7f3',
    accent: '#1d3452',
    text: '#1f2a35',
    textStrong: '#131a22',
    textMuted: '#4b5968',
    textSubtle: '#6d7c8c',
    background: '#f5f7fa',
    surface: '#ffffff',
    border: '#dde2e9',
    borderStrong: '#c7cfd9',
    success: '#1f8a4c',
    warning: '#a86900',
    danger: '#c13a2e',
    info: '#2c6fb8',
    sidebarBg: '#15263c'
  },
  subject: {
    english: '#2f6fb5',
    japanese: '#c1516a',
    math: '#6a58b8'
  },
  space: {
    1: '4px',
    2: '8px',
    3: '12px',
    4: '16px',
    5: '20px',
    6: '24px',
    7: '32px',
    8: '40px',
    9: '48px'
  },
  fontSize: {
    xs: '12px',
    sm: '13px',
    md: '14px',
    lg: '16px',
    xl: '18px',
    '2xl': '20px',
    '3xl': '24px'
  },
  radius: {
    sm: '4px',
    md: '6px',
    lg: '10px',
    xl: '14px',
    pill: '999px'
  },
  control: {
    sm: '30px',
    md: '36px',
    lg: '42px'
  },
  breakpoint: {
    sm: 640,
    md: 768,
    lg: 1024,
    xl: 1280
  },
  duration: {
    fast: '120ms',
    base: '200ms',
    slow: '300ms'
  }
} as const

export type DesignTokens = typeof designTokens
