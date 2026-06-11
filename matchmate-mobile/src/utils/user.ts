/** 用户标签最大选择数量，搜索筛选与个人标签共用 */
export const MAX_TAGS = 3;

export const getGenderText = (gender: number | null) => {
  if (gender === 1) return '男';
  if (gender === 2) return '女';
  return '未知';
};

export const getRoleText = (role: number) => {
  if (role === 1) return '管理员';
  if (role === 0) return '普通用户';
  return '未知';
};

export const isAdmin = (role: number) => role === 1;
