import myAxios from '../plugins/myAxios';
import type { AxiosResponse } from 'axios';
import type {
  BaseResponse,
  LoginRequest,
  PageResponse,
  RegisterResult,
  RegisterRequest,
  RegistrationPolicy,
  TagCategory,
  UpdateRegistrationLimitRequest,
  UpdateUserProfileRequest,
  UpdatePasswordRequest,
} from '../models/api';
import type { User, UserRecommendation } from '../models/user';

const unwrap = <T>(response: AxiosResponse<BaseResponse<T>>) =>
  response.data.data;

let currentUserRequest: Promise<User> | null = null;
let cachedCurrentUser: User | null = null;
let cachedCurrentUserAt = 0;
let tagCategoriesRequest: Promise<TagCategory[]> | null = null;
let cachedTagCategories: TagCategory[] | null = null;
const CURRENT_USER_CACHE_TTL = 30 * 1000;

export const clearCurrentUserCache = () => {
  currentUserRequest = null;
  cachedCurrentUser = null;
  cachedCurrentUserAt = 0;
};

export const clearTagCategoriesCache = () => {
  tagCategoriesRequest = null;
  cachedTagCategories = null;
};

export const getTagCategories = async () => {
  if (cachedTagCategories) return cachedTagCategories;
  if (!tagCategoriesRequest) {
    tagCategoriesRequest = myAxios
      .get<BaseResponse<TagCategory[]>>('/tag/categories')
      .then(unwrap)
      .then((categories) => {
        cachedTagCategories = categories;
        return categories;
      })
      .finally(() => {
        tagCategoriesRequest = null;
      });
  }
  return tagCategoriesRequest;
};

export const searchUsers = async (
  keyword = '',
  tagList: string[] = [],
  pageNum = 1,
  pageSize = 10,
) =>
  unwrap(
    await myAxios.get<BaseResponse<PageResponse<User>>>('/user/search/tags', {
      params: {
        keyword: keyword.trim() || undefined,
        tagList: tagList.length > 0 ? tagList : undefined,
        pageNum,
        pageSize,
      },
      paramsSerializer: {
        indexes: null,
      },
    }),
  );

export const recommendUsers = async (pageNum = 1, pageSize = 10) =>
  unwrap(
    await myAxios.get<BaseResponse<PageResponse<UserRecommendation>>>('/user/recommend', {
      params: { pageNum, pageSize },
    }),
  );

export const login = async (request: LoginRequest) => {
  const user = unwrap(await myAxios.post<BaseResponse<User>>('/user/login', request));
  cachedCurrentUser = user;
  cachedCurrentUserAt = Date.now();
  return user;
};

export const register = async (request: RegisterRequest) =>
  unwrap(await myAxios.post<BaseResponse<RegisterResult>>('/user/register', request));

export const logout = async () => {
  try {
    await myAxios.post<BaseResponse<null>>('/user/logout');
  } finally {
    clearCurrentUserCache();
  }
};

export const getCurrentUser = () => {
  if (cachedCurrentUser && Date.now() - cachedCurrentUserAt < CURRENT_USER_CACHE_TTL) {
    return Promise.resolve(cachedCurrentUser);
  }
  if (!currentUserRequest) {
    currentUserRequest = myAxios
      .get<BaseResponse<User>>('/user/current')
      .then(unwrap)
      .then((user) => {
        cachedCurrentUser = user;
        cachedCurrentUserAt = Date.now();
        return user;
      })
      .finally(() => {
        currentUserRequest = null;
      });
  }
  return currentUserRequest;
};

export const updateCurrentUser = async (request: UpdateUserProfileRequest) => {
  const user = unwrap(await myAxios.put<BaseResponse<User>>('/user/current', request));
  cachedCurrentUser = user;
  cachedCurrentUserAt = Date.now();
  return user;
};

export const updateCurrentUserPassword = async (request: UpdatePasswordRequest) => {
  await myAxios.put<BaseResponse<null>>('/user/password', request);
};

export const updateCurrentUserTags = async (tagList: string[]) => {
  const user = unwrap(
    await myAxios.put<BaseResponse<User>>('/user/tags', {
      tagList,
    }),
  );
  cachedCurrentUser = user;
  cachedCurrentUserAt = Date.now();
  return user;
};

export const deleteCurrentUser = async (userPassword: string) => {
  const result = unwrap(
    await myAxios.delete<BaseResponse<null>>('/user/current', {
      data: { userPassword },
    }),
  );
  clearCurrentUserCache();
  return result;
};

export const uploadAvatar = async (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  const user = unwrap(
    await myAxios.post<BaseResponse<User>>('/user/avatar', formData),
  );
  cachedCurrentUser = user;
  cachedCurrentUserAt = Date.now();
  return user;
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

export const getRegistrationPolicy = async () =>
  unwrap(await myAxios.get<BaseResponse<RegistrationPolicy>>('/user/registration/policy'));

export const updateRegistrationPolicy = async (
  request: UpdateRegistrationLimitRequest,
) =>
  unwrap(
    await myAxios.put<BaseResponse<RegistrationPolicy>>(
      '/user/registration/policy',
      request,
    ),
  );

export const searchPendingRegistrations = async (
  pageNum = 1,
  pageSize = 20,
) =>
  unwrap(
    await myAxios.get<BaseResponse<PageResponse<User>>>('/user/registration/pending', {
      params: { pageNum, pageSize },
    }),
  );

export const approveRegistration = async (userId: number) => {
  await myAxios.put<BaseResponse<null>>(`/user/registration/${userId}/approve`);
};

export const rejectRegistration = async (userId: number) => {
  await myAxios.put<BaseResponse<null>>(`/user/registration/${userId}/reject`);
};
