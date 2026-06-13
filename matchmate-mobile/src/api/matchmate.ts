import myAxios from '../plugins/myAxios';
import type { AxiosResponse } from 'axios';
import type {
  BaseResponse,
  LoginRequest,
  PageResponse,
  RegisterRequest,
  TagCategory,
  UpdateUserProfileRequest,
  UpdatePasswordRequest,
} from '../models/api';
import type { User } from '../models/user';

const unwrap = <T>(response: AxiosResponse<BaseResponse<T>>) =>
  response.data.data;

let currentUserRequest: Promise<User> | null = null;

export const getTagCategories = async () =>
  unwrap(await myAxios.get<BaseResponse<TagCategory[]>>('/tag/categories'));

export const searchUsers = async (keyword = '', tagList: string[] = []) =>
  unwrap(
    await myAxios.get<BaseResponse<User[]>>('/user/search/tags', {
      params: {
        keyword: keyword.trim() || undefined,
        tagList: tagList.length > 0 ? tagList : undefined,
      },
      paramsSerializer: {
        indexes: null,
      },
    }),
  );

export const recommendUsers = async (limit = 8) =>
  unwrap(
    await myAxios.get<BaseResponse<User[]>>('/user/recommend', {
      params: { limit },
    }),
  );

export const login = async (request: LoginRequest) =>
  unwrap(await myAxios.post<BaseResponse<User>>('/user/login', request));

export const register = async (request: RegisterRequest) =>
  unwrap(await myAxios.post<BaseResponse<number>>('/user/register', request));

export const logout = async () => {
  await myAxios.post<BaseResponse<null>>('/user/logout');
};

export const getCurrentUser = () => {
  if (!currentUserRequest) {
    currentUserRequest = myAxios
      .get<BaseResponse<User>>('/user/current')
      .then(unwrap)
      .finally(() => {
        currentUserRequest = null;
      });
  }
  return currentUserRequest;
};

export const updateCurrentUser = async (request: UpdateUserProfileRequest) =>
  unwrap(await myAxios.put<BaseResponse<User>>('/user/current', request));

export const updateCurrentUserPassword = async (request: UpdatePasswordRequest) => {
  await myAxios.put<BaseResponse<null>>('/user/password', request);
};

export const updateCurrentUserTags = async (tagList: string[]) =>
  unwrap(
    await myAxios.put<BaseResponse<User>>('/user/tags', {
      tagList,
    }),
  );

export const deleteCurrentUser = async (userPassword: string) =>
  unwrap(
    await myAxios.delete<BaseResponse<null>>('/user/current', {
      data: { userPassword },
    }),
  );

export const uploadAvatar = async (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return unwrap(
    await myAxios.post<BaseResponse<User>>('/user/avatar', formData),
  );
};

export const searchAdminUsers = async (
  keyword = '',
  pageNum = 1,
  pageSize = 20,
) =>
  unwrap(
    await myAxios.get<BaseResponse<PageResponse<User>>>('/user/search', {
      params: {
        username: keyword.trim() || undefined,
        pageNum,
        pageSize,
      },
    }),
  );

export const updateUserStatus = async (userId: number, userStatus: number) => {
  await myAxios.put<BaseResponse<null>>(`/user/${userId}/status`, {
    userStatus,
  });
};
