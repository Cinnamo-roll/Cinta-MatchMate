<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showConfirmDialog } from 'vant';
import { useNotify } from '../composables/useNotify';
import { useCardWebSocket } from '../composables/useCardWebSocket';
import {
  getRoomDetail,
  leaveRoom,
  addTransfer,
  addFund,
  endRoom,
  requestRoundUndo,
  requestFundUndo,
  approveUndo,
} from '../api/card';
import { getCurrentUser } from '../api/matchmate';
import { CardWsEvent } from '../models/card';
import type { CardFundRecordVO, CardRoomMemberVO, CardRoomVO, CardWsPayload } from '../models/card';
import type { User } from '../models/user';
import { getRequestErrorMessage, isUnauthorizedError } from '../utils/http';

type SettlementTransfer = {
  fromUserId: number;
  fromName: string;
  toUserId: number;
  toName: string;
  amount: number;
  unit: 'yuan' | 'fen';
};

type UndoTarget =
  | { type: 'round'; id: number; label: string }
  | { type: 'fund'; id: number; label: string };

const route = useRoute();
const router = useRouter();
const { showNotify } = useNotify();
const { connect, disconnect, onMessage } = useCardWebSocket();

const roomId = Number(route.params.id);
const room = ref<CardRoomVO | null>(null);
const currentUser = ref<User | null>(null);
const roomPageRef = ref<HTMLElement | null>(null);
const loading = ref(true);
const loginRequired = ref(false);
const POSITIVE_INTEGER_PATTERN = /^[1-9]\d{0,5}$/;

const showTransfer = ref(false);
const transferAmounts = ref<Record<number, string>>({});
const submitting = ref(false);

const showFund = ref(false);
const fundAmount = ref('');
const fundParticipantIds = ref<number[]>([]);

const showSettlement = ref(false);
const showUndoConfirm = ref(false);
const undoTarget = ref<UndoTarget | null>(null);
const settlementTab = ref(0);

const isOwner = computed(() => room.value?.ownerId === currentUser.value?.id);
const isEnded = computed(() => room.value?.status === 1);
const activeMembers = computed(() => room.value?.members.filter((m) => m.status === 0) ?? []);
const settlementMembers = computed(() => room.value?.members ?? []);
const otherMembers = computed(() => activeMembers.value.filter((m) => m.userId !== currentUser.value?.id));
const fundCandidates = computed(() => otherMembers.value);

const transferSum = computed(() =>
  Object.values(transferAmounts.value).reduce(
    (sum, value) => sum + (POSITIVE_INTEGER_PATTERN.test(value) ? Number(value) : 0),
    0,
  ),
);

const selectedFundShare = computed(() => {
  if (!POSITIVE_INTEGER_PATTERN.test(fundAmount.value) || fundParticipantIds.value.length === 0) {
    return '';
  }
  return (Number(fundAmount.value) / (fundParticipantIds.value.length + 1)).toFixed(2);
});

const myMember = computed(() => {
  const myId = currentUser.value?.id;
  return settlementMembers.value.find((member) => member.userId === myId) ?? null;
});

const totalScoreSum = computed(() =>
  settlementMembers.value.reduce((sum, member) => sum + member.totalScore, 0),
);

const settlementTransfers = computed<SettlementTransfer[]>(() => {
  const debtors = settlementMembers.value
    .filter((member) => member.totalScore < 0)
    .map((member) => ({
      userId: member.userId,
      name: member.username,
      amount: Math.abs(member.totalScore),
    }))
    .sort((a, b) => b.amount - a.amount);
  const creditors = settlementMembers.value
    .filter((member) => member.totalScore > 0)
    .map((member) => ({
      userId: member.userId,
      name: member.username,
      amount: member.totalScore,
    }))
    .sort((a, b) => b.amount - a.amount);

  const result: SettlementTransfer[] = [];
  let debtorIndex = 0;
  let creditorIndex = 0;

  while (debtorIndex < debtors.length && creditorIndex < creditors.length) {
    const debtor = debtors[debtorIndex];
    const creditor = creditors[creditorIndex];
    const amount = Math.min(debtor.amount, creditor.amount);
    if (amount > 0) {
      result.push({
        fromUserId: debtor.userId,
        fromName: debtor.name,
        toUserId: creditor.userId,
        toName: creditor.name,
        amount,
        unit: 'yuan',
      });
    }

    debtor.amount -= amount;
    creditor.amount -= amount;
    if (debtor.amount === 0) debtorIndex++;
    if (creditor.amount === 0) creditorIndex++;
  }

  return result;
});

