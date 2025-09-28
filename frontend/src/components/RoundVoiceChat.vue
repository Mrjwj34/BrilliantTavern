<template>
  <div class="round-voice-chat">
    <aside class="character-panel">
      <div class="panel-header">
        <h3>选择角色卡</h3>
        <button class="refresh-btn" @click="fetchCharacters(true)" :disabled="loadingCharacters">
          刷新
        </button>
      </div>
      <div class="search-box">
        <input
          v-model.trim="searchKeyword"
          type="text"
          placeholder="搜索角色卡"
          @keyup.enter="fetchCharacters(true)"
        />
        <button class="search-btn" @click="fetchCharacters(true)">搜索</button>
      </div>
      <div class="character-list" :class="{ loading: loadingCharacters }">
        <div v-if="loadingCharacters" class="loading-hint">加载角色中...</div>
        <template v-else>
          <div
            v-for="card in filteredCharacters"
            :key="card.id"
            :class="['character-item', { active: selectedCharacter && selectedCharacter.id === card.id }]"
            @click="selectCharacter(card)"
          >
            <div class="item-header">
              <h4>{{ card.name }}</h4>
              <span v-if="card.cardType" class="tag">{{ card.cardType }}</span>
            </div>
            <p class="item-desc">{{ card.shortDescription || '暂无描述' }}</p>
            <div class="item-meta">
              <span>音色ID: {{ card.ttsVoiceId || '默认' }}</span>
              <span v-if="card.likesCount !== undefined">❤ {{ card.likesCount }}</span>
            </div>
          </div>
          <div v-if="!filteredCharacters.length" class="empty-hint">
            未找到符合条件的角色卡
          </div>
        </template>
      </div>
    </aside>

    <section class="chat-panel">
      <header class="chat-header">
        <div>
          <h2>轮次语音对话</h2>
          <p v-if="selectedCharacter">当前角色：{{ selectedCharacter.name }}</p>
          <p v-else>请选择角色后开始对话</p>
          
          <!-- 语言设置区域 -->
          <div v-if="selectedCharacter" class="language-settings">
            <div class="language-item">
              <label class="language-label">语音语言：</label>
              <select v-model="currentVoiceLanguage" class="language-select" @change="updateLanguageSettings">
                <option value="zh">中文</option>
                <option value="ja">日文</option>
                <option value="en">英文</option>
              </select>
            </div>
            <div class="language-item">
              <label class="language-label">字幕语言：</label>
              <select v-model="currentSubtitleLanguage" class="language-select" @change="updateLanguageSettings">
                <option value="zh">中文</option>
                <option value="ja">日文</option>
                <option value="en">英文</option>
                <option value="ko">韩文</option>
                <option value="fr">法文</option>
                <option value="de">德文</option>
                <option value="es">西班牙文</option>
                <option value="ru">俄文</option>
              </select>
            </div>
          </div>
          
          <!-- 标题显示区域 -->
          <div v-if="chatTitle" class="chat-title-area">
            <span class="chat-title" :class="{ typing: titleTyping }">
              {{ chatTitle }}
              <span v-if="titleTyping" class="typing-cursor">|</span>
            </span>
          </div>
        </div>
        <div class="session-info" v-if="session">
          <span class="status-badge" :class="{ active: stompConnected }">{{ stompConnected ? '已连接' : '未连接' }}</span>
          <span class="session-id">会话ID：{{ session.sessionId }}</span>
        </div>
      </header>

      <div class="chat-body">
        <div v-if="!selectedCharacter" class="placeholder">
          <p>请从左侧选择一个角色卡以开启语音对话体验。</p>
        </div>
        <div v-else class="conversation" ref="chatListRef">
          <div v-if="historyLoading" class="loading-hint">加载历史对话...</div>
          <div
            v-for="message in messages"
            :key="message.id"
            :class="['message', message.role]"
          >
            <div class="meta">
              <span class="role">{{ message.role === 'user' ? '我' : selectedCharacter.name }}</span>
              <span class="time">{{ formatTime(message.timestamp) }}</span>
            </div>
            <div class="bubble" :class="{ typing: message.isTyping }">
              <p v-if="message.status === 'error'" class="error-text">{{ message.text }}</p>
              <p v-else>{{ message.text }}</p>
              <div v-if="message.audioSegments && message.audioSegments.length" class="audio-segments">
                <button
                  v-if="message.audioSegments.length === 1"
                  class="segment-btn"
                  @click="playSegment(message.audioSegments[0])"
                >
                  ▶ 播放语音
                </button>
                <button
                  v-else
                  v-for="segment in message.audioSegments"
                  :key="segment.segmentOrder"
                  class="segment-btn"
                  @click="playSegment(segment)"
                >
                  ▶ 播放第 {{ segment.segmentOrder + 1 }} 段
                </button>
              </div>
              <div v-if="message.status === 'pending'" class="pending-indicator">识别中...</div>
            </div>
          </div>
        </div>
      </div>

      <footer class="chat-controls">
        <div class="status-group">
          <span v-if="isRecording" class="recording-indicator">录音中 {{ recordElapsed }}s</span>
          <span v-else-if="isProcessing" class="processing-indicator">AI 正在思考...</span>
        </div>
        <div class="control-buttons">
          <button
            class="mic-btn"
            :class="{ active: isRecording }"
            :disabled="!canRecord"
            @click="toggleRecording"
          >
            {{ isRecording ? '结束录音' : '按下说话' }}
          </button>
          <button
            class="stop-btn"
            v-if="session"
            @click="endSession"
            :disabled="sessionClosing"
          >
            结束会话
          </button>
        </div>
      </footer>
    </section>
  </div>
</template>

<script>
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import {
  ref,
  reactive,
  computed,
  watch,
  nextTick,
  onMounted,
  onBeforeUnmount,
  inject
} from 'vue'
import { characterCardAPI, voiceChatAPI } from '@/api'
import { format, notification, storage } from '@/utils'

