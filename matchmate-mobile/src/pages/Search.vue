<script setup lang="ts">
import { computed, ref } from 'vue';
import { showToast } from 'vant';

type Tag = {
  id: number;
  name: string;
};

// 临时模拟后端返回的标签数据，接入接口后替换这里的数据来源即可。
const tagOptions = ref<Tag[]>([
  { id: 1, name: 'Java' },
  { id: 2, name: 'Python' },
  { id: 3, name: 'JavaScript' },
  { id: 4, name: 'TypeScript' },
  { id: 5, name: 'Vue' },
  { id: 6, name: 'React' },
  { id: 7, name: 'Spring Boot' },
  { id: 8, name: 'MySQL' },
  { id: 9, name: 'Redis' },
  { id: 10, name: 'Docker' },
]);

const keyword = ref('');
const selectedTagIds = ref<number[]>([]);

const filteredTags = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase();

  if (!normalizedKeyword) {
    return tagOptions.value;
  }

  return tagOptions.value.filter((tag) =>
    tag.name.toLowerCase().includes(normalizedKeyword),
  );
});

const selectedTags = computed(() =>
  tagOptions.value.filter((tag) => selectedTagIds.value.includes(tag.id)),
);

const removeTag = (tagId: number) => {
  selectedTagIds.value = selectedTagIds.value.filter((id) => id !== tagId);
};

const clearSelectedTags = () => {
  selectedTagIds.value = [];
};

const onSearch = () => {
  if (!keyword.value.trim()) {
    showToast('请输入要搜索的标签');
    return;
  }

  if (filteredTags.value.length === 0) {
    showToast('没有找到相关标签');
  }
};

const onCancel = () => {
  keyword.value = '';
};
</script>

<template>
  <div class="search-page">
    <form action="/" @submit.prevent>
      <van-search
        v-model="keyword"
        show-action
        clearable
        placeholder="请输入要搜索的标签"
        @search="onSearch"
        @cancel="onCancel"
      />
    </form>

    <section v-if="selectedTags.length > 0">
      <div>
        <span>已选标签（{{ selectedTags.length }}）</span>
        <van-button size="small" type="default" @click="clearSelectedTags">
          清空
        </van-button>
      </div>

      <van-tag
        v-for="tag in selectedTags"
        :key="tag.id"
        closeable
        type="primary"
        size="medium"
        @close="removeTag(tag.id)"
      >
        {{ tag.name }}
      </van-tag>
    </section>

    <section>
      <p>{{ keyword.trim() ? '搜索结果' : '全部标签' }}</p>

      <van-checkbox-group v-model="selectedTagIds">
        <van-cell-group>
          <van-cell
            v-for="tag in filteredTags"
            :key="tag.id"
            :title="tag.name"
          >
            <template #right-icon>
              <van-checkbox :name="tag.id" />
            </template>
          </van-cell>
        </van-cell-group>
      </van-checkbox-group>

      <van-empty
        v-if="filteredTags.length === 0"
        description="没有找到相关标签"
      />
    </section>
  </div>
</template>

<style scoped>
.search-page {
  padding-bottom: 50px;
}
</style>
