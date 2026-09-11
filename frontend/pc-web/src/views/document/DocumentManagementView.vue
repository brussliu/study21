<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import DocumentFolderView from '@/views/document/DocumentFolderView.vue'
import DocumentListView from '@/views/document/DocumentListView.vue'

type DocumentTab = 'list' | 'folder'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isFamilyUser = computed(() => auth.role === 'STUDENT' || auth.role === 'GUARDIAN')

/** ?view=folder のときだけフォルダ、それ以外（未指定含む）は一覧を既定にする。 */
function tabFromQuery(view: unknown): DocumentTab {
  return view === 'folder' ? 'folder' : 'list'
}

// タブ状態は URL クエリ ?view=folder と同期する（リロード・直リンクでもタブが保たれる）。
const activeTab = ref<DocumentTab>(tabFromQuery(route.query.view))

watch(() => route.query.view, (view) => { activeTab.value = tabFromQuery(view) })

function selectTab(tab: DocumentTab): void {
  if (activeTab.value === tab) return
  activeTab.value = tab
  const query = { ...route.query }
  if (tab === 'folder') query.view = 'folder'
  else delete query.view
  void router.replace({ path: route.path, query })
}
</script>

<template>
  <div v-if="isFamilyUser" class="doc-management">
    <div class="tabs" role="tablist">
      <button
        type="button"
        class="tabs__tab"
        :class="{ 'is-active': activeTab === 'list' }"
        role="tab"
        :aria-selected="activeTab === 'list'"
        @click="selectTab('list')"
      >
        一覧
      </button>
      <button
        type="button"
        class="tabs__tab"
        :class="{ 'is-active': activeTab === 'folder' }"
        role="tab"
        :aria-selected="activeTab === 'folder'"
        @click="selectTab('folder')"
      >
        フォルダ
      </button>
    </div>

    <section
      class="tabs__panel"
      :class="{ 'is-active': activeTab === 'list' }"
      role="tabpanel"
      aria-label="一覧"
    >
      <DocumentListView v-if="activeTab === 'list'" />
    </section>

    <section
      class="tabs__panel"
      :class="{ 'is-active': activeTab === 'folder' }"
      role="tabpanel"
      aria-label="フォルダ"
    >
      <DocumentFolderView v-if="activeTab === 'folder'" />
    </section>
  </div>
  <section v-else class="card page-body">
    <h2>資料管理</h2>
    <p>家族共有資料は、生徒または保護者としてログインした場合のみ表示できます。</p>
  </section>
</template>
