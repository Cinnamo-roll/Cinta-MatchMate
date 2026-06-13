<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showConfirmDialog } from 'vant';
import { useNotify } from '../composables/useNotify';
import { useCardWebSocket } from '../composables/useCardWebSocket';
import { getRoomDetail, leaveRoom, addTransfer, addFund, endRoom } from '../api/card';
import { getCurrentUser } from '../api/matchmate';
import { CardWsEvent } from '../models/card';
import type { CardRoomVO, CardWsPayload } from '../models/card';
import type { User } from '../models/user';
import { getRequestErrorMessage, isUnauthorizedError } from '../utils/http';

const route = useRoute();
const router = useRouter();
const { showNotify } = useNotify();
const { connect, disconnect, onMessage } = useCardWebSocket();

const roomId = Number(route.params.id);
const room = ref<CardRoomVO | null>(null);
const currentUser = ref<User | null>(null);
const loading = ref(true);
const loginRequired = ref(false);
const POSITIVE_INTEGER_PATTERN = /^[1-9]\d{0,5}$/;

// 每局记账弹窗
const showTransfer = ref(false);
const transferAmounts = ref<Record<number, string>>({});
const submitting = ref(false);

// 金额平摊弹窗
const showFund = ref(false);
const fundType = ref<1 | 2>(1);
const fundAmount = ref('');
const fundParticipantIds = ref<number[]>([]);

const isOwner = computed(() => room.value?.ownerId === currentUser.value?.id);
const isEnded = computed(() => room.value?.status === 1);
const activeMembers = computed(() => room.value?.members.filter((m) => m.status === 0) ?? []);
/** 除自己外的在房成员 */
const otherMembers = computed(() => activeMembers.value.filter((m) => m.userId !== currentUser.value?.id));

// 每局转账合计
const transferSum = computed(() =>
  Object.values(transferAmounts.value).reduce(
    (sum, value) => sum + (POSITIVE_INTEGER_PATTERN.test(value) ? Number(value) : 0),
    0,
  ),
);

const goToLogin = () => {
  router.replace({
    path: '/login',
    query: { redirect: route.fullPath },
  });
};

const handleRequestError = (error: unknown, fallback: string) => {
  if (isUnauthorizedError(error)) {
    loginRequired.value = true;
    room.value = null;
    showNotify('登录状态已失效，请重新登录');
    goToLogin();
    return;
  }
  showNotify(getRequestErrorMessage(error, fallback));
};

const loadRoom = async () => {
  if (!Number.isSafeInteger(roomId) || roomId <= 0) {
    loading.value = false;
    return;
  }
  try {
    loading.value = true;
    loginRequired.value = false;
    const [roomDetail, user] = await Promise.all([
      getRoomDetail(roomId),
      getCurrentUser(),
    ]);
    room.value = roomDetail;
    currentUser.value = user;
  } catch (error) {
    if (isUnauthorizedError(error)) {
      loginRequired.value = true;
      room.value = null;
      showNotify('请先登录后查看房间');
    } else {
      room.value = null;
    }
  } finally {
    loading.value = false;
  }
};

let unsubWs: (() => void) | null = null;
let refreshTimer: ReturnType<typeof setTimeout> | null = null;
onMounted(async () => {
  await loadRoom();
  if (room.value && !isEnded.value) {
    connect(roomId);
    unsubWs = onMessage((p: CardWsPayload) => {
      if (p.roomId !== roomId) return;
      if (p.type === CardWsEvent.ROOM_CLOSED) disconnect();
      if (refreshTimer) clearTimeout(refreshTimer);
      refreshTimer = setTimeout(loadRoom, 100);
    });
  }
});
onUnmounted(() => {
  if (refreshTimer) clearTimeout(refreshTimer);
  unsubWs?.();
  disconnect();
});

const handleLeave = async () => {
  try { await showConfirmDialog({ title: '退出房间', message: '确定退出当前房间吗？' }); }
  catch { return; }
  try {
    await leaveRoom(roomId);
    router.replace('/discover/card-ledger');
  } catch (error) {
    handleRequestError(error, '退出房间失败');
  }
};

