<template>
  <div class="character-market">
    <!-- 第一层：标题和新建按钮 -->
    <div class="market-header">
      <h1 class="market-title">角色市场</h1>
      <button @click="handleCreateNew" class="new-character-btn">
        <svg class="plus-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
          <line x1="12" y1="5" x2="12" y2="19"></line>
          <line x1="5" y1="12" x2="19" y2="12"></line>
        </svg>
        新建角色
      </button>
    </div>

    <!-- 第二层：搜索框 -->
    <div class="search-section">
      <div class="search-wrapper">
        <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
          <circle cx="11" cy="11" r="8"></circle>
          <path d="m21 21-4.35-4.35"></path>
        </svg>
        <input
          v-model="searchKeyword"
          type="text"
          class="search-input"
          placeholder="搜索角色名称、描述或作者..."
          @keyup.enter="handleSearch"
          @input="debouncedSearch"
        />
      </div>
    </div>

    <!-- 第三层：筛选选项 -->
    <div class="filter-section">
      <div class="filter-tabs">
        <button
          v-for="tab in sortTabs"
          :key="tab.id"
          :class="['filter-tab', { active: activeSort === tab.id }]"
          @click="handleSortChange(tab.id)"
        >
          {{ tab.label }}
        </button>
      </div>
    </div>

    <!-- 第四层：角色卡 -->
    <div class="cards-section">
      <!-- 加载状态 -->
      <div v-if="loading && cards.length === 0" class="loading-state">
        <div class="loading-spinner"></div>
        <p class="loading-text">正在加载角色卡...</p>
      </div>

      <!-- 空状态 -->
      <div v-else-if="!loading && cards.length === 0" class="empty-state">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
            <line x1="16" y1="2" x2="16" y2="6"></line>
            <line x1="8" y1="2" x2="8" y2="6"></line>
            <line x1="3" y1="10" x2="21" y2="10"></line>
          </svg>
        </div>
        <p class="empty-text">暂时没有找到角色卡</p>
        <p class="empty-desc">试试调整筛选条件或创建第一个角色吧</p>
      </div>

      <!-- 角色卡网格 -->
      <div v-else class="cards-grid">
        <CharacterCard
          v-for="card in cards"
          :key="card.id"
          :card="card"
          @detail="handleCardDetail"
          @like="handleLike"
          @delete="handleDelete"
        />
      </div>

      <!-- 无缝滚动换页检测区域 -->
      <div v-if="hasMore" ref="loadTrigger" class="load-trigger"></div>

      <!-- 加载更多指示器 -->
      <div v-if="loading && cards.length > 0" class="load-more-indicator">
        <div class="loading-spinner"></div>
        <span>加载更多...</span>
      </div>

      <!-- 到底提示 -->
      <div v-if="!loading && !hasMore && cards.length > 0" class="bottom-tip">
        <div class="bottom-tip-line"></div>
        <span class="bottom-tip-text">已经到底了</span>
        <div class="bottom-tip-line"></div>
      </div>
    </div>

    <!-- 回顶按钮 -->
    <transition name="fade">
      <button 
        v-if="showBackToTop" 
        @click="scrollToTop" 
        class="back-to-top-btn"
        title="回到顶部"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
          <polyline points="18,15 12,9 6,15"></polyline>
        </svg>
      </button>
    </transition>

    <!-- 角色详情弹窗 -->
    <transition name="modal">
      <div v-if="showDetailModal" class="character-detail-modal">
        <div class="modal-backdrop" @click="closeDetailModal"></div>
        <div class="modal-content-wrapper" @click.stop>
          <div class="modal-header">
            <h2>{{ editMode ? '编辑角色' : '角色详情' }}</h2>
            <div class="header-actions">
              <button v-if="canEdit && editMode" @click="cancelEdit" class="cancel-edit-btn">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                  <line x1="18" y1="6" x2="6" y2="18"></line>
                  <line x1="6" y1="6" x2="18" y2="18"></line>
                </svg>
                取消
              </button>
              <button v-if="canEdit" @click="toggleEditMode" :class="['edit-toggle-btn', { active: editMode, saving: saving }]" :disabled="saving">
                <svg v-if="!editMode" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                  <path d="m18 2 4 4-8 8-8-4 12-8z"/>
                  <path d="M10.5 8.5L20 18l-6 6-8.5-8.5"/>
                </svg>
                <svg v-else-if="!saving" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                  <polyline points="20,6 9,17 4,12"/>
                </svg>
                <div v-else class="loading-spinner"></div>
                {{ editMode ? (saving ? '保存中' : '保存') : '编辑' }}
              </button>
              <button @click="closeDetailModal" class="close-btn">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                  <line x1="18" y1="6" x2="6" y2="18"></line>
                  <line x1="6" y1="6" x2="18" y2="18"></line>
                </svg>
              </button>
            </div>
          </div>
          
          <div class="modal-content">
            <div class="character-form">
              <!-- 基础信息 -->
              <div class="form-section">
                <div class="section-title">基础信息</div>
                
                <!-- 头像编辑 -->
                <div class="form-group" v-if="editMode">
                  <label>角色头像</label>
                  <div class="avatar-edit-section">
                    <!-- 头像预览 -->
                    <div class="avatar-preview">
                      <div class="avatar-container" :class="{ 'has-image': editForm.avatarUrl || selectedCard?.avatarUrl, 'uploading': uploadingAvatar }">
                        <div v-if="uploadingAvatar" class="uploading-overlay">
                          <div class="upload-spinner"></div>
                          <span class="upload-text">上传中...</span>
                        </div>
                        <!-- 显示编辑中的头像或原有头像 -->
                        <img v-else-if="editForm.avatarUrl || selectedCard?.avatarUrl" 
                             :src="editForm.avatarUrl || selectedCard?.avatarUrl" 
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
                    </div>
                    
                    <!-- 上传和URL输入 -->
                    <div class="avatar-controls">
                      <!-- 上传按钮状态 -->
                      <div class="upload-section">
                        <input ref="avatarFileInput" 
                               type="file" 
                               accept="image/*" 
                               @change="handleAvatarFileSelect"
                               :disabled="uploadedAvatarMode === 'url'"
                               style="display: none;" />
                        <button type="button" 
                                @click="triggerAvatarFileInput" 
                                :disabled="uploadedAvatarMode === 'url'"
                                :class="{ 'disabled': uploadedAvatarMode === 'url' }"
                                class="upload-btn">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                            <path d="M21 15V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V15" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                            <path d="M17 8L12 3L7 8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                            <path d="M12 3V15" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                          </svg>
                          {{ uploadedAvatarMode === 'url' ? '上传已禁用（使用了链接）' : '上传图片' }}
                        </button>
                      </div>
                      
                      <!-- URL输入 -->
                      <div class="url-input-section">
                        <input 
                          v-model="displayedEditAvatarUrl"
                          type="url"
                          class="form-input"
                          :class="{ 'disabled': uploadedAvatarMode === 'uploaded' }"
                          :disabled="uploadedAvatarMode === 'uploaded'"
                          :readonly="uploadedAvatarMode === 'uploaded'"
                          placeholder="或输入图片链接"
                          maxlength="500"
                        />
                        <span v-if="uploadedAvatarMode === 'uploaded'" class="input-disabled-hint">
                          （已有上传文件，链接输入已禁用）
                        </span>
                      </div>
                      
                      <!-- 清除按钮 -->
                      <button v-if="editForm.avatarUrl" type="button" @click="clearAvatar" class="clear-avatar-btn">
                        清除
                      </button>
                    </div>
                  </div>
                </div>
                
                <!-- 只读模式显示头像 -->
                <div class="form-group" v-else>
                  <label>角色头像</label>
                  <div class="avatar-display">
                    <div class="avatar-container" :class="{ 'has-image': selectedCard?.avatarUrl }">
                      <img v-if="selectedCard?.avatarUrl" 
                           :src="selectedCard.avatarUrl" 
                           alt="角色头像" 
                           class="avatar-image" />
                      <div v-else class="avatar-placeholder">
                        <svg class="avatar-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                          <path d="M12 12C14.7614 12 17 9.76142 17 7C17 4.23858 14.7614 2 12 2C9.23858 2 7 4.23858 7 7C7 9.76142 9.23858 12 12 12Z" stroke="currentColor" stroke-width="2"/>
                          <path d="M20.5899 22C20.5899 18.13 16.7399 15 11.9999 15C7.25991 15 3.40991 18.13 3.40991 22" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                        </svg>
                        <span class="avatar-text">无头像</span>
                      </div>
                    </div>
                  </div>
                </div>
                
                <div class="form-group">
                  <label>角色名称</label>
                  <input 
                    v-if="editMode" 
                    v-model="editForm.name" 
                    class="form-input"
                    placeholder="输入角色名称"
                    maxlength="100"
                  />
                  <div v-else class="form-value">{{ selectedCard?.name || '-' }}</div>
                </div>
                
                <div class="form-group">
                  <label>简短描述</label>
                  <textarea 
                    v-if="editMode" 
                    v-model="editForm.shortDescription" 
                    class="form-textarea"
                    placeholder="输入简短描述"
                    rows="2"
                    maxlength="500"
                  ></textarea>
                  <div v-else class="form-value">{{ selectedCard?.shortDescription || '-' }}</div>
                </div>
                
                <div class="form-group">
                  <label>问候语</label>
                  <textarea 
                    v-if="editMode" 
                    v-model="editForm.greetingMessage" 
                    class="form-textarea"
                    placeholder="输入问候语"
                    rows="3"
                    maxlength="1000"
                  ></textarea>
                  <div v-else class="form-value">{{ selectedCard?.greetingMessage || '-' }}</div>
                </div>
                
                <div class="form-group" v-if="editMode">
                  <label>
                    <input type="checkbox" v-model="editForm.isPublic" class="form-checkbox" />
                    公开角色卡
                  </label>
                </div>
                
                <div class="form-group" v-if="editMode">
                  <label>语音音色</label>
                  <VoiceSelector
                    v-model="editForm.ttsVoiceId"
                    :voices="availableVoices"
                    placeholder="选择语音音色（可选）"
                  />
                </div>
              </div>
              
              <!-- 角色卡数据 -->
              <div class="form-section">
                <div class="section-title">角色卡数据</div>
                
                <div class="form-group">
                  <label>详细描述</label>
                  <textarea 
                    v-if="editMode" 
                    v-model="editForm.cardData.description" 
                    class="form-textarea"
                    placeholder="输入角色的详细描述"
                    rows="4"
                    maxlength="5000"
                  ></textarea>
                  <div v-else class="form-value">{{ selectedCard?.cardData?.description || '-' }}</div>
                </div>
                
                <div class="form-group">
                  <label>性格特征</label>
                  <textarea 
                    v-if="editMode" 
                    v-model="editForm.cardData.personality" 
                    class="form-textarea"
                    placeholder="描述角色的性格特征"
                    rows="3"
                    maxlength="2000"
                  ></textarea>
                  <div v-else class="form-value">{{ selectedCard?.cardData?.personality || '-' }}</div>
                </div>
                
                <div class="form-group">
                  <label>设定场景</label>
                  <textarea 
                    v-if="editMode" 
                    v-model="editForm.cardData.scenario" 
                    class="form-textarea"
                    placeholder="描述角色所处的场景"
                    rows="3"
                    maxlength="3000"
                  ></textarea>
                  <div v-else class="form-value">{{ selectedCard?.cardData?.scenario || '-' }}</div>
                </div>
              </div>
              
              <!-- 对话示例 -->
              <div class="form-section" v-if="editMode">
                <div class="section-title">
                  对话示例
                  <button type="button" @click="addExampleDialog" class="add-btn">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                      <line x1="12" y1="5" x2="12" y2="19"></line>
                      <line x1="5" y1="12" x2="19" y2="12"></line>
                    </svg>
                    添加
                  </button>
                </div>
                
                <div v-for="(dialog, index) in editForm.cardData.exampleDialogs" :key="index" class="dialog-group">
                  <div class="dialog-header">
                    <span>对话示例 {{ index + 1 }}</span>
                    <button type="button" @click="removeExampleDialog(index)" class="remove-btn">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                        <line x1="18" y1="6" x2="6" y2="18"></line>
                        <line x1="6" y1="6" x2="18" y2="18"></line>
                      </svg>
                    </button>
                  </div>
                  
                  <div class="form-group">
                    <label>用户</label>
                    <input 
                      v-model="dialog.user" 
                      class="form-input"
                      placeholder="用户说的话"
                      maxlength="1000"
                    />
                  </div>
                  
                  <div class="form-group">
                    <label>助手</label>
                    <input 
                      v-model="dialog.assistant" 
                      class="form-input"
                      placeholder="助手的回复"
                      maxlength="1000"
                    />
                  </div>
                </div>
              </div>
              
              <!-- 元信息 -->
              <div class="form-section" v-if="!editMode">
                <div class="section-title">元信息</div>
                
                <div class="form-group">
                  <label>创建者</label>
                  <div class="form-value">{{ selectedCard?.creatorUsername || '-' }}</div>
                </div>
                
                <div class="form-group">
                  <label>创建时间</label>
                  <div class="form-value">{{ selectedCard ? formatFullDate(selectedCard.createdAt) : '-' }}</div>
                </div>
                
                <div class="form-group">
                  <label>点赞数</label>
                  <div class="form-value">{{ selectedCard?.likesCount || 0 }}</div>
                </div>
                
                <div class="form-group">
                  <label>公开状态</label>
                  <div class="form-value">{{ selectedCard?.isPublic ? '公开' : '私有' }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import { ref, reactive, computed, onMounted, nextTick, onUnmounted } from 'vue'
import { characterCardAPI, uploadAPI, ttsAPI } from '@/api'
import { debounce, storage } from '@/utils'
import CharacterCard from './CharacterCard.vue'
import VoiceSelector from './VoiceSelector.vue'

export default {
  name: 'CharacterMarket',
  components: {
    CharacterCard,
    VoiceSelector
  },
  emits: ['create-new'],
  setup(props, { emit }) {
    const cards = ref([])
    const loading = ref(false)
    const searchKeyword = ref('')
    const activeSort = ref('public')
    const loadTrigger = ref(null)
    const observer = ref(null)
    const showBackToTop = ref(false)
    
    // 详情弹窗相关
    const showDetailModal = ref(false)
    const selectedCard = ref(null)
    const editMode = ref(false)
    const saving = ref(false) // 保存状态
    const uploadingAvatar = ref(false) // 头像上传状态
    const avatarFileInput = ref(null) // 文件输入引用
    const uploadedAvatarMode = ref('') // 'uploaded' | 'url' | ''
    const availableVoices = ref([]) // 音色列表    // 显示的头像URL - 编辑模式下的逻辑
    const displayedEditAvatarUrl = computed({
      get() {
        // 编辑模式下URL输入框始终为空，不显示原有URL（安全考虑）
        // 除非用户手动输入了新的URL
        if (uploadedAvatarMode.value === 'uploaded') {
          return '' // 上传模式下不显示URL
        }
        if (uploadedAvatarMode.value === 'url') {
          return editForm.avatarUrl // 用户输入的新URL
        }
        return '' // 默认为空，不显示原有URL
      },
      set(value) {
        if (uploadedAvatarMode.value !== 'uploaded') {
          editForm.avatarUrl = value
          uploadedAvatarMode.value = value ? 'url' : ''
        }
      }
    })
    const editForm = reactive({
      name: '',
      shortDescription: '',
      greetingMessage: '',
      isPublic: true,
      ttsVoiceId: '',
      avatarUrl: '', // 新增头像字段
      cardData: {
        description: '',
        personality: '',
        scenario: '',
        exampleDialogs: []
      }
    })
    
    const pagination = reactive({
      page: 0,
      size: 20,
      totalPages: 0,
      totalElements: 0,
      hasNext: false,
      hasPrevious: false,
      first: true,
      last: false,
      numberOfElements: 0
    })

    const sortTabs = [
      { id: 'public', label: '全部' },
      { id: 'popular', label: '热门' },
      { id: 'latest', label: '最新' },
      { id: 'my', label: '我的角色' },
      { id: 'liked', label: '我的点赞' }
    ]

    const hasMore = computed(() => {
      // 使用后端提供的hasNext字段，更准确
      return pagination.hasNext
    })

    // 计算是否可以编辑
    const canEdit = computed(() => {
      const user = storage.get('user')
      return user && selectedCard.value && selectedCard.value.creatorId === user.userId
    })

    // 防抖搜索
    const debouncedSearch = debounce(() => {
      handleSearch()
    }, 500)

    // 获取语音列表
    const loadVoices = async () => {
      try {
        const user = storage.get('user')
        if (!user || !user.userId) {
          console.warn('用户未登录，无法获取语音列表')
          availableVoices.value = []
          return
        }

        // 并发获取用户音色和公开音色
        const [userVoicesResponse, publicVoicesResponse] = await Promise.all([
          ttsAPI.getUserVoices(user.userId).catch(() => ({ data: [] })),
          ttsAPI.getPublicVoices().catch(() => ({ data: [] }))
        ])

        // 处理用户音色
        let userVoices = []
        if (Array.isArray(userVoicesResponse)) {
          userVoices = userVoicesResponse
        } else if (userVoicesResponse.data && Array.isArray(userVoicesResponse.data)) {
          userVoices = userVoicesResponse.data
        }

        // 处理公开音色
        let publicVoices = []
        if (Array.isArray(publicVoicesResponse)) {
          publicVoices = publicVoicesResponse
        } else if (publicVoicesResponse.data && Array.isArray(publicVoicesResponse.data)) {
          publicVoices = publicVoicesResponse.data
        }

        // 合并音色列表，去重（用户音色优先）
        const allVoices = [...userVoices]
        publicVoices.forEach(publicVoice => {
          if (!allVoices.find(voice => voice.id === publicVoice.id)) {
            allVoices.push(publicVoice)
          }
        })

        // 转换为选项格式
        availableVoices.value = allVoices.map(voice => ({
          id: voice.id,
          name: voice.description ? `${voice.name}（${voice.description}）` : voice.name
        }))

      } catch (error) {
        console.error('获取语音列表失败:', error)
        availableVoices.value = []
      }
    }

    const filteredVoices = computed(() => {
      const keyword = voiceSearch.value.trim().toLowerCase()
      if (!keyword) {
        return availableVoices.value
      }
      return availableVoices.value.filter(voice => {
        const name = (voice.name || '').toString().toLowerCase()
        const id = (voice.id || '').toString().toLowerCase()
        return name.includes(keyword) || id.includes(keyword)
      })
    })

    // 获取角色卡数据
    const fetchCards = async (reset = false) => {
      if (loading.value) return

      loading.value = true
      try {
        if (reset) {
          pagination.page = 0
          cards.value = []
        }

        const params = {
          page: pagination.page,
          size: pagination.size,
          keyword: searchKeyword.value.trim() || undefined
        }

        let response
        if (searchKeyword.value.trim()) {
          response = await characterCardAPI.searchCards(params)
        } else {
          switch (activeSort.value) {
            case 'popular':
              response = await characterCardAPI.getPopularCards(params)
              break
            case 'latest':
              response = await characterCardAPI.getLatestCards(params)
              break
            case 'my':
              response = await characterCardAPI.getMyCards(params)
              break
            case 'liked':
              response = await characterCardAPI.getLikedCards(params)
              break
            default:
              response = await characterCardAPI.getPublicCards(params)
          }
        }

        if (response && response.code === 200) {
          const { 
            content, 
            totalPages, 
            totalElements, 
            number,
            hasNext,
            hasPrevious,
            first,
            last,
            numberOfElements
          } = response.data
          
          // 确保每个卡片的 likesCount 字段是数字
          const processedContent = content.map(card => ({
            ...card,
            likesCount: typeof card.likesCount === 'number' ? card.likesCount : 0,
            isLikedByCurrentUser: Boolean(card.isLikedByCurrentUser)
          }))
          
          if (reset) {
            cards.value = processedContent
          } else {
            cards.value.push(...processedContent)
          }

          pagination.totalPages = totalPages
          pagination.totalElements = totalElements
          pagination.page = number
          pagination.hasNext = hasNext
          pagination.hasPrevious = hasPrevious
          pagination.first = first
          pagination.last = last
          pagination.numberOfElements = numberOfElements
        }
      } catch (error) {
        console.error('获取角色卡失败:', error)
      } finally {
        loading.value = false
      }
    }

    // 加载下一页
    const loadMore = async () => {
      if (hasMore.value && !loading.value) {
        pagination.page++
        await fetchCards()
      }
    }

    // 设置无缝滚动
    const setupInfiniteScroll = () => {
      if (!loadTrigger.value || observer.value) return

      observer.value = new IntersectionObserver(
        (entries) => {
          entries.forEach((entry) => {
            if (entry.isIntersecting && hasMore.value && !loading.value) {
              loadMore()
            }
          })
        },
        {
          root: null,
          rootMargin: '100px',
          threshold: 0.1
        }
      )

      observer.value.observe(loadTrigger.value)
    }

    // 处理搜索
    const handleSearch = () => {
      fetchCards(true)
    }

    // 处理排序切换
    const handleSortChange = (sort) => {
      if (activeSort.value !== sort) {
        activeSort.value = sort
        searchKeyword.value = '' // 切换排序时清除搜索
        fetchCards(true)
      }
    }

    // 处理卡片详情点击
    const handleCardDetail = (card) => {
      selectedCard.value = card
      showDetailModal.value = true
      // 初始化编辑表单数据，实现自动回显
      initEditForm(card)
    }

    // 关闭详情弹窗
    const closeDetailModal = () => {
      showDetailModal.value = false
      selectedCard.value = null
      editMode.value = false
    }

    // 初始化编辑表单数据
    const initEditForm = (card) => {
      if (!card) return
      
      editForm.name = card.name || ''
      editForm.shortDescription = card.shortDescription || ''
      editForm.greetingMessage = card.greetingMessage || ''
      editForm.isPublic = card.isPublic !== undefined ? card.isPublic : true
      editForm.ttsVoiceId = card.ttsVoiceId || ''
      // 保留原有头像URL用于显示
      editForm.avatarUrl = card.avatarUrl || ''
      
      // 重置上传状态
      uploadedAvatarMode.value = ''
      
      // 初始化cardData
      const cardData = card.cardData || {}
      editForm.cardData.description = cardData.description || ''
      editForm.cardData.personality = cardData.personality || ''
      editForm.cardData.scenario = cardData.scenario || ''
      
      // 初始化对话示例
      editForm.cardData.exampleDialogs = cardData.exampleDialogs ? 
        cardData.exampleDialogs.map(dialog => ({
          user: dialog.user || '',
          assistant: dialog.assistant || ''
        })) : []
    }

    // 切换编辑模式
    const toggleEditMode = async () => {
      if (editMode.value) {
        // 保存编辑
        if (saving.value) return // 防止重复点击
        await saveEdit()
      } else {
        // 进入编辑模式
        editMode.value = true
        initEditForm(selectedCard.value)
        // 加载音色列表
        await loadVoices()
      }
    }

    // 开始编辑（保持兼容）
    const startEdit = () => {
      editMode.value = true
      initEditForm(selectedCard.value)
    }

    // 取消编辑
    const cancelEdit = () => {
      editMode.value = false
      // 重新初始化表单数据，恢复到原始状态
      initEditForm(selectedCard.value)
    }

    // 添加对话示例
    const addExampleDialog = () => {
      editForm.cardData.exampleDialogs.push({
        user: '',
        assistant: ''
      })
    }

    // 删除对话示例
    const removeExampleDialog = (index) => {
      editForm.cardData.exampleDialogs.splice(index, 1)
    }

    // 保存编辑
    const saveEdit = async () => {
      try {
        saving.value = true
        const updateData = {
          name: editForm.name,
          shortDescription: editForm.shortDescription,
          greetingMessage: editForm.greetingMessage,
          isPublic: editForm.isPublic,
          ttsVoiceId: editForm.ttsVoiceId,
          avatarUrl: editForm.avatarUrl, // 添加头像URL字段
          cardData: {
            description: editForm.cardData.description,
            personality: editForm.cardData.personality,
            scenario: editForm.cardData.scenario,
            exampleDialogs: editForm.cardData.exampleDialogs.filter(dialog => 
              dialog.user.trim() || dialog.assistant.trim()
            )
          }
        }
        
        await characterCardAPI.update(selectedCard.value.id, updateData)
        
        // 更新本地数据
        Object.assign(selectedCard.value, updateData)
        const cardIndex = cards.value.findIndex(card => card.id === selectedCard.value.id)
        if (cardIndex !== -1) {
          Object.assign(cards.value[cardIndex], updateData)
        }
        
        editMode.value = false
      } catch (error) {
        console.error('保存失败:', error)
      } finally {
        saving.value = false
      }
    }

    // 格式化完整时间
    const formatFullDate = (dateString) => {
      if (!dateString) return ''
      
      const date = new Date(dateString)
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      })
    }

    // 处理点赞 - 修复字段名匹配问题
    const handleLike = async (cardId) => {
      try {
        // 先找到卡片
        const cardIndex = cards.value.findIndex(card => card.id === cardId)
        if (cardIndex === -1) return
        
        const card = cards.value[cardIndex]
        const wasLiked = card.isLikedByCurrentUser
        const currentLikesCount = typeof card.likesCount === 'number' ? card.likesCount : 0
        
        console.log('点赞前状态:', { cardId, wasLiked, currentLikesCount })
        
        // 调用API
        const response = await characterCardAPI.toggleLike(cardId)
        
        console.log('API响应:', response)
        
        // 根据API响应更新状态
        if (response && response.data) {
          // 兼容两种可能的字段名
          const newLikedStatus = response.data.isLiked !== undefined ? response.data.isLiked : response.data.liked
          const newLikesCount = response.data.likesCount
          
          console.log('解析后的状态:', { newLikedStatus, newLikesCount })
          
          card.isLikedByCurrentUser = newLikedStatus
          card.likesCount = typeof newLikesCount === 'number' ? newLikesCount : 0
          
          console.log('更新后的卡片状态:', { 
            isLiked: card.isLikedByCurrentUser, 
            likesCount: card.likesCount 
          })
        } else {
          // 如果API没有返回详细信息，则根据之前状态切换
          card.isLikedByCurrentUser = !wasLiked
          card.likesCount = Math.max(0, currentLikesCount + (wasLiked ? -1 : 1))
          console.log('使用备用逻辑更新状态')
        }
        
        // 同步更新详情弹窗中的数据
        if (selectedCard.value && selectedCard.value.id === cardId) {
          selectedCard.value.isLikedByCurrentUser = card.isLikedByCurrentUser
          selectedCard.value.likesCount = card.likesCount
        }
        
      } catch (error) {
        console.error('点赞操作失败:', error)
        // 出错时恢复原状态
        const cardIndex = cards.value.findIndex(card => card.id === cardId)
        if (cardIndex !== -1) {
          const card = cards.value[cardIndex]
          card.likesCount = typeof card.likesCount === 'number' ? card.likesCount : 0
        }
      }
    }

    // 处理删除
    const handleDelete = async (card) => {
      try {
        await characterCardAPI.delete(card.id)
        cards.value = cards.value.filter(c => c.id !== card.id)
      } catch (error) {
        console.error('删除失败:', error)
      }
    }

    // 处理新建角色
    const handleCreateNew = () => {
      emit('create-new')
    }

    // 回到顶部
    const scrollToTop = () => {
      window.scrollTo({
        top: 0,
        behavior: 'smooth'
      })
    }

    // 监听滚动事件
    const handleScroll = () => {
      showBackToTop.value = window.scrollY > 400
    }

    // 初始化
    onMounted(async () => {
      await fetchCards(true)
      await nextTick()
      setupInfiniteScroll()
      
      // 添加滚动监听
      window.addEventListener('scroll', handleScroll)
    })

    onUnmounted(() => {
      if (observer.value) {
        observer.value.disconnect()
        observer.value = null
      }
      
      // 移除滚动监听
      window.removeEventListener('scroll', handleScroll)
    })

    // 头像上传相关函数
    const triggerAvatarFileInput = () => {
      if (avatarFileInput.value) {
        avatarFileInput.value.click()
      }
    }

    const handleAvatarFileSelect = async (e) => {
      const files = e.target.files
      if (files.length > 0) {
        await handleAvatarFile(files[0])
      }
    }

    const handleAvatarFile = async (file) => {
      // 验证文件类型
      if (!file.type.startsWith('image/')) {
        console.error('请选择图片文件')
        return
      }

      // 验证文件大小 (5MB)
      if (file.size > 5 * 1024 * 1024) {
        console.error('图片大小不能超过5MB')
        return
      }

      uploadingAvatar.value = true
      
      try {
        const response = await uploadAPI.uploadAvatar(file)
        
        if (response && response.code === 200) {
          editForm.avatarUrl = response.data.url
          uploadedAvatarMode.value = 'uploaded' // 设置为上传模式
        } else {
          console.error(response.message || '头像上传失败')
        }
      } catch (error) {
        console.error('头像上传失败:', error)
      } finally {
        uploadingAvatar.value = false
      }
    }

    const clearAvatar = () => {
      editForm.avatarUrl = ''
      uploadedAvatarMode.value = ''
      if (avatarFileInput.value) {
        avatarFileInput.value.value = ''
      }
    }

    return {
      cards,
      loading,
      saving,
      searchKeyword,
      activeSort,
      sortTabs,
      hasMore,
      loadTrigger,
      showBackToTop,
      showDetailModal,
      selectedCard,
      editMode,
      editForm,
      canEdit,
      debouncedSearch,
      handleSearch,
      handleSortChange,
      handleCardDetail,
      handleLike,
      handleDelete,
      handleCreateNew,
      scrollToTop,
      closeDetailModal,
      toggleEditMode,
      startEdit,
      cancelEdit,
      saveEdit,
      addExampleDialog,
      removeExampleDialog,
      formatFullDate,
      // 音色相关
      availableVoices,
      loadVoices,
      // 头像相关
      uploadingAvatar,
      avatarFileInput,
      uploadedAvatarMode,
      displayedEditAvatarUrl,
      triggerAvatarFileInput,
      handleAvatarFileSelect,
      clearAvatar
    }
  }
}
</script>