export default {
  name: 'RoundVoiceChat',
  setup() {
    // 注入来自Dashboard的会话数据
    const selectedSession = inject('selectedSession', null)
    
    // 角色卡相关
    const searchKeyword = ref('')
    const characterList = ref([])
    const loadingCharacters = ref(false)
    const selectedCharacter = ref(null)
    
    // 语言设置相关
    const currentVoiceLanguage = ref('zh')
    const currentSubtitleLanguage = ref('zh')

    // 会话相关
    const session = ref(null)
    const sessionClosing = ref(false)
    const stompClient = ref(null)
    const stompConnected = ref(false)

    const messages = ref([])
    const historyLoading = ref(false)

    const isRecording = ref(false)
    const recordElapsed = ref(0)
    const recordTimer = ref(null)
    const mediaRecorder = ref(null)
    const mediaStream = ref(null)
    const recordedChunks = ref([])
    const isProcessing = ref(false)

    const activeMessageId = ref(null)
    const userMessages = reactive(new Map())
    const assistantMessages = reactive(new Map())
    // segmentBuffers已移除，现在直接处理完整音频

    const audioQueue = ref([])
    const currentAudio = ref(null)
    const chatListRef = ref(null)
    
    // 标题显示相关
    const chatTitle = ref('')
    const titleTyping = ref(false)
    const titleAnimationTimer = ref(null)

    const loadingState = computed(() => loadingCharacters.value || historyLoading.value)
    const canRecord = computed(() => {
      return !!session.value && stompConnected.value && !isProcessing.value && !loadingState.value
    })

    const parseTimestamp = (value) => {
      if (!value) return Date.now()
      if (typeof value === 'number') return value
      const time = new Date(value).getTime()
      return Number.isNaN(time) ? Date.now() : time
    }

    const filteredCharacters = computed(() => {
      if (!searchKeyword.value) {
        return characterList.value
      }
      const keyword = searchKeyword.value.toLowerCase()
      return characterList.value.filter(card => {
        const name = (card.name || '').toLowerCase()
        const desc = (card.shortDescription || '').toLowerCase()
        return name.includes(keyword) || desc.includes(keyword)
      })
    })

    const formatTime = (value) => {
      return format.date(parseTimestamp(value), 'YYYY/MM/DD HH:mm:ss')
    }

    const fetchCharacters = async (reset = false) => {
      if (loadingCharacters.value) return
      loadingCharacters.value = true
      try {
        if (reset) {
          characterList.value = []
        }
        const params = { size: 20 }
        if (searchKeyword.value) {
          params.keyword = searchKeyword.value
        }
        const response = await characterCardAPI.getMarketCards(params)
        if (response?.code === 200) {
          characterList.value = response.data?.items || []
        }
      } catch (error) {
        console.error('获取角色卡失败', error)
        notification.error('加载角色卡失败，请稍后重试')
      } finally {
        loadingCharacters.value = false
      }
    }

    const loadHistory = async (characterId) => {
      historyLoading.value = true
      cleanupMessageAudios()
      userMessages.clear()
      assistantMessages.clear()
      try {
        const response = await voiceChatAPI.getHistory({ characterCardId: characterId, limit: 20 })
        if (response?.code === 200) {
          const history = Array.isArray(response.data) ? response.data : []
          const mapped = history
            .map(item => ({
              id: `${item.id || `${characterId}-${item.timestamp}`}`,
              role: item.role === 'ASSISTANT' ? 'assistant' : 'user',
              text: item.content,
              timestamp: parseTimestamp(item.timestamp),
              audioSegments: []
            }))
            .sort((a, b) => a.timestamp - b.timestamp)
          messages.value = mapped
        } else {
          messages.value = []
        }
      } catch (error) {
        console.error('加载历史失败', error)
        messages.value = []
      } finally {
        historyLoading.value = false
        await nextTick(() => {
          if (chatListRef.value) {
            chatListRef.value.scrollTop = chatListRef.value.scrollHeight
          }
        })
      }
    }

    // 根据会话ID加载历史记录
    const loadSessionHistory = async (sessionId) => {
      historyLoading.value = true
      cleanupMessageAudios()
      userMessages.clear()
      assistantMessages.clear()
      try {
        const response = await voiceChatAPI.getSessionHistory(sessionId)
        if (response?.code === 200) {
          const history = Array.isArray(response.data) ? response.data : []
          const mapped = history
            .map(item => ({
              id: `${item.id || `${sessionId}-${item.timestamp}`}`,
              role: item.role === 'ASSISTANT' ? 'assistant' : 'user',
              text: item.content,
              timestamp: parseTimestamp(item.timestamp),
              audioSegments: []
            }))
            .sort((a, b) => a.timestamp - b.timestamp)
          messages.value = mapped
        } else {
          messages.value = []
        }
      } catch (error) {
        console.error('加载会话历史失败', error)
        messages.value = []
      } finally {
        historyLoading.value = false
        await nextTick(() => {
          if (chatListRef.value) {
            chatListRef.value.scrollTop = chatListRef.value.scrollHeight
          }
        })
      }
    }

    // 根据角色卡ID加载完整对话历史（跨所有会话）
    const loadCompleteHistory = async (cardId) => {
      historyLoading.value = true
      cleanupMessageAudios()
      userMessages.clear()
      assistantMessages.clear()
      try {
        const response = await voiceChatAPI.getCompleteHistory(cardId)
        if (response?.code === 200) {
          const history = Array.isArray(response.data) ? response.data : []
          const mapped = history
            .map(item => ({
              id: `${item.id || `complete-${item.timestamp}`}`,
              role: item.role === 'ASSISTANT' ? 'assistant' : 'user',
              text: item.content,
              timestamp: parseTimestamp(item.timestamp),
              audioSegments: []
            }))
            .sort((a, b) => a.timestamp - b.timestamp)
          messages.value = mapped
        } else {
          messages.value = []
        }
      } catch (error) {
        console.error('加载完整对话历史失败', error)
        messages.value = []
      } finally {
        historyLoading.value = false
        await nextTick(() => {
          if (chatListRef.value) {
            chatListRef.value.scrollTop = chatListRef.value.scrollHeight
          }
        })
      }
    }

    const selectCharacter = async (card) => {
      if (selectedCharacter.value && selectedCharacter.value.id === card.id) {
        return
      }
      selectedCharacter.value = card
      
      // 初始化语言设置（使用角色卡的默认语言）
      currentVoiceLanguage.value = card.voiceLanguage || 'zh'
      currentSubtitleLanguage.value = card.subtitleLanguage || 'zh'
      
      await startNewSession() // 创建新会话
    }
    
    // 更新语言设置
    const updateLanguageSettings = () => {
      console.log(`语言设置已更新: 语音=${currentVoiceLanguage.value}, 字幕=${currentSubtitleLanguage.value}`)
      // 这里���以添加任何需要在语言变更时执行的逻辑
    }

    const createMessageId = () => {
      if (typeof crypto !== 'undefined' && crypto.randomUUID) {
        return crypto.randomUUID()
      }
      return `msg-${Date.now()}-${Math.random().toString(16).slice(2)}`
    }

    const startNewSession = async () => {
      if (!selectedCharacter.value) return
      await endSession()
      try {
        const payload = {
          characterCardId: selectedCharacter.value.id,
          loadHistory: false, // 不加载历史记录
          createNew: true // 创建新会话
        }
        const response = await voiceChatAPI.createSession(payload)
        if (response?.code === 200) {
          session.value = response.data
          messages.value = [] // 清空消息列表
          
          // 添加欢迎消息
          if (session.value?.greetingMessage && session.value.greetingMessage.trim()) {
            messages.value.push({
              id: `${session.value.sessionId}-greeting`,
              role: 'assistant',
              text: session.value.greetingMessage,
              timestamp: Date.now(),
              audioSegments: []
            })
          }
          await connectStomp()
        } else {
          notification.error('创建会话失败: ' + (response?.message || '未知错误'))
        }
      } catch (error) {
        console.error('创建会话异常：', error)
        notification.error('创建会话失败，请稍后再试')
      }
    }

    const startSession = async () => {
      return startNewSession()
    }

    // 加载历史会话
    const startSessionWithHistory = async (sessionData) => {
      if (!sessionData || !sessionData.sessionId) return
      
      // 首先需要获取角色信息
      if (sessionData.cardId) {
        try {
          // 从角色列表中找到对应的角色，如果没有则获取
          let character = characterList.value.find(c => c.id === sessionData.cardId)
          if (!character) {
            // 如果角色列表中没有，先获取角色列表
            await fetchCharacters()
            character = characterList.value.find(c => c.id === sessionData.cardId)
          }
          
          if (character) {
            selectedCharacter.value = character
          }
        } catch (error) {
          console.error('获取角色信息失败:', error)
        }
      }

      await endSession()
      try {
        // 创建或恢复会话
        const payload = {
          characterCardId: sessionData.cardId,
          sessionId: sessionData.sessionId,
          loadHistory: true, // 加载历史记录
          createNew: false
        }
        const response = await voiceChatAPI.createSession(payload)
        if (response?.code === 200) {
          session.value = response.data
          // 根据情况加载历史：如果sessionData中有historyId则使用historyId加载，否则使用sessionId
          if (sessionData.historyId) {
            await loadHistoryById(sessionData.historyId)
          } else {
            await loadSessionHistory(sessionData.sessionId)
          }
          await connectStomp()
        } else {
          notification.error('恢复会话失败: ' + (response?.message || '未知错误'))
        }
      } catch (error) {
        console.error('恢复会话异常：', error)
        notification.error('恢复会话失败，请稍后再试')
      }
    }

    // 根据历史记录ID加载历史
    const loadHistoryById = async (historyId) => {
      historyLoading.value = true
      cleanupMessageAudios()
      userMessages.clear()
      assistantMessages.clear()
      try {
        const response = await voiceChatAPI.getHistoryById(historyId)
        if (response?.code === 200) {
          const history = Array.isArray(response.data) ? response.data : []
          const mapped = history
            .map(item => ({
              id: `${item.id || `history-${item.timestamp}`}`,
              role: item.role === 'ASSISTANT' ? 'assistant' : 'user',
              text: item.content,
              timestamp: parseTimestamp(item.timestamp),
              audioSegments: []
            }))
            .sort((a, b) => a.timestamp - b.timestamp)
          messages.value = mapped
        } else {
          messages.value = []
        }
      } catch (error) {
        console.error('加载历史记录失败', error)
        messages.value = []
      } finally {
        historyLoading.value = false
        await nextTick(() => {
          if (chatListRef.value) {
            chatListRef.value.scrollTop = chatListRef.value.scrollHeight
          }
        })
      }
    }

    const connectStomp = async () => {
      if (!session.value) return
      if (stompClient.value) {
        await disconnectStomp()
      }
      stompConnected.value = false

      const endpoint = session.value.websocketEndpoint || '/ws/voice-chat'
      const resolveWsUrl = () => {
        if (endpoint.startsWith('http')) {
          return endpoint.replace(/^http/, 'ws')
        }
        if (typeof window !== 'undefined') {
          return window.location.origin.replace(/^http/, 'ws') + endpoint
        }
        return endpoint
      }
      
      // 获取token用于WebSocket认证
      const token = storage.get('token')
      const connectHeaders = {}
      if (token) {
        connectHeaders.Authorization = `Bearer ${token}`
      }
      
      const client = new Client({
        webSocketFactory: () => {
          if (typeof window !== 'undefined' && 'WebSocket' in window) {
            try {
              const socket = new WebSocket(resolveWsUrl())
              socket.onerror = (event) => {
                console.warn('原生WebSocket连接出错，尝试回退到SockJS', event)
              }
              return socket
            } catch (error) {
              console.warn('原生WebSocket创建失败，使用SockJS', error)
            }
          }
          const sockJsUrl = endpoint.startsWith('http')
            ? endpoint
            : (typeof window !== 'undefined' ? `${window.location.origin}${endpoint}` : endpoint)
          return new SockJS(sockJsUrl)
        },
        connectHeaders: connectHeaders,
        reconnectDelay: 5000,
        debug: (msg) => {
          console.debug('[STOMP]', msg)
        }
      })

      client.onConnect = () => {
        console.info('STOMP连接成功')
        stompConnected.value = true
        client.subscribe(`/topic/voice/${session.value.sessionId}`, handleIncomingEvent)
      }

      client.onDisconnect = (frame) => {
        console.warn('STOMP连接断开', frame)
        stompConnected.value = false
      }

      client.onStompError = frame => {
        console.error('STOMP错误', frame)
        notification.error('实时通道异常，请重新进入会话')
      }

      client.onWebSocketClose = (event) => {
        console.warn('WebSocket连接关闭', event)
        stompConnected.value = false
      }

      client.onWebSocketError = (event) => {
        console.error('WebSocket错误', event)
      }

      stompClient.value = client
      client.activate()
    }

    const disconnectStomp = async () => {
      if (stompClient.value) {
        try {
          await stompClient.value.deactivate()
        } catch (error) {
          console.warn('关闭STOMP失败', error)
        }
        stompClient.value = null
        stompConnected.value = false
      }
    }

    const endSession = async () => {
      if (!session.value || sessionClosing.value) return
      sessionClosing.value = true
      try {
        await disconnectStomp()
        await voiceChatAPI.closeSession(session.value.sessionId)
      } catch (error) {
        console.warn('关闭会话失败', error)
      } finally {
        cleanupMessageAudios()
        clearAudioQueue()
        messages.value = []
        userMessages.clear()
        assistantMessages.clear()
          session.value = null
        sessionClosing.value = false
      }
    }

    const arrayBufferToBase64 = (buffer) => {
      let binary = ''
      const bytes = new Uint8Array(buffer)
      const chunkSize = 0x8000
      for (let i = 0; i < bytes.length; i += chunkSize) {
        const chunk = bytes.subarray(i, i + chunkSize)
        binary += String.fromCharCode.apply(null, chunk)
      }
      return btoa(binary)
    }

    const base64ToUint8Array = (base64) => {
      const binary = atob(base64)
      const len = binary.length
      const bytes = new Uint8Array(len)
      for (let i = 0; i < len; i++) {
        bytes[i] = binary.charCodeAt(i)
      }
      return bytes
    }

    const decodeAudioPayload = (data) => {
      if (!data) return null
      try {
        if (typeof data === 'string') {
          return base64ToUint8Array(data)
        }
        if (Array.isArray(data)) {
          return new Uint8Array(data)
        }
        if (typeof data === 'object') {
          if (data.type === 'Buffer' && Array.isArray(data.data)) {
            return new Uint8Array(data.data)
          }
          if (data.$binary?.base64) {
            return base64ToUint8Array(data.$binary.base64)
          }
        }
      } catch (error) {
        console.error('解码音频数据失败', error)
        return null
      }
      console.warn('未知的音频数据格式', data)
      return null
    }



    const toggleRecording = async () => {
      if (!isRecording.value) {
        await startRecording()
      } else {
        await stopRecording()
      }
    }

    const releaseMediaStream = () => {
      if (mediaStream.value) {
        mediaStream.value.getTracks().forEach(track => {
          try {
            track.stop()
          } catch (error) {
            console.warn('停止音频轨道失败', error)
          }
        })
        mediaStream.value = null
      }
    }

    const startRecording = async () => {
      if (!canRecord.value) {
        notification.warning('请先选择角色并等待会话建立')
        return
      }
      if (!navigator.mediaDevices?.getUserMedia) {
        notification.error('当前浏览器不支持音频录制')
        return
      }
      try {
        if (!mediaStream.value) {
          mediaStream.value = await navigator.mediaDevices.getUserMedia({ audio: true })
        }
        recordedChunks.value = []
        const options = { mimeType: 'audio/webm;codecs=opus' }
        if (typeof MediaRecorder.isTypeSupported === 'function' && !MediaRecorder.isTypeSupported(options.mimeType)) {
          delete options.mimeType
        }
        mediaRecorder.value = new MediaRecorder(mediaStream.value, options)
        mediaRecorder.value.ondataavailable = (event) => {
          if (event.data && event.data.size > 0) {
            recordedChunks.value.push(event.data)
          }
        }
        mediaRecorder.value.onerror = (event) => {
          console.error('录音出现错误', event.error)
          notification.error('录音失败，请重新尝试')
          stopRecording()
        }
        mediaRecorder.value.onstop = async () => {
          const chunks = recordedChunks.value.slice()
          const recorderMimeType = mediaRecorder.value?.mimeType
          const chunkMimeType = chunks[0]?.type
          const effectiveMimeType = recorderMimeType || chunkMimeType || 'audio/webm'
          recordedChunks.value = []
          try {
            if (!chunks.length) {
              isRecording.value = false
              return
            }
            const blob = new Blob(chunks, { type: effectiveMimeType })
            await sendVoiceBlob(blob, effectiveMimeType)
          } catch (error) {
            console.error('处理录音数据失败', error)
            notification.error('处理录音数据失败，请重试')
          } finally {
            releaseMediaStream()
            mediaRecorder.value = null
            isRecording.value = false
          }
        }
        mediaRecorder.value.start()
        isRecording.value = true
        recordElapsed.value = 0
        recordTimer.value = setInterval(() => {
          recordElapsed.value += 1
          if (recordElapsed.value >= 30) {
            stopRecording()
          }
        }, 1000)
      } catch (error) {
        console.error('录音失败', error)
        notification.error('无法访问麦克风，请检查权限设置')
        isRecording.value = false
        releaseMediaStream()
        mediaRecorder.value = null
      }
    }

    const stopRecording = async () => {
      if (!isRecording.value || !mediaRecorder.value) return
      clearInterval(recordTimer.value)
      recordTimer.value = null
      try {
        mediaRecorder.value.stop()
      } catch (error) {
        console.error('停止录音失败', error)
        releaseMediaStream()
        mediaRecorder.value = null
        isRecording.value = false
      }
    }

    const sendVoiceBlob = async (blob, mimeType) => {
      if (!stompClient.value || !stompConnected.value || !session.value) {
        notification.error('实时通道未连接，无法发送语音')
        return
      }
      const arrayBuffer = await blob.arrayBuffer()
      const base64Audio = arrayBufferToBase64(arrayBuffer)
      const messageId = createMessageId()
      const userMessage = {
        id: messageId,
        role: 'user',
        text: '识别中...',
        status: 'pending',
        timestamp: Date.now(),
        audioSegments: []
      }
      messages.value.push(userMessage)
      userMessages.set(messageId, userMessage)
      activeMessageId.value = messageId
      isProcessing.value = true

      try {
        stompClient.value.publish({
          destination: `/app/voice/${session.value.sessionId}`,
          headers: {
            'content-type': 'application/json'
          },
          body: JSON.stringify({
            messageId,
            audioFormat: extractFormat(mimeType),
            audioData: base64Audio,
            voiceLanguage: currentVoiceLanguage.value,
            subtitleLanguage: currentSubtitleLanguage.value,
            timestamp: Date.now()
          })
        })
        console.debug('已发送语音消息', {
          messageId,
          size: base64Audio.length,
          destination: `/app/voice/${session.value.sessionId}`
        })
      } catch (error) {
        console.error('发送语音数据失败', error)
        userMessage.text = '语音发送失败，请重试'
        userMessage.status = 'error'
        activeMessageId.value = null
        isProcessing.value = false
        notification.error('语音发送失败，请检查网络后重试')
        return
      }

      await nextTick(scrollToBottom)
    }

    const extractFormat = (mimeType) => {
      if (!mimeType) return 'webm'
      const match = mimeType.match(/audio\/(.*?)(;|$)/)
      return match ? match[1] : 'webm'
    }

    const handleIncomingEvent = (frame) => {
      try {
        const message = JSON.parse(frame.body)
        const { type, messageId, payload } = message
        
        console.debug('收到WebSocket消息:', { type, messageId, payload })
        console.log('收到WebSocket消息:', { type, messageId, payload })
        
        switch (type) {
          case 'PROCESSING_STARTED':
            isProcessing.value = true
            activeMessageId.value = messageId
            break
          case 'AI_TEXT_SEGMENT':
            handleAssistantSegment(message)
            break
          case 'SUBTITLE_STREAM':
            handleSubtitleStream(message)
            break
          case 'AUDIO_CHUNK':
            handleAudioChunk(message)
            break
          case 'ASR_RESULT':
            handleAsrResult(message)
            break
          case 'METHOD_EXECUTION':
            handleMethodExecution(message)
            break
          case 'ROUND_COMPLETED':
            finalizeAssistant(message)
            break
          case 'PROCESSING_COMPLETED':
            isProcessing.value = false
            activeMessageId.value = null
            break
          case 'ERROR':
            handleError(message)
            break
          case 'TITLE_UPDATE':
            handleTitleUpdate(message)
            break
          default:
            console.warn('未知的消息类型:', type)
            break
        }
      } catch (error) {
        console.error('解析实时消息失败', error)
      }
    }

    const ensureAssistantMessage = (messageId) => {
      if (!assistantMessages.has(messageId)) {
        // 获取对应的用户消息时间戳，确保助手消息在用户消息之后
        const userMessage = userMessages.get(messageId)
        const baseTimestamp = userMessage ? userMessage.timestamp : Date.now()
        
        const newMessage = {
          id: `${messageId}-assistant`,
          role: 'assistant',
          text: '',
          status: 'streaming',
          timestamp: baseTimestamp + 1, // 确保在用户消息之后
          audioSegments: [],
          finalSegmentOrder: null,
          completedByFallback: false
        }
        assistantMessages.set(messageId, newMessage)
        messages.value.push(newMessage)
      }
      return assistantMessages.get(messageId)
    }

    const handleAssistantSegment = (message) => {
      const { messageId, payload } = message
      console.debug('处理助手文本分段:', { messageId, payload })
      if (!messageId || !payload) return
      
      const assistantMessage = ensureAssistantMessage(messageId)
      const segmentOrder = Number(payload.segmentOrder ?? 0)
      const segments = assistantMessage.segments || {}
      segments[segmentOrder] = payload.text
      assistantMessage.segments = segments
      
      // 使用打字机效果显示新文本
      const fullText = Object.keys(segments)
        .sort((a, b) => Number(a) - Number(b))
        .map(key => segments[key])
        .join(' ')
      
      addTypingEffect(assistantMessage, payload.text)
      assistantMessage.timestamp = Date.now()
      
      if (payload.isFinal) {
        console.debug('设置最终文本分段:', { messageId, segmentOrder })
        assistantMessage.finalSegmentOrder = segmentOrder
        console.debug('最终分段顺序已设置:', assistantMessage.finalSegmentOrder)
      }
      
      nextTick(scrollToBottom)
    }

    const handleAudioChunk = (message) => {
      const { messageId, payload } = message
      console.debug('处理音频数据:', { 
        messageId, 
        audioFormat: payload?.audioFormat,
        audioDataSize: payload?.audioData?.length
      })
      console.log('处理音频数据:', { 
        messageId, 
        audioFormat: payload?.audioFormat,
        audioDataSize: payload?.audioData?.length
      })
      if (!messageId || !payload || !payload.audioData) return
      
      ensureAssistantMessage(messageId)
      
      // 直接处理完整音频数据（MP3格式）
      const audioData = decodeAudioPayload(payload.audioData)
      if (!audioData) {
        console.warn('音频数据解码失败')
        return
      }
      
      console.log('创建完整音频 Blob:', {
        format: payload.audioFormat || 'mp3',
        size: audioData.length,
        segmentOrder: payload.segmentOrder
      })
      
      // 直接创建音频 blob，不再合并分片
      const mimeType = getAudioMimeType(payload.audioFormat || 'mp3')
      const blob = new Blob([audioData], { type: mimeType })
      const url = URL.createObjectURL(blob)
      
      const segmentInfo = {
        url,
        segmentOrder: payload.segmentOrder || 0,
        messageId
      }
      
      enqueueAudio(segmentInfo)
      
      const assistantMessage = assistantMessages.get(messageId)
      if (assistantMessage) {
        assistantMessage.audioSegments = assistantMessage.audioSegments || []
        assistantMessage.audioSegments.push(segmentInfo)
        assistantMessage.audioSegments.sort((a, b) => a.segmentOrder - b.segmentOrder)
      }
      
      // 如果是最后一个音频段，完成轮次
      if (payload.isLast) {
        maybeCompleteRound(messageId, payload.segmentOrder || 0)
      }
    }

    // 获取音频MIME类型
    const getAudioMimeType = (format) => {
      const normalizedFormat = (format || 'mp3').toLowerCase()
      switch (normalizedFormat) {
        case 'mp3':
          return 'audio/mpeg'
        case 'wav':
          return 'audio/wav'
        case 'ogg':
          return 'audio/ogg'
        case 'aac':
          return 'audio/aac'
        default:
          return 'audio/mpeg' // 默认MP3
      }
    }

    const maybeCompleteRound = (messageId, segmentOrder) => {
      console.debug('检查轮次完成:', { messageId, segmentOrder, activeMessageId: activeMessageId.value, isProcessing: isProcessing.value })
      
      const assistantMessage = assistantMessages.get(messageId)
      if (!assistantMessage) {
        console.debug('未找到助手消息，强制完成轮次')
        // 如果找不到消息，但当前正在处理这个messageId，强制完成
        if (activeMessageId.value === messageId) {
          activeMessageId.value = null
          isProcessing.value = false
        }
        return
      }
      
      // 如果已经完成，不需要重复处理
      if (assistantMessage.status === 'done') {
        console.debug('消息已完成，检查是否需要重置处理状态')
        if (activeMessageId.value === messageId) {
          activeMessageId.value = null
          isProcessing.value = false
        }
        return
      }
      
      const expectedOrder = assistantMessage.finalSegmentOrder
      
      // 如果没有设置最终分段顺序，但这是一个音频分段完成，等待一段时间后强制完成
      if (typeof expectedOrder !== 'number') {
        console.debug('没有最终分段顺序，延迟完成轮次')
        setTimeout(() => {
          const currentMessage = assistantMessages.get(messageId)
          if (currentMessage && currentMessage.status !== 'done' && activeMessageId.value === messageId) {
            console.debug('延迟完成轮次')
            currentMessage.status = 'done'
            currentMessage.timestamp = Date.now()
            currentMessage.completedByFallback = true
            
            const userMessage = userMessages.get(messageId)
            if (userMessage && userMessage.status !== 'error') {
              userMessage.status = 'done'
              if (!userMessage.text || userMessage.text === '识别中...') {
                userMessage.text = '语音消息'
              }
            }
            
            if (activeMessageId.value === messageId) {
              activeMessageId.value = null
            }
            isProcessing.value = false
            nextTick(scrollToBottom)
          }
        }, 1000) // 1秒后强制完成
        return
      }
      
      // 如果当前分段不是期望的最终分段，继续等待
      if (segmentOrder !== expectedOrder) {
        console.debug('当前分段不是最终分段，继续等待', { current: segmentOrder, expected: expectedOrder })
        return
      }

      console.debug('完成轮次')
      assistantMessage.status = 'done'
      assistantMessage.timestamp = Date.now()
      assistantMessage.completedByFallback = true

      const userMessage = userMessages.get(messageId)
      if (userMessage) {
        if (userMessage.status !== 'error') {
          userMessage.status = 'done'
          if (!userMessage.text || userMessage.text === '识别中...') {
            userMessage.text = '语音消息'
          }
        }
      }

      if (activeMessageId.value === messageId) {
        activeMessageId.value = null
      }
      isProcessing.value = false
      nextTick(scrollToBottom)
    }

    const enqueueAudio = (segment) => {
      audioQueue.value.push(segment)
      if (!currentAudio.value) {
        playNextAudio()
      }
    }

    const playNextAudio = () => {
      if (!audioQueue.value.length) {
        currentAudio.value = null
        return
      }
      const segment = audioQueue.value.shift()
      console.log('播放音频片段:', { segmentOrder: segment.segmentOrder, messageId: segment.messageId })
      
      const audio = new Audio(segment.url)
      currentAudio.value = { audio, segment }
      
      audio.onloadeddata = () => {
        console.log('音频数据加载完成:', { duration: audio.duration, segmentOrder: segment.segmentOrder })
      }
      
      audio.onended = () => {
        console.log('音频播放结束:', { segmentOrder: segment.segmentOrder })
        currentAudio.value = null
        playNextAudio()
      }
      
      audio.onerror = (error) => {
        console.error('音频播放错误:', { segmentOrder: segment.segmentOrder, error })
        currentAudio.value = null
        playNextAudio()
      }
      
      audio.play().catch(error => {
        console.warn('自动播放失败', { segmentOrder: segment.segmentOrder, error })
        currentAudio.value = null
        playNextAudio()
      })
    }

    const playSegment = (segment) => {
      if (!segment?.url) return
      const audio = new Audio(segment.url)
      audio.play().catch(error => {
        console.warn('手动播放失败', error)
      })
    }

    const handleAsrResult = (message) => {
      const { messageId, payload } = message
      if (!messageId || !payload) return
      
      const userMessage = userMessages.get(messageId)
      if (userMessage && payload.text) {
        // 使用打字机效果显示用户转写结果
        addTypingEffect(userMessage, payload.text)
        userMessage.status = 'done'
      }
    }

    const finalizeAssistant = (message) => {
      const { messageId, payload } = message
      if (!messageId) return
      
      const assistantMessage = assistantMessages.get(messageId)
      if (assistantMessage) {
        assistantMessage.text = payload?.text || assistantMessage.text
        assistantMessage.status = 'done'
        assistantMessage.completedByFallback = false
      }
      isProcessing.value = false
      activeMessageId.value = null
    }

    const handleSubtitleStream = (message) => {
      const { messageId, payload } = message
      console.debug('处理字幕流:', { messageId, payload })
      if (!messageId || !payload) return
      
      const assistantMessage = ensureAssistantMessage(messageId)
      
      switch (payload.action) {
        case 'start':
          assistantMessage.subtitleStreaming = true
          assistantMessage.subtitleLanguage = payload.language
          assistantMessage.subtitleBuffer = ''
          break
        case 'segment':
          if (assistantMessage.subtitleStreaming) {
            // 使用打字机效果逐渐显示字幕内容
            addTypingEffect(assistantMessage, payload.text || '')
          }
          break
        case 'end':
          assistantMessage.subtitleStreaming = false
          if (payload.fullText) {
            assistantMessage.text = payload.processedText || payload.fullText
          }
          break
      }
    }

    const handleMethodExecution = (message) => {
      const { messageId, payload } = message
      console.debug('处理方法执行:', { messageId, payload })
      if (!messageId || !payload) return
      
      // 显示方法执行结果或错误信息
      const notification_type = payload.action === 'method_error' ? 'warning' : 'info'
      const content = payload.result?.message || payload.error || '方法执行完成'
      
      if (notification_type === 'warning') {
        notification.warning(content)
      } else {
        notification.info(content)
      }
    }

    // 打字机效果实现
    const addTypingEffect = (message, newText) => {
      if (!newText) return
      
      // 如果已经有打字动画在进行，先清除
      if (message.typingTimer) {
        clearInterval(message.typingTimer)
      }
      
      message.isTyping = true
      message.subtitleBuffer = message.subtitleBuffer || ''
      const targetText = message.subtitleBuffer + newText
      let currentIndex = message.subtitleBuffer.length
      
      message.typingTimer = setInterval(() => {
        if (currentIndex < targetText.length) {
          message.text = targetText.substring(0, currentIndex + 1)
          currentIndex++
        } else {
          clearInterval(message.typingTimer)
          message.typingTimer = null
          message.isTyping = false
          message.subtitleBuffer = targetText
        }
      }, 50) // 50ms 间隔，可调整打字速度
    }

    const handleError = (message) => {
      const { messageId, payload } = message
      if (!messageId) return
      
      const userMessage = userMessages.get(messageId)
      if (userMessage) {
        userMessage.text = payload?.error || '语音处理失败'
        userMessage.status = 'error'
      }
      isProcessing.value = false
      activeMessageId.value = null
      notification.error(payload?.error || '语音处理失败')
    }

    // 处理标题更新
    const handleTitleUpdate = (message) => {
      const { payload } = message
      if (payload?.title) {
        animateTitle(payload.title)
      }
    }

    // 标题打字机动画效果
    const animateTitle = (targetTitle) => {
      if (titleAnimationTimer.value) {
        clearInterval(titleAnimationTimer.value)
        titleAnimationTimer.value = null
      }

      titleTyping.value = true
      chatTitle.value = ''
      let currentIndex = 0

      titleAnimationTimer.value = setInterval(() => {
        if (currentIndex < targetTitle.length) {
          chatTitle.value += targetTitle[currentIndex]
          currentIndex++
        } else {
          clearInterval(titleAnimationTimer.value)
          titleAnimationTimer.value = null
          titleTyping.value = false
        }
      }, 80) // 每80ms显示一个字符
    }

    const scrollToBottom = () => {
      if (chatListRef.value) {
        chatListRef.value.scrollTop = chatListRef.value.scrollHeight
      }
    }

    const clearAudioQueue = () => {
      if (currentAudio.value?.audio) {
        currentAudio.value.audio.pause()
      }
      currentAudio.value = null
      audioQueue.value = []
    }

    const cleanupMessageAudios = () => {
      assistantMessages.forEach(message => {
        if (Array.isArray(message.audioSegments)) {
          message.audioSegments.forEach(segment => {
            if (segment?.url) {
              URL.revokeObjectURL(segment.url)
            }
          })
          message.audioSegments = []
        }
      })
    }

    watch(messages, () => nextTick(scrollToBottom))

    // 监听来自Dashboard的会话选择
    if (selectedSession && selectedSession.sessionData) {
      watch(selectedSession.sessionData, async (newSessionData) => {
        if (newSessionData && newSessionData.loadHistory && newSessionData.sessionId) {
          console.log('收到历史会话加载请求:', newSessionData)
          await startSessionWithHistory(newSessionData)
          // 清除标记，避免重复加载
          newSessionData.loadHistory = false
        }
      }, { immediate: true })
    }

    onMounted(() => {
      fetchCharacters(true)
    })

    onBeforeUnmount(async () => {
      clearInterval(recordTimer.value)
      if (titleAnimationTimer.value) {
        clearInterval(titleAnimationTimer.value)
        titleAnimationTimer.value = null
      }
      if (mediaRecorder.value && mediaRecorder.value.state !== 'inactive') {
        mediaRecorder.value.stop()
      }
      releaseMediaStream()
      await endSession()
      cleanupMessageAudios()
      clearAudioQueue()
    })

    return {
      // 角色卡相关
      searchKeyword,
      characterList,
      filteredCharacters,
      loadingCharacters,
      selectedCharacter,
      selectCharacter,
      fetchCharacters,

      // 会话相关
      session,
      sessionClosing,
      stompConnected,
      messages,
      historyLoading,
      
      // 录音和播放
      isRecording,
      recordElapsed,
      isProcessing,
      canRecord,
      toggleRecording,
      playSegment,
      
      // 其他
      formatTime,
      chatListRef,
      endSession,
      
      // 标题相关
      chatTitle,
      titleTyping,
      
      // 语言设置相关
      currentVoiceLanguage,
      currentSubtitleLanguage,
      updateLanguageSettings
    }
  }
}
</script>

