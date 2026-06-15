<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useNotify } from '../composables/useNotify';
import { getConversations, openConversation } from '../api/chat';
import { getCurrentUser } from '../api/matchmate';
import { useWebSocket } from '../composables/useWebSocket';
import type { ConversationVO, WsPushPayload } from '../models/chat';
import { MESSAGE_STATUS, isMessageRead, messageReadText } from '../utils/chat';
import { getRequestErrorMessage, isUnauthorizedError } from '../utils/http';
import { formatConversationTime } from '../utils/time';

const router = useRouter();
const route = useRoute();
const { showNotify } = useNotify();
const { connect, disconnect, onMessage } = useWebSocket();

const conversations = ref<ConversationVO[]>([]);
const loading = ref(false);
const loadFailed = ref(false);
const loginRequired = ref(false);
const searchText = ref('');
const currentUserId = ref<number | null>(null);

const filteredConversations = computed(() => {
  if (!searchText.value.trim()) return conversations.value;
  const kw = searchText.value.trim().toLowerCase();
  return conversations.value.filter(
    (c) => (c.targetUsername ?? '').toLowerCase().includes(kw)
      || (c.lastMessage ?? '').toLowerCase().includes(kw),
  );
});

const loadConversations = async () => {
  try {
    loading.value = true;
    loadFailed.value = false;
    loginRequired.value = false;
    const currentUser = await getCurrentUser();
    currentUserId.value = currentUser.id;
    conversations.value = await getConversations();
  } catch (error) {
    conversations.value = [];
    if (isUnauthorizedError(error)) {
      loginRequired.value = true;
      showNotify('请先登录后查看消息');
      return;
    }
    loadFailed.value = true;
    showNotify(getRequestErrorMessage(error, '消息加载失败，请稍后重试'));
  } finally {
    loading.value = false;
  }
};

const isLastMessageMine = (conv: ConversationVO) =>
  currentUserId.value !== null && conv.lastMessageSenderId === currentUserId.value;

const goToLogin = () => {
  router.push({
    path: '/login',
    query: { redirect: route.fullPath },
  });
};

const handleNewMessage = (payload: WsPushPayload) => {
  if (payload.type !== 'new_message') return;
  const msg = payload.data;
  // 更新或插入会话
  const idx = conversations.value.findIndex((c) => c.id === msg.conversationId);
  if (idx !== -1) {
    const conv = conversations.value[idx];
    conv.lastMessage = msg.content;
    conv.lastMessageTime = msg.createTime;
    conv.lastMessageSenderId = msg.senderId;
    conv.lastMessageReceiverId = msg.receiverId;
    conv.lastMessageStatus = msg.status;
    conv.unreadCount += 1;
    // 移到列表顶部
    conversations.value.splice(idx, 1);
    conversations.value.unshift(conv);
  } else {
    // 新会话，重新加载列表
    loadConversations();
  }
};

const handleMessagesRead = (payload: WsPushPayload) => {
  if (payload.type !== 'messages_read') return;
  const idx = conversations.value.findIndex(
    (conv) => conv.id === payload.data.conversationId,
  );
  if (idx === -1) return;
  const conv = conversations.value[idx];
  if (conv.lastMessageSenderId === currentUserId.value) {
    conv.lastMessageStatus = MESSAGE_STATUS.READ;
  }
};

const handleWsMessage = (payload: WsPushPayload) => {
  handleNewMessage(payload);
  handleMessagesRead(payload);
};

const enterConversation = async (conv: ConversationVO) => {
  // 打开会话：清空未读
  try {
    await openConversation(conv.id);
    conv.unreadCount = 0;
  } catch {
    // 静默失败
  }
  router.push({
    path: `/chat/${conv.id}`,
    query: {
      targetUserId: conv.targetUserId,
      username: conv.targetUsername,
      avatar: conv.targetAvatarUrl,
      isOnline: String(Boolean(conv.isOnline)),
    },
  });
};

let unsubMessage: (() => void) | null = null;

onMounted(() => {
  loadConversations();
  connect();
  unsubMessage = onMessage(handleWsMessage);
});

onUnmounted(() => {
  unsubMessage?.();
  disconnect();
});
</script>

