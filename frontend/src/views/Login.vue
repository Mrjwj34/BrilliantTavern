<template>
  <div class="login-container">
    <!-- 左侧内容 -->
    <div class="login-content">
      <div class="login-form-container">
        <!-- 头部 -->
        <div class="login-header">
          <h1 class="brand-title">BrilliantTavern</h1>
          <p class="brand-subtitle">欢迎回来</p>
        </div>

        <!-- 登录表单 -->
        <form @submit.prevent="handleLogin" class="login-form">
          <div class="form-group">
            <label for="username" class="form-label">用户名</label>
            <input
              id="username"
              v-model="formData.username"
              type="text"
              class="form-input"
              :class="{ 'error': errors.username }"
              placeholder="输入您的用户名"
              @blur="validateField('username')"
              @input="clearError('username'); clearError('general')"
            />
            <div v-if="errors.username" class="error-text">
              {{ errors.username }}
            </div>
          </div>

          <div class="form-group">
            <label for="password" class="form-label">密码</label>
            <div class="password-wrapper">
              <input
                id="password"
                v-model="formData.password"
                :type="showPassword ? 'text' : 'password'"
                class="form-input"
                :class="{ 'error': errors.password }"
                placeholder="输入您的密码"
                @blur="validateField('password')"
                @input="clearError('password'); clearError('general')"
              />
              <button
                type="button"
                class="password-toggle"
                @click="showPassword = !showPassword"
              >
                <svg v-if="showPassword" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                </svg>
                <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
              </button>
            </div>
            <div v-if="errors.password" class="error-text">
              {{ errors.password }}
            </div>
          </div>

          <!-- 通用错误显示 -->
          <div v-if="errors.general" class="form-group">
            <div class="error-text general-error">
              {{ errors.general }}
            </div>
          </div>

          <button
            type="submit"
            class="login-button"
            :disabled="loading || !isFormValid"
          >
            <div v-if="loading" class="loading-spinner"></div>
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>

        <!-- 底部链接 -->
        <div class="login-footer">
          <p class="footer-text">
            还没有账户？
            <router-link to="/register" class="footer-link">立即注册</router-link>
          </p>
        </div>
      </div>
    </div>

    <!-- 右侧装饰 -->
    <div class="login-decoration">
      <div class="decoration-content">
        <h2 class="decoration-title">开启AI对话之旅</h2>
        <p class="decoration-text">
          与虚拟角色进行自然对话，探索无限可能的交流体验。
        </p>
        <div class="decoration-features">
          <div class="feature-item">
            <div class="feature-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <span>智能对话</span>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
              </svg>
            </div>
            <span>个性角色</span>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                <line x1="12" y1="19" x2="12" y2="23"/>
                <line x1="8" y1="23" x2="16" y2="23"/>
              </svg>
            </div>
            <span>语音交互</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { authAPI } from '@/api'
import { storage, validation, notification } from '@/utils'

export default {
  name: 'Login',
  setup() {
    const router = useRouter()
    
    // 响应式数据
    const formData = ref({
      username: '',
      password: ''
    })
    
    const errors = ref({})
    const loading = ref(false)
    const showPassword = ref(false)
    
    // 计算属性
    const isFormValid = computed(() => {
      return formData.value.username.trim() && 
             formData.value.password.trim() && 
             Object.keys(errors.value).length === 0
    })
    
    // 方法
    const validateField = (field) => {
      const value = formData.value[field]
      
      switch (field) {
        case 'username':
          if (!validation.isRequired(value)) {
            errors.value.username = '请输入用户名'
          } else if (!validation.isUsername(value)) {
            errors.value.username = '用户名格式不正确（3-50位字母数字下划线）'
          } else {
            delete errors.value.username
          }
          break
          
        case 'password':
          if (!validation.isRequired(value)) {
            errors.value.password = '请输入密码'
          } else if (!validation.isPassword(value)) {
            errors.value.password = '密码长度至少6位'
          } else {
            delete errors.value.password
          }
          break
      }
    }
    
    const clearError = (field) => {
      if (field) {
        if (errors.value[field]) {
          delete errors.value[field]
        }
      } else {
        // 清除所有错误
        errors.value = {}
      }
    }
    
    const handleLogin = async () => {
      // 清除之前的通用错误
      if (errors.value.general) {
        delete errors.value.general
      }
      
      // 验证所有字段
      Object.keys(formData.value).forEach(validateField)
      
      if (!isFormValid.value) {
        return
      }
      
      loading.value = true
      
      try {
        const response = await authAPI.login(formData.value)
        
        if (response.code === 200) {
          const { token, userId, username, email } = response.data
          
          // 存储用户信息
          storage.set('token', token)
          storage.set('user', { userId, username, email })
          
          // 跳转到仪表盘
          router.push('/dashboard')
          
          // 显示成功消息
          notification.success('登录成功！欢迎回来')
          console.log('登录成功')
        } else {
          // 处理业务错误
          errors.value.general = response.message || '登录失败，请检查用户名和密码'
        }
      } catch (error) {
        console.error('登录失败:', error)
        if (error.response && error.response.data) {
          errors.value.general = error.response.data.message || '登录失败，请重试'
        } else {
          errors.value.general = error.message || '网络错误，请检查网络连接后重试'
        }
      } finally {
        loading.value = false
      }
    }
    
    // 生命周期
    onMounted(() => {
      // 路由守卫已经处理了重定向逻辑，这里不需要重复检查
      console.log('登录页面已加载')
    })
    
    return {
      formData,
      errors,
      loading,
      showPassword,
      isFormValid,
      validateField,
      clearError,
      handleLogin
    }
  }
}
</script>

