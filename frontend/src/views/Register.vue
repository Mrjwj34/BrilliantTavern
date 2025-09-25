<template>
  <div class="register-container">
    <!-- 左侧内容 -->
    <div class="register-content">
      <div class="register-form-container">
        <!-- 头部 -->
        <div class="register-header">
          <h1 class="brand-title">BrilliantTavern</h1>
          <p class="brand-subtitle">创建您的账户</p>
        </div>

        <!-- 注册表单 -->
        <form @submit.prevent="handleRegister" class="register-form">
          <div class="form-group">
            <label for="username" class="form-label">用户名</label>
            <input
              id="username"
              v-model="formData.username"
              type="text"
              class="form-input"
              :class="{ 'error': errors.username }"
              placeholder="请输入用户名"
              @blur="validateField('username')"
              @input="clearError('username'); clearError('general')"
            />
            <div v-if="errors.username" class="error-text">
              {{ errors.username }}
            </div>
          </div>

          <div class="form-group">
            <label for="email" class="form-label">邮箱地址</label>
            <input
              id="email"
              v-model="formData.email"
              type="email"
              class="form-input"
              :class="{ 'error': errors.email }"
              placeholder="请输入邮箱地址"
              @blur="validateField('email')"
              @input="clearError('email'); clearError('general')"
            />
            <div v-if="errors.email" class="error-text">
              {{ errors.email }}
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
                placeholder="请输入密码"
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

          <div class="form-group">
            <label for="confirmPassword" class="form-label">确认密码</label>
            <div class="password-wrapper">
              <input
                id="confirmPassword"
                v-model="formData.confirmPassword"
                :type="showConfirmPassword ? 'text' : 'password'"
                class="form-input"
                :class="{ 'error': errors.confirmPassword }"
                placeholder="请再次输入密码"
                @blur="validateField('confirmPassword')"
                @input="clearError('confirmPassword'); clearError('general')"
              />
              <button
                type="button"
                class="password-toggle"
                @click="showConfirmPassword = !showConfirmPassword"
              >
                <svg v-if="showConfirmPassword" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                </svg>
                <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
              </button>
            </div>
            <div v-if="errors.confirmPassword" class="error-text">
              {{ errors.confirmPassword }}
            </div>
          </div>

          <!-- 密码强度指示器 -->
          <div v-if="formData.password" class="password-strength">
            <div class="strength-label">密码强度</div>
            <div class="strength-bar">
              <div 
                class="strength-fill" 
                :class="passwordStrength.level"
                :style="{ width: passwordStrength.percentage + '%' }"
              ></div>
            </div>
            <div class="strength-text">{{ passwordStrength.text }}</div>
          </div>

          <!-- 通用错误显示 -->
          <div v-if="errors.general" class="form-group">
            <div class="error-text general-error">
              {{ errors.general }}
            </div>
          </div>

          <button
            type="submit"
            class="register-button"
            :disabled="loading || !isFormValid"
          >
            <div v-if="loading" class="loading-spinner"></div>
            {{ loading ? '注册中...' : '立即注册' }}
          </button>
        </form>

        <!-- 底部链接 -->
        <div class="register-footer">
          <p class="footer-text">
            已有账户？
            <router-link to="/login" class="footer-link">立即登录</router-link>
          </p>
        </div>
      </div>
    </div>

    <!-- 右侧装饰 -->
    <div class="register-decoration">
      <div class="decoration-content">
        <h2 class="decoration-title">加入我们</h2>
        <p class="decoration-text">
          开启您的AI对话体验，与智能角色建立深度连接。
        </p>
        <div class="decoration-benefits">
          <div class="benefit-item">
            <div class="benefit-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 12l2 2 4-4"/>
                <circle cx="12" cy="12" r="9"/>
              </svg>
            </div>
            <span>免费注册，即刻体验</span>
          </div>
          <div class="benefit-item">
            <div class="benefit-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 12l2 2 4-4"/>
                <circle cx="12" cy="12" r="9"/>
              </svg>
            </div>
            <span>多样化AI角色库</span>
          </div>
          <div class="benefit-item">
            <div class="benefit-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 12l2 2 4-4"/>
                <circle cx="12" cy="12" r="9"/>
              </svg>
            </div>
            <span>安全私密的对话环境</span>
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
import { validation, storage, notification } from '@/utils'

