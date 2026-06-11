import {createApp} from 'vue'
import App from './App.vue'
import {createRouter, createWebHistory} from 'vue-router'
import routes from "./config/route.ts";
import './styles/reset.css'
import './styles/auth.css'
import './styles/tag.css'

const app = createApp(App)
const router = createRouter({
    history: createWebHistory(),
    routes,
})

app.use(router)
app.mount('#app')


