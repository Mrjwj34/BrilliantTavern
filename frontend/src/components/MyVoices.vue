<template>
  <div class="my-voices">
    <div class="voices-header">
      <h3 class="voices-title">我的音色</h3>
      <div class="voices-actions">
        <button @click="refreshVoices" class="refresh-btn" :disabled="loading">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ spin: loading }">
            <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 4"/>
            <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 20"/>
            <path d="M21 4v5h-5"/>
            <path d="M3 20v-5h5"/>
          </svg>
          刷新
        </button>
        <button @click="$emit('create-new')" class="create-btn">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          新建音色
        </button>
      </div>
    </div>

    <div class="voices-content">
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
            <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
            <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
            <line x1="12" y1="19" x2="12" y2="23"/>
            <line x1="8" y1="23" x2="16" y2="23"/>
          </svg>
        </div>
        <h4>还没有音色</h4>
        <p>创建您的第一个音色，开始语音克隆之旅</p>
        <button @click="$emit('create-new')" class="create-first-btn">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          创建音色
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
              <button @click.stop="editVoice(voice)" class="edit-btn" title="编辑">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                  <path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
              </button>
              <button @click.stop="deleteVoice(voice)" class="delete-btn" title="删除">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M3 6h18"/>
                  <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
                  <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
                </svg>
              </button>
            </div>
          </div>
          
          <div class="voice-info">
            <h4 class="voice-name">{{ voice.name }}</h4>
            <p v-if="voice.description" class="voice-description">{{ voice.description }}</p>
            <div class="voice-meta">
              <span class="voice-status" :class="{ public: voice.isPublic }">
                {{ voice.isPublic ? '公开' : '私有' }}
              </span>
              <span class="voice-date">{{ formatDate(voice.createdAt) }}</span>
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
  name: 'MyVoices',
  emits: ['create-new', 'select-voice'],
  setup(props, { emit }) {
    const voices = ref([])
    const selectedVoice = ref(null)
    const loading = ref(false)
    
    // 获取用户音色列表
    const fetchVoices = async () => {
      loading.value = true
      try {
        const user = storage.get('user')
        if (!user) return
        
        const response = await ttsAPI.getUserVoices(user.userId)
        voices.value = response.data || []
      } catch (error) {
        console.error('获取音色列表失败:', error)
        voices.value = []
      } finally {
        loading.value = false
      }
    }
    
    // 刷新音色列表
    const refreshVoices = () => {
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
        const response = await ttsAPI.generateSpeech('这是一段测试音频', voice.id)
        
        // 创建音频播放
        const audioBlob = new Blob([response.data], { type: 'audio/wav' })
        const audioUrl = URL.createObjectURL(audioBlob)
        const audio = new Audio(audioUrl)
        
        audio.play().then(() => {
          // 播放完成后清理
          audio.onended = () => {
            URL.revokeObjectURL(audioUrl)
          }
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
    
    // 编辑音色
    const editVoice = (voice) => {
      // TODO: 实现编辑功能
      console.log('编辑音色:', voice)
      alert('编辑功能开发中')
    }
    
    // 删除音色
    const deleteVoice = async (voice) => {
      if (!confirm(`确定要删除音色"${voice.name}"吗？此操作无法撤销。`)) {
        return
      }
      
      try {
        const user = storage.get('user')
        if (!user) return
        
        await ttsAPI.deleteVoice(voice.id, user.userId)
        
        // 从列表中移除
        const index = voices.value.findIndex(v => v.id === voice.id)
        if (index > -1) {
          voices.value.splice(index, 1)
        }
        
        // 如果删除的是当前选中的音色，清空选择
        if (selectedVoice.value?.id === voice.id) {
          selectedVoice.value = null
        }
        
      } catch (error) {
        console.error('删除音色失败:', error)
        alert('删除失败: ' + error.message)
      }
    }
    
    // 格式化日期
    const formatDate = (dateString) => {
      const date = new Date(dateString)
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
      refreshVoices,
      selectVoice,
      testVoice,
      editVoice,
      deleteVoice,
      formatDate
    }
  }
}
</script>

<style lang="scss" scoped>
.my-voices {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--background-primary);
}

.voices-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-lg;
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.voices-title {
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.voices-actions {
  display: flex;
  gap: $spacing-sm;
}

.refresh-btn,
.create-btn {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing;
  border: none;
  border-radius: $border-radius;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-normal ease;
}

.refresh-btn {
  background: var(--background-tertiary);
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  
  &:hover:not(:disabled) {
    background: var(--background-primary);
    color: var(--text-primary);
  }
  
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.create-btn {
  background: var(--primary-color);
  color: white;
  
  &:hover {
    background: var(--primary-dark);
    transform: translateY(-1px);
  }
}

.voices-content {
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

.create-first-btn {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing $spacing-lg;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: $border-radius;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-normal ease;
  
  &:hover {
    background: var(--primary-dark);
    transform: translateY(-2px);
  }
}

// 音色网格
.voices-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
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
  background: var(--primary-color);
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
.edit-btn,
.delete-btn {
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

.edit-btn {
  background: var(--info-color);
  color: white;
  
  &:hover {
    background: darken(#2563eb, 10%);
    transform: scale(1.1);
  }
}

.delete-btn {
  background: var(--error-color);
  color: white;
  
  &:hover {
    background: darken(#dc2626, 10%);
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
    gap: $spacing-sm;
    font-size: 0.8rem;
    
    .voice-status {
      padding: 2px 8px;
      border-radius: 10px;
      font-weight: 500;
      background: var(--background-tertiary);
      color: var(--text-tertiary);
      
      &.public {
        background: rgba(5, 150, 105, 0.1);
        color: var(--success-color);
      }
    }
    
    .voice-date {
      color: var(--text-tertiary);
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
  .voices-header {
    flex-direction: column;
    gap: $spacing;
    align-items: stretch;
  }
  
  .voices-actions {
    justify-content: center;
  }
  
  .voices-grid {
    grid-template-columns: 1fr;
    gap: $spacing;
  }
  
  .voices-content {
    padding: $spacing;
  }
}
</style>
