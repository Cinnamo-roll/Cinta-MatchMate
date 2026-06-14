<script setup lang="ts">
import { useRouter } from 'vue-router';

const router = useRouter();
</script>

<template>
  <main class="not-found-page">
    <div class="not-found-mark">404</div>
    <h1>这里暂时没有内容</h1>
    <p>页面可能已移动，回到首页继续看看吧。</p>
    <van-button round type="primary" @click="router.replace('/')">
      返回首页
    </van-button>
  </main>
</template>

<style scoped>
.not-found-page {
  display: flex;
  align-items: center;
  flex-direction: column;
  min-height: calc(100dvh - var(--app-nav-height));
  padding: 18dvh var(--app-page-padding) 40px;
  color: var(--app-text);
  text-align: center;
  background: var(--app-bg);
}

.not-found-mark {
  display: grid;
  width: 96px;
  height: 96px;
  color: var(--app-primary);
  font-size: 26px;
  font-weight: 800;
  place-items: center;
  background: var(--app-primary-soft);
  border-radius: 30px;
  transform: rotate(-4deg);
}

h1 {
  margin: 24px 0 7px;
  font-size: 22px;
  letter-spacing: -.4px;
}

p {
  margin: 0 0 24px;
  color: var(--app-text-muted);
  font-size: 14px;
}
</style>
