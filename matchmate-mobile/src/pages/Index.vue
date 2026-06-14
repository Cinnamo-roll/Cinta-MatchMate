<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useNotify } from '../composables/useNotify';
import { searchUsers } from '../api/matchmate';
import UserCard from '../components/UserCard.vue';
import type { User } from '../models/user';

const PAGE_SIZE = 10;
const router = useRouter();
const { showNotify } = useNotify();
const users = ref<User[]>([]);
const loading = ref(false);
const refreshing = ref(false);
const finished = ref(false);
const loadFailed = ref(false);
const total = ref(0);
const introCollapsed = ref(false);
const introProgress = ref(0);
let pageNum = 1;
let requestInFlight = false;

const introStyle = computed(() => {
  const progress = introProgress.value;
  return {
    '--intro-copy-max-height': `${116 * (1 - progress)}px`,
    '--intro-copy-opacity': `${Math.max(0, 1 - progress * 1.35)}`,
    '--intro-copy-y': `${-12 * progress}px`,
    '--intro-copy-scale': `${1 - progress * 0.02}`,
    '--intro-padding-top': `${10 * (1 - progress)}px`,
    '--intro-padding-bottom': `${18 - 8 * progress}px`,
    '--intro-min-height': `${38 * progress}px`,
  };
});

const appendUniqueUsers = (records: User[]) => {
  const userMap = new Map(users.value.map((user) => [user.id, user]));
  records.forEach((user) => userMap.set(user.id, user));
  users.value = [...userMap.values()];
};

const loadUsers = async (reset = false) => {
  if (requestInFlight || (!reset && finished.value)) return;
  if (reset) {
    pageNum = 1;
    users.value = [];
    total.value = 0;
    finished.value = false;
  }

  requestInFlight = true;
  try {
    loading.value = true;
    loadFailed.value = false;
    const page = await searchUsers('', [], pageNum, PAGE_SIZE);
    total.value = page.total;
    appendUniqueUsers(page.records);
    finished.value =
      users.value.length >= page.total || page.records.length < PAGE_SIZE;
    pageNum += 1;
  } catch {
    loadFailed.value = true;
    showNotify('用户列表加载失败，请稍后重试');
  } finally {
    requestInFlight = false;
    loading.value = false;
    refreshing.value = false;
  }
};

const refreshUsers = () => loadUsers(true);
const retryLoad = () => loadUsers(users.value.length === 0);
const handleHomeScroll = (event: Event) => {
  const scrollTop = (event.target as HTMLElement).scrollTop;
  introProgress.value = Math.min(scrollTop / 150, 1);
  introCollapsed.value = introProgress.value > 0.82;
};

onMounted(() => loadUsers(true));
</script>

<template>
  <div class="home-page">
    <div
      class="home-fixed"
      :class="{ 'intro-collapsed': introCollapsed }"
      :style="introStyle"
    >
      <div class="home-search-bar">
        <button
          class="home-search"
          type="button"
          aria-label="搜索用户或标签"
          @click="router.push('/search')"
        >
          <van-icon name="search" size="18" />
          <span>搜索用户或标签</span>
        </button>
      </div>

      <section class="home-intro">
        <div class="home-intro-copy">
          <span class="eyebrow">MATCHMATE</span>
          <h1>发现合拍的伙伴</h1>
          <p>从兴趣和状态出发，认识更适合一起行动的人。</p>
        </div>
        <span class="people-count">
          {{ total }} 位伙伴
        </span>
      </section>
    </div>

    <div class="home-scroll" @scroll="handleHomeScroll">
      <van-pull-refresh v-model="refreshing" class="home-refresh" @refresh="refreshUsers">
        <div v-if="loading && users.length === 0" class="skeleton-list">
          <van-skeleton v-for="item in 3" :key="item" avatar title :row="2" />
        </div>

        <van-empty
          v-else-if="loadFailed && users.length === 0"
          image="network"
          description="加载失败，请检查网络后重试"
        >
          <van-button round size="small" type="primary" @click="retryLoad">
            重新加载
          </van-button>
        </van-empty>

        <van-empty
          v-else-if="!loading && users.length === 0"
          description="还没有伙伴入驻"
        />

        <van-list
          v-else
          v-model:loading="loading"
          :finished="finished"
          :immediate-check="false"
          finished-text="没有更多伙伴啦"
          @load="loadUsers()"
        >
          <div class="user-list">
            <UserCard v-for="user in users" :key="user.id" :user="user" />
          </div>

          <div v-if="loadFailed" class="load-error">
            <span>这一页加载失败了</span>
            <button type="button" @click="retryLoad">点此重试</button>
          </div>
        </van-list>
      </van-pull-refresh>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  height: calc(100dvh - var(--app-nav-height) - var(--app-tabbar-height) - var(--app-safe-bottom));
  min-height: 0;
  overflow: hidden;
  background: var(--app-bg);
  box-sizing: border-box;
}

