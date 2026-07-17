<!-- frontend/src/components/HelloWorld.vue -->
<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { getAssistantList, getChatHistory, sendChat, sendChatStream, createAssistant, deleteAssistant } from '@/api/chat' // 引入 API 函数
import KnowledgeManager from './KnowledgeManager.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'

const assistants = ref([])
const router = useRouter()
const username = ref(localStorage.getItem('username') || '用户')
const currentAssistant = ref(null)
const messages = ref([])
const userInput = ref('')
const messageListRef = ref(null)
const isUserScrolled = ref(false)
const isProgrammaticScroll = ref(false)

// 获取助手列表
onMounted(async () => {
  try {
     assistants.value = await getAssistantList()
  } catch (error) {
    console.error('获取助手列表失败', error)
  }
})



const scrollToBottom = () => {
  if (!messageListRef.value) return
  isProgrammaticScroll.value = true
  requestAnimationFrame(() => {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    setTimeout(() => {
      isProgrammaticScroll.value = false
    }, 100)
  })
}

const checkIsNearBottom = () => {
  if (!messageListRef.value) return false
  const { scrollTop, scrollHeight, clientHeight } = messageListRef.value
  return scrollHeight - scrollTop - clientHeight < 50
}

const handleScroll = () => {
  if (isProgrammaticScroll.value) return
  isUserScrolled.value = !checkIsNearBottom()
}

const autoScrollIfNeeded = () => {
  if (!isUserScrolled.value) {
    scrollToBottom()
  }
}


// 选中助手并加载历史
const selectAssistant = async (assistant) => {
  currentAssistant.value = assistant
  isUserScrolled.value = false
  try {
    const data = await getChatHistory(assistant.id)
    messages.value = Array.isArray(data) ? data : []
    await nextTick()
    scrollToBottom()
  } catch (error) {
    messages.value = []
  }
}

// 发送消息(流式)
const handleSend = async () => {
  if (!userInput.value.trim() || !currentAssistant.value) return

  messages.value.push({ role: 'user', message: userInput.value })
  const msgToSend = userInput.value
  userInput.value = ''

  // 用户发送消息时，若当前接近底部则允许自动滚动
  const shouldAutoScroll = !isUserScrolled.value

  // 先放一个空的 AI 气泡,随流逐字填充
  messages.value.push({ role: 'assistant', message: '' })
  const aiIndex = messages.value.length - 1
  
  if (shouldAutoScroll) {
    await nextTick()
    scrollToBottom()
  }

  try {
    await sendChatStream(currentAssistant.value.id, { message: msgToSend }, (chunk) => {
      messages.value[aiIndex].message += chunk
      if (shouldAutoScroll) {
        autoScrollIfNeeded()
      }
    })
  } catch (error) {
    console.error('发送消息失败', error)
    if (!messages.value[aiIndex].message) {
      messages.value[aiIndex].message = '[出错了,请重试]'
    }
  }
}
//新增助手：直接打开完整弹窗
const handleAddAssistant = () => {
  openAddDialog()
}

// 新增助手弹窗:状态 + 表单
const addDialogVisible = ref(false)
const addForm = reactive({ name: '', personality: '' })

// 打开弹窗(每次清空表单)
const openAddDialog = () => {
  addForm.name = ''
  addForm.personality = ''
  addDialogVisible.value = true
}

// 确认新增:名称 + AI 提示词一起提交
const confirmAddAssistant = async () => {
  if (!addForm.name.trim()) {
    ElMessage.warning('助手名称不能为空')
    return
  }
  if (!addForm.personality.trim()) {
    ElMessage.warning('AI 提示词不能为空，请设置助手的人设')
    return
  }
  try {
    const res = await createAssistant({ name: addForm.name, personality: addForm.personality })
    ElMessage.success('助手创建成功')
    assistants.value.push(res)
    addDialogVisible.value = false
  } catch (error) {
    console.error('创建助手失败', error)
  }
}

// 助手详情弹窗:查看名称 + 提示词
const detailDialogVisible = ref(false)
const detailAssistant = ref({ name: '', personality: '' })

// 点头像:弹出该助手的详情
const showAssistantDetail = (assistant) => {
  detailAssistant.value = assistant
  detailDialogVisible.value = true
}
//删除助手
const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除该助手及所有聊天记录吗？', '警告', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
    await deleteAssistant(id)
    ElMessage.success('删除成功')
    // 从列表中移除该项，若删除的是当前选中助手，需清空右侧聊天区
    assistants.value = assistants.value.filter(item => item.id !== id)
    if (currentAssistant.value && currentAssistant.value.id === id) {
      currentAssistant.value = null
      messages.value = []
    }
  } catch (error) {}
}

