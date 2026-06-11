<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { login } from '../api/matchmate';
import { useNotify } from '../composables/useNotify';

const router = useRouter();
const { showNotify } = useNotify();

const loggingIn = ref(false);
const loginForm = ref({
  userAccount: '',
  userPassword: '',
});

const submitLogin = async () => {
  if (!loginForm.value.userAccount || !loginForm.value.userPassword) {
    showNotify('请输入账号和密码');
    return;
  }

  try {
    loggingIn.value = true;
    await login(loginForm.value);
    showNotify('登录成功', 'success');
    router.replace('/user');
  } catch {
    loginForm.value.userPassword = '';
    showNotify('账号或密码错误');
  } finally {
    loggingIn.value = false;
  }
};
</script>

<template>
  <div class="auth-page">
    <van-icon name="contact-o" size="56" color="#1989fa" />
    <h2>登录 MatchMate</h2>
    <p>账号不区分大小写</p>

    <van-field
      v-model="loginForm.userAccount"
      label="账号"
      placeholder="请输入账号"
      clearable
    />
    <van-field
      v-model="loginForm.userPassword"
      type="password"
      label="密码"
      placeholder="请输入密码"
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
