<template>
  <div class="error-page">
    <h1 class="error-page__code">500</h1>
    <p class="error-page__message">サーバー内部エラーが発生しました</p>
    <RouterLink to="/">ホームへ戻る</RouterLink>
  </div>
</template>

<style scoped>
.error-page {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  text-align: center;
}
.error-page__code {
  margin: 0;
  font-size: var(--fs-4xl);
  color: var(--color-danger);
}
.error-page__message {
  margin: 0;
  color: var(--color-text-muted);
}
</style>
