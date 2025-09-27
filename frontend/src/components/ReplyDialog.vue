<template>
  <div class="reply-dialog-overlay" @click="handleOverlayClick">
    <div class="reply-dialog" @click.stop>
      <div class="dialog-header">
        <h3 class="dialog-title">回复评论</h3>
        <button class="close-btn" @click="$emit('close')" title="关闭">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>

      <!-- 被回复的评论 -->
      <div class="original-comment">
        <div class="comment-header">
          <div class="user-info">
            <div class="user-avatar">
              <img
                v-if="parentComment.authorAvatar"
                :src="parentComment.authorAvatar"
                :alt="parentComment.authorName"
                class="avatar-image"
              />
              <div v-else class="avatar-placeholder">
                {{ parentComment.authorName ? parentComment.authorName.charAt(0).toUpperCase() : 'U' }}
              </div>
            </div>
            <div class="user-details">
              <span class="author-name">{{ parentComment.authorName }}</span>
              <span class="comment-time">{{ formatRelativeDate(parentComment.createdAt) }}</span>
            </div>
          </div>
        </div>
        <div class="comment-content">{{ parentComment.content }}</div>
      </div>

      <!-- 回复输入区域 -->
      <div class="reply-input-section">
        <div class="user-avatar">
          <div class="avatar-placeholder">
            {{ currentUser ? currentUser.username.charAt(0).toUpperCase() : 'U' }}
          </div>
        </div>
        <div class="input-area">
          <textarea
            ref="replyInput"
            v-model="replyContent"
            class="reply-input"
            :placeholder="replyPlaceholder"
            rows="4"
            maxlength="1000"
            @keydown.ctrl.enter="submitReply"
            @keydown.meta.enter="submitReply"
          ></textarea>
          <div class="input-actions">
            <span class="char-count">{{ replyContent.length }}/1000</span>
            <div class="buttons">
              <button class="cancel-btn" @click="$emit('close')">取消</button>
              <button
                class="submit-btn"
                :disabled="!replyContent.trim() || submitting"
                @click="submitReply"
              >
                {{ submitting ? '发布中...' : '发布回复' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted, nextTick } from 'vue'
import { commentAPI } from '@/api'
import { storage } from '@/utils'

export default {
  name: 'ReplyDialog',
  props: {
    parentComment: {
      type: Object,
      required: true
    },
    cardId: {
      type: String,
      required: true
    },
    targetReply: {
      type: Object,
      default: null
    }
  },
  emits: ['close', 'submit'],
  setup(props, { emit }) {
    const replyContent = ref('')
    const submitting = ref(false)
    const replyInput = ref(null)

    // 当前用户
    const currentUser = computed(() => storage.get('user'))

    // 回复占位符文本
    const replyPlaceholder = computed(() => {
      if (props.targetReply) {
        return `回复 @${props.targetReply.authorName}：`
      }
      return `回复 @${props.parentComment.authorName}：`
    })

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

    // 提交回复
    const submitReply = async () => {
      if (!replyContent.value.trim() || submitting.value) return

      submitting.value = true
      try {
        let content = replyContent.value.trim()
        
        // 如果是回复某个具体的回复，在内容前加上@用户名
        if (props.targetReply) {
          content = `@${props.targetReply.authorName} ${content}`
        }

        const request = {
          cardId: props.cardId,
          content: content,
          parentCommentId: props.parentComment.id
        }

        const response = await commentAPI.createComment(request)
        
        // 通知父组件回复成功
        emit('submit', response.data)
        emit('close')
        
      } catch (error) {
        console.error('发布回复失败:', error)
      } finally {
        submitting.value = false
      }
    }

    // 处理遮罩层点击
    const handleOverlayClick = (event) => {
      if (event.target === event.currentTarget) {
        emit('close')
      }
    }

    // 组件挂载后聚焦输入框
    onMounted(async () => {
      await nextTick()
      if (replyInput.value) {
        replyInput.value.focus()
      }
    })

    return {
      replyContent,
      submitting,
      replyInput,
      currentUser,
      replyPlaceholder,
      formatRelativeDate,
      submitReply,
      handleOverlayClick
    }
  }
}
</script>

<style lang="scss" scoped>
.reply-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: $spacing;
}

.reply-dialog {
  background: var(--background-primary);
  border-radius: $border-radius-lg;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
  width: 100%;
  max-width: 600px;
  max-height: 80vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing $spacing-lg;
  border-bottom: 1px solid var(--border-light);

  .dialog-title {
    margin: 0;
    font-size: 1.125rem;
    font-weight: 600;
    color: var(--text-primary);
  }

  .close-btn {
    background: transparent;
    border: none;
    color: var(--text-secondary);
    cursor: pointer;
    padding: $spacing-xs;
    border-radius: $border-radius;
    transition: all $transition-fast ease;

    &:hover {
      background: var(--background-tertiary);
      color: var(--text-primary);
    }
  }
}

.original-comment {
  padding: $spacing-lg;
  background: var(--background-secondary);
  border-bottom: 1px solid var(--border-light);

  .comment-header {
    margin-bottom: $spacing-sm;
  }

  .user-info {
    display: flex;
    align-items: center;
    gap: $spacing;

    .user-avatar {
      .avatar-image {
        width: 32px;
        height: 32px;
        border-radius: 50%;
        object-fit: cover;
      }

      .avatar-placeholder {
        width: 32px;
        height: 32px;
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

    .user-details {
      display: flex;
      flex-direction: column;
      gap: 2px;

      .author-name {
        font-weight: 600;
        color: var(--text-primary);
        font-size: 14px;
      }

      .comment-time {
        color: var(--text-secondary);
        font-size: 12px;
      }
    }
  }

  .comment-content {
    color: var(--text-primary);
    line-height: 1.5;
    font-size: 14px;
    white-space: pre-wrap;
    word-wrap: break-word;
  }
}

.reply-input-section {
  display: flex;
  gap: $spacing;
  padding: $spacing-lg;
  flex: 1;

  .user-avatar {
    flex-shrink: 0;

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
      font-size: 16px;
    }
  }

  .input-area {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: $spacing;
  }

  .reply-input {
    width: 100%;
    border: 1px solid var(--border-light);
    border-radius: $border-radius;
    padding: $spacing;
    font-family: inherit;
    font-size: 14px;
    color: var(--text-primary);
    background: var(--background-secondary);
    resize: vertical;
    min-height: 100px;
    transition: border-color $transition-fast ease;

    &:focus {
      outline: none;
      border-color: var(--primary-color);
    }

    &::placeholder {
      color: var(--text-secondary);
    }
  }

  .input-actions {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .char-count {
      font-size: 12px;
      color: var(--text-secondary);
    }

    .buttons {
      display: flex;
      gap: $spacing;

      .cancel-btn {
        background: transparent;
        border: 1px solid var(--border-light);
        border-radius: $border-radius;
        padding: $spacing-xs $spacing;
        color: var(--text-secondary);
        cursor: pointer;
        font-weight: 500;
        transition: all $transition-fast ease;

        &:hover {
          border-color: var(--border-color);
          color: var(--text-primary);
        }
      }

      .submit-btn {
        background: var(--primary-color);
        border: none;
        border-radius: $border-radius;
        padding: $spacing-xs $spacing;
        color: white;
        cursor: pointer;
        font-weight: 500;
        transition: all $transition-fast ease;

        &:hover:not(:disabled) {
          background: var(--primary-hover);
        }

        &:disabled {
          background: var(--background-tertiary);
          color: var(--text-secondary);
          cursor: not-allowed;
        }
      }
    }
  }
}
</style>