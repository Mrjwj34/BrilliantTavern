<template>
  <div 
    class="voice-card"
    @click="handleCardClick"
    :class="{ selected: isSelected }"
  >
    <div class="voice-header">
      <div class="voice-title">
        <div class="voice-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
            <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
            <line x1="12" y1="19" x2="12" y2="23"/>
            <line x1="8" y1="23" x2="16" y2="23"/>
          </svg>
        </div>
        <h4 class="voice-name">{{ voice.name }}</h4>
      </div>
      <div class="voice-actions">
        <!-- 用户自己的音色显示编辑和删除按钮 -->
        <template v-if="mode === 'owner'">
          <button @click.stop="$emit('edit', voice)" class="edit-btn" title="编辑">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
          </button>
          <button @click.stop="$emit('delete', voice)" class="delete-btn" title="删除">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 6h18"/>
              <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
              <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
            </svg>
          </button>
        </template>
        
        <!-- 浏览模式显示播放和收藏按钮 -->
        <template v-else-if="mode === 'browse'">
          <button 
            @click.stop="handlePlay" 
            class="play-btn" 
            :class="{ playing: isPlaying, loading: isLoading }"
            :disabled="isLoading"
            :title="isLoading ? '加载中...' : isPlaying ? '暂停' : '播放'"
          >
            <svg v-if="!isLoading && !isPlaying" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="5 3,19 12,5 21,5 3"/>
            </svg>
            <svg v-else-if="!isLoading && isPlaying" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="6" y="4" width="4" height="16" rx="1"/>
              <rect x="14" y="4" width="4" height="16" rx="1"/>
            </svg>
            <div v-else class="loading-spinner">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
                <path d="M21 12a9 9 0 11-6.219-8.56"/>
              </svg>
            </div>
          </button>
          <button @click.stop="$emit('favorite', voice)" class="favorite-btn" :class="{ active: voice.isFavorited }" title="收藏">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
            </svg>
          </button>
        </template>
      </div>
    </div>
    
    <div class="voice-info">
      <p v-if="voice.description" class="voice-description">{{ voice.description }}</p>
      <div class="voice-meta">
        <!-- 用户模式显示公开状态和时间 -->
        <template v-if="mode === 'owner'">
          <span class="voice-status" :class="{ public: voice.isPublic }">
            {{ voice.isPublic ? '公开' : '私有' }}
          </span>
          <span class="voice-date">{{ formatDate(voice.createdAt) }}</span>
        </template>
        
        <!-- 浏览模式显示创建者和时间 -->
        <template v-else-if="mode === 'browse'">
          <div class="voice-creator">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
            <span>{{ voice.creatorName || '匿名' }}</span>
          </div>
          <span class="voice-date">{{ formatDate(voice.createdAt) }}</span>
        </template>
      </div>
      
      <!-- 浏览模式下显示统计信息 -->
      <div v-if="mode === 'browse'" class="voice-stats">
        <div class="stat-item">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
          </svg>
          <span>{{ voice.likesCount ?? voice.favoriteCount ?? 0 }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { onBeforeUnmount, ref } from 'vue'
import { ttsAPI } from '@/api'

const TEST_TEXT = '这是一个语音测试，用来演示音色效果。'

export default {
  name: 'VoiceCard',
  props: {
    voice: {
      type: Object,
      required: true
    },
    mode: {
      type: String,
      default: 'owner', // 'owner' | 'browse'
      validator: (value) => ['owner', 'browse'].includes(value)
    },
    isSelected: {
      type: Boolean,
      default: false
    }
  },
  emits: ['click', 'edit', 'delete', 'test', 'use', 'favorite'],
  setup(props, { emit }) {
    const isPlaying = ref(false)
    const isLoading = ref(false)
    const audioElement = ref(null)
    const audioUrl = ref(null)

    const handleCardClick = () => {
      emit('click', props.voice)
    }

    // 播放音色示例
    const cleanupAudio = () => {
      if (audioElement.value) {
        audioElement.value.pause()
        audioElement.value.src = ''
        audioElement.value = null
      }
      if (audioUrl.value) {
        URL.revokeObjectURL(audioUrl.value)
        audioUrl.value = null
      }
      isPlaying.value = false
    }

    const handlePlay = async () => {
      if (isLoading.value) return

      const referenceId = props.voice.referenceId || props.voice.reference_id
      if (!referenceId) {
        console.warn('当前音色缺少 referenceId，无法播放测试音频', props.voice)
        if (window.$notification) {
          window.$notification.error('当前音色缺少 referenceId，无法播放')
        } else {
          alert('当前音色缺少 referenceId，无法播放')
        }
        return
      }

      if (audioElement.value) {
        if (!audioElement.value.paused) {
          audioElement.value.pause()
          return
        }

        if (audioElement.value.currentTime > 0 && audioElement.value.currentTime < audioElement.value.duration) {
          try {
            await audioElement.value.play()
            return
          } catch (error) {
            console.error('恢复播放失败:', error)
          }
        }

        cleanupAudio()
      }

      try {
        isLoading.value = true
        const response = await ttsAPI.generateSpeech(TEST_TEXT, referenceId)
        
        // 检查响应是否有效
        if (!response) {
          throw new Error('音频生成响应为空')
        }
        
        const audioBlob = response instanceof Blob ? response : new Blob([response], { type: 'audio/mpeg' })
        
        // 检查 Blob 是否有效
        if (!audioBlob || audioBlob.size === 0) {
          throw new Error('音频数据为空')
        }
        
        const objectUrl = URL.createObjectURL(audioBlob)
        audioUrl.value = objectUrl

        const audio = new Audio()
        audio.preload = 'auto'
        audioElement.value = audio

        audio.onplay = () => {
          isPlaying.value = true
        }

        audio.onpause = () => {
          if (audio.currentTime < audio.duration) {
            isPlaying.value = false
          }
        }

        audio.onended = () => {
          isPlaying.value = false
          audio.currentTime = 0
        }

        audio.onerror = (event) => {
          const mediaError = audio.error
          console.error('音频播放失败 - 错误代码:', mediaError?.code, '错误信息:', mediaError?.message, '事件:', event)
          
          // 忽略某些不重要的错误
          if (mediaError && (mediaError.code === 1 || mediaError.code === 4)) {
            // MEDIA_ERR_ABORTED (1) 或 MEDIA_ERR_SRC_NOT_SUPPORTED (4)
            console.warn('忽略音频错误:', mediaError.code)
            return
          }
          
          cleanupAudio()
          if (window.$notification) {
            window.$notification.error('播放失败，请稍后重试')
          } else {
            alert('播放失败，请稍后重试')
          }
        }

        // 设置音频源
        audio.src = objectUrl
        
        // 等待音频加载完成再播放
        const loadPromise = new Promise((resolve, reject) => {
          const onLoad = () => {
            audio.removeEventListener('canplaythrough', onLoad)
            audio.removeEventListener('error', onError)
            resolve()
          }
          const onError = (error) => {
            audio.removeEventListener('canplaythrough', onLoad)
            audio.removeEventListener('error', onError)
            reject(error)
          }
          
          audio.addEventListener('canplaythrough', onLoad)
          audio.addEventListener('error', onError)
        })

        await loadPromise

        const playPromise = audio.play()
        if (playPromise && typeof playPromise.catch === 'function') {
          playPromise.catch(error => {
            console.error('播放Promise失败:', error.name, error.message)
            
            // 忽略用户交互相关错误
            if (error.name === 'AbortError' || error.name === 'NotAllowedError') {
              console.warn('音频播放被中断或需要用户交互:', error.name)
              return
            }
            
            // 忽略网络错误，可能是暂时性的
            if (error.name === 'NotSupportedError') {
              console.warn('音频格式不支持:', error.name)
              return
            }
            
            cleanupAudio()
            if (window.$notification) {
              window.$notification.error('播放失败，请稍后重试')
            } else {
              alert('播放失败，请稍后重试')
            }
          })
        }
      } catch (error) {
        console.error('语音生成失败:', error)
        cleanupAudio()
        if (window.$notification) {
          window.$notification.error('播放失败，请稍后重试')
        } else {
          alert('播放失败，请稍后重试')
        }
      } finally {
        isLoading.value = false
      }
    }

    onBeforeUnmount(() => {
      cleanupAudio()
    })

    const formatDate = (dateString) => {
      if (!dateString) return ''
      const date = new Date(dateString)
      const now = new Date()
      const diff = now - date
      
      const minutes = Math.floor(diff / (1000 * 60))
      const hours = Math.floor(diff / (1000 * 60 * 60))
      const days = Math.floor(diff / (1000 * 60 * 60 * 24))
      
      if (minutes < 60) {
        return `${minutes}分钟前`
      } else if (hours < 24) {
        return `${hours}小时前`
      } else if (days < 30) {
        return `${days}天前`
      } else {
        return date.toLocaleDateString('zh-CN')
      }
    }

    return {
      isPlaying,
      isLoading,
      handleCardClick,
      handlePlay,
      formatDate
    }
  }
}
</script>

<style lang="scss" scoped>
.voice-card {
  background: var(--background-secondary);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s ease;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
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
  margin-bottom: 16px;
}

.voice-title {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.voice-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #f97316; // 橙色
}

.voice-name {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.voice-actions {
  display: flex;
  gap: 8px;
}

.edit-btn,
.delete-btn,
.play-btn,
.favorite-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  background: transparent;
  color: var(--text-secondary);
  
  &:hover {
    background: var(--background-tertiary);
    transform: scale(1.1);
  }
  
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
    transform: none;
  }
}

