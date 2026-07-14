# AI聊天项目通用基础规范
## 语言输出规则
全部代码解读、修改方案、注释文字使用简体中文，仅代码关键字保留英文。

## 项目文件分层结构
### 后端 backend/src/main/java/com/example/ai_chat
1. 请求控制层：ChatController.java
2. 业务逻辑层：ChatService.java
3. 第三方接口调用层：LlmService.java
4. 禁止修改模块：entity实体类、repository持久层、config配置类

### 前端 frontend/src
1. 对话接口封装文件：api/chat.js
2. 全局请求工具：utils/request.js

## 扫描过滤规则
1. 检索范围仅限 backend/src/main/java、frontend/src 业务源码
2. 永久过滤目录：node_modules、target、.idea、test
3. 定位第三方接口代码：优先检索 LlmService，通过HTTP请求代码自动识别外部接口调用逻辑，不依赖固定关键词匹配

## 通用修改约束
1. 任何需求均禁止改动 entity、repository 数据库相关代码；
2. 仅修改当前需求直接关联的文件，不擅自新增无关类、无关配置；
3. 原有已有业务方法若无明确删除指令，全部保留，不可直接移除。

## 文件修改安全流程
所有代码调整、新增功能、逻辑优化，执行修改前必须输出新旧代码diff完整对比，等待输入y确认后再写入本地文件。

## 项目启动命令
cd backend
mvn spring-boot:run