<style lang="scss" scoped>
.character-market {
  max-width: 1800px !important; /* 强制应用更宽的宽度 */
  margin: 0 auto;
  padding: 2rem 3rem; /* 增加左右padding */
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 2rem;
  width: 100%; /* 确保占满可用宽度 */
}

// 移除自定义动画，使用Dashboard统一的tab-fade过渡

// 第一层：标题和新建按钮
.market-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0;
  margin: 0;
  max-width: 800px !important; /* 与搜索框保持一致的宽度 */
  width: 100%;
  margin: 0 auto; /* 居中显示 */
}

.market-title {
  // 使用与左侧边栏BrilliantTavern相同的字体和颜色
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--primary-color);
  margin: 0;
  transition: opacity 0.3s ease;
}

.new-character-btn {
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

  &:hover {
    transform: translateY(-1px);
  }

  .plus-icon {
    width: 1rem;
    height: 1rem;
    stroke-width: 2.5;
  }
}

// 第二层：搜索框
.search-section {
  width: 100%;
  display: flex;
  justify-content: center; /* 居中显示搜索框 */
}

.search-wrapper {
  position: relative;
  width: 100%;
  max-width: 800px; /* 限制搜索框最大宽度 */
}

.search-icon {
  position: absolute;
  left: 1rem;
  top: 50%;
  transform: translateY(-50%);
  width: 1.25rem;
  height: 1.25rem;
  color: var(--text-secondary);
  stroke-width: 2;
  z-index: 1;
}