const fundSettlementTransfers = computed<SettlementTransfer[]>(() => {
  const funds = room.value?.recentFunds ?? [];
  return funds.flatMap((fund) => {
    if (!fund.participants.length) return [];
    const totalPeople = fund.participants.length + 1;
    const baseShare = Math.floor(fund.amount / totalPeople);
    const remainder = fund.amount % totalPeople;
    return fund.participants.map((participant, index) => ({
      fromUserId: participant.userId,
      fromName: participant.username,
      toUserId: fund.creatorId,
      toName: fund.creatorName,
      amount: baseShare + (index + 1 < remainder ? 1 : 0),
      unit: 'fen',
    }));
  });
});

const allSettlementTransfers = computed(() => [
  ...settlementTransfers.value,
  ...fundSettlementTransfers.value,
]);

const mergedAllSettlementTransfers = computed(() => mergeTransfers(allSettlementTransfers.value));
const mergedMyPayTransfers = computed(() => {
  const myId = currentUser.value?.id;
  return mergedAllSettlementTransfers.value.filter((item) => item.fromUserId === myId);
});
const mergedMyReceiveTransfers = computed(() => {
  const myId = currentUser.value?.id;
  return mergedAllSettlementTransfers.value.filter((item) => item.toUserId === myId);
});
const myPayTotalFen = computed(() => sumTransferFen(mergedMyPayTransfers.value));
const myReceiveTotalFen = computed(() => sumTransferFen(mergedMyReceiveTransfers.value));
const myNetFen = computed(() => myReceiveTotalFen.value - myPayTotalFen.value);
const myNetLabel = computed(() => {
  if (myNetFen.value > 0) return '别人还需要给我';
  if (myNetFen.value < 0) return '我还需要给别人';
  return '刚好结清';
});

const fundCreatorSummaries = computed(() =>
  (room.value?.recentFunds ?? []).map((fund) => ({
    fundId: fund.fundId,
    creatorName: fund.creatorName,
    amount: fund.amount,
    share: formatFundShare(fund),
    participantCount: fund.participants.length + 1,
    receiverCount: fund.participants.length,
  })),
);

const hasAnySettlement = computed(() =>
  mergedAllSettlementTransfers.value.length > 0 || fundCreatorSummaries.value.length > 0,
);

const isScrollableContent = (target: EventTarget | null) =>
  target instanceof Element && Boolean(target.closest('.member-list, .records-list'));

const preventOuterTouchMove = (event: TouchEvent) => {
  if (!isScrollableContent(event.target)) {
    event.preventDefault();
  }
};

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
  await nextTick();
  roomPageRef.value?.addEventListener('touchmove', preventOuterTouchMove, { passive: false });
  if (room.value && !isEnded.value) {
    connect(roomId);
    unsubWs = onMessage((payload: CardWsPayload) => {
      if (payload.roomId !== roomId) return;
      if (payload.type === CardWsEvent.ROOM_CLOSED) disconnect();
      if (refreshTimer) clearTimeout(refreshTimer);
      refreshTimer = setTimeout(loadRoom, 100);
    });
  }
});

onUnmounted(() => {
  if (refreshTimer) clearTimeout(refreshTimer);
  roomPageRef.value?.removeEventListener('touchmove', preventOuterTouchMove);
  unsubWs?.();
  disconnect();
});

const handleLeave = async () => {
  try {
    await showConfirmDialog({ title: '退出房间', message: '确定退出当前房间吗？' });
  } catch {
    return;
  }
  try {
    await leaveRoom(roomId);
    router.replace('/discover/card-ledger');
  } catch (error) {
    handleRequestError(error, '退出房间失败');
  }
};

const handleEnd = async () => {
  try {
    await showConfirmDialog({
      title: '结束房间',
      message: '会保存本房间的输赢结果，确定结束吗？',
    });
  } catch {
    return;
  }
  try {
    room.value = await endRoom(roomId);
    showNotify('房间已结算', 'success');
  } catch (error) {
    handleRequestError(error, '结算失败');
  }
};

