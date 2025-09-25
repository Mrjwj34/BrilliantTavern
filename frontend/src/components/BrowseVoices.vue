<template>
  <div class="browse-voices">
    <div class="browse-header">
      <div class="header-left">
        <h3 class="browse-title">浏览音色</h3>
        <p class="browse-subtitle">发现社区分享的优质音色</p>
      </div>
      <div class="header-actions">
        <div class="search-box">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="search-icon">
            <circle cx="11" cy="11" r="8"/>
            <path d="M21 21l-4.35-4.35"/>
          </svg>
          <input 
            v-model="searchKeyword" 
            type="text" 
            placeholder="搜索音色..."
            @keyup.enter="handleSearch"
            class="search-input"
          />
          <button @click="handleSearch" class="search-btn">搜索</button>
        </div>
        <button @click="refreshVoices" class="refresh-btn" :disabled="loading">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ spin: loading }">
            <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 4"/>
            <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 20"/>
            <path d="M21 4v5h-5"/>
            <path d="M3 20v-5h5"/>
          </svg>
        </button>
      </div>
    </div>

    <div class="browse-content">
      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
            <path d="M21 12a9 9 0 11-6.219-8.56"/>
          </svg>
        </div>
        <p>加载中...</p>
      </div>

      <!-- 空状态 -->
      <div v-else-if="voices.length === 0" class="empty-state">
        <div class="empty-icon">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="11" cy="11" r="8"/>
            <path d="M21 21l-4.35-4.35"/>
          </svg>
        </div>
        <h4>{{ searchKeyword ? '没有找到相关音色' : '暂无公开音色' }}</h4>
        <p>{{ searchKeyword ? '试试其他关键词' : '等待社区贡献更多优质音色' }}</p>
        <button v-if="searchKeyword" @click="clearSearch" class="clear-search-btn">
          清除搜索
        </button>
      </div>

      <!-- 音色列表 -->
      <div v-else class="voices-grid">
        <div 
          v-for="voice in voices" 
          :key="voice.id" 
          class="voice-card"
          @click="selectVoice(voice)"
          :class="{ selected: selectedVoice?.id === voice.id }"
        >
          <div class="voice-header">
            <div class="voice-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                <line x1="12" y1="19" x2="12" y2="23"/>
                <line x1="8" y1="23" x2="16" y2="23"/>
              </svg>
            </div>
            <div class="voice-actions">
              <button @click.stop="testVoice(voice)" class="test-btn" title="试听">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polygon points="5 3,19 12,5 21,5 3"/>
                </svg>
              </button>
              <button @click.stop="useVoice(voice)" class="use-btn" title="使用">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 11l3 3L22 4"/>
                  <path d="M21 12c0 4.97-4.03 9-9 9s-9-4.03-9-9 4.03-9 9-9c1.5 0 2.91.37 4.15 1.02"/>
                </svg>
              </button>
              <button @click.stop="favoriteVoice(voice)" class="favorite-btn" :class="{ active: voice.isFavorited }" title="收藏">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                </svg>
              </button>
            </div>
          </div>
          
          <div class="voice-info">
            <h4 class="voice-name">{{ voice.name }}</h4>
            <p v-if="voice.description" class="voice-description">{{ voice.description }}</p>
            <div class="voice-meta">
              <div class="voice-creator">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
                <span>{{ voice.creatorName || '匿名' }}</span>
              </div>
              <span class="voice-date">{{ formatDate(voice.createdAt) }}</span>
            </div>
            
            <!-- 使用统计 -->
            <div class="voice-stats">
              <div class="stat-item">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polygon points="5 3,19 12,5 21,5 3"/>
                </svg>
                <span>{{ voice.playCount || 0 }}</span>
              </div>
              <div class="stat-item">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                </svg>
                <span>{{ voice.favoriteCount || 0 }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive, onMounted, computed } from 'vue'
import { ttsAPI } from '@/api'
import { storage } from '@/utils'