<style scoped lang="scss">
.round-voice-chat {
  display: flex;
  height: 100%;
  background: var(--background-primary);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12);
}

.history-panel {
  width: 280px;
  background: var(--background-secondary);
  border-right: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
  padding: 1rem;
  gap: 1rem;
  transition: width 0.3s ease;

  &.collapsed {
    width: 60px;
    padding: 1rem 0.5rem;
  }

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1rem;

    h3 {
      margin: 0;
      font-size: 1rem;
      color: var(--text-primary);
    }

    .header-controls {
      display: flex;
      gap: 0.5rem;
    }

    .toggle-btn,
    .refresh-btn {
      padding: 0.25rem 0.5rem;
      border: 1px solid var(--border-light);
      border-radius: 4px;
      background: var(--background-tertiary);
      color: var(--text-secondary);
      cursor: pointer;
      font-size: 0.8rem;
      transition: background 0.2s ease;

      &:hover {
        background: var(--background-hover);
        color: var(--text-primary);
      }

      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    }
  }

  .session-filter {
    select {
      width: 100%;
      padding: 0.5rem;
      border: 1px solid var(--border-light);
      border-radius: 6px;
      background: var(--background-tertiary);
      color: var(--text-primary);
      font-size: 0.9rem;
    }
  }

  .session-list {
    flex: 1;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;

    &.loading {
      opacity: 0.7;
    }

    .loading-hint,
    .empty-hint {
      text-align: center;
      color: var(--text-secondary);
      padding: 2rem 1rem;
      font-size: 0.9rem;
    }
  }

  .session-item {
    padding: 0.75rem;
    border-radius: 8px;
    background: var(--background-tertiary);
    border: 1px solid transparent;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      background: var(--background-hover);
      border-color: var(--border-light);
    }

    &.active {
      background: var(--primary-color);
      color: white;
      border-color: var(--primary-color);
    }

    .session-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 0.5rem;

      h4 {
        margin: 0;
        font-size: 0.9rem;
        font-weight: 500;
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .character-name {
        font-size: 0.75rem;
        opacity: 0.8;
        margin-left: 0.5rem;
        flex-shrink: 0;
      }
    }

    .session-preview {
      margin: 0 0 0.5rem 0;
      font-size: 0.8rem;
      opacity: 0.8;
      line-height: 1.4;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .session-meta {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 0.75rem;
      opacity: 0.7;

      .message-count {
        flex-shrink: 0;
      }

      .last-time {
        font-size: 0.7rem;
        opacity: 0.6;
      }
    }
  }
}

