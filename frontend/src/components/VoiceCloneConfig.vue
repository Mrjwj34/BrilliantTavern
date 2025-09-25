<template>
  <div class="voice-clone-config">
    <!-- 头部导航 -->
    <div class="config-header">
      <button @click="$emit('back')" class="back-btn">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5"/>
          <path d="M12 19l-7-7 7-7"/>
        </svg>
        返回
      </button>
      <h2 class="config-title">音色克隆配置</h2>
      <div class="config-actions">
        <button @click="resetForm" class="action-btn secondary">重置</button>
        <button @click="saveVoice" :disabled="!canSave || saving" class="action-btn primary">
          <svg v-if="saving" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
            <path d="M21 12a9 9 0 11-6.219-8.56"/>
          </svg>
          <span v-else>保存音色</span>
        </button>
      </div>
    </div>

    <!-- 配置表单 -->
    <div class="config-content">
      <div class="config-form">
        <!-- 基本信息 -->
        <div class="form-section">
          <h3 class="section-title">基本信息</h3>
          
          <div class="form-group">
            <label class="form-label">音色名称 </label>
            <input 
              v-model="formData.name" 
              type="text" 
              class="form-input" 
              placeholder="请输入音色名称"
              maxlength="50"
            />
            <div class="input-hint">{{ formData.name.length }}/50</div>
          </div>
          
          <div class="form-group">
            <label class="form-label">音色描述</label>
            <textarea 
              v-model="formData.description" 
              class="form-textarea" 
              placeholder="请简要描述这个音色的特点..."
              rows="3"
              maxlength="200"
            ></textarea>
            <div class="input-hint">{{ formData.description.length }}/200</div>
          </div>
        </div>

        <!-- 音频配置 -->
        <div class="form-section">
          <h3 class="section-title">音频文件</h3>
          
          <div class="audio-upload-section">
            <!-- 录音功能 -->
            <div class="recording-section">
              <div class="recording-controls">
                <button 
                  @click="toggleRecording" 
                  :class="['record-btn', { recording: isRecording }]"
                  :disabled="!microphoneSupported"
                  title="点击开始录音"
                >
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="mic-icon">
                    <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                    <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                    <line x1="12" y1="19" x2="12" y2="23"/>
                    <line x1="8" y1="23" x2="16" y2="23"/>
                  </svg>
                </button>
                
                <div class="recording-info">
                  <span class="recording-label">手动录制音频</span>
                  <span v-if="isRecording" class="recording-status">录音中 {{ formatTime(recordingTime) }}</span>
                  <span v-else-if="!microphoneSupported" class="recording-status error">麦克风不可用</span>
                  <span v-else class="recording-hint">点击麦克风开始录制音频</span>
                </div>
                
                <!-- 录音预览控制 - 放在右侧 -->
                <div v-if="recordedBlob" class="recorded-controls">
                  <button @click="playRecording" class="control-btn play-recorded-btn" :title="recordingPlaying ? '暂停' : '播放录音'">
                    <svg v-if="!recordingPlaying" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polygon points="5 3,19 12,5 21,5 3"/>
                    </svg>
                    <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="6" y="4" width="4" height="16"/>
                      <rect x="14" y="4" width="4" height="16"/>
                    </svg>
                  </button>
                  <button @click="useRecordedAudio" class="control-btn use-recorded-btn" title="使用此录音">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="20,6 9,17 4,12"/>
                    </svg>
                  </button>
                </div>
              </div>
            </div>
            
            <!-- 参考文本 - 录制区域下方 -->
            <div class="reference-text-section recording-reference">
              <div class="reference-label">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/>
                  <polyline points="14,2 14,8 20,8"/>
                  <line x1="16" y1="13" x2="8" y2="13"/>
                  <line x1="16" y1="17" x2="8" y2="17"/>
                  <polyline points="10,9 9,9 8,9"/>
                </svg>
                朗读文本
              </div>
              <div class="text-options">
                <label class="radio-option">
                  <input 
                    type="radio" 
                    value="default" 
                    v-model="textOption"
                    name="textOption" 
                  />
                  <span class="radio-label">使用默认</span>
                </label>
                <label class="radio-option">
                  <input 
                    type="radio" 
                    value="custom" 
                    v-model="textOption"
                    name="textOption" 
                  />
                  <span class="radio-label">自定义</span>
                </label>
              </div>
              
              <div class="reference-text-content">
                <textarea 
                  v-model="formData.referenceText"
                  :disabled="textOption === 'default'"
                  class="form-textarea compact"
                  :class="{ disabled: textOption === 'default' }"
                  placeholder="请输入要朗读的文本..."
                  rows="3"
                  maxlength="500"
                ></textarea>
                <div class="input-hint">{{ formData.referenceText.length }}/500</div>
              </div>
            </div>
            
            <!-- 分隔线 -->
            <div class="section-divider">
              <span>或</span>
            </div>

            <!-- 文件上传区域 -->
            <div class="upload-area" 
                 :class="{ 'drag-over': dragOver && !audioFile, 'has-file': audioFile, 'disabled': audioFile }"
                 @click="!audioFile && triggerFileSelect()"
                 @drop="!audioFile && handleDrop($event)" 
                 @dragover="!audioFile && handleDragOver($event)" 
                 @dragleave="!audioFile && handleDragLeave($event)">
              <input 
                ref="fileInput"
                type="file" 
                accept="audio/*" 
                @change="handleFileSelect"
                :disabled="!!audioFile"
                style="display: none"
              />
              
              <div class="upload-placeholder">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="upload-icon">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                  <polyline points="17,8 12,3 7,8"/>
                  <line x1="12" y1="3" x2="12" y2="15"/>
                </svg>
                <h4>上传音频文件</h4>
                <p v-if="!audioFile">拖拽文件到这里或点击上传</p>
                <p v-else class="disabled-text">已选择音频文件，请先移除当前文件</p>
                <p class="upload-hint">支持 MP3, WAV, M4A 等格式，建议大小在 10MB 以内</p>
              </div>
            </div>
            
            <!-- 音频预览区域 -->
            <div v-if="audioFile" class="audio-preview-section">
              <div class="section-title-small">音频预览</div>
              <div class="audio-preview">
                <!-- 动态音频波形显示 -->
                <div class="audio-waveform">
                  <div class="waveform-bars">
                    <div class="bar" 
                         v-for="i in 20" 
                         :key="`bar-${i}`" 
                         :class="{ active: (playing || recordingPlaying) && getBarActive(i) }"
                         :style="{ height: getBarHeight(i) + '%' }">
                    </div>
                  </div>
                </div>
                
                <div class="audio-info">
                  <div class="audio-details">
                    <div class="audio-name">{{ audioFile.name }}</div>
                    <div class="audio-meta">
                      {{ formatFileSize(audioFile.size) }} • {{ getFileExtension(audioFile.name).toUpperCase() }}
                    </div>
                  </div>
                </div>
                
                <!-- 音频播放控制 -->
                <div class="audio-controls">
                  <button @click.stop="togglePlay" class="control-btn play-btn" :title="playing ? '暂停' : '播放'">
                    <svg v-if="!playing" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polygon points="5 3,19 12,5 21,5 3"/>
                    </svg>
                    <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="6" y="4" width="4" height="16"/>
                      <rect x="14" y="4" width="4" height="16"/>
                    </svg>
                  </button>
                  <button @click.stop="removeFile" class="control-btn remove-btn" title="移除文件">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <line x1="18" y1="6" x2="6" y2="18"/>
                      <line x1="6" y1="6" x2="18" y2="18"/>
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>



        <!-- 分享设置 -->
        <div class="form-section">
          <h3 class="section-title">分享设置</h3>
          
          <div class="sharing-options">
            <label class="toggle-option">
              <input type="checkbox" v-model="formData.isPublic" />
              <span class="toggle-slider"></span>
              <div class="toggle-content">
                <div class="toggle-title">公开音色</div>
                <div class="toggle-description">允许其他用户发现和使用这个音色</div>
              </div>
            </label>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { ttsAPI } from '@/api'
