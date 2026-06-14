<script setup lang="ts">
import axios from 'axios';
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { showConfirmDialog } from 'vant';
import { useRouter } from 'vue-router';
import { getCurrentUser, searchAdminUsers, updateUserStatus } from '../api/matchmate';
import { useNotify } from '../composables/useNotify';
import type { User } from '../models/user';
import { isAdmin } from '../utils/user';

const PAGE_SIZE = 20;

const router = useRouter();
const { showNotify } = useNotify();
const currentUser = ref<User | null>(null);
const users = ref<User[]>([]);
const keyword = ref('');
const loading = ref(false);
const loadingMore = ref(false);
const finished = ref(false);
const updatingUserId = ref<number | null>(null);
let pageNum = 1;
let searchTimer: ReturnType<typeof setTimeout> | undefined;
let requestId = 0;

const errorMessage = (error: unknown, fallback: string) =>
  axios.isAxiosError(error)
    ? error.response?.data?.description || fallback
    : fallback;

const loadUsers = async (reset = false) => {
  if (loading.value || loadingMore.value || (!reset && finished.value)) return;
  const currentRequestId = ++requestId;

  if (reset) {
    pageNum = 1;
    finished.value = false;
    loading.value = true;
  } else {
    loadingMore.value = true;
  }

  try {
    const page = await searchAdminUsers(keyword.value, pageNum, PAGE_SIZE);
    if (currentRequestId !== requestId) return;
    users.value = reset ? page.records : [...users.value, ...page.records];
    finished.value = users.value.length >= page.total || page.records.length < PAGE_SIZE;
    pageNum += 1;
  } catch (error) {
    if (currentRequestId === requestId) {
      showNotify(errorMessage(error, '用户列表加载失败'));
    }
  } finally {
    if (currentRequestId === requestId) {
      loading.value = false;
      loadingMore.value = false;
    }
  }
};

const toggleUserStatus = async (user: User) => {
  const nextStatus = user.userStatus === 0 ? 1 : 0;
  const action = nextStatus === 1 ? '封停' : '解封';

  try {
    await showConfirmDialog({
      title: `${action}账号`,
      message: `确定要${action}“${user.username || user.userAccount}”吗？`,
      confirmButtonColor: nextStatus === 1 ? '#ee0a24' : '#1989fa',
    });
  } catch {
    return;
  }

  try {
    updatingUserId.value = user.id;
    await updateUserStatus(user.id, nextStatus);
    user.userStatus = nextStatus;
    showNotify(`账号已${action}`, 'success');
  } catch (error) {
    showNotify(errorMessage(error, `${action}失败`));
  } finally {
    updatingUserId.value = null;
  }
};

watch(keyword, () => {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => void loadUsers(true), 350);
});

onMounted(async () => {
  try {
    currentUser.value = await getCurrentUser();
    if (!isAdmin(currentUser.value.userRole)) {
      showNotify('仅管理员可访问用户管理');
      router.replace('/user');
      return;
    }
    await loadUsers(true);
  } catch {
    showNotify('请先登录管理员账号');
    router.replace('/login');
  }
});

onBeforeUnmount(() => {
  requestId += 1;
  if (searchTimer) clearTimeout(searchTimer);
});
</script>

<template>
  <div class="admin-users-page">
    <div class="admin-search">
      <van-search
        v-model="keyword"
        shape="round"
        placeholder="搜索昵称或账号"
        clearable
        @search="loadUsers(true)"
      />
    </div>

    <van-loading v-if="loading" class="page-loading" vertical>
      加载用户中...
    </van-loading>

    <div v-else-if="users.length > 0" class="user-list">
      <article v-for="user in users" :key="user.id" class="admin-user-card">
        <div class="avatar-wrap">
          <van-image
            round
            width="48"
            height="48"
            fit="cover"
            :src="user.avatarUrl || undefined"
          >
            <template #error>
              <van-icon name="contact-o" size="26" />
            </template>
          </van-image>
          <span v-if="user.isOnline" class="online-dot" />
        </div>

        <div class="user-info">
          <div class="user-heading">
            <strong>{{ user.username || user.userAccount }}</strong>
            <span v-if="isAdmin(user.userRole)" class="role-badge">管理员</span>
            <span v-else :class="['status-badge', { banned: user.userStatus !== 0 }]">
              {{ user.userStatus === 0 ? '正常' : '已封停' }}
            </span>
          </div>
          <span class="account">@{{ user.userAccount }}</span>
        </div>

        <van-button
          v-if="!isAdmin(user.userRole)"
          size="small"
          :type="user.userStatus === 0 ? 'danger' : 'primary'"
          :plain="user.userStatus === 0"
          :loading="updatingUserId === user.id"
          @click="toggleUserStatus(user)"
        >
          {{ user.userStatus === 0 ? '封停' : '解封' }}
        </van-button>
      </article>

      <van-button
        v-if="!finished"
        class="load-more"
        block
        plain
        type="primary"
        :loading="loadingMore"
        @click="loadUsers()"
      >
        加载更多
      </van-button>
      <p v-else class="list-end">共 {{ users.length }} 位用户</p>
    </div>

    <van-empty v-else description="没有找到用户" />
  </div>
</template>

<style scoped>
.admin-users-page {
  min-height: calc(100dvh - var(--app-nav-height));
  padding: 0 var(--app-page-padding) 24px;
  background: var(--app-bg);
  box-sizing: border-box;
}

.admin-search {
  position: sticky;
  top: 0;
  z-index: 99;
  margin: 0 calc(var(--app-page-padding) * -1);
  padding: 10px var(--app-page-padding);
  background: rgb(244 246 251 / 94%);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.admin-search :deep(.van-search) {
  padding: 0;
  background: transparent;
}

.page-loading {
  padding-top: 80px;
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.admin-user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 15px;
  background: var(--app-surface);
  border: 1px solid rgb(255 255 255 / 76%);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-shadow-sm);
}

.avatar-wrap {
  position: relative;
  flex-shrink: 0;
  width: 48px;
  height: 48px;
}

.online-dot {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 11px;
  height: 11px;
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
  gap: 6px;
  min-width: 0;
}

.user-heading strong {
  overflow: hidden;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  color: var(--app-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-badge,
.status-badge {
  flex-shrink: 0;
  padding: 2px 6px;
  color: var(--app-success);
  font-size: 10px;
  background: var(--app-accent-soft);
  border-radius: 7px;
}

.role-badge {
  color: var(--app-primary);
  background: var(--app-primary-soft);
}

.status-badge.banned {
  color: var(--app-danger);
  background: #fff0f0;
}

.load-more {
  margin-top: 4px;
}

.list-end {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
  text-align: center;
}
</style>
