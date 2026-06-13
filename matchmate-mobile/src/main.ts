import {createApp} from 'vue'
import App from './App.vue'
import {createRouter, createWebHistory} from 'vue-router'
import routes from "./config/route.ts";
import './styles/reset.css'
import './styles/auth.css'
import './styles/tag.css'

const disablePageZoom = () => {
    let lastTouchEnd = 0

    document.addEventListener('touchstart', (event) => {
        if (event.touches.length > 1) {
            event.preventDefault()
        }
    }, { passive: false })

    document.addEventListener('touchmove', (event) => {
        if (event.touches.length > 1) {
            event.preventDefault()
        }
    }, { passive: false })

    document.addEventListener('touchend', (event) => {
        const now = Date.now()
        if (now - lastTouchEnd <= 300) {
            event.preventDefault()
        }
        lastTouchEnd = now
    }, { passive: false })

    document.addEventListener('dblclick', (event) => {
        event.preventDefault()
    }, { passive: false })

    ;['gesturestart', 'gesturechange', 'gestureend'].forEach((name) => {
        document.addEventListener(name, (event) => {
            event.preventDefault()
        }, { passive: false })
    })
}

disablePageZoom()

const app = createApp(App)
const router = createRouter({
    history: createWebHistory(),
    routes,
})

app.use(router)
app.mount('#app')


