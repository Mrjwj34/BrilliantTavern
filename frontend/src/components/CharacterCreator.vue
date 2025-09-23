<template>
  <div class="character-creator">
    <!-- 第一层：标题和创建按钮 -->
    <div class="creator-header">
      <h1 class="creator-title">创建角色</h1>
      <button 
        type="button" 
        @click="handleSubmit" 
        :disabled="!isFormValid || loading"
        class="create-character-btn"
      >
        <span v-if="loading" class="loading-spinner"></span>
        {{ loading ? '创建中...' : '创建角色' }}
      </button>
    </div>

    <!-- 第二层：描述 -->
    <div class="creator-subtitle-section">
      <p class="creator-subtitle">设计独特的AI角色，让想象力成为现实</p>
    </div>

    <!-- 回顶按钮 -->
    <button 
      v-show="showBackToTop" 
      @click="scrollToTop" 
      class="back-to-top-btn"
      title="回到顶部"
    >
      <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M12 6L12 18M12 6L7 11M12 6L17 11" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </button>

    <!-- 错误提示弹窗 -->
    <div v-if="errorMessage" class="error-toast" :class="{ 'show': showErrorToast }">
      <div class="error-badge" aria-hidden="true">
        <svg class="badge-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 2L2 20h20L12 2z" stroke="currentColor" stroke-width="2" fill="none"/>
          <path d="M12 8v6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          <circle cx="12" cy="17" r="1.25" fill="currentColor"/>
        </svg>
        <span class="badge-text">错误</span>
      </div>
      <div class="error-content">
        <span class="error-text">{{ errorMessage }}</span>
      </div>
    </div>

    <!-- 第三层：表单区域 -->
    <div class="creator-content" ref="contentRef">
      <form @submit.prevent="handleSubmit" class="character-form">
        <!-- 角色头像 - 移到最前面 -->
        <div class="form-section">
          <div class="section-header">
            <h3 class="section-title">角色头像</h3>
            <p class="section-description">上传或输入角色头像</p>
          </div>
          
          <div class="avatar-section">
            <!-- 头像预览 -->
            <div class="avatar-preview">
              <div class="avatar-container" :class="{ 'has-image': formData.avatarUrl || uploadedImage }">
                <img v-if="uploadedImage || formData.avatarUrl" 
                     :src="uploadedImage || formData.avatarUrl" 
                     alt="角色头像" 
                     class="avatar-image" />
                <div v-else class="avatar-placeholder">
                  <svg class="avatar-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M12 12C14.7614 12 17 9.76142 17 7C17 4.23858 14.7614 2 12 2C9.23858 2 7 4.23858 7 7C7 9.76142 9.23858 12 12 12Z" stroke="currentColor" stroke-width="2"/>
                    <path d="M20.5899 22C20.5899 18.13 16.7399 15 11.9999 15C7.25991 15 3.40991 18.13 3.40991 22" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                  </svg>
                  <span class="avatar-text">头像</span>
                </div>
              </div>
              <!-- 图片来源提示 -->
              <div v-if="uploadedImage || formData.avatarUrl" class="avatar-source">
                {{ uploadedImage ? '已上传文件' : 'URL链接' }}
              </div>
            </div>
            
            <!-- 上传区域 -->
            <div class="upload-section">
              <div class="upload-area" 
                   @dragover.prevent="handleDragOver"
                   @dragleave="handleDragLeave"
                   @drop.prevent="handleDrop"
                   :class="{ 'drag-over': isDragOver, 'disabled': formData.avatarUrl }"
                    @click="triggerFileInput"
           role="button"
           tabindex="0"
           @keydown.enter.prevent="triggerFileInput"
           @keydown.space.prevent="triggerFileInput">
                <input ref="fileInput" 
        type="file" 
        accept="image/*" 
                        @change="handleFileSelect"
        id="avatarFileInput"
        aria-label="选择头像图片文件"
        style="position:absolute; width:1px; height:1px; opacity:0; pointer-events:auto;" />
                <label class="upload-content" :for="!formData.avatarUrl ? 'avatarFileInput' : null" @click.stop>
                  <svg class="upload-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M21 15V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V15" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    <path d="M17 8L12 3L7 8" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    <path d="M12 3V15" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                  <p class="upload-text">
                    {{ formData.avatarUrl ? '已有链接，文件上传已禁用' : '拖拽图片到此处或点击选择' }}
                  </p>
                  <p v-if="!formData.avatarUrl" class="upload-hint">支持 JPG、PNG、GIF 格式，建议尺寸 256x256 像素</p>
        </label>
              </div>
              
              <!-- URL输入 -->
              <div class="url-input-group">
                <label class="form-label">
                  或输入图片链接
                  <span v-if="uploadedImage" class="input-disabled-hint">（已有上传文件，链接输入已禁用）</span>
                </label>
                <input
                  v-model="formData.avatarUrl"
                  type="url"
                  class="form-input"
                  :class="{ 'error': errors.avatarUrl, 'disabled': uploadedImage }"
                  :disabled="uploadedImage"
                  placeholder="https://example.com/avatar.jpg"
                  maxlength="500"
                  @blur="validateField('avatarUrl')"
                  @input="handleUrlInput"
                />
                <div v-if="errors.avatarUrl" class="error-text">
                  {{ errors.avatarUrl }}
                </div>
              </div>
              
              <!-- 清除按钮 -->
              <div v-if="formData.avatarUrl || uploadedImage" class="avatar-actions">
                <button type="button" @click="clearAvatar" class="btn-clear">
                  清除头像
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 基础信息 -->
        <div class="form-section">
          <div class="section-header">
            <h3 class="section-title">基础信息</h3>
            <p class="section-description">设置角色的基本属性</p>
          </div>
          
          <div class="form-grid">
            <div class="form-group">
              <label for="name" class="form-label">
                角色名称 <span class="required">*</span>
              </label>
              <input
                id="name"
                v-model="formData.name"
                type="text"
                class="form-input"
                :class="{ 'error': errors.name }"
                placeholder="输入角色名称，如：苏格拉底"
                maxlength="100"
                @blur="validateField('name')"
                @input="clearError('name')"
              />
              <div v-if="errors.name" class="error-text">
                {{ errors.name }}
              </div>
            </div>

            <div class="form-group">
              <label for="shortDescription" class="form-label">
                简短描述 <span class="required">*</span>
              </label>
              <input
                id="shortDescription"
                v-model="formData.shortDescription"
                type="text"
                class="form-input"
                :class="{ 'error': errors.shortDescription }"
                placeholder="一句话描述角色，如：古希腊哲学家，智慧的化身"
                maxlength="500"
                @blur="validateField('shortDescription')"
                @input="clearError('shortDescription')"
              />
              <div v-if="errors.shortDescription" class="error-text">
                {{ errors.shortDescription }}
              </div>
            </div>
          </div>

          <div class="form-group">
            <label for="greetingMessage" class="form-label">问候语</label>
            <textarea
              id="greetingMessage"
              v-model="formData.greetingMessage"
              class="form-textarea"
              :class="{ 'error': errors.greetingMessage }"
              placeholder="角色的第一句话，如：你好，我是苏格拉底。让我们一起探讨智慧吧！"
              rows="3"
              maxlength="1000"
              @blur="validateField('greetingMessage')"
              @input="clearError('greetingMessage')"
            ></textarea>
            <div v-if="errors.greetingMessage" class="error-text">
              {{ errors.greetingMessage }}
            </div>
          </div>

          <div class="form-grid">
            <div class="form-group">
              <label class="form-label">公开设置</label>
              <div class="checkbox-group">
                <label class="checkbox-label">
                  <input
                    v-model="formData.isPublic"
                    type="checkbox"
                    class="checkbox-input"
                  />
                  <span class="checkbox-text">设为公开角色（其他用户可以看到并使用）</span>
                </label>
              </div>
            </div>

            <div class="form-group">
              <label for="ttsVoiceId" class="form-label">语音音色</label>
              <select
                id="ttsVoiceId"
                v-model="formData.ttsVoiceId"
                class="form-select"
                :class="{ 'error': errors.ttsVoiceId }"
              >
                <option value="">选择语音音色（可选）</option>
                <option v-for="voice in availableVoices" :key="voice.id" :value="voice.id">
                  {{ voice.name }}
                </option>
              </select>
            </div>
          </div>
        </div>

        <!-- 角色设定 -->
        <div class="form-section">
          <div class="section-header">
            <h3 class="section-title">角色设定</h3>
            <p class="section-description">详细描述角色的性格和背景</p>
          </div>

          <div class="form-group">
            <label for="description" class="form-label">
              详细描述 <span class="required">*</span>
            </label>
            <textarea
              id="description"
              v-model="formData.cardData.description"
              class="form-textarea"
              :class="{ 'error': errors.description }"
              placeholder="详细描述角色的背景故事、出生经历、重要事件等..."
              rows="6"
              maxlength="5000"
              @blur="validateField('description')"
              @input="clearError('description')"
            ></textarea>
            <div v-if="errors.description" class="error-text">
              {{ errors.description }}
            </div>
          </div>

          <div class="form-group">
            <label for="personality" class="form-label">
              性格特点 <span class="required">*</span>
            </label>
            <textarea
              id="personality"
              v-model="formData.cardData.personality"
              class="form-textarea"
              :class="{ 'error': errors.personality }"
              placeholder="描述角色的性格特征，如：聪明、好奇、有点固执、喜欢用反问来引导对话..."
              rows="4"
              maxlength="2000"
              @blur="validateField('personality')"
              @input="clearError('personality')"
            ></textarea>
            <div v-if="errors.personality" class="error-text">
              {{ errors.personality }}
            </div>
          </div>

          <div class="form-group">
            <label for="scenario" class="form-label">对话场景</label>
            <textarea
              id="scenario"
              v-model="formData.cardData.scenario"
              class="form-textarea"
              :class="{ 'error': errors.scenario }"
              placeholder="描述对话发生的场景环境，如：你正在雅典的市集上与苏格拉底相遇..."
              rows="4"
              maxlength="3000"
              @blur="validateField('scenario')"
              @input="clearError('scenario')"
            ></textarea>
            <div v-if="errors.scenario" class="error-text">
              {{ errors.scenario }}
            </div>
          </div>
        </div>

        <!-- 对话示例 -->
        <div class="form-section">
          <div class="section-header">
            <h3 class="section-title">对话示例</h3>
            <p class="section-description">提供一些对话样例，帮助AI更好地理解角色</p>
          </div>

          <div class="dialog-examples">
            <div
              v-for="(dialog, index) in formData.cardData.exampleDialogs"
              :key="index"
              class="dialog-item"
            >
              <div class="dialog-header">
                <h4 class="dialog-title">对话示例 {{ index + 1 }}</h4>
                <button
                  type="button"
                  @click="removeDialog(index)"
                  class="remove-dialog-btn"
                  title="删除此示例"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"/>
                    <line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                </button>
              </div>

              <div class="dialog-content">
                <div class="form-group">
                  <label class="form-label">用户说：</label>
                  <textarea
                    v-model="dialog.user"
                    class="form-textarea"
                    placeholder="用户的话语，如：什么是正义？"
                    rows="2"
                    maxlength="1000"
                  ></textarea>
                </div>

                <div class="form-group">
                  <label class="form-label">角色回答：</label>
                  <textarea
                    v-model="dialog.assistant"
                    class="form-textarea"
                    placeholder="角色的回答，如：这是一个很好的问题。那么，在你看来，一个正义的行为具体是什么样的呢？"
                    rows="3"
                    maxlength="1000"
                  ></textarea>
                </div>
              </div>
            </div>

            <button
              type="button"
              @click="addDialog"
              class="add-dialog-btn"
              :disabled="formData.cardData.exampleDialogs.length >= 5"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
              添加对话示例（最多5个）
            </button>
          </div>
        </div>

        <!-- 通用错误显示 -->
        <div v-if="errors.general" class="form-section">
          <div class="error-text general-error">
            {{ errors.general }}
          </div>
        </div>
      </form>
    </div>
  </div>
