<template>
  <div class="comment-item" :class="{ pinned: comment.isPinned }">
    <!-- 置顶标识 -->
    <div v-if="comment.isPinned" class="pinned-badge">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="12" y1="17" x2="12" y2="3"></line>
        <path d="M5 7h14l-2-2H7z"></path>
      </svg>
      置顶
    </div>

    <!-- 评论主体 -->
    <div class="comment-body">
      <!-- 用户头像 -->
      <div class="user-avatar">
        <img
          v-if="comment.authorAvatar"
          :src="comment.authorAvatar"
          :alt="comment.authorName"
          class="avatar-image"
        />
        <div v-else class="avatar-placeholder">
          {{ comment.authorName ? comment.authorName.charAt(0).toUpperCase() : 'U' }}
        </div>
      </div>

      <!-- 评论内容区域 -->
      <div class="comment-content">
        <!-- 用户信息和时间 -->
        <div class="comment-meta">
          <span class="author-name">{{ comment.authorName }}</span>
          <span v-if="isCardCreator" class="creator-badge">作者</span>
          <span class="comment-time" :title="formatFullDate(comment.createdAt)">
            {{ formatRelativeDate(comment.createdAt) }}
          </span>
        </div>

        <!-- 评论文本 -->
        <div class="comment-text">{{ comment.content }}</div>

        <!-- 操作按钮 -->
        <div class="comment-actions">
          <!-- 点赞按钮 -->
          <button
            :class="['action-btn', 'like-btn', { liked: comment.isLikedByCurrentUser }]"
            @click="$emit('like', comment.id)"
            :title="comment.isLikedByCurrentUser ? '取消点赞' : '点赞'"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
            </svg>
            <span v-if="comment.likesCount > 0">{{ formatCount(comment.likesCount) }}</span>
          </button>

          <!-- 回复按钮 -->
          <button class="action-btn reply-btn" @click="$emit('reply', comment)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            回复
          </button>

          <!-- 置顶按钮（仅角色卡作者可见） -->
          <button
            v-if="comment.canPin"
            class="action-btn pin-btn"
            @click="$emit('pin', comment.id)"
            :title="comment.isPinned ? '取消置顶' : '置顶'"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="17" x2="12" y2="3"></line>
              <path d="M5 7h14l-2-2H7z"></path>
            </svg>
            {{ comment.isPinned ? '取消置顶' : '置顶' }}
          </button>

          <!-- 删除按钮（评论作者或角色卡作者可见） -->
          <button
            v-if="comment.canDelete"
            class="action-btn delete-btn"
            @click="$emit('delete', comment.id)"
            title="删除评论"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 6h18"/>
              <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
              <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
              <line x1="10" y1="11" x2="10" y2="17"/>
              <line x1="14" y1="11" x2="14" y2="17"/>
            </svg>
            删除
          </button>
        </div>

        <!-- 回复列表 -->
        <div v-if="comment.repliesCount > 0" class="replies-section">
          <!-- 展开/收缩回复按钮 -->
          <button
            class="toggle-replies-btn"
            @click="$emit('toggle-replies', comment)"
          >
            <svg
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              :class="{ rotated: comment.showReplies }"
            >
              <polyline points="6,9 12,15 18,9"></polyline>
            </svg>
            {{ comment.showReplies ? '收起' : '展开' }}
            {{ comment.repliesCount }} 条回复
          </button>

          <!-- 回复列表 -->
          <div v-if="comment.showReplies && comment.replies" class="replies-list">
            <div
              v-for="reply in comment.replies"
              :key="reply.id"
              class="reply-item"
            >
              <!-- 回复头像 -->
              <div class="reply-avatar">
                <img
                  v-if="reply.authorAvatar"
                  :src="reply.authorAvatar"
                  :alt="reply.authorName"
                  class="avatar-image"
                />
                <div v-else class="avatar-placeholder">
                  {{ reply.authorName ? reply.authorName.charAt(0).toUpperCase() : 'U' }}
                </div>
              </div>

              <!-- 回复内容 -->
              <div class="reply-content">
                <div class="reply-meta">
                  <span class="author-name">{{ reply.authorName }}</span>
                  <span v-if="reply.authorId === cardCreatorId" class="creator-badge">作者</span>
                  <span class="comment-time" :title="formatFullDate(reply.createdAt)">
                    {{ formatRelativeDate(reply.createdAt) }}
                  </span>
                </div>

                <div class="reply-text">{{ reply.content }}</div>

                <div class="reply-actions">
                  <!-- 回复的点赞按钮 -->
                  <button
                    :class="['action-btn', 'like-btn', { liked: reply.isLikedByCurrentUser }]"
                    @click="$emit('like', reply.id)"
                    :title="reply.isLikedByCurrentUser ? '取消点赞' : '点赞'"
                  >
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                    </svg>
                    <span v-if="reply.likesCount > 0">{{ formatCount(reply.likesCount) }}</span>
                  </button>


                  <!-- 回复的删除按钮 -->
                  <button
                    v-if="reply.canDelete"
                    class="action-btn delete-btn"
                    @click="$emit('delete', reply.id)"
                    title="删除回复"
                  >
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M3 6h18"/>
                      <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
                      <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
                      <line x1="10" y1="11" x2="10" y2="17"/>
                      <line x1="14" y1="11" x2="14" y2="17"/>
                    </svg>
                    删除
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { computed } from 'vue'

