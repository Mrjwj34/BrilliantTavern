<template>
  <div class="my-voices">
    <div class="voices-header">
      <div class="header-left">
        <h3 class="voices-title">我的音色</h3>
        <div class="voices-search">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="search-icon">
            <circle cx="11" cy="11" r="8"/>
            <path d="M21 21l-4.35-4.35"/>
          </svg>
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索我的音色..."
            class="search-input"
          />
          <button v-if="searchKeyword" type="button" class="clear-search" @click="searchKeyword = ''" aria-label="清除搜索">
            &times;
          </button>
        </div>
      </div>
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
      <div v-else-if="!loading && voices.length === 0" class="empty-state">
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
      <div v-else-if="!loading && voices.length > 0 && filteredVoices.length === 0" class="empty-state">
        <div class="empty-icon">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="11" cy="11" r="8"/>
            <path d="M21 21l-4.35-4.35"/>
          </svg>
        </div>
        <h4>未找到匹配的音色</h4>
        <p>尝试调整搜索关键词</p>
  <button @click="searchKeyword = ''" class="filter-reset-btn">清除搜索</button>
      </div>

      <!-- 音色列表 -->
      <div v-else-if="!loading && filteredVoices.length > 0" class="voices-grid">
        <VoiceCard 
          v-for="voice in filteredVoices" 
          :key="voice.id" 
          :voice="voice"
          mode="owner"
          :is-selected="selectedVoice?.id === voice.id"
          @click="selectVoice"
          @edit="openEditModal"
          @delete="deleteVoice"
        />
      </div>
    </div>

    <transition name="fade">
      <div v-if="isEditModalVisible" class="voice-edit-modal">
        <div class="modal-backdrop" @click="!editLoading && closeEditModal()"></div>
        <div class="modal-dialog" @click.stop>
          <div class="modal-header">
            <h4>编辑音色</h4>
            <button class="close-btn" type="button" @click="closeEditModal" :disabled="editLoading">
              <span>&times;</span>
            </button>
          </div>
          <form class="modal-body" @submit.prevent="submitEdit">
            <label class="form-group">
              <span class="form-label">音色名称</span>
              <input
                v-model.trim="editForm.name"
                type="text"
                class="form-input"
                maxlength="50"
                :disabled="editLoading"
                placeholder="请输入音色名称"
                required
              />
            </label>

            <label class="form-group">
              <span class="form-label">音色描述</span>
              <textarea
                v-model.trim="editForm.description"
                class="form-textarea"
                :disabled="editLoading"
                rows="3"
                maxlength="200"
                placeholder="描述音色的特点（可选）"
              ></textarea>
            </label>

            <label class="form-group form-switch">
              <input
                v-model="editForm.isPublic"
                type="checkbox"
                :disabled="editLoading"
              />
              <span>公开此音色</span>
            </label>

            <div class="modal-actions">
              <button
                type="button"
                class="btn secondary"
                @click="closeEditModal"
                :disabled="editLoading"
              >取消</button>
              <button
                type="submit"
                class="btn primary"
                :disabled="editLoading || !hasEditChanges"
              >
                <span v-if="editLoading" class="loading-indicator"></span>
                保存修改
              </button>
            </div>
          </form>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import { ref, reactive, onMounted, computed } from 'vue'
import { ttsAPI } from '@/api'
import { storage } from '@/utils'
import { notification } from '@/utils/notification'
import VoiceCard from './VoiceCard.vue'

