<template>
  <div class="voice-chat-page">
    <aside class="character-panel">
      <div class="panel-header">
        <h2>角色卡</h2>
        <button class="refresh-btn" :disabled="loadingCharacters" @click="loadCharacters">
          <span v-if="loadingCharacters" class="spinner" />
          <span v-else>刷新</span>
        </button>
      </div>
      <div class="character-list" v-if="characters.length">
        <button
          v-for="card in characters"
          :key="card.id"
          type="button"
          :class="['character-item', { active: selectedCharacter && selectedCharacter.id === card.id }]"
          @click="selectCharacter(card)"
        >
          <div class="name">{{ card.name }}</div>
          <div class="description" v-if="card.shortDescription">{{ card.shortDescription }}</div>
        </button>
      </div>
      <div v-else class="empty-list">
        <p>暂无公开角色卡，请先创建或分享角色。</p>
      </div>
    </aside>

    <section class="conversation-panel">
      <header class="conversation-header">
        <div class="title-area">
          <h1 v-if="selectedCharacter">{{ selectedCharacter.name }}</h1>
          <h1 v-else>选择角色开始语音对话</h1>
          <p v-if="selectedCharacter && selectedCharacter.shortDescription" class="subtitle">
            {{ selectedCharacter.shortDescription }}
          </p>
        </div>
        <div class="status-area">
          <div :class="['status-indicator', stompConnected ? 'online' : 'offline']">
            <span class="dot" />
            <span>{{ stompConnected ? '实时连接已就绪' : '未连接' }}</span>
          </div>
          <div v-if="isProcessing" class="status-indicator processing">
            <span class="spinner" />
            <span>AI 正在思考...</span>
          </div>
        </div>
      </header>

      <div class="history" ref="historyRef">
        <div
          v-for="msg in conversation"
          :key="msg.id"
          :class="['message', msg.role, { 'is-history': msg.isHistory }]"
        >
          <div class="meta">
            <span class="role">{{ msg.role === 'user' ? '我' : selectedCharacter?.name || 'AI' }}</span>
            <span class="time">{{ formatTime(msg.createdAt) }}</span>
            <span v-if="msg.status && !msg.isHistory" class="status-tag">{{ statusLabel[msg.status] || '' }}</span>
          </div>
          <div class="bubble">
            <p v-if="msg.text" class="text">{{ msg.text }}</p>
            <div v-if="msg.partialText && msg.status === 'responding'" class="streaming-text">{{ msg.partialText }}</div>
            <audio v-if="msg.audioUrl" :src="msg.audioUrl" controls preload="metadata" />
            <div v-if="msg.audioSegments && msg.audioSegments.length" class="segment-audio">
              <div
                v-for="segment in msg.audioSegments"
                :key="segment.segmentOrder"
                class="segment-item"
              >
                <span class="segment-label">片段 {{ segment.segmentOrder + 1 }}</span>
                <audio :src="segment.audioUrl" controls preload="metadata" />
              </div>
            </div>
          </div>
        </div>
      </div>

      <footer class="recorder">
        <div class="recorder-status">
          <span v-if="!sessionReady" class="hint">请选择角色并建立会话以开始对话</span>
          <span v-else-if="isRecording" class="recording">
            <span class="dot" />
            <span>录音中... {{ Math.round(recordingDuration / 1000) }}s</span>
            <button type="button" class="secondary-btn" @click="cancelRecording">取消</button>
          </span>
          <span v-else-if="isProcessing" class="hint">等待AI回复中...</span>
          <span v-else class="hint">点击下方按钮开始录音</span>
        </div>
        <div class="recorder-controls">
          <button
            type="button"
            class="record-btn"
            :class="{ recording: isRecording, disabled: !sessionReady || !stompConnected }"
            :disabled="!sessionReady || !stompConnected"
            @click="toggleRecording"
          >
            <span class="icon" />
            <span>{{ isRecording ? '停止录音' : '开始录音' }}</span>
          </button>
          <label class="autoplay-toggle">
            <input type="checkbox" v-model="autoPlay" />
            <span>自动播放AI语音</span>
          </label>
        </div>
      </footer>
    </section>
  </div>
