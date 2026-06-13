<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { MAX_TAGS } from '../utils/user';
import { useNotify } from '../composables/useNotify';
import { useTagSelection } from '../composables/useTagSelection';
import { recommendUsers, searchUsers } from '../api/matchmate';
import UserCard from '../components/UserCard.vue';
import type { User } from '../models/user';

const keyword = ref('');
const selectedTags = ref<string[]>([]);
const users = ref<User[]>([]);
const showFilter = ref(false);
const activeCategoryName = ref('');
const loading = ref(false);
let searchTimer: ReturnType<typeof setTimeout> | undefined;
let latestRequestId = 0;

const { showNotify } = useNotify();
const { categories, draftTags, loadCategories, isTagSelected: isDraftSelected, toggleTag: toggleDraftTag } = useTagSelection(MAX_TAGS);

const activeCategory = computed(() =>
  categories.value.find(
    (category) => category.category === activeCategoryName.value,
  ),
);

const headerRef = ref<HTMLElement | null>(null);
const headerHeight = ref(62);

const updateHeaderHeight = () => {
  nextTick(() => {
    if (headerRef.value) {
      headerHeight.value = headerRef.value.offsetHeight;
    }
  });
};

watch([selectedTags, users], updateHeaderHeight);


const loadUsers = async () => {
  const requestId = ++latestRequestId;
  try {
    loading.value = true;
    // TODO: 后期改为基于大数据的智能推荐算法
    let results: User[];
    if (!keyword.value.trim() && selectedTags.value.length === 0) {
      results = await recommendUsers(8);
    } else {
      results = await searchUsers(keyword.value, selectedTags.value);
    }
    if (requestId === latestRequestId) {
      users.value = results;
    }
  } catch {
    if (requestId === latestRequestId) {
      showNotify('搜索失败，请稍后重试');
    }
  } finally {
    if (requestId === latestRequestId) {
      loading.value = false;
    }
  }
};

const openFilter = () => {
  draftTags.value = [...selectedTags.value];
  showFilter.value = true;
};

const applyFilter = () => {
  selectedTags.value = [...draftTags.value];
  showFilter.value = false;
  loadUsers();
};

const removeSelectedTag = (tag: string) => {
  selectedTags.value = selectedTags.value.filter((item) => item !== tag);
  loadUsers();
};

const resetSearch = () => {
  keyword.value = '';
  selectedTags.value = [];
  draftTags.value = [];
  loadUsers();
};

watch(keyword, () => {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(loadUsers, 350);
});

onBeforeUnmount(() => {
  latestRequestId += 1;
  if (searchTimer) clearTimeout(searchTimer);
});

onMounted(async () => {
  await loadCategories();
  activeCategoryName.value = categories.value[0]?.category ?? '';
  await loadUsers();
  updateHeaderHeight();
});
</script>

<template>
  <div class="search-page">
    <div ref="headerRef" class="search-header">
      <div class="search-toolbar">
        <van-search
          v-model="keyword"
          class="search-input"
          clearable
          shape="round"
          placeholder="搜索用户名、账号或标签"
          @search="loadUsers"
        />

        <button class="filter-button" type="button" @click="openFilter">
          <van-icon name="filter-o" size="17" />
          <span>筛选</span>
          <i v-if="selectedTags.length > 0">{{ selectedTags.length }}</i>
        </button>
      </div>

      <div v-if="selectedTags.length > 0" class="selected-bar">
        <div class="selected-scroll">
          <button
            v-for="tag in selectedTags"
            :key="tag"
            class="selected-chip"
            type="button"
            @click="removeSelectedTag(tag)"
          >
            {{ tag }}
            <van-icon name="cross" />
          </button>
        </div>
        <button class="clear-button" type="button" @click="resetSearch">清空</button>
      </div>

      <div class="result-heading">
        <div>
          <strong>{{ keyword.trim() || selectedTags.length ? '匹配结果' : '推荐伙伴' }}</strong>
          <span>{{ users.length }} 人</span>
        </div>
        <p v-if="selectedTags.length > 1">同时匹配全部所选标签</p>
      </div>
    </div>

    <main class="result-area" :style="{ paddingTop: headerHeight + 'px' }">
      <van-loading v-if="loading" class="page-loading" vertical>
        搜索中...
      </van-loading>

      <div v-else-if="users.length > 0" class="user-list">
        <UserCard
          v-for="user in users"
          :key="user.id"
          :user="user"
          :highlighted-tags="selectedTags"
        />
      </div>

      <van-empty v-else image="search" description="没有找到符合条件的伙伴">
        <van-button round size="small" type="primary" @click="resetSearch">
          清空条件
        </van-button>
      </van-empty>
    </main>

    <van-popup
      v-model:show="showFilter"
      position="bottom"
      round
      closeable
      :style="{ height: '78%' }"
    >
      <div class="filter-panel">
        <div class="filter-heading">
          <div>
            <h3>标签筛选</h3>
            <p>最多选择 {{ MAX_TAGS }} 个，需同时满足</p>
          </div>
          <span>{{ draftTags.length }} 项</span>
        </div>

        <div v-if="draftTags.length > 0" class="draft-section">
          <div class="draft-heading">
            <span>已选择</span>
            <button type="button" @click="draftTags = []">清空</button>
          </div>
          <div class="tag-list draft-tags">
            <van-tag
              v-for="tag in draftTags"
              :key="tag"
              closeable
              round
              size="medium"
              type="primary"
              @close="toggleDraftTag(tag)"
            >
              {{ tag }}
            </van-tag>
          </div>
        </div>

        <div class="filter-content">
          <aside class="category-menu">
            <button
              v-for="category in categories"
              :key="category.category"
              class="category-item"
              :class="{ active: activeCategoryName === category.category }"
              type="button"
              @click="activeCategoryName = category.category"
            >
              {{ category.category }}
            </button>
          </aside>

          <section class="tag-panel">
            <h4>{{ activeCategory?.category }}</h4>
            <div class="tag-list tag-options">
              <button
                v-for="tag in activeCategory?.tags"
                :key="tag"
                class="tag-option"
                :class="{ selected: isDraftSelected(tag) }"
                type="button"
                @click="toggleDraftTag(tag)"
              >
                <van-icon v-if="isDraftSelected(tag)" name="success" size="13" />
                {{ tag }}
              </button>
            </div>
          </section>
        </div>

        <div class="filter-footer">
          <van-button round plain type="primary" @click="draftTags = []">
            重置
          </van-button>
          <van-button round type="primary" @click="applyFilter">
            查看匹配结果
          </van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.search-page {
  height: calc(100dvh - var(--van-nav-bar-height, 46px));
  padding-bottom: 24px;
  overflow-y: auto;
  overscroll-behavior-y: none;
  -webkit-overflow-scrolling: touch;
  background: #f7f8fa;
  box-sizing: border-box;
  scrollbar-width: none;
}