.character-panel {
  width: 320px;
  background: var(--background-secondary);
  border-right: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
  padding: 1.25rem;
  gap: 1rem;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  h3 {
    margin: 0;
    font-size: 1.1rem;
    color: var(--text-primary);
  }
}

.refresh-btn,
.search-btn {
  padding: 0.35rem 0.75rem;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  background: var(--background-tertiary);
  color: var(--text-primary);
  cursor: pointer;
  transition: background 0.2s ease;

  &:hover {
    background: rgba(99, 102, 241, 0.12);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.search-box {
  display: flex;
  gap: 0.5rem;
  input {
    flex: 1;
    padding: 0.5rem 0.75rem;
    border-radius: 6px;
    border: 1px solid var(--border-light);
    background: var(--background-primary);
    color: var(--text-primary);
  }
}

.character-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;

  &.loading {
    justify-content: center;
    align-items: center;
    color: var(--text-secondary);
  }
}

.character-item {
  background: var(--background-primary);
  border-radius: 12px;
  padding: 0.9rem;
  border: 1px solid transparent;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease;

  &:hover {
    border-color: rgba(99, 102, 241, 0.4);
    transform: translateY(-2px);
  }

  &.active {
    border-color: var(--primary-color);
    box-shadow: 0 6px 16px rgba(99, 102, 241, 0.2);
  }

  .item-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    h4 {
      margin: 0;
      font-size: 1rem;
      color: var(--text-primary);
    }
    .tag {
      font-size: 0.75rem;
      padding: 0.1rem 0.4rem;
      background: rgba(99, 102, 241, 0.18);
      color: var(--primary-color);
      border-radius: 999px;
    }
  }

  .item-desc {
    margin: 0.25rem 0;
    color: var(--text-secondary);
    font-size: 0.85rem;
  }

  .item-meta {
    display: flex;
    justify-content: space-between;
    font-size: 0.8rem;
    color: var(--text-tertiary);
  }
}

