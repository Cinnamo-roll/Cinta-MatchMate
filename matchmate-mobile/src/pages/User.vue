 <script setup lang="ts">
import axios from 'axios';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useNotify } from '../composables/useNotify';
import { getGenderText, getRoleText, isAdmin, MAX_TAGS } from '../utils/user';
import { useTagSelection } from '../composables/useTagSelection';
import {
  getCurrentUser,
  logout,
  updateCurrentUser,
  updateCurrentUserTags,
  updateCurrentUserPassword,
  deleteCurrentUser,
  uploadAvatar,
} from '../api/matchmate';
import type { UpdateUserProfileRequest } from '../models/api';
import type { User } from '../models/user';

type EditableUserField = 'username' | 'gender' | 'phone' | 'email';

type UserField = {
  key: keyof User;
  label: string;
  editable?: boolean;
  inputType?: 'text' | 'tel' | 'email' | 'url';
};

const router = useRouter();
const { showNotify } = useNotify();
const user = ref<User | null>(null);
const loading = ref(true);

const fields: UserField[] = [
  { key: 'userAccount', label: '账号' },
  { key: 'userRole', label: '角色' },
  { key: 'username', label: '昵称', editable: true },
  { key: 'gender', label: '性别', editable: true },
  { key: 'phone', label: '手机', editable: true, inputType: 'tel' },
  { key: 'email', label: '邮箱', editable: true, inputType: 'email' },
  { key: 'createTime', label: '加入时间' },
];

const showEditor = ref(false);
const editingField = ref<UserField | null>(null);
const editingValue = ref('');
const isSubmitting = ref(false);

const avatarFileInput = ref<HTMLInputElement | null>(null);
const previewAvatarUrl = ref<string | null>(null);
const isUploadingAvatar = ref(false);

const showTagEditor = ref(false);
const { categories, draftTags, loadCategories, isTagSelected, toggleTag: toggleUserTag } = useTagSelection(MAX_TAGS);
const isSavingTags = ref(false);

const editorTitle = computed(() =>
  editingField.value ? `修改${editingField.value.label}` : '修改信息',
);

const selectedTagCountText = computed(
  () => `${draftTags.value.length}/${MAX_TAGS}`,
);

const isUnauthorized = (error: unknown) =>
  axios.isAxiosError(error) && error.response?.status === 401;

const loadCurrentUser = async () => {
  try {
    loading.value = true;
    user.value = await getCurrentUser();
  } catch (error) {
    if (isUnauthorized(error)) {
      router.replace('/login');
      return;
    }
    showNotify('个人信息加载失败');
    user.value = null;
  } finally {
    loading.value = false;
  }
};

const handleLogout = async () => {
  try {
    await logout();
    user.value = null;
    router.replace('/login');
    showNotify('已退出登录', 'success');
  } catch {
    showNotify('退出失败');
  }
};

const getJoinedDays = (createTime: string) => {
  const joinedAt = new Date(createTime).getTime();
  const elapsed = Date.now() - joinedAt;
  const days = Math.max(1, Math.floor(elapsed / 86400000) + 1);
  return `已加入 ${days} 天`;
};

const displayValue = (field: UserField) => {
  if (!user.value) return '';
  const value = user.value[field.key];

  if (field.key === 'gender') {
    const text = getGenderText(value as number | null);
    return text === '未知' ? '未填写' : text;
  }
  if (field.key === 'userRole') {
    return getRoleText(value as number);
  }
  if (field.key === 'createTime') {
    return getJoinedDays(String(value));
  }
  return String(value || '未填写');
};

const openEditor = (field: UserField) => {
  if (!field.editable || !user.value) return;
  editingField.value = field;
  editingValue.value = String(user.value[field.key] ?? '');
  showEditor.value = true;
};