const handleEnd = async () => {
  try { await showConfirmDialog({ title: '结束房间', message: '将结算所有成员积分并更新统计，确定吗？' }); }
  catch { return; }
  try { room.value = await endRoom(roomId); showNotify('房间已结算', 'success'); }
  catch (error) {
    handleRequestError(error, '结算失败');
  }
};

// ── 每局记账 ──
const openTransfer = () => {
  if (!room.value || !currentUser.value) return;
  transferAmounts.value = {};
  otherMembers.value.forEach((m) => (transferAmounts.value[m.userId] = ''));
  showTransfer.value = true;
};

const submitTransfer = async () => {
  if (submitting.value) return;
  const entries = Object.entries(transferAmounts.value)
    .filter(([, value]) => value.trim() !== '');
  if (entries.some(([, value]) => !POSITIVE_INTEGER_PATTERN.test(value))) {
    showNotify('金额只能输入 1 到 999999 的正整数');
    return;
  }
  const transfers = entries
    .map(([uid, v]) => {
      return { toUserId: Number(uid), amount: Number(v) };
    });
  if (!transfers.length) { showNotify('请至少输入一笔转账'); return; }

  submitting.value = true;
  try {
    room.value = await addTransfer(roomId, { transfers });
    showTransfer.value = false;
    showNotify('转账已记录', 'success');
  } catch (error) {
    handleRequestError(error, '记录牌局失败');
  } finally {
    submitting.value = false;
  }
};

const onTransferInput = (userId: number, value: string) => {
  const cleaned = value.replace(/\D/g, '').slice(0, 6);
  transferAmounts.value[userId] = cleaned;
};

// ── 金额平摊 ──
const openFund = () => {
  fundType.value = 1;
  fundAmount.value = '';
  fundParticipantIds.value = activeMembers.value.map((m) => m.userId);
  showFund.value = true;
};

const submitFund = async () => {
  if (submitting.value) return;
  if (!POSITIVE_INTEGER_PATTERN.test(fundAmount.value)) {
    showNotify('金额只能输入 1 到 999999 的正整数');
    return;
  }
  const amount = Number(fundAmount.value);
  if (!fundParticipantIds.value.length) { showNotify('请至少选择一位分摊成员'); return; }

  submitting.value = true;
  try {
    room.value = await addFund(roomId, {
      type: fundType.value,
      amount,
      participantIds: fundParticipantIds.value,
    });
    showFund.value = false;
    showNotify('资金已记录', 'success');
  } catch (error) {
    handleRequestError(error, '记录资金失败');
  } finally {
    submitting.value = false;
  }
};

const toggleFundParticipant = (userId: number) => {
  const idx = fundParticipantIds.value.indexOf(userId);
  if (idx >= 0) {
    fundParticipantIds.value.splice(idx, 1);
  } else {
    fundParticipantIds.value.push(userId);
  }
};

// ── 格式化 ──
const formatMoney = (fen: number) => (fen / 100).toFixed(2);
const formatScore = (pts: number) => {
  if (pts > 0) return `+${pts}`;
  return String(pts);
};
const formatTime = (t: string) =>
  new Date(t).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
</script>

