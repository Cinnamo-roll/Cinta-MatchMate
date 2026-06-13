export type ConversationVO = {
  id: number;
  targetUserId: number;
  targetUsername: string | null;
  targetAvatarUrl: string | null;
  lastMessage: string | null;
  lastMessageTime: string | null;
  unreadCount: number;
  isOnline: boolean | null;
  lastOnlineTime: string | null;
};

export type MessageVO = {
  id: number;
  conversationId: number;
  senderId: number;
  receiverId: number;
  content: string;
  messageType: number;
  status: number;
  createTime: string;
};

export type SendMessageRequest = {
  receiverId: number;
  content: string;
};

export type MessagesReadPayload = {
  conversationId: number;
  readerId: number;
};

export type WsPushPayload = {
  type: 'new_message';
  data: MessageVO;
} | {
  type: 'messages_read';
  data: MessagesReadPayload;
} | {
  type: 'account_banned';
  data: {
    message: string;
  };
};