</template>

<script>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { characterCardAPI, voiceChatAPI } from '@/api'
import { notification } from '@/utils/notification'
import { storage, format } from '@/utils'
import { blobToBase64, base64ToUint8Array, uint8ArrayToBlob } from '@/utils/audio'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const SUPPORTED_MIME_TYPES = [
  'audio/webm;codecs=opus',
  'audio/webm',
  'audio/ogg;codecs=opus',
  'audio/ogg'
]

const HISTORY_LIMIT = 20

export default {
  name: 'VoiceChatView',
  setup() {
    const characters = ref([])
    const loadingCharacters = ref(false)
    const selectedCharacter = ref(null)
    const sessionInfo = ref(null)

    const conversation = reactive([])
    const historyRef = ref(null)

    const stompClient = ref(null)
    const stompConnected = ref(false)
    const subscription = ref(null)

    const processingCounter = ref(0)
    const isProcessing = computed(() => processingCounter.value > 0)

    const mediaStream = ref(null)
    const mediaRecorder = ref(null)
    const isRecording = ref(false)
    const recordingChunks = ref([])
    const recordingStartTime = ref(null)
    const recordingDuration = ref(0)
    let recordingTimer = null
    const recorderMimeType = ref('audio/webm')
    const currentRecordingMessageId = ref(null)
    const discardRecording = ref(false)

    const autoPlay = ref(true)
    const playbackQueue = []
    let currentAudio = null

    const pendingUserMessages = new Map()
    const aiMessages = new Map()
    const segmentBuffers = new Map()
    const createdObjectUrls = new Set()

    const statusLabel = {
      pending: '等待识别',
      transcribing: '语音识别中',
      transcribed: '识别完成',
      responding: '回复生成中',
      done: '完成'
    }

    const sessionReady = computed(() => !!sessionInfo.value)

    const formatTime = (date) => {
      if (!date) return ''
      return format.date(date, 'HH:mm:ss')
    }

    const scrollToBottom = () => {
      nextTick(() => {
        if (historyRef.value) {
          historyRef.value.scrollTop = historyRef.value.scrollHeight
        }
      })
    }

    watch(() => conversation.length, scrollToBottom)
    watch(conversation, scrollToBottom, { deep: true })
    watch(autoPlay, (value) => {
      if (value) {
        playNext()
      } else if (currentAudio) {
        currentAudio.pause()
        currentAudio = null
      }
    })

    const loadCharacters = async () => {
      try {
        loadingCharacters.value = true
        const response = await characterCardAPI.getPublicCards({ page: 0, size: 30 })
        characters.value = response.data?.content || response.data || []
      } catch (error) {
        console.error('加载角色卡失败', error)
        notification.error(error.message || '加载角色卡失败')
      } finally {
        loadingCharacters.value = false
      }
    }

    const selectCharacter = async (card) => {
      if (selectedCharacter.value && selectedCharacter.value.id === card.id) {
        return
      }
      selectedCharacter.value = card
      await startConversation(card)
    }

    const startConversation = async (card) => {
      await endActiveSession()
      resetConversation()
      disconnectStomp()
      try {
        const payload = {
          characterCardId: card.id,
          loadHistory: true,
          historyLimit: HISTORY_LIMIT
        }
        const response = await voiceChatAPI.createSession(payload)
        sessionInfo.value = response.data
        stompConnected.value = false

        await loadHistory(card.id)

        if (sessionInfo.value?.greetingMessage) {
          conversation.push({
            id: `greeting-${sessionInfo.value.sessionId}`,
            messageId: `greeting-${sessionInfo.value.sessionId}`,
            role: 'assistant',
            text: sessionInfo.value.greetingMessage,
            createdAt: new Date(),
            status: 'done',
            isHistory: false
          })
        }

        connectStomp()
      } catch (error) {
        console.error('创建会话失败', error)
        notification.error(error.message || '创建语音会话失败')
        sessionInfo.value = null
      }
    }

    const endActiveSession = async () => {
      if (sessionInfo.value?.sessionId) {
        try {
          await voiceChatAPI.closeSession(sessionInfo.value.sessionId)
        } catch (error) {
          console.warn('关闭会话失败', error)
        }
      }
      sessionInfo.value = null
    }

    const resetConversation = () => {
      revokeObjectUrls()
      conversation.splice(0, conversation.length)
      pendingUserMessages.clear()
      aiMessages.clear()
      segmentBuffers.clear()
      clearPlaybackQueue()
    }

    const revokeObjectUrls = () => {
      createdObjectUrls.forEach((url) => URL.revokeObjectURL(url))
      createdObjectUrls.clear()
    }

    const loadHistory = async (cardId) => {
      try {
        const res = await voiceChatAPI.fetchHistory(cardId, HISTORY_LIMIT)
        const items = res.data || []
        items
          .sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))
          .forEach((item) => {
            conversation.push({
              id: `history-${item.id}`,
              messageId: `history-${item.id}`,
              role: item.role === 'USER' ? 'user' : 'assistant',
              text: item.content,
              createdAt: item.timestamp ? new Date(item.timestamp) : new Date(),
              status: 'done',
              isHistory: true
            })
          })
      } catch (error) {
        console.warn('加载历史失败', error)
      }
    }

    const connectStomp = () => {
      if (!sessionInfo.value) return

      const endpoint = sessionInfo.value.websocketEndpoint || '/ws/voice-chat'
      const destination = sessionInfo.value.subscriptionDestination || `/topic/voice/${sessionInfo.value.sessionId}`
      const token = storage.get('token')

      const client = new Client({
        reconnectDelay: 5000,
        debug: () => {},
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        webSocketFactory: () => new SockJS(endpoint)
      })

      client.onConnect = () => {
        stompConnected.value = true
        subscription.value = client.subscribe(destination, handleInboundEvent)
      }

      client.onDisconnect = () => {
        stompConnected.value = false
      }

      client.onWebSocketClose = () => {
        stompConnected.value = false
      }

      client.onStompError = (frame) => {
        console.error('STOMP错误', frame)
        notification.error('语音对话通道异常')
      }

      client.onWebSocketError = (event) => {
        console.error('WebSocket错误', event)
        stompConnected.value = false
      }

      client.activate()
      stompClient.value = client
    }

    const disconnectStomp = () => {
      if (subscription.value) {
        subscription.value.unsubscribe()
        subscription.value = null
      }
      if (stompClient.value) {
        stompClient.value.deactivate()
        stompClient.value = null
      }
      stompConnected.value = false
    }

    const handleInboundEvent = (message) => {
      try {
        const event = JSON.parse(message.body)
        const { type, messageId } = event
        switch (type) {
          case 'PROCESSING_STARTED':
            processingCounter.value += 1
            ensureAssistantMessage(messageId)
            break
          case 'PROCESSING_COMPLETED':
            processingCounter.value = Math.max(0, processingCounter.value - 1)
            break
          case 'ASR_RESULT':
            updateUserTranscription(event)
            break
          case 'AI_TEXT_SEGMENT':
            updateAssistantText(event)
            break
          case 'AUDIO_CHUNK':
            handleAudioChunk(event)
            break
          case 'ROUND_COMPLETED':
            finalizeAssistantText(event)
            break
          case 'ERROR':
            handleError(event)
            break
          default:
            break
        }
      } catch (error) {
        console.error('处理语音事件失败', error)
      }
    }

    const ensureUserMessage = (messageId) => {
      if (!messageId) return null
      let message = pendingUserMessages.get(messageId)
      if (!message) {
        message = conversation.find((item) => item.messageId === messageId && item.role === 'user')
        if (!message) {
          message = {
            id: `${messageId}-user`,
            messageId,
            role: 'user',
            text: '',
            createdAt: new Date(),
            status: 'pending',
            isHistory: false
          }
          conversation.push(message)
        }
        pendingUserMessages.set(messageId, message)
      }
      return message
    }

    const ensureAssistantMessage = (messageId) => {
      if (!messageId) return null
      let message = aiMessages.get(messageId)
      if (!message) {
        message = {
          id: `${messageId}-assistant`,
          messageId,
          role: 'assistant',
          text: '',
          partialText: '',
          audioSegments: [],
          createdAt: new Date(),
          status: 'responding',
          isHistory: false,
          _segments: new Map()
        }
        aiMessages.set(messageId, message)
        conversation.push(message)
      }
      return message
    }

    const updateUserTranscription = (event) => {
      const { messageId, text } = event
      if (!messageId) return
      const message = ensureUserMessage(messageId)
      if (!message) return
      if (text) {
        message.text = text
      }
      message.status = 'done'
    }

    const updateAssistantText = (event) => {
      const { messageId, segmentOrder, text } = event
      if (!messageId || typeof segmentOrder !== 'number') return
      const message = ensureAssistantMessage(messageId)
      if (!message || !text) return
      message._segments.set(segmentOrder, text)
      message.partialText = Array.from(message._segments.keys())
        .sort((a, b) => a - b)
        .map((key) => message._segments.get(key))
        .join('')
      message.status = 'responding'
    }

    const finalizeAssistantText = (event) => {
      const { messageId, text } = event
      if (!messageId) return
      const message = ensureAssistantMessage(messageId)
      if (!message) return
      message.text = text || message.partialText || ''
      message.partialText = ''
      message.status = 'done'
    }

    const handleAudioChunk = (event) => {
      const { messageId, segmentOrder, audioData, audioFormat, isLast, chunkIndex } = event
      if (!messageId || audioData == null || segmentOrder == null) return
      const bufferKey = `${messageId}:${segmentOrder}`
      if (!segmentBuffers.has(bufferKey)) {
        segmentBuffers.set(bufferKey, [])
      }
      const buffer = segmentBuffers.get(bufferKey)
      const bytes = base64ToUint8Array(audioData)
      if (bytes && bytes.length) {
        buffer.push(bytes)
      }
      if (isLast) {
        const totalLength = buffer.reduce((sum, arr) => sum + arr.length, 0)
        if (totalLength > 0) {
          const merged = new Uint8Array(totalLength)
          let offset = 0
          buffer.forEach((arr) => {
            merged.set(arr, offset)
            offset += arr.length
          })
          const mimeType = audioFormat && audioFormat.includes('/') ? audioFormat : `audio/${audioFormat || 'wav'}`
          const blob = uint8ArrayToBlob(merged, mimeType)
          const audioUrl = URL.createObjectURL(blob)
          createdObjectUrls.add(audioUrl)
          const message = ensureAssistantMessage(messageId)
          if (message) {
            const existingIndex = message.audioSegments.findIndex((segment) => segment.segmentOrder === segmentOrder)
            const segmentData = { segmentOrder, chunkIndex, audioUrl }
            if (existingIndex >= 0) {
              const existing = message.audioSegments[existingIndex]
              if (existing?.audioUrl) {
                URL.revokeObjectURL(existing.audioUrl)
                createdObjectUrls.delete(existing.audioUrl)
              }
              message.audioSegments.splice(existingIndex, 1, segmentData)
            } else {
              message.audioSegments.push(segmentData)
            }
            message.audioSegments.sort((a, b) => {
              if (a.segmentOrder === b.segmentOrder) {
                return (a.chunkIndex || 0) - (b.chunkIndex || 0)
              }
              return a.segmentOrder - b.segmentOrder
            })
            if (autoPlay.value) {
              enqueuePlayback(audioUrl)
            }
          }
        }
        segmentBuffers.delete(bufferKey)
      }
    }

    const handleError = (event) => {
      processingCounter.value = Math.max(0, processingCounter.value - 1)
      const { messageId, error: errorMessage } = event
      notification.error(errorMessage || '语音处理失败')
      if (messageId) {
        const userMsg = pendingUserMessages.get(messageId)
        if (userMsg) {
          userMsg.status = 'done'
        }
        const aiMsg = aiMessages.get(messageId)
        if (aiMsg) {
          aiMsg.status = 'done'
        }
      }
    }

    const enqueuePlayback = (audioUrl) => {
      playbackQueue.push(audioUrl)
      if (!currentAudio) {
        playNext()
      }
    }

    const playNext = () => {
      if (!autoPlay.value || playbackQueue.length === 0) {
        currentAudio = null
        return
      }
      const url = playbackQueue.shift()
      currentAudio = new Audio(url)
      currentAudio.onended = () => {
        currentAudio = null
        playNext()
      }
      currentAudio.onerror = () => {
        currentAudio = null
        playNext()
      }
      currentAudio.play().catch((error) => {
        console.warn('自动播放失败', error)
        currentAudio = null
      })
    }

    const clearPlaybackQueue = () => {
      playbackQueue.splice(0, playbackQueue.length)
      if (currentAudio) {
        currentAudio.pause()
        currentAudio = null
      }
    }

    const pickSupportedMimeType = () => {
      if (typeof MediaRecorder === 'undefined') {
        return ''
      }
      for (const type of SUPPORTED_MIME_TYPES) {
        if (MediaRecorder.isTypeSupported(type)) {
          return type
        }
      }
      return ''
    }

    const prepareMediaRecorder = async () => {
      if (typeof MediaRecorder === 'undefined') {
        throw new Error('当前浏览器不支持录音功能')
      }
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        throw new Error('当前浏览器不支持音频录制')
      }
      if (!mediaStream.value) {
        mediaStream.value = await navigator.mediaDevices.getUserMedia({ audio: true })
      }
      const mimeType = pickSupportedMimeType()
      recorderMimeType.value = mimeType || 'audio/webm'
      mediaRecorder.value = new MediaRecorder(mediaStream.value, mimeType ? { mimeType } : undefined)
      mediaRecorder.value.ondataavailable = (event) => {
        if (event.data && event.data.size > 0) {
          recordingChunks.value.push(event.data)
        }
      }
      mediaRecorder.value.onstop = handleRecorderStop
      mediaRecorder.value.onerror = (event) => {
        console.error('MediaRecorder错误', event.error)
        notification.error('录音设备发生错误')
      }
    }

    const toggleRecording = async () => {
      if (isRecording.value) {
        stopRecording()
      } else {
        if (!sessionReady.value || !stompConnected.value) {
          notification.warning('请先选择角色并等待连接就绪')
          return
        }
        try {
          await prepareMediaRecorder()
          startRecording()
        } catch (error) {
          console.error('初始化录音失败', error)
          notification.error(error.message || '无法访问麦克风，请检查浏览器权限设置')
        }
      }
    }

    const startRecording = () => {
      recordingChunks.value = []
      recordingStartTime.value = Date.now()
      recordingDuration.value = 0
      const generatedId = typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
      const message = {
        id: `${generatedId}-user`,
        messageId: generatedId,
        role: 'user',
        text: '',
        createdAt: new Date(),
        status: 'pending',
        isHistory: false
      }
      conversation.push(message)
      pendingUserMessages.set(generatedId, message)
      currentRecordingMessageId.value = generatedId
      discardRecording.value = false

      mediaRecorder.value.start()
      isRecording.value = true
      recordingTimer = setInterval(() => {
        if (recordingStartTime.value) {
          recordingDuration.value = Date.now() - recordingStartTime.value
        }
      }, 200)
    }

    const stopRecording = () => {
      if (mediaRecorder.value && isRecording.value) {
        if (recordingTimer) {
          clearInterval(recordingTimer)
          recordingTimer = null
        }
        if (mediaRecorder.value.state !== 'inactive') {
          mediaRecorder.value.stop()
        }
        isRecording.value = false
        if (recordingStartTime.value) {
          recordingDuration.value = Date.now() - recordingStartTime.value
        }
        recordingStartTime.value = null
      }
    }

    const cancelRecording = () => {
      if (!isRecording.value) return
      discardRecording.value = true
      stopRecording()
    }

    const handleRecorderStop = async () => {
      const messageId = currentRecordingMessageId.value
      currentRecordingMessageId.value = null
      const message = messageId ? pendingUserMessages.get(messageId) : null
      const chunks = recordingChunks.value.slice()
      recordingChunks.value = []
      if (discardRecording.value) {
        discardRecording.value = false
        if (message) {
          const index = conversation.indexOf(message)
          if (index >= 0) {
            conversation.splice(index, 1)
          }
          pendingUserMessages.delete(messageId)
        }
        return
      }
      if (!messageId || chunks.length === 0) {
        return
      }
      try {
        const blob = new Blob(chunks, { type: recorderMimeType.value })
        const base64Data = await blobToBase64(blob)
        if (message) {
          message.status = 'transcribing'
        }
        await publishVoiceMessage({
          messageId,
          base64Data,
          durationMillis: recordingDuration.value,
          mimeType: recorderMimeType.value
        })
      } catch (error) {
        console.error('发送语音消息失败', error)
        notification.error('发送语音消息失败')
        if (message) {
          message.status = 'done'
        }
      }
    }

    const publishVoiceMessage = async ({ messageId, base64Data, durationMillis, mimeType }) => {
      if (!stompClient.value || !stompConnected.value || !sessionInfo.value) {
        notification.error('实时通道未连接，请稍后再试')
        return
      }
      const destination = sessionInfo.value.publishDestination || `/app/voice/${sessionInfo.value.sessionId}`
      const audioFormat = mimeType?.includes('/') ? mimeType.split('/')[1] : mimeType
      const track = mediaStream.value?.getTracks?.()[0]
      const sampleRate = track?.getSettings?.().sampleRate
      const payload = {
        messageId,
        audioData: base64Data,
        audioFormat: audioFormat || 'webm',
        durationMillis,
        timestamp: Date.now()
      }
      if (sampleRate) {
        payload.sampleRate = sampleRate
      }
      try {
        stompClient.value.publish({
          destination,
          body: JSON.stringify(payload)
        })
      } catch (error) {
        console.error('发送语音消息失败', error)
        notification.error('实时通道发送失败')
      }
    }

    const releaseMediaDevices = () => {
      if (mediaStream.value) {
        mediaStream.value.getTracks().forEach((track) => track.stop())
        mediaStream.value = null
      }
      mediaRecorder.value = null
    }

    onMounted(() => {
      loadCharacters()
    })

    onBeforeUnmount(() => {
      try {
        if (isRecording.value) {
          discardRecording.value = true
          stopRecording()
        }
        disconnectStomp()
        clearPlaybackQueue()
        revokeObjectUrls()
        releaseMediaDevices()
        if (sessionInfo.value?.sessionId) {
          voiceChatAPI.closeSession(sessionInfo.value.sessionId).catch(() => {})
        }
      } finally {
        sessionInfo.value = null
      }
    })

    return {
      characters,
      loadingCharacters,
      selectedCharacter,
      sessionInfo,
      conversation,
      historyRef,
      stompConnected,
      isProcessing,
      isRecording,
      recordingDuration,
      autoPlay,
      statusLabel,
      sessionReady,
      formatTime,
      loadCharacters,
      selectCharacter,
      toggleRecording,
      cancelRecording
    }
  }
}
</script>