.empty-hint,
.loading-hint,
.placeholder {
  text-align: center;
  color: var(--text-secondary);
  padding: 1rem;
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--background-primary);
}

.chat-header {
  padding: 1.5rem;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;

  h2 {
    margin: 0 0 0.25rem;
    font-size: 1.4rem;
    color: var(--text-primary);
  }

  p {
    margin: 0;
    color: var(--text-secondary);
  }

  // 标题显示区域
  .chat-title-area {
    margin-top: 0.5rem;
    
    .chat-title {
      display: inline-block;
      font-size: 1rem;
      color: var(--primary-color);
      font-weight: 500;
      
      &.typing {
        .typing-cursor {
          animation: blink 1s infinite;
        }
      }
      
      .typing-cursor {
        color: var(--primary-color);
        font-weight: normal;
      }
    }
  }
}

.session-info {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  align-items: flex-end;

  .status-badge {
    padding: 0.25rem 0.75rem;
    border-radius: 999px;
    font-size: 0.8rem;
    background: rgba(99, 102, 241, 0.18);
    color: var(--primary-color);

    &.active {
      background: rgba(34, 197, 94, 0.18);
      color: #22c55e;
    }
  }

  .session-id {
    font-size: 0.75rem;
    color: var(--text-tertiary);
  }
}

