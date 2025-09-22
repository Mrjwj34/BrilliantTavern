<template>
  <div class="dashboard">
    <!-- 导航栏 -->
    <header class="dashboard-header">
      <div class="header-content">
        <div class="brand">
          <h1 class="brand-title">BrilliantTavern</h1>
        </div>
        <div class="user-menu">
          <div class="user-info">
            <span class="user-name">{{ user?.username }}</span>
            <div class="user-avatar">
              {{ user?.username?.charAt(0)?.toUpperCase() }}
            </div>
          </div>
          <button @click="handleLogout" class="logout-btn">
            退出登录
          </button>
        </div>
      </div>
    </header>

    <!-- 主要内容 -->
    <main class="dashboard-main">
      <div class="welcome-section">
        <div class="welcome-card">
          <div class="welcome-content">
            <h2 class="welcome-title">
              欢迎回来，{{ user?.username }}！
            </h2>
            <p class="welcome-text">
              准备好与AI角色开始精彩的对话了吗？选择一个角色开始您的冒险之旅。
            </p>
          </div>
          <div class="welcome-illustration">
            <div class="illustration-circle">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20.24 12.24a6 6 0 0 0-8.49-8.49L5 10.5V19h8.5z"/>
                <line x1="16" y1="8" x2="2" y2="22"/>
                <line x1="17.5" y1="15" x2="9" y2="15"/>
              </svg>
            </div>
          </div>
        </div>
      </div>

      <!-- 功能卡片 -->
      <div class="features-section">
        <div class="features-grid">
          <div class="feature-card">
            <div class="feature-icon">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M16 7h.01"/>
                <path d="M8 7h.01"/>
                <path d="M12 20a8 8 0 1 0 0-16 8 8 0 0 0 0 16z"/>
                <path d="M12 14s1.5-2 0-2-1.5 2-1.5 2"/>
              </svg>
            </div>
            <h3 class="feature-title">角色市场</h3>
            <p class="feature-desc">探索丰富的AI角色，找到您喜欢的对话伙伴</p>
            <button class="feature-btn" disabled>即将开放</button>
          </div>

          <div class="feature-card">
            <div class="feature-icon">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <h3 class="feature-title">语音对话</h3>
            <p class="feature-desc">与AI角色进行自然流畅的语音交互</p>
            <button class="feature-btn" disabled>即将开放</button>
          </div>

          <div class="feature-card">
            <div class="feature-icon">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
              </svg>
            </div>
            <h3 class="feature-title">创建角色</h3>
            <p class="feature-desc">设计专属的AI角色，分享给其他用户</p>
            <button class="feature-btn" disabled>即将开放</button>
          </div>

          <div class="feature-card">
            <div class="feature-icon">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
              </svg>
            </div>
            <h3 class="feature-title">对话历史</h3>
            <p class="feature-desc">回顾与AI角色的精彩对话记录</p>
            <button class="feature-btn" disabled>即将开放</button>
          </div>
        </div>
      </div>

      <!-- 统计信息 -->
      <div class="stats-section">
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-value">0</div>
            <div class="stat-label">对话次数</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">0</div>
            <div class="stat-label">使用角色</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">0</div>
            <div class="stat-label">创建角色</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ formatDate(user?.createdAt) || '今日' }}</div>
            <div class="stat-label">加入时间</div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { authAPI } from '@/api'
import { storage, format, tokenUtils } from '@/utils'

export default {
  name: 'Dashboard',
  setup() {
    const router = useRouter()
    const user = ref(null)

    const formatDate = (date) => {
      if (!date) return ''
      return format.date(date, 'YYYY-MM-DD')
    }

    const handleLogout = async () => {
      try {
        await authAPI.logout()
      } catch (error) {
        console.error('登出失败:', error)
      } finally {
        // 清除认证信息
        tokenUtils.clearAuth()
        // 跳转到登录页
        router.push('/login')
      }
    }

    const loadUserInfo = async () => {
      try {
        // 从本地存储获取用户信息
        const localUser = storage.get('user')
        if (localUser) {
          user.value = localUser
          console.log('已加载本地用户信息:', localUser.username)
        }

        // 尝试从服务器获取最新用户信息（可选）
        // 注意：这里不强制要求API成功，因为可能没有对应的后端接口
        try {
          if (typeof authAPI.getUserInfo === 'function') {
            const response = await authAPI.getUserInfo()
            if (response && response.code === 200) {
              user.value = {
                ...user.value,
                ...response.data
              }
              // 更新本地存储
              storage.set('user', user.value)
              console.log('已更新用户信息从服务器')
            }
          }
        } catch (apiError) {
          // API调用失败不影响用户信息显示
          console.log('无法从服务器更新用户信息，使用本地数据')
        }
      } catch (error) {
        console.error('加载用户信息失败:', error)
        // 如果连本地用户信息都获取不到，说明可能有问题
        if (!storage.get('user')) {
          console.error('本地用户信息缺失，清除认证信息')
          tokenUtils.clearAuth()
          router.push('/login')
        }
      }
    }

    onMounted(() => {
      loadUserInfo()
    })

    return {
      user,
      formatDate,
      handleLogout
    }
  }
}
</script>