</template>

<script>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { characterCardAPI, voiceAPI } from '@/api'
import { validation } from '@/utils'

export default {
  name: 'CharacterCreator',
  emits: ['created'],
  setup(props, { emit }) {
    const loading = ref(false)
    const errors = ref({})
    const availableVoices = ref([]) // 语音列表
    const isDragOver = ref(false) // 拖拽状态
    const uploadedImage = ref(null) // 上传的图片base64
    const fileInput = ref(null) // 文件输入引用
    const contentRef = ref(null) // 内容区域引用
  const scrollContainer = ref(null) // 实际滚动容器（如 .workspace-content）
    
    // 头部滚动状态（保留用于回顶按钮）
    const showBackToTop = ref(false)
    
    // 错误提示
    const errorMessage = ref('')
    const showErrorToast = ref(false)

    const formData = reactive({
      name: '',
      shortDescription: '',
      greetingMessage: '',
      isPublic: false,
      ttsVoiceId: '',
      avatarUrl: '', // 新增头像字段
      cardData: {
        description: '',
        personality: '',
        scenario: '',
        exampleDialogs: [] // 默认为空数组，用户可选择添加
        // 删除 customPrompts
      }
    })

    const isFormValid = computed(() => {
      return formData.name.trim() && 
             formData.shortDescription.trim() &&
             formData.cardData.description.trim() &&
             formData.cardData.personality.trim() &&
             Object.keys(errors.value).length === 0
    })

    // 验证字段
    const validateField = (field) => {
      const value = formData[field] || formData.cardData[field]
      
      switch (field) {
        case 'name':
          if (!validation.isRequired(value)) {
            errors.value.name = '请输入角色名称'
          } else if (value.trim().length < 1) {
            errors.value.name = '角色名称不能为空'
          } else {
            delete errors.value.name
          }
          break
          
        case 'shortDescription':
          if (!validation.isRequired(value)) {
            errors.value.shortDescription = '请输入简短描述'
          } else if (value.length > 500) {
            errors.value.shortDescription = '简短描述不能超过500字符'
          } else {
            delete errors.value.shortDescription
          }
          break
          
        case 'greetingMessage':
          if (value && value.length > 1000) {
            errors.value.greetingMessage = '问候语不能超过1000字符'
          } else {
            delete errors.value.greetingMessage
          }
          break
          
        case 'description':
          if (!validation.isRequired(value)) {
            errors.value.description = '请输入角色详细描述'
          } else if (value.length > 5000) {
            errors.value.description = '详细描述不能超过5000字符'
          } else {
            delete errors.value.description
          }
          break
          
        case 'personality':
          if (!validation.isRequired(value)) {
            errors.value.personality = '请输入角色性格特点'
          } else if (value.length > 2000) {
            errors.value.personality = '性格特点不能超过2000字符'
          } else {
            delete errors.value.personality
          }
          break
          
        case 'scenario':
          if (value && value.length > 3000) {
            errors.value.scenario = '对话场景不能超过3000字符'
          } else {
            delete errors.value.scenario
          }
          break

        case 'avatarUrl':
          if (value && !validation.isUrl(value)) {
            errors.value.avatarUrl = '请输入有效的图片链接'
          } else if (value && value.length > 500) {
            errors.value.avatarUrl = '头像链接不能超过500字符'
          } else {
            delete errors.value.avatarUrl
          }
          break
      }
    }

    // 清除错误
    const clearError = (field) => {
      if (errors.value[field]) {
        delete errors.value[field]
      }
    }

    // 添加对话示例
    const addDialog = () => {
      if (formData.cardData.exampleDialogs.length < 5) {
        formData.cardData.exampleDialogs.push({ user: '', assistant: '' })
      }
    }

    // 删除对话示例
    const removeDialog = (index) => {
      formData.cardData.exampleDialogs.splice(index, 1)
    }

    // 头像上传相关方法
    const handleFile = (file) => {
      // 如果有URL链接，先清除
      if (formData.avatarUrl) {
        showError('请先清除图片链接再上传文件，或直接使用链接方式')
        return
      }

      // 验证文件类型
      if (!file.type.startsWith('image/')) {
        showError('请选择图片文件')
        return
      }

      // 验证文件大小 (5MB)
      if (file.size > 5 * 1024 * 1024) {
        showError('图片大小不能超过5MB')
        return
      }

      // 读取文件并转换为base64
      const reader = new FileReader()
      reader.onload = (e) => {
        uploadedImage.value = e.target.result
        clearError('avatarUrl')
      }
      reader.onerror = () => {
        showError('图片读取失败')
      }
      reader.readAsDataURL(file)
    }

    const handleUrlInput = () => {
      clearError('avatarUrl')
      // 如果输入URL，清除上传的图片
      if (formData.avatarUrl && uploadedImage.value) {
        uploadedImage.value = null
        if (fileInput.value) {
          fileInput.value.value = ''
        }
      }
    }

  const triggerFileInput = (event) => {
      console.log('triggerFileInput called', event, fileInput.value)
      if (!formData.avatarUrl) {
        if (fileInput.value) {
          console.log('Clicking file input element')
          fileInput.value.click()
        } else {
          console.error('File input ref is null')
        }
      } else {
        showError('请先清除图片链接再上传文件')
      }
    }

    const handleDragOver = (e) => {
      if (!formData.avatarUrl) {
        e.preventDefault()
        isDragOver.value = true
      }
    }

    const handleDragLeave = () => {
      isDragOver.value = false
    }

    const handleDrop = (e) => {
      if (!formData.avatarUrl) {
        e.preventDefault()
        isDragOver.value = false
        
        const files = e.dataTransfer.files
        if (files.length > 0) {
          handleFile(files[0])
        }
      }
    }

    const handleFileSelect = (e) => {
      const files = e.target.files
      if (files.length > 0) {
        handleFile(files[0])
      }
    }

    const clearAvatar = () => {
      uploadedImage.value = null
      formData.avatarUrl = ''
      if (fileInput.value) {
        fileInput.value.value = ''
      }
      clearError('avatarUrl')
    }

    // 错误提示相关
    const showError = (message) => {
      errorMessage.value = message
      showErrorToast.value = true
      
      // 3秒后自动消失
      setTimeout(() => {
        showErrorToast.value = false
        setTimeout(() => {
          errorMessage.value = ''
        }, 300) // 等待动画完成
      }, 3000)
    }

    // 滚动相关
    const getScrollTop = (container) => {
      if (!container) return 0
      if (container === window) {
        return window.pageYOffset || document.documentElement.scrollTop || 0
      }
      return container.scrollTop || 0
    }

    const handleScroll = () => {
      // 优先使用实际滚动容器（如 .workspace-content），否则回退到 window 或本地内容容器
      const primary = scrollContainer.value || window
      const primaryTop = getScrollTop(primary)
      const contentTop = getScrollTop(contentRef.value)
      const windowTop = getScrollTop(window)
      const scrollTop = Math.max(primaryTop, contentTop, windowTop)
      showBackToTop.value = scrollTop > 300
    }

    const scrollToTop = () => {
      const target = scrollContainer.value || window
      if (target === window) {
        window.scrollTo({ top: 0, behavior: 'smooth' })
      } else {
        target.scrollTo({ top: 0, behavior: 'smooth' })
      }
    }

    // 获取语音列表
    const loadVoices = async () => {
      try {
        const response = await voiceAPI.getVoiceList()
        if (response && response.code === 200) {
          availableVoices.value = response.data.map(voice => ({
            id: voice.id,
            name: `${voice.name}（${voice.description}）`
          }))
        }
      } catch (error) {
        console.error('获取语音列表失败:', error)
        // 使用占位符数据作为后备
        availableVoices.value = [
          { id: 'voice_001', name: '小雨（温柔甜美的女声）' },
          { id: 'voice_002', name: '小明（阳光活泼的男声）' },
          { id: 'voice_003', name: '小慧（知性优雅的女声）' },
          { id: 'voice_004', name: '小峰（成熟稳重的男声）' },
          { id: 'voice_005', name: '小萌（可爱活泼的女声）' }
        ]
      }
    }

    // 处理提交
    const handleSubmit = async () => {
      // 清除之前的通用错误
      if (errors.value.general) {
        delete errors.value.general
      }

      // 验证必填字段
      validateField('name')
      validateField('shortDescription')
      validateField('description')
      validateField('personality')

      if (!isFormValid.value) {
        return
      }

      loading.value = true

      try {
        // 过滤空的对话示例
        const filteredDialogs = formData.cardData.exampleDialogs.filter(
          dialog => dialog.user.trim() || dialog.assistant.trim()
        )

        // 构建提交数据
        const submitData = {
          ...formData,
          // 优先使用上传的图片，否则使用URL
          avatarUrl: uploadedImage.value || formData.avatarUrl,
          cardData: {
            ...formData.cardData,
            exampleDialogs: filteredDialogs
          }
        }

        const response = await characterCardAPI.create(submitData)

        if (response && response.code === 200) {
          // 创建成功
          emit('created', response.data)
          resetForm()
          // TODO: 显示成功提示
          console.log('角色创建成功')
        } else {
          showError(response.message || '创建失败，请重试')
        }
      } catch (error) {
        console.error('创建角色失败:', error)
        if (error.response) {
          // HTTP错误响应
          const status = error.response.status
          const message = error.response.data?.message
          
          if (status >= 500) {
            showError(message || '服务器内部错误，请稍后重试')
          } else if (status === 401) {
            showError('登录已过期，请重新登录')
          } else if (status === 403) {
            showError('权限不足，无法执行此操作')
          } else if (status === 400) {
            showError(message || '请求参数错误')
          } else {
            showError(message || '请求失败，请重试')
          }
        } else if (error.request) {
          // 网络错误
          showError('网络连接失败，请检查网络后重试')
        } else {
          // 其他错误
          showError(error.message || '发生未知错误，请重试')
        }
      } finally {
        loading.value = false
      }
    }

    // 重置表单
    const resetForm = () => {
      Object.assign(formData, {
        name: '',
        shortDescription: '',
        greetingMessage: '',
        isPublic: false,
        ttsVoiceId: '',
        avatarUrl: '',
        cardData: {
          description: '',
          personality: '',
          scenario: '',
          exampleDialogs: [] // 重置为空数组
        }
      })
      errors.value = {}
      uploadedImage.value = null
      if (fileInput.value) {
        fileInput.value.value = ''
      }
    }

    // 组件挂载时加载语音列表
  onMounted(() => {
      loadVoices()
      // 识别外层滚动容器（Dashboard 中为 .workspace-content）
      const workspace = document.querySelector('.workspace-content')
      scrollContainer.value = workspace || window

      // 添加滚动事件监听（优先外层容器，其次窗口与本地内容容器）
      if (scrollContainer.value && scrollContainer.value !== window) {
        scrollContainer.value.addEventListener('scroll', handleScroll)
      }
      window.addEventListener('scroll', handleScroll)
      if (contentRef.value) {
        contentRef.value.addEventListener('scroll', handleScroll)
      }
  // 初始计算一次
  handleScroll()
    })

  onUnmounted(() => {
      // 清除滚动事件监听
      if (scrollContainer.value && scrollContainer.value !== window) {
        scrollContainer.value.removeEventListener('scroll', handleScroll)
      }
      window.removeEventListener('scroll', handleScroll)
      if (contentRef.value) {
        contentRef.value.removeEventListener('scroll', handleScroll)
      }
    })

    return {
      formData,
      errors,
      loading,
      isFormValid,
      availableVoices,
      isDragOver,
      uploadedImage,
      fileInput,
      contentRef,
  scrollContainer,
      showBackToTop,
      errorMessage,
      showErrorToast,
      validateField,
      clearError,
      addDialog,
      removeDialog,
      handleSubmit,
      resetForm,
      triggerFileInput,
      handleDragOver,
      handleDragLeave,
      handleDrop,
      handleFileSelect,
      handleUrlInput,
  clearAvatar,
  scrollToTop
    }
  }
}
</script>

