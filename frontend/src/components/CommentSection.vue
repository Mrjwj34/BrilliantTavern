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
            :disabled="loading"
            @click="handleSortChange(option.value)"
          >
            <span>{{ typeof option.label === 'string' ? option.label : option.label.value }}</span>
            <svg v-if="option.value === 'created_at' && sortBy === 'created_at'" 
                 class="sort-icon" 
                 :class="{ 'rotate-180': sortOrder === 'asc' }" 
                 width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M6 9l6 6 6-6"/>
            </svg>
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
        <transition-group name="comment-list" tag="div" class="comment-transition-wrapper">
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
        </transition-group>
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
    const sortOrder = ref('desc') // desc: 新到旧, asc: 旧到新
    const currentPage = ref(0)
    const pageSize = ref(20)
    const cursor = ref(null)
    const totalCommentsCount = ref(0)
    
    // 当前用户
    const currentUser = computed(() => storage.get('user'))
    
    // 排序选项
    const sortOptions = [
      { 
        value: 'created_at', 
        label: computed(() => sortBy.value === 'created_at' && sortOrder.value === 'desc' ? '最新' : '最旧'),
        order: computed(() => sortBy.value === 'created_at' ? sortOrder.value : 'desc')
      },
      { value: 'likes_count', label: '点赞最多', order: 'desc' }
    ]
    
    // 计算总评论数
    const totalComments = computed(() => {
      return totalCommentsCount.value
    })

    // 加载评论
    const loadComments = async (append = false) => {
      if (loading.value) return
      
      loading.value = true
      try {
        const params = {
          cardId: props.cardId,
          sortBy: sortBy.value,
          sortOrder: sortOrder.value,
          page: append ? currentPage.value : 0,
          size: pageSize.value
        }
        
        // 只有在追加加载且有游标时才传递cursor参数
        if (append && cursor.value) {
          params.cursor = cursor.value
        }
        
        console.log('分页请求参数:', params) // 调试日志
        
        const response = await commentAPI.getComments(params)
        const pageData = response.data || {}
        const newComments = pageData.comments || []
        
        console.log('分页响应数据:', { 
          newCommentsCount: newComments.length, 
          hasMore: pageData.hasMore,
          nextCursor: pageData.nextCursor,
          totalCount: pageData.totalCount
        }) // 调试日志
        
        if (append) {
          // 追加新评论到现有列表
          comments.value.push(...newComments)
        } else {
          // 重置评论列表 - 这是第一页加载或排序切换
          comments.value = newComments
          currentPage.value = 0
          cursor.value = null
          console.log('重置评论列表，当前评论数:', newComments.length)
        }
        
        // 更新分页状态
        hasMore.value = pageData.hasMore || false
        
        // 更新游标：使用后端返回的nextCursor，如果没有则使用最后一条评论的ID
        if (hasMore.value && newComments.length > 0) {
          cursor.value = pageData.nextCursor || newComments[newComments.length - 1].id
        }
        
        // 更新页码（用于日志和调试）
        if (append) {
          currentPage.value += 1
        }
        
        // 更新总评论数
        totalCommentsCount.value = pageData.totalCount || 0
        
        // 通知父组件更新评论数
        emit('comment-count-update', totalCommentsCount.value)
        
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
        
        // 更新总评论数
        totalCommentsCount.value += 1
        
        // 通知父组件更新评论数
        emit('comment-count-update', totalCommentsCount.value)
        
      } catch (error) {
        console.error('发表评论失败:', error)
      } finally {
        submitting.value = false
      }
    }

    // 处理排序变化
    const handleSortChange = async (newSortBy) => {
      console.log('点击排序按钮:', newSortBy, '当前排序:', sortBy.value, '当前顺序:', sortOrder.value)
      
      // 防止在加载过程中重复点击
      if (loading.value) return
      
      // 如果点击的是当前时间排序，则切换排序顺序
      if (sortBy.value === newSortBy && newSortBy === 'created_at') {
        sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
        console.log('切换时间排序顺序:', sortOrder.value)
      } else if (sortBy.value !== newSortBy) {
        // 切换到不同的排序字段
        sortBy.value = newSortBy
        sortOrder.value = newSortBy === 'created_at' ? 'desc' : 'desc' // 默认排序
        console.log('切换排序方式:', sortBy.value, '排序顺序:', sortOrder.value)
      } else {
        // 点击相同的非时间排序，不做处理
        return
      }
      
      // 重置所有分页相关状态
      cursor.value = null
      currentPage.value = 0
      hasMore.value = true
      
      // 滚动到顶部
      if (commentsContainer.value) {
        commentsContainer.value.scrollTop = 0
        console.log('已重置滚动位置到顶部')
      }
      
      // 重新加载第一页
      await loadComments(false)
      
      // 重新设置无限滚动观察器
      await resetInfiniteScroll()
      
      console.log('排序切换完成，无限滚动已重新设置')
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
      
      // 注意：回复��影响主评论总数，所以不需要更新totalCommentsCount
      // 但需要通知父组件当前的总数（主评论数）
      emit('comment-count-update', totalCommentsCount.value)
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
          totalCommentsCount.value = Math.max(0, totalCommentsCount.value - 1)
          emit('comment-count-update', totalCommentsCount.value)
        } else {
          // 在回复中查找并删除
          for (const mainComment of comments.value) {
            if (mainComment.replies) {
              const replyIndex = mainComment.replies.findIndex(r => r.id === commentId)
              if (replyIndex > -1) {
                mainComment.replies.splice(replyIndex, 1)
                mainComment.repliesCount = Math.max(0, (mainComment.repliesCount || 0) - 1)
                
                // 注意：回复不影响主评论总数，所以不需要更新totalCommentsCount
                // 但需要通知父组件更新显示的回复数量
                emit('comment-count-update', totalCommentsCount.value)
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

    // 无限滚动观察器引用
    let currentObserver = null
    
    // 无缝滚动加载
    const setupInfiniteScroll = () => {
      // 先清理现有的观察器
      if (currentObserver) {
        currentObserver.disconnect()
        currentObserver = null
      }
      
      if (!loadTrigger.value) {
        console.log('loadTrigger元素未找到，延迟设置观察器')
        return null
      }
      
      currentObserver = new IntersectionObserver(
        (entries) => {
          const entry = entries[0]
          if (entry.isIntersecting && hasMore.value && !loading.value) {
            console.log('触发无限滚动加载', { hasMore: hasMore.value, loading: loading.value }) // 调试日志
            loadComments(true)
          }
        },
        { threshold: 0.1 }
      )
      
      currentObserver.observe(loadTrigger.value)
      console.log('无限滚动观察器已设置')
      
      return () => {
        if (currentObserver) {
          currentObserver.disconnect()
          currentObserver = null
        }
      }
    }
    
    // 重新设置无限滚动观察器
    const resetInfiniteScroll = async () => {
      await nextTick() // 等待DOM更新
      setupInfiniteScroll()
    }

    // 组件挂载
    onMounted(async () => {
      await loadComments()
      await nextTick()
      
      setupInfiniteScroll()
    })
    
    // 组件卸载时清理观察器
    onUnmounted(() => {
      if (currentObserver) {
        currentObserver.disconnect()
        currentObserver = null
        console.log('组件卸载，已清理无限滚动观察器')
      }
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
    display: flex;
    align-items: center;
    gap: 4px;

    &:hover:not(:disabled) {
      border-color: var(--primary-color);
      color: var(--primary-color);
    }

    &.active {
      background: var(--primary-color);
      border-color: var(--primary-color);
      color: white;
    }

    &:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .sort-icon {
      transition: transform 0.3s ease;
      flex-shrink: 0;
      
      &.rotate-180 {
        transform: rotate(180deg);
      }
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

// 评论列表过渡动画
.comment-transition-wrapper {
  position: relative;
}

.comment-list-enter-active,
.comment-list-leave-active {
  transition: all 0.4s ease;
}

.comment-list-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.comment-list-leave-to {
  opacity: 0;
  transform: translateX(-30px);
}

.comment-list-move {
  transition: transform 0.4s ease;
}
</style>