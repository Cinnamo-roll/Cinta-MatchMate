<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { showConfirmDialog } from 'vant';
import { useRouter } from 'vue-router';
import { getCurrentUser, searchAdminUsers, updateUserStatus } from '../api/matchmate';
import { useNotify } from '../composables/useNotify';
import type { User } from '../models/user';
import { getRequestErrorMessage } from '../utils/http';
import {
  USER_STATUS,
  getUserStatusText,
  isAdmin,
  isBannedUserStatus,
  isNormalUserStatus,
  isPendingUserStatus,
} from '../utils/user';

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

const getStatusClass = (status: number) => ({
  banned: isBannedUserStatus(status),
  pending: isPendingUserStatus(status),
});

const statusActionText = (status: number) => {
  if (isPendingUserStatus(status)) return '去审核';
  return isNormalUserStatus(status) ? '封停' : '解封';
};

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
      showNotify(getRequestErrorMessage(error, '用户列表加载失败'));
    }
  } finally {
    if (currentRequestId === requestId) {
      loading.value = false;
      loadingMore.value = false;
    }
  }
};

const toggleUserStatus = async (user: User) => {
  if (isPendingUserStatus(user.userStatus)) {
    router.push('/admin/registrations');
    return;
  }
  const nextStatus = isNormalUserStatus(user.userStatus)
    ? USER_STATUS.BANNED
    : USER_STATUS.NORMAL;
  const action = nextStatus === USER_STATUS.BANNED ? '封停' : '解封';
  const targetName = user.username || user.userAccount;

  try {
    await showConfirmDialog({
      title: `${action}账号`,
      message: nextStatus === USER_STATUS.BANNED
        ? `封停后「${targetName}」将不能继续登录和聊天。`
        : `解封后「${targetName}」可以重新登录使用。`,
      theme: 'round-button',
      cancelButtonText: '再想想',
      confirmButtonText: `确认${action}`,
      confirmButtonColor: nextStatus === USER_STATUS.BANNED ? '#ef5d72' : '#5968e9',
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
    showNotify(getRequestErrorMessage(error, `${action}失败`));
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
            <span v-else :class="['status-badge', getStatusClass(user.userStatus)]">
              {{ getUserStatusText(user.userStatus) }}
            </span>
          </div>
          <span class="account">@{{ user.userAccount }}</span>
        </div>

        <van-button
          v-if="!isAdmin(user.userRole)"
          size="small"
          :type="isNormalUserStatus(user.userStatus) ? 'danger' : 'primary'"
          :plain="isNormalUserStatus(user.userStatus)"
          :loading="updatingUserId === user.id"
          @click="toggleUserStatus(user)"
        >
          {{ statusActionText(user.userStatus) }}
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

.status-badge.pending {
  color: #d98a00;
  background: #fff7df;
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