<template>
  <div class="conversation-page">
    <div class="conversation-fixed">
      <div class="retention-notice">
        <van-icon name="info-o" />
        <span>聊天记录仅保留 24 小时，请及时查看重要信息</span>
      </div>
      <!-- 搜索框 -->
      <div class="search-bar">
        <van-icon name="search" size="16" color="#999" />
        <input
          v-model="searchText"
          class="search-input"
          type="text"
          placeholder="搜索会话..."
        />
        <van-icon
          v-if="searchText"
          name="clear"
          size="16"
          color="#999"
          @click="searchText = ''"
        />
      </div>
    </div>

    <main class="conversation-scroll">
      <van-loading v-if="loading" class="page-loading" vertical>
        加载中...
      </van-loading>

      <div v-else-if="loginRequired" class="login-hint">
        <van-icon name="chat-o" size="28" color="#1989fa" />
        <strong>请先登录后查看消息</strong>
        <p>登录后可以查看会话、未读消息和聊天记录。</p>
        <van-button round size="small" type="primary" @click="goToLogin">
          去登录
        </van-button>
      </div>

      <div v-else-if="loadFailed" class="login-hint">
        <van-icon name="warning-o" size="28" color="#ee0a24" />
        <strong>消息暂时加载失败</strong>
        <p>网络可能开小差了，请稍后重试。</p>
        <van-button round size="small" type="primary" @click="loadConversations">
          重试
        </van-button>
      </div>

      <template v-else-if="filteredConversations.length > 0">
        <button
          v-for="conv in filteredConversations"
          :key="conv.id"
          type="button"
          class="conv-item"
          @click="enterConversation(conv)"
        >
          <div class="conv-avatar">
            <van-image
              v-if="conv.targetAvatarUrl"
              :src="conv.targetAvatarUrl"
              round
              width="48"
              height="48"
              fit="cover"
            />
            <van-icon v-else name="contact" size="32" color="#c8c9cc" />
            <span v-if="conv.isOnline" class="online-dot" />
          </div>
          <div class="conv-info">
            <div class="conv-top">
              <span class="conv-name">{{ conv.targetUsername ?? '未知用户' }}</span>
              <span class="conv-time">{{ formatConversationTime(conv.lastMessageTime) }}</span>
            </div>
            <div class="conv-bottom">
              <span class="conv-last-msg">{{ conv.lastMessage ?? '' }}</span>
              <span
                v-if="isLastMessageMine(conv)"
                class="message-status"
                :class="{ read: isMessageRead(conv.lastMessageStatus) }"
              >
                {{ messageReadText(conv.lastMessageStatus) }}
              </span>
              <van-badge v-else-if="conv.unreadCount > 0" :content="conv.unreadCount" max="99" />
            </div>
          </div>
        </button>
      </template>

      <van-empty v-else description="暂无会话" />
    </main>
  </div>
</template>

<style scoped>
.conversation-page {
  display: flex;
  flex-direction: column;
  height: calc(100dvh - var(--app-nav-height) - var(--app-tabbar-height) - var(--app-safe-bottom));
  min-height: 0;
  max-width: 100%;
  overflow: hidden;
  background: var(--app-bg);
}

.conversation-fixed {
  flex: 0 0 auto;
  background: var(--app-bg);
}

.conversation-scroll {
  flex: 1 1 auto;
  min-height: 0;
  padding-bottom: 14px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.conversation-scroll::-webkit-scrollbar {
  display: none;
}

.page-loading {
  padding-top: 80px;
}

.login-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin: 48px 24px 0;
  padding: 28px 18px;
  color: var(--app-text-secondary);
  text-align: center;
  background: var(--app-surface);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-shadow-sm);
}

.login-hint strong {
  color: var(--app-text);
  font-size: 16px;
}

.login-hint p {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.retention-notice {
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: calc(100% - (var(--app-page-padding) * 2));
  padding: 9px 12px;
  margin: 10px var(--app-page-padding) 4px;
  overflow: hidden;
  color: #9a692d;
  font-size: 12px;
  background: #fff6e9;
  border: 1px solid #f9e6c9;
  border-radius: 12px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: calc(100% - (var(--app-page-padding) * 2));
  max-width: 100%;
  min-width: 0;
  padding: 14px 15px;
  margin: 0 var(--app-page-padding) 10px;
  overflow: hidden;
  color: inherit;
  text-align: left;
  background: var(--app-surface);
  border: 1px solid rgb(255 255 255 / 76%);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-shadow-sm);
  cursor: pointer;
  transition: transform var(--app-duration-fast) var(--app-ease);
}

.conv-item:active {
  background: var(--app-surface-muted);
  transform: scale(.992);
}

.conv-avatar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  background: var(--app-surface-muted);
  border-radius: 50%;
}

.online-dot {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 12px;
  height: 12px;
  background: var(--app-success);
  border: 2px solid var(--app-surface);
  border-radius: 50%;
}

.conv-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.conv-top,
.conv-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.conv-name {
  overflow: hidden;
  color: var(--app-text);
  font-size: 16px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  flex-shrink: 0;
  color: var(--app-text-muted);
  font-size: 12px;
}

.conv-last-msg {
  flex: 1;
  min-width: 0;
  margin-right: 8px;
  overflow: hidden;
  color: var(--app-text-muted);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-status {
  flex-shrink: 0;
  padding: 2px 7px;
  color: var(--app-text-muted);
  font-size: 11px;
  line-height: 1.3;
  background: var(--app-surface-muted);
  border-radius: var(--app-pill-radius);
}

.message-status.read {
  color: var(--app-primary);
  background: var(--app-primary-soft);
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: calc(100% - (var(--app-page-padding) * 2));
  min-height: 42px;
  padding: 8px 13px;
  margin: 8px var(--app-page-padding) 12px;
  overflow: hidden;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-pill-radius);
  box-shadow: var(--app-shadow-sm);
}

.search-input {
  flex: 1;
  color: var(--app-text);
  font-size: 16px;
  background: transparent;
  border: 0;
  outline: none;
}

.search-input::placeholder {
  color: var(--app-text-muted);
}
</style>