<template>
  <div class="room-page">
    <van-loading v-if="loading" class="page-loading" vertical>加载中...</van-loading>

    <van-empty v-else-if="loginRequired" description="请先登录后查看房间">
      <van-button round type="primary" size="small" @click="goToLogin">
        去登录
      </van-button>
    </van-empty>

    <template v-else-if="room">
      <!-- 房间头部 -->
      <div class="room-header" :class="{ ended: isEnded }">
        <div class="room-code-row">
          <span class="room-code">#{{ room.roomCode }}</span>
          <span class="room-badge" :class="isEnded ? 'badge-ended' : 'badge-active'">
            {{ isEnded ? '已结束' : '进行中' }}
          </span>
        </div>
        <span class="room-meta">{{ activeMembers.length }}/{{ room.maxMembers }} 人</span>
      </div>

      <!-- 成员列表 -->
      <div class="section">
        <div class="section-title">成员</div>
        <div class="member-list">
          <div
            v-for="m in room.members"
            :key="m.userId"
            class="member-item"
            :class="{ left: m.status !== 0 }"
          >
            <van-image round width="40" height="40" fit="cover" :src="m.avatarUrl || undefined">
              <template #error><van-icon name="contact" size="20" color="#c8c9cc" /></template>
            </van-image>
            <div class="member-info">
              <span class="member-name">
                {{ m.username }}
                <b v-if="m.userId === room.ownerId" class="owner-tag">房主</b>
              </span>
              <span
                :class="[
                  'member-score',
                  m.totalScore > 0 ? 'positive' : m.totalScore < 0 ? 'negative' : '',
                ]"
              >
                {{ formatScore(m.totalScore) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 平摊资金余额 -->
      <div v-if="room.fundBalance !== undefined && room.fundBalance !== 0" class="fund-balance-bar">
        资金余额：
        <b :class="room.fundBalance > 0 ? 'positive' : 'negative'">
          {{ room.fundBalance > 0 ? '+' : '' }}{{ formatMoney(Math.abs(room.fundBalance)) }} 元
        </b>
        <small>({{ room.fundBalance > 0 ? '应收' : '应付' }})</small>
      </div>

      <!-- 记账按钮 -->
      <div class="section" v-if="!isEnded">
        <div class="section-title">记账</div>
        <div class="action-row">
          <van-button
            round
            type="primary"
            size="small"
            :disabled="activeMembers.length < 2"
            @click="openTransfer"
          >
            每局记账
          </van-button>
          <van-button round type="warning" size="small" @click="openFund">
            金额平摊
          </van-button>
        </div>
      </div>

      <!-- 牌局记录 -->
      <div class="section" v-if="room.recentRounds?.length">
        <div class="section-title">牌局记录 ({{ room.recentRounds.length }})</div>
        <div v-for="r in room.recentRounds" :key="r.roundId" class="record-card">
          <div class="record-head">
            <span>第 {{ r.roundNo }} 局</span>
            <time>{{ formatTime(r.createTime) }}</time>
          </div>
          <div class="record-scores">
            <span v-for="s in r.scores" :key="s.userId">
              {{ s.username }}
              <b :class="s.score > 0 ? 'positive' : s.score < 0 ? 'negative' : ''">
                {{ formatScore(s.score) }}
              </b>
            </span>
          </div>
        </div>
      </div>

      <!-- 资金记录 -->
      <div class="section" v-if="room.recentFunds?.length">
        <div class="section-title">资金记录</div>
        <div v-for="f in room.recentFunds" :key="f.fundId" class="record-card">
          <div class="record-head">
            <span>{{ f.type === 1 ? '💰 加钱' : '💸 扣钱' }}</span>
            <b>{{ formatMoney(f.amount) }} 元</b>
            <time>{{ formatTime(f.createTime) }}</time>
          </div>
          <div class="record-meta">
            发起：{{ f.creatorName }}&nbsp;|&nbsp;分摊：{{
              f.participants.map((p) => p.username).join('、')
            }}
          </div>
        </div>
      </div>

      <!-- 空记录 -->
      <div
        class="section"
        v-if="!room.recentRounds?.length && !room.recentFunds?.length"
      >
        <van-empty image-size="60" description="暂无记录" />
      </div>

      <!-- 底部操作 -->
      <div class="bottom-bar">
        <van-button
          v-if="!isEnded && !isOwner"
          block
          round
          type="default"
          @click="handleLeave"
        >
          退出房间
        </van-button>
        <van-button
          v-if="!isEnded && isOwner"
          block
          round
          type="danger"
          @click="handleEnd"
        >
          结束房间并结算
        </van-button>
      </div>
    </template>

    <van-empty v-else description="房间不存在或你已不在房间">
      <van-button
        round
        type="primary"
        size="small"
        @click="router.replace('/discover/card-ledger')"
      >
        返回记账本
      </van-button>
    </van-empty>

    <!-- 每局记账弹窗 -->
    <van-dialog
      v-model:show="showTransfer"
      title="每局记账"
      show-cancel-button
      :confirm-button-loading="submitting"
      :before-close="
        (action: string) => {
          if (action === 'confirm') submitTransfer();
          else showTransfer = false;
          return false;
        }
      "
    >
      <div class="transfer-inputs">
        <div
          v-for="m in otherMembers"
          :key="m.userId"
          class="transfer-input-row"
        >
          <van-image round width="32" height="32" fit="cover" :src="m.avatarUrl || undefined">
            <template #error><van-icon name="contact" size="16" color="#c8c9cc" /></template>
          </van-image>
          <span class="transfer-name">{{ m.username }}</span>
          <div class="transfer-amount-wrap">
            <input
              :value="transferAmounts[m.userId] || ''"
              type="text"
              inputmode="numeric"
              pattern="[0-9]*"
              maxlength="6"
              placeholder="0"
              class="transfer-input"
              @input="onTransferInput(m.userId, ($event.target as HTMLInputElement).value)"
            />
            <span class="transfer-unit">元</span>
          </div>
        </div>
        <div class="transfer-sum" v-if="transferSum > 0">
          你将转出合计：<b>{{ transferSum.toFixed(0) }} 元</b>
        </div>
        <div class="amount-hint">仅支持正整数，1 元 = 1 积分</div>
      </div>
    </van-dialog>

    <!-- 金额平摊弹窗 -->
    <van-dialog
      v-model:show="showFund"
      title="金额平摊"
      show-cancel-button
      :confirm-button-loading="submitting"
      :before-close="
        (action: string) => {
          if (action === 'confirm') submitFund();
          else showFund = false;
          return false;
        }
      "
    >
      <div class="fund-form">
        <div class="fund-type-row">
          <span
            :class="['fund-type-tab', { active: fundType === 1 }]"
            @click="fundType = 1"
          >
            💰 加钱
          </span>
          <span
            :class="['fund-type-tab', { active: fundType === 2 }]"
            @click="fundType = 2"
          >
            💸 扣钱
          </span>
        </div>
        <div class="fund-amount-row">
          <input
            v-model="fundAmount"
            type="text"
            inputmode="numeric"
            pattern="[0-9]*"
            maxlength="6"
            placeholder="0"
            class="fund-amount-input"
            @input="fundAmount = fundAmount.replace(/\D/g, '').slice(0, 6)"
          />
          <span class="fund-unit">元</span>
        </div>
        <div class="amount-hint">仅支持 1 到 999999 的正整数金额</div>
        <div class="fund-members">
          <div class="fund-members-title">参与分摊</div>
          <div class="fund-member-list">
            <div
              v-for="m in activeMembers"
              :key="m.userId"
              :class="[
                'fund-member-chip',
                { selected: fundParticipantIds.includes(m.userId) },
              ]"
              @click="toggleFundParticipant(m.userId)"
            >
              {{ m.username }}
            </div>
          </div>
        </div>
      </div>
    </van-dialog>
  </div>
</template>

<style scoped>
.room-page {
  min-height: 100dvh;
  padding: 12px 12px 80px;
  background: #f7f8fa;
  box-sizing: border-box;
}
.page-loading {
  padding-top: 120px;
}

.room-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  margin-bottom: 12px;
  background: linear-gradient(135deg, #1989fa, #07c160);
  border-radius: 14px;
  color: #fff;
}
.room-header.ended {
  background: #969799;
}
.room-code-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.room-code {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 3px;
}
.room-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
}
.badge-active {
  background: rgb(255 255 255 / 25%);
}
.badge-ended {
  background: rgb(255 255 255 / 25%);
}
.room-meta {
  font-size: 13px;
  opacity: 0.85;
}

