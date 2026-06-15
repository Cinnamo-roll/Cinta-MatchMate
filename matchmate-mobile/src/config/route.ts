import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('../pages/Index.vue'),
    meta: { title: 'MatchMate', lockScroll: true },
  },
  {
    path: '/discover',
    component: () => import('../pages/Discover.vue'),
    meta: { title: '发现' },
  },
  {
    path: '/team',
    component: () => import('../pages/Team.vue'),
    meta: { title: '消息', lockScroll: true },
  },
  {
    path: '/user',
    component: () => import('../pages/User.vue'),
    meta: { title: '我的' },
  },
  {
    path: '/discover/card-ledger',
    component: () => import('../pages/CardLedger.vue'),
    meta: { title: '打牌记账本', showBack: true, hideTabbar: true, lockScroll: true },
  },
  {
    path: '/card-room/:id',
    component: () => import('../pages/CardRoom.vue'),
    meta: { title: '房间', showBack: true, hideTabbar: true, lockScroll: true, backTarget: '/discover' },
  },
  {
    path: '/chat/:id',
    component: () => import('../pages/ChatDetail.vue'),
    meta: { title: '聊天', hideNavbar: true, hideTabbar: true, lockScroll: true },
  },
  {
    path: '/search',
    component: () => import('../pages/Search.vue'),
    meta: { title: '搜索伙伴', showBack: true, hideTabbar: true, lockScroll: true },
  },
  {
    path: '/admin/users',
    component: () => import('../pages/AdminUsers.vue'),
    meta: { title: '用户管理', showBack: true, hideTabbar: true },
  },
  {
    path: '/admin/registrations',
    component: () => import('../pages/AdminRegistrations.vue'),
    meta: { title: '注册审核', showBack: true, hideTabbar: true },
  },
  {
    path: '/login',
    component: () => import('../pages/Login.vue'),
    meta: { title: '登录', lockScroll: true },
  },
  {
    path: '/register',
    component: () => import('../pages/Register.vue'),
    meta: { title: '注册', showBack: true, hideTabbar: true, lockScroll: true },
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('../pages/NotFound.vue'),
    meta: { title: '页面不存在', hideTabbar: true },
  },
];

export default routes;
