import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import './style.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// A hard refresh keeps the JWT (persisted in localStorage) but loses the in-memory user
// profile, since only the token and active tenant id are persisted — without this, the
// tenant switcher and active-tenant badge would render empty until the next login.
const auth = useAuthStore()
if (auth.token) {
  auth.fetchProfile()
}

app.mount('#app')
