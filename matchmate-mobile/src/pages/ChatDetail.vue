<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, nextTick } from 'vue';
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
const composing = ref(false);
const sending = ref(false);
const loading = ref(false);
const loadingMore = ref(false);
const hasMore = ref(true);
const currentUser = ref<User | null>(null);
const pageNum = ref(1);
const PAGE_SIZE = 20;
let openConvDone = false;

const messageListEl = ref<HTMLElement | null>(null);
const canSend = computed(() => inputText.value.trim().length > 0 && !sending.value);

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

const handleEnter = (event: KeyboardEvent) => {
  if (event.isComposing || composing.value) return;
  event.preventDefault();
  void handleSend();
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

const goBack = () => {
  if (window.history.length > 1) {
    router.back();
    return;
  }
  router.replace('/team');
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
      @click-left="goBack"
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

    <form class="input-bar" @submit.prevent="handleSend">
      <div class="composer-shell" :class="{ 'composer-shell--active': inputText.trim() }">
        <van-field
          v-model="inputText"
          class="input-field"
          type="textarea"
          rows="1"
          :autosize="{ minHeight: 24, maxHeight: 92 }"
          placeholder="输入消息..."
          :disabled="sending"
          :border="false"
          maxlength="1000"
          enterkeyhint="send"
          @compositionstart="composing = true"
          @compositionend="composing = false"
          @focus="scrollToBottom"
          @keydown.enter.exact="handleEnter"
        />
      </div>

      <button
        class="send-btn"
        :class="{ 'send-btn--ready': canSend }"
        type="submit"
        :disabled="!canSend"
        aria-label="发送消息"
      >
        <van-loading v-if="sending" size="16" color="#fff" />
        <van-icon v-else name="guide-o" size="19" />
      </button>
    </form>
  </div>
</template>

<style scoped>
.chat-detail {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 100%;
  overflow-x: hidden;
  background: var(--app-bg);
}
.chat-navbar {
  --van-nav-bar-height: 48px;
  --van-nav-bar-title-font-size: 15px;
}
.chat-navbar-title { display: flex; flex-direction: column; align-items: center; line-height: 1.15; }
.chat-navbar-title strong { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-navbar-title span { margin-top: 2px; color: var(--app-text-muted); font-size: 10px; font-weight: 400; }
.chat-navbar-title span.online { color: var(--app-success); }
.message-list { flex: 1; max-width: 100%; overflow-x: hidden; overflow-y: auto; padding: 14px var(--app-page-padding); overscroll-behavior: contain; -webkit-overflow-scrolling: touch; }
.load-more-hint { text-align: center; padding: 8px; font-size: 12px; color: var(--app-text-muted); }
.retention-notice {
  margin: 0 auto 12px;
  padding: 5px 10px;
  color: var(--app-text-muted);
  font-size: 11px;
  text-align: center;
  background: rgb(255 255 255 / 72%);
  border-radius: var(--app-pill-radius);
}
.page-loading { padding-top: 80px; }

.msg-row { display: flex; align-items: flex-start; gap: 8px; max-width: 100%; min-width: 0; margin-bottom: 16px; }
.msg-row--self { justify-content: flex-end; }

.msg-avatar { flex-shrink: 0; width: 36px; height: 36px; border-radius: 50%; background: var(--app-surface-muted); overflow: hidden; }
.msg-avatar :deep(img) { border-radius: 50%; }

.msg-content { display: flex; flex-direction: column; max-width: 70%; min-width: 0; }
.msg-content--self { align-items: flex-end; }

.msg-bubble {
  display: inline-block; padding: 10px 14px; background: var(--app-surface);
  border-radius: 16px 16px 16px 5px;
  box-shadow: var(--app-shadow-sm); overflow-wrap: anywhere; word-break: break-word;
}
.msg-bubble--self { background: var(--app-primary); color: #fff; border-radius: 16px 16px 5px 16px; }

.msg-text { font-size: 15px; line-height: 1.4; }
.msg-meta {
  display: flex;
  gap: 6px;
  margin-top: 4px;
  color: var(--app-text-muted);
  font-size: 11px;
}
.msg-meta .read { color: var(--app-primary); }

.input-bar {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 10px var(--app-page-padding);
  padding-bottom: calc(10px + var(--app-safe-bottom));
  background:
    linear-gradient(180deg, rgb(255 255 255 / 78%), rgb(255 255 255 / 96%)),
    var(--app-surface);
  border-top: 1px solid rgb(233 235 242 / 84%);
  box-shadow: 0 -10px 28px rgb(37 45 76 / 7%);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.composer-shell {
  min-width: 0;
  flex: 1;
  padding: 3px;
  background: linear-gradient(135deg, #f9fbff, #eef7ff);
  border: 1px solid rgb(89 104 233 / 10%);
  border-radius: 22px;
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 88%),
    0 6px 16px rgb(37 45 76 / 4%);
  transition:
    border-color var(--app-duration-fast) ease,
    box-shadow var(--app-duration-fast) ease,
    background-color var(--app-duration-fast) ease;
}

.composer-shell:focus-within,
.composer-shell--active {
  border-color: rgb(89 104 233 / 24%);
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 90%),
    0 8px 20px rgb(89 104 233 / 10%);
}

.input-field {
  min-width: 0;
  padding: 4px 12px;
  background: transparent;
  border-radius: 18px;
}

.input-field :deep(.van-field__body) {
  align-items: center;
}

.input-field :deep(.van-field__control) {
  max-height: 92px;
  color: var(--app-text);
  font-size: 15px;
  line-height: 1.45;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.send-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  padding: 0;
  color: #fff;
  background: #c8d0ee;
  border: 0;
  border-radius: 50%;
  box-shadow: 0 8px 18px rgb(89 104 233 / 14%);
  transition:
    transform var(--app-duration-fast) var(--app-ease),
    background var(--app-duration-fast) ease,
    box-shadow var(--app-duration-fast) ease,
    opacity var(--app-duration-fast) ease;
}

.send-btn--ready {
  background: linear-gradient(135deg, #6bb8ff, var(--app-primary));
  box-shadow: 0 10px 22px rgb(89 104 233 / 24%);
}

.send-btn:disabled {
  cursor: not-allowed;
  opacity: .72;
}

.send-btn--ready:active {
  transform: scale(.94);
}

@media (max-width: 360px) {
  .input-bar {
    gap: 8px;
    padding-top: 8px;
  }

  .send-btn {
    width: 40px;
    height: 40px;
  }
}
</style>