.chat-body {
  flex: 1;
  overflow: hidden;
  padding: 1.5rem;
  display: flex;
}

.conversation {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.message {
  display: flex;
  flex-direction: column;
  max-width: 80%;

  &.assistant {
    align-self: flex-start;
  }

  &.user {
    align-self: flex-end;
  }

  .meta {
    display: flex;
    justify-content: space-between;
    font-size: 0.75rem;
    color: var(--text-tertiary);
    margin-bottom: 0.25rem;
  }

  .bubble {
    padding: 0.9rem 1rem;
    border-radius: 14px;
    background: var(--background-secondary);
    color: var(--text-primary);
    position: relative;

    .error-text {
      color: #f87171;
    }

    .pending-indicator {
      margin-top: 0.5rem;
      font-size: 0.75rem;
      color: var(--primary-color);
    }

    /* 打字机效果样式 */
    &.typing {
      position: relative;
      
      &::after {
        content: '|';
        color: var(--primary-color);
        animation: blink 1s infinite;
        font-weight: bold;
        margin-left: 2px;
      }
    }
  }

  &.user .bubble {
    background: rgba(99, 102, 241, 0.15);
  }
}

.audio-segments {
  margin-top: 0.5rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;

  .segment-btn {
    padding: 0.25rem 0.5rem;
    font-size: 0.75rem;
    border-radius: 6px;
    border: 1px solid var(--border-light);
    background: var(--background-primary);
    cursor: pointer;
    transition: background 0.2s ease;

    &:hover {
      background: rgba(99, 102, 241, 0.2);
    }
  }
}

.chat-controls {
  padding: 1.25rem 1.5rem;
  border-top: 1px solid var(--border-light);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-group {
  font-size: 0.85rem;
  color: var(--text-secondary);

  .recording-indicator {
    color: #ef4444;
  }

  .processing-indicator {
    color: var(--primary-color);
  }
}

.control-buttons {
  display: flex;
  gap: 0.75rem;

  button {
    border: none;
    border-radius: 999px;
    padding: 0.6rem 1.4rem;
    font-size: 0.95rem;
    cursor: pointer;
    transition: transform 0.2s ease, box-shadow 0.2s ease;

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }

  .mic-btn {
    background: var(--primary-color);
    color: #fff;
    box-shadow: 0 10px 24px rgba(99, 102, 241, 0.25);

    &.active {
      background: #ef4444;
      box-shadow: 0 10px 24px rgba(239, 68, 68, 0.3);
    }
  }

  .stop-btn {
    background: var(--background-secondary);
    color: var(--text-primary);
  }
}

@media (max-width: 1100px) {
  .round-voice-chat {
    flex-direction: column;
  }

  .character-panel {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--border-light);
  }
}

/* 语言设置样式 */
.language-settings {
  display: flex;
  gap: 1rem;
  margin: 0.75rem 0;
  padding: 0.75rem;
  background: var(--background-secondary);
  border-radius: 0.5rem;
  border: 1px solid var(--border-light);
}

.language-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.language-label {
  font-size: 0.875rem;
  color: var(--text-secondary);
  font-weight: 500;
  white-space: nowrap;
}

.language-select {
  padding: 0.375rem 0.5rem;
  border: 1px solid var(--border-color);
  border-radius: 0.375rem;
  background: var(--surface-color);
  color: var(--text-primary);
  font-size: 0.875rem;
  min-width: 80px;
  
  &:focus {
    outline: none;
    border-color: var(--primary-color);
  }
}

/* 闪烁光标动画 */
@keyframes blink {
  0%, 50% {
    opacity: 1;
  }
  51%, 100% {
    opacity: 0;
  }
}
</style>