<style lang="scss" scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  background: linear-gradient(135deg, #fef7e7 0%, #fff5d6 100%);
  
  @media (max-width: 768px) {
    flex-direction: column;
  }
}

.login-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  
  @media (max-width: 768px) {
    flex: none;
    padding: 1rem;
  }
}

.login-form-container {
  width: 100%;
  max-width: 400px;
  background: white;
  border-radius: 12px;
  padding: 2.5rem;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  border: 1px solid rgba(217, 119, 6, 0.1);
  animation: fadeInUp 0.4s ease-out;
}

.login-header {
  text-align: center;
  margin-bottom: 2rem;
}

.brand-title {
  font-size: 2rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 0.5rem 0;
  letter-spacing: -0.5px;
}

.brand-subtitle {
  font-size: 1rem;
  color: var(--text-secondary);
  margin: 0;
}

.login-form {
  .form-group {
    margin-bottom: 1.5rem;
  }
  
  .form-label {
    display: block;
    margin-bottom: 0.5rem;
    font-size: 0.875rem;
    font-weight: 500;
    color: var(--text-primary);
  }
  
  .form-input {
    width: 100%;
    padding: 0.75rem;
    border: 1.5px solid var(--border-color);
    border-radius: 8px;
    font-size: 0.875rem;
    transition: all 0.2s ease;
    background: white;
    
    &:focus {
      outline: none;
      border-color: var(--primary-color);
      box-shadow: 0 0 0 3px rgba(217, 119, 6, 0.1);
    }
    
    &.error {
      border-color: var(--error-color);
      background: rgba(239, 68, 68, 0.05);
      
      &:focus {
        box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.1);
      }
    }
    
    &::placeholder {
      color: var(--text-placeholder);
    }
  }
}

.password-wrapper {
  position: relative;
  
  .password-toggle {
    position: absolute;
    right: 0.75rem;
    top: 50%;
    transform: translateY(-50%);
    background: none;
    border: none;
    color: var(--text-tertiary);
    cursor: pointer;
    padding: 0.25rem;
    border-radius: 4px;
    transition: color 0.2s ease;
    
    &:hover {
      color: var(--text-secondary);
    }
    
    svg {
      display: block;
    }
  }
}

.error-text {
  font-size: 0.75rem;
  color: var(--error-color);
  margin-top: 0.5rem;
  
  &.general-error {
    background: rgba(239, 68, 68, 0.1);
    padding: 0.75rem;
    border-radius: 6px;
    border-left: 3px solid var(--error-color);
    font-size: 0.8125rem;
    margin-top: 0;
    margin-bottom: 0.5rem;
    display: flex;
    align-items: center;
    gap: 0.5rem;
    
    &::before {
      content: "⚠";
      font-size: 0.875rem;
    }
  }
}

.login-button {
  width: 100%;
  padding: 0.875rem 1rem;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.9375rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-top: 1.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  box-shadow: 0 2px 4px rgba(217, 119, 6, 0.2);
  
  &:hover:not(:disabled) {
    background: #c2621b;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(217, 119, 6, 0.35);
  }
  
  &:active:not(:disabled) {
    transform: translateY(0);
    box-shadow: 0 2px 4px rgba(217, 119, 6, 0.25);
  }
  
  &:focus {
    outline: none;
    box-shadow: 0 0 0 3px rgba(217, 119, 6, 0.25), 0 2px 4px rgba(217, 119, 6, 0.2);
  }
  
  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
  }
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top: 2px solid white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.login-footer {
  text-align: center;
  margin-top: 2rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--border-color);
}

.footer-text {
  font-size: 0.875rem;
  color: var(--text-secondary);
  margin: 0;
}

.footer-link {
  color: var(--primary-color);
  text-decoration: none;
  font-weight: 500;
  
  &:hover {
    text-decoration: underline;
  }
}

.login-decoration {
  flex: 1;
  background: linear-gradient(135deg, #ea7317 0%, #d97706 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 3rem;
  color: white;
  
  @media (max-width: 768px) {
    flex: none;
    padding: 2rem;
    min-height: 300px;
  }
}

.decoration-content {
  max-width: 400px;
  text-align: center;
}

.decoration-title {
  font-size: 2.5rem;
  font-weight: 700;
  margin: 0 0 1rem 0;
  letter-spacing: -1px;
  
  @media (max-width: 768px) {
    font-size: 2rem;
  }
}

.decoration-text {
  font-size: 1.125rem;
  line-height: 1.6;
  margin: 0 0 2.5rem 0;
  opacity: 0.9;
}

.decoration-features {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  font-size: 1rem;
  font-weight: 500;
}

.feature-icon {
  width: 40px;
  height: 40px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  
  svg {
    opacity: 0.9;
  }
}

// 动画关键帧
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

// 为表单元素添加交错动画
.login-header {
  animation: fadeInUp 0.4s ease-out 0.05s both;
}

.form-group {
  animation: fadeInUp 0.4s ease-out both;
  
  &:nth-child(1) { animation-delay: 0.1s; }
  &:nth-child(2) { animation-delay: 0.15s; }
}

.login-button {
  animation: fadeInUp 0.4s ease-out 0.2s both;
}

.register-link {
  animation: fadeInUp 0.4s ease-out 0.25s both;
}
</style>