.search-input {
  width: 100%;
  padding: 1rem 1rem 1rem 3rem;
  border: none;
  border-bottom: 2px solid var(--border-light);
  background: transparent;
  font-size: 1rem;
  color: var(--text-primary);
  outline: none;
  transition: border-color 0.2s ease;

  &::placeholder {
    color: var(--text-tertiary);
  }

  &:focus {
    border-bottom-color: var(--primary-color);
  }
}

// 第三层：筛选选项
.filter-section {
  width: 100%;
  display: flex;
  justify-content: center; /* 居中显示筛选标签 */
}

.filter-tabs {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  max-width: 600px; /* 限制筛选标签区域宽度 */
}

.filter-tab {
  padding: 0.75rem 1rem;
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  border-radius: 0.5rem;
  transition: all 0.2s ease;
  position: relative;

  &:hover,
  &:focus {
    background: var(--background-tertiary);
    color: var(--text-primary);
  }

  &.active {
    background: var(--background-tertiary);
    color: var(--primary-color);
    font-weight: 600;
  }
}

// 第四层：角色卡
.cards-section {
  flex: 1;
  width: 100%;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1.5rem;
  width: 100%;
  max-width: none; /* 角色卡区域不限制最大宽度，充分利用空间 */
  
  /* 在更宽的屏幕上显示更多列 */
  @media (min-width: 1400px) {
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 2rem;
  }
  
  @media (min-width: 1800px) {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 2rem;
  }
}

