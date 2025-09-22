<template>
  <div class="dashboard">
    <!-- 左侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <!-- 顶部品牌区域 -->
      <div class="sidebar-header">
        <div class="brand-section">
          <h2 class="brand-title">BrilliantTavern</h2>
        </div>
        <div class="header-actions">
          <button @click="toggleTheme" class="theme-toggle-btn" :title="isDarkMode ? '切换到浅色模式' : '切换到深色模式'">
            <svg v-if="isDarkMode" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="5"/>
              <line x1="12" y1="1" x2="12" y2="3"/>
              <line x1="12" y1="21" x2="12" y2="23"/>
              <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
              <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
              <line x1="1" y1="12" x2="3" y2="12"/>
              <line x1="21" y1="12" x2="23" y2="12"/>
              <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
              <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
            </svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
            </svg>
          </button>
          <button @click="toggleSidebar" class="sidebar-toggle-btn" :title="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ 'rotated': sidebarCollapsed }">
              <path d="M11 19l-7-7 7-7"/>
              <path d="M21 19l-7-7 7-7"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- 导航标签页 -->
      <div class="sidebar-nav">
        <div class="nav-tabs">
          <button 
            v-for="tab in navTabs" 
            :key="tab.id"
            :class="['nav-tab', { active: activeTab === tab.id }]"
            :title="sidebarCollapsed ? tab.label : ''"
            @click="setActiveTab(tab.id)"
          >
            <svg v-if="tab.id === 'market'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="nav-tab-icon">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            <svg v-else-if="tab.id === 'create'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="nav-tab-icon">
              <path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .962 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.582a.5.5 0 0 1 0 .962L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.962 0L9.937 15.5Z"/>
              <path d="M19 3v4"/>
              <path d="M21 5h-4"/>
            </svg>
            <svg v-else-if="tab.id === 'voice'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="nav-tab-icon">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
              <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
              <line x1="12" y1="19" x2="12" y2="23"/>
              <line x1="8" y1="23" x2="16" y2="23"/>
            </svg>
            <span class="nav-tab-text">{{ tab.label }}</span>
          </button>
        </div>
      </div>

      <!-- 历史记录区域 -->
      <div class="sidebar-history">
        <div class="history-header">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="history-icon">
            <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
            <path d="M3 3v5h5"/>
            <polyline points="12,7 12,12 16,14"/>
          </svg>
          <h3 class="history-title">历史记录</h3>
        </div>
        <div class="history-placeholder">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="history-placeholder-icon">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
          </svg>
          <p class="history-placeholder-text">暂无历史记录</p>
        </div>
      </div>

      <!-- 用户信息 -->
      <div class="sidebar-footer">
        <div class="user-info">
          <div class="user-avatar">
            {{ user?.username?.charAt(0)?.toUpperCase() }}
          </div>
          <div class="user-details">
            <div class="user-name">{{ user?.username }}</div>
            <div class="user-status">在线</div>
          </div>
          <button @click="handleLogout" class="logout-button">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="logout-icon">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16,17 21,12 16,7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
          </button>
        </div>
      </div>
    </aside>

    <!-- 右侧工作区 -->
    <main class="workspace">
      <!-- 工作区内容 -->
      <div class="workspace-content">
        <!-- 欢迎页面 -->
        <div v-if="activeTab === 'welcome'" class="welcome-view">
          <div class="welcome-container">
            <div class="welcome-header">
              <div class="welcome-content-wrapper">
                <div class="welcome-icon">
                  <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z"/>
                  </svg>
                </div>
                <div class="welcome-text-content">
                  <h1 class="welcome-title">欢迎使用 BrilliantTavern</h1>
                  <p class="welcome-subtitle">开始与AI角色的精彩对话之旅</p>
                </div>
              </div>
            </div>
            
            <div class="welcome-features">
              <div class="feature-item clickable" @click="setActiveTab('market')">
                <div class="feature-icon">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                    <circle cx="9" cy="7" r="4"/>
                    <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                    <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                  </svg>
                </div>
                <div class="feature-content">
                  <h3 class="feature-title">角色市场</h3>
                  <p class="feature-description">探索丰富多样的AI角色，找到最适合您的对话伙伴</p>
                </div>
              </div>
              
              <div class="feature-item clickable" @click="setActiveTab('create')">
                <div class="feature-icon">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .962 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.582a.5.5 0 0 1 0 .962L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.962 0L9.937 15.5Z"/>
                    <path d="M19 3v4"/>
                    <path d="M21 5h-4"/>
                  </svg>
                </div>
                <div class="feature-content">
                  <h3 class="feature-title">创建角色</h3>
                  <p class="feature-description">设计独特的AI角色，让想象力成为现实</p>
                </div>
              </div>
              
              <div class="feature-item clickable" @click="setActiveTab('voice')">
                <div class="feature-icon">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                    <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                    <line x1="12" y1="19" x2="12" y2="23"/>
                    <line x1="8" y1="23" x2="16" y2="23"/>
                  </svg>
                </div>
                <div class="feature-content">
                  <h3 class="feature-title">语音对话</h3>
                  <p class="feature-description">体验自然流畅的语音交互，让对话更加生动</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 角色市场页面占位 -->
        <div v-else-if="activeTab === 'market'" class="placeholder-view">
          <div class="placeholder-content">
            <div class="placeholder-icon-wrapper">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="placeholder-icon">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </div>
            <h2 class="placeholder-title">角色市场</h2>
            <p class="placeholder-text">功能开发中，敬请期待</p>
          </div>
        </div>

        <!-- 创建角色页面占位 -->
        <div v-else-if="activeTab === 'create'" class="placeholder-view">
          <div class="placeholder-content">
            <div class="placeholder-icon-wrapper">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="placeholder-icon">
                <path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .962 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.582a.5.5 0 0 1 0 .962L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.962 0L9.937 15.5Z"/>
                <path d="M19 3v4"/>
                <path d="M21 5h-4"/>
              </svg>
            </div>
            <h2 class="placeholder-title">创建角色</h2>
            <p class="placeholder-text">功能开发中，敬请期待</p>
          </div>
        </div>

        <!-- 语音对话页面占位 -->
        <div v-else-if="activeTab === 'voice'" class="placeholder-view">
          <div class="placeholder-content">
            <div class="placeholder-icon-wrapper">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="placeholder-icon">
                <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                <line x1="12" y1="19" x2="12" y2="23"/>
                <line x1="8" y1="23" x2="16" y2="23"/>
              </svg>
            </div>
            <h2 class="placeholder-title">语音对话</h2>
            <p class="placeholder-text">功能开发中，敬请期待</p>
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
import { storage, tokenUtils } from '@/utils'

