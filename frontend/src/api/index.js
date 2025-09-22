import request from '@/utils/request'

// 用户认证相关API
export const authAPI = {
  // 用户登录
  login(loginData) {
    return request.post('/auth/login', loginData)
  },

  // 用户注册
  register(registerData) {
    return request.post('/auth/register', registerData)
  },

  // 用户登出
  logout() {
    return request.post('/auth/logout')
  },

  // 获取用户信息
  getUserInfo() {
    return request.get('/test/profile')
  }
}

// 测试相关API
export const testAPI = {
  // 公开接口测试
  publicTest() {
    return request.get('/test/public')
  }
}
