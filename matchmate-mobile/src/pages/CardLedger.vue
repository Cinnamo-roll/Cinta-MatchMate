<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useNotify } from '../composables/useNotify';
import {
  createRoom,
  getActiveRoom,
  getCardHistory,
  getCardRanking,
  joinRoom,
} from '../api/card';
import { getCurrentUser } from '../api/matchmate';
import type { CardRoomHistory } from '../models/card';
import type { User } from '../models/user';
import { CARD_ROOM_STATUS, cardLedgerRoute } from '../utils/cardRoom';
import { getRequestErrorMessage, isUnauthorizedError } from '../utils/http';
import { formatMonthDay } from '../utils/time';

const router = useRouter();
const route = useRoute();
const { showNotify } = useNotify();

const currentUser = ref<User | null>(null);
const loading = ref(false);
const loadFailed = ref(false);
const loginRequired = ref(false);
const joinCode = ref('');
const joinPassword = ref('');
const showJoin = ref(false);
const checking = ref(true);
const ranking = ref<User[]>([]);
const history = ref<CardRoomHistory[]>([]);
const shouldSkipActiveRoom = () => route.query.skipActiveRoom === '1';
const cardRoomPath = (roomId: number) => `/card-room/${roomId}`;

const loadData = async () => {
  loadFailed.value = false;
  loginRequired.value = false;
  checking.value = true;
  try {
    const user = await getCurrentUser(true);
    currentUser.value = user;
    if (!shouldSkipActiveRoom()) {
      const active = await getActiveRoom();
      if (active) {
        router.replace(cardRoomPath(active.roomId));
        return;
      }
    }
    const [r, h] = await Promise.all([
      getCardRanking(),
      getCardHistory(),
    ]);
    ranking.value = r;
    history.value = h;
  } catch (error) {
    if (isUnauthorizedError(error)) {
      currentUser.value = null;
      ranking.value = [];
      history.value = [];
      loginRequired.value = true;
      showNotify('请先登录后使用打牌记账本');
    } else {
      loadFailed.value = true;
    }
  } finally {
    checking.value = false;
  }
};

onMounted(loadData);

const goToLogin = () => {
  router.push({
    path: '/login',
    query: { redirect: route.fullPath },
  });
};

const requireLogin = () => {
  if (currentUser.value) return true;
  showNotify('请先登录后使用打牌记账本');
  goToLogin();
  return false;
};

const handleCreate = async () => {
  if (!requireLogin()) return;
  loading.value = true;
  try {
    const room = await createRoom();
    router.replace(cardRoomPath(room.roomId));
  } catch (error) {
    if (isUnauthorizedError(error)) {
      currentUser.value = null;
      loginRequired.value = true;
      showNotify('登录状态已失效，请重新登录');
      goToLogin();
      return;
    }
    showNotify(getRequestErrorMessage(error, '创建房间失败'));
  } finally {
    loading.value = false;
  }
};

const handleJoin = async () => {
  if (!requireLogin()) {
    showJoin.value = false;
    return;
  }
  const code = joinCode.value.trim();
  const password = joinPassword.value.trim();
  if (!code || code.length !== 6 || !/^\d{6}$/.test(code)) {
    showNotify('请输入6位数字房间号');
    return;
  }
  if (!password || password.length !== 4 || !/^\d{4}$/.test(password)) {
    showNotify('请输入4位数字房间密码');
    return;
  }
  loading.value = true;
  try {
    const room = await joinRoom(code, password);
    showJoin.value = false;
    joinCode.value = '';
    joinPassword.value = '';
    if (room.status === CARD_ROOM_STATUS.ENDED) {
      router.replace(cardLedgerRoute());
      return;
    }
    router.replace(cardRoomPath(room.roomId));
  } catch (error) {
    if (isUnauthorizedError(error)) {
      currentUser.value = null;
      loginRequired.value = true;
      showJoin.value = false;
      showNotify('登录状态已失效，请重新登录');
      goToLogin();
      return;
    }
    showNotify(getRequestErrorMessage(error, '加入房间失败'));
  } finally {
    loading.value = false;
  }
};

const displayName = (user: User) => user.username || user.userAccount;
</script>

