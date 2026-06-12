<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
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

const router = useRouter();
const { showNotify } = useNotify();

const currentUser = ref<User | null>(null);
const loading = ref(false);
const loadFailed = ref(false);
const joinCode = ref('');
const showJoin = ref(false);
const checking = ref(true);
const ranking = ref<User[]>([]);
const history = ref<CardRoomHistory[]>([]);

const loadData = async () => {
  loadFailed.value = false;
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
  } catch {
    loadFailed.value = true;
  } finally {
    checking.value = false;
  }
};

onMounted(loadData);

const handleCreate = async () => {
  if (!currentUser.value) {
    router.push('/login');
    return;
  }
  loading.value = true;
  try {
    const room = await createRoom();
    router.replace(`/card-room/${room.roomId}`);
  } catch (e: any) {
    showNotify(e?.response?.data?.description || e?.message || '创建失败');
  } finally {
    loading.value = false;
  }
};

const handleJoin = async () => {
  if (!currentUser.value) {
    showJoin.value = false;
    router.push('/login');
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
  } catch (e: any) {
    showNotify(e?.response?.data?.description || e?.message || '加入失败');
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
          <span>总积分 <b>{{ currentUser.totalScore ?? 0 }}</b></span>
        </div>
        <div class="user-stat-detail">
          <span>胜 {{ currentUser.wins ?? 0 }}</span>
          <span>负 {{ currentUser.losses ?? 0 }}</span>
          <span>{{ currentUser.winRate ? (currentUser.winRate * 100).toFixed(1) : '0.0' }}%</span>
        </div>
      </div>
      <div v-if="!currentUser && !loadFailed" class="login-hint">
        <span>登录后可查看积分</span>
        <van-button size="small" round type="primary" @click="router.push('/login')">去登录</van-button>
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
          <div class="section-title">积分排名</div>
          <div v-if="ranking.length" class="rank-scroll">
            <div v-for="(user, index) in ranking" :key="user.id" class="ranking-item">
              <span class="ranking-index">{{ index + 1 }}</span>
              <van-image round width="34" height="34" fit="cover" :src="user.avatarUrl || undefined">
                <template #error><van-icon name="contact" size="18" color="#c8c9cc" /></template>
              </van-image>
              <span class="ranking-name">{{ displayName(user) }}</span>
              <b>{{ user.totalScore ?? 0 }}</b>
            </div>
          </div>
          <van-empty v-else-if="!loadFailed" image-size="52" description="暂无积分排名" />
        </section>
        <section class="overview-section">
          <div class="section-title">最近记录</div>
          <div v-if="history.length" class="history-scroll">
            <button v-for="item in history" :key="item.roomId" type="button" class="history-item"
              @click="router.push(`/card-room/${item.roomId}`)">
              <span>
                <strong>房间号：{{ item.roomCode }}</strong>
                <small>{{ item.memberCount }} 人 · {{ formatDate(item.createTime) }}</small>
              </span>
              <b :class="item.score > 0 ? 'positive' : item.score < 0 ? 'negative' : ''">
                {{ item.score > 0 ? '+' : '' }}{{ item.score }}
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