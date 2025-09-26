<template>
  <div class="browse-voices">
    <div class="browse-header">
      <div class="header-left">
        <h3 class="browse-title">浏览音色</h3>
        <p class="browse-subtitle">发现社区分享的优质音色</p>
      </div>
      <div class="header-actions">
        <div class="search-controls">
          <div class="search-field">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="search-icon">
              <circle cx="11" cy="11" r="8"/>
              <path d="M21 21l-4.35-4.35"/>
            </svg>
            <input 
              v-model="searchKeyword" 
              type="text" 
              placeholder="搜索音色..."
              class="search-input"
              @input="handleSearchInput"
              @keyup.enter="executeSearch"
            />
            <button
              v-if="searchKeyword"
              type="button"
              class="clear-input"
              @click="clearSearch"
              aria-label="清除搜索"
            >
              &times;
            </button>
          </div>
          <div class="sort-filters">
            <button
              type="button"
              :class="{ active: sortOption === 'newest' }"
              @click="changeSort('newest')"
            >最新</button>
            <button
              type="button"
              :class="{ active: sortOption === 'likes' }"
              @click="changeSort('likes')"
            >最受欢迎</button>
          </div>
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
      <div v-if="isInitialLoading" class="loading-state">
        <div class="loading-spinner">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
            <path d="M21 12a9 9 0 11-6.219-8.56"/>
          </svg>
        </div>
        <p>加载中...</p>
      </div>

      <!-- 空状态 -->
      <div v-else-if="isEmptyState" class="empty-state">
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
      <div v-else>
        <div class="voices-grid">
          <VoiceCard
            v-for="voice in voices"
            :key="voice.id"
            :voice="voice"
            mode="browse"
            @favorite="favoriteVoice"
          />
        </div>

        <div v-if="hasMore" ref="loadTrigger" class="load-trigger"></div>

        <!-- 加载更多指示器 -->
        <div v-if="loading && voices.length > 0" class="load-more-indicator">
          <div class="loading-spinner">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
              <path d="M21 12a9 9 0 11-6.219-8.56"/>
            </svg>
          </div>
          <span>加载更多...</span>
        </div>

        <!-- 底部提示 -->
        <div v-if="!loading && !hasMore && voices.length > 0" class="bottom-tip">
          <div class="bottom-tip-line"></div>
          <span class="bottom-tip-text">已经到底了</span>
          <div class="bottom-tip-line"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { ttsAPI } from '@/api'
import { storage } from '@/utils'
import { notification } from '@/utils/notification'
import VoiceCard from './VoiceCard.vue'

const PAGE_SIZE = 20