const confirmUpdate = async () => {
  if (!editingField.value || !user.value) return;

  const field = editingField.value.key as EditableUserField;
  const value = editingValue.value.trim();
  const payload: UpdateUserProfileRequest = {
    [field]: field === 'gender' ? Number(value) : value,
  };

  try {
    isSubmitting.value = true;
    user.value = await updateCurrentUser(payload);
    showEditor.value = false;
    showNotify('修改成功', 'success');
  } catch (error) {
    const message = axios.isAxiosError(error)
      ? error.response?.data?.description
      : '';
    showNotify(message || '修改失败');
  } finally {
    isSubmitting.value = false;
  }
};

const openTagEditor = async () => {
  if (!user.value) return;
  draftTags.value = [...user.value.userTags];

  try {
    if (categories.value.length === 0) {
      await loadCategories();
    }
    showTagEditor.value = true;
  } catch {
    showNotify('标签分类加载失败');
  }
};

const saveUserTags = async () => {
  try {
    isSavingTags.value = true;
    user.value = await updateCurrentUserTags(draftTags.value);
    showTagEditor.value = false;
    showNotify('标签保存成功', 'success');
  } catch {
    showNotify('标签保存失败');
  } finally {
    isSavingTags.value = false;
  }
};

const showMenu = ref(false);
const closeMenu = () => { showMenu.value = false; };

const showPasswordPopup = ref(false);
const currentPassword = ref('');
const newPassword = ref('');
const checkPassword = ref('');
const isUpdatingPassword = ref(false);

const openPasswordPopup = () => {
  showMenu.value = false;
  currentPassword.value = '';
  newPassword.value = '';
  checkPassword.value = '';
  showPasswordPopup.value = true;
};

const confirmUpdatePassword = async () => {
  if (!currentPassword.value || !newPassword.value || !checkPassword.value) {
    showNotify('请完整填写密码信息');
    return;
  }
  if (newPassword.value.length < 8 || newPassword.value.length > 64) {
    showNotify('新密码长度必须为 8 到 64 位');
    return;
  }
  if (newPassword.value !== checkPassword.value) {
    showNotify('两次输入的新密码不一致');
    return;
  }
  if (currentPassword.value === newPassword.value) {
    showNotify('新密码不能与当前密码相同');
    return;
  }

  try {
    isUpdatingPassword.value = true;
    await updateCurrentUserPassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
      checkPassword: checkPassword.value,
    });
    showPasswordPopup.value = false;
    showNotify('密码修改成功', 'success');
  } catch (error) {
    const message = axios.isAxiosError(error)
      ? error.response?.data?.description
      : '';
    showNotify(message || '密码修改失败');
  } finally {
    isUpdatingPassword.value = false;
  }
};

const showDeletePopup = ref(false);
const deletePassword = ref('');
const deleteStep = ref<'password' | 'confirm'>('password');
const isDeleting = ref(false);

const openDeletePopup = () => {
  showMenu.value = false;
  deletePassword.value = '';
  deleteStep.value = 'password';
  showDeletePopup.value = true;
};

const verifyPassword = () => {
  if (!deletePassword.value) {
    showNotify('请输入密码');
    return;
  }
  deleteStep.value = 'confirm';
};

const confirmDeleteAccount = async () => {
  try {
    isDeleting.value = true;
    await deleteCurrentUser(deletePassword.value);
    showDeletePopup.value = false;
    user.value = null;
    router.replace('/login');
    showNotify('账户已注销', 'success');
  } catch {
    showNotify('密码错误，注销失败');
  } finally {
    isDeleting.value = false;
  }
};

onMounted(() => {
  loadCurrentUser();
  document.addEventListener('click', closeMenu);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', closeMenu);
  if (previewAvatarUrl.value) {
    URL.revokeObjectURL(previewAvatarUrl.value);
  }
});

const onAvatarClick = () => {
  if (isUploadingAvatar.value) return;
  avatarFileInput.value?.click();
};

