<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { showConfirmDialog } from 'vant';
import { useRouter } from 'vue-router';
import {
  approveRegistration,
  getCurrentUser,
  getRegistrationPolicy,
  rejectRegistration,
  searchPendingRegistrations,
  updateRegistrationPolicy,
} from '../api/matchmate';
import { useNotify } from '../composables/useNotify';
import type { RegistrationPolicy } from '../models/api';
import type { User } from '../models/user';
import { getRequestErrorMessage } from '../utils/http';
import { formatMonthDayTime } from '../utils/time';
import { isAdmin } from '../utils/user';

const PAGE_SIZE = 20;

const router = useRouter();
const { showNotify } = useNotify();

const policy = ref<RegistrationPolicy | null>(null);
const dailyLimitInput = ref('');
const pendingUsers = ref<User[]>([]);
const loading = ref(false);
const loadingMore = ref(false);
const finished = ref(false);
const savingLimit = ref(false);
const reviewingUserId = ref<number | null>(null);
let pageNum = 1;

const syncPolicy = async () => {
  policy.value = await getRegistrationPolicy();
  dailyLimitInput.value = String(policy.value.dailyLimit);
};

const loadPendingUsers = async (reset = false) => {
  if (loading.value || loadingMore.value || (!reset && finished.value)) return;
  if (reset) {
    pageNum = 1;
    finished.value = false;
    loading.value = true;
  } else {
    loadingMore.value = true;
  }

  try {
    const page = await searchPendingRegistrations(pageNum, PAGE_SIZE);
    pendingUsers.value = reset ? page.records : [...pendingUsers.value, ...page.records];
    finished.value =
      pendingUsers.value.length >= page.total || page.records.length < PAGE_SIZE;
    pageNum += 1;
  } catch (error) {
    showNotify(getRequestErrorMessage(error, '待审核列表加载失败'));
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
};

const saveLimit = async () => {
  const dailyLimit = Number(dailyLimitInput.value);
  if (!Number.isInteger(dailyLimit) || dailyLimit < 0 || dailyLimit > 1000) {
    showNotify('每日限额请输入 0 到 1000 的整数');
    return;
  }

  try {
    savingLimit.value = true;
    policy.value = await updateRegistrationPolicy({ dailyLimit });
    dailyLimitInput.value = String(policy.value.dailyLimit);
    showNotify('每日注册限额已更新', 'success');
  } catch (error) {
    showNotify(getRequestErrorMessage(error, '限额保存失败'));
  } finally {
    savingLimit.value = false;
  }
};

const removePendingUser = (userId: number) => {
  pendingUsers.value = pendingUsers.value.filter((user) => user.id !== userId);
  if (policy.value) {
    policy.value.pendingCount = Math.max(0, policy.value.pendingCount - 1);
  }
};

const approveUser = async (user: User) => {
  try {
    await showConfirmDialog({
      title: '同意注册申请',
      message: `同意后「${user.username || user.userAccount}」就可以登录 MatchMate 了。`,
      theme: 'round-button',
      cancelButtonText: '再想想',
      confirmButtonText: '同意通过',
      confirmButtonColor: '#5968e9',
    });
  } catch {
    return;
  }

  try {
    reviewingUserId.value = user.id;
    await approveRegistration(user.id);
    removePendingUser(user.id);
    await syncPolicy();
    showNotify('已同意注册申请', 'success');
  } catch (error) {
    showNotify(getRequestErrorMessage(error, '同意失败'));
  } finally {
    reviewingUserId.value = null;
  }
};

const rejectUser = async (user: User) => {
  try {
    await showConfirmDialog({
      title: '拒绝注册',
      message: `拒绝后会移除「${user.username || user.userAccount}」这次注册申请，账号将无法登录。`,
      theme: 'round-button',
      cancelButtonText: '再想想',
      confirmButtonText: '确认拒绝',
      confirmButtonColor: '#ef5d72',
    });
  } catch {
    return;
  }

  try {
    reviewingUserId.value = user.id;
    await rejectRegistration(user.id);
    removePendingUser(user.id);
    await syncPolicy();
    showNotify('已拒绝注册申请', 'success');
  } catch (error) {
    showNotify(getRequestErrorMessage(error, '拒绝失败'));
  } finally {
    reviewingUserId.value = null;
  }
};

onMounted(async () => {
  try {
    const currentUser = await getCurrentUser();
    if (!isAdmin(currentUser.userRole)) {
      showNotify('仅管理员可访问注册审核');
      router.replace('/user');
      return;
    }
    await Promise.all([syncPolicy(), loadPendingUsers(true)]);
  } catch (error) {
    showNotify(getRequestErrorMessage(error, '请先登录管理员账号'));
    router.replace('/login');
  }
});
</script>

<template>
  <div class="registration-review-page">
    <section class="policy-card">
      <div class="policy-title">
        <div>
          <strong>每日注册限额</strong>
          <p>超过限额的新用户会进入待审核列表</p>
        </div>
        <span v-if="policy">{{ policy.pendingCount }} 待审核</span>
      </div>

      <div class="limit-row">
        <van-field
          v-model="dailyLimitInput"
          type="digit"
          label="每日通过"
          placeholder="例如 20"
          clearable
        />
        <van-button
          round
          type="primary"
          :loading="savingLimit"
          @click="saveLimit"
        >
          保存
        </van-button>
      </div>

      <div v-if="policy" class="policy-stats">
        <span>今日已通过 <b>{{ policy.approvedToday }}</b></span>
        <span>当前限额 <b>{{ policy.dailyLimit }}</b></span>
      </div>
    </section>

    <section class="pending-section">
      <div class="section-heading">
        <strong>待审核注册</strong>
        <span>按申请时间从早到晚处理</span>
      </div>

      <van-loading v-if="loading" class="page-loading" vertical>
        加载待审核申请...
      </van-loading>

      <div v-else-if="pendingUsers.length > 0" class="pending-list">
        <article
          v-for="user in pendingUsers"
          :key="user.id"
          class="pending-card"
        >
          <div class="pending-avatar">
            <van-image
              round
              width="46"
              height="46"
              fit="cover"
              :src="user.avatarUrl || undefined"
            >
              <template #error>
                <van-icon name="contact-o" size="24" />
              </template>
            </van-image>
          </div>

          <div class="pending-info">
            <strong>{{ user.username || user.userAccount }}</strong>
            <span>@{{ user.userAccount }}</span>
            <small>申请时间 {{ formatMonthDayTime(user.createTime) }}</small>
          </div>

          <div class="pending-actions">
            <van-button
              size="small"
              round
              type="primary"
              :loading="reviewingUserId === user.id"
              @click="approveUser(user)"
            >
              同意
            </van-button>
            <van-button
              size="small"
              round
              plain
              type="danger"
              :loading="reviewingUserId === user.id"
              @click="rejectUser(user)"
            >
              拒绝
            </van-button>
          </div>
        </article>

        <van-button
          v-if="!finished"
          class="load-more"
          block
          plain
          type="primary"
          :loading="loadingMore"
          @click="loadPendingUsers()"
        >
          加载更多
        </van-button>
        <p v-else class="list-end">没有更多待审核申请了</p>
      </div>

      <van-empty v-else image-size="72" description="暂无待审核注册申请" />
    </section>
  </div>
</template>

<style scoped>
.registration-review-page {
  min-height: calc(100dvh - var(--app-nav-height));
  padding: 14px var(--app-page-padding) calc(24px + var(--app-safe-bottom));
  background: var(--app-bg);
  box-sizing: border-box;
}

.policy-card,
.pending-section {
  padding: 16px;
  background: var(--app-surface);
  border: 1px solid rgb(255 255 255 / 76%);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-shadow-sm);
}