.section {
  margin-bottom: 12px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 10px;
  padding-left: 4px;
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.member-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: #fff;
  border-radius: 10px;
}
.member-item.left {
  opacity: 0.5;
}
.member-info {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.member-name {
  font-size: 14px;
}
.owner-tag {
  font-size: 10px;
  color: #1989fa;
  margin-left: 4px;
  font-weight: 400;
}
.member-score {
  font-size: 16px;
  font-weight: 600;
}
.member-score.positive {
  color: #07c160;
}
.member-score.negative {
  color: #ee0a24;
}

.fund-balance-bar {
  padding: 10px 16px;
  margin-bottom: 12px;
  background: #fff;
  border-radius: 10px;
  font-size: 14px;
  color: #646566;
}
.fund-balance-bar b {
  margin: 0 4px;
}
.fund-balance-bar small {
  color: #969799;
}

.action-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.record-card {
  padding: 12px;
  margin-bottom: 8px;
  background: #fff;
  border-radius: 10px;
}
.record-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 13px;
}
.record-head time {
  color: #969799;
  font-size: 11px;
  margin-left: auto;
}
.record-scores {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 4px;
  font-size: 13px;
}
.record-scores span {
  margin-right: 8px;
}
.record-scores b {
  font-weight: 600;
}
.record-meta {
  font-size: 12px;
  color: #969799;
}
.positive {
  color: #07c160;
}
.negative {
  color: #ee0a24;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom, 0px));
  background: #fff;
  border-top: 1px solid #eee;
  z-index: 99;
}

