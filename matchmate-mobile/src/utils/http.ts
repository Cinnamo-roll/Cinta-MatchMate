import axios from 'axios';

export const isUnauthorizedError = (error: unknown) =>
  axios.isAxiosError(error) && error.response?.status === 401;

export const getRequestErrorMessage = (error: unknown, fallback: string) => {
  if (!axios.isAxiosError(error)) {
    return fallback;
  }

  if (!error.response) {
    return '网络连接失败，请确认前端和后端服务地址可被当前设备访问';
  }

  const description = error.response?.data?.description;
  if (typeof description === 'string' && description.trim()) {
    return description;
  }

  if (error.response.status >= 500) {
    return '服务器暂时不可用，请稍后重试';
  }

  const message = error.response?.data?.message;
  return typeof message === 'string' && message.trim() ? message : fallback;
};