export default {
  name: 'Dashboard',
  setup() {
    const router = useRouter()
    const user = ref(null)
    const activeTab = ref('welcome')
    const sidebarCollapsed = ref(false)
    const isDarkMode = ref(false)

    const navTabs = [
      { id: 'market', label: '角色市场' },
      { id: 'create', label: '创建角色' },
      { id: 'voice', label: '语音对话' }
    ]

    const setActiveTab = (tabId) => {
      activeTab.value = tabId
    }

    const toggleSidebar = () => {
      sidebarCollapsed.value = !sidebarCollapsed.value
    }

    const toggleTheme = () => {
      isDarkMode.value = !isDarkMode.value
      document.documentElement.setAttribute('data-theme', isDarkMode.value ? 'dark' : 'light')
      localStorage.setItem('theme', isDarkMode.value ? 'dark' : 'light')
    }

    const handleLogout = async () => {
      try {
        await authAPI.logout()
      } catch (error) {
        console.error('登出失败:', error)
      } finally {
        tokenUtils.clearAuth()
        router.push('/login')
      }
    }

    const loadUserInfo = async () => {
      try {
        const localUser = storage.get('user')
        if (localUser) {
          user.value = localUser
          console.log('已加载本地用户信息:', localUser.username)
        }

        try {
          if (typeof authAPI.getUserInfo === 'function') {
            const response = await authAPI.getUserInfo()
            if (response && response.code === 200) {
              user.value = {
                ...user.value,
                ...response.data
              }
              storage.set('user', user.value)
              console.log('已更新用户信息从服务器')
            }
          }
        } catch (apiError) {
          console.log('无法从服务器更新用户信息，使用本地数据')
        }
      } catch (error) {
        console.error('加载用户信息失败:', error)
        if (!storage.get('user')) {
          console.error('本地用户信息缺失，清除认证信息')
          tokenUtils.clearAuth()
          router.push('/login')
        }
      }
    }

    const initTheme = () => {
      const savedTheme = localStorage.getItem('theme')
      if (savedTheme) {
        isDarkMode.value = savedTheme === 'dark'
        document.documentElement.setAttribute('data-theme', savedTheme)
      } else {
        // 检测系统主题偏好
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
        isDarkMode.value = prefersDark
        document.documentElement.setAttribute('data-theme', prefersDark ? 'dark' : 'light')
      }
    }

    onMounted(() => {
      loadUserInfo()
      initTheme()
    })

    return {
      user,
      activeTab,
      navTabs,
      sidebarCollapsed,
      isDarkMode,
      setActiveTab,
      toggleSidebar,
      toggleTheme,
      handleLogout
    }
  }
}
</script>