export default {
  name: 'MyVoices',
  components: {
    VoiceCard
  },
  emits: ['create-new', 'select-voice'],
  setup(props, { emit }) {
  const voices = ref([])
  const searchKeyword = ref('')
    const selectedVoice = ref(null)
    const loading = ref(false)
    const isEditModalVisible = ref(false)
    const editLoading = ref(false)
    const editForm = reactive({
      id: null,
      name: '',
      description: '',
      isPublic: false
    })
    const editingVoiceOriginal = ref(null)

    const fetchVoices = async () => {
      loading.value = true
      try {
        const user = storage.get('user')
        if (!user) {
          voices.value = []
          return
        }

        const response = await ttsAPI.getUserVoices(user.userId)
        console.log('获取用户音色列表响应:', response)

        if (Array.isArray(response)) {
          voices.value = response
        } else if (response?.data && Array.isArray(response.data)) {
          voices.value = response.data
        } else if (response?.code === 200 && Array.isArray(response.data)) {
          voices.value = response.data
        } else {
          console.warn('未知的用户音色响应格式:', response)
          voices.value = []
        }
      } catch (error) {
        console.error('获取音色列表失败:', error)
        voices.value = []
      } finally {
        loading.value = false
      }
    }

    const refreshVoices = () => {
      fetchVoices()
    }

    const selectVoice = (voice) => {
      selectedVoice.value = voice
      emit('select-voice', voice)
    }

    const openEditModal = (voice) => {
      editingVoiceOriginal.value = { ...voice }
      editForm.id = voice.id
      editForm.name = voice.name || ''
      editForm.description = voice.description || ''
      editForm.isPublic = !!voice.isPublic
      isEditModalVisible.value = true
    }

    const closeEditModal = () => {
      isEditModalVisible.value = false
      editLoading.value = false
      editingVoiceOriginal.value = null
      editForm.id = null
      editForm.name = ''
      editForm.description = ''
      editForm.isPublic = false
    }

    const hasEditChanges = computed(() => {
      if (!editingVoiceOriginal.value) return false
      return (
        editForm.name.trim() !== (editingVoiceOriginal.value.name || '') ||
        editForm.description.trim() !== (editingVoiceOriginal.value.description || '') ||
        editForm.isPublic !== !!editingVoiceOriginal.value.isPublic
      )
    })

    const submitEdit = async () => {
      if (editLoading.value) return
      if (!editForm.name.trim()) {
        notification.error('音色名称不能为空')
        return
      }
      if (!hasEditChanges.value) {
        notification.info('未检测到任何修改')
        return
      }

      try {
        editLoading.value = true
        const user = storage.get('user')
        if (!user) {
          notification.error('请先登录')
          return
        }

        const payload = {
          name: editForm.name.trim(),
          description: editForm.description.trim(),
          isPublic: editForm.isPublic
        }

        const response = await ttsAPI.updateVoice(editForm.id, user.userId, payload)

        let updatedVoice = null
        let successMessage = '音色更新成功'

        if (response && typeof response === 'object') {
          if (response.code === 200) {
            updatedVoice = response.data
            if (response.message) successMessage = response.message
          } else if (response.id) {
            updatedVoice = response
          }
        }

        if (!updatedVoice) {
          notification.error(response?.message || '音色更新失败')
          return
        }

        const idx = voices.value.findIndex(v => v.id === updatedVoice.id)
        if (idx > -1) {
          voices.value[idx] = {
            ...voices.value[idx],
            ...updatedVoice
          }
        }

        if (selectedVoice.value?.id === updatedVoice.id) {
          selectedVoice.value = {
            ...selectedVoice.value,
            ...updatedVoice
          }
          emit('select-voice', selectedVoice.value)
        }

        notification.success(successMessage)
        closeEditModal()
      } catch (error) {
        console.error('更新音色失败:', error)
        notification.error(error.message || '音色更新失败')
      } finally {
        editLoading.value = false
      }
    }

    const deleteVoice = async (voice) => {
      if (!confirm(`确定要删除音色"${voice.name}"吗？此操作无法撤销。`)) {
        return
      }

      try {
        const user = storage.get('user')
        if (!user) {
          notification.error('请先登录')
          return
        }

        await ttsAPI.deleteVoice(voice.id, user.userId)

        const index = voices.value.findIndex(v => v.id === voice.id)
        if (index > -1) {
          voices.value.splice(index, 1)
        }

        if (selectedVoice.value?.id === voice.id) {
          selectedVoice.value = null
        }

        notification.success('音色已删除')
      } catch (error) {
        console.error('删除音色失败:', error)
        notification.error('删除失败: ' + (error.message || '未知错误'))
      }
    }

    const formatDate = (dateString) => {
      const date = new Date(dateString)
      return date.toLocaleDateString('zh-CN', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
      })
    }

    const filteredVoices = computed(() => {
      const keyword = searchKeyword.value.trim().toLowerCase()
      if (!keyword) {
        return voices.value
      }
      return voices.value.filter(voice => {
        const name = voice.name || ''
        const description = voice.description || ''
        return name.toLowerCase().includes(keyword) || description.toLowerCase().includes(keyword)
      })
    })

    onMounted(() => {
      fetchVoices()
    })

    return {
      voices,
      filteredVoices,
      selectedVoice,
      loading,
      isEditModalVisible,
      editForm,
      editLoading,
      hasEditChanges,
      searchKeyword,
      refreshVoices,
      selectVoice,
      openEditModal,
      closeEditModal,
      submitEdit,
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

.header-left {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.voices-title {
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.voices-search {
  display: flex;
  align-items: center;
  background: var(--background-secondary);
  border-radius: $border-radius;
  border: 1px solid transparent;
  padding: 0 $spacing-sm;
  height: 38px;
  transition: all $transition-fast ease;

  &:focus-within {
    border-color: var(--primary-color);
    box-shadow: 0 0 0 2px rgba(217, 119, 6, 0.15);
  }

  .search-icon {
    color: var(--text-tertiary);
    margin-right: $spacing-sm;
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

  .clear-search {
    background: transparent;
    border: none;
    color: var(--text-tertiary);
    font-size: 1rem;
    cursor: pointer;
    line-height: 1;

    &:hover {
      color: var(--text-secondary);
    }
  }
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
  border: 1px solid var(--border-color);
  border-radius: $border-radius;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-normal ease;
  background: transparent;
  color: var(--text-secondary);
  
  &:hover:not(:disabled) {
    background: var(--background-tertiary);
    color: var(--text-primary);
    border-color: var(--primary-color);
  }
  
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
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
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  border-radius: $border-radius;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-normal ease;
  
  &:hover {
    background: var(--background-tertiary);
    color: var(--text-primary);
    border-color: var(--primary-color);
    transform: translateY(-2px);
  }
}

.filter-reset-btn {
  padding: $spacing-sm $spacing-lg;
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  border-radius: $border-radius;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-normal ease;

  &:hover {
    background: var(--background-tertiary);
    color: var(--text-primary);
    border-color: var(--primary-color);
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

.voice-title {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
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
  gap: $spacing-xs;
}

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
  background: transparent;
  color: var(--text-secondary);
  
  &:hover {
    background: var(--background-tertiary);
    transform: scale(1.1);
  }
}

.edit-btn:hover {
  color: var(--info-color);
}

.delete-btn:hover {
  color: var(--error-color);
}

.voice-info {
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

.voice-edit-modal {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.modal-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
}

.modal-dialog {
  position: relative;
  width: min(480px, 90vw);
  background: var(--background-primary);
  border-radius: $border-radius-lg;
  box-shadow: var(--shadow-large);
  padding: $spacing-lg;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: $spacing;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-sm;

  h4 {
    margin: 0;
    font-size: 1.1rem;
    color: var(--text-primary);
  }
}

.close-btn {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  font-size: 1.5rem;
  cursor: pointer;
  line-height: 1;

  &:hover:not(:disabled) {
    color: var(--text-primary);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.modal-body {
  display: flex;
  flex-direction: column;
  gap: $spacing;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;

  .form-label {
    font-weight: 600;
    color: var(--text-secondary);
  }

  .form-input,
  .form-textarea {
    width: 100%;
    padding: $spacing-sm;
    border: 1px solid var(--border-light);
    border-radius: $border-radius;
    background: var(--background-secondary);
    color: var(--text-primary);
    transition: border-color 0.2s ease, box-shadow 0.2s ease;

    &:focus {
      outline: none;
      border-color: var(--primary-color);
      box-shadow: 0 0 0 2px rgba(217, 119, 6, 0.2);
    }

    &:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
  }
}

.form-switch {
  flex-direction: row;
  align-items: center;
  gap: $spacing-sm;
  font-weight: 500;
  color: var(--text-secondary);

  input[type='checkbox'] {
    width: 18px;
    height: 18px;
    cursor: pointer;
  }
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
  margin-top: $spacing;
}

.btn {
  min-width: 96px;
  padding: $spacing-sm $spacing;
  border-radius: $border-radius;
  border: 1px solid transparent;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &.primary {
    background: var(--primary-color);
    color: #fff;

    &:hover:not(:disabled) {
      opacity: 0.9;
    }
  }

  &.secondary {
    background: transparent;
    color: var(--text-secondary);
    border-color: var(--border-light);

    &:hover:not(:disabled) {
      border-color: var(--primary-color);
      color: var(--text-primary);
      background: var(--background-tertiary);
    }
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.loading-indicator {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.6);
  border-top-color: #fff;
  border-radius: 50%;
  margin-right: $spacing-xs;
  animation: spin 1s linear infinite;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

// 响应式
@media (max-width: 768px) {
  .voices-header {
    flex-direction: column;
    gap: $spacing;
    align-items: stretch;
  }

  .header-left {
    width: 100%;
  }

  .voices-search {
    width: 100%;
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