.search-page::-webkit-scrollbar {
  display: none;
}

.search-header {
  position: fixed;
  top: var(--van-nav-bar-height, 46px);
  right: 0;
  left: 0;
  z-index: 99;
  background: #fff;
}

.search-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #fff;
  box-sizing: border-box;
}

.search-input {
  min-width: 0;
  flex: 1;
  padding: 0;
}

.filter-button {
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  height: 34px;
  padding: 0 10px;
  color: #323233;
  background: #f2f3f5;
  border: 0;
  border-radius: 17px;
}

.filter-button i {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 17px;
  height: 17px;
  color: #fff;
  font-size: 11px;
  font-style: normal;
  background: #1989fa;
  border-radius: 9px;
}

.selected-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 12px 10px;
  background: #fff;
}

.selected-scroll {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
  min-width: 0;
  overflow-x: hidden;
  scrollbar-width: none;
}

.selected-scroll::-webkit-scrollbar {
  display: none;
}

.selected-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  padding: 6px 10px;
  color: #1989fa;
  font-size: 12px;
  background: #ecf9ff;
  border: 0;
  border-radius: 14px;
}

.clear-button {
  flex-shrink: 0;
  padding: 0;
  color: #969799;
  font-size: 12px;
  background: transparent;
  border: 0;
}

.result-area {
  padding: 0 12px 16px;
  box-sizing: border-box;
}

.result-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 10px 12px 6px;
  background: #fff;
}

.result-heading strong {
  margin-right: 8px;
  font-size: 17px;
}

.result-heading span,
.result-heading p {
  color: #969799;
  font-size: 12px;
}

.result-heading p {
  margin: 0;
}

.page-loading {
  padding-top: 60px;
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.filter-heading {
  display: flex;
  justify-content: space-between;
  padding: 20px 48px 14px 18px;
}

.filter-heading h3 {
  margin: 0;
}

.filter-heading p {
  margin: 5px 0 0;
  color: #969799;
  font-size: 12px;
}

.filter-heading > span {
  color: #1989fa;
  font-size: 13px;
  font-weight: 600;
}

.draft-section {
  padding: 0 18px 14px;
}

.draft-heading {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #646566;
  font-size: 13px;
}

.draft-heading button {
  padding: 0;
  color: #969799;
  background: transparent;
  border: 0;
}

.filter-content {
  display: flex;
  min-height: 0;
  flex: 1;
  border-top: 1px solid #f2f3f5;
}

.category-menu {
  width: 104px;
  overflow-y: auto;
  background: #f7f8fa;
}

.category-item {
  position: relative;
  width: 100%;
  padding: 15px 8px;
  color: #646566;
  font-size: 13px;
  background: transparent;
  border: 0;
}

.category-item.active {
  color: #1989fa;
  font-weight: 600;
  background: #fff;
}

.category-item.active::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 18px;
  background: #1989fa;
  content: '';
  transform: translateY(-50%);
}

.tag-panel {
  min-width: 0;
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.tag-panel h4 {
  margin: 0 0 14px;
}

.filter-footer {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 10px;
  padding: 12px 16px 20px;
  box-shadow: 0 -2px 8px rgb(0 0 0 / 5%);
}
</style>
