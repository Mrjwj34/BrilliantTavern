/**
 * 全局通知系统
 * 提供统一的错误、成功、警告等消息提示
 */
import { ref, nextTick } from 'vue'

// 通知队列
const notifications = ref([])
let notificationId = 0

// 通知类型
export const NotificationType = {
  SUCCESS: 'success',
  ERROR: 'error',
  WARNING: 'warning',
  INFO: 'info'
}

// 创建通知
function createNotification(type, message, duration = 4000) {
  const id = ++notificationId
  
  const notification = {
    id,
    type,
    message,
    show: false,
    duration
  }
  
  notifications.value.push(notification)
  
  // 下一个tick显示动画
  nextTick(() => {
    notification.show = true
  })
  
  // 自动消失
  setTimeout(() => {
    hideNotification(id)
  }, duration)
  
  return id
}

// 隐藏通知
function hideNotification(id) {
  const notification = notifications.value.find(n => n.id === id)
  if (notification) {
    notification.show = false
    
    // 动画完成后移除
    setTimeout(() => {
      const index = notifications.value.findIndex(n => n.id === id)
      if (index > -1) {
        notifications.value.splice(index, 1)
      }
    }, 300) // 等待CSS动画完成
  }
}

// 清除所有通知
function clearAllNotifications() {
  notifications.value.forEach(notification => {
    notification.show = false
  })
  
  setTimeout(() => {
    notifications.value.splice(0)
  }, 300)
}

// 导出的API
export const notification = {
  // 成功提示
  success(message, duration) {
    return createNotification(NotificationType.SUCCESS, message, duration)
  },
  
  // 错误提示
  error(message, duration) {
    return createNotification(NotificationType.ERROR, message, duration)
  },
  
  // 警告提示
  warning(message, duration) {
    return createNotification(NotificationType.WARNING, message, duration)
  },
  
  // 信息提示
  info(message, duration) {
    return createNotification(NotificationType.INFO, message, duration)
  },
  
  // 隐藏指定通知
  hide: hideNotification,
  
  // 清除所有通知
  clear: clearAllNotifications
}

// 导出通知列表（供组件使用）
export { notifications }