import { storage } from '@/utils'
import { notification } from '@/utils/notification'

export default {
  name: 'VoiceCloneConfig',
  emits: ['back'],
  setup(props, { emit }) {
    // 表单数据
    const formData = reactive({
      name: '',
      description: '',
      referenceText: '春天来了，万物复苏。鸟儿在枝头歌唱，花儿在阳光下绽放。微风轻抚着脸庞，带来了温暖的气息。在这美好的季节里，人们纷纷走出家门，感受大自然的魅力。',
      isPublic: false
    })
    
    // 音频相关
    const audioFile = ref(null)
    const audioPreviewUrl = ref('')
    const playing = ref(false)
    const uploadAudioElement = ref(null)
    const recordingAudioElement = ref(null)
    const recordingUrl = ref('')
    const currentTime = ref(0)
    const duration = ref(0)
    
    // 录音相关
    const isRecording = ref(false)
    const recordingTime = ref(0)
    const recordedBlob = ref(null)
    const recordingPlaying = ref(false)
    const microphoneSupported = ref(true)
    const mediaRecorder = ref(null)
    const recordingTimer = ref(null)
    
    // UI 状态
    const dragOver = ref(false)
    const textOption = ref('default')
    const saving = ref(false)
    const fileInput = ref(null)
    
    // 默认参考文本
    const defaultReferenceText = '春天来了，万物复苏。鸟儿在枝头歌唱，花儿在阳光下绽放。微风轻抚着脸庞，带来了温暖的气息。在这美好的季节里，人们纷纷走出家门，感受大自然的魅力。'
    
    // 计算属性
    const canSave = computed(() => {
      return formData.name.trim() && 
             (audioFile.value || recordedBlob.value) && 
             formData.referenceText.trim()
    })
    
    // 监听文本选项变化
    watch(textOption, (newVal) => {
      if (newVal === 'default') {
        formData.referenceText = defaultReferenceText
      } else if (newVal === 'custom' && formData.referenceText === defaultReferenceText) {
        formData.referenceText = ''
      }
    })
    
    // 文件处理
    const triggerFileSelect = () => {
      if (fileInput.value) {
        fileInput.value.click()
      }
    }
    
    const handleFileSelect = (event) => {
      const file = event.target.files[0]
      if (file) {
        setAudioFile(file)
      }
    }
    
    const setAudioFile = (file) => {
      // 验证文件类型
      if (!file.type.startsWith('audio/')) {
        alert('请选择音频文件')
        return
      }
      
      // 验证文件大小 (10MB)
      const maxSize = 10 * 1024 * 1024
      if (file.size > maxSize) {
        alert('文件大小不能超过 10MB')
        return
      }
      
      audioFile.value = file
      audioPreviewUrl.value = URL.createObjectURL(file)
      
      // 清除录音
      if (recordedBlob.value) {
        recordedBlob.value = null
        if (recordingUrl.value) {
          URL.revokeObjectURL(recordingUrl.value)
          recordingUrl.value = ''
        }
        if (recordingAudioElement.value) {
          recordingAudioElement.value.pause()
          recordingAudioElement.value = null
        }
        recordingPlaying.value = false
      }
      
      // 停止当前播放
      playing.value = false
      if (uploadAudioElement.value) {
        uploadAudioElement.value.pause()
        uploadAudioElement.value = null
      }
    }
    
    const removeFile = () => {
      if (audioPreviewUrl.value) {
        URL.revokeObjectURL(audioPreviewUrl.value)
      }
      if (recordingUrl.value) {
        URL.revokeObjectURL(recordingUrl.value)
      }
      
      // 停止所有音频播放
      if (uploadAudioElement.value) {
        uploadAudioElement.value.pause()
        uploadAudioElement.value = null
      }
      if (recordingAudioElement.value) {
        recordingAudioElement.value.pause()
        recordingAudioElement.value = null
      }
      
      audioFile.value = null
      audioPreviewUrl.value = ''
      recordingUrl.value = ''
      playing.value = false
      recordingPlaying.value = false
      currentTime.value = 0
      duration.value = 0
      
      if (fileInput.value) {
        fileInput.value.value = ''
      }
    }
    
    // 拖拽处理
    const handleDragOver = (e) => {
      e.preventDefault()
      dragOver.value = true
    }
    
    const handleDragLeave = (e) => {
      e.preventDefault()
      dragOver.value = false
    }
    
    const handleDrop = (e) => {
      e.preventDefault()
      dragOver.value = false
      
      const files = e.dataTransfer.files
      if (files.length > 0) {
        setAudioFile(files[0])
      }
    }
    
    // 音频播放
    const togglePlay = () => {
      if (!audioPreviewUrl.value) return
      
      if (playing.value) {
        if (uploadAudioElement.value) {
          uploadAudioElement.value.pause()
        }
        playing.value = false
        recordingPlaying.value = false
      } else {
        // 停止录音播放（如果正在播放）
        if (recordingAudioElement.value && recordingPlaying.value) {
          recordingAudioElement.value.pause()
          recordingPlaying.value = false
        }
        
        if (!uploadAudioElement.value || uploadAudioElement.value.src !== audioPreviewUrl.value) {
          uploadAudioElement.value = new Audio(audioPreviewUrl.value)
          
          uploadAudioElement.value.addEventListener('loadedmetadata', () => {
            duration.value = uploadAudioElement.value.duration
          })
          
          uploadAudioElement.value.addEventListener('timeupdate', () => {
            currentTime.value = uploadAudioElement.value.currentTime
          })
          
          uploadAudioElement.value.addEventListener('ended', () => {
            playing.value = false
            currentTime.value = 0
          })
        }
        
        uploadAudioElement.value.play()
        playing.value = true
      }
    }
    
    // 录音功能
    const toggleRecording = async () => {
      if (!microphoneSupported.value) {
        alert('浏览器不支持录音功能')
        return
      }
      
      if (isRecording.value) {
        stopRecording()
      } else {
        await startRecording()
      }
    }
    
    const startRecording = async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
        mediaRecorder.value = new MediaRecorder(stream)
        
        const chunks = []
        mediaRecorder.value.ondataavailable = (event) => {
          chunks.push(event.data)
        }
        
        mediaRecorder.value.onstop = () => {
          const blob = new Blob(chunks, { type: 'audio/wav' })
          recordedBlob.value = blob
          
          // 停止所有轨道
          stream.getTracks().forEach(track => track.stop())
        }
        
        mediaRecorder.value.start()
        isRecording.value = true
        recordingTime.value = 0
        
        // 开始计时
        recordingTimer.value = setInterval(() => {
          recordingTime.value++
        }, 1000)
        
      } catch (error) {
        console.error('录音启动失败:', error)
        alert('无法访问麦克风，请检查权限设置')
        microphoneSupported.value = false
      }
    }
    
    const stopRecording = () => {
      if (mediaRecorder.value && isRecording.value) {
        mediaRecorder.value.stop()
        isRecording.value = false
        
        if (recordingTimer.value) {
          clearInterval(recordingTimer.value)
          recordingTimer.value = null
        }
      }
    }
    
    const playRecording = () => {
      if (!recordedBlob.value) return
      
      if (recordingPlaying.value) {
        // 暂停录音播放
        if (recordingAudioElement.value) {
          recordingAudioElement.value.pause()
        }
        recordingPlaying.value = false
        playing.value = false
        return
      }
      
      // 停止上传音频播放（如果正在播放）
      if (uploadAudioElement.value && playing.value) {
        uploadAudioElement.value.pause()
        playing.value = false
      }
      
      if (!recordingUrl.value) {
        recordingUrl.value = URL.createObjectURL(recordedBlob.value)
      }
      
      if (!recordingAudioElement.value || recordingAudioElement.value.src !== recordingUrl.value) {
        recordingAudioElement.value = new Audio(recordingUrl.value)
        
        recordingAudioElement.value.addEventListener('loadedmetadata', () => {
          duration.value = recordingAudioElement.value.duration
        })
        
        recordingAudioElement.value.addEventListener('timeupdate', () => {
          currentTime.value = recordingAudioElement.value.currentTime
        })
        
        recordingAudioElement.value.addEventListener('ended', () => {
          recordingPlaying.value = false
          playing.value = false
          currentTime.value = 0
        })
      }
      
      recordingAudioElement.value.play()
      recordingPlaying.value = true
      playing.value = true
    }
    
    // 更新波形显示
    const updateWaveform = () => {
      const activeElement = recordingPlaying.value ? recordingAudioElement.value : uploadAudioElement.value
      if (activeElement) {
        currentTime.value = activeElement.currentTime
        duration.value = activeElement.duration || 0
      }
    }
    
    // 获取波形柱状图高度
    const getBarHeight = (index) => {
      if (!index || index < 1) return 20
      
      const base = [20, 45, 35, 60, 80, 55, 70, 40, 90, 65, 50, 75, 35, 85, 45, 60, 30, 55, 40, 70]
      const barIndex = (index - 1) % base.length
      
      if ((playing.value || recordingPlaying.value) && duration.value > 0 && currentTime.value >= 0) {
        // 根据播放进度调整高度
        const progress = Math.max(0, Math.min(1, currentTime.value / duration.value))
        const activeRange = progress * 20
        
        if (index <= activeRange + 2) {
          // 增加活跃范围内的柱子高度
          const heightMultiplier = Math.max(1.0, 1.5 - Math.abs(index - activeRange) * 0.1)
          return Math.max(base[barIndex] * heightMultiplier, 25)
        }
      }
      
      return base[barIndex]
    }
    
    // 获取波形柱是否激活
    const getBarActive = (index) => {
      if (!index || index < 1) return false
      if (!(playing.value || recordingPlaying.value) || duration.value <= 0 || currentTime.value < 0) return false
      
      const progress = Math.max(0, Math.min(1, currentTime.value / duration.value))
      const activePosition = progress * 20
      return index >= activePosition - 1 && index <= activePosition + 1
    }
    
    const useRecordedAudio = () => {
      if (recordedBlob.value) {
        // 清除已选择的文件
        removeFile()
        
        // 创建 File 对象
        const file = new File([recordedBlob.value], `recording-${Date.now()}.wav`, {
          type: 'audio/wav'
        })
        
        setAudioFile(file)
        recordedBlob.value = null
      }
    }
    
    // 表单操作
    const resetForm = () => {
      formData.name = ''
      formData.description = ''
      formData.referenceText = defaultReferenceText
      formData.isPublic = false
      textOption.value = 'default'
      
      removeFile()
      
      if (recordedBlob.value) {
        recordedBlob.value = null
      }
    }
    
    const saveVoice = async () => {
      if (!canSave.value) return
      
      saving.value = true
      
      try {
        const user = storage.get('user')
        if (!user) {
          notification.error('请先登录')
          return
        }
        
        // 准备提交的文件
        const fileToUpload = audioFile.value
        if (!fileToUpload) {
          notification.error('请选择音频文件')
          return
        }
        
        // 调用API创建音色
        const response = await ttsAPI.createVoice({
          userId: user.userId,
          name: formData.name.trim(),
          description: formData.description.trim(),
          audioFile: fileToUpload,
          referenceText: formData.referenceText.trim(),
          isPublic: formData.isPublic
        })
        
        console.log('createVoice API response:', response)
        
        // 检查响应格式 - 后端返回的是 ApiResponse 格式
        if (response.code === 200) {
          // 使用全局通知系统显示成功消息
          notification.success(response.message || '音色创建成功!')
          resetForm()
          emit('back')
        } else {
          // 使用全局通知系统显示错误消息
          notification.error(response.message || '音色创建失败')
        }
        
      } catch (error) {
        console.error('保存音色失败:', error)
        notification.error('保存失败: ' + (error.message || '未知错误'))
      } finally {
        saving.value = false
      }
    }
    
    // 工具函数
    const formatFileSize = (bytes) => {
      if (bytes < 1024) return bytes + ' B'
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
      return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
    }
    
    const getFileExtension = (filename) => {
      return filename.split('.').pop() || ''
    }
    
    const formatTime = (seconds) => {
      const mins = Math.floor(seconds / 60)
      const secs = seconds % 60
      return `${mins}:${secs.toString().padStart(2, '0')}`
    }
    
    // 生命周期
    onMounted(() => {
      // 检查麦克风支持
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        microphoneSupported.value = false
      }
    })
    
    onUnmounted(() => {
      // 清理资源
      if (audioPreviewUrl.value) {
        URL.revokeObjectURL(audioPreviewUrl.value)
      }
      
      if (recordingUrl.value) {
        URL.revokeObjectURL(recordingUrl.value)
      }
      
      if (uploadAudioElement.value) {
        uploadAudioElement.value.pause()
        uploadAudioElement.value = null
      }
      
      if (recordingAudioElement.value) {
        recordingAudioElement.value.pause()
        recordingAudioElement.value = null
      }
      
      if (recordingTimer.value) {
        clearInterval(recordingTimer.value)
      }
      
      if (isRecording.value) {
        stopRecording()
      }
    })
    
    return {
      // 数据
      formData,
      audioFile,
      audioPreviewUrl,
      playing,
      uploadAudioElement,
      recordingAudioElement,
      recordingUrl,
      currentTime,
      duration,
      isRecording,
      recordingTime,
      recordedBlob,
      recordingPlaying,
      microphoneSupported,
      dragOver,
      textOption,
      saving,
      fileInput,
      
      // 计算属性
      canSave,
      
      // 方法
      triggerFileSelect,
      handleFileSelect,
      removeFile,
      handleDragOver,
      handleDragLeave,
      handleDrop,
      togglePlay,
      toggleRecording,
      playRecording,
      useRecordedAudio,
      updateWaveform,
      resetForm,
      saveVoice,
      formatFileSize,
      getFileExtension,
      formatTime,
      getBarHeight,
      getBarActive
    }
  }
}
</script>

