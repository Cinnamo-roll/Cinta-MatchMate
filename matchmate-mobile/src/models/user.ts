export type User = {
  id: number;
  username: string | null;
  userAccount: string;
  avatarUrl: string | null;
  gender: number | null;
  phone: string | null;
  email: string | null;
  userStatus: number;
  userRole: number;
  userTags: string[];
  createTime: string;
};