// 角色卡片动画
@keyframes slideInCard {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

// 加载和空状态
.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  text-align: center;
}

.loading-spinner {
  width: 2rem;
  height: 2rem;
  border: 3px solid var(--border-light);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 1rem;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-text {
  color: var(--text-secondary);
  font-size: 0.875rem;
  margin: 0;
}

.empty-icon {
  width: 3rem;
  height: 3rem;
  color: var(--text-tertiary);
  margin-bottom: 1rem;

  svg {
    width: 100%;
    height: 100%;
    stroke-width: 1.5;
  }
}

.empty-text {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 0.5rem 0;
}

.empty-desc {
  color: var(--text-secondary);
  font-size: 0.875rem;
  margin: 0;
}

// 无缝滚动触发区域
.load-trigger {
  height: 1px;
  width: 100%;
}

// 加载更多指示器
.load-more-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 2rem 0;
  color: var(--text-secondary);
  font-size: 0.875rem;

  .loading-spinner {
    width: 1.25rem;
    height: 1.25rem;
    border-width: 2px;
    margin: 0;
  }
}

// 响应式设计
@media (max-width: 768px) {
  .character-market {
    padding: 1rem;
    gap: 1.5rem;
  }

  .market-header {
    flex-direction: column;
    align-items: stretch;
    gap: 1rem;
    max-width: none; /* 移动端不限制宽度 */
  }

  .market-title {
    font-size: 1.5rem;
    text-align: center;
  }

  .new-character-btn {
    align-self: center;
  }

  .search-wrapper {
    max-width: none; /* 移动端搜索框占满宽度 */
  }

  .filter-tabs {
    justify-content: center;
    max-width: none; /* 移动端筛选标签不限制宽度 */
  }

  .cards-grid {
    grid-template-columns: 1fr;
    gap: 1rem;
  }
}

