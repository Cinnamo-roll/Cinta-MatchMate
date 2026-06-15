/** 用户标签最大选择数量，搜索筛选与个人标签共用 */
export const MAX_TAGS = 3;

export const USER_STATUS = {
  NORMAL: 0,
  BANNED: 1,
  PENDING: 2,
} as const;

export const USER_ROLE = {
  USER: 0,
  ADMIN: 1,
} as const;

export const getGenderText = (gender: number | null) => {
  if (gender === 1) return '男';
  if (gender === 2) return '女';
  return '未知';
};

export const getRoleText = (role: number) => {
  if (role === USER_ROLE.ADMIN) return '管理员';
  if (role === USER_ROLE.USER) return '普通用户';
  return '未知';
};

export const getUserStatusText = (status: number) => {
  if (status === USER_STATUS.NORMAL) return '正常';
  if (status === USER_STATUS.BANNED) return '已封停';
  if (status === USER_STATUS.PENDING) return '待审核';
  return '异常';
};

export const isAdmin = (role: number) => role === USER_ROLE.ADMIN;
export const isNormalUserStatus = (status: number) => status === USER_STATUS.NORMAL;
export const isBannedUserStatus = (status: number) => status === USER_STATUS.BANNED;
export const isPendingUserStatus = (status: number) => status === USER_STATUS.PENDING;