export default {
  name: 'CommentItem',
  props: {
    comment: {
      type: Object,
      required: true
    },
    currentUser: {
      type: Object,
      default: null
    },
    cardCreatorId: {
      type: String,
      required: true
    }
  },
  emits: ['like', 'reply', 'pin', 'delete', 'toggle-replies'],
  setup(props) {
    // 判断是否为角色卡作者
    const isCardCreator = computed(() => {
      return props.comment.authorId === props.cardCreatorId
    })

    // 格式化计数
    const formatCount = (count) => {
      if (typeof count !== 'number' || count == null || isNaN(count)) {
        return '0'
      }
      
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

    return {
      isCardCreator,
      formatCount,
      formatRelativeDate,
      formatFullDate
    }
  }
}
</script>

<style lang="scss" scoped>
.comment-item {
  margin-bottom: $spacing;
  
  &.pinned {
    .comment-body {
      background: var(--background-secondary);
      border: 1px solid var(--primary-color);
      border-radius: $border-radius;
    }
  }
}

.pinned-badge {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  color: var(--primary-color);
  font-size: 12px;
  font-weight: 500;
  margin-bottom: $spacing-xs;
  
  svg {
    stroke: currentColor;
  }
}

.comment-body {
  display: flex;
  gap: $spacing;
  padding: $spacing;
  transition: all $transition-fast ease;
}

.user-avatar, .reply-avatar {
  flex-shrink: 0;
  
  .avatar-image {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    object-fit: cover;
  }
  
  .avatar-placeholder {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: var(--primary-color);
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 600;
    font-size: 14px;
  }
}

.comment-content {
  flex: 1;
  min-width: 0;
}

.comment-meta, .reply-meta {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-xs;
  
  .author-name {
    font-weight: 600;
    color: var(--text-primary);
    font-size: 14px;
  }
  
  .creator-badge {
    background: var(--primary-color);
    color: white;
    font-size: 10px;
    padding: 2px 6px;
    border-radius: $border-radius-sm;
    font-weight: 500;
  }
  
  .comment-time {
    color: var(--text-secondary);
    font-size: 12px;
  }
  
}

.comment-text, .reply-text {
  color: var(--text-primary);
  line-height: 1.5;
  margin-bottom: $spacing-sm;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.comment-actions, .reply-actions {
  display: flex;
  align-items: center;
  gap: $spacing;
  margin-top: $spacing-sm;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  background: transparent;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 12px;
  padding: $spacing-xs;
  border-radius: $border-radius;
  transition: all $transition-fast ease;
  
  &:hover {
    background: var(--background-tertiary);
    color: var(--text-primary);
  }
  
  &.like-btn {
    &.liked {
      color: var(--error-color);
      
      svg {
        fill: currentColor;
      }
    }
  }
  
  &.pin-btn:hover {
    color: var(--primary-color);
  }
  
  &.delete-btn:hover {
    color: var(--error-color);
  }
}

.replies-section {
  margin-top: $spacing;
  border-left: 2px solid var(--border-light);
  padding-left: $spacing;
}

.toggle-replies-btn {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  background: transparent;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 13px;
  padding: $spacing-xs 0;
  transition: all $transition-fast ease;
  
  &:hover {
    color: var(--primary-color);
  }
  
  svg {
    transition: transform $transition-fast ease;
    
    &.rotated {
      transform: rotate(-180deg);
    }
  }
}

.replies-list {
  margin-top: $spacing;
}

.reply-item {
  display: flex;
  gap: $spacing-sm;
  padding: $spacing-sm 0;
  
  &:not(:last-child) {
    border-bottom: 1px solid var(--border-light);
  }
}

.reply-avatar {
  .avatar-image, .avatar-placeholder {
    width: 28px;
    height: 28px;
    font-size: 12px;
  }
}

.reply-content {
  flex: 1;
  min-width: 0;
}

.reply-meta {
  .author-name {
    font-size: 13px;
  }
  
  .comment-time {
    font-size: 11px;
  }
}

.reply-text {
  font-size: 13px;
  margin-bottom: $spacing-xs;
}

.reply-actions {
  gap: $spacing-sm;
  
  .action-btn {
    font-size: 11px;
    padding: 2px $spacing-xs;
    
    svg {
      width: 12px;
      height: 12px;
    }
  }
}
</style>