const onAvatarFileSelected = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;

  const maxSize = 5 * 1024 * 1024;
  if (file.size > maxSize) {
    showNotify('图片大小不能超过 5MB');
    input.value = '';
    return;
  }
  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
  if (!allowedTypes.includes(file.type)) {
    showNotify('仅支持 JPG、PNG、GIF、WebP 格式');
    input.value = '';
    return;
  }

  previewAvatarUrl.value = URL.createObjectURL(file);

  try {
    isUploadingAvatar.value = true;
    user.value = await uploadAvatar(file);
    showNotify('头像更新成功', 'success');
  } catch (error) {
    const message = axios.isAxiosError(error)
      ? error.response?.data?.description
      : '';
    showNotify(message || '头像上传失败');
  } finally {
    isUploadingAvatar.value = false;
    if (previewAvatarUrl.value) {
      URL.revokeObjectURL(previewAvatarUrl.value);
      previewAvatarUrl.value = null;
    }
    input.value = '';
  }
};
</script>

<template>
  <div class="user-page">
    <van-loading v-if="loading" class="page-loading" vertical>
      正在加载...
    </van-loading>

    <template v-else-if="user">
      <div class="top-menu">
        <van-icon
          name="ellipsis"
          size="22"
          role="button"
          tabindex="0"
          aria-label="更多操作"
          @click.stop="showMenu = !showMenu"
          @keyup.enter.stop="showMenu = !showMenu"
        />
        <div v-if="showMenu" class="dropdown-menu" @click.stop>
          <div class="dropdown-item" @click="showMenu = false; handleLogout()">退出登录</div>
          <div class="dropdown-item" @click="openPasswordPopup">修改密码</div>
          <div class="dropdown-item dropdown-item--danger" @click="openDeletePopup">注销账户</div>
        </div>
      </div>

      <div class="avatar-wrapper">
        <input
          ref="avatarFileInput"
          type="file"
          accept="image/jpeg,image/png,image/gif,image/webp"
          style="display: none"
          @change="onAvatarFileSelected"
        />
        <div class="avatar-clickable" @click="onAvatarClick">
          <van-image
            round
            width="80"
            height="80"
            fit="cover"
            :src="previewAvatarUrl || user.avatarUrl || undefined"
          >
            <template #error>
              <van-icon name="contact-o" size="38" />
            </template>
          </van-image>
          <div class="avatar-overlay">
            <van-loading
              v-if="isUploadingAvatar"
              size="24"
              color="#fff"
            />
            <van-icon v-else name="photograph" size="22" />
          </div>
        </div>
      </div>

      <van-cell-group>
        <van-cell
          v-for="field in fields.filter((item) => item.key !== 'avatarUrl')"
          :key="field.key"
          :title="field.label"
          :is-link="field.editable"
          @click="openEditor(field)"
        >
          <template #value>
            <span
              class="field-value"
              :class="{ 'email-value': field.key === 'email' }"
            >
              {{ displayValue(field) }}
            </span>
          </template>
        </van-cell>
      </van-cell-group>

      <van-cell-group v-if="isAdmin(user.userRole)" class="admin-entry">
        <van-cell
          title="用户管理"
          label="封停或恢复普通用户账号"
          icon="manager-o"
          is-link
          @click="router.push('/admin/users')"
        />
      </van-cell-group>

      <section class="user-tags-section">
        <div class="tags-heading">
          <div>
            <strong>我的标签</strong>
            <p>最多选择 {{ MAX_TAGS }} 个，用于匹配伙伴</p>
          </div>
          <van-button size="small" type="primary" plain @click="openTagEditor">
            管理标签
          </van-button>
        </div>

        <div v-if="user.userTags.length > 0" class="tag-list current-tags">
          <van-tag
            v-for="tag in user.userTags"
            :key="tag"
            size="medium"
            type="primary"
          >
            {{ tag }}
          </van-tag>
        </div>
        <van-empty v-else image-size="64" description="还没有添加标签" />
      </section>
    </template>

    <van-popup
      v-model:show="showEditor"
      position="bottom"
      round
      closeable
      class="profile-bottom-popup"
      :z-index="3000"
      teleport="body"
    >
      <div class="editor">
        <h3>{{ editorTitle }}</h3>
        <van-field
          v-if="editingField?.key !== 'gender'"
          v-model="editingValue"
          :type="editingField?.inputType ?? 'text'"
          :label="editingField?.label"
          :placeholder="`请输入${editingField?.label ?? ''}`"
          clearable
        />
        <van-radio-group
          v-else
          v-model="editingValue"
          direction="horizontal"
        >
          <van-radio name="1">男</van-radio>
          <van-radio name="2">女</van-radio>
        </van-radio-group>
        <van-button
          block
          type="primary"
          :loading="isSubmitting"
          loading-text="提交中..."
          @click="confirmUpdate"
        >
          确认修改
        </van-button>
      </div>
    </van-popup>

    <van-popup
      v-model:show="showTagEditor"
      position="bottom"
      round
      closeable
      class="profile-bottom-popup"
      :z-index="3000"
      teleport="body"
      :style="{ height: '80%' }"
    >
      <div class="tag-editor">
        <div class="tag-editor-heading">
          <div>
            <h3>选择个人标签</h3>
            <p>选择最能代表你的 {{ MAX_TAGS }} 个标签</p>
          </div>
          <span>{{ selectedTagCountText }}</span>
        </div>

        <div v-if="draftTags.length > 0" class="tag-list draft-tags">
          <van-tag
            v-for="tag in draftTags"
            :key="tag"
            closeable
            size="medium"
            type="primary"
            @close="toggleUserTag(tag)"
          >
            {{ tag }}
          </van-tag>
        </div>

        <div class="tag-category-list">
          <section
            v-for="category in categories"
            :key="category.category"
            class="tag-category"
          >
            <h4>{{ category.category }}</h4>
            <div class="tag-list tag-options">
              <button
                v-for="tag in category.tags"
                :key="tag"
                class="tag-option"
                :class="{ selected: isTagSelected(tag) }"
                type="button"
                @click="toggleUserTag(tag)"
              >
                {{ tag }}
              </button>
            </div>
          </section>
        </div>

        <div class="tag-editor-footer">
          <van-button
            block
            type="primary"
            :loading="isSavingTags"
            loading-text="保存中..."
            @click="saveUserTags"
          >
            保存标签
          </van-button>
        </div>
      </div>
  </van-popup>

  <van-popup
    v-model:show="showPasswordPopup"
    position="bottom"
    round
    closeable
    class="profile-bottom-popup"
    :z-index="3000"
    teleport="body"
  >
    <div class="password-popup">
      <h3>修改密码</h3>
      <p class="password-desc">修改后请使用新密码登录</p>
      <van-field
        v-model="currentPassword"
        type="password"
        label="当前密码"
        placeholder="请输入当前密码"
        maxlength="64"
        clearable
      />
      <van-field
        v-model="newPassword"
        type="password"
        label="新密码"
        placeholder="请输入 8 到 64 位新密码"
        maxlength="64"
        clearable
      />
      <van-field
        v-model="checkPassword"
        type="password"
        label="确认密码"
        placeholder="请再次输入新密码"
        maxlength="64"
        clearable
        @keyup.enter="confirmUpdatePassword"
      />
      <van-button
        block
        type="primary"
        :loading="isUpdatingPassword"
        loading-text="修改中..."
        @click="confirmUpdatePassword"
      >
        确认修改
      </van-button>
    </div>
  </van-popup>

  <van-popup
    v-model:show="showDeletePopup"
    position="bottom"
    round
    closeable
    class="profile-bottom-popup"
    :z-index="3000"
    teleport="body"
  >
    <div class="delete-popup">
      <template v-if="deleteStep === 'password'">
        <h3>注销账户</h3>
        <p class="delete-desc">请输入密码以验证身份</p>
        <van-field
          v-model="deletePassword"
          type="password"
          label="密码"
          placeholder="请输入当前密码"
          clearable
          @keyup.enter="verifyPassword"
        />
        <van-button block type="primary" @click="verifyPassword">
          下一步
        </van-button>
      </template>

      <template v-else>
        <h3>确认注销</h3>
        <p class="delete-desc">注销后账户数据将无法恢复，确定要继续吗？</p>
        <van-button
          block
          type="danger"
          :loading="isDeleting"
          loading-text="注销中..."
          @click="confirmDeleteAccount"
        >
          我确定注销账户
        </van-button>
        <van-button block plain @click="showDeletePopup = false" style="margin-top: 10px">
          再想想
        </van-button>
      </template>
    </div>
  </van-popup>
 </div>
