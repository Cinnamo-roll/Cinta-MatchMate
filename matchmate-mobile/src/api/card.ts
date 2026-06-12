import myAxios from '../plugins/myAxios';
import type { AxiosResponse } from 'axios';
import type { BaseResponse } from '../models/api';
import type {
  CardRoomHistory,
  CardRoomVO,
  AddTransferRequest,
  AddFundRequest,
  AddExpenseRequest,
} from '../models/card';
import type { User } from '../models/user';

const unwrap = <T>(response: AxiosResponse<BaseResponse<T>>) =>
  response.data.data;

export const createRoom = async () =>
  unwrap(await myAxios.post<BaseResponse<CardRoomVO>>('/card-room/create'));

export const joinRoom = async (roomCode: string) =>
  unwrap(
    await myAxios.post<BaseResponse<CardRoomVO>>('/card-room/join', { roomCode }),
  );

export const getRoomDetail = async (roomId: number) =>
  unwrap(await myAxios.get<BaseResponse<CardRoomVO>>(`/card-room/${roomId}`));

export const getActiveRoom = async () =>
  unwrap(await myAxios.get<BaseResponse<CardRoomVO | null>>('/card-room/active-room'));

export const getCardHistory = async (limit = 10) =>
  unwrap(
    await myAxios.get<BaseResponse<CardRoomHistory[]>>('/card-room/history', {
      params: { limit },
    }),
  );

export const getCardRanking = async (limit = 20) =>
  unwrap(
    await myAxios.get<BaseResponse<User[]>>('/card-room/ranking', {
      params: { limit },
    }),
  );

export const leaveRoom = async (roomId: number) => {
  await myAxios.post<BaseResponse<null>>(`/card-room/${roomId}/leave`);
};

export const addTransfer = async (roomId: number, req: AddTransferRequest) =>
  unwrap(
    await myAxios.post<BaseResponse<CardRoomVO>>(`/card-room/${roomId}/transfer`, req),
  );

export const addFund = async (roomId: number, req: AddFundRequest) =>
  unwrap(
    await myAxios.post<BaseResponse<CardRoomVO>>(`/card-room/${roomId}/fund`, req),
  );

export const addExpense = async (roomId: number, req: AddExpenseRequest) =>
  unwrap(
    await myAxios.post<BaseResponse<CardRoomVO>>(`/card-room/${roomId}/expense`, req),
  );

export const endRoom = async (roomId: number) =>
  unwrap(
    await myAxios.post<BaseResponse<CardRoomVO>>(`/card-room/${roomId}/end`),
  );
