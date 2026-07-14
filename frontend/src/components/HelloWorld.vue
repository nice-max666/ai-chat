<!-- frontend/src/components/HelloWorld.vue -->
<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { getAssistantList, getChatHistory, sendChat, sendChatStream, createAssistant, deleteAssistant } from '@/api/chat' // 引入 API 函数
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'

const assistants = ref([])
const currentAssistant = ref(null)
const messages = ref([])
const userInput = ref('')
const messageListRef = ref(null)

// 获取助手列表
onMounted(async () => {
  try {
     // TestController 未使用 Result 包装，拦截器会原样返回数组
     assistants.value = await getAssistantList()
  } catch (error) {
    console.error('获取助手列表失败', error)
  }
})

// 封装滚动到底部的方法
const scrollToBottom = async () => {
  await nextTick() // 等待 Vue 完成 DOM 更新
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}


// 选中助手并加载历史
const selectAssistant = async (assistant) => {
  currentAssistant.value = assistant
  try {
    const data = await getChatHistory(assistant.id)
    messages.value = Array.isArray(data) ? data : []
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

  // 先放一个空的 AI 气泡,随流逐字填充
  messages.value.push({ role: 'assistant', message: '' })
  const aiIndex = messages.value.length - 1
  scrollToBottom()

  try {
    await sendChatStream(currentAssistant.value.id, { message: msgToSend }, (chunk) => {
      messages.value[aiIndex].message += chunk // 追加片段,视图实时更新
      scrollToBottom()
    })
  } catch (error) {
    console.error('发送消息失败', error)
    if (!messages.value[aiIndex].message) {
      messages.value[aiIndex].message = '[出错了,请重试]'
    }
  }
}
//新增助手
const handleAddAssistant = () => {
  ElMessageBox.prompt('请输入助手名称', '新增助手', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '名称不能为空'
  }).then(async ({ value }) => {
    // 调用后端接口，仅传 name，id与时间由后端生成
    const res = await createAssistant({ name: value })
    ElMessage.success('助手创建成功')
    assistants.value.push(res) // 将新助手追加到列表
  }).catch(() => {})
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
    </aside>

    <!-- 右侧聊天窗口 -->
    <main class="chat-area">
      <div v-if="!currentAssistant" class="empty-state">
        <div class="empty-emoji">💬</div>
        <div>请选择一个助手开始聊天</div>
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
        <div ref="messageListRef" class="message-list">
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
              {{ msg.message }}<span v-if="msg.role === 'assistant' && msg.message === ''" class="typing-cursor"></span>
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

    <!-- 助手详情弹窗:展示名称 + AI 提示词 -->
    <el-dialog v-model="detailDialogVisible" title="助手详情" width="500px">
      <el-form label-width="90px">
        <el-form-item label="助手名称">
          <span>{{ detailAssistant.name }}</span>
        </el-form-item>
        <el-form-item label="AI 提示词">
          <span style="white-space: pre-wrap;">{{ detailAssistant.personality || '(未设置)' }}</span>
        </el-form-item>
      </el-form>
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
  width: 260px;
  flex-shrink: 0;
  background: linear-gradient(180deg, #1e1b4b 0%, #312e81 100%);
  display: flex;
  flex-direction: column;
}
.sidebar-brand {
  display: flex; align-items: center; gap: 12px;
  padding: 22px 20px;
}
.brand-logo {
  width: 36px; height: 36px; border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 700; font-size: 15px;
  box-shadow: 0 4px 12px rgba(99,102,241,.45);
}
.brand-text { color: #fff; font-size: 18px; font-weight: 600; }

.sidebar-add { padding: 4px 16px 12px; }
.add-btn {
  width: 100%; height: 40px; border: none; color: #fff; font-weight: 500;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  border-radius: 10px; transition: transform .2s, opacity .2s;
}
.add-btn:hover { transform: translateY(-1px); opacity: .92; color: #fff; }

.assistant-list { flex: 1; overflow-y: auto; padding: 8px 12px; }
.assistant-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; margin-bottom: 4px;
  border-radius: 10px; cursor: pointer;
  color: #cbd5e1; transition: background .2s;
}
.assistant-item:hover { background: rgba(255,255,255,.08); }
.assistant-item.active { background: rgba(255,255,255,.15); color: #fff; }
.assistant-avatar {
  width: 32px; height: 32px; flex-shrink: 0; border-radius: 50%;
  background: linear-gradient(135deg, #f472b6, #a855f7);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 600; font-size: 14px; transition: transform .2s;
}
.assistant-avatar:hover { transform: scale(1.12); }
.assistant-name { flex: 1; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.delete-icon { display: none; cursor: pointer; color: #f87171; }
.assistant-item:hover .delete-icon { display: inline-flex; }

/* ===== 聊天区 ===== */
.chat-area { flex: 1; min-width: 0; display: flex; flex-direction: column; background: #f7f8fc; }
.empty-state {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 14px; color: #9ca3af; font-size: 15px;
}
.empty-emoji { font-size: 52px; }

.chat-header {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 24px; background: #fff;
  border-bottom: 1px solid #eef0f5; box-shadow: 0 1px 3px rgba(0,0,0,.03);
}
.header-avatar {
  width: 40px; height: 40px; border-radius: 50%;
  background: linear-gradient(135deg, #f472b6, #a855f7);
  display: flex; align-items: center; justify-content: center; color: #fff; font-weight: 600;
}
.header-name { font-size: 16px; font-weight: 600; color: #1f2937; }
.header-sub { font-size: 12px; color: #9ca3af; }

.message-list { flex: 1; overflow-y: auto; padding: 24px; }
.msg-row { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 18px; }
.row-user { flex-direction: row-reverse; }
.msg-avatar {
  width: 34px; height: 34px; flex-shrink: 0; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 600; font-size: 13px;
}
.ai-avatar { background: linear-gradient(135deg, #f472b6, #a855f7); }
.user-avatar { background: linear-gradient(135deg, #38bdf8, #3b82f6); }

.chat-bubble {
  max-width: 68%; padding: 12px 16px; border-radius: 14px;
  word-wrap: break-word; white-space: pre-wrap; line-height: 1.6; font-size: 14px;
  box-shadow: 0 2px 8px rgba(0,0,0,.05);
}
.bubble-user {
  background: linear-gradient(135deg, #6366f1, #3b82f6); color: #fff;
  border-bottom-right-radius: 4px;
}
.bubble-ai {
  background: #fff; color: #374151; border: 1px solid #eef0f5;
  border-bottom-left-radius: 4px;
}
.typing-cursor {
  display: inline-block; width: 7px; height: 15px; margin-left: 2px;
  background: #a855f7; border-radius: 2px; vertical-align: text-bottom;
  animation: blink 1s steps(2) infinite;
}
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0; } }

.chat-input {
  display: flex; gap: 12px; padding: 16px 24px;
  background: #fff; border-top: 1px solid #eef0f5;
}
.input-box :deep(.el-input__wrapper) { border-radius: 10px; }
.send-btn {
  border: none; color: #fff; font-weight: 500; padding: 0 26px;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  border-radius: 10px; transition: opacity .2s;
}
.send-btn:hover { opacity: .92; color: #fff; }
</style>

<!-- 全局覆盖:清掉 Vite 模板对 #app 的宽度/居中限制,让聊天铺满全屏 -->
<style>
#app { width: 100%; max-width: none; min-height: 100vh; margin: 0; text-align: left; border: none; }
</style>