.pending-section {
  margin-top: 12px;
}

.policy-title,
.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.policy-title strong,
.section-heading strong {
  color: var(--app-text);
  font-size: 16px;
}

.policy-title p,
.section-heading span {
  margin: 5px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
}

.policy-title > span {
  flex-shrink: 0;
  padding: 5px 9px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 700;
  background: var(--app-primary-soft);
  border-radius: var(--app-pill-radius);
}

.limit-row {
  display: grid;
  grid-template-columns: 1fr 82px;
  gap: 10px;
  align-items: center;
  margin-top: 14px;
}

.limit-row :deep(.van-cell) {
  padding: 8px 12px;
  background: var(--app-surface-muted);
  border-radius: 14px;
}

.policy-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.policy-stats span {
  padding: 10px 12px;
  color: var(--app-text-muted);
  font-size: 12px;
  background: var(--app-surface-muted);
  border-radius: 14px;
}

.policy-stats b {
  color: var(--app-text);
  font-size: 18px;
}

.page-loading {
  padding: 42px 0;
}

.pending-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.pending-card {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 12px;
  background: var(--app-surface-muted);
  border-radius: 16px;
}

.pending-avatar {
  flex: 0 0 auto;
}

.pending-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.pending-info strong,
.pending-info span,
.pending-info small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pending-info strong {
  color: var(--app-text);
  font-size: 14px;
}

.pending-info span,
.pending-info small {
  color: var(--app-text-muted);
  font-size: 12px;
}

.pending-actions {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: 7px;
}

.load-more {
  margin-top: 2px;
}

.list-end {
  margin: 2px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
  text-align: center;
}
</style>
