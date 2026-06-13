<script setup lang="ts">
import { onMounted, onUnmounted, ref, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useNotify } from '../composables/useNotify';
import {
  closeConversation,
  findConversationId,
  getConversation,
  getMessages,
  openConversation,
  sendMessage,
} from '../api/chat';
import { getCurrentUser } from '../api/matchmate';
import { useWebSocket } from '../composables/useWebSocket';
import type { MessageVO, WsPushPayload } from '../models/chat';
import type { User } from '../models/user';

const route = useRoute();
const router = useRouter();
const { showNotify } = useNotify();
const { connect, disconnect, onMessage } = useWebSocket();

let conversationId = Number(route.params.id);
const targetUserId = ref(Number(route.query.targetUserId as string) || 0);
const targetUsername = ref((route.query.username as string) || (route.query.targetUsername as string) || '');
const targetAvatarUrl = ref((route.query.avatar as string) || (route.query.targetAvatarUrl as string) || '');
const targetIsOnline = ref(route.query.isOnline === 'true');

let unsubMessage: (() => void) | null = null;
const messages = ref<MessageVO[]>([]);
const inputText = ref('');
const sending = ref(false);
const loading = ref(false);
const loadingMore = ref(false);
const hasMore = ref(true);
const currentUser = ref<User | null>(null);
const pageNum = ref(1);
const PAGE_SIZE = 20;
let openConvDone = false;

const messageListEl = ref<HTMLElement | null>(null);

const scrollToBottom = () => {
  nextTick(() => {
    if (messageListEl.value) {
      messageListEl.value.scrollTop = messageListEl.value.scrollHeight;
    }
  });
};

