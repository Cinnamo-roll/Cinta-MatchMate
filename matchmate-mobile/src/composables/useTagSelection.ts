import { ref } from 'vue';
import { getTagCategories } from '../api/matchmate';
import { useNotify } from './useNotify';
import type { TagCategory } from '../models/api';

export const useTagSelection = (maxTags: number) => {
  const { showNotify } = useNotify();
  const categories = ref<TagCategory[]>([]);
  const draftTags = ref<string[]>([]);

  const loadCategories = async () => {
    if (categories.value.length > 0) return;
    try {
      categories.value = await getTagCategories();
    } catch {
      showNotify('标签分类加载失败');
    }
  };

  const isTagSelected = (tag: string) => draftTags.value.includes(tag);

  const toggleTag = (tag: string) => {
    if (isTagSelected(tag)) {
      draftTags.value = draftTags.value.filter((item) => item !== tag);
      return;
    }
    if (draftTags.value.length >= maxTags) {
      showNotify(`最多选择 ${maxTags} 个标签`);
      return;
    }
    draftTags.value.push(tag);
  };

  const clearDraftTags = () => {
    draftTags.value = [];
  };

  return {
    categories,
    draftTags,
    loadCategories,
    isTagSelected,
    toggleTag,
    clearDraftTags,
  };
};
