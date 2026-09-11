import { ref } from 'vue'

const STORAGE_KEY = 'study21.sidebar'

function readCollapsed(): boolean {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === 'collapsed'
  } catch {
    return false
  }
}

/**
 * 侧栏状态：桌面端折叠（is-collapsed）、移动端抽屉（is-open）。
 * 折叠状态持久化到 localStorage，与 ui-demo 的 shell.js 行为一致。
 */
export function useSidebar() {
  const collapsed = ref(readCollapsed())
  const drawerOpen = ref(false)

  function toggleCollapsed(): void {
    collapsed.value = !collapsed.value
    try {
      window.localStorage.setItem(STORAGE_KEY, collapsed.value ? 'collapsed' : 'expanded')
    } catch {
      /* ignore */
    }
  }

  function openDrawer(): void {
    drawerOpen.value = true
  }

  function closeDrawer(): void {
    drawerOpen.value = false
  }

  /** 顶栏菜单按钮：桌面折叠 / 移动端开抽屉。 */
  function toggle(): void {
    if (window.innerWidth <= 768) {
      drawerOpen.value = !drawerOpen.value
    } else {
      toggleCollapsed()
    }
  }

  return { collapsed, drawerOpen, toggleCollapsed, openDrawer, closeDrawer, toggle }
}