const openTransfer = () => {
  if (!room.value || !currentUser.value) return;
  transferAmounts.value = {};
  otherMembers.value.forEach((member) => {
    transferAmounts.value[member.userId] = '';
  });
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
  const transfers = entries.map(([userId, value]) => ({
    toUserId: Number(userId),
    amount: Number(value),
  }));
  if (!transfers.length) {
    showNotify('至少填一个人的金额');
    return;
  }

  submitting.value = true;
  try {
    room.value = await addTransfer(roomId, { transfers });
    showTransfer.value = false;
    showNotify('牌局已记录', 'success');
  } catch (error) {
    handleRequestError(error, '记录牌局失败');
  } finally {
    submitting.value = false;
  }
};

const onTransferInput = (userId: number, value: string) => {
  transferAmounts.value[userId] = value.replace(/\D/g, '').slice(0, 6);
};

const openFund = () => {
  fundAmount.value = '';
  fundParticipantIds.value = fundCandidates.value.map((member) => member.userId);
  showFund.value = true;
};

const submitFund = async () => {
  if (submitting.value) return;
  if (!POSITIVE_INTEGER_PATTERN.test(fundAmount.value)) {
    showNotify('金额只能输入 1 到 999999 的正整数');
    return;
  }
  if (!fundParticipantIds.value.length) {
    showNotify('请至少选择一位分摊成员');
    return;
  }

  submitting.value = true;
  try {
    room.value = await addFund(roomId, {
      amount: Number(fundAmount.value),
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
  const index = fundParticipantIds.value.indexOf(userId);
  if (index >= 0) {
    fundParticipantIds.value.splice(index, 1);
    return;
  }
  fundParticipantIds.value.push(userId);
};

const openSettlement = () => {
  if (!room.value || !currentUser.value) return;
  settlementTab.value = 0;
  showSettlement.value = true;
};

const openUndo = (target: UndoTarget) => {
  undoTarget.value = target;
  showUndoConfirm.value = true;
};

const submitUndo = async () => {
  const target = undoTarget.value;
  if (!target || submitting.value) return;
  submitting.value = true;
  try {
    room.value = target.type === 'round'
      ? await requestRoundUndo(roomId, target.id)
      : await requestFundUndo(roomId, target.id);
    showUndoConfirm.value = false;
    undoTarget.value = null;
    showNotify('已发起撤销，等参与人同意后生效', 'success');
  } catch (error) {
    handleRequestError(error, '发起撤销失败');
  } finally {
    submitting.value = false;
  }
};

const submitUndoApproval = async (requestId: number) => {
  if (submitting.value) return;
  submitting.value = true;
  try {
    room.value = await approveUndo(roomId, requestId);
    showNotify('已同意撤销', 'success');
  } catch (error) {
    handleRequestError(error, '同意撤销失败');
  } finally {
    submitting.value = false;
  }
};

const formatMoney = (fen: number) => (fen / 100).toFixed(2);
const formatScore = (score: number) => (score > 0 ? `+${score}` : String(score));
const transferAmountFen = (item: SettlementTransfer) =>
  item.unit === 'fen' ? item.amount : item.amount * 100;
const formatSettlementAmount = (item: SettlementTransfer) =>
  formatMoney(transferAmountFen(item));
const formatSignedFen = (fen: number) => {
  if (fen > 0) return `+${formatMoney(fen)}`;
  if (fen < 0) return `-${formatMoney(Math.abs(fen))}`;
  return '0.00';
};
const sumTransferFen = (items: SettlementTransfer[]) =>
  items.reduce((sum, item) => sum + transferAmountFen(item), 0);
const mergeTransfers = (items: SettlementTransfer[]) => {
  const byPair = new Map<string, SettlementTransfer & { unit: 'fen' }>();
  items.forEach((item) => {
    const key = `${item.fromUserId}-${item.toUserId}`;
    const existing = byPair.get(key);
    if (existing) {
      existing.amount += transferAmountFen(item);
      return;
    }
    byPair.set(key, {
      fromUserId: item.fromUserId,
      fromName: item.fromName,
      toUserId: item.toUserId,
      toName: item.toName,
      amount: transferAmountFen(item),
      unit: 'fen',
    });
  });
  return Array.from(byPair.values()).filter((item) => item.amount > 0);
};
const formatTime = (time: string) =>
  new Date(time).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
const undoHint = (status: { approvedCount: number; requiredCount: number; requesterName: string | null }) =>
  `${status.requesterName ?? '有人'}申请撤销，已同意 ${status.approvedCount}/${status.requiredCount}`;
const canRequestUndo = (creatorId: number) =>
  !isEnded.value && creatorId === currentUser.value?.id;
const formatFundShare = (fund: CardFundRecordVO) => {
  if (!fund.participants.length) return '0.00';
  return formatMoney(fund.amount / (fund.participants.length + 1));
};
const fundShareText = (fund: CardFundRecordVO) =>
  `每人平摊 ${formatFundShare(fund)} 元`;
const memberStatusText = (member: CardRoomMemberVO) => {
  if (member.status === 1) return '已退出';
  if (member.status === 2) return '已结算';
  return '在房间';
};
</script>

<template>
  <div ref="roomPageRef" class="room-page">
    <van-loading v-if="loading" class="page-loading" vertical>加载中...</van-loading>

    <van-empty v-else-if="loginRequired" description="请先登录后查看房间">
      <van-button round type="primary" size="small" @click="goToLogin">
        去登录
      </van-button>
    </van-empty>

    <template v-else-if="room">
      <header class="room-header" :class="{ ended: isEnded }">
        <div class="room-code-row">
          <span class="room-code">#{{ room.roomCode }}</span>
          <span class="room-badge">
            {{ isEnded ? '已结束' : '进行中' }}
          </span>
        </div>
        <span class="room-meta">{{ activeMembers.length }}/{{ room.maxMembers }} 人</span>
      </header>

      <section class="room-panel member-section">
        <div class="panel-head">
          <div>
            <div class="section-title">成员</div>
            <div class="section-desc">当前在房 {{ activeMembers.length }} 人</div>
          </div>
          <span class="panel-count">{{ room.members.length }}/{{ room.maxMembers }}</span>
        </div>
        <div class="member-list">
          <div
            v-for="member in room.members"
            :key="member.userId"
            class="member-item"
            :class="{ left: member.status !== 0 }"
          >
            <van-image round width="40" height="40" fit="cover" :src="member.avatarUrl || undefined">
              <template #error>
                <van-icon name="contact" size="20" color="#c8c9cc" />
              </template>
            </van-image>
            <div class="member-info">
              <div class="member-name-row">
                <span class="member-name">{{ member.username }}</span>
                <b v-if="member.userId === room.ownerId" class="owner-tag">房主</b>
                <small v-if="member.status !== 0">{{ memberStatusText(member) }}</small>
              </div>
              <span
                :class="[
                  'member-score',
                  member.totalScore > 0 ? 'positive' : member.totalScore < 0 ? 'negative' : '',
                ]"
              >
                {{ formatScore(member.totalScore) }} 元
              </span>
            </div>
          </div>
        </div>
      </section>

      <section v-if="!isEnded" class="room-panel action-section">
        <div class="panel-head">
          <div>
            <div class="section-title">记一笔</div>
            <div class="section-desc">进行记录</div>
          </div>
        </div>
        <div class="action-row">
          <van-button
            round
            type="primary"
            size="small"
            :disabled="activeMembers.length < 2"
            @click="openTransfer"
          >
            记一笔
          </van-button>
          <van-button round type="warning" size="small" @click="openFund">
            发起平摊
          </van-button>
          <van-button round plain type="primary" size="small" @click="openSettlement">
            当前统计
          </van-button>
        </div>
      </section>

      <section class="room-panel records-section">
        <div class="panel-head">
          <div>
            <div class="section-title">收支记录</div>
            <div class="section-desc">最近记下的输赢和资金平摊</div>
          </div>
        </div>
        <div class="records-list">
          <div v-if="room.recentRounds?.length">
            <div v-for="round in room.recentRounds" :key="round.roundId" class="record-card">
              <div class="record-head">
                <span>收支记录</span>
                <time>{{ formatTime(round.createTime) }}</time>
              </div>
              <div class="record-scores">
                <span v-for="score in round.scores" :key="score.userId">
                  {{ score.username }}
                  <b :class="score.score > 0 ? 'positive' : score.score < 0 ? 'negative' : ''">
                    {{ formatScore(score.score) }} 元
                  </b>
                </span>
              </div>
              <div class="record-actions" v-if="!isEnded">
                <span v-if="round.undoStatus" class="undo-status">
                  {{ undoHint(round.undoStatus) }}
                </span>
                <van-button
                  v-if="round.undoStatus?.canApprove"
                  size="mini"
                  plain
                  type="primary"
                  :loading="submitting"
                  @click="submitUndoApproval(round.undoStatus.requestId)"
                >
                  同意撤销
                </van-button>
                <van-button
                  v-else-if="!round.undoStatus && canRequestUndo(round.creatorId)"
                  size="mini"
                  plain
                  type="danger"
                  @click="openUndo({ type: 'round', id: round.roundId, label: '这笔收支记录' })"
                >
                  申请撤销
                </van-button>
              </div>
            </div>
          </div>

          <div v-if="room.recentFunds?.length" class="fund-record-group">
            <div class="subsection-title">资金记录</div>
            <div v-for="fund in room.recentFunds" :key="fund.fundId" class="record-card">
              <div class="record-head">
                <span>资金平摊</span>
                <b>{{ formatMoney(fund.amount) }} 元</b>
                <time>{{ formatTime(fund.createTime) }}</time>
              </div>
              <div class="fund-share-line">
                {{ fundShareText(fund) }}
              </div>
              <div class="record-meta">
                {{ fund.creatorName }} 记录，参与：{{
                  fund.participants.map((participant) => participant.username).join('、')
                }}
              </div>
              <div class="record-actions" v-if="!isEnded">
                <span v-if="fund.undoStatus" class="undo-status">
                  {{ undoHint(fund.undoStatus) }}
                </span>
                <van-button
                  v-if="fund.undoStatus?.canApprove"
                  size="mini"
                  plain
                  type="primary"
                  :loading="submitting"
                  @click="submitUndoApproval(fund.undoStatus.requestId)"
                >
                  同意撤销
                </van-button>
                <van-button
                  v-else-if="!fund.undoStatus && canRequestUndo(fund.creatorId)"
                  size="mini"
                  plain
                  type="danger"
                  @click="openUndo({ type: 'fund', id: fund.fundId, label: `${fund.creatorName} 发起的 ${formatMoney(fund.amount)} 元平摊` })"
                >
                  申请撤销
                </van-button>
              </div>
            </div>
          </div>

          <van-empty
            v-if="!room.recentRounds?.length && !room.recentFunds?.length"
            image-size="60"
            description="暂无记录"
          />
        </div>
      </section>

      <footer class="bottom-bar">
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
        <van-button
          v-if="isEnded"
          block
          round
          plain
          type="primary"
          @click="openSettlement"
        >
          查看结算明细
        </van-button>
      </footer>
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

    <van-dialog
      v-model:show="showTransfer"
      title="记一笔收支"
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
          v-for="member in otherMembers"
          :key="member.userId"
          class="transfer-input-row"
        >
          <van-image round width="32" height="32" fit="cover" :src="member.avatarUrl || undefined">
            <template #error>
              <van-icon name="contact" size="16" color="#c8c9cc" />
            </template>
          </van-image>
          <span class="transfer-name">{{ member.username }}</span>
          <div class="transfer-amount-wrap">
            <input
              :value="transferAmounts[member.userId] || ''"
              type="text"
              inputmode="numeric"
              pattern="[0-9]*"
              maxlength="6"
              placeholder="0"
              class="transfer-input"
              @input="onTransferInput(member.userId, ($event.target as HTMLInputElement).value)"
            />
            <span class="transfer-unit">元</span>
          </div>
        </div>
        <div class="transfer-sum" v-if="transferSum > 0">
          你将转出合计：<b>{{ transferSum.toFixed(0) }} 元</b>
        </div>
        <div class="amount-hint">仅支持正整数金额，单位为元</div>
      </div>
    </van-dialog>

    <van-dialog
      v-model:show="showFund"
      title="资金平摊"
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
          <div class="fund-members-title">谁一起平摊这笔钱（不含自己）</div>
          <div class="fund-member-list">
            <button
              v-for="member in fundCandidates"
              :key="member.userId"
              type="button"
              :class="[
                'fund-member-chip',
                { selected: fundParticipantIds.includes(member.userId) },
              ]"
              @click="toggleFundParticipant(member.userId)"
            >
              {{ member.username }}
            </button>
          </div>
        </div>
        <div v-if="selectedFundShare" class="fund-preview">
          这笔钱共 {{ fundParticipantIds.length + 1 }} 人平摊，你也算一份；其他人每人给你
          <b>{{ selectedFundShare }} 元</b>
        </div>
      </div>
    </van-dialog>

    <van-dialog
      v-model:show="showSettlement"
      title="结算明细"
      confirm-button-text="知道了"
    >
      <div class="settlement-panel">
        <div class="settlement-hero">
          <span>我的结算</span>
          <b :class="myNetFen > 0 ? 'positive' : myNetFen < 0 ? 'negative' : ''">
            {{ formatSignedFen(myNetFen) }} 元
          </b>
          <small>{{ myNetLabel }}</small>
        </div>

        <div v-if="totalScoreSum !== 0" class="settlement-warning">
          当前所有人的输赢金额没有完全对上，下面的结算明细按现有记录估算。
        </div>

        <div class="settlement-cards">
          <div>
            <span>我要付</span>
            <b>{{ formatMoney(myPayTotalFen) }} 元</b>
          </div>
          <div>
            <span>我要收</span>
            <b>{{ formatMoney(myReceiveTotalFen) }} 元</b>
          </div>
          <div>
            <span>输赢金额</span>
            <b :class="myMember && myMember.totalScore > 0 ? 'positive' : myMember && myMember.totalScore < 0 ? 'negative' : ''">
              {{ myMember ? formatScore(myMember.totalScore) : '0' }} 元
            </b>
          </div>
        </div>

        <van-tabs v-model:active="settlementTab" shrink class="settlement-tabs">
          <van-tab title="我的">
            <section class="settlement-block">
              <h4>我需要给谁</h4>
              <div v-if="mergedMyPayTransfers.length" class="settlement-list">
                <div v-for="item in mergedMyPayTransfers" :key="`pay-${item.toUserId}`">
                  <span>{{ item.toName }}</span>
                  <b>{{ formatSettlementAmount(item) }} 元</b>
                </div>
              </div>
              <p v-else>暂无需要你转出的金额</p>
            </section>

            <section class="settlement-block">
              <h4>谁需要给我</h4>
              <div v-if="mergedMyReceiveTransfers.length" class="settlement-list">
                <div v-for="item in mergedMyReceiveTransfers" :key="`receive-${item.fromUserId}`">
                  <span>{{ item.fromName }}</span>
                  <b>{{ formatSettlementAmount(item) }} 元</b>
                </div>
              </div>
              <p v-else>暂无需要别人转给你的金额</p>
            </section>
          </van-tab>

          <van-tab title="全部">
            <section class="settlement-block">
              <h4>全部收付款</h4>
              <div v-if="mergedAllSettlementTransfers.length" class="settlement-list">
                <div v-for="(item, index) in mergedAllSettlementTransfers" :key="`all-${index}`">
                  <span>{{ item.fromName }} 给 {{ item.toName }}</span>
                  <b>{{ formatSettlementAmount(item) }} 元</b>
                </div>
              </div>
              <p v-else>暂无需要结算的收付款</p>
            </section>

            <section class="settlement-block">
              <h4>资金平摊记录</h4>
              <div v-if="fundCreatorSummaries.length" class="settlement-list">
                <div v-for="item in fundCreatorSummaries" :key="item.fundId">
                  <span>{{ item.creatorName }} 先付 {{ formatMoney(item.amount) }} 元，{{ item.participantCount }} 人平摊</span>
                  <b>每人 {{ item.share }} 元</b>
                </div>
              </div>
              <p v-else>暂无资金平摊记录</p>
            </section>
          </van-tab>
        </van-tabs>

        <van-empty
          v-if="!hasAnySettlement"
          image-size="56"
          description="当前没有需要结算的记录"
        />
      </div>
    </van-dialog>

    <van-dialog
      v-model:show="showUndoConfirm"
      title="申请撤销"
      show-cancel-button
      confirm-button-text="发起撤销"
      :confirm-button-loading="submitting"
      :before-close="
        (action: string) => {
          if (action === 'confirm') submitUndo();
          else {
            showUndoConfirm = false;
            undoTarget = null;
          }
          return false;
        }
      "
    >
      <div class="undo-confirm">
        <p>确定申请撤销「{{ undoTarget?.label }}」吗？</p>
        <span>所有参与这条记录的人都同意后，系统才会真正撤销并回滚金额。</span>
      </div>
    </van-dialog>
  </div>
</template>

<style scoped>
.room-page {
  display: flex;
  flex-direction: column;
  height: calc(100dvh - var(--van-nav-bar-height, 46px));
  min-height: 0;
  padding: 12px 12px 76px;
  overflow: hidden;
  overscroll-behavior-x: none;
  touch-action: pan-y;
  background: #f7f8fa;
  box-sizing: border-box;
}

.page-loading {
  padding-top: 120px;
}

.room-header {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  margin-bottom: 10px;
  color: #fff;
  background: linear-gradient(135deg, #1989fa, #07c160);
  border-radius: 8px;
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
  padding: 2px 8px;
  font-size: 11px;
  background: rgb(255 255 255 / 25%);
  border-radius: 10px;
}

.room-meta {
  font-size: 13px;
  opacity: 0.85;
}

.room-panel {
  display: flex;
  flex-direction: column;
  padding: 12px;
  margin-bottom: 10px;
  overflow-x: hidden;
  background: #fff;
  border-radius: 8px;
}

.panel-head {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.section-title {
  color: #323233;
  font-weight: 600;
  font-size: 15px;
  line-height: 1.3;
}

.section-desc,
.panel-count {
  margin-top: 2px;
  color: #969799;
  font-size: 12px;
  line-height: 1.4;
}

.panel-count {
  flex: 0 0 auto;
  margin-top: 0;
}

.member-section {
  flex: 0 0 min(34%, 230px);
  min-height: 112px;
}

.member-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  max-width: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: none;
  touch-action: pan-y;
}

.member-list::-webkit-scrollbar,
.records-list::-webkit-scrollbar {
  display: none;
}

.member-item {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  max-width: 100%;
  min-width: 0;
  background: #f7f8fa;
  border-radius: 8px;
}

.member-item.left {
  opacity: 0.55;
}

.member-info {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.member-name-row {
  display: flex;
  align-items: center;
  min-width: 0;
}

.member-name {
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.member-name-row small {
  flex: 0 0 auto;
  margin-left: 6px;
  color: #969799;
  font-size: 11px;
}

.owner-tag {
  flex: 0 0 auto;
  margin-left: 4px;
  color: #1989fa;
  font-weight: 400;
  font-size: 10px;
}

.member-score {
  flex: 0 0 auto;
  margin-left: 8px;
  font-weight: 600;
  font-size: 16px;
}

.positive {
  color: #07c160;
}

.negative {
  color: #ee0a24;
}

.fund-balance-bar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 10px;
  color: #646566;
  font-size: 14px;
  background: #fff;
  border-radius: 8px;
}

.fund-balance-bar b {
  flex: 0 0 auto;
}

.action-section {
  flex: 0 0 auto;
  margin-bottom: 10px;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.action-row :deep(.van-button) {
  flex: 1 1 92px;
}

.records-section {
  flex: 1 1 auto;
  min-height: 0;
}

.records-list {
  flex: 1;
  min-height: 0;
  max-width: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: none;
  touch-action: pan-y;
}

.record-card {
  padding: 12px;
  margin-bottom: 8px;
  max-width: 100%;
  min-width: 0;
  overflow-x: hidden;
  background: #f7f8fa;
  border-radius: 8px;
}

.record-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 13px;
}

.record-head time {
  margin-left: auto;
  color: #969799;
  font-size: 11px;
}

.record-scores {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 13px;
}

.record-scores b {
  font-weight: 600;
}

.fund-record-group {
  margin-top: 8px;
}

.subsection-title {
  padding: 4px 4px 8px;
  color: #646566;
  font-size: 13px;
}

.record-meta {
  color: #969799;
  font-size: 12px;
  line-height: 1.5;
}

.fund-share-line {
  margin-bottom: 4px;
  color: #323233;
  font-size: 13px;
  font-weight: 600;
}

.bottom-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 99;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom, 0px));
  background: #fff;
  border-top: 1px solid #eee;
}

