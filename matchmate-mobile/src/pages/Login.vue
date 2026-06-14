<script setup lang="ts">
import axios from 'axios';
import { ref } from 'vue';
import { showConfirmDialog } from 'vant';
import { useRoute, useRouter } from 'vue-router';
import { login } from '../api/matchmate';
import { useNotify } from '../composables/useNotify';
import { getRequestErrorMessage } from '../utils/http';

const router = useRouter();
const route = useRoute();
const { showNotify } = useNotify();

const loggingIn = ref(false);
const loginForm = ref({
  userAccount: '',
  userPassword: '',
});

const LOGIN_CONFLICT_CODE = 40900;

const isLoginConflict = (error: unknown) =>
  axios.isAxiosError(error) && error.response?.data?.code === LOGIN_CONFLICT_CODE;

const redirectAfterLogin = () => {
  showNotify('登录成功', 'success');
  const redirect = typeof route.query.redirect === 'string'
    && route.query.redirect.startsWith('/')
    && !route.query.redirect.startsWith('//')
    ? route.query.redirect
    : '/user';
  router.replace(redirect);
};

const doLogin = async (forceLogin = false) => {
  await login({
    userAccount: loginForm.value.userAccount.trim(),
    userPassword: loginForm.value.userPassword,
    forceLogin,
  });
  redirectAfterLogin();
};

const confirmTakeoverLogin = async (error: unknown) => {
  try {
    await showConfirmDialog({
      title: '账号已在其他设备登录',
      message: getRequestErrorMessage(
        error,
        '该账号当前已有活跃登录会话。继续登录将使原设备下线，是否接管本次登录？',
      ),
      theme: 'round-button',
      cancelButtonText: '取消',
      confirmButtonText: '接管登录',
      confirmButtonColor: '#5968e9',
    });
  } catch {
    return;
  }

  try {
    await doLogin(true);
  } catch (takeoverError) {
    loginForm.value.userPassword = '';
    showNotify(getRequestErrorMessage(takeoverError, '接管登录失败，请重新输入密码'));
  }
};

const submitLogin = async () => {
  if (!loginForm.value.userAccount || !loginForm.value.userPassword) {
    showNotify('请输入账号和密码');
    return;
  }

  try {
    loggingIn.value = true;
    await doLogin();
  } catch (error) {
    if (isLoginConflict(error)) {
      await confirmTakeoverLogin(error);
      return;
    }
    loginForm.value.userPassword = '';
    showNotify(getRequestErrorMessage(error, '账号或密码错误'));
  } finally {
    loggingIn.value = false;
  }
};
</script>

<template>
  <div class="auth-page">
    <van-icon name="contact-o" size="56" color="var(--app-primary)" />
    <h2>登录 MatchMate</h2>
    <p>账号不区分大小写</p>

    <van-field
      v-model="loginForm.userAccount"
      label="账号"
      placeholder="请输入账号"
      autocomplete="username"
      clearable
    />
    <van-field
      v-model="loginForm.userPassword"
      type="password"
      label="密码"
      placeholder="请输入密码"
      autocomplete="current-password"
      clearable
      @keyup.enter="submitLogin"
    />
    <van-button
      block
      round
      type="primary"
      :loading="loggingIn"
      loading-text="登录中..."
      @click="submitLogin"
    >
      登录
    </van-button>

    <p class="auth-link">
      没有账号？<span @click="router.push('/register')">去注册</span>
    </p>
  </div>
</template>
