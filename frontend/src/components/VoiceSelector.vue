<template>
  <div class="voice-select-container">
    <div class="custom-select" :class="{ open: isOpen }">
      <div class="select-trigger" @click="toggleSelect">
        <span class="selected-text">
          {{ selectedText || placeholder }}
        </span>
        <svg class="select-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="6,9 12,15 18,9"></polyline>
        </svg>
      </div>
      <div class="select-dropdown" v-show="isOpen">
        <div class="search-box">
          <svg class="search-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8" />
            <path d="M21 21l-4.35-4.35" />
          </svg>
          <input
            ref="searchInput"
            v-model="searchKeyword"
            type="text"
            placeholder="搜索音色..."
            @keydown.stop
            @click.stop
          />
          <button
            v-if="searchKeyword"
            type="button"
            class="clear-search"
            @click.stop="searchKeyword = ''"
            aria-label="清除搜索"
          >&times;</button>
        </div>
        <div class="options-list">
          <div 
            class="option-item" 
            :class="{ selected: !modelValue }"
            @click="selectVoice('')"
          >
            {{ placeholder }}
          </div>
          <div 
            v-for="voice in filteredVoices" 
            :key="voice.id" 
            class="option-item"
            :class="{ selected: modelValue === voice.id }"
            @click="selectVoice(voice.id)"
          >
            {{ voice.name }}
          </div>
          <div v-if="filteredVoices.length === 0 && searchKeyword" class="no-results">
            未找到匹配的音色
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'

export default {
  name: 'VoiceSelector',
  props: {
    modelValue: {
      type: String,
      default: ''
    },
    voices: {
      type: Array,
      default: () => []
    },
    placeholder: {
      type: String,
      default: '选择语音音色（可选）'
    },
    disabled: {
      type: Boolean,
      default: false
    }
  },
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    const isOpen = ref(false)
    const searchKeyword = ref('')
    const searchInput = ref(null)

    // 计算选中的音色文本
    const selectedText = computed(() => {
      if (!props.modelValue) return ''
      const selectedVoice = props.voices.find(v => v.id === props.modelValue)
      return selectedVoice ? selectedVoice.name : ''
    })

    // 过滤音色列表
    const filteredVoices = computed(() => {
      const keyword = searchKeyword.value.trim().toLowerCase()
      if (!keyword) {
        return props.voices
      }
      return props.voices.filter(voice => {
        const name = (voice.name || '').toString().toLowerCase()
        const id = (voice.id || '').toString().toLowerCase()
        return name.includes(keyword) || id.includes(keyword)
      })
    })

    // 切换下拉框
    const toggleSelect = () => {
      if (props.disabled) return
      
      isOpen.value = !isOpen.value
      if (isOpen.value) {
        nextTick(() => {
          if (searchInput.value) {
            searchInput.value.focus()
          }
        })
      }
    }

    // 选择音色
    const selectVoice = (voiceId) => {
      emit('update:modelValue', voiceId)
      emit('change', voiceId)
      isOpen.value = false
      searchKeyword.value = ''
    }

    // 点击外部关闭下拉框
    const handleClickOutside = (event) => {
      const selectContainer = event.target.closest('.voice-select-container')
      if (!selectContainer) {
        isOpen.value = false
      }
    }

    onMounted(() => {
      document.addEventListener('click', handleClickOutside)
    })

    onUnmounted(() => {
      document.removeEventListener('click', handleClickOutside)
    })

    return {
      isOpen,
      searchKeyword,
      searchInput,
      selectedText,
      filteredVoices,
      toggleSelect,
      selectVoice
    }
  }
}
</script>

<style lang="scss" scoped>
.voice-select-container {
  position: relative;
}

.custom-select {
  position: relative;
  
  .select-trigger {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 1rem;
    background: var(--background-secondary);
    color: var(--text-primary);
    border: none;
    border-radius: 8px;
    font-size: 0.875rem;
    font-family: inherit;
    cursor: pointer;
    transition: all 0.2s ease;
    user-select: none;

    &:hover {
      background: var(--background-tertiary);
    }

    .selected-text {
      flex: 1;
      text-align: left;
      color: var(--text-primary);
      
      &:empty::before {
        content: attr(data-placeholder);
        color: var(--text-tertiary);
      }
    }

    .select-arrow {
      width: 1rem;
      height: 1rem;
      margin-left: 0.5rem;
      transition: transform 0.2s ease;
      color: var(--text-secondary);
    }
  }

  &.open {
    .select-trigger {
      background: var(--background-tertiary);
      box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
      border-radius: 8px 8px 0 0;
      
      .select-arrow {
        transform: rotate(180deg);
      }
    }
  }

  .select-dropdown {
    position: absolute;
    top: 100%;
    left: 0;
    right: 0;
    background: var(--background-secondary);
    border-radius: 0 0 8px 8px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    z-index: 2002;
    max-height: 300px;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }

  .search-box {
    position: relative;
    padding: 0.75rem;
    border-bottom: 1px solid var(--border-light);
    background: var(--background-tertiary);
    
    .search-icon {
      position: absolute;
      left: 1.25rem;
      top: 50%;
      transform: translateY(-50%);
      color: var(--text-tertiary);
      pointer-events: none;
    }

    input {
      width: 100%;
      padding: 0.5rem 0.5rem 0.5rem 2rem;
      background: var(--background-primary);
      border: none;
      border-radius: 6px;
      font-size: 0.875rem;
      color: var(--text-primary);
      
      &:focus {
        outline: none;
        box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.2);
      }

      &::placeholder {
        color: var(--text-tertiary);
      }
    }

    .clear-search {
      position: absolute;
      right: 1.25rem;
      top: 50%;
      transform: translateY(-50%);
      background: none;
      border: none;
      font-size: 1.25rem;
      color: var(--text-tertiary);
      cursor: pointer;
      padding: 0;
      width: 1.5rem;
      height: 1.5rem;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      transition: all 0.2s ease;

      &:hover {
        background: var(--background-secondary);
        color: var(--text-secondary);
      }
    }
  }

  .options-list {
    flex: 1;
    overflow-y: auto;
    max-height: 240px;
  }

  .option-item {
    padding: 0.75rem 1rem;
    cursor: pointer;
    font-size: 0.875rem;
    color: var(--text-primary);
    transition: all 0.2s ease;
    border-bottom: 1px solid transparent;

    &:hover {
      background: var(--background-tertiary);
    }

    &.selected {
      background: rgba(99, 102, 241, 0.1);
      color: var(--primary-color);
      font-weight: 500;
    }

    &:last-child {
      border-bottom: none;
    }
  }

  .no-results {
    padding: 1rem;
    text-align: center;
    color: var(--text-tertiary);
    font-size: 0.875rem;
    font-style: italic;
  }
}
</style>