// 到底提示
.bottom-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 2rem 0;
  margin-top: 1rem;
}

.bottom-tip-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(to right, transparent, var(--border-light), transparent);
  max-width: 100px;
}

.bottom-tip-text {
  color: var(--text-tertiary);
  font-size: 0.75rem;
  white-space: nowrap;
  padding: 0 0.5rem;
}

// 回顶按钮
.back-to-top-btn {
  position: fixed;
  right: 2rem;
  bottom: 2rem;
  width: 3rem;
  height: 3rem;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  transition: all 0.3s ease;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    background: var(--primary-hover);
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.2);
  }

  &:active {
    transform: translateY(-1px);
  }

  svg {
    width: 1.25rem;
    height: 1.25rem;
    stroke-width: 2.5;
  }
}

// 过渡动画
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

// 移动端回顶按钮
@media (max-width: 768px) {
  .back-to-top-btn {
    right: 1rem;
    bottom: 1rem;
    width: 2.5rem;
    height: 2.5rem;

    svg {
      width: 1rem;
      height: 1rem;
    }
  }
}
</style>

<!-- 全局样式覆盖，确保宽度生效 -->
<style lang="scss">
.character-market {
  max-width: 1800px !important;
  width: 100% !important;
  padding: 2rem 3rem !important;
}