<template>
  <div class="card-home">
    <van-loading v-if="checking" class="page-loading" vertical>加载中...</van-loading>
    <template v-else>
      <div class="card-fixed">
        <div v-if="loadFailed" class="error-bar">
          <van-icon name="warning-o" size="16" />
          <span>网络异常，请重试</span>
          <van-button size="small" round type="primary" @click="loadData">重试</van-button>
        </div>
        <div class="user-stat-card" v-if="currentUser">
          <van-image round width="48" height="48" fit="cover" :src="currentUser.avatarUrl || undefined">
            <template #error><van-icon name="contact" size="24" color="#c8c9cc" /></template>
          </van-image>
          <div class="user-stat-info">
            <strong>{{ currentUser.username || currentUser.userAccount }}</strong>
            <span>赢得金额 <b>{{ currentUser.totalScore ?? 0 }}</b> 元</span>
          </div>
          <div class="user-stat-detail">
            <span>胜 {{ currentUser.wins ?? 0 }}</span>
            <span>负 {{ currentUser.losses ?? 0 }}</span>
            <span>{{ currentUser.winRate ? (currentUser.winRate * 100).toFixed(1) : '0.0' }}%</span>
          </div>
        </div>
        <div v-if="loginRequired || (!currentUser && !loadFailed)" class="login-hint">
          <span>登录后可查看自己的牌局账本</span>
          <van-button size="small" round type="primary" @click="goToLogin">去登录</van-button>
        </div>
        <div class="action-grid" v-if="currentUser">
          <button class="action-btn create-btn" @click="handleCreate" :disabled="loading">
            <van-icon name="add-o" size="24" />
            <span>我要开房</span>
          </button>
          <button class="action-btn join-btn" @click="showJoin = true" :disabled="loading">
            <van-icon name="friends-o" size="24" />
            <span>加入房间</span>
          </button>
        </div>
      </div>
      <div class="bottom-sections" v-if="currentUser">
        <section class="overview-section">
          <div class="section-title">排名</div>
          <div v-if="ranking.length" class="rank-scroll">
            <div v-for="(user, index) in ranking" :key="user.id" class="ranking-item">
              <span class="ranking-index">{{ index + 1 }}</span>
              <van-image round width="34" height="34" fit="cover" :src="user.avatarUrl || undefined">
                <template #error><van-icon name="contact" size="18" color="#c8c9cc" /></template>
              </van-image>
              <span class="ranking-name">{{ displayName(user) }}</span>
              <b>{{ user.totalScore ?? 0 }} 元</b>
            </div>
          </div>
          <van-empty v-else-if="!loadFailed" image-size="52" description="暂无排名" />
        </section>
        <section class="overview-section">
          <div class="section-title-row">
            <div class="section-title">最近记录</div>
            <span class="retention-hint">仅保留最近 6 条</span>
          </div>
          <div v-if="history.length" class="history-scroll">
            <button v-for="item in history" :key="item.roomId" type="button" class="history-item"
              @click="router.push(cardRoomPath(item.roomId))">
              <span>
                <strong>房间号：{{ item.roomCode }}</strong>
                <small>{{ item.memberCount }} 人 · {{ formatMonthDay(item.createTime) }}</small>
              </span>
              <b :class="item.score > 0 ? 'positive' : item.score < 0 ? 'negative' : ''">
                {{ item.score > 0 ? '+' : '' }}{{ item.score }} 元
              </b>
            </button>
          </div>
          <van-empty v-else-if="!loadFailed" image-size="70" description="暂无历史记录">
            <template #bottom>
              <span class="hint-sub">开房后自动保存牌局记录</span>
            </template>
          </van-empty>
        </section>
      </div>
      <van-dialog v-model:show="showJoin" title="输入房间号" show-cancel-button
        :confirm-button-loading="loading"
        :before-close="(action: string) => {
          if (action === 'confirm') handleJoin();
          else { showJoin = false; joinCode = ''; joinPassword = ''; }
          return false;
        }">
        <van-field v-model="joinCode" type="digit" maxlength="6" placeholder="请输入6位数字房间号"
          autofocus :border="false"
          style="text-align:center;font-size:22px;letter-spacing:4px;" />
        <van-field v-model="joinPassword" type="digit" maxlength="4" placeholder="请输入4位数字密码"
          :border="false"
          style="text-align:center;font-size:20px;letter-spacing:4px;" />
      </van-dialog>
    </template>
  </div>
