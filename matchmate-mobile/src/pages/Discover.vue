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
  min-height: calc(100dvh - var(--van-nav-bar-height, 46px) - var(--van-tabbar-height, 50px));
  padding: 24px 16px;
  background: #f7f8fa;
  box-sizing: border-box;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 24px 12px;
}

.feature-item {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 9px;
  min-width: 0;
  padding: 0;
  color: #323233;
  background: transparent;
  border: 0;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.feature-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  box-shadow: 0 3px 10px rgb(0 0 0 / 8%);
}

.feature-name {
  max-width: 100%;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
