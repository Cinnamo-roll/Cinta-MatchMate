<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useNotify } from '../composables/useNotify';
import { getConversations, openConversation } from '../api/chat';
import { useWebSocket } from '../composables/useWebSocket';
import type { ConversationVO, WsPushPayload } from '../models/chat';

const router = useRouter();
const { showNotify } = useNotify();
const { connect, disconnect, onMessage } = useWebSocket();

const conversations = ref<ConversationVO[]>([]);
const loading = ref(false);
const searchText = ref('');

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
    conversations.value = await getConversations();
  } catch {
    showNotify('会话列表加载失败');
  } finally {
    loading.value = false;
  }
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
    conv.unreadCount += 1;
    // 移到列表顶部
    conversations.value.splice(idx, 1);
    conversations.value.unshift(conv);
  } else {
    // 新会话，重新加载列表
    loadConversations();
  }
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

const formatTime = (timeStr: string | null) => {
  if (!timeStr) return '';
  const date = new Date(timeStr);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  // 今天内显示时间
  if (diff < 86400000 && date.getDate() === now.getDate()) {
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  }
  // 昨天
  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  if (date.getDate() === yesterday.getDate()) {
    return '昨天';
  }
  // 今年内显示月日
  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}/${date.getDate()}`;
  }
  return `${date.getFullYear()}/${date.getMonth() + 1}/${date.getDate()}`;
};

let unsubMessage: (() => void) | null = null;

onMounted(() => {
  loadConversations();
  connect();
  unsubMessage = onMessage(handleNewMessage);
});

onUnmounted(() => {
  unsubMessage?.();
  disconnect();
});
</script>

<template>
  <div class="conversation-page">
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

    <van-loading v-if="loading" class="page-loading" vertical>
      加载中...
    </van-loading>

    <template v-else-if="filteredConversations.length > 0">
      <div
        v-for="conv in filteredConversations"
        :key="conv.id"
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
            <span class="conv-time">{{ formatTime(conv.lastMessageTime) }}</span>
          </div>
          <div class="conv-bottom">
            <span class="conv-last-msg">{{ conv.lastMessage ?? '' }}</span>
            <van-badge v-if="conv.unreadCount > 0" :content="conv.unreadCount" max="99" />
          </div>
        </div>
      </div>
    </template>

    <van-empty v-else description="暂无会话" />
  </div>
</template>

<style scoped>
.conversation-page {
  display: flex;
  flex-direction: column;
  height: calc(100dvh - var(--van-nav-bar-height, 46px) - var(--van-tabbar-height, 50px));
  overflow-y: auto;
  overscroll-behavior-y: none;
  background: #fff;
}

.page-loading {
  padding-top: 80px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.conv-item:active {
  background: #f2f3f5;
}

.conv-avatar {
  flex-shrink: 0;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #f5f5f5;
}

.online-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 12px;
  height: 12px;
  background: #07c160;
  border: 2px solid #fff;
  border-radius: 50%;
}

.conv-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.conv-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.conv-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 12px;
  color: #999;
  flex-shrink: 0;
}

.conv-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.conv-last-msg {
  font-size: 13px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  margin-right: 8px;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 12px;
  padding: 8px 12px;
  background: #f5f5f5;
  border-radius: 8px;
}

.search-input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 14px;
  color: #333;
}

.search-input::placeholder {
  color: #999;
}
</style>