.edit-btn:hover {
  color: var(--info-color);
}

.delete-btn:hover {
  color: var(--error-color);
}

.play-btn:hover {
  color: var(--success-color);
}

.play-btn.playing {
  color: var(--success-color);
}

.play-btn.loading {
  color: var(--success-color);

  .spin {
    animation: spin 1s linear infinite;
  }
}

.play-btn .loading-spinner {
  display: flex;
  align-items: center;
  justify-content: center;
}

.play-btn .loading-spinner svg {
  width: 16px;
  height: 16px;
}

.favorite-btn:hover,
.favorite-btn.active {
  color: #e11d48; // 红色
}

.voice-info {
  .voice-description {
    color: var(--text-secondary);
    font-size: 0.9rem;
    line-height: 1.4;
    margin: 0 0 12px 0;
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
    gap: 12px;
    font-size: 0.8rem;
    margin-bottom: 8px;
    
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
    
    .voice-creator {
      display: flex;
      align-items: center;
      gap: 4px;
      color: var(--text-secondary);
    }
    
    .voice-date {
      color: var(--text-tertiary);
    }
  }

  .voice-stats {
    display: flex;
    gap: 16px;
    font-size: 0.75rem;
    color: var(--text-tertiary);

    .stat-item {
      display: flex;
      align-items: center;
      gap: 4px;
    }
  }
}

// 旋转动画
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
</style>
