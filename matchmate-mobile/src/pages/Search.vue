<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { MAX_TAGS } from '../utils/user';
import { useNotify } from '../composables/useNotify';
import { useTagSelection } from '../composables/useTagSelection';
import { recommendUsers, searchUsers } from '../api/matchmate';
import UserCard from '../components/UserCard.vue';
import type { User } from '../models/user';

const SEARCH_PAGE_SIZE = 10;
const SEARCH_RECOMMENDATION_LIMIT = 6;
const keyword = ref('');
const selectedTags = ref<string[]>([]);
const users = ref<User[]>([]);
const recommendationReasons = ref<Record<number, string>>({});
const showFilter = ref(false);
const activeCategoryName = ref('');
const loading = ref(false);
const finished = ref(false);
const loadFailed = ref(false);
const total = ref(0);
let searchTimer: ReturnType<typeof setTimeout> | undefined;
let pageNum = 1;
let requestInFlight = false;
let requestGeneration = 0;
let pendingReset = false;

const { showNotify } = useNotify();
const { categories, draftTags, loadCategories, isTagSelected: isDraftSelected, toggleTag: toggleDraftTag } = useTagSelection(MAX_TAGS);

const activeCategory = computed(() =>
  categories.value.find(
    (category) => category.category === activeCategoryName.value,
  ),
);

const isRecommendationMode = () =>
  !keyword.value.trim() && selectedTags.value.length === 0;

const appendUniqueUsers = (records: User[]) => {
  const userMap = new Map(users.value.map((user) => [user.id, user]));
  records.forEach((user) => userMap.set(user.id, user));
  users.value = [...userMap.values()];
};

const loadUsers = async (reset = false) => {
  if (reset) {
    requestGeneration += 1;
    pageNum = 1;
    users.value = [];
    recommendationReasons.value = {};
    total.value = 0;
    finished.value = false;
    loadFailed.value = false;
  }
  if (requestInFlight) {
    pendingReset = pendingReset || reset;
    return;
  }
  if (finished.value) return;

  const generation = requestGeneration;
  requestInFlight = true;
  try {
    loading.value = true;
    loadFailed.value = false;
    if (isRecommendationMode()) {
      const page = await recommendUsers(pageNum, SEARCH_RECOMMENDATION_LIMIT);
      if (generation !== requestGeneration) return;
      total.value = Math.min(page.total, SEARCH_RECOMMENDATION_LIMIT);
      page.records.forEach((item) => {
        recommendationReasons.value[item.user.id] = item.reason;
      });
      appendUniqueUsers(page.records.map((item) => item.user));
      finished.value = true;
    } else {
      const page = await searchUsers(
        keyword.value,
        selectedTags.value,
        pageNum,
        SEARCH_PAGE_SIZE,
      );
      if (generation !== requestGeneration) return;
      total.value = page.total;
      appendUniqueUsers(page.records);
      finished.value =
        users.value.length >= page.total || page.records.length < SEARCH_PAGE_SIZE;
    }
    pageNum += 1;
  } catch {
    if (generation === requestGeneration) {
      loadFailed.value = true;
      showNotify('搜索失败，请稍后重试');
    }
  } finally {
    requestInFlight = false;
    if (generation === requestGeneration) {
      loading.value = false;
    }
    if (pendingReset) {
      pendingReset = false;
      void loadUsers(true);
    }
  }
};

const resetAndLoad = () => loadUsers(true);
const retryLoad = () => loadUsers(users.value.length === 0);

const openFilter = () => {
  draftTags.value = [...selectedTags.value];
  showFilter.value = true;
};

const applyFilter = () => {
  selectedTags.value = [...draftTags.value];
  showFilter.value = false;
  resetAndLoad();
};

const removeSelectedTag = (tag: string) => {
  selectedTags.value = selectedTags.value.filter((item) => item !== tag);
  resetAndLoad();
};

const resetSearch = () => {
  keyword.value = '';
  selectedTags.value = [];
  draftTags.value = [];
  resetAndLoad();
};

watch(keyword, () => {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(resetAndLoad, 350);
});

onBeforeUnmount(() => {
  requestGeneration += 1;
  if (searchTimer) clearTimeout(searchTimer);
});

onMounted(async () => {
  await loadCategories();
  activeCategoryName.value = categories.value[0]?.category ?? '';
  await resetAndLoad();
});
</script>

