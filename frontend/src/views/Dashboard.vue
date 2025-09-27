<template>
  <div class="dashboard">
    <!-- 左侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <!-- 顶部品牌区域 -->
      <div class="sidebar-header">
        <div class="brand-section">
          <h2 class="brand-title clickable" @click="goToWelcome">BrilliantTavern</h2>
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
          <button @click="refreshHistory" class="history-refresh-btn" :disabled="loadingHistory" title="刷新历史记录">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ spinning: loadingHistory }">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
              <path d="M3 3v5h5"/>
              <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/>
              <path d="M21 21v-5h-5"/>
            </svg>
          </button>
        </div>
        <div class="history-content">
          <div v-if="loadingHistory" class="history-loading">
            <div class="loading-spinner"></div>
            <span>加载中...</span>
          </div>
          <div v-else-if="filteredChatSessions.length === 0" class="history-placeholder">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="history-placeholder-icon">
              <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
            </svg>
            <p class="history-placeholder-text">
              {{ selectedCharacterInMarket ? '暂无与此角色的对话记录' : '暂无历史记录' }}
            </p>
          </div>
          <div v-else class="history-list">
            <div
              v-for="session in filteredChatSessions"
              :key="session.sessionId"
              :class="['history-item', { active: currentSessionId === session.sessionId }]"
              @click="selectHistorySession(session)"
            >
              <div class="history-item-header">
                <h4 class="history-item-title">{{ session.title }}</h4>
                <div class="history-item-actions">
                  <span class="history-item-character">{{ session.cardName }}</span>
                  <button 
                    class="delete-history-btn"
                    @click.stop="deleteHistorySession(session)"
                    :title="'删除对话记录'"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3,6 5,6 21,6"></polyline>
                      <path d="M19,6V20a2,2,0,0,1-2,2H7a2,2,0,0,1-2-2V6M8,6V4a2,2,0,0,1,2-2h4a2,2,0,0,1,2,2V6"></path>
                      <line x1="10" y1="11" x2="10" y2="17"></line>
                      <line x1="14" y1="11" x2="14" y2="17"></line>
                    </svg>
                  </button>
                </div>
              </div>
              <p class="history-item-preview">{{ session.firstMessage || '新对话' }}</p>
              <div class="history-item-meta">
                <span class="history-item-count">{{ session.messageCount }} 条</span>
                <span class="history-item-time">{{ formatHistoryTime(session.lastTime) }}</span>
              </div>
            </div>
          </div>
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

    <!-- 删除历史记录确认对话框 -->
    <Teleport to="body">
      <transition name="confirm-fade" appear>
        <div v-if="deleteConfirm.visible" class="confirm-modal">
          <div class="modal-backdrop" @click="!deleteConfirm.loading && closeDeleteConfirm()"></div>
          <div class="modal-dialog compact" @click.stop>
            <div class="modal-header">
              <h4>删除对话记录</h4>
              <button class="close-btn" type="button" @click="closeDeleteConfirm" :disabled="deleteConfirm.loading">
                <span>&times;</span>
              </button>
            </div>
            <div class="modal-content">
              <p class="confirm-message">
                确定要删除与"<strong>{{ deleteConfirm.session?.cardName }}</strong>"的对话记录吗？
                <br>
                <span class="warning-text">该操作无法恢复。</span>
              </p>
            </div>
            <div class="modal-actions">
              <button
                type="button"
                class="btn secondary"
                @click="closeDeleteConfirm"
                :disabled="deleteConfirm.loading"
              >取消</button>
              <button
                type="button"
                class="btn danger"
                @click="confirmDeleteHistory"
                :disabled="deleteConfirm.loading"
              >
                <span v-if="deleteConfirm.loading" class="loading-indicator"></span>
                确认删除
              </button>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>

    <!-- 右侧工作区 -->
    <main class="workspace">
      <!-- 工作区内容 -->
      <div class="workspace-content">
        <transition name="tab-fade" mode="out-in">
          <!-- 欢迎页面 -->
          <div v-if="activeTab === 'welcome'" key="welcome" class="welcome-view" :class="{ 'first-load': isFirstLoad }">
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

          <!-- 角色市场页面 -->
          <div v-else-if="activeTab === 'market'" key="market" class="market-view">
            <CharacterMarket 
              @create-new="handleCreateNew" 
              @character-selected="handleCharacterSelected"
              @character-deselected="handleCharacterDeselected"
            />
          </div>

          <!-- 创建角色页面 -->
          <div v-else-if="activeTab === 'create'" key="create" class="create-view">
            <CharacterCreator @created="handleCharacterCreated" />
          </div>

          <!-- 语音对话页面 -->
          <div v-else-if="activeTab === 'voice'" key="voice" class="voice-view">
            <VoiceChat />
          </div>
        </transition>
      </div>
    </main>
  </div>