.home-fixed {
  flex: 0 0 auto;
  padding: 0 var(--app-page-padding);
  background: var(--app-bg);
  transition:
    box-shadow var(--app-duration) ease,
    transform var(--app-duration) var(--app-ease);
}

.home-fixed.intro-collapsed {
  box-shadow: 0 10px 24px rgb(37 45 76 / 5%);
}

.home-scroll {
  flex: 1 1 auto;
  min-height: 0;
  padding: 0 var(--app-page-padding) 24px;
  overflow-y: auto;
  overscroll-behavior-y: contain;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.home-scroll::-webkit-scrollbar {
  display: none;
}

.home-refresh {
  min-height: 100%;
}

.home-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 42px;
  padding: 0 15px;
  color: var(--app-text-muted);
  font-size: 14px;
  text-align: left;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-pill-radius);
  box-shadow: var(--app-shadow-sm);
  transition:
    transform var(--app-duration-fast) var(--app-ease),
    border-color var(--app-duration-fast) ease;
}

.home-search:active {
  border-color: rgb(89 104 233 / 24%);
  transform: scale(.99);
}

.home-search-bar {
  margin: 0 calc(var(--app-page-padding) * -1);
  padding: 12px var(--app-page-padding) 10px;
}

.home-intro {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  min-height: var(--intro-min-height, 0);
  padding: var(--intro-padding-top, 10px) 4px var(--intro-padding-bottom, 18px);
  overflow: hidden;
  transition:
    padding 80ms linear,
    min-height 80ms linear;
}

.home-fixed.intro-collapsed .home-intro {
  align-items: center;
  justify-content: flex-end;
  min-height: 38px;
  padding: 0 4px 10px;
}

.home-intro-copy {
  min-width: 0;
  max-height: var(--intro-copy-max-height, 116px);
  opacity: var(--intro-copy-opacity, 1);
  transform: translateY(var(--intro-copy-y, 0)) scale(var(--intro-copy-scale, 1));
  overflow: hidden;
  transition:
    max-height 80ms linear,
    opacity 80ms linear,
    transform 80ms linear;
}

.home-fixed.intro-collapsed .home-intro-copy {
  pointer-events: none;
}

.eyebrow {
  color: var(--app-primary);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 1.6px;
}

.home-intro h1 {
  margin: 3px 0 4px;
  color: var(--app-text);
  font-size: 24px;
  line-height: 1.28;
  letter-spacing: -.6px;
}

.home-intro p {
  max-width: 245px;
  margin: 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.68;
}

.people-count {
  flex-shrink: 0;
  padding: 6px 10px;
  color: var(--app-primary);
  font-size: 11px;
  font-weight: 700;
  background: var(--app-primary-soft);
  border-radius: var(--app-pill-radius);
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-list {
  display: grid;
  gap: 12px;
}

.skeleton-list :deep(.van-skeleton) {
  padding: 18px 15px;
  background: var(--app-surface);
  border-radius: var(--app-card-radius);
}

.load-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 18px 0 4px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.load-error button {
  padding: 4px 10px;
  color: var(--app-primary);
  background: var(--app-primary-soft);
  border: 0;
  border-radius: var(--app-pill-radius);
}

:deep(.van-list__finished-text),
:deep(.van-list__loading) {
  color: var(--app-text-muted);
  font-size: 12px;
}
</style>
