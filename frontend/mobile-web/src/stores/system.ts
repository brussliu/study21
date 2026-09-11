import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  AdminApiClient,
  UserApiClient,
  healthStateFromError,
  healthStateOf,
  type HealthState
} from '@study21/web-shared'

export const useSystemStore = defineStore('system', () => {
  const adminHealth = ref<HealthState>('UNKNOWN')
  const userHealth = ref<HealthState>('UNKNOWN')
  const loading = ref(false)

  async function checkHealth(): Promise<void> {
    loading.value = true
    try {
      const [adminResult, userResult] = await Promise.allSettled([
        new AdminApiClient().health(),
        new UserApiClient().health()
      ])
      adminHealth.value =
        adminResult.status === 'fulfilled' ? healthStateOf(adminResult.value) : healthStateFromError()
      userHealth.value =
        userResult.status === 'fulfilled' ? healthStateOf(userResult.value) : healthStateFromError()
    } finally {
      loading.value = false
    }
  }

  return { adminHealth, userHealth, loading, checkHealth }
})