export default {
  name: 'BrowseVoices',
  components: {
    VoiceCard
  },
  emits: ['select-voice'],
  setup(props, { emit }) {
    const voices = ref([])
    const selectedVoice = ref(null)
    const loading = ref(false)
    const hasMore = ref(true)
    const nextCursor = ref(null)
    const searchKeyword = ref('')
    const sortOption = ref('newest')
    const searchDebounce = ref(null)
    const likeRequesting = ref(false)
    const loadTrigger = ref(null)

    let observer = null

    const isInitialLoading = computed(() => loading.value && voices.value.length === 0)
    const isEmptyState = computed(() => !loading.value && voices.value.length === 0)

    const getUserId = () => storage.get('user')?.userId || ''

    const getFilterBySort = () => (sortOption.value === 'likes' ? 'popular' : 'latest')

    const normalizeVoice = (voice = {}) => ({
      ...voice,
      likesCount: voice?.likesCount ?? voice?.favoriteCount ?? 0,
      isFavorited: Boolean(voice?.isFavorited ?? voice?.liked),
      creatorName: voice?.creatorName || voice?.creatorUsername || voice?.creator?.username || '匿名用户'
    })

    const processVoiceList = (list = []) => list.map(normalizeVoice)

    const cleanupObserver = () => {
      if (observer) {
        observer.disconnect()
        observer = null
      }
    }

    const loadVoices = async (reset = false) => {
      if (loading.value) return
      if (reset) {
        nextCursor.value = null
        hasMore.value = true
        voices.value = []
      } else if (!hasMore.value) {
        return
      }

      loading.value = true

      try {
        const params = {
          filter: getFilterBySort(),
          size: PAGE_SIZE
        }

        const keyword = searchKeyword.value.trim()
        if (keyword) {
          params.keyword = keyword
        }
        if (!reset && nextCursor.value) {
          params.cursor = nextCursor.value
        }

        const userId = getUserId()
        if (userId) {
          params.userId = userId
        }

        const response = await ttsAPI.getMarketVoices(params)
        const { items = [], nextCursor: cursorValue = null, hasNext } = response || {}
        const normalized = processVoiceList(Array.isArray(items) ? items : [])

        if (reset) {
          voices.value = normalized
        } else {
          const merged = [...voices.value]
          normalized.forEach(item => {
            const index = merged.findIndex(existing => existing.id === item.id)
            if (index === -1) {
              merged.push(item)
            } else {
              merged[index] = { ...merged[index], ...item }
            }
          })
          voices.value = merged
        }

        nextCursor.value = cursorValue || null
        const shouldHaveNext = typeof hasNext === 'boolean' ? hasNext : normalized.length === PAGE_SIZE
        hasMore.value = shouldHaveNext
      } catch (error) {
        console.error('获取音色市场失败:', error)
        notification.error(error?.response?.data?.message || error?.message || '获取音色失败，请稍后重试')
        if (reset) {
          voices.value = []
        }
        hasMore.value = false
      } finally {
        loading.value = false
        nextTick(() => {
          if (hasMore.value) {
            initObserver()
          } else {
            cleanupObserver()
          }
        })
      }
    }

    const initObserver = () => {
      cleanupObserver()
      if (!hasMore.value || !loadTrigger.value) {
        return
      }

      observer = new IntersectionObserver(entries => {
        const entry = entries[0]
        if (entry?.isIntersecting && hasMore.value && !loading.value) {
          loadVoices(false)
        }
      }, {
        rootMargin: '0px 0px 200px 0px',
        threshold: 0.1
      })

      observer.observe(loadTrigger.value)
    }

    const refreshVoices = () => {
      loadVoices(true)
    }

    const executeSearch = () => {
      if (searchDebounce.value) {
        clearTimeout(searchDebounce.value)
        searchDebounce.value = null
      }
      loadVoices(true)
    }

    const handleSearchInput = () => {
      if (searchDebounce.value) {
        clearTimeout(searchDebounce.value)
      }
      searchDebounce.value = setTimeout(() => {
        loadVoices(true)
        searchDebounce.value = null
      }, 300)
    }

    const clearSearch = () => {
      if (!searchKeyword.value) return
      searchKeyword.value = ''
      loadVoices(true)
    }

    const changeSort = (value) => {
      if (sortOption.value === value) return
      sortOption.value = value
      loadVoices(true)
    }

    const selectVoice = (voice) => {
      selectedVoice.value = voice
      emit('select-voice', voice)
    }

    const favoriteVoice = async (voice) => {
      const userId = getUserId()
      if (!userId) {
        notification.warning('请先登录后再收藏音色')
        return
      }

      if (likeRequesting.value) return
      likeRequesting.value = true

      try {
        const response = voice.isFavorited
          ? await ttsAPI.unlikeVoice(voice.id, userId)
          : await ttsAPI.likeVoice(voice.id, userId)

        const updatedVoice = response && response.id ? normalizeVoice(response) : null

        if (updatedVoice && updatedVoice.id) {
          const index = voices.value.findIndex(item => item.id === updatedVoice.id)
          if (index !== -1) {
            voices.value[index] = {
              ...voices.value[index],
              ...updatedVoice
            }
          }
          if (selectedVoice.value?.id === updatedVoice.id) {
            selectedVoice.value = {
              ...selectedVoice.value,
              ...updatedVoice
            }
          }
        } else {
          voice.isFavorited = !voice.isFavorited
          const delta = voice.isFavorited ? 1 : -1
          voice.likesCount = Math.max(0, (voice.likesCount || 0) + delta)
        }
      } catch (error) {
        console.error('更新音色点赞状态失败:', error)
        notification.error(error?.response?.data?.message || '操作失败，请稍后重试')
      } finally {
        likeRequesting.value = false
      }
    }

    onMounted(() => {
      loadVoices(true)
    })

    watch(loadTrigger, (el) => {
      if (el && hasMore.value) {
        initObserver()
      } else if (!el) {
        cleanupObserver()
      }
    })

    watch(hasMore, (value) => {
      if (!value) {
        cleanupObserver()
      } else if (loadTrigger.value) {
        initObserver()
      }
    })

    onUnmounted(() => {
      if (searchDebounce.value) {
        clearTimeout(searchDebounce.value)
      }
      cleanupObserver()
    })

    return {
      voices,
      selectedVoice,
      loading,
      hasMore,
      isInitialLoading,
      isEmptyState,
      searchKeyword,
      sortOption,
      loadTrigger,
      refreshVoices,
      handleSearchInput,
      executeSearch,
      clearSearch,
      changeSort,
      selectVoice,
      favoriteVoice
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

.search-controls {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
  min-width: 280px;
}

.search-field {
  display: flex;
  align-items: center;
  background: var(--background-secondary);
  border-radius: $border-radius;
  padding: 0 $spacing-sm;
  height: 40px;
  border: 1px solid transparent;
  transition: all $transition-fast ease;

  &:focus-within {
    border-color: var(--primary-color);
    box-shadow: 0 0 0 3px rgba(217, 119, 6, 0.1);
  }

  .search-icon {
    color: var(--text-tertiary);
    flex-shrink: 0;
    margin-right: $spacing-sm;
  }

  .search-input {
    flex: 1;
    border: none;
    background: transparent;
    color: var(--text-primary);
    outline: none;
    font-size: 0.95rem;

    &::placeholder {
      color: var(--text-placeholder);
    }
  }

  .clear-input {
    background: transparent;
    border: none;
    color: var(--text-tertiary);
    font-size: 1.1rem;
    cursor: pointer;
    padding: 0;
    line-height: 1;

    &:hover {
      color: var(--text-secondary);
    }
  }
}

.sort-filters {
  display: flex;
  gap: $spacing-xs;

  button {
    padding: 6px 12px;
    border-radius: $border-radius-sm;
    border: 1px solid transparent;
    background: transparent;
    color: var(--text-tertiary);
    font-size: 0.85rem;
    cursor: pointer;
    transition: all $transition-fast ease;

    &:hover {
      color: var(--text-primary);
      background: var(--background-tertiary);
    }

    &.active {
      color: var(--primary-color);
      background: rgba(217, 119, 6, 0.12);
      border-color: rgba(217, 119, 6, 0.3);
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

.load-trigger {
  width: 100%;
  height: 1px;
}

.load-more-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing;
  padding: 2rem 0;
  color: var(--text-secondary);
  font-size: 0.9rem;

  .loading-spinner {
    width: 1.5rem;
    height: 1.5rem;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--primary-color);
    margin: 0;
  }
}

.bottom-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing;
  padding: 2rem 0;
  margin-top: $spacing;
}

.bottom-tip-line {
  flex: 1;
  height: 1px;
  max-width: 100px;
  background: linear-gradient(to right, transparent, var(--border-light), transparent);
}

.bottom-tip-text {
  color: var(--text-tertiary);
  font-size: 0.8rem;
  white-space: nowrap;
  padding: 0 $spacing-sm;
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
    align-items: stretch;
    gap: $spacing-sm;
  }
  
  .search-controls {
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
