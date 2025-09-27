import request from '@/utils/request'

/**
 * 评论相关API
 */
export const commentApi = {
  /**
   * 获取评论列表
   * @param {Object} params - 查询参数
   * @param {string} params.cardId - 角色卡ID
   * @param {string} params.sortBy - 排序字段 (created_at, likes_count)
   * @param {string} params.sortOrder - 排序方向 (asc, desc)
   * @param {number} params.page - 页码
   * @param {number} params.size - 页大小
   * @param {number} params.cursor - 游标ID
   */
  getComments(params) {
    return request.get('/api/comments', { params })
  },

  /**
   * 创建评论
   * @param {Object} request - 创建评论请求
   * @param {string} request.cardId - 角色卡ID
   * @param {string} request.content - 评论内容
   * @param {number} request.parentCommentId - 父评论ID（可选）
   */
  createComment(requestData) {
    return request.post('/api/comments', requestData)
  },

  /**
   * 获取评论的回复列表
   * @param {number} commentId - 评论ID
   */
  getCommentReplies(commentId) {
    return request.get(`/api/comments/${commentId}/replies`)
  },

  /**
   * 点赞/取消点赞评论
   * @param {number} commentId - 评论ID
   */
  toggleCommentLike(commentId) {
    return request.post(`/api/comments/${commentId}/like`)
  },

  /**
   * 置顶/取消置顶评论
   * @param {number} commentId - 评论ID
   */
  toggleCommentPin(commentId) {
    return request.post(`/api/comments/${commentId}/pin`)
  },

  /**
   * 删除评论
   * @param {number} commentId - 评论ID
   */
  deleteComment(commentId) {
    return request.delete(`/api/comments/${commentId}`)
  }
}