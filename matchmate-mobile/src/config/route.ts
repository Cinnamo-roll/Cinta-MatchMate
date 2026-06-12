import Index from '../pages/Index.vue';
import Login from '../pages/Login.vue';
import Register from '../pages/Register.vue';
import Search from '../pages/Search.vue';
import Team from '../pages/Team.vue';
import User from '../pages/User.vue';
import ChatDetail from '../pages/ChatDetail.vue';

const routes = [
  { path: '/', component: Index, meta: { title: 'MatchMate' } },
  { path: '/team', component: Team, meta: { title: '消息' } },
  { path: '/user', component: User, meta: { title: '我的', lockScroll: true } },
  {
    path: '/chat/:id',
    component: ChatDetail,
    meta: { title: '聊天', hideNavbar: true, hideTabbar: true, lockScroll: true },
  },
  {
    path: '/search',
    component: Search,
    meta: { title: '搜索伙伴', showBack: true, hideTabbar: true },
  },
  {
    path: '/login',
    component: Login,
    meta: { title: '登录', tabbar: 'user', lockScroll: true },
  },
  {
    path: '/register',
    component: Register,
    meta: { title: '注册', showBack: true, hideTabbar: true, lockScroll: true },
  },
];

export default routes;