</template>
<script>
import { ref, reactive, onMounted, computed, provide } from 'vue'
import { useRouter } from 'vue-router'
import { authAPI, voiceChatAPI } from '@/api'
import { storage, tokenUtils, format } from '@/utils'
import { notification } from '@/utils/notification'
import CharacterMarket from '@/components/CharacterMarket.vue'
import CharacterCreator from '@/components/CharacterCreator.vue'
import VoiceChat from '@/views/VoiceChat.vue'

export default {
  name: 'Dashboard',
  components: {
    CharacterMarket,
    CharacterCreator,
    VoiceChat
  },
  setup() {
    const router = useRouter()
    const user = ref(null)
    const activeTab = ref('welcome')
    const sidebarCollapsed = ref(false)
    const isDarkMode = ref(false)
    const isFirstLoad = ref(true) // 标记首次加载
    
    // 历史对话相关
    const chatSessions = ref([])
    const loadingHistory = ref(false)
    const currentSessionId = ref(null)
    const selectedCharacterInMarket = ref(null) // 当前在市场中选中的角色
    const selectedSessionData = ref(null) // 当前选中的会话详细数据

    // 删除确认对话框
    const deleteConfirm = reactive({
      visible: false,
      session: null,
      loading: false
    })

    // 提供给子组件的会话数据
    provide('selectedSession', {
      sessionId: currentSessionId,
      sessionData: selectedSessionData
    })

    const navTabs = [
      { id: 'market', label: '角色市场' },
      { id: 'create', label: '创建角色' },
      { id: 'voice', label: '语音对话' }
    ]

    // 根据当前选中角色筛选历史对话
    const filteredChatSessions = computed(() => {
      if (!selectedCharacterInMarket.value) {
        // 没有选中角色时显示所有对话
        return chatSessions.value
      } else {
        // 选中角色时只显示与该角色的对话
        return chatSessions.value.filter(session => session.cardId === selectedCharacterInMarket.value.id)
      }
    })

    // 获取历史对话列表
    const fetchChatSessions = async () => {
      loadingHistory.value = true
      try {
        const response = await voiceChatAPI.getUserSessions({ limit: 20 })
        if (response?.code === 200) {
          chatSessions.value = Array.isArray(response.data) ? response.data : []
        } else {
          chatSessions.value = []
          console.error('获取历史对话失败:', response?.message)
        }
      } catch (error) {
        console.error('获取历史对话异常:', error)
        chatSessions.value = []
      } finally {
        loadingHistory.value = false
      }
    }

    // 刷新历史记录
    const refreshHistory = () => {
      fetchChatSessions()
    }

    // 选择历史对话会话
    const selectHistorySession = (session) => {
      if (currentSessionId.value === session.sessionId) {
        return
      }
      
      // 设置当前会话ID和数据
      currentSessionId.value = session.sessionId
      selectedSessionData.value = {
        historyId: session.sessionId, // 在新设计中，这实际上是historyId
        sessionId: session.sessionId,
        cardId: session.cardId,
        cardName: session.cardName,
        title: session.title,
        loadHistory: true // 标记需要加载历史记录
      }
      
      // 切换到语音对话标签
      setActiveTab('voice')
    }

    // 打开删除确认对话框
    const deleteHistorySession = (session) => {
      deleteConfirm.session = session
      deleteConfirm.visible = true
      deleteConfirm.loading = false
    }

    // 关闭删除确认对话框
    const closeDeleteConfirm = () => {
      if (deleteConfirm.loading) return
      deleteConfirm.visible = false
      deleteConfirm.session = null
    }

    // 确认删除历史记录
    const confirmDeleteHistory = async () => {
      if (deleteConfirm.loading || !deleteConfirm.session) {
        return
      }

      try {
        deleteConfirm.loading = true
        
        // 调用删除API
        const response = await voiceChatAPI.deleteHistory(deleteConfirm.session.sessionId)
        
        if (response?.code === 200) {
          // 从列表中移除该会话
          chatSessions.value = chatSessions.value.filter(s => s.sessionId !== deleteConfirm.session.sessionId)
          
          // 如果删除的是当前选中的会话，清除选中状态
          if (currentSessionId.value === deleteConfirm.session.sessionId) {
            currentSessionId.value = null
            selectedSessionData.value = null
            // 如果在语音标签页，切换到欢迎页
            if (activeTab.value === 'voice') {
              setActiveTab('welcome')
            }
          }
          
          notification.success('对话记录删除成功')
        } else {
          notification.error('删除失败: ' + (response?.message || '未知错误'))
        }
      } catch (error) {
        console.error('删除历史记录失败:', error)
        notification.error('删除失败，请稍后再试')
      } finally {
        deleteConfirm.loading = false
        closeDeleteConfirm()
      }
    }

    // 格式化历史时间
    const formatHistoryTime = (timestamp) => {
      if (!timestamp) return ''
      return format.date(timestamp, 'MM-DD HH:mm')
    }

    // 监听角色市场中的角色选择事件
    const handleCharacterSelected = (character) => {
      selectedCharacterInMarket.value = character
      // 清除当前选中的历史会话
      currentSessionId.value = null
      selectedSessionData.value = null
    }

    // 监听角色市场中的角色取消选择事件
    const handleCharacterDeselected = () => {
      selectedCharacterInMarket.value = null
      // 清除当前选中的历史会话
      currentSessionId.value = null
      selectedSessionData.value = null
    }

    const setActiveTab = (tabId) => {
      activeTab.value = tabId
      // 切换标签页后，标记不再是首次加载
      if (isFirstLoad.value) {
        isFirstLoad.value = false
      }
      // 如果不是切换到语音对话标签，清除会话数据
      if (tabId !== 'voice') {
        currentSessionId.value = null
        selectedSessionData.value = null
      }
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
        } else {
          console.error('本地用户信息缺失，清除认证信息')
          tokenUtils.clearAuth()
          router.push('/login')
        }
      } catch (error) {
        console.error('加载用户信息失败:', error)
        console.error('本地用户信息缺失，清除认证信息')
        tokenUtils.clearAuth()
        router.push('/login')
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

    const handleCharacterCreated = (character) => {
      console.log('角色创建成功:', character)
      // 创建成功后切换到角色市场
      setActiveTab('market')
      // TODO: 显示成功提示
    }

    const handleCreateNew = () => {
      setActiveTab('create')
    }

    const goToWelcome = () => {
      activeTab.value = 'welcome'
    }

    onMounted(() => {
      loadUserInfo()
      initTheme()
      fetchChatSessions() // 加载历史对话
    })

    return {
      user,
      activeTab,
      navTabs,
      sidebarCollapsed,
      isDarkMode,
      isFirstLoad,
      
      // 历史对话相关
      chatSessions,
      loadingHistory,
      currentSessionId,
      selectedCharacterInMarket,
      selectedSessionData,
      filteredChatSessions,
      
      // 方法
      setActiveTab,
      toggleSidebar,
      toggleTheme,
      handleLogout,
      handleCharacterCreated,
      handleCreateNew,
      goToWelcome,
      
      // 历史对话方法
      fetchChatSessions,
      refreshHistory,
      selectHistorySession,
      deleteHistorySession,
      closeDeleteConfirm,
      confirmDeleteHistory,
      deleteConfirm,
      formatHistoryTime,
      handleCharacterSelected,
      handleCharacterDeselected
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
  
  &.clickable {
    cursor: pointer;
    transition: color 0.3s ease, opacity 0.3s ease;
    
    &:hover {
      color: #f59e0b; /* 使用温暖的黄色作为hover状态 */
      opacity: 0.9;
    }
  }
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
  display: flex;
  flex-direction: column;
}

.history-header {
  margin-bottom: $spacing;
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  flex-shrink: 0;
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
  flex: 1;
}

.history-refresh-btn {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 0.25rem;
  border-radius: $border-radius-sm;
  transition: all $transition-fast ease;
  
  &:hover:not(:disabled) {
    color: var(--text-primary);
    background: var(--background-hover);
  }
  
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
  
  svg.spinning {
    animation: spin 1s linear infinite;
  }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.history-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.history-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100px;
  gap: $spacing-xs;
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--border-color);
  border-top: 2px solid var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
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
  text-align: center;
}

.history-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.history-item {
  padding: 0.75rem;
  background: var(--background-tertiary);
  border: 1px solid transparent;
  border-radius: $border-radius;
  cursor: pointer;
  transition: all $transition-fast ease;
  
  &:hover {
    background: var(--background-hover);
    border-color: var(--border-light);
  }
  
  &.active {
    background: var(--primary-color);
    color: white;
    border-color: var(--primary-color);
  }
}

.history-item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 0.5rem;
  gap: 0.5rem;
}