<template>
  <div class="search-page">
    <div class="search-header">
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
          <span>{{ total }} 人</span>
        </div>
        <p v-if="selectedTags.length > 1">同时匹配全部所选标签</p>
      </div>
    </div>

    <main class="result-area">
      <div v-if="loading && users.length === 0" class="skeleton-list">
        <van-skeleton v-for="item in 3" :key="item" avatar title :row="2" />
      </div>

      <van-empty
        v-else-if="loadFailed && users.length === 0"
        image="network"
        description="加载失败，请检查网络后重试"
      >
        <van-button round size="small" type="primary" @click="retryLoad">
          重新加载
        </van-button>
      </van-empty>

      <van-empty
        v-else-if="!loading && users.length === 0"
        image="search"
        description="没有找到符合条件的伙伴"
      >
        <van-button round size="small" type="primary" @click="resetSearch">
          清空条件
        </van-button>
      </van-empty>

      <van-list
        v-else
        v-model:loading="loading"
        :finished="finished"
        :immediate-check="false"
        finished-text="没有更多伙伴啦"
        @load="loadUsers()"
      >
        <div class="user-list">
          <UserCard
            v-for="user in users"
            :key="user.id"
            :user="user"
            :highlighted-tags="selectedTags"
            :recommendation-reason="recommendationReasons[user.id]"
          />
        </div>

        <div v-if="loadFailed" class="load-error">
          <span>这一页加载失败了</span>
          <button type="button" @click="retryLoad">点此重试</button>
        </div>
      </van-list>
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
  display: flex;
  flex-direction: column;
  height: calc(100dvh - var(--app-nav-height));
  min-height: 0;
  overflow: hidden;
  background: var(--app-bg);
  box-sizing: border-box;
}

.search-header {
  position: relative;
  flex: 0 0 auto;
  z-index: 99;
  background: rgb(255 255 255 / 94%);
  border-bottom: 1px solid var(--app-border);
  box-shadow: 0 8px 24px rgb(37 45 76 / 4%);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.search-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px var(--app-page-padding);
  background: transparent;
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
  color: var(--app-text);
  background: var(--app-surface-muted);
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
  background: var(--app-primary);
  border-radius: 9px;
}

.selected-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px var(--app-page-padding) 10px;
  background: transparent;
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
  color: var(--app-primary);
  font-size: 12px;
  background: var(--app-primary-soft);
  border: 0;
  border-radius: 14px;
}

.clear-button {
  flex-shrink: 0;
  padding: 0;
  color: var(--app-text-muted);
  font-size: 12px;
  background: transparent;
  border: 0;
}

.result-area {
  flex: 1 1 auto;
  min-height: 0;
  padding: 14px var(--app-page-padding) calc(24px + var(--app-safe-bottom));
  overflow-y: auto;
  overscroll-behavior-y: contain;
  -webkit-overflow-scrolling: touch;
  box-sizing: border-box;
  scrollbar-width: none;
}

.result-area::-webkit-scrollbar {
  display: none;
}

.result-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 10px var(--app-page-padding) 8px;
  background: transparent;
}

.result-heading strong {
  margin-right: 8px;
  font-size: 17px;
}

.result-heading span,
.result-heading p {
  color: var(--app-text-muted);
  font-size: 12px;
}

.result-heading p {
  margin: 0;
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-list {
  display: grid;
  gap: 12px;
}

.skeleton-list :deep(.van-skeleton) {
  padding: 18px 15px;
  background: var(--app-surface);
  border-radius: var(--app-card-radius);
}

.load-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 18px 0 4px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.load-error button {
  padding: 4px 10px;
  color: var(--app-primary);
  background: var(--app-primary-soft);
  border: 0;
  border-radius: var(--app-pill-radius);
}

:deep(.van-list__finished-text),
:deep(.van-list__loading) {
  color: var(--app-text-muted);
  font-size: 12px;
}

.filter-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.filter-heading {
  display: flex;
  justify-content: space-between;
  padding: 22px 48px 14px 20px;
}

.filter-heading h3 {
  margin: 0;
}

.filter-heading p {
  margin: 5px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
}

.filter-heading > span {
  color: var(--app-primary);
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
  color: var(--app-text-secondary);
  font-size: 13px;
}

.draft-heading button {
  padding: 0;
  color: var(--app-text-muted);
  background: transparent;
  border: 0;
}

.filter-content {
  display: flex;
  min-height: 0;
  flex: 1;
  border-top: 1px solid var(--app-border);
}

.category-menu {
  width: 104px;
  overflow-y: auto;
  background: var(--app-bg);
}

.category-item {
  position: relative;
  width: 100%;
  padding: 15px 8px;
  color: var(--app-text-secondary);
  font-size: 13px;
  background: transparent;
  border: 0;
}

.category-item.active {
  color: var(--app-primary);
  font-weight: 600;
  background: var(--app-surface);
}

.category-item.active::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 18px;
  background: var(--app-primary);
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
  padding: 12px 16px calc(14px + var(--app-safe-bottom));
  background: var(--app-surface);
  box-shadow: 0 -8px 24px rgb(37 45 76 / 7%);
}
</style>
