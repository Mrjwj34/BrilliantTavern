<template>
  <div class="round-voice-chat">
    <aside class="character-panel" :class="{ collapsed: characterPanelCollapsed }">
      <div class="panel-header">
        <h3>选择角色卡</h3>
        <button class="collapse-btn" @click="toggleCharacterPanel" title="收起侧边栏">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="15,18 9,12 15,6"/>
          </svg>
        </button>
      </div>
      <div class="search-box">
        <input
          v-model.trim="searchKeyword"
          type="text"
          placeholder="搜索角色卡"
          @keyup.enter="fetchCharacters(true)"
        />
        <button class="search-btn" @click="fetchCharacters(true)">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <path d="M21 21l-4.35-4.35"/>
          </svg>
        </button>
      </div>
      <div class="character-list" :class="{ loading: loadingCharacters }">
        <div v-if="loadingCharacters" class="loading-hint">
          <div class="loading-spinner"></div>
          加载角色中...
        </div>
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
              <span v-if="card.likesCount !== undefined">❤ {{ card.likesCount }}</span>
            </div>
          </div>
          <div v-if="!filteredCharacters.length" class="empty-hint">
            未找到符合条件的角色卡
          </div>
        </template>
      </div>
    </aside>

    <!-- 收起时的浮动展开按钮 -->
    <div v-show="characterPanelCollapsed" class="floating-expand-btn" @click="toggleCharacterPanel">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="9,18 15,12 9,6"/>
      </svg>
    </div>

    <section class="chat-panel">
      <header class="chat-header">
        <div>
          <div class="header-top">
            <h2>轮次语音对话</h2>
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
          </div>
          <p v-if="selectedCharacter">当前角色：{{ selectedCharacter.name }}</p>
          <p v-else>请选择角色后开始对话</p>
          
          <!-- 标题显示区域 -->
          <div v-if="chatTitle" class="chat-title-area">
            <span class="chat-title" :class="{ typing: titleTyping }">
              {{ chatTitle }}
              <span v-if="titleTyping" class="typing-cursor">|</span>
            </span>
          </div>
        </div>
        <div class="session-info" v-if="session || isSelectingCharacter">
          <span class="status-badge" :class="{ 
            active: stompConnected && !isSelectingCharacter, 
            connecting: (!stompConnected && session) || isSelectingCharacter 
          }">
            <div v-if="(!stompConnected && session) || isSelectingCharacter" class="connecting-spinner"></div>
            {{ isSelectingCharacter ? '准备中...' : (stompConnected ? '已连接' : '连接中...') }}
          </span>
        </div>
      </header>

      <div class="chat-body">
        <div v-if="!selectedCharacter" class="placeholder">
          <p>请从左侧选择一个角色卡以开启语音对话体验。</p>
        </div>
        <div v-else class="conversation" ref="chatListRef">
          <div v-if="historyLoading" class="history-loading">
            <div class="history-loading-spinner"></div>
            <span>加载历史对话...</span>
          </div>
          <!-- 场景描述区域 -->
          <div v-if="!historyLoading && selectedCharacter && !messages.length" class="scene-description">
            <div v-if="selectedCharacter.scenario" class="scenario-text">
              <em>{{ selectedCharacter.scenario }}</em>
            </div>
            <div v-else class="default-scene">
              <em>欢迎和 {{ selectedCharacter.name }} 对话，请开始你们的交流...</em>
            </div>
          </div>
          <div
            v-for="message in messages"
            :key="message.id"
            :class="['message', message.role]"
          >
            <div class="message-avatar">
              <img 
                v-if="message.role === 'assistant' && selectedCharacter?.avatarUrl" 
                :src="selectedCharacter.avatarUrl" 
                :alt="selectedCharacter.name" 
                class="avatar"
              />
              <div 
                v-else-if="message.role === 'assistant'" 
                class="avatar default-avatar assistant-avatar"
              >
                {{ selectedCharacter?.name?.[0] || 'A' }}
              </div>
              <div 
                v-else 
                class="avatar default-avatar user-avatar"
              >
                我
              </div>
            </div>
            <div class="message-content">
              <div class="meta">
                <span class="role">{{ message.role === 'user' ? '我' : selectedCharacter.name }}</span>
                <span class="time">{{ formatTime(message.timestamp) }}</span>
              </div>
              <div class="bubble" :class="{ typing: message.isTyping }">
              <!-- 内联播放按钮，直接跟在文字后面 -->
              <p v-if="message.status === 'error'" class="error-text">{{ message.text }}</p>
              <p v-else class="message-text">
                <span 
                  :id="`message-text-${message.id}`"
                  class="text-content"
                  :data-message-id="message.id"
                >
                  {{ message.text }}
                </span>
                <!-- 播放按钮直接跟在文字后面 -->
                <button
                  v-if="message.audioSegments && message.audioSegments.length === 1"
                  class="inline-play-btn"
                  :class="{ playing: currentPlayingId === `${message.id}-0` }"
                  @click="togglePlaySegment(message.audioSegments[0], message)"
                  title="播放语音"
                >
                  <svg v-if="currentPlayingId !== `${message.id}-0`" width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M8 5v14l11-7z"/>
                  </svg>
                  <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                    <rect x="6" y="4" width="4" height="16"/>
                    <rect x="14" y="4" width="4" height="16"/>
                  </svg>
                </button>
              </p>
              
              <!-- 多段音频的处理 -->
              <div v-if="message.audioSegments && message.audioSegments.length > 1" class="multi-audio-segments">
                <div
                  v-for="segment in message.audioSegments"
                  :key="segment.segmentOrder"
                  class="segment-item"
                >
                  <button
                    class="segment-play-btn"
                    :class="{ playing: currentPlayingId === `${message.id}-${segment.segmentOrder}` }"
                    @click="togglePlaySegment(segment, message)"
                  >
                    <svg v-if="currentPlayingId !== `${message.id}-${segment.segmentOrder}`" width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M8 5v14l11-7z"/>
                    </svg>
                    <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
                      <rect x="6" y="4" width="4" height="16"/>
                      <rect x="14" y="4" width="4" height="16"/>
                    </svg>
                  </button>
                  <span class="segment-label">第 {{ segment.segmentOrder + 1 }} 段</span>
                </div>
              </div>
              
              <!-- 图像显示区域 -->
              <div v-if="message.images && message.images.length" class="image-gallery">
                <div 
                  v-for="image in message.images" 
                  :key="image.id" 
                  class="image-container"
                  :class="{ 'self-portrait': image.isSelf }"
                >
                  <div v-if="image.status === 'generating'" class="image-placeholder">
                    <div class="loading-spinner"></div>
                    <p class="loading-text">{{ image.isSelf ? '正在绘制自画像...' : '正在生成图像...' }}</p>
                    <p class="description">{{ image.description }}</p>
                  </div>
                  <div v-else-if="image.status === 'completed'" class="generated-image">
                    <img 
                      :src="image.imageUri" 
                      :alt="image.description"
                      class="ai-generated-img"
                      @click="viewImageFullscreen(image)"
                    />
                    <div class="image-caption">
                      <span class="image-type">{{ image.isSelf ? '🎭 自画像' : '🎨 创作' }}</span>
                      <span class="image-desc">{{ image.description }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <div v-if="message.status === 'pending'" class="pending-indicator">识别中...</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <footer class="chat-controls">
        <div class="status-group">
          <div v-if="isRecording" class="recording-indicator">
            <span class="recording-text">录音中</span>
            <div class="audio-visualizer">
              <div 
                v-for="i in 20" 
                :key="i" 
                class="audio-bar" 
                :style="{ animationDelay: `${i * 0.1}s`, height: audioLevels[i - 1] + '%' }"
              ></div>
            </div>
            <span class="recording-time">{{ recordElapsed }}s</span>
          </div>
          <span v-else-if="isProcessing" class="processing-indicator">AI 正在思考...</span>
        </div>
        <div class="control-buttons">
          <button
            class="mic-btn"
            :class="{ active: isRecording, recording: isRecording }"
            :disabled="!canRecord"
            @click="toggleRecording"
            :title="isRecording ? '点击结束录音' : '点击开始录音'"
          >
            <svg v-if="!isRecording" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
              <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
              <line x1="12" y1="19" x2="12" y2="23"/>
              <line x1="8" y1="23" x2="16" y2="23"/>
            </svg>
            <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <rect x="6" y="6" width="12" height="12" rx="2"/>
            </svg>
            <span class="mic-text">{{ isRecording ? '停止' : '录音' }}</span>
          </button>
          <button
            class="new-chat-btn"
            v-if="selectedCharacter"
            @click="createNewChat"
            :disabled="sessionClosing || isProcessing"
          >
            新建对话
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
  emits: ['character-selected', 'character-deselected'],
  setup(props, { emit }) {
    // 注入来自Dashboard的会话数据
    const selectedSession = inject('selectedSession', null)
    
    // 角色卡相关
    const searchKeyword = ref('')
    const characterList = ref([])
    const loadingCharacters = ref(false)
    const selectedCharacter = ref(null)
    const characterPanelCollapsed = ref(false)
    const isSelectingCharacter = ref(false)
    
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

    // 音频可视化相关
    const audioLevels = ref(Array(20).fill(20)) // 20个音频条的高度
    const analyser = ref(null)
    const audioContext = ref(null)
    const animationFrame = ref(null)
    
    // 播放状态管理
    const currentPlayingId = ref(null)
    const currentPlayingAudio = ref(null)
    const playingProgress = ref(0)

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
    
    // 映射历史消息，包括处理附件信息
    const mapHistoryMessage = (item, fallbackId) => {
      const baseMessage = {
        id: `${item.id || fallbackId}`,
        role: item.role === 'ASSISTANT' ? 'assistant' : 'user',
        text: item.content,
        timestamp: parseTimestamp(item.timestamp),
        audioSegments: []
      }
      
      // 处理附件信息（主要是图片）
      if (item.attachments) {
        try {
          const attachments = JSON.parse(item.attachments)
          if (attachments.images && Array.isArray(attachments.images)) {
            baseMessage.images = attachments.images.map(img => ({
              id: `history-image-${item.id}-${img.uri}`,
              status: 'completed',
              imageUri: img.uri,
              description: img.description || '',
              isSelf: img.isSelf || false
            }))
          }
        } catch (e) {
          console.warn('解析历史记录附件信息失败:', e, item.attachments)
        }
      }
      
      return baseMessage
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
            .map(item => mapHistoryMessage(item, `${characterId}-${item.timestamp}`))
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
            .map(item => mapHistoryMessage(item, `${sessionId}-${item.timestamp}`))
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
            .map(item => mapHistoryMessage(item, `complete-${item.timestamp}`))
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
      // 总是发射角色选中事件，确保历史筛选正常工作
      emit('character-selected', card)
      
      // 如果是同一个角色，只需要触发筛选，不需要重新创建会话
      if (selectedCharacter.value && selectedCharacter.value.id === card.id) {
        return
      }
      
      isSelectingCharacter.value = true
      selectedCharacter.value = card
      
      try {
        // 初始化语言设置（使用角色卡的默认语言）
        currentVoiceLanguage.value = card.voiceLanguage || 'zh'
        currentSubtitleLanguage.value = card.subtitleLanguage || 'zh'
        
        await startNewSession() // 创建新会话
      } finally {
        isSelectingCharacter.value = false
      }
    }
    
    // 更新语言设置
    const updateLanguageSettings = () => {
      console.log(`语言设置已更新: 语音=${currentVoiceLanguage.value}, 字幕=${currentSubtitleLanguage.value}`)
      // 这里���以添加任何需要在语言变更时执行的逻辑
    }

    // 切换角色面板收起状态
    const toggleCharacterPanel = () => {
      characterPanelCollapsed.value = !characterPanelCollapsed.value
    }

    // 创建新对话
    const createNewChat = async () => {
      if (!selectedCharacter.value) return
      
      // 清除之前的标题
      chatTitle.value = ''
      titleTyping.value = false
      if (titleAnimationTimer.value) {
        clearInterval(titleAnimationTimer.value)
        titleAnimationTimer.value = null
      }
      
      // 开始新会话
      await startNewSession()
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
            // 保持角色卡的原始语言设置，不重置为中文
            currentVoiceLanguage.value = character.voiceLanguage || 'zh'
            currentSubtitleLanguage.value = character.subtitleLanguage || 'zh'
            // 通知父组件角色被选中，用于筛选历史对话
            emit('character-selected', character)
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
            .map(item => mapHistoryMessage(item, `history-${item.timestamp}`))
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
        
        // 仅在真正清除角色选择时才发射取消选择事件
        // 注意：切换角色时不应该发射此事件
      }
    }

    // 清除角色选择
    const clearCharacterSelection = () => {
      selectedCharacter.value = null
      emit('character-deselected')
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
        
        // 初始化音频可视化
        startAudioVisualization()
        
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
      
      // 停止音频可视化
      stopAudioVisualization()
      
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
          case 'RETRY_FAILED':
            handleRetryFailed(message)
            break
          case 'ROUND_DISCARDED':
            handleRoundDiscarded(message)
            break
          case 'TITLE_UPDATE':
            handleTitleUpdate(message)
            break
          case 'MEMORY_RETRIEVAL_STARTED':
            handleMemoryRetrievalStarted(message)
            break
          case 'MEMORY_RETRIEVAL_COMPLETED':
            handleMemoryRetrievalCompleted(message)
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
      
      // 检查是否是记忆检索事件
      if (payload.text && payload.text.includes('[MEMORY_EVENT:')) {
        handleMemoryEvent(payload.text)
        return // 不显示这些特殊事件作为对话内容
      }
      
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
      
      // 设置播放状态，让按钮显示为"播放中"
      const playId = `${segment.messageId}-${segment.segmentOrder || 0}`
      currentPlayingId.value = playId
      currentPlayingAudio.value = audio
      
      // 找到对应的消息进行文字染色
      const messageId = segment.messageId
      const assistantMessage = assistantMessages.get(messageId)
      const messageText = assistantMessage?.text || ''
      
      // 监听播放进度，实现文字染色效果
      audio.addEventListener('timeupdate', () => {
        if (audio.duration > 0) {
          const progress = (audio.currentTime / audio.duration)
          playingProgress.value = progress * 100
          updateTextHighlight(messageId, progress, messageText)
        }
      })
      
      audio.onloadeddata = () => {
        console.log('音频数据加载完成:', { duration: audio.duration, segmentOrder: segment.segmentOrder })
      }
      
      audio.onended = () => {
        console.log('音频播放结束:', { segmentOrder: segment.segmentOrder })
        // 清理状态
        clearTextHighlight(messageId)
        currentPlayingId.value = null
        currentPlayingAudio.value = null
        playingProgress.value = 0
        currentAudio.value = null
        playNextAudio()
      }
      
      audio.onerror = (error) => {
        console.error('音频播放错误:', { segmentOrder: segment.segmentOrder, error })
        // 清理状态
        clearTextHighlight(messageId)
        currentPlayingId.value = null
        currentPlayingAudio.value = null
        playingProgress.value = 0
        currentAudio.value = null
        playNextAudio()
      }
      
      audio.play().catch(error => {
        console.warn('自动播放失败', { segmentOrder: segment.segmentOrder, error })
        // 清理状态
        clearTextHighlight(messageId)
        currentPlayingId.value = null
        currentPlayingAudio.value = null
        playingProgress.value = 0
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

    // 切换播放/暂停音频片段
    const togglePlaySegment = (segment, message) => {
      if (!segment?.url) return
      
      const playId = `${message.id}-${segment.segmentOrder || 0}`
      
      // 如果当前有其他音频在播放，先停止
      if (currentPlayingAudio.value && currentPlayingId.value !== playId) {
        currentPlayingAudio.value.pause()
        const oldMessageId = currentPlayingId.value.split('-')[0]
        clearTextHighlight(oldMessageId)
        currentPlayingAudio.value = null
        currentPlayingId.value = null
        playingProgress.value = 0
      }
      
      // 如果是同一个音频，切换播放/暂停
      if (currentPlayingId.value === playId) {
        if (currentPlayingAudio.value) {
          if (currentPlayingAudio.value.paused) {
            // 恢复播放
            currentPlayingAudio.value.play().catch(console.warn)
          } else {
            // 暂停播放
            currentPlayingAudio.value.pause()
            // 暂停时清除播放状态，让按钮变回播放状态
            clearTextHighlight(message.id)
            currentPlayingAudio.value = null
            currentPlayingId.value = null
            playingProgress.value = 0
          }
        }
        return
      }
      
      // 播放新音频
      const audio = new Audio(segment.url)
      currentPlayingAudio.value = audio
      currentPlayingId.value = playId
      
      // 监听播放进度，实现文字染色效果
      audio.addEventListener('timeupdate', () => {
        if (audio.duration > 0) {
          const progress = (audio.currentTime / audio.duration)
          playingProgress.value = progress * 100
          updateTextHighlight(message.id, progress, message.text)
        }
      })
      
      // 播放结束时清理状态
      audio.addEventListener('ended', () => {
        clearTextHighlight(message.id)
        currentPlayingAudio.value = null
        currentPlayingId.value = null
        playingProgress.value = 0
      })
      
      // 播放失败时清理状态
      audio.addEventListener('error', () => {
        clearTextHighlight(message.id)
        currentPlayingAudio.value = null
        currentPlayingId.value = null
        playingProgress.value = 0
      })
      
      // 暂停事件监听器
      audio.addEventListener('pause', () => {
        // 当音频被暂停时，不自动清理状态
        // 只有在手动停止时才清理状态
      })
      
      audio.play().catch(error => {
        console.warn('音频播放失败', error)
        clearTextHighlight(message.id)
        currentPlayingAudio.value = null
        currentPlayingId.value = null
        playingProgress.value = 0
      })
    }

    // 更新文字高亮效果 - 像字幕一样
    const updateTextHighlight = (messageId, progress, text) => {
      const textElement = document.getElementById(`message-text-${messageId}`)
      console.log('updateTextHighlight:', { messageId, progress, text, textElement })
      
      if (!textElement || !text) {
        console.warn('文本元素未找到或文本为空:', { messageId, textElement, text })
        return
      }
      
      // 计算应该高亮到哪个字符
      const textLength = text.length
      const highlightedLength = Math.floor(textLength * progress)
      
      console.log('高亮计算:', { textLength, progress, highlightedLength })
      
      if (highlightedLength >= textLength) {
        // 全部高亮
        textElement.innerHTML = `<span class="subtitle-highlighted">${text}</span>`
      } else if (highlightedLength <= 0) {
        // 没有高亮
        textElement.innerHTML = text
      } else {
        // 部分高亮
        const highlightedText = text.substring(0, highlightedLength)
        const remainingText = text.substring(highlightedLength)
        textElement.innerHTML = `<span class="subtitle-highlighted">${highlightedText}</span><span class="subtitle-remaining">${remainingText}</span>`
      }
    }

    // 清除文字高亮效果
    const clearTextHighlight = (messageId) => {
      const textElement = document.getElementById(`message-text-${messageId}`)
      console.log('clearTextHighlight:', { messageId, textElement })
      
      if (textElement) {
        // 获取原始文本内容
        const originalText = textElement.getAttribute('data-original-text') || textElement.textContent || textElement.innerText
        textElement.innerHTML = originalText
      }
    }
    
    // 查看图像全屏
    const viewImageFullscreen = (image) => {
      // 简单的全屏查看实现
      const modal = document.createElement('div')
      modal.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.9);
        display: flex;
        justify-content: center;
        align-items: center;
        z-index: 9999;
        cursor: pointer;
      `
      
      const img = document.createElement('img')
      img.src = image.imageUri
      img.alt = image.description
      img.style.cssText = `
        max-width: 90%;
        max-height: 90%;
        object-fit: contain;
        border-radius: 8px;
        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);
      `
      
      modal.appendChild(img)
      document.body.appendChild(modal)
      
      // 点击关闭
      modal.addEventListener('click', () => {
        document.body.removeChild(modal)
      })
      
      // ESC键关闭
      const handleKeyDown = (e) => {
        if (e.key === 'Escape') {
          document.body.removeChild(modal)
          document.removeEventListener('keydown', handleKeyDown)
        }
      }
      document.addEventListener('keydown', handleKeyDown)
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
      
      // 根据不同的方法执行结果显示不同的提示
      if (payload.action === 'method_error') {
        // 只有已知方法的错误才显示，避免显示未知方法的错误
        const result = payload.result || {}
        if (result.methodName === 'remember' || result.methodName === '记住') {
          const errorContent = payload.error || '记忆存储失败'
          notification.warning(errorContent)
        } else if (result.methodName === 'imagen' || result.methodName === '生成图片' || result.methodName === '生图') {
          const errorContent = payload.error || '图像生成失败'
          notification.error(errorContent)
        }
        // 未知方法的错误直接忽略，不显示弹窗
      } else if (payload.action === 'method_executed') {
        const result = payload.result || {}
        
        // 只处理已知的方法类型
        if (result.methodName === 'remember' || result.methodName === '记住') {
          const characterName = selectedCharacter.value?.name || '角色'
          const memoryContent = result.memoryContent || ''
          const shortContent = memoryContent.length > 15 ? memoryContent.substring(0, 15) + '...' : memoryContent
          
          // 显示有趣的记忆成功提示
          const memoryMessages = [
            `💭 ${characterName}默默记下了这件事...`,
            `🧠 ${characterName}把这个重要信息存进了脑海里！`,
            `📝 ${characterName}认真记住了：${shortContent}`,
            `✨ ${characterName}将这段记忆珍藏起来了`,
            `🎯 ${characterName}牢牢记住了这个重要信息！`
          ]
          
          const randomMessage = memoryMessages[Math.floor(Math.random() * memoryMessages.length)]
          
          notification.success(randomMessage)
        }
        // 未识别的方法直接忽略，不显示任何弹窗
      } else if (payload.action === 'image_generation_started') {
        // 图像生成开始
        handleImageGenerationStarted(message)
      } else if (payload.action === 'image_generation_completed') {
        // 图像生成完成
        handleImageGenerationCompleted(message)
      } else if (payload.action === 'image_generation_failed') {
        // 图像生成失败
        handleImageGenerationFailed(message)
      }
    }

    // 处理记忆检索开始事件
    const handleMemoryRetrievalStarted = (message) => {
      const { messageId, payload } = message
      console.debug('处理记忆检索开始:', { messageId, payload })
      
      if (!payload?.message) return
      
      // 显示记忆检索开始的友好提示
      const startMessages = [
        `🤔 ${payload.message}`,
        `💭 ${payload.message}`,
        `🧠 ${payload.message}`,
        `📚 ${payload.message}`
      ]
      
      const randomMessage = startMessages[Math.floor(Math.random() * startMessages.length)]
      notification.info(randomMessage)
    }
    
    // 处理记忆检索完成事件
    const handleMemoryRetrievalCompleted = (message) => {
      const { messageId, payload } = message
      console.debug('处理记忆检索完成:', { messageId, payload })
      
      if (!payload?.message) return
      
      // 显示记忆检索完成的友好提示
      const completedMessages = [
        `✨ ${payload.message}`,
        `💡 ${payload.message}`,
        `🎯 ${payload.message}`,
        `🌟 ${payload.message}`
      ]
      
      const randomMessage = completedMessages[Math.floor(Math.random() * completedMessages.length)]
      notification.success(randomMessage)
    }

    // 处理图像生成开始事件
    const handleImageGenerationStarted = (message) => {
      const { messageId, payload } = message
      console.debug('处理图像生成开始:', { messageId, payload })
      
      const characterName = selectedCharacter.value?.name || '角色'
      const isSelf = payload.isSelf
      const description = payload.description
      
      // 显示图像生成开始提示
      const startMessages = isSelf ? [
        `🎨 ${characterName}正在画自己的肖像...`,
        `🖼️ ${characterName}准备展示自己的样子...`,
        `✨ ${characterName}正在创作自画像...`,
        `🎭 ${characterName}要展现${description}的表情...`
      ] : [
        `🎨 ${characterName}正在创作图像...`,
        `🖼️ ${characterName}开始画画了...`,
        `✨ ${characterName}的创意正在形成...`,
        `🖌️ ${characterName}正在描绘：${description.substring(0, 20)}...`
      ]
      
      const randomMessage = startMessages[Math.floor(Math.random() * startMessages.length)]
      notification.info(randomMessage)
      
      // 在对话中添加生成中的图像占位符
      addImagePlaceholder(messageId, isSelf, description)
    }
    
    // 处理图像生成完成事件
    const handleImageGenerationCompleted = (message) => {
      const { messageId, payload } = message
      console.debug('处理图像生成完成:', { messageId, payload })
      
      const characterName = selectedCharacter.value?.name || '角色'
      const result = payload.result
      
      // 显示图像生成完成提示
      const completedMessages = [
        `🎉 ${characterName}完成了创作！`,
        `✨ ${characterName}的作品诞生了！`,
        `🖼️ ${characterName}展示了精彩的图像！`,
        `🎨 ${characterName}的艺术天赋展现无遗！`
      ]
      
      const randomMessage = completedMessages[Math.floor(Math.random() * completedMessages.length)]
      notification.success(randomMessage)
      
      // 更新对话中的图像
      updateImageInMessage(messageId, result)
    }
    
    // 处理图像生成失败事件
    const handleImageGenerationFailed = (message) => {
      const { messageId, payload } = message
      console.debug('处理图像生成失败:', { messageId, payload })
      
      const characterName = selectedCharacter.value?.name || '角色'
      const errorMessage = payload.error || '图像生成失败'
      
      // 显示错误提示
      const failedMessages = [
        `😔 ${characterName}的创作遇到了困难...`,
        `🎨 ${characterName}暂时无法完成这幅作品`,
        `💭 ${characterName}说：抱歉，我现在画不出来这个...`,
        `🖼️ ${characterName}的灵感暂时卡住了`
      ]
      
      const randomMessage = failedMessages[Math.floor(Math.random() * failedMessages.length)]
      notification.error(`${randomMessage}\n详细错误：${errorMessage}`)
      
      // 移除对话中的图像占位符
      removeImagePlaceholder(messageId)
    }
    
    // 添加图像占位符到对话
    const addImagePlaceholder = (messageId, isSelf, description) => {
      // 确保有对应的助手消息
      const assistantMessage = ensureAssistantMessage(messageId)
      
      // 添加图像占位符
      if (!assistantMessage.images) {
        assistantMessage.images = []
      }
      
      assistantMessage.images.push({
        id: `${messageId}-image-${Date.now()}`,
        status: 'generating',
        isSelf: isSelf,
        description: description,
        imageUri: null
      })
      
      // 更新消息
      updateMessages()
    }
    
    // 更新消息中的图像
    const updateImageInMessage = (messageId, result) => {
      const assistantMessage = assistantMessages.get(messageId)
      if (assistantMessage && assistantMessage.images) {
        // 找到对应的图像占位符并更新
        const imageIndex = assistantMessage.images.findIndex(img => img.status === 'generating')
        if (imageIndex !== -1) {
          assistantMessage.images[imageIndex] = {
            ...assistantMessage.images[imageIndex],
            status: 'completed',
            imageUri: result.imageUri,
            isSelf: result.isSelf
          }
          
          // 更新消息
          updateMessages()
        }
      }
    }
    
    // 移除消息中的图像占位符
    const removeImagePlaceholder = (messageId) => {
      const assistantMessage = assistantMessages.get(messageId)
      if (assistantMessage && assistantMessage.images) {
        // 移除所有正在生成的图像占位符
        assistantMessage.images = assistantMessage.images.filter(img => img.status !== 'generating')
        
        // 更新消息
        updateMessages()
      }
    }

    // 处理其他事件
    const handleMemoryEvent = (text) => {
      console.debug('处理记忆事件:', text)
      const characterName = selectedCharacter.value?.name || '角色'
      
      // 解析 [MEMORY_EVENT:TYPE]内容[/MEMORY_EVENT] 格式
      const memoryEventMatch = text.match(/\[MEMORY_EVENT:(\w+)\](.+?)\[\/MEMORY_EVENT\]/)
      if (!memoryEventMatch) {
        console.warn('无法解析记忆事件格式:', text)
        return
      }
      
      const [, eventType, eventMessage] = memoryEventMatch
      
      switch (eventType) {
        case 'MEMORY_RETRIEVAL_STARTED':
          notification.info(`🧠 ${characterName}正在努力回忆...`, {
            duration: 2000
          })
          break
        case 'MEMORY_RETRIEVAL_COMPLETED':
          notification.success(`✨ ${characterName}想起来了！`, {
            duration: 2000
          })
          break
        default:
          console.warn('未知的记忆事件类型:', eventType)
          break
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

    const handleRetryFailed = (message) => {
      const { messageId, payload } = message
      if (!messageId) return
      
      console.warn('重试失败:', payload)
      
      const userMessage = userMessages.get(messageId)
      if (userMessage) {
        userMessage.text = `重试失败: ${payload?.finalError || '操作多次重试后仍然失败'}`
        userMessage.status = 'error'
      }
      
      // 显示错误通知
      notification.error(`操作失败: ${payload?.operation || '语音处理'}重试失败，请重新发送`)
      
      isProcessing.value = false
      activeMessageId.value = null
    }

    const handleRoundDiscarded = (message) => {
      const { messageId, payload } = message
      if (!messageId) return
      
      console.warn('对话轮次被丢弃:', payload)
      
      // 移除用户消息气泡
      const userMessage = userMessages.get(messageId)
      if (userMessage) {
        userMessages.delete(messageId)
        // 从messages列表中移除
        const index = messages.value.findIndex(msg => msg.id === messageId)
        if (index !== -1) {
          messages.value.splice(index, 1)
        }
      }
      
      // 显示错误通知
      notification.error(`对话处理失败: ${payload?.reason || '多次重试后仍然失败，已丢弃本次对话'}`)
      
      isProcessing.value = false
      activeMessageId.value = null
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

    // 音频可视化函数
    const startAudioVisualization = () => {
      if (!mediaStream.value) return
      
      try {
        audioContext.value = new (window.AudioContext || window.webkitAudioContext)()
        const source = audioContext.value.createMediaStreamSource(mediaStream.value)
        analyser.value = audioContext.value.createAnalyser()
        
        analyser.value.fftSize = 256
        const dataArray = new Uint8Array(analyser.value.frequencyBinCount)
        
        source.connect(analyser.value)
        
        const updateLevels = () => {
          if (!isRecording.value || !analyser.value) return
          
          analyser.value.getByteFrequencyData(dataArray)
          
          // 计算平均音量
          let sum = 0
          for (let i = 0; i < dataArray.length; i++) {
            sum += dataArray[i]
          }
          const average = sum / dataArray.length
          
          // 生成20个音频条的随机但基于音量的高度
          const newLevels = audioLevels.value.map((_, index) => {
            const baseHeight = Math.max(5, average / 2.55) // 将0-255转换为0-100，最小5%
            const randomFactor = 0.5 + Math.random() * 0.5 // 0.5-1.0的随机因子
            const variation = Math.sin((Date.now() + index * 100) / 200) * 20 // 添加波动效果
            return Math.min(100, Math.max(5, baseHeight * randomFactor + variation))
          })
          
          audioLevels.value = newLevels
          
          animationFrame.value = requestAnimationFrame(updateLevels)
        }
        
        updateLevels()
      } catch (error) {
        console.warn('音频可视化初始化失败', error)
      }
    }

    const stopAudioVisualization = () => {
      if (animationFrame.value) {
        cancelAnimationFrame(animationFrame.value)
        animationFrame.value = null
      }
      
      if (audioContext.value) {
        audioContext.value.close()
        audioContext.value = null
      }
      
      analyser.value = null
      audioLevels.value = Array(20).fill(20) // 重置为默认高度
    }

    return {
      // 角色卡相关
      searchKeyword,
      characterList,
      filteredCharacters,
      loadingCharacters,
      selectedCharacter,
      characterPanelCollapsed,
      isSelectingCharacter,
      selectCharacter,
      fetchCharacters,
      toggleCharacterPanel,
      createNewChat,

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
      togglePlaySegment,
      viewImageFullscreen,
      
      // 音频可视化
      audioLevels,
      currentPlayingId,
      playingProgress,
      
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
  position: relative;
}

.floating-expand-btn {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  background: var(--primary-color);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  z-index: 10;
  transition: all 0.3s ease;

  &:hover {
    background: var(--primary-dark);
    transform: translateY(-50%) scale(1.1);
    box-shadow: 0 6px 16px rgba(99, 102, 241, 0.4);
  }
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
  transition: width 0.3s ease, transform 0.3s ease;
  position: relative;

  &.collapsed {
    width: 0;
    padding: 0;
    overflow: hidden;
    transform: translateX(-100%);
  }
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

.search-btn {
  padding: 0.5rem;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;

  &:hover {
    background: var(--background-tertiary);
    color: var(--primary-color);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.collapse-btn {
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;

  &:hover {
    background: var(--background-tertiary);
    color: var(--text-secondary);
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
    justify-content: flex-start;
    align-items: flex-start;
    color: var(--text-secondary);
    background: transparent;
  }

  .loading-hint {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 1rem;
    color: var(--text-secondary);
    font-size: 0.85rem;
    background: transparent;

    .loading-spinner {
      width: 16px;
      height: 16px;
      border: 2px solid var(--border-light);
      border-top: 2px solid var(--primary-color);
      border-radius: 50%;
      animation: spin 1s linear infinite;
      flex-shrink: 0;
    }
  }
}

.character-item {
  background: var(--background-primary);
  border-radius: 8px;
  padding: 0.75rem;
  border: 1px solid transparent;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease;

  &:hover {
    border-color: rgba(99, 102, 241, 0.4);
    transform: translateY(-1px);
  }

  &.active {
    border-color: var(--primary-color);
    box-shadow: 0 4px 12px rgba(99, 102, 241, 0.2);
  }

  .item-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 0.375rem;
    
    h4 {
      margin: 0;
      font-size: 0.95rem;
      color: var(--text-primary);
    }
    .tag {
      font-size: 0.7rem;
      padding: 0.125rem 0.375rem;
      background: rgba(99, 102, 241, 0.18);
      color: var(--primary-color);
      border-radius: 999px;
    }
  }

  .item-desc {
    margin: 0 0 0.375rem;
    color: var(--text-secondary);
    font-size: 0.8rem;
    line-height: 1.3;
  }

  .item-meta {
    display: flex;
    justify-content: flex-end;
    font-size: 0.75rem;
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
  padding: 1rem 1.5rem;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;

  h2 {
    margin: 0;
    font-size: 1.3rem;
    color: var(--text-primary);
  }

  p {
    margin: 0.25rem 0 0;
    color: var(--text-secondary);
    font-size: 0.9rem;
  }

  // 标题显示区域
  .chat-title-area {
    margin-top: 0.5rem;
    
    .chat-title {
      display: inline-block;
      font-size: 0.95rem;
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
    display: flex;
    align-items: center;
    gap: 0.5rem;

    &.active {
      background: rgba(34, 197, 94, 0.18);
      color: #22c55e;
    }

    &.connecting {
      background: rgba(251, 191, 36, 0.18);
      color: #f59e0b;
    }

    .connecting-spinner {
      width: 12px;
      height: 12px;
      border: 2px solid transparent;
      border-top: 2px solid currentColor;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
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
  gap: 0.75rem;
  max-width: 80%;
  align-items: flex-start;

  &.assistant {
    align-self: flex-start;
    flex-direction: row;
  }

  &.user {
    align-self: flex-end;
    flex-direction: row-reverse;
  }

  .message-avatar {
    flex-shrink: 0;
    
    .avatar {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      object-fit: cover;
      border: 2px solid var(--border-light);
      
      &.default-avatar {
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 0.8rem;
        font-weight: 600;
        color: white;
        
        &.user-avatar {
          background: linear-gradient(135deg, var(--primary-color), var(--primary-dark));
        }
        
        &.assistant-avatar {
          background: linear-gradient(135deg, #10b981, #059669);
        }
      }
    }
  }

  .message-content {
    flex: 1;
    min-width: 0;
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

/* 内联播放按钮样式 */
.inline-play-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  margin-left: 0.5rem;
  border-radius: 3px;
  transition: all 0.2s ease;
  vertical-align: baseline;

  &:hover {
    color: var(--primary-color);
    background: rgba(99, 102, 241, 0.1);
  }

  &.playing {
    color: #ef4444;
    
    &:hover {
      color: #dc2626;
      background: rgba(239, 68, 68, 0.1);
    }
  }

  svg {
    width: 14px;
    height: 14px;
  }
}

/* 字幕高亮效果 */
.subtitle-highlighted {
  background: linear-gradient(120deg, #3b82f6 0%, #8b5cf6 100%);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
  font-weight: 500;
  transition: all 0.1s ease;
}

.subtitle-remaining {
  color: var(--text-secondary);
  opacity: 0.7;
  transition: all 0.1s ease;
}

/* 多段音频样式 */
.multi-audio-segments {
  margin-top: 0.5rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;

  .segment-item {
    display: flex;
    align-items: center;
    gap: 0.25rem;
    padding: 0.25rem 0.5rem;
    background: rgba(99, 102, 241, 0.05);
    border-radius: 6px;
    border: 1px solid rgba(99, 102, 241, 0.1);

    .segment-play-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 16px;
      height: 16px;
      border: none;
      background: transparent;
      color: #9ca3af;
      cursor: pointer;
      border-radius: 2px;
      transition: all 0.2s ease;

      &:hover {
        color: var(--primary-color);
      }

      &.playing {
        color: #ef4444;
      }

      svg {
        width: 12px;
        height: 12px;
      }
    }

    .segment-label {
      font-size: 0.75rem;
      color: var(--text-secondary);
      font-weight: 500;
    }
  }
}

.image-gallery {
  margin-top: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;

  .image-container {
    border-radius: 12px;
    overflow: hidden;
    border: 2px solid var(--border-light);
    transition: border-color 0.3s ease, transform 0.2s ease;

    &.self-portrait {
      border-color: #f59e0b;
      background: linear-gradient(135deg, #fef3c7, #fde68a);
    }

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
    }

    .image-placeholder {
      padding: 2rem;
      text-align: center;
      background: linear-gradient(135deg, #f3f4f6, #e5e7eb);
      min-height: 200px;
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;

      .loading-spinner {
        width: 40px;
        height: 40px;
        border: 4px solid #e5e7eb;
        border-top: 4px solid #6366f1;
        border-radius: 50%;
        animation: spin 1s linear infinite;
        margin-bottom: 1rem;
      }

      .loading-text {
        font-size: 1rem;
        font-weight: 600;
        color: #6b7280;
        margin-bottom: 0.5rem;
      }

      .description {
        font-size: 0.875rem;
        color: #9ca3af;
        font-style: italic;
        max-width: 300px;
        word-wrap: break-word;
      }
    }

    .generated-image {
      position: relative;

      .ai-generated-img {
        width: 100%;
        height: auto;
        max-height: 400px;
        object-fit: cover;
        cursor: pointer;
        transition: transform 0.2s ease;

        &:hover {
          transform: scale(1.02);
        }
      }

      .image-caption {
        padding: 0.75rem;
        background: rgba(255, 255, 255, 0.95);
        backdrop-filter: blur(8px);
        border-top: 1px solid var(--border-light);
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 0.5rem;

        .image-type {
          font-size: 0.875rem;
          font-weight: 600;
          color: #6366f1;
          background: rgba(99, 102, 241, 0.1);
          padding: 0.25rem 0.5rem;
          border-radius: 6px;
          white-space: nowrap;
        }

        .image-desc {
          font-size: 0.875rem;
          color: #6b7280;
          flex: 1;
          text-align: right;
          font-style: italic;
          word-wrap: break-word;
          overflow: hidden;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
        }
      }
    }
  }
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
    box-shadow: 0 10px 24px rgba(239, 68, 68, 0.3);
  }
  50% {
    transform: scale(1.05);
    box-shadow: 0 15px 35px rgba(239, 68, 68, 0.5);
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
    display: flex;
    align-items: center;
    gap: 1rem;
    color: #ef4444;
    font-weight: 500;

    .recording-text {
      font-size: 0.9rem;
    }

    .recording-time {
      font-size: 0.85rem;
      opacity: 0.8;
    }

    .audio-visualizer {
      display: flex;
      align-items: center;
      gap: 2px;
      height: 20px;
      min-width: 100px;

      .audio-bar {
        width: 3px;
        background: linear-gradient(to top, #ef4444, #fca5a5);
        border-radius: 2px;
        min-height: 2px;
        transition: height 0.1s ease;
        animation: audioWave 1.5s ease-in-out infinite;
      }
    }
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
    display: flex;
    align-items: center;
    gap: 0.5rem;
    border-radius: 50px;
    padding: 0.8rem 1.6rem;
    position: relative;

    &.recording {
      background: #ef4444;
      box-shadow: 0 10px 24px rgba(239, 68, 68, 0.3);
      animation: pulse 2s infinite;
    }

    .mic-text {
      font-size: 0.9rem;
      font-weight: 500;
    }
  }

  .new-chat-btn {
    background: var(--background-secondary);
    color: var(--text-primary);
    border: 1px solid var(--border-light);

    &:hover:not(:disabled) {
      background: var(--background-tertiary);
      border-color: var(--primary-color);
    }
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

/* 头部顶部布局 */
.header-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 1rem;
  margin-bottom: 0.25rem;
}

/* 语言设置样式 */
.language-settings {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.language-item {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.language-label {
  font-size: 0.8rem;
  color: var(--text-secondary);
  font-weight: 500;
  white-space: nowrap;
}

.language-select {
  padding: 0.25rem 0.125rem;
  border: none;
  border-bottom: 1px solid var(--border-light);
  border-radius: 0;
  background: transparent;
  color: var(--text-primary);
  font-size: 0.8rem;
  min-width: 60px;
  
  &:focus {
    outline: none;
    border-bottom-color: var(--primary-color);
  }
  
  &:hover {
    border-bottom-color: var(--text-secondary);
  }
}

/* 闪烁光标动画 */
/* 历史加载动画样式 */
.history-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 3rem 1rem;
  color: var(--text-secondary);
  font-size: 0.9rem;
  
  .history-loading-spinner {
    width: 24px;
    height: 24px;
    border: 3px solid var(--border-light);
    border-top: 3px solid var(--primary-color);
    border-radius: 50%;
    animation: spin 1s linear infinite;
  }
}

/* 场景描述样式 */
.scene-description {
  padding: 2rem 1rem;
  text-align: center;
  
  .scenario-text,
  .default-scene {
    font-style: italic;
    color: var(--text-tertiary);
    font-size: 0.95rem;
    line-height: 1.5;
    opacity: 0.8;
    
    em {
      font-style: italic;
    }
  }
}

@keyframes blink {
  0%, 50% {
    opacity: 1;
  }
  51%, 100% {
    opacity: 0;
  }
}

@keyframes audioWave {
  0%, 100% {
    transform: scaleY(0.3);
  }
  50% {
    transform: scaleY(1);
  }
}
</style>
