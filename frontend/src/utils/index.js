/**
 * 存储工具类
 */
export const storage = {
  // 获取localStorage
  get(key) {
    try {
      const item = localStorage.getItem(key)
      return item ? JSON.parse(item) : null
    } catch (error) {
      console.error('获取localStorage失败:', error)
      return null
    }
  },

  // 设置localStorage
  set(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value))
      return true
    } catch (error) {
      console.error('设置localStorage失败:', error)
      return false
    }
  },

  // 删除localStorage
  remove(key) {
    try {
      localStorage.removeItem(key)
      return true
    } catch (error) {
      console.error('删除localStorage失败:', error)
      return false
    }
  },

  // 清空localStorage
  clear() {
    try {
      localStorage.clear()
      return true
    } catch (error) {
      console.error('清空localStorage失败:', error)
      return false
    }
  }
}

/**
 * 表单验证工具
 */
export const validation = {
  // 验证邮箱
  isEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    return emailRegex.test(email)
  },

  // 验证用户名（3-50个字符，字母数字下划线）
  isUsername(username) {
    const usernameRegex = /^[a-zA-Z0-9_]{3,50}$/
    return usernameRegex.test(username)
  },

  // 验证密码（至少6位）
  isPassword(password) {
    return password && password.length >= 6
  },

  // 验证必填项
  isRequired(value) {
    return value !== null && value !== undefined && value !== ''
  },

  // 验证URL
  isUrl(url) {
    try {
      new URL(url)
      return true
    } catch {
      return false
    }
  }
}

/**
 * 格式化工具
 */
export const format = {
  // 格式化日期
  date(date, format = 'YYYY-MM-DD HH:mm:ss') {
    if (!date) return ''
    
    const d = new Date(date)
    const year = d.getFullYear()
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const hour = String(d.getHours()).padStart(2, '0')
    const minute = String(d.getMinutes()).padStart(2, '0')
    const second = String(d.getSeconds()).padStart(2, '0')
    
    return format
      .replace('YYYY', year)
      .replace('MM', month)
      .replace('DD', day)
      .replace('HH', hour)
      .replace('mm', minute)
      .replace('ss', second)
  },

  // 格式化文件大小
  fileSize(bytes) {
    if (!bytes) return '0 B'
    
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  }
}

/**
 * DOM工具
 */
export const dom = {
  // 添加类名
  addClass(element, className) {
    if (element && className) {
      element.classList.add(className)
    }
  },

  // 移除类名
  removeClass(element, className) {
    if (element && className) {
      element.classList.remove(className)
    }
  },

  // 切换类名
  toggleClass(element, className) {
    if (element && className) {
      element.classList.toggle(className)
    }
  }
}

/**
 * 防抖函数
 */
export function debounce(func, wait) {
  let timeout
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout)
      func(...args)
    }
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)
  }
}

/**
 * 节流函数
 */
export function throttle(func, limit) {
  let inThrottle
  return function(...args) {
    if (!inThrottle) {
      func.apply(this, args)
      inThrottle = true
      setTimeout(() => inThrottle = false, limit)
    }
  }
}

/**
 * Token工具类
 */
export const tokenUtils = {
  // 检查token是否存在
  hasToken() {
    return !!storage.get('token')
  },

  // 检查token是否过期（简单检查，实际应该解析JWT）
  isTokenExpired() {
    const token = storage.get('token')
    if (!token) return true
    
    try {
      // 检查JWT格式
      const parts = token.split('.')
      if (parts.length !== 3) {
        console.warn('Token格式不正确')
        return true
      }
      
      // 解析payload
      const payload = JSON.parse(atob(parts[1]))
      if (!payload.exp) {
        console.warn('Token没有过期时间')
        return true
      }
      
      const exp = payload.exp * 1000 // 转换为毫秒
      const now = Date.now()
      const isExpired = now >= exp
      
      if (isExpired) {
        console.log('Token已过期')
      }
      
      return isExpired
    } catch (error) {
      console.error('解析token失败:', error)
      return true // 解析失败认为过期
    }
  },

  // 清除认证信息
  clearAuth() {
    storage.remove('token')
    storage.remove('user')
  },

  // 获取token
  getToken() {
    return storage.get('token')
  }
}