<style lang="scss" scoped>
.character-creator {
  padding: 2rem;
  max-width: 1200px; /* 放宽整体宽度以更好利用屏幕空间 */
  margin: 0 auto;
  min-height: calc(100vh - 120px);
  position: relative;
}

.creator-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0;
  margin: 0 auto 1rem auto; /* 居中并限制宽度，仅标题/按钮区域保持较窄 */
  max-width: 800px; /* 仅限制标题层的宽度 */
  width: 100%;
}

.creator-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--primary-color);
  margin: 0;
}

.create-character-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.625rem 1.25rem; /* 调整小一些 */
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 0.5rem;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
    background: var(--primary-dark);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
    transform: none;
  }

  .loading-spinner {
    width: 0.875rem;
    height: 0.875rem;
    border: 2px solid rgba(255, 255, 255, 0.3);
    border-top: 2px solid white;
    border-radius: 50%;
    animation: spin 1s linear infinite;
  }
}

.creator-subtitle-section {
  margin-bottom: 2rem;
}

.creator-subtitle {
  color: #666;
  font-size: 0.875rem;
  margin: 0;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid transparent;
  border-top: 2px solid currentColor;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.creator-content {
  flex: 1;
  overflow-y: auto;
}

.character-form {
  max-width: 1100px; /* 增加最大宽度以更好利用屏幕空间 */
  margin: 0 auto;
}

.form-section {
  background: var(--background-secondary);
  border: none; /* 移除边框 */
  border-radius: 12px; /* 更大的圆角 */
  padding: $spacing-lg;
  margin-bottom: $spacing-lg;

  &:last-of-type {
    margin-bottom: $spacing-xl;
  }
}

.section-header {
  margin-bottom: $spacing-lg;
}

.section-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 $spacing-xs 0;
}

