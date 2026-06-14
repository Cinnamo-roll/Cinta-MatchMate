<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useNotify } from '../composables/useNotify';
import { useWebSocket } from '../composables/useWebSocket';
import { getCurrentUser, logout } from '../api/matchmate';
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
const isAuthPage = computed(() => route.path === '/login' || route.path === '/register');
const activeTab = computed<TabName>({
  get: () => {
    if (route.meta.tabbar === 'user') return 'user';
    if (route.path === '/login' || route.path === '/register') return 'user';
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
let websocketSubscribed = false;

const syncWebSocket = async () => {
  if (isAuthPage.value) {
    if (websocketSubscribed) {
      websocketSubscribed = false;
      disconnect();
    }
    return;
  }

  if (websocketSubscribed) return;

  try {
    await getCurrentUser();
    if (isAuthPage.value || websocketSubscribed) return;
    connect();
    websocketSubscribed = true;
  } catch {
    // Public pages remain available without a chat WebSocket.
  }
};

const handleWsMessage = (payload: WsPushPayload) => {
  if (payload.type !== 'account_banned') return;
  forceDisconnect();
  void logout().catch(() => undefined);
  showNotify(payload.data.message || '账号已被封禁，请联系管理员');
  router.replace('/login');
};

watch(() => route.path, () => {
  void syncWebSocket();
}, { immediate: true });

onMounted(() => {
  unsubscribeWs = onMessage(handleWsMessage);
});
onBeforeUnmount(() => {
  unsubscribeWs?.();
  setDocumentScrollLock(false);
  if (websocketSubscribed) disconnect();
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
        'layout-content--with-tabbar': showTabbar,
      }"
    >
      <router-view v-slot="{ Component, route: currentRoute }">
        <Transition name="page" mode="out-in">
          <component :is="Component" :key="currentRoute.path" />
        </Transition>
      </router-view>
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
  min-height: 100dvh;
  overflow-x: hidden;
  overscroll-behavior-x: none;
  background: var(--app-bg);
}

.layout-content {
  display: flow-root;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: calc(100dvh - var(--app-nav-height));
  overflow-x: hidden;
}

.basic-layout--locked {
  height: 100dvh;
  min-height: 100dvh;
  overflow: hidden;
  overscroll-behavior: none;
}

.layout-content--locked {
  height: calc(100dvh - var(--app-nav-height));
  min-height: 0;
  overflow: hidden;
  overscroll-behavior: none;
}

.layout-content--locked.layout-content--full {
  height: 100dvh;
}

.layout-content--locked.layout-content--with-tabbar {
  height: calc(100dvh - var(--app-nav-height) - var(--app-tabbar-height) - var(--app-safe-bottom));
}

.layout-content--locked.layout-content--full.layout-content--with-tabbar {
  height: calc(100dvh - var(--app-tabbar-height) - var(--app-safe-bottom));
}

.bottom-notify {
  position: fixed;
  right: 16px;
  bottom: calc(var(--app-tabbar-height) + var(--app-safe-bottom) + 14px);
  left: 50%;
  width: max-content;
  max-width: min(calc(100vw - 32px), 420px);
  transform: translateX(-50%);
  padding: 11px 18px;
  color: #fff;
  font-size: 14px;
  line-height: 1.45;
  text-align: center;
  overflow-wrap: anywhere;
  border: 1px solid rgb(255 255 255 / 18%);
  border-radius: var(--app-pill-radius);
  box-shadow: 0 12px 32px rgb(25 30 52 / 22%);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  z-index: 9999;
}

.bottom-notify.error {
  background: rgb(214 67 91 / 92%);
}

.bottom-notify.success {
  background: rgb(24 157 104 / 92%);
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

.page-enter-active,
.page-leave-active {
  transition:
    opacity var(--app-duration-fast) ease,
    transform var(--app-duration) var(--app-ease);
}

.page-enter-from {
  opacity: 0;
  transform: translateY(5px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-3px);
}

@media (min-width: 600px) {
  .basic-layout {
    max-width: 480px;
  }

  .bottom-notify {
    right: auto;
  }
}
</style>