<style lang="scss" scoped>
.dashboard {
  min-height: 100vh;
  background: linear-gradient(135deg, #fef7e7 0%, #fff5d6 100%);
}

.dashboard-header {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.06);
  position: sticky;
  top: 0;
  z-index: 100;
  border-bottom: 1px solid rgba(217, 119, 6, 0.1);
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 70px;
}

.brand-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--primary-color);
  margin: 0;
}

.user-menu {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-name {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-primary);
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--primary-color);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 1rem;
}

.logout-btn {
  padding: 0.5rem 1rem;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 0.875rem;
  transition: all 0.2s ease;

  &:hover {
    background: #f9fafb;
    border-color: var(--primary-color);
    color: var(--primary-color);
  }
}

.dashboard-main {
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem 1.5rem;
}

.welcome-section {
  margin-bottom: 2.5rem;
}

.welcome-card {
  background: white;
  border-radius: 16px;
  padding: 2.5rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  border: 1px solid rgba(217, 119, 6, 0.1);
  
  @media (max-width: 768px) {
    flex-direction: column;
    text-align: center;
    gap: 2rem;
  }
}

.welcome-content {
  flex: 1;
}

.welcome-title {
  font-size: 2rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 0.75rem 0;
  letter-spacing: -0.5px;
}

.welcome-text {
  font-size: 1rem;
  color: var(--text-secondary);
  line-height: 1.6;
  max-width: 500px;
  margin: 0;
}

.welcome-illustration {
  .illustration-circle {
    width: 120px;
    height: 120px;
    border-radius: 50%;
    background: linear-gradient(135deg, var(--primary-color) 0%, #ea7317 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 8px 24px rgba(217, 119, 6, 0.3);
    color: white;
  }
}

.features-section {
  margin-bottom: 2.5rem;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1.5rem;
}

.feature-card {
  background: white;
  border-radius: 12px;
  padding: 2rem;
  text-align: center;
  transition: all 0.2s ease;
  border: 1px solid rgba(217, 119, 6, 0.1);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
    border-color: rgba(217, 119, 6, 0.2);
  }
}

.feature-icon {
  color: var(--primary-color);
  margin-bottom: 1rem;
  display: flex;
  justify-content: center;
}

.feature-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 0.75rem 0;
}

.feature-desc {
  font-size: 0.875rem;
  color: var(--text-secondary);
  line-height: 1.5;
  margin: 0 0 1.5rem 0;
}

.feature-btn {
  padding: 0.75rem 1.5rem;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    background: #c2621b;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(217, 119, 6, 0.3);
  }

  &:disabled {
    background: #e5e7eb;
    color: #9ca3af;
    cursor: not-allowed;
  }
}

.stats-section {
  .stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 1.25rem;
  }

  .stat-card {
    background: white;
    border-radius: 12px;
    padding: 1.5rem;
    text-align: center;
    border: 1px solid rgba(217, 119, 6, 0.1);
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  }

  .stat-value {
    font-size: 1.75rem;
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: 0.5rem;
  }

  .stat-label {
    font-size: 0.875rem;
    color: var(--text-secondary);
  }
}

@media (max-width: 768px) {
  .header-content {
    padding: 0 1rem;
    height: 60px;
  }

  .brand-title {
    font-size: 1.25rem;
  }

  .user-menu {
    gap: 12px;
  }

  .user-name {
    display: none;
  }

  .dashboard-main {
    padding: 1.5rem 1rem;
  }

  .welcome-card {
    padding: 2rem 1.5rem;
  }

  .welcome-title {
    font-size: 1.5rem;
  }

  .illustration-circle {
    width: 80px !important;
    height: 80px !important;
  }

  .features-grid {
    grid-template-columns: 1fr;
  }

  .feature-card {
    padding: 1.5rem;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr) !important;
  }
}
</style>
