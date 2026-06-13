<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { User } from '../models/user';
import { getGenderText, isAdmin } from '../utils/user';
import { getCurrentUser } from '../api/matchmate';

defineProps<{
  user: User;
  highlightedTags?: string[];
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
  gap: 14px;
  width: 100%;
  min-width: 0;
  padding: 16px;
  overflow: hidden;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 3px 12px rgb(0 0 0 / 4%);
  box-sizing: border-box;
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
  background: #07c160;
  border: 2px solid #fff;
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
  color: #323233;
  font-size: 17px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gender-badge {
  flex-shrink: 0;
  padding: 2px 7px;
  color: #1989fa;
  font-size: 11px;
  background: #ecf9ff;
  border-radius: 8px;
}

.user-account {
  display: block;
  margin: 3px 0 9px;
  overflow: hidden;
  color: #969799;
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
  background: #07c160;
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
  color: #969799;
  font-size: 13px;
}

.chat-button {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  padding: 0;
  color: #1989fa;
  background: #ecf9ff;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.chat-button:active {
  background: #d8f3ff;
  transform: scale(0.96);
}
</style>