.character-market .market-header {
  max-width: 800px !important; /* 与搜索框保持一致 */
}

.character-market .characters-grid {
  max-width: none !important;
  width: 100% !important;
}

// 详情弹窗样式
.character-detail-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}

.modal-backdrop {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  z-index: 1;
}

.modal-content-wrapper {
  background: var(--background-primary);
  border-radius: 16px;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.15);
  width: 100%;
  max-width: 900px;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 10;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2rem 2rem 1rem 2rem;
  background: var(--background-primary);

  h2 {
    margin: 0;
    color: var(--text-primary);
    font-size: 1.5rem;
    font-weight: 700;
  }

  .header-actions {
    display: flex;
    flex-direction: row; /* 确保按钮水平排列 */
    align-items: center;
    gap: 0.75rem;
  }

  .edit-toggle-btn {
    display: flex;
    align-items: center; /* 恢复center对齐 */
    justify-content: center;
    flex-direction: row; /* 确保水平排列 */
    gap: 0.5rem;
    background: var(--primary-color);
    color: white;
    border: none;
    padding: 0.75rem 1rem;
    border-radius: 8px;
    font-size: 0.875rem;
    font-weight: 500;
    line-height: 1; /* 确保文字垂直居中 */
    cursor: pointer;
    transition: background-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
    min-width: 80px; /* 最小宽度 */
    height: 40px; /* 固定高度 */
    box-sizing: border-box;
    white-space: nowrap; /* 防止文字换行 */

    &:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    }

    &.active {
      background: #f59e0b; /* 黄色调 */
      min-width: 88px; /* 保存状态稍微宽一些 */
      
      &.saving {
        background: #d97706; /* 更深的黄色调 */
        cursor: not-allowed;
      }
    }

    &:disabled {
      opacity: 0.7;
      cursor: not-allowed;
    }

    svg, .loading-spinner {
      width: 16px;
      height: 16px;
      flex-shrink: 0; /* 防止图标压缩 */
      display: inline-block; /* 统一为inline-block */
      vertical-align: middle; /* 垂直居中对齐 */
    }

    .loading-spinner {
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-radius: 50%;
      border-top-color: white;
      animation: spin 1s linear infinite;
      box-sizing: border-box; /* 确保border计算正确 */
      margin: 0; /* 保持与文本和图标居中对齐 */
    }
  }

  .cancel-edit-btn {
    display: flex;
    align-items: center;
    flex-direction: row; /* 确保水平排列 */
    gap: 0.5rem;
    background: var(--background-secondary);
    color: var(--text-secondary);
    border: none;
    padding: 0.75rem 1rem;
    border-radius: 8px;
    font-size: 0.875rem;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      background: var(--background-tertiary);
      color: var(--text-primary);
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    }

    svg {
      width: 16px;
      height: 16px;
    }
  }

  .close-btn {
    background: var(--background-secondary);
    border: none;
    color: var(--text-secondary);
    cursor: pointer;
    padding: 0.75rem;
    border-radius: 8px;
    transition: all 0.2s ease;

    &:hover {
      background: var(--background-tertiary);
      color: var(--text-primary);
    }

    svg {
      width: 20px;
      height: 20px;
    }
  }
}

