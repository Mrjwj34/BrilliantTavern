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

// 角色卡相关API
export const characterCardAPI = {
  // 创建角色卡
  create(cardData) {
    return request.post('/api/character-cards', cardData)
  },

  // 获取公开角色卡列表
  getPublicCards(params) {
    return request.get('/api/character-cards/public', { params })
  },

  // 获取热门角色卡
  getPopularCards(params) {
    return request.get('/api/character-cards/popular', { params })
  },

  // 获取最新角色卡
  getLatestCards(params) {
    return request.get('/api/character-cards/latest', { params })
  },

  // 获取我的角色卡
  getMyCards(params) {
    return request.get('/api/character-cards/my', { params })
  },

  // 获取我点赞的角色卡
  getLikedCards(params) {
    return request.get('/api/character-cards/liked', { params })
  },

  // 获取指定用户的角色卡
  getUserCards(userId, params) {
    return request.get(`/api/character-cards/user/${userId}`, { params })
  },

  // 搜索角色卡
  searchCards(params) {
    return request.get('/api/character-cards/search', { params })
  },

  // 获取角色卡详情
  getCardDetail(cardId) {
    return request.get(`/api/character-cards/${cardId}`)
  },

  // 更新角色卡
  update(cardId, cardData) {
    return request.put(`/api/character-cards/${cardId}`, cardData)
  },

  // 删除角色卡
  delete(cardId) {
    return request.delete(`/api/character-cards/${cardId}`)
  },

  // 点赞/取消点赞角色卡
  toggleLike(cardId) {
    return request.post(`/api/character-cards/${cardId}/like`)
  }
}

// 文件上传相关API
export const uploadAPI = {
  // 上传头像
  uploadAvatar(file) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/api/upload/avatar', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  }
}

// 语音相关API
export const voiceAPI = {
  // 获取可用语音列表
  getVoiceList() {
    return request.get('/api/voice/list')
  }
}