.section-description {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.5;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-lg;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: $spacing;
  }
}

.form-group {
  margin-bottom: $spacing;

  &:last-child {
    margin-bottom: 0;
  }
}

.form-label {
  display: block;
  margin-bottom: $spacing-xs;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);

  .required {
    color: var(--error-color);
    margin-left: 2px;
  }
}

.form-input,
.form-textarea,
.form-select {
  width: 100%;
  padding: $spacing-sm;
  border: none; /* 移除边框 */
  border-radius: 8px; /* 圆角 */
  font-size: 14px;
  background: var(--background-tertiary); /* 使用更深的背景色 */
  color: var(--text-primary);
  transition: all $transition-fast ease;

  &:focus {
    outline: none;
    background: var(--background-primary); /* 聚焦时变亮 */
    box-shadow: 0 0 0 2px var(--primary-color); /* 使用阴影替代边框 */
  }

  &.error {
    background: rgba(239, 68, 68, 0.1);
    box-shadow: 0 0 0 2px var(--error-color);
  }

  &::placeholder {
    color: var(--text-placeholder);
  }
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
  font-family: inherit;
  line-height: 1.5;
}

.form-select {
  cursor: pointer;
}

.checkbox-group {
  .checkbox-label {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    cursor: pointer;
    
    .checkbox-input {
      width: auto;
      margin: 0;
      cursor: pointer;
    }
    
    .checkbox-text {
      font-size: 14px;
      color: var(--text-secondary);
    }
  }
}

