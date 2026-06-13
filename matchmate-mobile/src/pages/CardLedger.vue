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
import { getRequestErrorMessage, isUnauthorizedError } from '../utils/http';

const router = useRouter();
const route = useRoute();
const { showNotify } = useNotify();

const currentUser = ref<User | null>(null);
const loading = ref(false);
const loadFailed = ref(false);
const loginRequired = ref(false);
const joinCode = ref('');
const showJoin = ref(false);
const checking = ref(true);
const ranking = ref<User[]>([]);
const history = ref<CardRoomHistory[]>([]);

const loadData = async () => {
  loadFailed.value = false;
  loginRequired.value = false;
  checking.value = true;
  try {
    const user = await getCurrentUser();
    currentUser.value = user;
    const active = await getActiveRoom();
    if (active) {
      router.replace(`/card-room/${active.roomId}`);
      return;
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
    router.replace(`/card-room/${room.roomId}`);
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
  if (!code || code.length !== 6 || !/^\d{6}$/.test(code)) {
    showNotify('请输入6位数字房间号');
    return;
  }
  loading.value = true;
  try {
    const room = await joinRoom(code);
    showJoin.value = false;
    joinCode.value = '';
    router.replace(`/card-room/${room.roomId}`);
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
const formatDate = (value: string) =>
  new Date(value).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
</script>

<template>
  <div class="card-home">
    <van-loading v-if="checking" class="page-loading" vertical>加载中...</van-loading>
    <template v-else>
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
              @click="router.push(`/card-room/${item.roomId}`)">
              <span>
                <strong>房间号：{{ item.roomCode }}</strong>
                <small>{{ item.memberCount }} 人 · {{ formatDate(item.createTime) }}</small>
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
          else { showJoin = false; joinCode = ''; }
          return false;
        }">
        <van-field v-model="joinCode" type="digit" maxlength="6" placeholder="请输入6位数字房间号"
          autofocus :border="false"
          style="text-align:center;font-size:22px;letter-spacing:4px;" />
      </van-dialog>
    </template>
  </div>
</template>

<style scoped>
.card-home {
  position: fixed;
  top: var(--van-nav-bar-height, 46px);
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  padding: 12px 16px 0;
  background: #f7f8fa;
  box-sizing: border-box;
  overflow: hidden;
}
.page-loading { padding-top: 120px; }
.error-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; margin-bottom: 10px; flex-shrink: 0;
  background: #fff3f0; border-radius: 10px;
  color: #ee0a24; font-size: 13px;
}
.error-bar .van-icon { flex-shrink: 0; }
.error-bar span { flex: 1; }
.user-stat-card {
  display: flex; align-items: center; gap: 12px;
  padding: 16px; flex-shrink: 0;
  background: linear-gradient(135deg, #1989fa, #07c160);
  border-radius: 14px; color: #fff;
}
.user-stat-info { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.user-stat-info strong { font-size: 16px; }
.user-stat-info span { font-size: 13px; opacity: .9; }
.user-stat-info b { font-size: 18px; }
.user-stat-detail { display: flex; flex-direction: column; gap: 2px; font-size: 12px; opacity: .85; }
.login-hint {
  display: flex; align-items: center; justify-content: space-between;
  padding: 20px 16px; flex-shrink: 0; background: #fff;
  border-radius: 14px; color: #969799; font-size: 14px;
}
.action-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 12px;
  margin: 12px 0; flex-shrink: 0;
}
.action-btn {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  padding: 24px 16px; border: 0; border-radius: 14px;
  font-size: 15px; cursor: pointer; transition: opacity .2s;
}
.action-btn:active { opacity: .8; }
.action-btn:disabled { opacity: .5; }
.create-btn { background: #1989fa; color: #fff; }
.join-btn { background: #fff; color: #323233; box-shadow: 0 2px 8px rgb(0 0 0 / 6%); }
.bottom-sections {
  flex: 1; display: flex; flex-direction: column; gap: 8px;
  min-height: 0; padding-bottom: 16px;
}
.overview-section {
  flex: 1; min-height: 0; padding: 10px 14px;
  background: #fff; border-radius: 14px;
  display: flex; flex-direction: column;
}
.section-title { margin-bottom: 6px; flex-shrink: 0; color: #323233; font-size: 15px; font-weight: 600; }
.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-title-row .section-title { margin-bottom: 6px; }
.retention-hint { color: #969799; font-size: 11px; }
.rank-scroll {
  flex: 1; min-height: 0; overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}
.history-scroll {
  flex: 1; min-height: 0; overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}
.ranking-item {
  display: flex; align-items: center; gap: 10px;
  min-height: 46px; border-bottom: 1px solid #f5f5f5;
}
.ranking-item:last-child { border-bottom: 0; }
.ranking-index { width: 20px; color: #969799; text-align: center; font-size: 13px; }
.ranking-name { flex: 1; min-width: 0; overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.ranking-item b { color: #1989fa; font-size: 15px; }
.history-item {
  display: flex; align-items: center; justify-content: space-between;
  width: 100%; min-height: 46px; padding: 6px 0;
  color: #323233; text-align: left; background: transparent;
  border: 0; border-bottom: 1px solid #f5f5f5;
}
.history-item:last-child { border-bottom: 0; }
.history-item span { display: flex; flex-direction: column; gap: 3px; }
.history-item strong { font-size: 15px; }
.history-item small { color: #969799; font-size: 12px; }
.history-item b { font-size: 16px; }
.positive { color: #07c160; }
.negative { color: #ee0a24; }
.hint-sub { color: #969799; font-size: 13px; }
</style>
