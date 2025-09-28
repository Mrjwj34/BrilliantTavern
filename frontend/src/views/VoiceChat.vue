<template>
  <div class="voice-chat" :class="{ 'sidebar-collapsed': rightSidebarCollapsed }">
    <!-- 主内容区域 -->
    <div class="main-content" :class="{ 
      'centered': currentView === 'welcome' || currentView === 'voice-chat',
      'full-width': currentView === 'voice-clone'
    }">
      <!-- 界面切换动画 -->
      <transition name="view-transition" mode="out-in" appear>
        <!-- 引导界面 -->
        <div v-if="currentView === 'welcome'" key="welcome" class="welcome-section">
          <div class="welcome-content">
            <p class="welcome-description">
              <em>欢迎使用语音对话！创建新的音色或者即刻开始对话...</em>
            </p>
          </div>
        </div>

        <!-- 音色克隆配置界面 -->
        <div v-else-if="currentView === 'voice-clone'" key="voice-clone" class="voice-clone-section">
          <!-- 子视图切换 -->
          <transition name="slide-fade" mode="out-in" appear>
            <div v-if="currentSubView === 'new-voice'" key="new-voice" class="config-view">
              <VoiceCloneConfig @back="switchToView('welcome')" />
            </div>
            <div v-else-if="currentSubView === 'my-voices'" key="my-voices" class="config-view">
              <MyVoices @create-new="switchToSubView('new-voice')" @select-voice="handleVoiceSelect" />
            </div>
            <div v-else-if="currentSubView === 'browse-voices'" key="browse-voices" class="config-view">
              <BrowseVoices @select-voice="handleVoiceSelect" />
            </div>
            <div v-else key="default" class="config-view">
              <VoiceCloneConfig @back="switchToView('welcome')" />
            </div>
          </transition>
        </div>

        <!-- 语音对话界面 -->
        <div v-else-if="currentView === 'voice-chat'" key="voice-chat" class="voice-chat-section">
          <RoundVoiceChat 
            v-if="currentChatMode === 'round'" 
            @character-selected="handleCharacterSelected"
            @character-deselected="handleCharacterDeselected"
          />
          <div v-else class="chat-placeholder">
            <div class="chat-icon">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
              </svg>
            </div>
            <h3>请选择右侧的「轮次对话」进入体验</h3>
            <p>支持实时语音识别与AI回应，敬请体验。</p>
          </div>
        </div>
      </transition>
    </div>

    <!-- 右侧功能侧边栏 -->
    <aside class="function-sidebar" :class="{ collapsed: rightSidebarCollapsed }">      
      <div class="sidebar-header">
        <button @click="toggleRightSidebar" class="toggle-btn" :title="rightSidebarCollapsed ? '展开侧边栏' : '收起侧边栏'">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline :points="rightSidebarCollapsed ? '15,18 9,12 15,6' : '9,18 15,12 9,6'"/>
          </svg>
        </button>
        <h3 class="sidebar-title">功能</h3>
      </div>
      
      <div class="sidebar-content">
        <!-- 音色克隆功能区 -->
        <div class="function-group">
          <button 
            @click="toggleSection('voice-clone')"
            :class="['function-tab', { active: activeSections.includes('voice-clone') }]"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
              <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
              <line x1="12" y1="19" x2="12" y2="23"/>
              <line x1="8" y1="23" x2="16" y2="23"/>
            </svg>
            <span>&nbsp;音色克隆</span>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" 
                 :class="['expand-icon', { expanded: activeSections.includes('voice-clone') }]">
              <polyline points="6,9 12,15 18,9"/>
            </svg>
          </button>
          
          <transition name="slide-down" appear>
            <div v-show="activeSections.includes('voice-clone')" class="function-items">
              <button 
                @click="switchToSubView('new-voice')"
                :class="['function-item', { active: currentSubView === 'new-voice' }]"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="5" x2="12" y2="19"/>
                  <line x1="5" y1="12" x2="19" y2="12"/>
                </svg>
                新建音色
              </button>
              <button 
                @click="switchToSubView('my-voices')"
                :class="['function-item', { active: currentSubView === 'my-voices' }]"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
                我的音色
              </button>
              <button 
                @click="switchToSubView('browse-voices')"
                :class="['function-item', { active: currentSubView === 'browse-voices' }]"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="11" cy="11" r="8"/>
                  <path d="M21 21l-4.35-4.35"/>
                </svg>
                浏览音色
              </button>
            </div>
          </transition>
        </div>

        <!-- 语音对话功能区 -->
        <div class="function-group">
          <button 
            @click="toggleSection('voice-chat')"
            :class="['function-tab', { active: activeSections.includes('voice-chat') }]"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
            </svg>
            <span>&nbsp;语音对话</span>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" 
                 :class="['expand-icon', { expanded: activeSections.includes('voice-chat') }]">
              <polyline points="6,9 12,15 18,9"/>
            </svg>
          </button>
          
          <transition name="slide-down" appear>
            <div v-show="activeSections.includes('voice-chat')" class="function-items">
              <button
                @click="switchToChatMode('round')"
                :class="['function-item', { active: currentChatMode === 'round' }]"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
                </svg>
                轮次对话
              </button>
            </div>
          </transition>
        </div>
      </div>
    </aside>
    
    <!-- 收起时的浮动按钮 -->
    <div v-show="rightSidebarCollapsed" class="collapsed-toggle" 
         :class="{ 'show': showFloatingBtn }"
         @mouseenter="showFloatingBtn = true" 
         @mouseleave="showFloatingBtn = false">
      <button @click="toggleRightSidebar" class="floating-toggle-btn" title="展开侧边栏">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="15,18 9,12 15,6"/>
        </svg>
      </button>
    </div>
    
    <!-- 右边缘触发区域 -->
    <div v-show="rightSidebarCollapsed" class="edge-trigger" 
         @mouseenter="showFloatingBtn = true" 
         @mouseleave="showFloatingBtn = false">
    </div>
  </div>
