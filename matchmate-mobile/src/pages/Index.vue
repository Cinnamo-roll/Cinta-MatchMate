<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useNotify } from '../composables/useNotify';
import { searchUsers } from '../api/matchmate';
import { isAdmin } from '../utils/user';
import UserCard from '../components/UserCard.vue';
import type { User } from '../models/user';

const router = useRouter();
const { showNotify } = useNotify();
const users = ref<User[]>([]);
const loading = ref(false);
const loadFailed = ref(false);

const loadUsers = async () => {
  try {
    loading.value = true;
    loadFailed.value = false;
    const list = await searchUsers();
    users.value = list.sort((a, b) => (isAdmin(b.userRole) ? 1 : 0) - (isAdmin(a.userRole) ? 1 : 0));
  } catch {
    loadFailed.value = true;
    showNotify('用户列表加载失败');
  } finally {
    loading.value = false;
  }
};

onMounted(loadUsers);
</script>

<template>
  <div class="home-page">
    <div class="home-search-bar">
      <button
        class="home-search"
        type="button"
        aria-label="搜索用户或标签"
        @click="router.push('/search')"
      >
        <van-icon name="search" size="18" />
        <span>搜索用户或标签</span>
      </button>
    </div>

    <van-loading v-if="loading" class="page-loading" vertical>
      正在加载伙伴...
    </van-loading>

    <div v-else-if="users.length > 0" class="user-list">
      <UserCard v-for="user in users" :key="user.id" :user="user" />
    </div>

    <van-empty
      v-else
      :description="loadFailed ? '加载失败，请稍后重试' : '暂无用户'"
    >
      <van-button
        v-if="loadFailed"
        round
        size="small"
        type="primary"
        @click="loadUsers"
      >
        重新加载
      </van-button>
    </van-empty>
  </div>
</template>

<style scoped>
.home-page {
  height: calc(100dvh - var(--van-nav-bar-height, 46px));
  padding: 74px 12px 62px;
  overflow-y: auto;
  overscroll-behavior-y: none;
  -webkit-overflow-scrolling: touch;
  background: #f7f8fa;
  box-sizing: border-box;
  scrollbar-width: none;
}

.home-page::-webkit-scrollbar {
  display: none;
}

.home-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 38px;
  padding: 0 14px;
  color: #969799;
  font-size: 14px;
  text-align: left;
  background: #fff;
  border: 0;
  border-radius: 19px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.home-search-bar {
  position: fixed;
  right: 0;
  left: 0;
  z-index: 99;
  top: var(--van-nav-bar-height, 46px);
  padding: 12px;
  background: #f7f8fa;
}

.page-loading {
  padding-top: 80px;
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
