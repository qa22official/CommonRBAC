import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { resetBusinessRoutes } from './router/dynamic'
import { permissionDirective } from './directives/perm'
import { useAuthStore } from './stores/auth'
import { setSessionExpiredHandler } from './utils/auth-session'
import './styles.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(ElementPlus, { locale: zhCn })
app.directive('perm', permissionDirective)

const auth = useAuthStore(pinia)

setSessionExpiredHandler(() => {
  auth.clearAuth()
  resetBusinessRoutes()
  void router.replace({ path: '/login', query: { reason: 'expired' } })
})

app.use(router)
app.mount('#app')
