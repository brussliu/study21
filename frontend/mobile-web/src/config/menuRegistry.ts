import type { MenuItem } from '@study21/web-shared'
import type { MobileArea } from '@/router'

/**
 * Menu Registry（Mobile）
 * 業務メニューは未決定のため空。フレームワークのホーム相当のみ提供する。
 */
export const businessMenu: MenuItem[] = []

export function frameworkMenu(area: MobileArea): MenuItem[] {
  return [
    {
      id: `${area}-home`,
      label: 'ホーム',
      path: `/${area}/home`,
      enabled: true
    }
  ]
}

export function homePathOf(area: MobileArea): string {
  return `/${area}/home`
}
