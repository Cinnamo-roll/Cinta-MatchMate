<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { register } from '../api/matchmate';
import { useNotify } from '../composables/useNotify';
import { getRequestErrorMessage } from '../utils/http';

const router = useRouter();
const { showNotify } = useNotify();

const registering = ref(false);
const registerForm = ref({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
});

const submitRegister = async () => {
  const { userAccount, userPassword, checkPassword } = registerForm.value;

  if (!userAccount || !userPassword || !checkPassword) {
    showNotify('请填写所有字段');
    return;
  }
  if (userPassword !== checkPassword) {
    showNotify('两次密码不一致');
    return;
  }

  try {
    registering.value = true;
    const result = await register({
      userAccount: userAccount.trim(),
      userPassword,
      checkPassword,
    });
    showNotify(result.message || '注册成功', 'success');
    router.replace('/login');
  } catch (error) {
    showNotify(getRequestErrorMessage(error, '注册失败，请检查账号或密码'));
  } finally {
    registering.value = false;
  }
};
</script>

<template>
  <div class="auth-page">
    <van-icon name="contact-o" size="56" color="var(--app-primary)" />
    <h2>注册 MatchMate</h2>
    <p>创建账号，找到志同道合的伙伴</p>

    <van-field
      v-model="registerForm.userAccount"
      label="账号"
      placeholder="4-16位字母或数字"
      autocomplete="username"
      clearable
    />
    <van-field
      v-model="registerForm.userPassword"
      type="password"
      label="密码"
      placeholder="至少8位"
      autocomplete="new-password"
      clearable
    />
    <van-field
      v-model="registerForm.checkPassword"
      type="password"
      label="确认密码"
      placeholder="再次输入密码"
      autocomplete="new-password"
      clearable
      @keyup.enter="submitRegister"
    />
    <van-button
      block
      round
      type="primary"
      :loading="registering"
      loading-text="注册中..."
      @click="submitRegister"
    >
      注册
    </van-button>

    <p class="auth-link">
      已有账号？<span @click="router.replace('/login')">去登录</span>
    </p>
  </div>
</template>
