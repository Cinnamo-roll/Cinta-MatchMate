<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { User } from '../models/user';
import { getGenderText, isAdmin } from '../utils/user';
import { getCurrentUser } from '../api/matchmate';

defineProps<{
  user: User;
  highlightedTags?: string[];
  recommendationReason?: string;
}>();

const router = useRouter();
const myId = ref<number | null>(null);

const startChat = (user: User) => {
  router.push({
    path: '/chat/0',
    query: {
      targetUserId: user.id,
      username: (user.username || user.userAccount),
      avatar: user.avatarUrl,
      isOnline: String(Boolean(user.isOnline)),
    },
  });
};

onMounted(async () => {
  try {
    const me = await getCurrentUser();
    myId.value = me?.id ?? null;
  } catch { /* 未登录 */ }
});
</script>

<template>
  <article class="user-card">
    <div class="avatar-wrap">
      <van-image
        class="user-avatar"
        round
        width="56"
        height="56"
        fit="cover"
        :src="user.avatarUrl || undefined"
      >
        <template #error>
          <van-icon name="contact-o" size="28" />
        </template>
      </van-image>
      <span v-if="user.isOnline" class="online-dot" />
    </div>

    <div class="user-info">
      <div class="user-heading">
        <strong class="user-name">
          {{ user.username || user.userAccount }}
        </strong>
        <span class="gender-badge">{{ getGenderText(user.gender) }}</span>
      </div>

      <span class="user-account">
        @{{ user.userAccount }}
        <em v-if="isAdmin(user.userRole)" class="admin-badge">管理员</em>
      </span>

      <div class="user-tags">
        <van-tag
          v-for="tag in user.userTags"
          :key="tag"
          :type="highlightedTags?.includes(tag) ? 'primary' : 'default'"
          round
          plain
        >
          {{ tag }}
        </van-tag>
        <span v-if="user.userTags.length === 0" class="no-tags">暂无标签</span>
      </div>

      <p v-if="recommendationReason" class="recommendation-reason">
        <van-icon name="like-o" />
        <span>{{ recommendationReason }}</span>
      </p>
    </div>

    <button
      v-if="user.id !== myId"
      class="chat-button"
      type="button"
      :aria-label="`给${user.username || user.userAccount}留言`"
      @click.stop="startChat(user)"
    >
      <van-icon name="chat-o" size="22" />
    </button>
  </article>
</template>

<style scoped>
.user-card {
  display: flex;
  align-items: center;
  gap: 13px;
  width: 100%;
  min-width: 0;
  padding: 15px;
  overflow: hidden;
  background: var(--app-surface);
  border: 1px solid rgb(255 255 255 / 76%);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-shadow-sm);
  box-sizing: border-box;
  transition:
    transform var(--app-duration-fast) var(--app-ease),
    box-shadow var(--app-duration-fast) ease;
}

.user-card:active {
  transform: scale(.992);
}

.avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.user-avatar {
  flex-shrink: 0;
}

.online-dot {
  position: absolute;
  bottom: 1px;
  right: 1px;
  width: 14px;
  height: 14px;
  background: var(--app-success);
  border: 2px solid var(--app-surface);
  border-radius: 50%;
}

.user-info {
  min-width: 0;
  flex: 1;
}

.user-heading {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.user-name {
  min-width: 0;
  overflow: hidden;
  color: var(--app-text);
  font-size: 17px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gender-badge {
  flex-shrink: 0;
  padding: 2px 7px;
  color: var(--app-primary);
  font-size: 11px;
  background: var(--app-primary-soft);
  border-radius: 8px;
}

.user-account {
  display: block;
  margin: 3px 0 9px;
  overflow: hidden;
  color: var(--app-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-badge {
  display: inline-block;
  padding: 1px 6px;
  margin-left: 6px;
  color: #fff;
  font-size: 10px;
  font-style: normal;
  background: var(--app-accent);
  border-radius: 6px;
  vertical-align: middle;
}

.user-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.user-tags :deep(.van-tag) {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.no-tags {
  color: var(--app-text-muted);
  font-size: 13px;
}

.recommendation-reason {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin: 9px 0 0;
  padding: 7px 9px;
  color: #327eb5;
  font-size: 12px;
  line-height: 1.4;
  background: #edf8ff;
  border-radius: 10px;
}

.recommendation-reason .van-icon {
  flex-shrink: 0;
  margin-top: 1px;
}

.chat-button {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  padding: 0;
  color: var(--app-primary);
  background: var(--app-primary-soft);
  border: 0;
  border-radius: 50%;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.chat-button:active {
  background: #dfe3ff;
  transform: scale(0.96);
}

@media (max-width: 360px) {
  .user-card {
    gap: 10px;
    padding: 13px;
  }

  .user-avatar {
    width: 52px !important;
    height: 52px !important;
  }

  .user-tags {
    gap: 6px;
  }
}
</style>