</template>

<style scoped>
.card-home {
  display: flex;
  flex-direction: column;
  height: calc(100dvh - var(--app-nav-height));
  min-height: 0;
  padding: 14px var(--app-page-padding) calc(28px + var(--app-safe-bottom));
  overflow: hidden;
  background: var(--app-bg);
  box-sizing: border-box;
}

.card-fixed {
  flex: 0 0 auto;
}

.page-loading {
  padding-top: 120px;
}

.error-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
  color: var(--app-danger);
  font-size: 13px;
  background: #fff0f2;
  border: 1px solid #ffdce2;
  border-radius: 14px;
}

.error-bar .van-icon {
  flex-shrink: 0;
}

.error-bar span {
  flex: 1;
}

.user-stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px;
  color: #fff;
  background:
    radial-gradient(circle at 88% 12%, rgb(255 255 255 / 18%) 0 44px, transparent 45px),
    linear-gradient(135deg, #5968e9 0%, #6b63df 58%, #28aa9b 120%);
  border-radius: 24px;
  box-shadow: 0 16px 34px rgb(89 104 233 / 20%);
}

.user-stat-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.user-stat-info strong {
  overflow: hidden;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-stat-info span {
  font-size: 13px;
  opacity: .88;
}

.user-stat-info b {
  font-size: 19px;
}

.user-stat-detail {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex-shrink: 0;
  font-size: 12px;
  text-align: right;
  opacity: .84;
}

.login-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 16px;
  color: var(--app-text-secondary);
  font-size: 14px;
  background: var(--app-surface);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-shadow-sm);
}

.action-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin: 14px 0;
}

.action-btn {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 12px;
  min-height: 108px;
  padding: 18px;
  font-size: 15px;
  font-weight: 700;
  border: 0;
  border-radius: var(--app-card-radius);
  cursor: pointer;
  transition:
    opacity var(--app-duration-fast) ease,
    transform var(--app-duration-fast) var(--app-ease);
}

.action-btn:active {
  transform: scale(.98);
}

.action-btn:disabled {
  opacity: .5;
}

.create-btn {
  color: #fff;
  background: var(--app-primary);
  box-shadow: 0 12px 26px rgb(89 104 233 / 20%);
}

.join-btn {
  color: var(--app-text);
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  box-shadow: var(--app-shadow-sm);
}

.bottom-sections {
  display: grid;
  flex: 1 1 auto;
  grid-template-rows: minmax(0, .82fr) minmax(0, 1fr);
  gap: 12px;
  min-height: 0;
}

.overview-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding: 16px;
  background: var(--app-surface);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-shadow-sm);
}

.section-title {
  margin-bottom: 8px;
  color: var(--app-text);
  font-size: 16px;
  font-weight: 700;
}

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.retention-hint,
.ranking-index,
.history-item small,
.hint-sub {
  color: var(--app-text-muted);
}

.retention-hint {
  font-size: 11px;
}

.rank-scroll,
.history-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior-y: contain;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.rank-scroll::-webkit-scrollbar,
.history-scroll::-webkit-scrollbar {
  display: none;
}

.ranking-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  border-bottom: 1px solid var(--app-border);
}

.ranking-item:last-child,
.history-item:last-child {
  border-bottom: 0;
}

.ranking-index {
  width: 20px;
  font-size: 13px;
  text-align: center;
}

.ranking-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ranking-item b {
  color: var(--app-primary);
  font-size: 15px;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 54px;
  padding: 7px 0;
  color: var(--app-text);
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--app-border);
}

.history-item span {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.history-item strong {
  font-size: 14px;
}

.history-item small {
  font-size: 12px;
}

.history-item b {
  flex-shrink: 0;
  font-size: 16px;
}

.positive {
  color: var(--app-success);
}

.negative {
  color: var(--app-danger);
}

.hint-sub {
  font-size: 13px;
}
</style>
