<template>
  <div class="comment-section">
    <!-- 评论区头部 -->
    <div class="comment-header">
      <h3 class="comment-title">评论 ({{ totalComments }})</h3>
      <div class="comment-controls">
        <div class="sort-options">
          <button
            v-for="option in sortOptions"
            :key="option.value"
            :class="['sort-btn', { active: sortBy === option.value }]"
            @click="handleSortChange(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
        <button class="close-btn" @click="$emit('close')" title="关闭评论区">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
    </div>

    <!-- 评论列表 -->
    <div class="comments-list" ref="commentsContainer">
      <!-- 加载状态 -->
      <div v-if="loading && comments.length === 0" class="loading-state">
        <div class="loading-spinner"></div>
        <p>正在加载评论...</p>
      </div>

      <!-- 空状态 -->
      <div v-else-if="!loading && comments.length === 0" class="empty-state">
        <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <p class="empty-text">还没有评论</p>
        <p class="empty-desc">成为第一个评论的人吧！</p>
      </div>

      <!-- 评论项 -->
      <div v-else>
        <CommentItem
          v-for="comment in comments"
          :key="comment.id"
          :comment="comment"
          :current-user="currentUser"
          :card-creator-id="cardCreatorId"
          @like="handleCommentLike"
          @reply="handleReply"
          @pin="handlePin"
          @delete="handleDelete"
          @toggle-replies="handleToggleReplies"
        />
      </div>

      <!-- 加载更多 -->
      <div v-if="hasMore" ref="loadTrigger" class="load-trigger"></div>
      
      <!-- 加载更多指示器 -->
      <div v-if="loading && comments.length > 0" class="load-more-indicator">
        <div class="loading-spinner"></div>
        <span>加载更多评论...</span>
      </div>

      <!-- 到底提示 -->
      <div v-if="!loading && !hasMore && comments.length > 0" class="bottom-tip">
        已显示全部评论
      </div>
    </div>

    <!-- 发表评论区域 -->
    <div class="comment-input-section">
      <div class="user-avatar">
        <div class="avatar-placeholder">
          {{ currentUser ? currentUser.username.charAt(0).toUpperCase() : 'U' }}
        </div>
      </div>
      <div class="input-area">
        <textarea
          v-model="newComment"
          class="comment-input"
          placeholder="写下你的评论..."
          rows="3"
          maxlength="1000"
          @keydown.ctrl.enter="submitComment"
          @keydown.meta.enter="submitComment"
        ></textarea>
        <div class="input-actions">
          <span class="char-count">{{ newComment.length }}/1000</span>
          <button
            class="submit-btn"
            :disabled="!newComment.trim() || submitting"
            @click="submitComment"
          >
            {{ submitting ? '发布中...' : '发布' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 回复对话框 -->
    <ReplyDialog
      v-if="replyingTo"
      :parent-comment="replyingTo"
      :card-id="cardId"
      @close="replyingTo = null"
      @submit="handleReplySubmit"
    />
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import CommentItem from './CommentItem.vue'
import ReplyDialog from './ReplyDialog.vue'
import { commentAPI } from '@/api'
import { storage } from '@/utils'

export default {
  name: 'CommentSection',
  components: {
    CommentItem,
    ReplyDialog
  },
  props: {
    cardId: {
      type: [String, Object],
      required: true
    },
    cardCreatorId: {
      type: [String, Object],
      required: true
    }
  },
  emits: ['close', 'comment-count-update'],
  setup(props, { emit }) {
    // 响应式数据
    const comments = ref([])
    const loading = ref(false)
    const submitting = ref(false)
    const hasMore = ref(true)
    const newComment = ref('')
    const replyingTo = ref(null)
    const commentsContainer = ref(null)
    const loadTrigger = ref(null)
    
    // 排序和分页
    const sortBy = ref('created_at')
    const currentPage = ref(0)
    const pageSize = ref(20)
    const cursor = ref(null)
    
    // 当前用户
    const currentUser = computed(() => storage.get('user'))
    
    // 排序选项
    const sortOptions = [
      { value: 'created_at', label: '最新' },
      { value: 'likes_count', label: '点赞最多' }
    ]
    
    // 计算总评论数
    const totalComments = computed(() => {
      return comments.value.length
    })

    // 加载评论
    const loadComments = async (append = false) => {
      if (loading.value) return
      
      loading.value = true
      try {
        const params = {
          cardId: props.cardId,
          sortBy: sortBy.value,
          sortOrder: 'desc',
          page: append ? currentPage.value : 0,
          size: pageSize.value
        }
        
        if (append && cursor.value) {
          params.cursor = cursor.value
        }
        
        const response = await commentAPI.getComments(params)
        const newComments = response.data || []
        
        if (append) {
          comments.value.push(...newComments)
        } else {
          comments.value = newComments
          currentPage.value = 0
        }
        
        hasMore.value = newComments.length === pageSize.value
        if (newComments.length > 0) {
          cursor.value = newComments[newComments.length - 1].id
          currentPage.value += 1
        }
        
        // 通知父组件更新评论数
        emit('comment-count-update', comments.value.length)
        
      } catch (error) {
        console.error('加载评论失败:', error)
      } finally {
        loading.value = false
      }
    }

    // 提交评论
    const submitComment = async () => {
      if (!newComment.value.trim() || submitting.value) return
      
      submitting.value = true
      try {
        const request = {
          cardId: props.cardId,
          content: newComment.value.trim()
        }
        
        const response = await commentAPI.createComment(request)
        
        // 添加到评论列表顶部
        comments.value.unshift(response.data)
        newComment.value = ''
        
        // 通知父组件更新评论数
        emit('comment-count-update', comments.value.length)
        
      } catch (error) {
        console.error('发表评论失败:', error)
      } finally {
        submitting.value = false
      }
    }

    // 处理排序变化
    const handleSortChange = (newSortBy) => {
      if (sortBy.value === newSortBy) return
      
      sortBy.value = newSortBy
      cursor.value = null
      loadComments(false)
    }

    // 处理评论点赞
    const handleCommentLike = async (commentId) => {
      try {
        const response = await commentAPI.toggleCommentLike(commentId)
        const isLiked = response.data
        
        // 更新评论的点赞状态（支持主评论和回复）
        const comment = comments.value.find(c => c.id === commentId)
        if (comment) {
          comment.isLikedByCurrentUser = isLiked
          comment.likesCount += isLiked ? 1 : -1
        } else {
          // 在回复中查找
          for (const mainComment of comments.value) {
            if (mainComment.replies) {
              const reply = mainComment.replies.find(r => r.id === commentId)
              if (reply) {
                reply.isLikedByCurrentUser = isLiked
                reply.likesCount += isLiked ? 1 : -1
                break
              }
            }
          }
        }
      } catch (error) {
        console.error('点赞失败:', error)
      }
    }

    // 处理回复
    const handleReply = (comment) => {
      replyingTo.value = comment
    }

    // 处理回复提交
    const handleReplySubmit = (reply) => {
      // 找到父评论并添加回复
      const parentComment = comments.value.find(c => c.id === reply.parentCommentId)
      if (parentComment) {
        if (!parentComment.replies) {
          parentComment.replies = []
        }
        parentComment.replies.push(reply)
        parentComment.repliesCount = (parentComment.repliesCount || 0) + 1
      }
      
      replyingTo.value = null
      
      // 通知父组件更新评论数（包括回复）
      const totalCount = comments.value.length + comments.value.reduce((sum, c) => sum + (c.repliesCount || 0), 0)
      emit('comment-count-update', totalCount)
    }

    // 处理置顶
    const handlePin = async (commentId) => {
      try {
        await commentAPI.toggleCommentPin(commentId)
        
        // 重新加载评论以反映置顶状态变化
        await loadComments(false)
      } catch (error) {
        console.error('置顶失败:', error)
      }
    }

    // 处理删除
    const handleDelete = async (commentId) => {
      if (!confirm('确定要删除这条评论吗？')) return
      
      try {
        await commentAPI.deleteComment(commentId)
        
        // 从列表中移除评论（支持主评论和回复）
        const index = comments.value.findIndex(c => c.id === commentId)
        if (index > -1) {
          // 删除主评论
          comments.value.splice(index, 1)
          emit('comment-count-update', comments.value.length)
        } else {
          // 在回复中查找并删除
          for (const mainComment of comments.value) {
            if (mainComment.replies) {
              const replyIndex = mainComment.replies.findIndex(r => r.id === commentId)
              if (replyIndex > -1) {
                mainComment.replies.splice(replyIndex, 1)
                mainComment.repliesCount = Math.max(0, (mainComment.repliesCount || 0) - 1)
                
                // 更新总评论数
                const totalCount = comments.value.length + comments.value.reduce((sum, c) => sum + (c.repliesCount || 0), 0)
                emit('comment-count-update', totalCount)
                break
              }
            }
          }
        }
      } catch (error) {
        console.error('删除评论失败:', error)
      }
    }

    // 处理回复展开/收缩
    const handleToggleReplies = async (comment) => {
      if (comment.showReplies && comment.replies) {
        // 收缩回复
        comment.showReplies = false
        return
      }
      
      // 展开回复
      if (!comment.replies || comment.replies.length === 0) {
        // 加载回复
        try {
          const response = await commentAPI.getCommentReplies(comment.id)
          comment.replies = response.data || []
        } catch (error) {
          console.error('加载回复失败:', error)
          return
        }
      }
      
      comment.showReplies = true
    }

    // 无缝滚动加载
    const setupInfiniteScroll = () => {
      if (!loadTrigger.value) return
      
      const observer = new IntersectionObserver(
        (entries) => {
          const entry = entries[0]
          if (entry.isIntersecting && hasMore.value && !loading.value) {
            loadComments(true)
          }
        },
        { threshold: 0.1 }
      )
      
      observer.observe(loadTrigger.value)
      
      return () => observer.disconnect()
    }

    // 组件挂载
    onMounted(async () => {
      await loadComments()
      await nextTick()
      
      const cleanup = setupInfiniteScroll()
      onUnmounted(cleanup)
    })

    return {
      // 数据
      comments,
      loading,
      submitting,
      hasMore,
      newComment,
      replyingTo,
      commentsContainer,
      loadTrigger,
      
      // 排序和分页
      sortBy,
      sortOptions,
      totalComments,
      
      // 用户
      currentUser,
      
      // 方法
      loadComments,
      submitComment,
      handleSortChange,
      handleCommentLike,
      handleReply,
      handleReplySubmit,
      handlePin,
      handleDelete,
      handleToggleReplies
    }
  }
}
</script>

<style lang="scss" scoped>
.comment-section {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--background-primary);
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing;
  border-bottom: 1px solid var(--border-light);

  .comment-title {
    margin: 0;
    font-size: 1.25rem;
    font-weight: 600;
    color: var(--text-primary);
  }

  .comment-controls {
    display: flex;
    align-items: center;
    gap: $spacing;
  }

  .sort-options {
    display: flex;
    gap: $spacing-xs;
  }

  .sort-btn {
    background: transparent;
    border: 1px solid var(--border-light);
    border-radius: $border-radius;
    padding: $spacing-xs $spacing-sm;
    color: var(--text-secondary);
    cursor: pointer;
    transition: all $transition-fast ease;
    font-size: 14px;

    &:hover {
      border-color: var(--primary-color);
      color: var(--primary-color);
    }

    &.active {
      background: var(--primary-color);
      border-color: var(--primary-color);
      color: white;
    }
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

.comment-input-section {
  display: flex;
  gap: $spacing;
  padding: $spacing;
  border-top: 1px solid var(--border-light);
  flex-shrink: 0; /* 防止压缩 */

  .user-avatar {
    flex-shrink: 0;

    .avatar-placeholder {
      width: 40px;
      height: 40px;
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
    gap: $spacing-sm;
  }

  .comment-input {
    width: 100%;
    border: 1px solid var(--border-light);
    border-radius: $border-radius;
    padding: $spacing-sm;
    font-family: inherit;
    font-size: 14px;
    color: var(--text-primary);
    background: var(--background-secondary);
    resize: vertical;
    min-height: 80px;
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

.comments-list {
  flex: 1;
  overflow-y: auto;
  padding: $spacing;
  min-height: 0; /* 允许弹性收缩 */
}

.loading-state, .empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl;
  color: var(--text-secondary);

  .loading-spinner {
    width: 24px;
    height: 24px;
    border: 2px solid var(--border-light);
    border-top: 2px solid var(--primary-color);
    border-radius: 50%;
    animation: spin 1s linear infinite;
    margin-bottom: $spacing;
  }

  .empty-icon {
    width: 48px;
    height: 48px;
    margin-bottom: $spacing;
    stroke: var(--text-secondary);
  }

  .empty-text {
    font-size: 16px;
    font-weight: 500;
    margin-bottom: $spacing-xs;
  }

  .empty-desc {
    font-size: 14px;
    margin: 0;
  }
}

.load-trigger {
  height: 20px;
}

.load-more-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing;
  color: var(--text-secondary);

  .loading-spinner {
    width: 16px;
    height: 16px;
    border: 1px solid var(--border-light);
    border-top: 1px solid var(--primary-color);
    border-radius: 50%;
    animation: spin 1s linear infinite;
  }
}

.bottom-tip {
  text-align: center;
  padding: $spacing;
  color: var(--text-secondary);
  font-size: 14px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style>