</template>

<style scoped>
.user-page {
  position: relative;
  height: calc(100dvh - var(--van-nav-bar-height, 46px));
  padding-bottom: 62px;
  overflow-y: auto;
  overscroll-behavior-y: none;
  -webkit-overflow-scrolling: touch;
  background: #f7f8fa;
  box-sizing: border-box;
  scrollbar-width: none;
}

.user-page::-webkit-scrollbar {
  display: none;
}

.page-loading {
  padding-top: 100px;
}

.avatar-wrapper {
  position: relative;
  z-index: 2001;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px 0;
  background: #fff;
}

.avatar-clickable {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.avatar-clickable:active {
  opacity: 0.7;
  transform: scale(0.95);
  transition: opacity 0.15s, transform 0.15s;
}

.avatar-overlay {
  position: absolute;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  color: #fff;
  background: rgb(25 137 250 / 90%);
  border: 2px solid #fff;
  border-radius: 50%;
  box-sizing: border-box;
}

.avatar-hint {
  color: #969799;
  font-size: 12px;
}

.field-value {
  display: block;
}

.email-value {
  max-width: 180px;
  margin-left: auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-tags-section {
  margin-top: 12px;
  padding: 16px;
  background: #fff;
}

.admin-entry {
  margin-top: 12px;
}

.tags-heading,
.tag-editor-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.tags-heading p,
.tag-editor-heading p {
  margin: 5px 0 0;
  color: #969799;
  font-size: 12px;
}

.current-tags {
  margin-top: 16px;
}

.top-menu {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 2002;
}

.top-menu .van-icon {
  display: block;
  padding: 4px;
  color: #646566;
  cursor: pointer;
}

.dropdown-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 6px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  overflow: hidden;
}

.dropdown-item {
  padding: 10px 20px;
  color: #323233;
  font-size: 14px;
  white-space: nowrap;
  cursor: pointer;
}

.dropdown-item:active {
  background: #f2f3f5;
}

.dropdown-item--danger {
  color: #ee0a24;
}

.dropdown-item--danger:active {
  background: #fff0f0;
}

.editor {
  padding: 20px 16px calc(24px + env(safe-area-inset-bottom, 0px));
}

.editor h3 {
  margin: 0 0 16px;
  text-align: center;
}

.editor .van-radio-group {
  justify-content: center;
  padding: 16px 0;
}

.editor .van-button {
  margin-top: 20px;
}

.tag-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.tag-editor-heading {
  padding: 20px 48px 14px 16px;
}

.tag-editor-heading h3 {
  margin: 0;
}

.tag-editor-heading span {
  color: #1989fa;
  font-weight: 600;
}

.draft-tags {
  padding: 0 16px 14px;
}

.tag-category-list {
  flex: 1;
  padding: 0 16px calc(90px + env(safe-area-inset-bottom, 0px));
  overflow-y: auto;
}

.tag-category {
  margin-bottom: 20px;
}

.tag-category h4 {
  margin: 0 0 12px;
}

.tag-editor-footer {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 12px 16px calc(20px + env(safe-area-inset-bottom, 0px));
  background: #fff;
  box-shadow: 0 -2px 8px rgb(0 0 0 / 5%);
}

.password-popup,
.delete-popup {
  padding: 20px 16px calc(24px + env(safe-area-inset-bottom, 0px));
}

.password-popup h3,
.delete-popup h3 {
  margin: 0 0 8px;
  text-align: center;
}

.password-desc,
.delete-desc {
  margin: 0 0 16px;
  color: #969799;
  font-size: 13px;
  text-align: center;
}

.password-popup .van-button,
.delete-popup .van-button {
  margin-top: 16px;
}
</style>