const loadMessages = async (appendTop = false) => {
  if (conversationId === 0) return false;
  const previousScrollHeight = messageListEl.value?.scrollHeight ?? 0;
  try {
    if (appendTop) loadingMore.value = true;
    else loading.value = true;
    const list = await getMessages(conversationId, pageNum.value, PAGE_SIZE);
    if (list.length < PAGE_SIZE) hasMore.value = false;
    if (appendTop) {
      messages.value = [...list, ...messages.value];
      await nextTick();
      if (messageListEl.value) {
        messageListEl.value.scrollTop += messageListEl.value.scrollHeight - previousScrollHeight;
      }
    } else {
      messages.value = list;
    }
    if (!appendTop) {
      loading.value = false;
      await nextTick();
      if (messageListEl.value) {
        messageListEl.value.scrollTop = messageListEl.value.scrollHeight;
      }
    }
    return true;
  } catch {
    showNotify('消息加载失败');
    return false;
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
};

const loadMore = async () => {
  if (loadingMore.value || !hasMore.value) return;
  pageNum.value++;
  if (!await loadMessages(true)) pageNum.value--;
};

const onScroll = () => {
  if (!messageListEl.value) return;
  if (messageListEl.value.scrollTop <= 20) loadMore();
};

const isMsgRelevant = (msg: MessageVO) => {
  if (conversationId > 0) return msg.conversationId === conversationId;
  // 新会话：通过 targetUserId 匹配
  if (!currentUser.value) return false;
  const myId = currentUser.value.id;
  return (msg.senderId === myId && msg.receiverId === targetUserId.value)
      || (msg.receiverId === myId && msg.senderId === targetUserId.value);
};

const handleSend = async () => {
  const content = inputText.value.trim();
  if (!content || sending.value) return;
  if (!targetUserId.value || !currentUser.value) {
    showNotify('无法确定聊天对象');
    return;
  }

  sending.value = true;
  const tempId = -Date.now();
  const optimisticMsg: MessageVO = {
    id: tempId, conversationId: conversationId,
    senderId: currentUser.value.id, receiverId: targetUserId.value,
    content, messageType: 0, status: 0,
    createTime: new Date().toISOString(),
  };
  messages.value.push(optimisticMsg);
  inputText.value = '';
  scrollToBottom();

  try {
    const realMsg = await sendMessage({ receiverId: targetUserId.value, content });
    const idx = messages.value.findIndex((m) => m.id === tempId);
    if (idx !== -1) messages.value[idx] = realMsg;
    else messages.value.push(realMsg);
    // 新会话：更新 conversationId 并替换路由
    if (conversationId === 0 && realMsg.conversationId) {
      conversationId = realMsg.conversationId;
      router.replace({ path: `/chat/${conversationId}`, query: route.query });
    }
  } catch (e: unknown) {
    const idx = messages.value.findIndex((m) => m.id === tempId);
    if (idx !== -1) messages.value.splice(idx, 1);
    const errMsg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message || '发送失败';
    showNotify(errMsg);
  } finally {
    sending.value = false;
  }
};

const handleWsMessage = (payload: WsPushPayload) => {
  if (payload.type === 'messages_read') {
    if (payload.data.conversationId !== conversationId) return;
    messages.value.forEach((message) => {
      if (isSelf(message) && message.status === 0) {
        message.status = 1;
      }
    });
    return;
  }
  if (payload.type !== 'new_message') return;

  const msg = payload.data;
  if (!isMsgRelevant(msg) || messages.value.some((m) => m.id === msg.id)) return;
  messages.value.push(msg);
  scrollToBottom();
  if (conversationId > 0) void openConversation(conversationId);
};

const isSelf = (msg: MessageVO) => msg.senderId === currentUser.value?.id;

const formatTime = (timeStr: string) => {
  const date = new Date(timeStr);
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
};

onMounted(async () => {
  try { currentUser.value = await getCurrentUser(); } catch {
    showNotify('请先登录'); router.replace('/user'); return;
  }
  connect();
  unsubMessage = onMessage(handleWsMessage);

  // 新会话：尝试查找已有会话 ID
  if (conversationId === 0 && targetUserId.value > 0) {
    try {
      const existingId = await findConversationId(targetUserId.value);
      if (existingId) {
        conversationId = existingId;
        router.replace({ path: `/chat/${conversationId}`, query: route.query });
      }
    } catch { /* ignore */ }
  }

  if (conversationId > 0) {
    try {
      const conversation = await getConversation(conversationId);
      targetUserId.value = conversation.targetUserId;
      targetUsername.value = conversation.targetUsername || '';
      targetAvatarUrl.value = conversation.targetAvatarUrl || '';
      targetIsOnline.value = Boolean(conversation.isOnline);
    } catch {
      showNotify('会话信息加载失败');
      router.replace('/team');
      return;
    }
    if (!openConvDone) {
      try { await openConversation(conversationId); openConvDone = true; } catch { /* ignore */ }
    }
    await loadMessages();
  }
});

onUnmounted(() => {
  if (conversationId > 0) void closeConversation(conversationId);
  unsubMessage?.();
  disconnect();
});
</script>

<template>
  <div class="chat-detail">
    <van-nav-bar
      class="chat-navbar"
      left-arrow fixed placeholder safe-area-inset-top
      @click-left="router.back()"
    >
      <template #title>
        <div class="chat-navbar-title">
          <strong>{{ targetUsername || '聊天' }}</strong>
          <span :class="{ online: targetIsOnline }">
            {{ targetIsOnline ? '在线' : '离线' }}
          </span>
        </div>
      </template>
    </van-nav-bar>

    <div ref="messageListEl" class="message-list" @scroll="onScroll">
      <div class="retention-notice">聊天记录仅保留 24 小时</div>
      <div v-if="loadingMore" class="load-more-hint">加载更多...</div>
      <van-loading v-if="loading" class="page-loading" vertical>加载中...</van-loading>

      <template v-else-if="messages.length > 0">
        <div
          v-for="msg in messages" :key="msg.id"
          class="msg-row"
          :class="{ 'msg-row--self': isSelf(msg) }"
        >
          <!-- 对方头像 -->
          <van-image
            v-if="!isSelf(msg)"
            :src="targetAvatarUrl || undefined"
            round width="36" height="36" fit="cover"
            class="msg-avatar"
          >
            <template #error>
              <van-icon name="contact" size="22" color="#c8c9cc" />
            </template>
          </van-image>

          <div class="msg-content" :class="{ 'msg-content--self': isSelf(msg) }">
            <div class="msg-bubble" :class="{ 'msg-bubble--self': isSelf(msg) }">
              <span class="msg-text">{{ msg.content }}</span>
            </div>
            <div class="msg-meta">
              <span>{{ formatTime(msg.createTime) }}</span>
              <span v-if="isSelf(msg)" :class="{ read: msg.status === 1 }">
                {{ msg.status === 1 ? '已读' : '未读' }}
              </span>
            </div>
          </div>

          <!-- 自己头像 -->
          <van-image
            v-if="isSelf(msg)"
            :src="currentUser?.avatarUrl || undefined"
            round width="36" height="36" fit="cover"
            class="msg-avatar"
          >
            <template #error>
              <van-icon name="contact" size="22" color="#c8c9cc" />
            </template>
          </van-image>
        </div>
      </template>

      <van-empty v-else description="开始聊天吧" />
    </div>

    <div class="input-bar">
      <van-field
        v-model="inputText" class="input-field" type="text"
        placeholder="输入消息..." :disabled="sending" :border="false"
        @keydown.enter.prevent="handleSend"
      />
      <van-button
        class="send-btn" type="primary" size="small"
        :loading="sending" :disabled="!inputText.trim()"
        @click="handleSend"
      >发送</van-button>
    </div>
  </div>
</template>

<style scoped>
.chat-detail { display: flex; flex-direction: column; height: 100%; max-width: 100%; overflow-x: hidden; touch-action: pan-y; background: #f7f8fa; }
.chat-navbar {
  --van-nav-bar-height: 42px;
  --van-nav-bar-title-font-size: 15px;
}
.chat-navbar-title { display: flex; flex-direction: column; align-items: center; line-height: 1.15; }
.chat-navbar-title strong { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-navbar-title span { margin-top: 2px; color: #969799; font-size: 10px; font-weight: 400; }
.chat-navbar-title span.online { color: #07c160; }
.message-list { flex: 1; max-width: 100%; overflow-x: hidden; overflow-y: auto; padding: 12px 16px; overscroll-behavior-x: none; touch-action: pan-y; -webkit-overflow-scrolling: touch; }
.load-more-hint { text-align: center; padding: 8px; font-size: 12px; color: #999; }
.retention-notice {
  margin: 0 auto 12px;
  color: #969799;
  font-size: 11px;
  text-align: center;
}
.page-loading { padding-top: 80px; }

.msg-row { display: flex; align-items: flex-start; gap: 8px; max-width: 100%; min-width: 0; margin-bottom: 16px; }
.msg-row--self { justify-content: flex-end; }

.msg-avatar { flex-shrink: 0; width: 36px; height: 36px; border-radius: 50%; background: #f5f5f5; overflow: hidden; }
.msg-avatar :deep(img) { border-radius: 50%; }

.msg-content { display: flex; flex-direction: column; max-width: 70%; min-width: 0; }
.msg-content--self { align-items: flex-end; }

.msg-bubble {
  display: inline-block; padding: 10px 14px; background: #fff;
  border-radius: 12px 12px 12px 4px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 6%); overflow-wrap: anywhere; word-break: break-word;
}
.msg-bubble--self { background: #07c160; color: #fff; border-radius: 12px 12px 4px 12px; }

.msg-text { font-size: 15px; line-height: 1.4; }
.msg-meta {
  display: flex;
  gap: 6px;
  margin-top: 4px;
  color: #999;
  font-size: 11px;
}
.msg-meta .read { color: #1989fa; }

.input-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px));
  background: #fff; border-top: 1px solid #eee;
}
.input-field { flex: 1; background: #f5f5f5; border-radius: 20px; padding: 0 12px; }
.send-btn { flex-shrink: 0; border-radius: 18px; padding: 0 16px; }
</style>
