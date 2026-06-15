import type { RouteLocationRaw } from 'vue-router';
import type { CardRoomMemberVO, CardRoomVO } from '../models/card';

export const CARD_ROOM_STATUS = {
  ACTIVE: 0,
  ENDED: 1,
} as const;

export const CARD_MEMBER_STATUS = {
  ACTIVE: 0,
  LEFT: 1,
  SETTLED: 2,
  KICKED: 3,
  REJOIN_REQUEST: 4,
} as const;

export const POSITIVE_INTEGER_PATTERN = /^[1-9]\d{0,5}$/;

export const DISCOVER_PATH = '/discover';
export const CARD_LEDGER_PATH = '/discover/card-ledger';

export const cardLedgerRoute = (): RouteLocationRaw => ({
  path: CARD_LEDGER_PATH,
  query: { skipActiveRoom: '1' },
});

export const cardRoomBackTarget = (room: CardRoomVO | null) =>
  room?.status === CARD_ROOM_STATUS.ENDED
    ? `${CARD_LEDGER_PATH}?skipActiveRoom=1`
    : DISCOVER_PATH;

export const sanitizeMoneyInput = (value: string) =>
  value.replace(/\D/g, '').slice(0, 6);

export const getCardPayloadUserId = (data: unknown) => {
  if (!data || typeof data !== 'object' || !('id' in data)) return null;
  const id = (data as { id?: unknown }).id;
  return typeof id === 'number' ? id : null;
};

export const memberStatusText = (member: CardRoomMemberVO) => {
  if (member.status === CARD_MEMBER_STATUS.LEFT) return '已退出';
  if (member.status === CARD_MEMBER_STATUS.SETTLED) return '已结算';
  if (member.status === CARD_MEMBER_STATUS.KICKED) return '已踢出';
  if (member.status === CARD_MEMBER_STATUS.REJOIN_REQUEST) return '申请加入';
  return '在房间';
};