<style lang="scss" scoped>
.voice-clone-config {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--background-primary);
  min-height: 600px; /* 确保有足够的最小高度 */
}

// 头部
.config-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-lg;
  border-bottom: 1px solid var(--border-light);
  background: var(--background-secondary);
  flex-shrink: 0;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: $border-radius;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all $transition-normal ease;
  
  &:hover {
    background: var(--background-tertiary);
    color: var(--text-primary);
  }
}

.config-title {
  font-size: 1.3rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
  flex: 1;
  text-align: center;
}

.config-actions {
  display: flex;
  gap: $spacing-sm;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing;
  border: none;
  border-radius: $border-radius;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-normal ease;
  min-width: 80px;
  justify-content: center;
  
  &.primary {
    background: var(--primary-color);
    color: white;
    
    &:hover:not(:disabled) {
      background: var(--primary-dark);
      transform: translateY(-1px);
    }
    
    &:disabled {
      background: var(--text-tertiary);
      cursor: not-allowed;
      transform: none;
    }
  }
  
  &.secondary {
    background: var(--background-tertiary);
    color: var(--text-primary);
    border: 1px solid var(--border-color);
    
    &:hover {
      background: var(--background-primary);
    }
  }
}

// 内容区域
.config-content {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-lg;
}

.config-form {
  max-width: 600px;
  margin: 0 auto;
}