<style lang="scss" scoped>
.dashboard {
  height: 100vh;
  display: flex;
  background: var(--background-primary);
  color: var(--text-primary);
}

// 左侧边栏
.sidebar {
  width: 280px;
  background: var(--background-secondary);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  position: relative;
  transition: all 0.3s ease;

  &.collapsed {
    width: 60px;

    .brand-title,
    .nav-tab-text,
    .user-details,
    .history-title,
    .history-placeholder-text {
      opacity: 0;
      pointer-events: none;
      display: none;
    }

    // 隐藏不需要的功能区域
    .sidebar-history,
    .sidebar-footer,
    .theme-toggle-btn {
      display: none;
    }

    .nav-tab {
      justify-content: center;
      padding: $spacing-sm;
      width: 44px;
      height: 44px;
      border-radius: 50%;
      
      &:hover {
        background: var(--background-tertiary);
        border-color: var(--border-light);
        transform: scale(1.05);
      }

      &.active {
        background: var(--background-tertiary);
        border-color: var(--primary-color);
        box-shadow: var(--shadow-light);
      }
    }

    .sidebar-header {
      padding: $spacing-sm $spacing-xs;
      justify-content: center;
    }

    .sidebar-nav {
      flex: 1;
      display: flex;
      flex-direction: column;
      justify-content: flex-start;
      padding: $spacing $spacing-xs $spacing $spacing-xs;
    }
    
    .nav-tabs {
      gap: $spacing-sm;
      align-items: center;
    }

    .brand-section {
      width: 0;
      overflow: hidden;
    }

    .header-actions {
      width: 100%;
      justify-content: center;
      
      .sidebar-toggle-btn {
        width: 44px;
        height: 44px;
        border-radius: 50%;
        padding: $spacing-sm;
        
        &:hover {
          background: var(--background-tertiary);
          transform: scale(1.05);
        }
      }
    }
  }
}

// 侧边栏头部
.sidebar-header {
  padding: $spacing $spacing $spacing-xs $spacing;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--background-secondary);
  border-bottom: none;
}

.brand-section {
  flex: 1;
}

.brand-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--primary-color);
  margin: 0;
  transition: opacity 0.3s ease;
}

.header-actions {
  display: flex;
  gap: $spacing-xs;
}