.transfer-inputs {
  max-height: 50vh;
  padding: 8px 16px;
  overflow-y: auto;
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
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.transfer-amount-wrap {
  display: flex;
  align-items: center;
  gap: 4px;
}

.transfer-input {
  width: 80px;
  padding: 6px 8px;
  font-size: 16px;
  text-align: center;
  border: 1px solid #ebedf0;
  border-radius: 8px;
  outline: none;
}

.transfer-input:focus,
.fund-amount-input:focus {
  border-color: #1989fa;
}

.transfer-unit,
.fund-unit {
  color: #969799;
  font-size: 13px;
}

.transfer-sum {
  padding: 12px 0 4px;
  color: #ee0a24;
  font-size: 15px;
  text-align: center;
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

.fund-form {
  padding: 8px 16px 16px;
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
  font-size: 22px;
  text-align: center;
  border: 1px solid #ebedf0;
  border-radius: 8px;
  outline: none;
}

.fund-members-title {
  margin-bottom: 10px;
  color: #969799;
  font-size: 13px;
}

.fund-member-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.fund-member-chip {
  padding: 6px 14px;
  color: #646566;
  font-size: 13px;
  background: #f7f8fa;
  border: 0;
  border-radius: 16px;
}

.fund-member-chip.selected {
  color: #fff;
  background: #1989fa;
}

.fund-preview {
  padding: 10px 12px;
  margin-top: 12px;
  color: #646566;
  font-size: 13px;
  text-align: center;
  background: #f7f8fa;
  border-radius: 8px;
}

.fund-preview b {
  color: #1989fa;
}

.settlement-panel {
  max-height: 68vh;
  padding: 4px 16px 18px;
  overflow-y: auto;
  background: #f7f8fa;
}

.settlement-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 16px 12px;
  margin-bottom: 10px;
  background: #fff;
  border-radius: 8px;
}

.settlement-hero span,
.settlement-hero small {
  color: #969799;
  font-size: 12px;
}

.settlement-hero b {
  font-size: 26px;
  line-height: 1.2;
}

.settlement-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}