// 表单区块
.form-section {
  margin-bottom: $spacing-2xl;
  
  &:last-child {
    margin-bottom: 0;
  }
}

.section-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: $spacing-lg;
  display: flex;
  align-items: center;
  
  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: var(--border-light);
    margin-left: $spacing;
  }
}

// 表单控件
.form-group {
  margin-bottom: $spacing-lg;
}

.form-label {
  display: block;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: $spacing-sm;
  
  &::after {
    content: ' *';
    color: var(--error-color);
  }
  
  &.optional::after {
    display: none;
  }
}

.form-input,
.form-textarea {
  width: 100%;
  padding: $spacing $spacing-sm;
  border: 1px solid var(--border-color);
  border-radius: $border-radius;
  background: var(--background-primary);
  color: var(--text-primary);
  font-size: 0.95rem;
  transition: all $transition-normal ease;
  resize: vertical;
  
  &:focus {
    outline: none;
    border-color: var(--primary-color);
    box-shadow: 0 0 0 3px rgba(217, 119, 6, 0.1);
  }
  
  &.disabled {
    background: var(--background-tertiary);
    color: var(--text-tertiary);
    cursor: not-allowed;
  }
  
  &::placeholder {
    color: var(--text-placeholder);
  }
}

.input-hint {
  text-align: right;
  font-size: 0.8rem;
  color: var(--text-tertiary);
  margin-top: $spacing-xs;
}