<style lang="scss" scoped>
.voice-chat-page {
  display: flex;
  height: calc(100vh - 120px);
  min-height: 680px;
  background: var(--background-primary);
  color: var(--text-primary);
}

.character-panel {
  width: 280px;
  border-right: 1px solid var(--border-light);
  background: var(--background-secondary);
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-sm;
  padding: $spacing;
  border-bottom: 1px solid var(--border-light);
}

.refresh-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 6px 10px;
  border: none;
  border-radius: $border-radius-sm;
  background: var(--background-primary);
  color: var(--text-secondary);
  cursor: pointer;
  min-width: 64px;
  transition: all $transition-fast ease;

  &:hover:not(:disabled) {
    background: var(--primary-color);
    color: #fff;
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
}

.spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: rgba(255, 255, 255, 1);
  animation: spin 1s linear infinite;
}

.character-list {
  flex: 1;
  padding: $spacing;
  overflow-y: auto;
}

.character-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: $spacing-sm $spacing;
  border: none;
  background: transparent;
  border-radius: $border-radius-sm;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all $transition-normal ease;
  text-align: left;

  &:hover {
    background: var(--background-primary);
    color: var(--text-primary);
  }

  &.active {
    background: rgba(217, 119, 6, 0.15);
    color: var(--primary-color);
  }

  .name {
    font-weight: 600;
    font-size: 0.95rem;
  }

  .description {
    font-size: 0.8rem;
    color: var(--text-secondary);
  }
}