// 退出登录
const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  router.push('/login')
}
</script>



<template>
  <div class="app-wrapper">
    <!-- 左侧助手列表 -->
    <aside class="sidebar">
      <div class="sidebar-brand">
        <div class="brand-logo">AI</div>
        <div class="brand-text">AI 助手</div>
      </div>

      <div class="sidebar-add">
        <el-button class="add-btn" @click="openAddDialog">＋ 添加助手</el-button>
      </div>

      <div class="assistant-list">
        <div
          v-for="item in assistants"
          :key="item.id"
          class="assistant-item"
          :class="{ active: currentAssistant && currentAssistant.id === item.id }"
          @click="selectAssistant(item)"
        >
          <div class="assistant-avatar" @click.stop="showAssistantDetail(item)">
            {{ item.name ? item.name.charAt(0) : '?' }}
          </div>
          <span class="assistant-name">{{ item.name }}</span>
          <el-icon class="delete-icon" @click.stop="handleDelete(item.id)"><Delete /></el-icon>
        </div>
      </div>
      <div class="sidebar-footer">
        <div class="sidebar-user">
          <div class="user-avatar-small">{{ username.charAt(0) }}</div>
          <span class="user-name-small">{{ username }}</span>
        </div>
        <el-button class="logout-btn" @click="handleLogout">退出登录</el-button>
      </div>
    </aside>

    <!-- 右侧聊天窗口 -->
    <main class="chat-area">
      <div v-if="!currentAssistant" class="empty-state">
        <div class="hero-icon">
          <svg viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="50" cy="50" r="48" stroke="url(#h1)" stroke-width="1.5" stroke-dasharray="8 4" opacity=".4"/>
            <circle cx="50" cy="50" r="36" stroke="url(#h2)" stroke-width="1" opacity=".6"/>
            <circle cx="50" cy="50" r="24" stroke="url(#h3)" stroke-width="1" stroke-dasharray="4 6" opacity=".3"/>
            <circle cx="50" cy="50" r="12" fill="url(#h4)" opacity=".9"/>
            <defs>
              <linearGradient id="h1" x1="0" y1="0" x2="100" y2="100"><stop stop-color="#6366f1"/><stop offset="1" stop-color="#a855f7"/></linearGradient>
              <linearGradient id="h2" x1="100" y1="0" x2="0" y2="100"><stop stop-color="#a855f7"/><stop offset="1" stop-color="#6366f1"/></linearGradient>
              <linearGradient id="h3" x1="0" y1="100" x2="100" y2="0"><stop stop-color="#6366f1"/><stop offset="1" stop-color="#c084fc"/></linearGradient>
              <radialGradient id="h4"><stop stop-color="#6366f1"/><stop offset="1" stop-color="#a855f7"/></radialGradient>
            </defs>
          </svg>
        </div>
        <div class="hero-text">你好，选择一个助手开始对话吧</div>
      </div>

      <template v-else>
        <!-- 顶部助手信息栏 -->
        <div class="chat-header">
          <div class="header-avatar">{{ currentAssistant.name ? currentAssistant.name.charAt(0) : '?' }}</div>
          <div class="header-info">
            <div class="header-name">{{ currentAssistant.name }}</div>
            <div class="header-sub">在线 · 随时为你解答</div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div ref="messageListRef" class="message-list" @scroll="handleScroll">
          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="msg-row"
            :class="msg.role === 'user' ? 'row-user' : 'row-ai'"
          >
            <div v-if="msg.role !== 'user'" class="msg-avatar ai-avatar">
              {{ currentAssistant.name ? currentAssistant.name.charAt(0) : 'A' }}
            </div>
            <div class="chat-bubble" :class="msg.role === 'user' ? 'bubble-user' : 'bubble-ai'">
              {{ msg.message }}<span v-if="msg.role === 'assistant' && !msg.message" class="typing-cursor"></span>
            </div>
            <div v-if="msg.role === 'user'" class="msg-avatar user-avatar">我</div>
          </div>
        </div>

        <!-- 输入框 -->
        <div class="chat-input">
          <el-input
            v-model="userInput"
            placeholder="输入消息,按回车发送..."
            @keyup.enter="handleSend"
            class="input-box"
          />
          <el-button class="send-btn" @click="handleSend">发送</el-button>
        </div>
      </template>
    </main>

    <!-- 新增助手弹窗:填名称 + AI 提示词 -->
    <el-dialog v-model="addDialogVisible" title="新增助手" width="500px">
      <el-form label-width="90px">
        <el-form-item label="助手名称">
          <el-input v-model="addForm.name" placeholder="给助手起个名字" />
        </el-form-item>
        <el-form-item label="AI 提示词">
          <el-input
            v-model="addForm.personality"
            type="textarea"
            :rows="4"
            placeholder="设定助手的人设/角色,例如:你是一位耐心的数学老师,回答简洁、口吻友好"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAddAssistant">确定</el-button>
      </template>
    </el-dialog>

    <!-- 助手详情弹窗:展示名称 + AI 提示词 + 知识库 -->
    <el-dialog v-model="detailDialogVisible" :title="detailAssistant.name" width="520px">
      <el-tabs model-value="info">
        <el-tab-pane label="基本信息" name="info">
          <el-form label-width="90px">
            <el-form-item label="助手名称">
              <span>{{ detailAssistant.name }}</span>
            </el-form-item>
            <el-form-item label="AI 提示词">
              <span style="white-space: pre-wrap;">{{ detailAssistant.personality || '(未设置)' }}</span>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="知识库" name="knowledge">
          <KnowledgeManager :assistantId="detailAssistant.id" />
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button type="primary" @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.app-wrapper {
  display: flex;
  height: 100vh;
  font-family: system-ui, 'Segoe UI', 'Microsoft YaHei', sans-serif;
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: 270px;
  flex-shrink: 0;
  background: linear-gradient(180deg, #110e2b 0%, #1e1b4b 50%, #252160 100%);
  display: flex;
  flex-direction: column;
}
.sidebar-brand {
  display: flex; align-items: center; gap: 12px;
  padding: 24px 20px 18px;
}
.brand-logo {
  width: 40px; height: 40px; border-radius: 12px;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 700; font-size: 17px;
  box-shadow: 0 4px 16px rgba(99,102,241,.5);
}
.brand-text { color: #fff; font-size: 19px; font-weight: 700; letter-spacing: .5px; }

.sidebar-add { padding: 0 16px 14px; }
.add-btn {
  width: 100%; height: 42px; border: 1.5px solid rgba(255,255,255,.15);
  color: #cbd5e1; font-weight: 500; font-size: 14px;
  background: rgba(255,255,255,.04); border-radius: 12px;
  transition: all .2s;
}
.add-btn:hover { background: rgba(255,255,255,.1); color: #fff; border-color: rgba(255,255,255,.25); transform: translateY(-1px); }

.assistant-list { flex: 1; overflow-y: auto; padding: 4px 12px 8px; }
.assistant-list::-webkit-scrollbar { width: 4px; }
.assistant-list::-webkit-scrollbar-thumb { background: rgba(255,255,255,.1); border-radius: 4px; }

.assistant-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; margin-bottom: 2px;
  border-radius: 12px; cursor: pointer;
  color: #cbd5e1; transition: all .2s;
}
.assistant-item:hover { background: rgba(255,255,255,.06); color: #e2e8f0; }
.assistant-item.active { background: rgba(99,102,241,.25); color: #fff; box-shadow: 0 2px 8px rgba(99,102,241,.2); }
.assistant-avatar {
  width: 34px; height: 34px; flex-shrink: 0; border-radius: 50%;
  background: linear-gradient(135deg, #f472b6, #a855f7);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 600; font-size: 14px; transition: transform .2s;
}
.assistant-item:hover .assistant-avatar { transform: scale(1.08); }
.assistant-name { flex: 1; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.delete-icon { display: none; cursor: pointer; color: #f87171; opacity: .8; transition: opacity .15s; }
.delete-icon:hover { opacity: 1; }
.assistant-item:hover .delete-icon { display: inline-flex; }

/* 侧边栏底部 */
.sidebar-footer {
  padding: 10px 16px 16px; border-top: 1px solid rgba(255,255,255,.08);
}
.sidebar-user {
  display: flex; align-items: center; gap: 10px; margin-bottom: 10px;
}
.user-avatar-small {
  width: 32px; height: 32px; border-radius: 50%;
  background: linear-gradient(135deg, #38bdf8, #3b82f6);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 600; font-size: 13px;
}
.user-name-small { color: #cbd5e1; font-size: 13px; font-weight: 500; }
.logout-btn {
  width: 100%; border: none; color: #94a3b8; font-weight: 500;
  background: rgba(255,255,255,.05); border-radius: 10px; transition: all .2s;
  height: 36px; font-size: 13px;
}
.logout-btn:hover { color: #fca5a5; background: rgba(248,113,113,.12); }

/* ===== 聊天区 ===== */
.chat-area { flex: 1; min-width: 0; display: flex; flex-direction: column; }

/* 空白状态 */
.empty-state {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 28px;
  background: #fafbfd;
}

/* 图标 */
.hero-icon { position: relative; }
.hero-icon svg {
  width: 120px; height: 120px;
  animation: heroFloat 4s ease-in-out infinite;
}
@keyframes heroFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}
/* 文字 */
.hero-text {
  font-size: 15px; color: #9ca3af; letter-spacing: 1px;
}

/* 顶栏 */
.chat-header {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 24px; background: #fff;
  border-bottom: 1px solid #eef0f5;
  box-shadow: 0 1px 4px rgba(0,0,0,.03);
}
.header-avatar {
  width: 42px; height: 42px; border-radius: 50%;
  background: linear-gradient(135deg, #f472b6, #a855f7);
  display: flex; align-items: center; justify-content: center; color: #fff; font-weight: 600; font-size: 16px;
}
.header-name { font-size: 16px; font-weight: 600; color: #1f2937; }
.header-sub { font-size: 12px; color: #9ca3af; }

/* 消息列表 */
.message-list {
  flex: 1; overflow-y: auto; padding: 24px;
  background-color: #f8f9fd;
  background-image: radial-gradient(circle, #e0e2f0 1px, transparent 1px);
  background-size: 24px 24px;
}
.message-list::-webkit-scrollbar { width: 6px; }
.message-list::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 3px; }
.message-list::-webkit-scrollbar-thumb:hover { background: #9ca3af; }

.msg-row { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 20px; animation: msgIn .35s ease-out; }
@keyframes msgIn { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }
.row-user { flex-direction: row-reverse; }
.msg-avatar {
  width: 36px; height: 36px; flex-shrink: 0; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 600; font-size: 14px;
}
.ai-avatar { background: linear-gradient(135deg, #f472b6, #a855f7); }
.user-avatar { background: linear-gradient(135deg, #38bdf8, #3b82f6); }

.chat-bubble {
  max-width: 68%; padding: 12px 18px; border-radius: 16px;
  word-wrap: break-word; white-space: pre-wrap; line-height: 1.65; font-size: 14px;
}
.bubble-user {
  background: linear-gradient(135deg, #6366f1, #4f46e5); color: #fff;
  border-bottom-right-radius: 6px;
  box-shadow: 0 2px 12px rgba(99,102,241,.25);
}
.bubble-ai {
  background: #fff; color: #374151; border: 1px solid #eef0f5;
  border-bottom-left-radius: 6px;
  box-shadow: 0 2px 8px rgba(0,0,0,.04);
}
.typing-cursor {
  display: inline-block; width: 7px; height: 15px; margin-left: 2px;
  background: #a855f7; border-radius: 2px; vertical-align: text-bottom;
  animation: blink 1s steps(2) infinite;
}
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0; } }

/* 输入区 */
.chat-input {
  display: flex; gap: 12px; padding: 16px 24px;
  background: #fff; border-top: 1px solid #eef0f5;
}
.input-box :deep(.el-input__wrapper) {
  border-radius: 12px; box-shadow: none; border: 1.5px solid #e5e7eb;
  transition: border-color .2s;
}
.input-box :deep(.el-input__wrapper:hover) { border-color: #c7d2fe; }
.input-box :deep(.el-input__wrapper.is-focus) { border-color: #6366f1; box-shadow: 0 0 0 3px rgba(99,102,241,.08); }
.send-btn {
  border: none; color: #fff; font-weight: 600; padding: 0 28px; font-size: 14px;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  border-radius: 12px; transition: all .2s;
}
.send-btn:hover { transform: translateY(-1px); box-shadow: 0 4px 16px rgba(99,102,241,.4); color: #fff; }
</style>

<!-- 全局覆盖:清掉 Vite 模板对 #app 的宽度/居中限制,让聊天铺满全屏 -->
<style>
#app { width: 100%; max-width: none; min-height: 100vh; margin: 0; text-align: left; border: none; }
</style>
