<script setup lang="ts">
/**
 * mobile-web の暫定トップ（準備中）。
 * 業務画面を追加したら、このページはルートから外す（もしくは通知ページとして残す）。
 */
</script>

<template>
  <main class="prep">
    <h1 class="prep__title">Study 2.1 Mobile</h1>
    <p class="prep__lead">この画面は準備中です。</p>
    <p class="prep__note">
      mobile-web は現在、画面の骨組みだけを置いています。内容は後で追加します。<br />
      PC 版は <code>/</code>（pc-web）から利用できます。
    </p>
  </main>
</template>

<style scoped>
.prep {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3, 12px);
  padding: var(--sp-6, 24px);
  text-align: center;
}

.prep__title {
  margin: 0;
  font-size: var(--fs-xl, 24px);
  color: var(--color-text);
}

.prep__lead {
  margin: 0;
  font-size: var(--fs-md, 16px);
  color: var(--color-text);
}

.prep__note {
  margin: 0;
  max-width: 32em;
  font-size: var(--fs-sm, 14px);
  line-height: var(--lh-normal, 1.7);
  color: var(--color-text-muted, #5b6b7a);
}
</style>