/* 每局记账弹窗 */
.transfer-inputs {
  max-height: 50vh;
  overflow-y: auto;
  padding: 8px 16px;
}
.transfer-input-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
}
.transfer-name {
  flex: 1;
  font-size: 14px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.transfer-amount-wrap {
  display: flex;
  align-items: center;
  gap: 4px;
}
.transfer-input {
  width: 80px;
  padding: 6px 8px;
  border: 1px solid #ebedf0;
  border-radius: 8px;
  text-align: center;
  font-size: 16px;
  outline: none;
}
.transfer-input:focus {
  border-color: #1989fa;
}
.transfer-unit {
  font-size: 13px;
  color: #969799;
}
.transfer-sum {
  padding: 12px 0 4px;
  text-align: center;
  font-size: 15px;
  color: #ee0a24;
}
.transfer-sum b {
  font-size: 17px;
}
.amount-hint {
  margin: 6px 0 10px;
  color: #969799;
  font-size: 12px;
  text-align: center;
}

/* 金额平摊弹窗 */
.fund-form {
  padding: 8px 16px 16px;
}
.fund-type-row {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.fund-type-tab {
  flex: 1;
  padding: 10px 0;
  text-align: center;
  font-size: 15px;
  border-radius: 10px;
  background: #f7f8fa;
  color: #646566;
  cursor: pointer;
  transition: all 0.2s;
}
.fund-type-tab.active {
  background: #1989fa;
  color: #fff;
}
.fund-amount-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-bottom: 16px;
}
.fund-amount-input {
  width: 140px;
  padding: 10px 12px;
  border: 1px solid #ebedf0;
  border-radius: 10px;
  text-align: center;
  font-size: 22px;
  outline: none;
}
.fund-amount-input:focus {
  border-color: #1989fa;
}
.fund-unit {
  font-size: 16px;
  color: #323233;
}
.fund-members-title {
  font-size: 13px;
  color: #969799;
  margin-bottom: 10px;
}
.fund-member-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.fund-member-chip {
  padding: 6px 14px;
  font-size: 13px;
  border-radius: 16px;
  background: #f7f8fa;
  color: #646566;
  cursor: pointer;
  transition: all 0.2s;
}
.fund-member-chip.selected {
  background: #1989fa;
  color: #fff;
}
</style>
