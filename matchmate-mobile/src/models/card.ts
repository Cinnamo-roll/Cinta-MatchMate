// 打牌记账本 - 类型定义

/** 房间状态 */
export type CardRoomVO = {
  roomId: number;
  roomCode: string;
  ownerId: number;
  ownerName: string;
  status: number;       // 0-进行中 1-已结束
  maxMembers: number;
  teaAmount: number;    // 茶钱总额(分)
  mealAmount: number;   // 饭钱总额(分)
  settleTime: string | null;
  createTime: string;
  members: CardRoomMemberVO[];
  recentRounds: CardRoundVO[];
  recentExpenses: CardExpenseVO[];
  recentFunds: CardFundRecordVO[];
  fundBalance: number;  // 当前用户平摊资金余额（分）
};

/** 房间成员 */
export type CardRoomMemberVO = {
  userId: number;
  username: string;
  avatarUrl: string | null;
  totalScore: number;
  status: number; // 0-在房间 1-已退出 2-已结算
  wins: number;
  losses: number;
  joinTime: string;
};

/** 牌局记录 */
export type CardRoundVO = {
  roundId: number;
  roundNo: number;
  creatorId: number;
  createTime: string;
  scores: RoundScoreEntry[];
};

export type RoundScoreEntry = {
  userId: number;
  username: string;
  score: number;
};

/** 费用记录 */
export type CardExpenseVO = {
  expenseId: number;
  type: number;   // 1-茶钱 2-饭钱
  amount: number; // 金额(分)
  payerId: number;
  payerName: string;
  createTime: string;
  participants: ExpenseParticipant[];
};

export type ExpenseParticipant = {
  userId: number;
  username: string;
};

/** 平摊资金记录 */
export type CardFundRecordVO = {
  fundId: number;
  type: number;   // 1-加钱 2-扣钱
  amount: number; // 金额（分）
  creatorId: number;
  creatorName: string;
  createTime: string;
  participants: FundParticipant[];
};

export type FundParticipant = {
  userId: number;
  username: string;
};

export type CardRoomHistory = {
  roomId: number;
  roomCode: string;
  ownerName: string | null;
  status: number;
  memberCount: number;
  score: number;
  createTime: string;
  settleTime: string | null;
};

/** 请求体 */
export type AddTransferRequest = {
  transfers: { toUserId: number; amount: number }[];
};

export type AddFundRequest = {
  type: number;           // 1-加钱 2-扣钱
  amount: number;         // 元
  participantIds: number[];
};

export type AddRoundRequest = {
  scores: { userId: number; score: number }[];
};

export type AddExpenseRequest = {
  type: number;           // 1-茶 2-饭
  amount: number;         // 分
  participantIds: number[];
};

/** WebSocket 推送载荷 */
export type CardWsPayload = {
  type: string;
  roomId: number;
  data: any;
};

/** 事件类型常量 */
export const CardWsEvent = {
  MEMBER_JOINED: 'card_room_member_joined',
  MEMBER_LEFT: 'card_room_member_left',
  ROUND_CREATED: 'card_room_round_created',
  EXPENSE_CREATED: 'card_room_expense_created',
  FUND_CREATED: 'card_room_fund_created',
  ROOM_CLOSED: 'card_room_closed',
} as const;
