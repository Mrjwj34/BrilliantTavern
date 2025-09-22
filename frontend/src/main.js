import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { setRouter } from '@/utils/request'

// 导入全局样式
import '@/styles/global.scss'

// 设置router实例到request工具中
setRouter(router)

// 创建Vue应用
const app = createApp(App)

// 使用路由
app.use(router)

// 挂载应用
app.mount('#app')