// 音频上传
.audio-upload-section {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
  
  // 录音功能
  .recording-section {
    .recording-controls {
      display: flex;
      align-items: center;
      gap: $spacing;
      margin-bottom: $spacing;
      
      .record-btn {
        width: 60px;
        height: 60px;
        border: none;
        background: transparent;
        border-radius: 50%;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: all $transition-normal ease;
        position: relative;
        flex-shrink: 0;
        
        .mic-icon {
          color: #f59e0b !important;
          filter: drop-shadow(0 2px 4px rgba(245, 158, 11, 0.3));
        }
        
        &:hover {
          background: rgba(0, 0, 0, 0.05) !important;
          transform: scale(1.05);
        }
        
        &:disabled {
          opacity: 0.5;
          cursor: not-allowed;
          
          &:hover {
            background: transparent !important;
            transform: none;
          }
        }
        
        &.recording {
          .mic-icon {
            color: #dc2626 !important;
          }
          
          &::after {
            content: '';
            position: absolute;
            width: 100%;
            height: 100%;
            border-radius: 50%;
            border: 2px solid #dc2626;
            animation: pulse 2s infinite;
          }
        }
      }
      
      .recording-info {
        display: flex;
        flex-direction: column;
        gap: $spacing-xs;
        
        .recording-label {
          font-size: 1rem;
          font-weight: 500;
          color: var(--text-primary);
        }
        
        .recording-status {
          font-size: 0.85rem;
          color: #dc2626;
          font-weight: 500;
          
          &.error {
            color: var(--error-color);
          }
        }
        
        .recording-hint {
          font-size: 0.8rem;
          color: var(--text-tertiary);
        }
      }
    }
    
    .recorded-controls {
      display: flex;
      gap: $spacing-sm;
      margin-left: 76px; // 与麦克风对齐
    }
  }
  
  // 分隔线
  .section-divider {
    text-align: center;
    margin: $spacing 0;
    position: relative;
    
    &::before {
      content: '';
      position: absolute;
      top: 50%;
      left: 0;
      right: 0;
      height: 1px;
      background: var(--border-light);
    }
    
    span {
      background: var(--background-primary);
      color: var(--text-tertiary);
      padding: 0 $spacing;
      font-size: 0.9rem;
    }
  }
  
  .upload-area {
    border: 2px dashed var(--border-color);
    border-radius: $border-radius-lg;
    padding: $spacing-2xl;
    text-align: center;
    transition: all $transition-normal ease;
    background: var(--background-secondary);
    cursor: pointer;
    
    &:hover:not(.disabled) {
      border-color: var(--primary-color);
      background: rgba(217, 119, 6, 0.02);
    }
    
    &.drag-over:not(.disabled) {
      border-color: var(--primary-color);
      background: rgba(217, 119, 6, 0.05);
    }
    
    &.has-file {
      border-style: solid;
      border-color: var(--success-color);
      background: rgba(5, 150, 105, 0.05);
    }
    
    &.disabled {
      opacity: 0.6;
      cursor: not-allowed;
      background: var(--background-tertiary);
    }
  }
  
  .upload-placeholder {
    .upload-icon {
      color: var(--text-tertiary);
      margin-bottom: $spacing;
    }
    
    h4 {
      font-size: 1.2rem;
      font-weight: 600;
      color: var(--text-primary);
      margin-bottom: $spacing-sm;
    }
    
    p {
      color: var(--text-secondary);
      margin-bottom: $spacing-xs;
      
      &.upload-hint {
        font-size: 0.9rem;
        color: var(--text-tertiary);
      }
    }
  }
}

