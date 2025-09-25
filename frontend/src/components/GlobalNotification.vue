<template>
  <teleport to="body">
    <div class="notification-container">
      <transition-group 
        name="notification" 
        tag="div" 
        class="notification-list"
        appear
      >
        <div 
          v-for="item in notifications"
          :key="item.id"
          :class="[
            'notification-item', 
            `notification-${item.type}`,
            { 'show': item.show }
          ]"
        >
          <!-- 图标 -->
          <div class="notification-icon">
            <svg 
              v-if="item.type === 'success'" 
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="2"
            >
              <path d="M20 6L9 17l-5-5"/>
            </svg>
            <svg 
              v-else-if="item.type === 'error'" 
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="2"
            >
              <circle cx="12" cy="12" r="10"/>
              <line x1="15" y1="9" x2="9" y2="15"/>
              <line x1="9" y1="9" x2="15" y2="15"/>
            </svg>
            <svg 
              v-else-if="item.type === 'warning'" 
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="2"
            >
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
              <line x1="12" y1="9" x2="12" y2="13"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
            <svg 
              v-else
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="2"
            >
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="16" x2="12" y2="12"/>
              <line x1="12" y1="8" x2="12.01" y2="8"/>
            </svg>
          </div>
          
          <!-- 消息内容 -->
          <div class="notification-content">
            <span class="notification-message">{{ item.message }}</span>
          </div>
          
          <!-- 关闭按钮 -->
          <button 
            class="notification-close"
            @click="() => notification.hide(item.id)"
            type="button"
            aria-label="关闭通知"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
      </transition-group>
    </div>
  </teleport>
</template>

<script>
import { notifications, notification } from '@/utils/notification'

export default {
  name: 'GlobalNotification',
  setup() {
    return {
      notifications,
      notification
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.notification-container {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 10000;
  pointer-events: none;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.notification-item {
  pointer-events: auto;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
  border: 1px solid transparent;
  min-width: 320px;
  max-width: 480px;
  
  // 类型样式
  &.notification-success {
    border-color: $success-color;
    .notification-icon {
      color: $success-color;
    }
  }
  
  &.notification-error {
    border-color: $error-color;
    .notification-icon {
      color: $error-color;
    }
  }
  
  &.notification-warning {
    border-color: $warning-color;
    .notification-icon {
      color: $warning-color;
    }
  }
  
  &.notification-info {
    border-color: $info-color;
    .notification-icon {
      color: $info-color;
    }
  }
}

.notification-icon {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  margin-top: 1px;
  
  svg {
    width: 100%;
    height: 100%;
  }
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-message {
  display: block;
  font-size: 14px;
  line-height: 1.5;
  color: $gray-800;
  word-wrap: break-word;
}

.notification-close {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border: none;
  background: none;
  color: $gray-400;
  cursor: pointer;
  padding: 0;
  margin-top: 1px;
  
  &:hover {
    color: $gray-600;
  }
  
  svg {
    width: 100%;
    height: 100%;
  }
}

// 动画
.notification-enter-active,
.notification-leave-active {
  transition: all 0.3s ease;
}

.notification-enter-from {
  opacity: 0;
  transform: translateX(100%);
}

.notification-leave-to {
  opacity: 0;
  transform: translateX(100%);
}

.notification-move {
  transition: transform 0.3s ease;
}

// 响应式设计
@media (max-width: 640px) {
  .notification-container {
    top: 10px;
    right: 10px;
    left: 10px;
  }
  
  .notification-item {
    min-width: auto;
    max-width: none;
  }
}
</style>
