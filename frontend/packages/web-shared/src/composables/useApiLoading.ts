import { ref, type Ref } from 'vue'

export interface ApiLoadingState {
  loading: Ref<boolean>
  run: <T>(task: () => Promise<T>) => Promise<T>
}

/**
 * 统一异步加载状态封装。组件/服务用它统一维护 loading 状态。
 */
export function useApiLoading(): ApiLoadingState {
  const loading = ref(false)

  async function run<T>(task: () => Promise<T>): Promise<T> {
    loading.value = true
    try {
      return await task()
    } finally {
      loading.value = false
    }
  }

  return { loading, run }
}