// 音频预览区域
.audio-preview-section {
  margin-top: $spacing-lg;
  
  .section-title-small {
    font-size: 0.9rem;
    font-weight: 600;
    color: var(--text-secondary);
    margin-bottom: $spacing;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
}
.audio-preview {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
  background: var(--background-primary);
  padding: $spacing-lg;
  border-radius: $border-radius;
  border: 1px solid var(--border-light);
}

// 动态音频波形柱状图
.audio-waveform {
  flex-shrink: 0;
  padding: $spacing-sm;
  
  .waveform-bars {
    display: flex;
    align-items: end;
    gap: 2px;
    height: 50px;
    
    .bar {
      width: 3px;
      background: linear-gradient(to top, #9ca3af, #d1d5db) !important;
      border-radius: 1px;
      transition: all 0.3s ease;
      min-height: 8px;
      
      &.active {
        background: linear-gradient(to top, #d97706, #f59e0b, #fbbf24) !important;
        box-shadow: 0 0 6px rgba(245, 158, 11, 0.4);
        transform: scaleY(1.1);
      }
    }
  }
}

.audio-info {
  flex: 1;
  
  .audio-details {
    .audio-name {
      font-weight: 500;
      color: var(--text-primary);
      margin-bottom: $spacing-xs;
      font-size: 0.95rem;
    }
    
    .audio-meta {
      font-size: 0.85rem;
      color: var(--text-secondary);
    }
  }
}

.audio-controls {
  display: flex;
  gap: $spacing-sm;
}

// 扁平化控制按钮
.control-btn {
  width: 40px !important;
  height: 40px !important;
  border: none !important;
  background: transparent !important;
  border-radius: $border-radius !important;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all $transition-normal ease;
  color: var(--text-secondary) !important;
  
  svg {
    color: inherit !important;
  }
  
  &:hover {
    background: rgba(0, 0, 0, 0.08) !important;
    color: var(--text-primary) !important;
    transform: scale(1.1);
  }
  
  &.play-btn:hover {
    color: #059669 !important;
    background: rgba(5, 150, 105, 0.1) !important;
  }
  
  &.remove-btn:hover {
    color: #dc2626 !important;
    background: rgba(220, 38, 38, 0.1) !important;
  }
  
  &.play-recorded-btn:hover {
    color: #2563eb !important;
    background: rgba(37, 99, 235, 0.1) !important;
  }
  
  &.use-recorded-btn:hover {
    color: #059669 !important;
    background: rgba(5, 150, 105, 0.1) !important;
  }
}

// 文本选项
.reference-text-section {
  .text-options {
    display: flex;
    gap: $spacing-lg;
    margin-bottom: $spacing;
  }
  
  .radio-option {
    display: flex;
    align-items: center;
    cursor: pointer;
    
    input[type="radio"] {
      margin-right: $spacing-sm;
      accent-color: var(--primary-color);
    }
    
    .radio-label {
      color: var(--text-primary);
      font-weight: 500;
    }
  }
  
  // 录制区域的参考文本样式
  &.recording-reference {
    margin-top: $spacing-lg;
    padding: $spacing;
    background: var(--background-card);
    border: 1px solid var(--border-light);
    border-radius: $border-radius;
    
    .reference-label {
      font-size: 14px;
      font-weight: 600;
      color: var(--text-primary);
      margin-bottom: $spacing-sm;
      display: flex;
      align-items: center;
      gap: $spacing-sm;
      
      svg {
        width: 16px;
        height: 16px;
        color: var(--primary-color);
      }
    }
    
    .text-options {
      gap: $spacing;
      margin-bottom: $spacing-sm;
      
      .radio-option {
        .radio-label {
          font-size: 13px;
          font-weight: 500;
        }
      }
    }
    
    .reference-text-content {
      .form-textarea.compact {
        font-size: 13px;
        line-height: 1.4;
        padding: $spacing-sm;
        min-height: 60px;
        resize: vertical;
      }
      
      .input-hint {
        font-size: 11px;
        color: var(--text-tertiary);
        text-align: right;
        margin-top: 4px;
      }
    }
  }
}

// 分享设置
.sharing-options {
  .toggle-option {
    display: flex;
    align-items: center;
    gap: $spacing;
    cursor: pointer;
    padding: $spacing;
    border: 1px solid var(--border-light);
    border-radius: $border-radius;
    transition: all $transition-normal ease;
    
    &:hover {
      background: var(--background-secondary);
    }
    
    input[type="checkbox"] {
      display: none;
    }
    
    .toggle-slider {
      width: 44px;
      height: 24px;
      background: var(--text-tertiary);
      border-radius: 12px;
      position: relative;
      transition: all $transition-normal ease;
      flex-shrink: 0;
      
      &::after {
        content: '';
        width: 20px;
        height: 20px;
        background: white;
        border-radius: 50%;
        position: absolute;
        top: 2px;
        left: 2px;
        transition: all $transition-normal ease;
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
      }
    }
    
    input:checked + .toggle-slider {
      background: var(--success-color);
      
      &::after {
        left: 22px;
      }
    }
    
    .toggle-title {
      font-weight: 500;
      color: var(--text-primary);
      margin-bottom: $spacing-xs;
    }
    
    .toggle-description {
      font-size: 0.9rem;
      color: var(--text-secondary);
    }
  }
}

// 动画
@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(220, 38, 38, 0.7);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(220, 38, 38, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(220, 38, 38, 0);
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.spin {
  animation: spin 1s linear infinite;
}

// 响应式
@media (max-width: 768px) {
  .config-header {
    padding: $spacing;
    flex-direction: column;
    gap: $spacing;
    
    .config-title {
      order: -1;
    }
    
    .config-actions {
      width: 100%;
      justify-content: center;
    }
  }
  
  .config-content {
    padding: $spacing;
  }
  
  .audio-preview {
    flex-direction: column;
    gap: $spacing;
    align-items: stretch;
  }
  
  .audio-controls {
    justify-content: center;
  }
  
  .text-options {
    flex-direction: column;
    gap: $spacing-sm;
  }
}
</style>
