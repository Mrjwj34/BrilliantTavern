import axios from 'axios'
import { storage, tokenUtils } from './index'
import { notification } from './notification'

// 用于在拦截器中访问router的变量
let routerInstance = null

// 设置router实例（在main.js中调用）
export const setRouter = (router) => {
  routerInstance = router
}

// 创建axios实例
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    // 添加token到请求头
    const token = storage.get('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    const { data } = response
    
    // 如果是统一的API响应格式
    if (data.code !== undefined) {
      if (data.code === 200) {
        return data
      } else {
        // 业务错误
        const error = new Error(data.message || '请求失败')
        error.code = data.code
        return Promise.reject(error)
      }
    }
    
    // 直接返回数据
    return data
  },
  error => {
    console.error('响应错误:', error)
    
    // 处理HTTP错误状态码
    if (error.response) {
      const { status, data } = error.response
      
      switch (status) {
        case 401:
          // 未授权，清除token并跳转到登录页（使用router而不是location.href避免页面刷新）
          console.log('收到401响应，清除认证信息')
          tokenUtils.clearAuth()
          notification.error('登录已过期，请重新登录')
          if (routerInstance) {
            routerInstance.push('/login')
          } else {
            console.warn('Router实例未设置，使用location跳转')
            window.location.href = '/login'
          }
          break
        case 403:
          error.message = '权限不足'
          notification.error('权限不足')
          break
        case 404:
          error.message = '请求的资源不存在'
          notification.error('请求的资源不存在')
          break
        case 500:
          error.message = '服务器内部错误'
          notification.error('服务器内部错误，请稍后重试')
          break
        default:
          error.message = data?.message || `请求失败 (${status})`
          notification.error(error.message)
      }
    } else if (error.request) {
      error.message = '网络连接失败，请检查网络'
      notification.error('网络连接失败，请检查网络')
    } else {
      error.message = error.message || '请求配置错误'
      notification.error(error.message)
    }
    
    return Promise.reject(error)
  }
)

export default request