.error-text {
  font-size: 12px;
  color: var(--error-color);
  margin-top: $spacing-xs;

  &.general-error {
    background: rgba(239, 68, 68, 0.1);
    padding: $spacing-sm;
    border-radius: 8px;
    border-left: 3px solid var(--error-color);
    font-size: 14px;
    margin: 0;
    display: flex;
    align-items: center;
    gap: $spacing-xs;

    &::before {
      content: "⚠";
      font-size: 16px;
    }
  }
}

.form-help-text {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: $spacing-xs;
  line-height: 1.4;
}

.dialog-examples {
  .dialog-item {
    background: var(--background-primary);
    border: none; /* 移除边框 */
    border-radius: 8px;
    padding: $spacing;
    margin-bottom: $spacing;

    &:last-of-type {
      margin-bottom: $spacing;
    }
  }

  .dialog-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: $spacing;
  }

  .dialog-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
    margin: 0;
  }

  .remove-dialog-btn {
    background: transparent;
    border: none;
    color: var(--text-tertiary);
    cursor: pointer;
    padding: 4px;
    border-radius: $border-radius-sm;
    transition: all $transition-fast ease;

    &:hover {
      color: var(--error-color);
      background: rgba(239, 68, 68, 0.1);
    }
  }

  .dialog-content {
    .form-group {
      margin-bottom: $spacing-sm;

      &:last-child {
        margin-bottom: 0;
      }
    }
  }
}

