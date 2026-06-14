import { ref } from 'vue';
import { normalizeErrorMessage } from '../utils/http';

const message = ref('');
const type = ref<'error' | 'success'>('error');
let timer: ReturnType<typeof setTimeout> | null = null;

export const useNotify = () => {
  const showNotify = (msg: string, t: 'error' | 'success' = 'error') => {
    if (timer) clearTimeout(timer);
    message.value = normalizeErrorMessage(msg);
    type.value = t;
    timer = setTimeout(() => {
      message.value = '';
    }, 2000);
  };

  return { message, type, showNotify };
};
