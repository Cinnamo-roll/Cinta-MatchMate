import myAxios from '../plugins/myAxios';
import type { AxiosResponse } from 'axios';
import type { BaseResponse } from '../models/api';
import type { ConversationVO, MessageVO, SendMessageRequest } from '../models/chat';

const unwrap = <T>(response: AxiosResponse<BaseResponse<T>>) =>
  response.data.data;

export const getConversations = async () =>
  unwrap(
    await myAxios.get<BaseResponse<ConversationVO[]>>('/chat/conversations'),
  );

export const getConversation = async (conversationId: number) =>
  unwrap(
    await myAxios.get<BaseResponse<ConversationVO>>(`/chat/conversation/${conversationId}`),
  );

export const getMessages = async (conversationId: number, page = 1, pageSize = 20) =>
  unwrap(
    await myAxios.get<BaseResponse<MessageVO[]>>('/chat/messages', {
      params: { conversationId, page, pageSize },
    }),
  );

export const sendMessage = async (request: SendMessageRequest) =>
  unwrap(
    await myAxios.post<BaseResponse<MessageVO>>('/chat/message/send', request),
  );

export const openConversation = async (conversationId: number) => {
  await myAxios.put<BaseResponse<null>>(`/chat/conversation/${conversationId}/read`);
};

export const closeConversation = async (conversationId: number) => {
  await myAxios.put<BaseResponse<null>>(`/chat/conversation/${conversationId}/close`);
};

export const findConversationId = async (targetUserId: number) =>
  unwrap(
    await myAxios.get<BaseResponse<number | null>>(`/chat/conversation/with/${targetUserId}`),
  );
