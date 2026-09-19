<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { collectionLabel, findSameHeading } from '@/features/japanese-demo/logic'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import type { DemoWord } from '@/features/japanese-demo/types'

/**
 * 削除確認のダイアログ。
 *
 * 伝えること:
 * ・単語と読み
 * ・どの書籍のどの Unit に載っているか（**すべて**出す）
 * ・複数の書籍に載っているときは、影響が**すべての収録**に及ぶことをはっきり書く
 * ・学習の記録は残る（消えるのは母表と収録・詳細）
 */

const props = defineProps<{
  word: DemoWord
  state: 'IDLE' | 'DELETING' | 'FAILED'
  message: string
}>()

const emit = defineEmits<{ cancel: []; confirm: [] }>()

const store = useJapaneseDemoStore()

const collections = computed(() => props.word.collections.map((collection) => collectionLabel(collection)))
/** 同じ表記で読みが違う語（削除しても別の語は残ることを伝える）。 */
const sameHeading = computed(() => findSameHeading(store.words, props.word))
</script>

<template>
  <div class="overlay">
    <section
      class="dialog dialog--md" role="dialog" aria-modal="true"
      aria-labelledby="demoDeleteTitle" data-demo-delete-dialog
    >
      <div class="dialog__head">
        <h2 id="demoDeleteTitle" class="dialog__title">
          <AppIcon name="trash" size="sm" /> 単語の削除
        </h2>
        <button type="button" class="dialog__close" aria-label="閉じる" data-demo-delete-close @click="emit('cancel')">
          <AppIcon name="x" size="sm" />
        </button>
      </div>

      <div class="dialog__body">
        <div class="jp-demo-pair">
          <div class="jp-demo-pair__head">
            <span class="jp-demo-ja is-large">{{ word.heading }}</span>
            <span class="jp-demo-reading">{{ word.reading }}</span>
            <span class="badge badge--outline">{{ word.partOfSpeech }}</span>
          </div>
          <p class="jp-demo-zh">{{ word.chineseMeaning }}</p>
        </div>

        <section class="jp-demo-section">
          <h3 class="jp-demo-section__title">
            収録（{{ collections.length }} 件）
          </h3>
          <ul data-demo-delete-collections>
            <li v-for="collection in collections" :key="collection">{{ collection }}</li>
          </ul>
          <!-- 複数の本に載っている語は、影響が全部に及ぶことを明示する -->
          <p v-if="collections.length > 1" class="alert alert--warning" data-demo-delete-multi>
            この単語は <strong>{{ collections.length }} 冊</strong>に収録されています。
            削除すると<strong>すべての書籍の収録</strong>が対象になります（いま見ている書籍の分だけを外すことはできません）。
          </p>
          <p v-else-if="collections.length === 1" class="jp-demo-section__hint">
            収録は 1 冊です。削除するとこの収録も一緒に消えます。
          </p>
          <p v-else class="jp-demo-section__hint">
            収録がありません（詳細情報だけを持っている語）。
          </p>
        </section>

        <section class="jp-demo-section">
          <h3 class="jp-demo-section__title">削除するとどうなるか</h3>
          <ul class="jp-demo-section__hint">
            <li>単語の一覧から消えます。</li>
            <li>収録・詳細情報も一緒に消えます。</li>
            <li><strong>これまでの学習の記録は残ります</strong>（学習した事実は消しません）。</li>
          </ul>
          <p v-if="sameHeading.length > 0" class="jp-demo-section__hint" data-demo-delete-same-heading>
            同じ表記の
            <template v-for="(other, index) in sameHeading" :key="other.id">
              {{ index > 0 ? '、' : '' }}「{{ other.heading }}（{{ other.reading }}）」
            </template>
            は<strong>別の単語</strong>なので、こちらは残ります。
          </p>
        </section>

        <p v-if="state === 'FAILED'" class="alert alert--danger" data-demo-delete-error>
          {{ message }}
        </p>
      </div>

      <div class="dialog__foot">
        <button type="button" class="btn btn--secondary" data-demo-delete-cancel @click="emit('cancel')">
          キャンセル
        </button>
        <button
          type="button" class="btn btn--danger" data-demo-delete-confirm
          :disabled="state === 'DELETING'" @click="emit('confirm')"
        >
          <AppIcon name="trash" size="sm" /> {{ state === 'DELETING' ? '削除しています…' : '削除する' }}
        </button>
      </div>
    </section>
  </div>
</template>
