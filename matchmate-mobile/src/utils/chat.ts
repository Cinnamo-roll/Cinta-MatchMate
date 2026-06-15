export const MESSAGE_TYPE = {
  TEXT: 0,
} as const;

export const MESSAGE_STATUS = {
  UNREAD: 0,
  READ: 1,
} as const;

export const messageReadText = (status: number | null) =>
  status === MESSAGE_STATUS.READ ? '已读' : '未读';

export const isMessageRead = (status: number | null) =>
  status === MESSAGE_STATUS.READ;