export default {
  name: 'BrowseVoices',
  emits: ['select-voice'],
  setup(props, { emit }) {
    const voices = ref([])
    const selectedVoice = ref(null)
    const loading = ref(false)
    const searchKeyword = ref('')
    const isSearching = ref(false)
    
    // 获取公开音色列表
    const fetchVoices = async () => {
      loading.value = true
      try {
        const response = await ttsAPI.getPublicVoices()
        voices.value = (response.data || []).map(voice => ({
          ...voice,
          isFavorited: false, // TODO: 从后端获取收藏状态
          playCount: Math.floor(Math.random() * 1000), // 模拟数据
          favoriteCount: Math.floor(Math.random() * 100), // 模拟数据
          creatorName: voice.creatorName || '匿名用户' // 假设后端返回创建者姓名
        }))
      } catch (error) {
        console.error('获取公开音色列表失败:', error)
        voices.value = []
      } finally {
        loading.value = false
      }
    }
    
    // 搜索音色
    const searchVoices = async (keyword) => {
      if (!keyword.trim()) {
        await fetchVoices()
        return
      }
      
      loading.value = true
      try {
        const user = storage.get('user')
        const response = await ttsAPI.searchVoices(keyword, user?.userId || '', true)
        voices.value = (response.data || []).map(voice => ({
          ...voice,
          isFavorited: false,
          playCount: Math.floor(Math.random() * 1000),
          favoriteCount: Math.floor(Math.random() * 100),
          creatorName: voice.creatorName || '匿名用户'
        }))
      } catch (error) {
        console.error('搜索音色失败:', error)
        voices.value = []
      } finally {
        loading.value = false
      }
    }
    
    // 刷新音色列表
    const refreshVoices = () => {
      if (searchKeyword.value.trim()) {
        searchVoices(searchKeyword.value)
      } else {
        fetchVoices()
      }
    }
    
    // 处理搜索
    const handleSearch = () => {
      searchVoices(searchKeyword.value)
    }
    
    // 清除搜索
    const clearSearch = () => {
      searchKeyword.value = ''
      fetchVoices()
    }
    
    // 选择音色
    const selectVoice = (voice) => {
      selectedVoice.value = voice
      emit('select-voice', voice)
    }
    
    // 试听音色
    const testVoice = async (voice) => {
      try {
        const response = await ttsAPI.generateSpeech('这是一段测试音频，用于预览音色效果', voice.id)
        
        // 创建音频播放
        const audioBlob = new Blob([response.data], { type: 'audio/wav' })
        const audioUrl = URL.createObjectURL(audioBlob)
        const audio = new Audio(audioUrl)
        
        audio.play().then(() => {
          // 播放完成后清理
          audio.onended = () => {
            URL.revokeObjectURL(audioUrl)
          }
          
          // 更新播放次数（模拟）
          voice.playCount = (voice.playCount || 0) + 1
        }).catch(error => {
          console.error('音频播放失败:', error)
          URL.revokeObjectURL(audioUrl)
          alert('音频播放失败')
        })
        
      } catch (error) {
        console.error('试听失败:', error)
        alert('试听失败: ' + error.message)
      }
    }
    
    // 使用音色
    const useVoice = (voice) => {
      selectVoice(voice)
      // TODO: 可以添加更多使用音色的逻辑
      alert(`已选择音色: ${voice.name}`)
    }
    
    // 收藏音色
    const favoriteVoice = (voice) => {
      // TODO: 调用后端API收藏/取消收藏
      voice.isFavorited = !voice.isFavorited
      if (voice.isFavorited) {
        voice.favoriteCount = (voice.favoriteCount || 0) + 1
      } else {
        voice.favoriteCount = Math.max(0, (voice.favoriteCount || 0) - 1)
      }
      
      console.log(`${voice.isFavorited ? '收藏' : '取消收藏'}音色:`, voice.name)
    }
    
    // 格式化日期
    const formatDate = (dateString) => {
      const date = new Date(dateString)
      const now = new Date()
      const diffTime = Math.abs(now - date)
      const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
      
      if (diffDays === 1) return '今天'
      if (diffDays <= 7) return `${diffDays}天前`
      if (diffDays <= 30) return `${Math.ceil(diffDays / 7)}周前`
      if (diffDays <= 365) return `${Math.ceil(diffDays / 30)}个月前`
      
      return date.toLocaleDateString('zh-CN', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
      })
    }
    
    onMounted(() => {
      fetchVoices()
    })
    
    return {
      voices,
      selectedVoice,
      loading,
      searchKeyword,
      refreshVoices,
      handleSearch,
      clearSearch,
      selectVoice,
      testVoice,
      useVoice,
      favoriteVoice,
      formatDate
    }
  }
}
</script>

<style lang="scss" scoped>
.browse-voices {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--background-primary);
}

.browse-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: $spacing-lg;
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.header-left {
  flex: 1;
  
  .browse-title {
    font-size: 1.2rem;
    font-weight: 600;
    color: var(--text-primary);
    margin: 0 0 $spacing-xs 0;
  }
  
  .browse-subtitle {
    font-size: 0.9rem;
    color: var(--text-secondary);
    margin: 0;
  }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: $spacing;
}

