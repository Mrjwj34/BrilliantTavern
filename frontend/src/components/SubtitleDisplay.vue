<template>
  <div class="subtitle-display" :class="{ visible: isVisible }">
    <!-- 字幕容器 -->
    <div class="subtitle-container" v-if="isVisible">
      <div class="subtitle-content">
        <!-- 实时字幕文本 -->
        <div class="subtitle-text" v-html="formattedSubtitle"></div>
        
        <!-- 动作说明 -->
        <div v-if="currentAction" class="subtitle-action">
          {{ currentAction }}
        </div>
      </div>
      
      <!-- 字幕控制栏 -->
      <div class="subtitle-controls">
        <button @click="toggleVisibility" class="toggle-btn">
          {{ isVisible ? '隐藏字幕' : '显示字幕' }}
        </button>
        <span class="subtitle-info">{{ language.toUpperCase() }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

// Props
const props = defineProps({
  subtitleData: {
    type: Object,
    default: () => ({})
  },
  language: {
    type: String,
    default: 'zh'
  },
  autoHide: {
    type: Boolean,
    default: false
  },
  autoHideDelay: {
    type: Number,
    default: 3000
  }
})

// Emits
const emit = defineEmits(['visibility-changed'])

// Reactive state
const isVisible = ref(true)
const currentSubtitle = ref('')
const currentAction = ref('')
const segments = ref([])
const autoHideTimer = ref(null)

// Computed
const formattedSubtitle = computed(() => {
  if (!currentSubtitle.value) return ''
  
  // 处理动作标记 <action>动作</action>
  return currentSubtitle.value.replace(
    /<action>([^<]+)<\/action>/g, 
    '<span class="action-marker">$1</span>'
  )
})

// Methods
const updateSubtitle = (data) => {
  if (!data) return
  
  switch (data.action) {
    case 'start':
      // 字幕开始
      segments.value = []
      currentSubtitle.value = ''
      currentAction.value = ''
      break
      
    case 'segment':
      // 字幕片段
      if (data.isFinal) {
        // 最终片段，替换当前字幕
        currentSubtitle.value = data.processedText || data.text || ''
      } else {
        // 流式片段，追加到当前字幕
        currentSubtitle.value += data.text || ''
      }
      
      // 提取动作说明
      extractActions(data.processedText || data.text || '')
      break
      
    case 'end':
      // 字幕结束
      currentSubtitle.value = data.processedText || data.fullText || ''
      extractActions(currentSubtitle.value)
      
      // 自动隐藏
      if (props.autoHide) {
        startAutoHide()
      }
      break
  }
  
  // 显示字幕
  if (!isVisible.value && currentSubtitle.value) {
    isVisible.value = true
    emit('visibility-changed', true)
  }
}

const extractActions = (text) => {
  if (!text) return
  
  // 提取最后一个动作标记
  const actionMatch = text.match(/<action>([^<]+)<\/action>/g)
  if (actionMatch && actionMatch.length > 0) {
    const lastAction = actionMatch[actionMatch.length - 1]
    currentAction.value = lastAction.replace(/<\/?action>/g, '')
  }
}

const toggleVisibility = () => {
  isVisible.value = !isVisible.value
  emit('visibility-changed', isVisible.value)
  
  if (autoHideTimer.value) {
    clearTimeout(autoHideTimer.value)
    autoHideTimer.value = null
  }
}

const startAutoHide = () => {
  if (autoHideTimer.value) {
    clearTimeout(autoHideTimer.value)
  }
  
  autoHideTimer.value = setTimeout(() => {
    isVisible.value = false
    emit('visibility-changed', false)
    autoHideTimer.value = null
  }, props.autoHideDelay)
}

const clearSubtitle = () => {
  currentSubtitle.value = ''
  currentAction.value = ''
  segments.value = []
  
  if (autoHideTimer.value) {
    clearTimeout(autoHideTimer.value)
    autoHideTimer.value = null
  }
}

// Watch for subtitle data changes
watch(() => props.subtitleData, (newData) => {
  if (newData) {
    updateSubtitle(newData)
  }
}, { deep: true, immediate: true })

// Expose methods
defineExpose({
  updateSubtitle,
  clearSubtitle,
  toggleVisibility
})
</script>

<style scoped>
.subtitle-display {
  position: fixed;
  bottom: 120px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  max-width: 80%;
  min-width: 300px;
  transition: all 0.3s ease;
  opacity: 0;
  pointer-events: none;
}

.subtitle-display.visible {
  opacity: 1;
  pointer-events: auto;
}

.subtitle-container {
  background: rgba(0, 0, 0, 0.85);
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.subtitle-content {
  margin-bottom: 12px;
}

.subtitle-text {
  color: #ffffff;
  font-size: 16px;
  font-weight: 500;
  line-height: 1.5;
  text-align: center;
  word-wrap: break-word;
  white-space: pre-wrap;
}

.subtitle-text :deep(.action-marker) {
  color: #ffd700;
  font-style: italic;
  font-weight: 600;
  background: rgba(255, 215, 0, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  margin: 0 2px;
}

.subtitle-action {
  color: #87ceeb;
  font-size: 14px;
  font-style: italic;
  text-align: center;
  margin-top: 8px;
  opacity: 0.9;
}

.subtitle-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  padding-top: 8px;
}

.toggle-btn {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #ffffff;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.toggle-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.3);
}

.subtitle-info {
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
  font-weight: 500;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .subtitle-display {
    bottom: 100px;
    max-width: 90%;
    min-width: 280px;
  }
  
  .subtitle-container {
    padding: 12px 16px;
  }
  
  .subtitle-text {
    font-size: 14px;
  }
}

/* 动画效果 */
@keyframes subtitleFadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.subtitle-display.visible .subtitle-container {
  animation: subtitleFadeIn 0.3s ease;
}

/* 高对比度模式 */
@media (prefers-contrast: high) {
  .subtitle-container {
    background: rgba(0, 0, 0, 0.95);
    border: 2px solid #ffffff;
  }
  
  .subtitle-text {
    text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.8);
  }
}

/* 减少动画模式 */
@media (prefers-reduced-motion: reduce) {
  .subtitle-display,
  .subtitle-container {
    transition: none;
  }
  
  .subtitle-display.visible .subtitle-container {
    animation: none;
  }
}
</style>