<template>
  <div class="character-card" @click="handleCardClick">
    <!-- 左侧区域：头像和点赞 -->
    <div class="card-left">
      <div class="character-avatar">
        <img 
          v-if="card && card.avatarUrl" 
          :src="card.avatarUrl" 
          :alt="card ? card.name : '角色头像'"
          class="avatar-image"
          @error="handleImageError"
        />
        <div v-else class="avatar-placeholder">
          {{ card && card.name ? card.name.charAt(0).toUpperCase() : '?' }}
        </div>
      </div>
      
      <button
        @click.stop="handleLike"
        :class="['like-section', { liked: card && card.isLikedByCurrentUser }]"
        :title="card && card.isLikedByCurrentUser ? '取消点赞' : '点赞'"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="like-icon">
          <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
        </svg>
        <span class="like-count">{{ formatCount((card && typeof card.likesCount === 'number') ? card.likesCount : 0) }}</span>
      </button>
    </div>

    <!-- 右侧区域：角色信息 -->
    <div class="card-right">
      <div class="card-main">
        <h3 class="character-name">{{ card ? card.name : '未知角色' }}</h3>
        <p v-if="card && card.shortDescription" class="character-description">
          {{ card.shortDescription }}
        </p>
        <p v-else-if="card && card.greetingMessage" class="character-description">
          {{ card.greetingMessage }}
        </p>
      </div>

      <!-- 卡片底部元信息 -->
      <div class="card-meta">
        <div class="creator-info">
          <span class="creator-name">{{ card && card.creatorUsername ? card.creatorUsername : '未知' }}</span>
        </div>
        <div class="created-date">
          {{ card ? formatRelativeDate(card.createdAt) : '' }}
        </div>
        <div v-if="card && card.ttsVoiceId" class="voice-indicator" title="支持语音">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
            <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
            <line x1="12" y1="19" x2="12" y2="23"/>
            <line x1="8" y1="23" x2="16" y2="23"/>
          </svg>
        </div>
      </div>
    </div>

    <!-- 权限用户的删除按钮 -->
    <div v-if="isOwnCard" class="card-actions" @click.stop>
      <button class="delete-btn" @click="handleDelete" title="删除角色">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M3 6h18"/>
          <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
          <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
          <line x1="10" y1="11" x2="10" y2="17"/>
          <line x1="14" y1="11" x2="14" y2="17"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script>
import { computed, ref } from 'vue'
import { storage } from '@/utils'

export default {
  name: 'CharacterCard',
  props: {
    card: {
      type: Object,
      required: true
    }
  },
  emits: ['like', 'click', 'delete', 'detail'],
  setup(props, { emit }) {
    // 计算是否为自己的卡片
    const isOwnCard = computed(() => {
      const user = storage.get('user')
      return user && props.card.creatorId === user.userId
    })

    // 格式化计数
    const formatCount = (count) => {
      // 处理 undefined、null 或非数字值
      if (typeof count !== 'number' || count == null || isNaN(count)) {
        return '0'
      }
      
      // 确保是正数
      count = Math.max(0, count)
      
      if (count >= 1000000) {
        return Math.floor(count / 100000) / 10 + 'M'
      } else if (count >= 1000) {
        return Math.floor(count / 100) / 10 + 'k'
      }
      return count.toString()
    }

    // 格式化相对时间
    const formatRelativeDate = (dateString) => {
      if (!dateString) return ''
      
      const date = new Date(dateString)
      const now = new Date()
      const diffTime = Math.abs(now - date)
      const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24))
      const diffHours = Math.floor(diffTime / (1000 * 60 * 60))
      const diffMinutes = Math.floor(diffTime / (1000 * 60))

      if (diffDays > 30) {
        return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
      } else if (diffDays > 0) {
        return `${diffDays}天前`
      } else if (diffHours > 0) {
        return `${diffHours}小时前`
      } else if (diffMinutes > 0) {
        return `${diffMinutes}分钟前`
      } else {
        return '刚刚'
      }
    }

    // 格式化完整时间
    const formatFullDate = (dateString) => {
      if (!dateString) return ''
      
      const date = new Date(dateString)
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      })
    }

    // 处理图片加载错误
    const handleImageError = (event) => {
      // 隐藏图片，显示默认头像
      event.target.style.display = 'none'
    }

    // 处理点赞
    const handleLike = () => {
      emit('like', props.card.id)
    }

    // 处理卡片点击 - 显示详情
    const handleCardClick = () => {
      emit('detail', props.card)
    }

    // 处理删除
    const handleDelete = () => {
      emit('delete', props.card)
    }

    return {
      isOwnCard,
      formatCount,
      formatRelativeDate,
      formatFullDate,
      handleImageError,
      handleLike,
      handleCardClick,
      handleDelete
    }
  }
}
</script>

<style lang="scss" scoped>
.character-card {
  position: relative;
  display: flex;
  gap: $spacing;
  background: var(--background-secondary);
  border: 1px solid var(--border-light);
  border-radius: $border-radius-lg;
  padding: $spacing;
  cursor: pointer;
  transition: all $transition-fast ease;
  overflow: hidden;
  animation: slideInCard 0.3s ease-out;
  animation-fill-mode: both;

  &:hover {
    border-color: var(--border-color);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(0);
  }
}

@keyframes slideInCard {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.card-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
  flex-shrink: 0;
}