.theme-toggle-btn,
.sidebar-toggle-btn {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: $spacing-xs;
  border-radius: $border-radius-sm;
  transition: all $transition-fast ease;

  &:hover {
    background: var(--background-tertiary);
    color: var(--text-primary);
  }
  
  svg {
    transition: transform 0.3s ease;
    
    &.rotated {
      transform: rotate(180deg);
    }
  }
}

.sidebar-nav {
  padding: $spacing-xs $spacing;
  border-bottom: none;
}

.nav-tabs {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.nav-tab {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing;
  background: transparent;
  border: 1px solid transparent;
  border-radius: $border-radius;
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all $transition-fast ease;
  width: 100%;
  text-align: left;

  &:hover {
    background: var(--background-tertiary);
    border-color: var(--border-light);
    color: var(--text-primary);
  }

  &.active {
    background: var(--background-tertiary);
    color: var(--primary-color);
    font-weight: 500;
    border-color: var(--primary-color);
    box-shadow: var(--shadow-light);
  }
}

.nav-tab-icon {
  width: 16px;
  height: 16px;
  stroke-width: 2;
  transition: opacity 0.3s ease;
  flex-shrink: 0;
}

.nav-tab-text {
  flex: 1;
  transition: opacity 0.3s ease;
}

// 历史记录区域
.sidebar-history {
  flex: 1;
  padding: $spacing;
  overflow-y: auto;
  border-top: none;
}

.history-header {
  margin-bottom: $spacing;
  display: flex;
  align-items: center;
  gap: $spacing-xs;
}

.history-icon {
  color: var(--text-secondary);
  width: 14px;
  height: 14px;
}

.history-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
  transition: opacity 0.3s ease;
}

.history-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100px;
  gap: $spacing-xs;
}

.history-placeholder-icon {
  color: var(--text-tertiary);
  width: 20px;
  height: 20px;
}

.history-placeholder-text {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0;
  transition: opacity 0.3s ease;
}

// 用户信息
.sidebar-footer {
  padding: $spacing;
  background: var(--background-secondary);
  border-top: none;
}

.user-info {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm;
  background: var(--background-tertiary);
  border-radius: $border-radius;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--primary-color);
  color: var(--text-white);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
}

.user-details {
  flex: 1;
  transition: opacity 0.3s ease;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  line-height: 1.2;
}

.user-status {
  font-size: 12px;
  color: var(--success-color);
  line-height: 1.2;
}

.logout-button {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: $spacing-xs;
  border-radius: $border-radius-sm;
  transition: all $transition-fast ease;

  &:hover {
    background: var(--background-primary);
    color: var(--text-primary);
  }
}

.logout-icon {
  width: 16px;
  height: 16px;
}

// 右侧工作区
.workspace {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.workspace-content {
  flex: 1;
  overflow-y: auto;
  background: var(--background-primary);
}

// 欢迎页面
.welcome-view {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-2xl;
}

.welcome-container {
  max-width: 600px;
  width: 100%;
}

.welcome-header {
  margin-bottom: $spacing-2xl;
}

.welcome-content-wrapper {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
}

.welcome-icon {
  color: $primary-color;
  background: rgba($primary-color, 0.1);
  padding: $spacing-lg;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba($primary-color, 0.15);
  flex-shrink: 0;
}

.welcome-text-content {
  flex: 1;
}

.welcome-title {
  font-size: 2.25rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 $spacing-sm 0;
  line-height: 1.2;
}

.welcome-subtitle {
  font-size: 1.125rem;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.5;
}

.welcome-features {
  margin-bottom: $spacing-2xl;
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  gap: $spacing;
  text-align: left;
  padding: $spacing-lg;
  background: var(--background-secondary);
  border-radius: $border-radius-lg;
  border: 1px solid var(--border-light);
  transition: all $transition-normal ease;

  &:hover {
    background: var(--background-tertiary);
    border-color: var(--primary-color);
    transform: translateY(-2px);
    box-shadow: var(--shadow-medium);
  }

  &.clickable {
    cursor: pointer;

    &:hover {
      background: var(--background-tertiary);
      border-color: var(--primary-color);
    }
  }
}

.feature-icon {
  color: var(--primary-color);
  background: var(--background-tertiary);
  padding: $spacing-sm;
  border-radius: $border-radius;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all $transition-fast ease;

  .feature-item:hover & {
    background: var(--background-primary);
    transform: scale(1.05);
  }

  .feature-item.clickable:hover & {
    background: var(--background-primary);
    color: var(--primary-dark);
  }
}

.feature-content {
  flex: 1;
}

.feature-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 $spacing-xs 0;
}

