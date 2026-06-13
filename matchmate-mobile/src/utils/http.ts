import axios from 'axios';

export const isUnauthorizedError = (error: unknown) =>
  axios.isAxiosError(error) && error.response?.status === 401;

export const getRequestErrorMessage = (error: unknown, fallback: string) => {
  if (!axios.isAxiosError(error)) {
    return fallback;
  }

  const description = error.response?.data?.description;
  return typeof description === 'string' && description.trim() ? description : fallback;
};
