const twoDigits = (value: number) => String(value).padStart(2, '0');

export const formatClockTime = (time: string) => {
  const date = new Date(time);
  return `${twoDigits(date.getHours())}:${twoDigits(date.getMinutes())}`;
};

export const formatMonthDay = (time: string) =>
  new Date(time).toLocaleDateString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  });

export const formatMonthDayTime = (time: string) =>
  new Date(time).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });

export const formatConversationTime = (time: string | null) => {
  if (!time) return '';
  const date = new Date(time);
  const now = new Date();
  if (date.toDateString() === now.toDateString()) {
    return formatClockTime(time);
  }
  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) {
    return '昨天';
  }
  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}/${date.getDate()}`;
  }
  return `${date.getFullYear()}/${date.getMonth() + 1}/${date.getDate()}`;
};
