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
  }
}

// 角色卡相关API
export const characterCardAPI = {
  // 创建角色卡
  create(cardData) {
    return request.post('/character-cards', cardData)
  },

  // 获取公开角色卡列表
  getPublicCards(params) {
    return request.get('/character-cards/public', { params })
  },

  // 获取热门角色卡
  getPopularCards(params) {
    return request.get('/character-cards/popular', { params })
  },

  // 获取最新角色卡
  getLatestCards(params) {
    return request.get('/character-cards/latest', { params })
  },

  // 获取我的角色卡
  getMyCards(params) {
    return request.get('/character-cards/my', { params })
  },

  // 获取我点赞的角色卡
  getLikedCards(params) {
    return request.get('/character-cards/liked', { params })
  },

  // 获取指定用户的角色卡
  getUserCards(userId, params) {
    return request.get(`/character-cards/user/${userId}`, { params })
  },

  // 搜索角色卡
  searchCards(params) {
    return request.get('/character-cards/search', { params })
  },

  // 游标分页获取角色市场角色卡
  getMarketCards(params) {
    return request.get('/character-cards/market', { params })
  },

  // 获取角色卡详情
  getCardDetail(cardId) {
    return request.get(`/character-cards/${cardId}`)
  },

  // 更新角色卡
  update(cardId, cardData) {
    return request.put(`/character-cards/${cardId}`, cardData)
  },

  // 删除角色卡
  delete(cardId) {
    return request.delete(`/character-cards/${cardId}`)
  },

  // 点赞/取消点赞角色卡
  toggleLike(cardId) {
    return request.post(`/character-cards/${cardId}/like`)
  }
}

// 文件上传相关API
export const uploadAPI = {
  // 上传头像
  uploadAvatar(file) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/upload/avatar', formData, {
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
    return request.get('/voice/list')
  }
}

// 语音聊天相关API
export const voiceChatAPI = {
  createSession(payload) {
    return request.post('/voice-chat/sessions', payload)
  },

  getHistory(params) {
    return request.get('/voice-chat/history', { params })
  },

  closeSession(sessionId) {
    return request.post(`/voice-chat/sessions/${sessionId}/close`)
  },

  checkSessionStatus(sessionId) {
    return request.get(`/voice-chat/sessions/${sessionId}/status`)
  }
}

// TTS语音相关API
export const ttsAPI = {
  // 创建音色
  createVoice(data) {
    const formData = new FormData()
    formData.append('userId', data.userId)
    formData.append('name', data.name)
    formData.append('description', data.description || '')
    formData.append('audio', data.audioFile)
    formData.append('referenceText', data.referenceText)
    formData.append('isPublic', data.isPublic)
    
    return request.post('/tts/reference', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // 删除音色
  deleteVoice(voiceId, userId) {
    return request.delete(`/tts/reference/${voiceId}`, {
      params: { userId }
    })
  },

  // 获取用户音色列表
  getUserVoices(userId) {
    return request.get('/tts/reference', {
      params: { userId }
    })
  },

  // 获取公开音色列表
  getPublicVoices(params = {}) {
    return request.get('/tts/reference/public', { params })
  },

  // 游标分页获取音色市场
  getMarketVoices(params = {}) {
    return request.get('/tts/reference/market', { params })
  },

  // 获取音色详情
  getVoice(voiceId, userId) {
    return request.get(`/tts/reference/${voiceId}`, {
      params: { userId }
    })
  },

  // 更新音色信息
  updateVoice(voiceId, userId, data) {
    const params = { userId }
    if (data.name) params.name = data.name
    if (data.description !== undefined) params.description = data.description
    if (data.isPublic !== undefined) params.isPublic = data.isPublic
    
    return request.put(`/tts/reference/${voiceId}`, null, { params })
  },

  // 搜索音色
  searchVoices(keyword, userId, includePublic = true, sort = 'newest') {
    return request.get('/tts/reference/search', {
      params: {
        keyword,
        userId,
        includePublic,
        sort
      }
    })
  },

  likeVoice(voiceId, userId) {
    return request.post(`/tts/reference/${voiceId}/like`, null, {
      params: { userId }
    })
  },

  unlikeVoice(voiceId, userId) {
    return request.delete(`/tts/reference/${voiceId}/like`, {
      params: { userId }
    })
  },

  // 语音合成
  generateSpeech(text, voiceId) {
    const params = { text }
    if (voiceId) {
      params.voiceId = voiceId
    }
    
    return request.post('/tts/reference/speak', null, {
      params,
      responseType: 'blob' // 返回音频blob
    })
  }
}
