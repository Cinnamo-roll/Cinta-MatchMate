<script setup lang="ts">
import { computed, ref } from 'vue';
import { showFailToast, showSuccessToast } from 'vant';
import type { User } from '../models/user';

type EditableUserField = Exclude<keyof User, 'id'>;

type UserField = {
  key: keyof User;
  label: string;
  editable?: boolean;
  inputType?: 'text' | 'number' | 'tel' | 'email' | 'url';
};

const user = ref<User>({
  id: 1,
  username: 'MatchMate 用户',
  userAccount: 'matchmate',
  avatarUrl: 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg',
  gender: 1,
  phone: '13800138000',
  email: 'matchmate@example.com',
  userStatus: 0,
  userRole: 0,
  userTags: ['Java', 'Vue', 'Spring Boot'],
  createTime: new Date(),
});

const fields: UserField[] = [
  { key: 'userAccount', label: '账号' },
  { key: 'username', label: '用户名', editable: true },
  { key: 'avatarUrl', label: '头像地址', editable: true, inputType: 'url' },
  { key: 'gender', label: '性别', editable: true },
  { key: 'phone', label: '手机号', editable: true, inputType: 'tel' },
  { key: 'email', label: '邮箱', editable: true, inputType: 'email' },
  { key: 'createTime', label: '创建时间' },
];

const showEditor = ref(false);
const editingField = ref<UserField | null>(null);
const editingValue = ref('');
const isSubmitting = ref(false);

const editorTitle = computed(() =>
  editingField.value ? `修改${editingField.value.label}` : '修改信息',
);

const editorPlaceholder = computed(() => {
  return `请输入${editingField.value?.label ?? ''}`;
});

const formatDate = (date: Date) => {
  const pad = (value: number) => String(value).padStart(2, '0');

  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ].join('-') + ` ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
};

const displayValue = (field: UserField) => {
  const value = user.value[field.key];

  if (field.key === 'gender') {
    return value === 1 ? '男' : '女';
  }

  if (field.key === 'createTime') {
    return formatDate(value as Date);
  }

  return String(value || '未填写');
};

const avatarField = fields.find((field) => field.key === 'avatarUrl')!;

const openEditor = (field: UserField) => {
  if (!field.editable) {
    return;
  }

  const value = user.value[field.key];
  editingField.value = field;
  editingValue.value = field.key === 'createTime'
      ? formatDate(value as Date)
      : String(value);
  showEditor.value = true;
};

const parseEditingValue = (field: EditableUserField) => {
  const value = editingValue.value.trim();

  if (field === 'gender') {
    return Number(value);
  }

  if (field === 'createTime') {
    const dateValue = new Date(value.replace(' ', 'T'));

    if (Number.isNaN(dateValue.getTime())) {
      throw new Error('请输入有效的日期时间');
    }

    return dateValue;
  }

  return value;
};

const mockUpdateUser = async (field: EditableUserField, value: User[EditableUserField]) => {
  await new Promise((resolve) => setTimeout(resolve, 500));

  console.info('模拟更新用户信息', {
    userId: user.value.id,
    field,
    value,
  });
};

const confirmUpdate = async () => {
  if (!editingField.value || editingField.value.key === 'id') {
    return;
  }

  try {
    const field = editingField.value.key;
    const value = parseEditingValue(field);

    isSubmitting.value = true;
    await mockUpdateUser(field, value);
    Object.assign(user.value, { [field]: value });
    showEditor.value = false;
    showSuccessToast('修改成功');
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : '修改失败');
  } finally {
    isSubmitting.value = false;
  }
};
</script>

<template>
  <div class="user-page">
    <div class="avatar-wrapper" @click="openEditor(avatarField)">
      <van-image
        round
        width="80"
        height="80"
        :src="user.avatarUrl"
      />
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

    <van-popup
      v-model:show="showEditor"
      position="bottom"
      round
      closeable
    >
      <div class="editor">
        <h3>{{ editorTitle }}</h3>

        <van-field
          v-if="editingField?.key !== 'gender'"
          v-model="editingValue"
          :type="editingField?.inputType ?? 'text'"
          :label="editingField?.label"
          :placeholder="editorPlaceholder"
          clearable
          autofocus
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
  </div>
</template>

<style scoped>
.user-page {
  padding-bottom: 50px;
}

.avatar-wrapper {
  display: flex;
  justify-content: center;
  padding: 24px 0;
  cursor: pointer;
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

.editor {
  padding: 20px 16px 24px;
}

.editor h3 {
  margin: 0 0 16px;
  text-align: center;
}

.editor .van-button {
  margin-top: 20px;
}
</style>