.history-item-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}

.history-item-title {
  font-size: 0.875rem;
  font-weight: 500;
  margin: 0;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-item-character {
  font-size: 0.75rem;
  opacity: 0.8;
  flex-shrink: 0;
}

.delete-history-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  background: transparent;
  border: none;
  border-radius: 4px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
  opacity: 0;
  
  &:hover {
    background: rgba(239, 68, 68, 0.1);
    color: #ef4444;
  }
  
  &:active {
    transform: scale(0.95);
  }
  
  svg {
    width: 14px;
    height: 14px;
    stroke-width: 2;
  }
}

.history-item:hover .delete-history-btn {
  opacity: 1;
}

.history-item.active .delete-history-btn {
  color: rgba(255, 255, 255, 0.8);
  
  &:hover {
    background: rgba(255, 255, 255, 0.2);
    color: white;
  }
}

.history-item-preview {
  margin: 0 0 0.5rem 0;
  font-size: 0.8rem;
  opacity: 0.8;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.history-item-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.75rem;
  opacity: 0.7;
}

.history-item-count {
  flex-shrink: 0;
}

.history-item-time {
  font-size: 0.7rem;
  opacity: 0.6;
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

// 新页面样式
.market-view,
.create-view,
.voice-view {
  height: 100%;
  display: flex;
  flex-direction: column;
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

// 标签页切换过渡动画
.tab-fade-enter-active,
.tab-fade-leave-active {
  transition: opacity 0.25s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.tab-fade-enter-from {
  opacity: 0;
}

.tab-fade-leave-to {
  opacity: 0;
}

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

// 为功能卡片添加延迟动画（仅在页面首次加载时触发）
.welcome-view.first-load .feature-item {
  animation: fadeInUp 0.3s ease-out;
  
  &:nth-child(1) { animation-delay: 0.05s; }
  &:nth-child(2) { animation-delay: 0.1s; }
  &:nth-child(3) { animation-delay: 0.15s; }
}

/* 删除确认对话框样式 */
.confirm-modal {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 99999;
  background: rgba(0, 0, 0, 0.5); /* 半透明黑色背景 */
}

.modal-backdrop {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}

.modal-dialog {
  position: relative;
  background: #ffffff; /* 纯白色背景 */
  border-radius: 12px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
  max-width: 90vw;
  width: 100%;
  max-height: 90vh;
  overflow-y: auto;
  border: 1px solid #e5e7eb;

  &.compact {
    width: 400px;
    max-width: 90vw;
  }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid #e5e7eb;

  h4 {
    margin: 0;
    font-size: 1.125rem;
    font-weight: 600;
    color: #111827; /* 深灰色文本 */
  }

  .close-btn {
    background: none;
    border: none;
    color: var(--text-secondary);
    font-size: 24px;
    line-height: 1;
    cursor: pointer;
    padding: 0;
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 6px;
    transition: all 0.2s ease;

    &:hover:not(:disabled) {
      background: var(--surface-hover);
      color: var(--text-primary);
    }

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }
}

.modal-content {
  padding: 20px 24px;

  .confirm-message {
    margin: 0;
    font-size: 0.9rem;
    color: #374151; /* 灰色文本 */
    line-height: 1.5;

    .warning-text {
      color: #dc2626; /* 红色警告文本 */
      font-weight: 500;
      font-size: 0.85rem;
    }
  }
}

.modal-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding: 16px 24px 20px;

  .btn {
    padding: 8px 16px;
    border-radius: 6px;
    font-size: 0.875rem;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s ease;
    border: 1px solid transparent;
    min-width: 80px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    &.secondary {
      background: var(--surface-color);
      color: var(--text-primary);
      border-color: var(--border-color);

      &:hover:not(:disabled) {
        background: var(--surface-hover);
        border-color: var(--border-hover);
      }
    }

    &.danger {
      background: var(--error-color);
      color: white;

      &:hover:not(:disabled) {
        background: #dc2626;
      }

      .loading-indicator {
        width: 14px;
        height: 14px;
        border: 2px solid rgba(255, 255, 255, 0.3);
        border-top-color: white;
        border-radius: 50%;
        animation: spin 1s linear infinite;
      }
    }
  }
}

/* 删除确认弹窗的动画 */
.confirm-fade-enter-active, .confirm-fade-leave-active {
  transition: opacity 0.15s ease;
}

.confirm-fade-enter-from, .confirm-fade-leave-to {
  opacity: 0;
}
</style>