export default {
  name: 'Register',
  setup() {
    const router = useRouter()
    
    // 响应式数据
    const formData = ref({
      username: '',
      email: '',
      password: '',
      confirmPassword: ''
    })
    
    const errors = ref({})
    const loading = ref(false)
    const showPassword = ref(false)
    const showConfirmPassword = ref(false)
    
    // 计算属性
    const isFormValid = computed(() => {
      return formData.value.username && 
             formData.value.email && 
             formData.value.password && 
             formData.value.confirmPassword && 
             Object.keys(errors.value).length === 0
    })
    
    const passwordStrength = computed(() => {
      const password = formData.value.password
      if (!password) return { level: 'weak', percentage: 0, text: '' }
      
      let score = 0
      let text = '弱'
      
      // 长度检查
      if (password.length >= 8) score += 25
      
      // 包含数字
      if (/\d/.test(password)) score += 25
      
      // 包含小写字母
      if (/[a-z]/.test(password)) score += 25
      
      // 包含大写字母或特殊字符
      if (/[A-Z]/.test(password) || /[!@#$%^&*(),.?":{}|<>]/.test(password)) score += 25
      
      if (score >= 75) {
        text = '强'
        return { level: 'strong', percentage: score, text }
      } else if (score >= 50) {
        text = '中'
        return { level: 'medium', percentage: score, text }
      } else {
        return { level: 'weak', percentage: score, text }
      }
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
          
        case 'email':
          if (!validation.isRequired(value)) {
            errors.value.email = '请输入邮箱地址'
          } else if (!validation.isEmail(value)) {
            errors.value.email = '请输入有效的邮箱地址'
          } else {
            delete errors.value.email
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
          
          // 如果确认密码已填写，重新验证确认密码
          if (formData.value.confirmPassword) {
            validateField('confirmPassword')
          }
          break
          
        case 'confirmPassword':
          if (!validation.isRequired(value)) {
            errors.value.confirmPassword = '请确认密码'
          } else if (value !== formData.value.password) {
            errors.value.confirmPassword = '两次输入的密码不一致'
          } else {
            delete errors.value.confirmPassword
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
    
    const handleRegister = async () => {
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
        const { confirmPassword, ...registerData } = formData.value
        const response = await authAPI.register(registerData)
        
        if (response.code === 200) {
          const { token, userId, username, email } = response.data
          
          // 存储用户信息
          storage.set('token', token)
          storage.set('user', { userId, username, email })
          
          // 跳转到仪表盘
          router.push('/dashboard')
          
          notification.success('注册成功！欢迎加入BrilliantTavern')
          console.log('注册成功')
        } else {
          // 处理业务错误
          errors.value.general = response.message || '注册失败，请检查输入信息'
        }
      } catch (error) {
        console.error('注册失败:', error)
        if (error.response && error.response.data) {
          errors.value.general = error.response.data.message || '注册失败，请重试'
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
      console.log('注册页面已加载')
    })
    
    return {
      formData,
      errors,
      loading,
      showPassword,
      showConfirmPassword,
      isFormValid,
      passwordStrength,
      validateField,
      clearError,
      handleRegister
    }
  }
}
</script>

<style lang="scss" scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  background: linear-gradient(135deg, #fef7e7 0%, #fff5d6 100%);
  
  @media (max-width: 768px) {
    flex-direction: column;
  }
}

.register-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  
  @media (max-width: 768px) {
    flex: none;
    padding: 1rem;
    min-height: auto;
    max-height: none;
    overflow: visible;
  }
}

.register-form-container {
  width: 100%;
  max-width: 420px;
  background: white;
  border-radius: 12px;
  padding: 2.5rem;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  border: 1px solid rgba(217, 119, 6, 0.1);
  max-height: 90vh;
  overflow-y: auto;
  
  @media (max-width: 768px) {
    max-height: none;
    overflow-y: visible;
  }
}

.register-header {
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

.register-form {
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

.password-strength {
  margin: 1rem 0;
  
  .strength-label {
    font-size: 0.75rem;
    color: var(--text-secondary);
    margin-bottom: 0.5rem;
  }
  
  .strength-bar {
    width: 100%;
    height: 4px;
    background: var(--border-color);
    border-radius: 2px;
    overflow: hidden;
    margin-bottom: 0.25rem;
    
    .strength-fill {
      height: 100%;
      transition: all 0.3s ease;
      border-radius: 2px;
      
      &.weak {
        background: #ef4444;
      }
      
      &.medium {
        background: #f59e0b;
      }
      
      &.strong {
        background: var(--success-color);
      }
    }
  }
  
  .strength-text {
    font-size: 0.75rem;
    color: var(--text-tertiary);
  }
}

.register-button {
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

.register-footer {
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

.register-decoration {
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
    min-height: 280px;
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

.decoration-benefits {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.benefit-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  font-size: 1rem;
  font-weight: 500;
}

.benefit-icon {
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
</style>