.empty-list {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-lg;
  text-align: center;
  color: var(--text-secondary);
}

.conversation-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: $spacing-lg;
  gap: $spacing-lg;
}

.conversation-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $spacing;
}

.title-area {
  display: flex;
  flex-direction: column;
  gap: 4px;

  h1 {
    font-size: 1.4rem;
    font-weight: 600;
    margin: 0;
  }

  .subtitle {
    font-size: 0.9rem;
    color: var(--text-secondary);
  }
}

.status-area {
  display: flex;
  gap: $spacing;
  align-items: center;
}

.status-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 16px;
  background: var(--background-secondary);
  font-size: 0.8rem;

  &.online {
    color: var(--success-color);
  }

  &.offline {
    color: var(--text-secondary);
  }

  &.processing {
    color: var(--warning-color);
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: currentColor;
  }
}

.history {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: $spacing;
  gap: $spacing-xs;
  background: var(--background-tertiary);
  border-radius: $border-radius-lg;
  overflow-y: auto;
}

.message {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: $spacing-sm;
  border-radius: $border-radius-sm;
  background: var(--background-primary);
  box-shadow: var(--shadow-light);

  &.user {
    align-self: flex-end;
    background: var(--primary-color);
    color: #fff;
  }

  &.assistant {
    align-self: flex-start;
  }

  &.is-history {
    opacity: 0.75;
  }
}

