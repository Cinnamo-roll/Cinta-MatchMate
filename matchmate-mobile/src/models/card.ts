// Card ledger type definitions.

export type CardRoomVO = {
  roomId: number;
  roomCode: string;
  ownerId: number;
  ownerName: string;
  status: number;
  maxMembers: number;
  settleTime: string | null;
  createTime: string;
  members: CardRoomMemberVO[];
  recentRounds: CardRoundVO[];
  recentFunds: CardFundRecordVO[];
  fundBalance: number;
};

export type CardRoomMemberVO = {
  userId: number;
  username: string;
  avatarUrl: string | null;
  totalScore: number;
  status: number;
  wins: number;
  losses: number;
  joinTime: string;
};

export type CardRoundVO = {
  roundId: number;
  roundNo: number;
  creatorId: number;
  createTime: string;
  scores: RoundScoreEntry[];
  undoStatus: CardUndoStatus | null;
};

export type RoundScoreEntry = {
  userId: number;
  username: string;
  score: number;
};

export type CardFundRecordVO = {
  fundId: number;
  type: number;
  amount: number;
  creatorId: number;
  creatorName: string;
  createTime: string;
  participants: FundParticipant[];
  undoStatus: CardUndoStatus | null;
};

export type CardUndoStatus = {
  requestId: number;
  requesterId: number;
  requesterName: string | null;
  approvedCount: number;
  requiredCount: number;
  approvedByMe: boolean;
  canApprove: boolean;
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

export type AddTransferRequest = {
  transfers: { toUserId: number; amount: number }[];
};

export type AddFundRequest = {
  type?: number;
  amount: number;
  participantIds: number[];
};

export type AddRoundRequest = {
  scores: { userId: number; score: number }[];
};

export type CardWsPayload = {
  type: string;
  roomId: number;
  data: unknown;
};

export const CardWsEvent = {
  MEMBER_JOINED: 'card_room_member_joined',
  MEMBER_LEFT: 'card_room_member_left',
  ROUND_CREATED: 'card_room_round_created',
  FUND_CREATED: 'card_room_fund_created',
  ROOM_CLOSED: 'card_room_closed',
} as const;