</template>

<script>
import { ref, reactive, onMounted, inject, watch } from 'vue'
import VoiceCloneConfig from '@/components/VoiceCloneConfig.vue'
import MyVoices from '@/components/MyVoices.vue'
import BrowseVoices from '@/components/BrowseVoices.vue'
import RoundVoiceChat from '@/components/RoundVoiceChat.vue'

export default {
  name: 'VoiceChat',
  emits: ['character-selected', 'character-deselected'],
  components: {
    VoiceCloneConfig,
    MyVoices,
    BrowseVoices,
    RoundVoiceChat
  },
  setup(props, { emit }) {
    // 注入来自Dashboard的会话数据
    const selectedSession = inject('selectedSession', null)
    
    // 当前主视图
    const currentView = ref('welcome')
    // 当前子视图
    const currentSubView = ref('')
    // 激活的功能区域
    const activeSections = ref(['voice-clone'])
    // 右侧边栏收起状态
    const rightSidebarCollapsed = ref(false)
    // 浮动按钮显示状态
    const showFloatingBtn = ref(false)
    // 语音对话模式
    const currentChatMode = ref('')

    // 切换主视图
    const switchToView = (view) => {
      currentView.value = view
    }

    // 切换子视图
    const switchToSubView = (subView) => {
      currentSubView.value = subView
      currentView.value = 'voice-clone'
      
      // 确保音色克隆区域是展开的
      if (!activeSections.value.includes('voice-clone')) {
        activeSections.value.push('voice-clone')
      }
      
      // 收起语音对话区域
      const chatIndex = activeSections.value.indexOf('voice-chat')
      if (chatIndex > -1) {
        activeSections.value.splice(chatIndex, 1)
      }
    }

    // 切换功能区域
    const toggleSection = (section) => {
      const index = activeSections.value.indexOf(section)
      if (index > -1) {
        activeSections.value.splice(index, 1)
        if (section === 'voice-chat') {
          currentChatMode.value = ''
          currentView.value = activeSections.value.includes('voice-clone') ? 'voice-clone' : 'welcome'
        }
      } else {
        // 关闭其他区域，只保留当前区域
        activeSections.value = [section]
        
        // 根据区域设置对应的视图
        if (section === 'voice-clone') {
          switchToView('voice-clone')
        } else if (section === 'voice-chat') {
          // 自动选择轮次对话模式
          currentChatMode.value = 'round'
          switchToView('voice-chat')
        }
      }
    }

    // 切换语音对话模式
    const switchToChatMode = (mode) => {
      currentChatMode.value = mode
      currentView.value = 'voice-chat'
      if (!activeSections.value.includes('voice-chat')) {
        activeSections.value = ['voice-chat']
      }
    }

    // 处理音色选择
    const handleVoiceSelect = (voice) => {
      console.log('选择了音色:', voice)
      // TODO: 这里可以添加更多音色选择后的处理逻辑
    }

    // 处理角色选择事件
    const handleCharacterSelected = (character) => {
      emit('character-selected', character)
    }

    // 处理角色取消选择事件
    const handleCharacterDeselected = () => {
      emit('character-deselected')
    }

    // 切换右侧边栏
    const toggleRightSidebar = () => {
      rightSidebarCollapsed.value = !rightSidebarCollapsed.value
    }

    onMounted(() => {
      // 初始化
      console.log('VoiceChat component mounted')
    })

    // 监听来自Dashboard的会话选择
    if (selectedSession && selectedSession.sessionData) {
      watch(selectedSession.sessionData, (newSessionData) => {
        if (newSessionData && newSessionData.loadHistory && newSessionData.sessionId) {
          console.log('VoiceChat收到历史会话加载请求，切换到轮次对话:', newSessionData)
          
          // 切换到语音对话视图
          currentView.value = 'voice-chat'
          
          // 设置激活区域为语音对话
          activeSections.value = ['voice-chat']
          
          // 自动设置为轮次对话模式
          currentChatMode.value = 'round'
        }
      }, { immediate: true })
    }

    return {
      currentView,
      currentSubView,
      activeSections,
      rightSidebarCollapsed,
      showFloatingBtn,
      currentChatMode,
      switchToView,
      switchToSubView,
      toggleSection,
      switchToChatMode,
      handleVoiceSelect,
      handleCharacterSelected,
      handleCharacterDeselected,
      toggleRightSidebar
    }
  }
}
</script>