.search-box {
  display: flex;
  align-items: center;
  background: var(--background-secondary);
  border: 1px solid var(--border-color);
  border-radius: $border-radius;
  padding: $spacing-xs;
  gap: $spacing-xs;
  min-width: 280px;
  
  &:focus-within {
    border-color: var(--primary-color);
    box-shadow: 0 0 0 3px rgba(217, 119, 6, 0.1);
  }
  
  .search-icon {
    color: var(--text-tertiary);
    flex-shrink: 0;
  }
  
  .search-input {
    flex: 1;
    border: none;
    background: transparent;
    color: var(--text-primary);
    outline: none;
    font-size: 0.9rem;
    
    &::placeholder {
      color: var(--text-placeholder);
    }
  }
  
  .search-btn {
    background: var(--primary-color);
    color: white;
    border: none;
    border-radius: $border-radius-sm;
    padding: $spacing-xs $spacing-sm;
    font-size: 0.85rem;
    font-weight: 500;
    cursor: pointer;
    transition: all $transition-fast ease;
    
    &:hover {
      background: var(--primary-dark);
    }
  }
}

.refresh-btn {
  width: 40px;
  height: 40px;
  background: var(--background-tertiary);
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  border-radius: $border-radius;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all $transition-normal ease;
  
  &:hover:not(:disabled) {
    background: var(--background-primary);
    color: var(--text-primary);
  }
  
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.browse-content {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-lg;
}

// 加载状态
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--text-secondary);
  
  .loading-spinner {
    margin-bottom: $spacing;
    color: var(--primary-color);
  }
}

// 空状态
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  text-align: center;
  
  .empty-icon {
    margin-bottom: $spacing-lg;
    color: var(--text-tertiary);
  }
  
  h4 {
    font-size: 1.2rem;
    color: var(--text-primary);
    margin-bottom: $spacing-sm;
  }
  
  p {
    color: var(--text-secondary);
    margin-bottom: $spacing-lg;
  }
}

.clear-search-btn {
  padding: $spacing-sm $spacing-lg;
  background: var(--background-tertiary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-radius: $border-radius;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-normal ease;
  
  &:hover {
    background: var(--background-primary);
  }
}

// 音色网格
.voices-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: $spacing-lg;
}

.voice-card {
  background: var(--background-secondary);
  border: 1px solid var(--border-light);
  border-radius: $border-radius-lg;
  padding: $spacing-lg;
  cursor: pointer;
  transition: all $transition-normal ease;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-medium);
    border-color: var(--primary-color);
  }
  
  &.selected {
    border-color: var(--primary-color);
    box-shadow: 0 0 0 3px rgba(217, 119, 6, 0.1);
  }
}

.voice-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing;
}

.voice-icon {
  width: 40px;
  height: 40px;
  background: var(--secondary-color);
  color: white;
  border-radius: $border-radius;
  display: flex;
  align-items: center;
  justify-content: center;
}

.voice-actions {
  display: flex;
  gap: $spacing-xs;
}

.test-btn,
.use-btn,
.favorite-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: $border-radius-sm;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all $transition-normal ease;
}

.test-btn {
  background: var(--success-color);
  color: white;
  
  &:hover {
    background: darken(#059669, 10%);
    transform: scale(1.1);
  }
}

.use-btn {
  background: var(--primary-color);
  color: white;
  
  &:hover {
    background: var(--primary-dark);
    transform: scale(1.1);
  }
}

.favorite-btn {
  background: var(--background-tertiary);
  color: var(--text-secondary);
  
  &:hover,
  &.active {
    background: var(--error-color);
    color: white;
    transform: scale(1.1);
  }
}

.voice-info {
  .voice-name {
    font-size: 1.1rem;
    font-weight: 600;
    color: var(--text-primary);
    margin: 0 0 $spacing-sm 0;
  }
  
  .voice-description {
    color: var(--text-secondary);
    font-size: 0.9rem;
    line-height: 1.4;
    margin: 0 0 $spacing 0;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  
  .voice-meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing;
    font-size: 0.8rem;
    
    .voice-creator {
      display: flex;
      align-items: center;
      gap: $spacing-xs;
      color: var(--text-secondary);
      
      svg {
        flex-shrink: 0;
      }
      
      span {
        font-weight: 500;
      }
    }
    
    .voice-date {
      color: var(--text-tertiary);
    }
  }
  
  .voice-stats {
    display: flex;
    gap: $spacing-lg;
    
    .stat-item {
      display: flex;
      align-items: center;
      gap: $spacing-xs;
      color: var(--text-tertiary);
      font-size: 0.8rem;
      
      svg {
        flex-shrink: 0;
      }
    }
  }
}

// 动画
@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.spin {
  animation: spin 1s linear infinite;
}

// 响应式
@media (max-width: 768px) {
  .browse-header {
    flex-direction: column;
    gap: $spacing;
    align-items: stretch;
  }
  
  .header-actions {
    flex-direction: column;
  }
  
  .search-box {
    min-width: auto;
    width: 100%;
  }
  
  .voices-grid {
    grid-template-columns: 1fr;
    gap: $spacing;
  }
  
  .browse-content {
    padding: $spacing;
  }
}
</style>
