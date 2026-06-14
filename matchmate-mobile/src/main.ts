import {createApp} from 'vue'
import App from './App.vue'
import {createRouter, createWebHistory} from 'vue-router'
import routes from "./config/route.ts";
import './styles/theme.css'
import './styles/reset.css'
import './styles/auth.css'
import './styles/tag.css'

const installZoomGuards = () => {
    const blockZoom = (event: Event) => {
        event.preventDefault()
    }

    document.addEventListener('gesturestart', blockZoom, { passive: false })
    document.addEventListener('gesturechange', blockZoom, { passive: false })
    document.addEventListener('gestureend', blockZoom, { passive: false })

    document.addEventListener('touchmove', (event) => {
        if (event.touches.length > 1) event.preventDefault()
    }, { passive: false })

    window.addEventListener('wheel', (event) => {
        if (event.ctrlKey || event.metaKey) event.preventDefault()
    }, { passive: false })

    window.addEventListener('keydown', (event) => {
        if (!(event.ctrlKey || event.metaKey)) return
        if (['+', '-', '=', '0'].includes(event.key)) {
            event.preventDefault()
        }
    })
}

installZoomGuards()

const app = createApp(App)
const router = createRouter({
    history: createWebHistory(),
    routes,
})

router.afterEach((to) => {
    const pageTitle = String(to.meta.title ?? 'MatchMate')
    document.title = pageTitle === 'MatchMate' ? pageTitle : `${pageTitle} · MatchMate`
})

app.use(router)
app.mount('#app')


