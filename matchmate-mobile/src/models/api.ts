export type BaseResponse<T> = {
  code: number;
  data: T;
  message: string;
  description: string;
};

export type TagCategory = {
  category: string;
  tags: string[];
};

export type LoginRequest = {
  userAccount: string;
  userPassword: string;
};

export type RegisterRequest = {
  userAccount: string;
  userPassword: string;
  checkPassword: string;
};

export type UpdateUserProfileRequest = {
  username?: string;
  avatarUrl?: string;
  gender?: number;
  phone?: string;
  email?: string;
};

export type UpdatePasswordRequest = {
  currentPassword: string;
  newPassword: string;
  checkPassword: string;
};
