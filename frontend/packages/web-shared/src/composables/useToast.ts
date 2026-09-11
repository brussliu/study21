import { reactive } from 'vue'

export type ToastType = 'success' | 'info' | 'warning' | 'danger'

export interface ToastItem {
  id: number
  type: ToastType
  message: string
  duration: number
}

const state = reactive<{ items: ToastItem[] }>({ items: [] })
let sequence = 0

/**
 * 軽量なグローバル Toast ストア。
 * `<ToastHost />` と組み合わせて使う。ビジネス意味を持たない。
 */
export function useToast() {
  function dismiss(id: number): void {
    const index = state.items.findIndex((item) => item.id === id)
    if (index >= 0) {
      state.items.splice(index, 1)
    }
  }

  function show(type: ToastType, message: string, duration = 3000): number {
    const id = ++sequence
    state.items.push({ id, type, message, duration })
    if (duration > 0) {
      setTimeout(() => dismiss(id), duration)
    }
    return id
  }

  return {
    items: state.items,
    show,
    success: (message: string, duration?: number): number => show('success', message, duration),
    info: (message: string, duration?: number): number => show('info', message, duration),
    warning: (message: string, duration?: number): number => show('warning', message, duration),
    danger: (message: string, duration?: number): number => show('danger', message, duration),
    dismiss
  }
}
