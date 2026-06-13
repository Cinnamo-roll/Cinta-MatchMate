<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useNotify } from '../composables/useNotify';
import { useWebSocket } from '../composables/useWebSocket';
import { logout } from '../api/matchmate';
import type { WsPushPayload } from '../models/chat';

type TabName = 'index' | 'discover' | 'message' | 'user';

const route = useRoute();
const router = useRouter();
const { message: notifyMsg, type: notifyType, showNotify } = useNotify();
const { connect, disconnect, forceDisconnect, onMessage } = useWebSocket();

const tabRoutes: Record<TabName, string> = {
  index: '/',
  discover: '/discover',
  message: '/team',
  user: '/user',
};

const title = computed(() => String(route.meta.title ?? 'MatchMate'));
const showBack = computed(() => Boolean(route.meta.showBack));
const showSearch = computed(() => Boolean(route.meta.showSearch));
const showNavbar = computed(() => !route.meta.hideNavbar);
const showTabbar = computed(() => !route.meta.hideTabbar);
const lockScroll = computed(() => Boolean(route.meta.lockScroll));
const activeTab = computed<TabName>({
  get: () => {
    if (route.meta.tabbar === 'user') return 'user';
    if (route.path === '/discover') return 'discover';
    if (route.path === '/team') return 'message';
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
let unsubscribeWs: (() => void) | null = null;

const handleWsMessage = (payload: WsPushPayload) => {
  if (payload.type !== 'account_banned') return;
  forceDisconnect();
  void logout().catch(() => undefined);
  showNotify(payload.data.message || '账号已被封禁，请联系管理员');
  router.replace('/login');
};

onMounted(() => {
  connect();
  unsubscribeWs = onMessage(handleWsMessage);
});
onBeforeUnmount(() => {
  unsubscribeWs?.();
  setDocumentScrollLock(false);
  disconnect();
});
</script>

<template>
  <div
    class="basic-layout"
    :class="{ 'basic-layout--locked': lockScroll }"
  >
    <van-nav-bar
      v-if="showNavbar"
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
      :class="{
        'layout-content--locked': lockScroll,
        'layout-content--full': !showNavbar,
      }"
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
      <van-tabbar-item icon="apps-o" name="discover">
        发现
      </van-tabbar-item>
      <van-tabbar-item icon="chat-o" name="message">
        消息
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
  overflow-x: hidden;
  overscroll-behavior-x: none;
  touch-action: pan-y;
  background: #f7f8fa;
}

.layout-content {
  display: flow-root;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: calc(100dvh - 46px);
  overflow-x: hidden;
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

.layout-content--locked.layout-content--full {
  height: 100dvh;
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