.modal-content {
  flex: 1;
  overflow-y: auto;
  padding: 0 2rem 2rem 2rem;
}

.character-form {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;

  .section-title {
    font-size: 1.125rem;
    font-weight: 600;
    color: var(--text-primary);
    margin: 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .add-btn {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    background: var(--background-secondary);
    color: var(--text-primary);
    border: none;
    padding: 0.5rem 1rem;
    border-radius: 6px;
    font-size: 0.875rem;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      background: var(--background-tertiary);
    }

    svg {
      width: 14px;
      height: 14px;
    }
  }
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;

  label {
    font-weight: 500;
    color: var(--text-primary);
    font-size: 0.9rem;
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .form-checkbox {
    width: auto;
    margin: 0;
  }

  .form-value {
    color: var(--text-secondary);
    line-height: 1.5;
    min-height: 1.5em;
    padding: 0.75rem 0;
  }

  .form-input,
  .form-textarea,
  .form-select {
    padding: 1rem;
    background: var(--background-secondary);
    color: var(--text-primary);
    border: none;
    border-radius: 8px;
    font-size: 0.875rem;
    font-family: inherit;
    transition: all 0.2s ease;
    resize: none;

    &:focus {
      outline: none;
      background: var(--background-tertiary);
      box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
    }

    &::placeholder {
      color: var(--text-tertiary);
    }
  }

  .form-select {
    appearance: none;
    cursor: pointer;
    background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6,9 12,15 18,9'%3e%3c/polyline%3e%3c/svg%3e");
    background-repeat: no-repeat;
    background-position: right 1rem center;
    background-size: 1rem;
    padding-right: 3rem;
  }

  .form-textarea {
    resize: vertical;
    min-height: 80px;
    line-height: 1.5;
  }
}

.dialog-group {
  background: var(--background-secondary);
  border-radius: 8px;
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;

  .dialog-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    span {
      font-weight: 500;
      color: var(--text-primary);
    }

    .remove-btn {
      background: none;
      border: none;
      color: var(--text-tertiary);
      cursor: pointer;
      padding: 0.25rem;
      border-radius: 4px;
      transition: all 0.2s ease;

      &:hover {
        color: #ef4444;
        background: rgba(239, 68, 68, 0.1);
      }

      svg {
        width: 16px;
        height: 16px;
      }
    }
  }
}