.character-avatar {
  width: 80px;
  height: 80px;
  border-radius: $border-radius;
  overflow: hidden;
  background: var(--background-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;

  .avatar-image {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .avatar-placeholder {
    font-size: 2rem;
    font-weight: 600;
    color: var(--text-secondary);
  }
}

.like-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  background: transparent;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all $transition-fast ease;
  padding: $spacing-xs;
  border-radius: $border-radius;

  &:hover {
    background: var(--background-tertiary);
    color: var(--text-primary);
  }

  &.liked {
    color: var(--error-color);

    .like-icon {
      fill: currentColor;
    }
  }

  .like-icon {
    width: 16px;
    height: 16px;
  }

  .like-count {
    font-size: 12px;
    font-weight: 500;
    min-width: 20px;
    text-align: center;
  }
}

.card-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.card-main {
  flex: 1;
  margin-bottom: $spacing-sm;
}

.character-name {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 $spacing-xs 0;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.character-description {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: auto;
}

.creator-info {
  .creator-name {
    font-weight: 500;
  }
}

.created-date {
  &::before {
    content: '•';
    margin-right: $spacing-xs;
  }
}

.voice-indicator {
  display: flex;
  align-items: center;
  color: var(--success-color);
  
  &::before {
    content: '•';
    margin-right: $spacing-xs;
    color: var(--text-tertiary);
  }

  svg {
    width: 12px;
    height: 12px;
  }
}

.card-actions {
  position: absolute;
  top: $spacing-sm;
  right: $spacing-sm;
}

.delete-btn {
  background: transparent;
  border: none;
  padding: $spacing-xs;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all $transition-fast ease;
  border-radius: $border-radius-sm;
  opacity: 0;
  transform: translateY(-5px);

  &:hover {
    background: var(--error-color);
    color: white;
    transform: translateY(-5px) scale(1.1);
  }
}

.character-card:hover .delete-btn {
  opacity: 1;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: $spacing;
}

.character-info {
  flex: 1;
  min-width: 0;
}

.character-name {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 $spacing-xs 0;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.character-description {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-actions {
  flex-shrink: 0;
  margin-left: $spacing-sm;
}

.like-btn {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: $border-radius;
  padding: $spacing-xs $spacing-sm;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all $transition-fast ease;

  &:hover {
    background: var(--background-tertiary);
    border-color: var(--primary-color);
    color: var(--primary-color);
  }

  &.liked {
    background: rgba(217, 119, 6, 0.1);
    border-color: var(--primary-color);
    color: var(--primary-color);

    svg {
      fill: currentColor;
    }

    &:hover {
      background: rgba(217, 119, 6, 0.15);
    }
  }

  svg {
    width: 14px;
    height: 14px;
  }

  .like-count {
    font-weight: 500;
    min-width: 16px;
    text-align: center;
  }
}

.card-content {
  margin-bottom: $spacing;
}

.greeting-message {
  display: flex;
  gap: $spacing-xs;
  align-items: flex-start;
  padding: $spacing-sm;
  background: var(--background-tertiary);
  border-radius: $border-radius;
  margin-bottom: $spacing-sm;
  border-left: 3px solid var(--primary-color);
}

.greeting-icon {
  flex-shrink: 0;
  color: var(--primary-color);
  margin-top: 2px;
}

.greeting-text {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.character-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
}

.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.2;

  &.voice-tag {
    background: rgba(34, 197, 94, 0.1);
    color: #059669;
  }

  &.public-tag {
    background: rgba(59, 130, 246, 0.1);
    color: #2563eb;
  }
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: $spacing-sm;
  border-top: 1px solid var(--border-light);
}

.creator-info {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  min-width: 0;
}

.creator-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--primary-color);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.creator-name {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  flex-shrink: 0;
}

.created-date {
  font-size: 11px;
  color: var(--text-tertiary);
}

.card-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity $transition-fast ease;
  border-radius: $border-radius-lg;
}

.overlay-actions {
  display: flex;
  gap: $spacing-sm;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-xs $spacing-sm;
  background: white;
  border: none;
  border-radius: $border-radius;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-fast ease;

  &:hover {
    background: var(--background-tertiary);
  }

  &.chat-btn {
    background: var(--primary-color);
    color: white;

    &:hover {
      background: var(--primary-dark);
    }
  }

  &.edit-btn {
    background: var(--background-tertiary);
    border: 1px solid var(--border-color);
  }

  svg {
    width: 14px;
    height: 14px;
  }
}

// 响应式设计
@media (max-width: 768px) {
  .character-card {
    padding: $spacing-sm;
  }

  .card-header {
    margin-bottom: $spacing-sm;
  }

  .character-name {
    font-size: 1rem;
  }

  .character-description {
    font-size: 13px;
    -webkit-line-clamp: 1;
    line-clamp: 1;
  }

  .like-btn {
    padding: 4px 6px;
    
    .like-count {
      display: none;
    }
  }

  .greeting-message {
    padding: $spacing-xs;
    margin-bottom: $spacing-xs;
  }

  .greeting-text {
    font-size: 12px;
    -webkit-line-clamp: 2;
    line-clamp: 2;
  }

  .creator-name {
    font-size: 11px;
  }

  .created-date {
    font-size: 10px;
  }

  .overlay-actions {
    flex-direction: column;
    gap: $spacing-xs;
  }

  .action-btn {
    padding: $spacing-xs $spacing-sm;
    font-size: 12px;
  }
}
</style>
