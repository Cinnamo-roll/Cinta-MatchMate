import myAxios from '../plugins/myAxios';
import type { AxiosResponse } from 'axios';
import type { BaseResponse } from '../models/api';
import type {
  CardRoomHistory,
  CardRoomVO,
  AddTransferRequest,
  AddFundRequest,
} from '../models/card';
import type { User } from '../models/user';

const unwrap = <T>(response: AxiosResponse<BaseResponse<T>>) =>
  response.data.data;

export const createRoom = async () =>
  unwrap(await myAxios.post<BaseResponse<CardRoomVO>>('/card-room/create'));

export const joinRoom = async (roomCode: string, roomPassword: string) =>
  unwrap(
    await myAxios.post<BaseResponse<CardRoomVO>>('/card-room/join', { roomCode, roomPassword }),
  );

export const getRoomDetail = async (roomId: number) =>
  unwrap(await myAxios.get<BaseResponse<CardRoomVO>>(`/card-room/${roomId}`));

export const getActiveRoom = async () =>
  unwrap(await myAxios.get<BaseResponse<CardRoomVO | null>>('/card-room/active-room'));

export const getCardHistory = async (limit = 6) =>
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

export const kickMember = async (roomId: number, userId: number) =>
  unwrap(
    await myAxios.post<BaseResponse<CardRoomVO>>(
      `/card-room/${roomId}/member/${userId}/kick`,
    ),
  );

export const approveMember = async (roomId: number, userId: number) =>
  unwrap(
    await myAxios.post<BaseResponse<CardRoomVO>>(
      `/card-room/${roomId}/member/${userId}/approve`,
    ),
  );

export const addTransfer = async (roomId: number, req: AddTransferRequest) =>
  unwrap(
    await myAxios.post<BaseResponse<CardRoomVO>>(`/card-room/${roomId}/transfer`, req),
  );

export const addFund = async (roomId: number, req: AddFundRequest) =>
  unwrap(
    await myAxios.post<BaseResponse<CardRoomVO>>(`/card-room/${roomId}/fund`, req),
  );

export const requestRoundUndo = async (roomId: number, roundId: number) =>
  unwrap(
    await myAxios.post<BaseResponse<CardRoomVO>>(`/card-room/${roomId}/round/${roundId}/undo`),
  );

export const requestFundUndo = async (roomId: number, fundId: number) =>
  unwrap(
    await myAxios.post<BaseResponse<CardRoomVO>>(`/card-room/${roomId}/fund/${fundId}/undo`),
  );

export const approveUndo = async (roomId: number, requestId: number) =>
  unwrap(
    await myAxios.post<BaseResponse<CardRoomVO>>(`/card-room/${roomId}/undo/${requestId}/approve`),
  );

export const endRoom = async (roomId: number) =>
  unwrap(
    await myAxios.post<BaseResponse<CardRoomVO>>(`/card-room/${roomId}/end`),
  );
