<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useNotify } from '../composables/useNotify';

type TabName = 'index' | 'team' | 'user';

const route = useRoute();
const router = useRouter();
const { message: notifyMsg, type: notifyType } = useNotify();

const tabRoutes: Record<TabName, string> = {
  index: '/',
  team: '/team',
  user: '/user',
};

const title = computed(() => String(route.meta.title ?? 'MatchMate'));
const showBack = computed(() => Boolean(route.meta.showBack));
const showSearch = computed(() => Boolean(route.meta.showSearch));
const showTabbar = computed(() => !route.meta.hideTabbar);
const lockScroll = computed(() => Boolean(route.meta.lockScroll));
const activeTab = computed<TabName>({
  get: () => {
    if (route.meta.tabbar === 'user') return 'user';
    if (route.path === '/team') return 'team';
    if (route.path === '/user') return 'user';
    return 'index';
  },
  set: (tab) => {
    router.push(tabRoutes[tab]);
  },
});

const onClickLeft = () => {
  if (window.history.length > 1) {
    router.back();
    return;
  }

  router.replace('/');
};

const onClickSearch = () => {
  router.push('/search');
};

const setDocumentScrollLock = (locked: boolean) => {
  document.documentElement.classList.toggle('scroll-locked', locked);
  document.body.classList.toggle('scroll-locked', locked);
  if (locked) window.scrollTo(0, 0);
};

watch(lockScroll, setDocumentScrollLock, { immediate: true });
onBeforeUnmount(() => setDocumentScrollLock(false));
</script>

<template>
  <div
    class="basic-layout"
    :class="{ 'basic-layout--locked': lockScroll }"
  >
    <van-nav-bar
      :title="title"
      :left-arrow="showBack"
      :left-text="showBack ? '返回' : ''"
      fixed
      placeholder
      safe-area-inset-top
      @click-left="onClickLeft"
    >
      <template v-if="showSearch" #right>
        <van-icon
          name="search"
          size="20"
          aria-label="搜索标签"
          @click="onClickSearch"
        />
      </template>
    </van-nav-bar>

    <main
      class="layout-content"
      :class="{ 'layout-content--locked': lockScroll }"
    >
      <router-view />
    </main>

    <van-tabbar
      v-if="showTabbar"
      v-model="activeTab"
      safe-area-inset-bottom
    >
      <van-tabbar-item icon="home-o" name="index">
        主页
      </van-tabbar-item>
      <van-tabbar-item icon="friends-o" name="team">
        队伍
      </van-tabbar-item>
      <van-tabbar-item icon="contact-o" name="user">
        我的
      </van-tabbar-item>
    </van-tabbar>

    <Transition name="notify">
      <div v-if="notifyMsg" class="bottom-notify" :class="notifyType">
        {{ notifyMsg }}
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.basic-layout {
  width: 100%;
  max-width: 100vw;
  min-height: 100vh;
  overflow-x: clip;
  background: #f7f8fa;
}

.layout-content {
  display: flow-root;
  width: 100%;
  min-width: 0;
  min-height: calc(100dvh - 46px);
}

.basic-layout--locked {
  height: 100dvh;
  min-height: 100dvh;
  overflow: hidden;
  overscroll-behavior: none;
}

.layout-content--locked {
  height: calc(100dvh - 46px);
  min-height: 0;
  overflow: hidden;
  overscroll-behavior: none;
  touch-action: none;
}

.bottom-notify {
  position: fixed;
  bottom: 70px;
  left: 50%;
  transform: translateX(-50%);
  padding: 10px 24px;
  color: #fff;
  font-size: 14px;
  white-space: nowrap;
  border-radius: 20px;
  z-index: 9999;
}

.bottom-notify.error {
  background: rgba(238, 10, 36, 0.85);
}

.bottom-notify.success {
  background: rgba(7, 193, 96, 0.85);
}

.notify-enter-active,
.notify-leave-active {
  transition: all 0.3s ease;
}

.notify-enter-from,
.notify-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(20px);
}
</style>