// 弹窗动画
.modal-enter-active,
.modal-leave-active {
  transition: all 0.3s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
  transform: scale(0.9);
}

.modal-enter-active .character-detail-modal,
.modal-leave-active .character-detail-modal {
  transition: all 0.3s ease;
}

.modal-enter-from .character-detail-modal,
.modal-leave-to .character-detail-modal {
  transform: translateY(-50px);
  opacity: 0;
}

// 加载动画
@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

// 头像编辑相关样式
.avatar-edit-section {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.avatar-display {
  display: flex;
  align-items: center;
}

.avatar-preview,
.avatar-display {
  flex-shrink: 0;
}

.avatar-container {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--background-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--border-color);
  transition: border-color 0.2s;
  position: relative;

  &.has-image {
    border-color: var(--primary-color);
  }

  &.uploading {
    border-color: var(--primary-color);
  }
}

.uploading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(255, 255, 255, 0.9);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
  z-index: 2;
  font-size: 0.75rem;
}

.upload-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid var(--border-color);
  border-top: 2px solid var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
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
  gap: 0.25rem;
  color: var(--text-tertiary);
}

.avatar-icon {
  width: 24px;
  height: 24px;
}

.avatar-text {
  font-size: 0.75rem;
}

.avatar-controls {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.upload-section {
  display: flex;
  align-items: center;
}

.upload-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: var(--background-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s;

  &:hover:not(.disabled) {
    background: var(--background-primary);
    border-color: var(--primary-color);
  }

  &.disabled {
    background: var(--background-tertiary);
    color: var(--text-tertiary);
    cursor: not-allowed;
    opacity: 0.6;
  }

  svg {
    width: 16px;
    height: 16px;
  }
}

.url-input-section {
  flex: 1;
}

.clear-avatar-btn {
  align-self: flex-start;
  padding: 0.5rem 1rem;
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.875rem;
  transition: all 0.2s;

  &:hover {
    color: var(--error-color);
    border-color: var(--error-color);
    background: rgba(239, 68, 68, 0.05);
  }
}

/* 头像编辑相关禁用状态样式 */
.form-input.disabled {
  background: var(--background-tertiary);
  color: var(--text-tertiary);
  cursor: not-allowed;
  opacity: 0.6;
}

.input-disabled-hint {
  font-size: 0.75rem;
  color: var(--text-tertiary);
  font-style: italic;
  margin-top: 0.25rem;
}

@media (max-width: 768px) {
  .avatar-edit-section {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .avatar-controls {
    width: 100%;
  }

  .avatar-container {
    width: 60px;
    height: 60px;
  }
}
</style>