.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.78rem;
  color: var(--text-secondary);
}

.status-tag {
  padding: 2px 6px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.2);
}

.bubble {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
  word-break: break-word;
}

.streaming-text {
  font-family: ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace;
  color: var(--primary-color);
}

.segment-audio {
  display: grid;
  gap: $spacing-xs;
}

.segment-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: $spacing-xs;
  border-radius: $border-radius-sm;
  background: rgba(217, 119, 6, 0.08);
}

.recorder {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  padding: $spacing;
  border: 1px solid var(--border-light);
  border-radius: $border-radius-lg;
  background: var(--background-secondary);
}

.recorder-status {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  color: var(--text-secondary);
  font-size: 0.9rem;

  .hint {
    flex: 1;
  }

  .recording {
    color: var(--success-color);
    display: inline-flex;
    align-items: center;
    gap: 6px;

    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--success-color);
    }
  }
}

.recorder-controls {
  display: flex;
  align-items: center;
  gap: $spacing;
}

.record-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 140px;
  height: 44px;
  border: none;
  border-radius: 22px;
  background: var(--primary-color);
  color: #fff;
  font-size: 0.95rem;
  cursor: pointer;
  transition: all $transition-normal ease;

  &:hover:not(.disabled) {
    background: var(--primary-dark);
  }

  &.disabled {
    background: var(--background-tertiary);
    color: var(--text-tertiary);
    cursor: not-allowed;
  }

  &.recording {
    background: var(--error-color);
  }

  .icon {
    display: inline-block;
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: #fff;
  }
}

.secondary-btn {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: $border-radius-sm;
  transition: background $transition-fast ease;

  &:hover {
    background: rgba(255, 255, 255, 0.2);
  }
}

.autoplay-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.85rem;
  color: var(--text-secondary);

  input {
    accent-color: var(--primary-color);
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

@media (max-width: 1024px) {
  .voice-chat-page {
    flex-direction: column;
  }

  .character-panel {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--border-light);
  }

  .conversation-panel {
    padding: $spacing;
  }

  .recorder-controls {
    flex-direction: column;
    align-items: stretch;
  }

  .record-btn {
    width: 100%;
  }
}
</style>