.feature-description {
  font-size: 0.875rem;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.5;
}

// 占位页面
.placeholder-view {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-2xl;
}

.placeholder-content {
  text-align: center;
  max-width: 400px;
}

.placeholder-icon-wrapper {
  display: flex;
  justify-content: center;
  margin-bottom: $spacing-lg;
}

.placeholder-icon {
  color: var(--text-tertiary);
  width: 48px;
  height: 48px;
  background: var(--background-tertiary);
  padding: $spacing-sm;
  border-radius: 50%;
}

.placeholder-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 $spacing 0;
}

.placeholder-text {
  font-size: 1rem;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.5;
}

// 响应式设计
@media (max-width: 768px) {
  .dashboard {
    flex-direction: column;
    height: 100vh;
  }

  .sidebar {
    width: 100%;
    height: 64px;
    flex-direction: row;
    border-right: none;
    border-bottom: 1px solid var(--border-color);
    align-items: center;
  }

  .sidebar-header {
    padding: $spacing-sm;
    flex-shrink: 0;
    
    .brand-title {
      display: none;
    }
    
    .header-actions {
      gap: $spacing-xs;
      margin: 0 auto;
    }
  }

  .sidebar-nav {
    flex: 1;
    padding: $spacing-sm;
    
    .nav-tabs {
      flex-direction: row;
      gap: $spacing-xs;
      align-items: center;
      justify-content: space-evenly;
      width: 100%;
      
      &::-webkit-scrollbar {
        display: none;
      }
    }
    
    .nav-tab {
      white-space: nowrap;
      flex: 1;
      flex-shrink: 0;
      padding: $spacing-xs $spacing-sm;
      border-radius: 20px;
      max-width: calc(100% / 3);
      min-width: 0;
      
      .nav-tab-icon {
        width: 14px;
        height: 14px;
      }
    }
  }

  .nav-tab-text {
    display: none;
  }

  .sidebar-history {
    display: none;
  }

  .sidebar-footer {
    padding: $spacing-sm;
    border-top: none;
    border-left: none;
    flex-shrink: 0;
    
    .user-info {
      padding: $spacing-xs;
    }
    
    .user-avatar {
      width: 28px;
      height: 28px;
      font-size: 12px;
    }
  }

  .user-details {
    display: none;
  }

  .workspace {
    flex: 1;
    overflow: hidden;
  }

  .welcome-title {
    font-size: 1.75rem;
  }

  .welcome-features {
    gap: $spacing;
  }

  .feature-item {
    padding: $spacing;
  }

  .welcome-actions {
    flex-direction: column;
  }
  
  // 收起状态下的移动端样式
  .sidebar.collapsed {
    width: 100%;
    height: 56px;
    
    .sidebar-header {
      .brand-section {
        display: none;
      }
      
      .header-actions {
        margin: 0 auto;
      }
    }
    
    .sidebar-nav {
      .nav-tabs {
        justify-content: space-evenly;
      }
      
      .nav-tab {
        width: 40px;
        height: 40px;
        border-radius: 50%;
        justify-content: center;
        flex: none;
        max-width: 40px;
      }
    }
    
    .sidebar-footer {
      .user-info {
        justify-content: center;
        
        .user-avatar {
          width: 32px;
          height: 32px;
        }
      }
    }
  }
}
</style>