.settlement-cards div {
  min-width: 0;
  padding: 10px 8px;
  background: #fff;
  border-radius: 8px;
}

.settlement-cards span {
  display: block;
  margin-bottom: 5px;
  color: #969799;
  font-size: 12px;
}

.settlement-cards b {
  display: block;
  overflow: hidden;
  color: #323233;
  font-size: 14px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.settlement-warning {
  padding: 8px 10px;
  margin-bottom: 10px;
  color: #ed6a0c;
  font-size: 12px;
  background: #fff7e8;
  border-radius: 8px;
}

.settlement-tabs {
  overflow: hidden;
  background: #fff;
  border-radius: 8px;
}

.settlement-tabs :deep(.van-tabs__wrap) {
  border-bottom: 1px solid #f2f3f5;
}

.settlement-tabs :deep(.van-tab__panel) {
  padding: 10px;
}

.settlement-block {
  margin-top: 4px;
}

.settlement-block + .settlement-block {
  margin-top: 14px;
}

.settlement-block h4 {
  margin: 0 0 8px;
  color: #323233;
  font-size: 14px;
}

.settlement-block p {
  margin: 0;
  color: #969799;
  font-size: 13px;
}

.settlement-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.settlement-list div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  background: #f7f8fa;
  border-radius: 8px;
}

.settlement-list span {
  min-width: 0;
  overflow: hidden;
  color: #323233;
  font-size: 13px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.settlement-list b {
  flex: 0 0 auto;
  color: #1989fa;
  font-size: 14px;
}

.record-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.undo-status {
  flex: 1;
  min-width: 0;
  color: #969799;
  font-size: 12px;
}

.undo-confirm {
  padding: 8px 18px 18px;
}

.undo-confirm p {
  margin: 0 0 8px;
  color: #323233;
  font-size: 15px;
  line-height: 1.5;
}

.undo-confirm span {
  color: #969799;
  font-size: 13px;
  line-height: 1.5;
}
</style>