.add-dialog-btn {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  width: 100%;
  padding: $spacing-sm;
  background: var(--background-tertiary);
  border: none; /* 移除虚线边框 */
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-fast ease;

  &:hover:not(:disabled) {
    background: var(--background-primary);
    color: var(--primary-color);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.form-actions {
  display: flex;
  gap: $spacing;
  justify-content: center; /* 居中对齐 */
  padding: $spacing-lg 0 0;
  border-top: none; /* 移除边框 */

  @media (max-width: 768px) {
    flex-direction: column-reverse;
  }
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  border: none; /* 移除边框 */
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-fast ease;
  text-decoration: none;
  outline: none;
  min-width: 120px; /* 增加最小宽度 */

  &:disabled {
    cursor: not-allowed;
    opacity: 0.5;
  }

  &.btn-primary {
    background: var(--primary-color);
    color: white;

    &:hover:not(:disabled) {
      background: var(--primary-dark);
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(217, 119, 6, 0.3);
    }
  }

  &.btn-secondary {
    background: var(--background-tertiary);
    color: var(--text-primary);

    &:hover:not(:disabled) {
      background: var(--background-primary);
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    }
  }
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top: 2px solid white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

// 响应式设计
@media (max-width: 768px) {
  .creator-header {
    padding: $spacing;
  }

  .page-title {
    font-size: 1.5rem;
  }

  .creator-content {
    padding: $spacing;
  }

  .form-section {
    padding: $spacing;
    margin-bottom: $spacing;
  }

  .section-title {
    font-size: 1.125rem;
  }

  .form-actions {
    gap: $spacing-sm;
  }

  .btn {
    padding: $spacing-sm $spacing;
    min-width: auto;
  }
}

// 回顶按钮样式
.back-to-top-btn {
  position: fixed;
  bottom: $spacing-lg;
  right: $spacing-lg;
  z-index: 999;
  width: 48px;
  height: 48px;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.2);
    background: var(--primary-dark);
  }

  svg {
    width: 24px;
    height: 24px;
  }
}

// 错误提示弹窗样式
.error-toast {
  position: fixed;
  bottom: 20px;
  right: 20px;
  transform: translateX(100px);
  z-index: 9999;
  background: #fff; /* 白底 */
  color: var(--text-primary);
  padding: $spacing-lg $spacing-xl; /* 整体更大一些 */
  border-radius: 12px;
  border: 1px solid var(--border-color);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  opacity: 0;
  transition: all 0.3s ease;
  max-width: 480px; /* 更大的最大宽度 */
  word-wrap: break-word;
  text-align: left; /* 整体左对齐 */

  &.show {
    opacity: 1;
    transform: translateX(0);
  }
}

.error-badge {
  /* 放在卡片内部的左上角（正常文档流），不再凸出 */
  position: relative;
  top: auto;
  left: auto;
  background: transparent;
  color: var(--warning-color);
  border: none;
  border-radius: 0;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px; /* 与正文间距 */
}

.badge-icon {
  width: 16px;
  height: 16px;
}

.badge-text {
  font-size: 12px;
  font-weight: 600;
}

.error-content {
  display: flex;
  flex-direction: column; /* 垂直排列，使文本靠上 */
  align-items: flex-start; /* 左上对齐 */
  gap: $spacing-xs;
  color: var(--text-primary);
}

.error-text {
  font-size: 0.95rem;
  line-height: 1.6; /* 更舒适的行高 */
  color: var(--text-secondary); /* 文案减淡 */
  margin: 0;
}

// 头像上传相关样式
.avatar-section {
  display: flex;
  gap: $spacing-lg;
  align-items: flex-start;
}

.avatar-preview {
  flex-shrink: 0;
}

.avatar-container {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--background-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--border-color);
  transition: border-color 0.2s;

  &.has-image {
    border-color: var(--primary-color);
  }
}

.avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-xs;
  color: var(--text-tertiary);
}

.avatar-icon {
  width: 32px;
  height: 32px;
}

.avatar-text {
  font-size: 0.875rem;
}

.avatar-source {
  text-align: center;
  font-size: 0.75rem;
  color: var(--text-tertiary);
  margin-top: $spacing-xs;
}

.upload-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: $spacing;
}

.upload-area {
  border: 2px dashed var(--border-color);
  border-radius: 8px;
  padding: $spacing-lg;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  background: var(--background-secondary);
  position: relative;
  z-index: 1;

  &:hover, &.drag-over {
    border-color: var(--primary-color);
    background: rgba(217, 119, 6, 0.05); /* 使用直接的颜色值替代 */
  }

  &.disabled {
    cursor: not-allowed;
    opacity: 0.6;
    background: var(--background-tertiary);

    &:hover {
      border-color: var(--border-color);
      background: var(--background-tertiary);
    }
  .upload-content { pointer-events: none; }
  }
}

/* 去除覆盖式 input，改用 label for 行为触发文件选择器 */

.upload-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
  pointer-events: auto; /* 允许点击 label 触发 input */
  cursor: pointer;
}

.upload-icon {
  width: 48px;
  height: 48px;
  color: var(--text-tertiary);
}

.upload-text {
  font-size: 1rem;
  color: var(--text-primary);
  margin: 0;
  font-weight: 500;
}

.upload-hint {
  font-size: 0.875rem;
  color: var(--text-tertiary);
  margin: 0;
}

.url-input-group {
  .form-label {
    margin-bottom: $spacing-xs;
  }

  .form-input.disabled {
    background: var(--background-tertiary);
    color: var(--text-tertiary);
    cursor: not-allowed;
  }

  .input-disabled-hint {
    font-size: 0.75rem;
    color: var(--text-tertiary);
    font-style: italic;
  }
}

.avatar-actions {
  display: flex;
  gap: $spacing-sm;
}

.btn-clear {
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  padding: $spacing-xs $spacing-sm;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.875rem;
  transition: all 0.2s;

  &:hover {
    color: var(--error-color);
    border-color: var(--error-color);
    background: rgba(239, 68, 68, 0.05); /* 使用直接的颜色值 */
  }
}

@media (max-width: 768px) {
  .avatar-section {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .avatar-container {
    width: 100px;
    height: 100px;
  }

  .upload-section {
    width: 100%;
  }
}
</style>