<style lang="scss" scoped>
.voice-chat {
  display: flex;
  height: 100%;
  min-height: calc(100vh - 120px); /* 确保有最小高度，但不超出容器 */
  background: var(--background-primary);
  overflow: hidden;
  margin: 0;
  padding: 0;
}

.main-content {
  flex: 1;
  display: flex;
  padding: $spacing-lg;
  overflow-y: auto;
  transition: margin-right 0.3s ease;
  
  &.centered {
    align-items: center;
    justify-content: center;
  }
  
  &.full-width {
    align-items: stretch;
    justify-content: stretch;
    padding: 0 !important;
    
    > * {
      width: 100%;
      flex: 1;
    }
  }
}

// 音色克隆界面
.voice-clone-section {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0;
  margin: 0;
}

.config-view {
  width: 100%;
  height: 100%;
  flex: 1;
  padding: 0;
  margin: 0;
  
  // 确保子组件也能占满整个空间
  > * {
    width: 100%;
    height: 100%;
  }
  
  // 覆盖VoiceCloneConfig组件的内边距，使其完全贴边
  :deep(.voice-clone-config) {
    .config-header {
      padding: $spacing-sm $spacing;
    }
    
    .config-content {
      padding: $spacing $spacing;
    }
  }
}

// 语音对话界面
.voice-chat-section {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: stretch;
  justify-content: stretch;
}

.voice-chat-section > * {
  flex: 1;
  min-width: 0;
}

.chat-placeholder {
  text-align: center;
  color: var(--text-secondary);
  margin: auto;
  
  .chat-icon {
    margin-bottom: $spacing;
    color: var(--primary-color);
    opacity: 0.7;
  }
  
  h3 {
    margin: $spacing 0;
    color: var(--text-primary);
  }
}

// 引导界面
.welcome-section {
  max-width: 600px;
  width: 100%;
  text-align: center;
}

.welcome-content {
  .welcome-description {
    font-size: 1.2rem;
    color: var(--text-secondary);
    line-height: 1.6;
    font-style: italic;
    padding: $spacing-xl 0;
  }
}

// 功能侧边栏
.function-sidebar {
  width: 280px;
  background: var(--background-secondary);
  border-left: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.3s ease, transform 0.3s ease;
  position: relative;
  
  &.collapsed {
    width: 0;
    transform: translateX(100%);
  }
}

.collapsed-toggle {
  position: fixed;
  right: 30px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1000;
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: auto;
  
  &.show {
    opacity: 0.8;
  }
  
  &:hover {
    opacity: 1 !important;
  }
}

// 右边缘触发区域
.edge-trigger {
  position: fixed;
  right: 0;
  top: 0;
  width: 50px;
  height: 100vh;
  z-index: 998;
  pointer-events: auto;
}

