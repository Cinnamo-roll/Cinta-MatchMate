<script setup lang="ts">
import { useRouter } from 'vue-router';

const router = useRouter();

const features = [
  {
    name: '打牌记账本',
    icon: 'records-o',
    color: '#ff976a',
    path: '/discover/card-ledger',
  },
];
</script>

<template>
  <div class="discover-page">
    <section class="discover-hero">
      <span>一起做点有意思的事</span>
      <h1>发现更多玩法</h1>
      <p>轻量工具和共同活动，都从这里开始。</p>
    </section>

    <div class="feature-grid">
      <button
        v-for="feature in features"
        :key="feature.path"
        class="feature-item"
        type="button"
        @click="router.push(feature.path)"
      >
        <span
          class="feature-icon"
          :style="{ backgroundColor: feature.color }"
        >
          <van-icon :name="feature.icon" size="28" color="#fff" />
        </span>
        <span class="feature-name">{{ feature.name }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.discover-page {
  min-height: calc(100dvh - var(--app-nav-height) - var(--app-tabbar-height) - var(--app-safe-bottom));
  padding: 18px var(--app-page-padding) 28px;
  background: var(--app-bg);
  box-sizing: border-box;
}

.discover-hero {
  position: relative;
  padding: 22px;
  margin-bottom: 18px;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(circle at 88% 18%, rgb(255 255 255 / 22%) 0 42px, transparent 43px),
    linear-gradient(135deg, #5968e9 0%, #7b64df 58%, #34a6b8 120%);
  border-radius: 24px;
  box-shadow: 0 16px 34px rgb(89 104 233 / 20%);
}

.discover-hero span {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .6px;
  opacity: .78;
}

.discover-hero h1 {
  margin: 7px 0 4px;
  font-size: 24px;
  line-height: 1.2;
  letter-spacing: -.5px;
}

.discover-hero p {
  margin: 0;
  font-size: 13px;
  opacity: .84;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.feature-item {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 11px;
  min-width: 0;
  min-height: 116px;
  padding: 16px 8px 14px;
  color: var(--app-text);
  background: var(--app-surface);
  border: 1px solid rgb(255 255 255 / 75%);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-shadow-sm);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: transform var(--app-duration-fast) var(--app-ease);
}

.feature-item:active {
  transform: scale(.97);
}

.feature-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 17px;
  box-shadow: 0 8px 18px rgb(245 157 72 / 22%);
}

.feature-name {
  max-width: 100%;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