.floating-toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  background: var(--primary-color);
  color: white;
  cursor: pointer;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  transition: all 0.3s ease;
  
  &:hover {
    background: var(--primary-dark);
    transform: scale(1.1);
  }
}

.sidebar-header {
  padding: $spacing;
  background: var(--background-secondary);
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  min-height: 60px;
}

.sidebar-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--primary-color);
  margin: 0;
  white-space: nowrap;
  opacity: 1;
  transition: opacity 0.3s ease;
  
  .collapsed & {
    opacity: 0;
  }
}

.toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  border-radius: $border-radius-sm;
  transition: all $transition-fast ease;
  flex-shrink: 0;
  
  &:hover {
    background: var(--background-primary);
    color: var(--text-secondary);
  }
}

.sidebar-content {
  flex: 1;
  padding: $spacing-sm;
  overflow-y: auto;
  opacity: 1;
  transition: opacity 0.3s ease;
  
  .collapsed & {
    opacity: 0;
    pointer-events: none;
  }
}

.function-group {
  margin-bottom: $spacing-sm;
  
  &:last-child {
    margin-bottom: 0;
  }
}

.function-tab {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing $spacing-sm;
  background: transparent;
  border: none;
  border-radius: $border-radius;
  cursor: pointer;
  transition: all $transition-normal ease;
  font-size: 0.95rem;
  font-weight: 500;
  color: var(--text-primary);
  
  &:hover {
    background: var(--background-tertiary);
  }
  
  &.active {
    background: var(--primary-color);
    color: white;
    box-shadow: 0 2px 4px rgba(217, 119, 6, 0.3);
  }
  
  span {
    display: flex;
    align-items: center;
    gap: $spacing; /* 增加图标与文字的间距 */
    flex: 1;
    text-align: left;
  }
}

.expand-icon {
  transition: transform $transition-normal ease;
  
  &.expanded {
    transform: rotate(180deg);
  }
}

.function-items {
  margin-top: $spacing-sm;
  padding-left: $spacing;
}

.function-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing;
  background: transparent;
  border: none;
  border-radius: $border-radius-sm;
  cursor: pointer;
  transition: all $transition-normal ease;
  font-size: 0.9rem;
  color: var(--text-secondary);
  margin-bottom: $spacing-xs;
  position: relative;
  
  &:hover:not(.disabled) {
    background: var(--background-tertiary);
    color: var(--text-primary);
    transform: translateX(4px);
  }
  
  &.active {
    background: rgba(217, 119, 6, 0.1);
    color: var(--primary-color);
    font-weight: 500;
    
    svg {
      color: var(--primary-color);
    }
  }
  
  &.disabled {
    opacity: 0.6;
    cursor: not-allowed;
    
    .coming-soon {
      position: absolute;
      right: $spacing-sm;
      top: 50%;
      transform: translateY(-50%);
      background: var(--warning-color);
      color: white;
      font-size: 0.7rem;
      padding: 2px 6px;
      border-radius: 10px;
      font-weight: 500;
    }
  }
  
  &:last-child {
    margin-bottom: 0;
  }
}

// 过渡动画
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all $transition-normal cubic-bezier(0.25, 0.46, 0.45, 0.94);
  overflow: hidden;
}

.slide-down-enter-from {
  opacity: 0;
  max-height: 0;
  transform: translateY(-10px);
}

.slide-down-leave-to {
  opacity: 0;
  max-height: 0;
  transform: translateY(-10px);
}

.slide-down-enter-to,
.slide-down-leave-from {
  opacity: 1;
  max-height: 200px;
  transform: translateY(0);
}

// 响应式设计
@media (max-width: 768px) {
  .voice-chat {
    flex-direction: column;
  }
  
  .function-sidebar {
    width: 100%;
    height: auto;
    max-height: 300px;
    border-left: none;
    border-top: 1px solid var(--border-light);
    order: 2;
  }
  
  .main-content {
    order: 1;
    flex: 1;
    padding: $spacing;
  }
  
  .welcome-actions {
    flex-direction: column;
    align-items: center;
  }
  
  .welcome-btn {
    width: 100%;
    max-width: 300px;
  }
}

// 过渡动画
.view-transition-enter-active,
.view-transition-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.view-transition-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.view-transition-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
