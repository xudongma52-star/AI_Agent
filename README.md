# AI_Agent — 明道（MingDao）

基于 Spring AI Alibaba 构建的智能对话 Agent，融合王阳明心学与老子道家思想，支持 RAG 知识库检索、流式对话、对话记忆持久化及 PDF 报告导出。

---

## 技术栈

| 技术 | 版本 | 用途 |
|---|---|---|
| Spring Boot | 3.5.13 | 后端框架 |
| Spring AI Alibaba | 1.1.2.0 | AI 对话集成（DashScope 通义千问） |
| Spring AI | 1.1.2 | ChatClient / Advisors / Tool 抽象 |
| PostgreSQL + pgvector | 15+ | 主库 + 向量存储 |
| Redis | 7.0+ | 对话记忆热缓存 |
| RabbitMQ | 3.x | 消息异步持久化解耦 |
| MyBatis-Plus | 3.5.15 | ORM |
| Ollama | — | 本地模型运行（专家子 Agent） |
| React 18 + TypeScript | 18.3.1 | 前端 |
| Vite | 6.0.5 | 前端构建 |
| Tailwind CSS | 3.4.17 | 样式 |
| Zustand | 5.0.2 | 前端状态管理 |
| openhtmltopdf + commonmark | 1.0.10 / 0.22.0 | Markdown → PDF 渲染 |
| Knife4j | 4.4.0 | API 文档 |

## 环境要求

- JDK 21+
- Maven 3.6+（或使用项目自带的 Maven Wrapper）
- PostgreSQL 15+（需安装 pgvector 扩展）
- Redis 7.0+
- RabbitMQ 3.x
- Ollama（可选，用于本地专家 Agent）
- DashScope API Key（阿里云百炼）

## Docker 一键部署（推荐）

```bash
git clone https://github.com/xudongma52-star/AI_Agent.git
cd AI_Agent

# 1. 配置 API Key
cp .env.example .env
# 编辑 .env，填入你的 DASHSCOPE_API_KEY

# 2. 一行启动所有服务（基础设施 + 后端）
docker compose up -d

# 3. 可选：启动前端
docker compose --profile frontend up -d
```

启动后访问：
| 服务 | 地址 |
|---|---|
| 后端 API | http://localhost:10002 |
| API 文档 (Knife4j) | http://localhost:10002/doc.html |
| 前端（如已启动） | http://localhost:3000 |
| RabbitMQ 管理界面 | http://localhost:15672 |

Ollama 模型 `qwen3.6` 会在首次启动时自动拉取（约需几分钟），专家子 Agent 在此之前不可用，主对话不受影响。

## 本地开发启动

### 1. 克隆项目

```bash
git clone https://github.com/xudongma52-star/AI_Agent.git
cd AI_Agent
```

### 2. 创建数据库并启用 pgvector

```sql
CREATE DATABASE ai_agent;
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. 配置

复制示例配置并根据自己的环境修改：

```bash
cp src/main/resources/application-example.yml src/main/resources/application.yml
```

然后修改 `application.yml` 中的以下关键配置：

| 配置项 | 说明 |
|---|---|
| `spring.ai.dashscope.api-key` | 阿里云百炼 API Key（必填） |
| `spring.datasource.password` | 数据库密码 |
| `spring.data.redis.password` | Redis 密码（无密码则留空） |
| `spring.rabbitmq.password` | RabbitMQ 密码 |

> `application.yml` 已加入 `.gitignore`，不会提交到仓库。`application-example.yml` 是提交跟踪的模板文件。

### 4. 启动中间件

确保 PostgreSQL、Redis、RabbitMQ 已启动。如需本地专家 Agent，还需启动 Ollama 并拉取模型。

### 5. 启动后端

```bash
./mvnw spring-boot:run
```

后端默认运行在 `http://localhost:10002`，API 文档见 `http://localhost:10002/doc.html`。

### 6. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器运行在 `http://localhost:3000`，自动代理 `/api` 请求到后端。

---

## 后端架构

### 整体分层

```
controller/        —— REST API 层，对接前端请求
app/               —— 核心编排层（MaxApp），组装 ChatClient、Advisors、Tools
memory/            —— 对话记忆（Redis + PostgreSQL 双层存储）
mq/                —— RabbitMQ 消费者，异步持久化消息
rag/               —— RAG 知识库（文档加载、向量检索、问答）
agent/experts/     —— 专家子 Agent（老子专家、阳明专家、典籍检索）
tools/             —— Agent 可调用工具（日期时间、PDF 导出）
advisor/           —— 自定义 Advisor（日志、Re-Reading）
config/            —— 基础设施配置
entity/ / mapper/  —— 数据库实体与 MyBatis-Plus 映射
dto/               —— 数据传输对象
exception/         —— 全局异常处理
```

### 核心类：MaxApp

`MaxApp`（`src/main/java/com/max/ai_agent/app/MaxApp.java:32`）是后端的中枢，负责：

1. **组装 ChatClient**：注入系统提示词（定义"明道"人设——融合阳明心学与老子道家的智者）、Advisor 链、可调用工具
2. **对话逻辑**：提供 `nowChat()`（同步）和 `nowChatStream()`（SSE 流式）两个核心方法
3. **RAG 注入**：当用户启用 RAG 时，先检索向量库，将参考资料注入用户消息后再发送给 LLM

```java
this.chatClient = ChatClient.builder(dashscopeChatModel)
    .defaultSystem(SYSTEM_PROMPT)
    .defaultAdvisors(
        MessageChatMemoryAdvisor.builder(redisPostgreSqlChatMemory).build(),
        new SimpleLoggerAdvisor(),
        new ReReadingAdvisor()
    )
    .defaultTools(dateTimeTool, pdfExportTool, scholarTool, laoZiExpertTool, yangMingExpertTool)
    .build();
```

### API 端点

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/chat/start` | 开始新对话（同步） |
| `POST` | `/api/chat/continue` | 继续已有对话（同步） |
| `POST` | `/api/chat/start/stream` | 开始新对话（SSE 流式） |
| `POST` | `/api/chat/continue/stream` | 继续已有对话（SSE 流式） |
| `GET` | `/api/chat/pdf/download/{downloadId}` | 下载生成的 PDF |
| `POST` | `/api/chat/rebuild` | 重建知识库 |
| `POST` | `/api/rag/question` | 独立 RAG 问答（支持按书名过滤） |
| `POST` | `/api/rag/rebuild` | 重建 RAG 知识库 |

请求体与响应体定义见 `src/main/java/com/max/ai_agent/dto/`。

---

## 对话记忆：Redis + PostgreSQL 双层存储

这是项目中处理缓存最复杂的模块，位于 `src/main/java/com/max/ai_agent/memory/RedisPostgreSqlChatMemory.java`。

### 架构设计

```
用户消息 → Redis (热缓存，Lua 原子写入) → RabbitMQ (异步) → PostgreSQL (冷存储)
                ↑                                  │
                └──── 缓存未命中时回填 ──────────────┘
```

### 三级缓存防护

| 问题 | 策略 | 实现 |
|---|---|---|
| **缓存穿透** | 空值缓存 | 查询 PostgreSQL 为空时，写入 `EMPTY` 标记（5 分钟 TTL），下次直接返回空 |
| **缓存击穿** | 分布式锁 | 缓存未命中时，用 `SETNX` 获取锁，只有拿到锁的线程才能查 PostgreSQL 并回填 Redis |
| **缓存雪崩** | TTL 抖动 | 基础 TTL 上叠加 0~300 秒随机值，避免大量 key 同时过期 |

### 写入路径

1. **Lua 脚本原子写入**：`RPUSH` 消息 JSON + 计数器自增，保证顺序号唯一
2. **发后即忘**：消息写入 Redis 后立即响应用户，另起线程发送 RabbitMQ 消息
3. **MQ 失败不阻塞**：即使 RabbitMQ 宕机，消息仍在 Redis 中，不抛异常、不回滚

### 读取路径

1. 检查 Redis 空标记（防穿透）
2. 尝试从 Redis 读取
3. 未命中 → 获取分布式锁 → 双重检查 → 查 PostgreSQL → Pipeline 回填 Redis

详细代码见 `RedisPostgreSqlChatMemory.java` 中的注释，每一步的设计取舍都有说明。

---

## 消息队列：异步持久化

`ChatMemoryConsumer`（`src/main/java/com/max/ai_agent/mq/ChatMemoryConsumer.java`）负责消费 RabbitMQ 消息并写入 PostgreSQL：

- **手动 ACK**：消费成功才确认，失败不丢消息
- **幂等性**：通过 `DuplicateKeyException` 识别重复消息，直接 ACK 丢弃
- **重试机制**：消费失败抛出异常，由 Spring Retry 接管（最多 3 次，指数退避）
- **死信兜底**：重试耗尽后进入死信队列，记录告警日志（预留钉钉/企微通知接口）

---

## RAG 知识库

### 文档加载

`MarkdownDocumentLoader` 解析 `src/main/resources/documents/` 下的 Markdown 文件，利用标题层级（`#` → 书名、`##` → 章节、`###` → 小节）提取结构化元数据，`---` 作为段落分隔符，形成 `<文档, 元数据>` 对。

### 向量化与检索

- **Embedding 模型**：DashScope `text-embedding-v3`，1024 维
- **向量数据库**：pgvector，HNSW 索引，COSINE_DISTANCE 相似度
- **分块策略**：800 token / 块，200 token 重叠
- **检索参数**：Top-K = 5，相似度阈值 0.70
- **元数据过滤**：支持按书名过滤检索结果

### 问答流程

`RagQueryService.query()`：
1. 检索 → 2. 格式化上下文（附带出处引用）→ 3. 填充 Prompt 模板 → 4. LLM 生成答案 → 5. 返回答案 + 溯源列表

Prompt 模板位于 `src/main/resources/prompts/rag-prompt.st`。

### 启动初始化

`RagKnowledgeService` 实现 `CommandLineRunner`，应用启动时自动加载文档、分块、嵌入、写入 pgvector。

---

## 专家子 Agent

主 Agent（明道）在处理专业问题时，会通过 Tool Calling 将子任务委派给三个专家：

| 专家 | 底层模型 | 职责 |
|---|---|---|
| `YangMingExpertTool` | Ollama 本地模型 | 专门回答心学问题，引用《传习录》原文 |
| `LaoZiExpertTool` | Ollama 本地模型 | 专门回答道家问题，引用《道德经》原文 |
| `ScholarTool` | Ollama 本地模型 + pgvector | 严格从知识库检索原文出处，必须注明出处，不编造 |

每个专家有独立的 System Prompt，限定回答范围，拒绝超出领域的问题。主模型拿到专家回复后，用自己的语气自然融入最终回答。

---

## PDF 导出

`PdfExportTool`（`src/main/java/com/max/ai_agent/tools/PdfExportTool.java`）实现 Markdown → PDF 的内存级转换：

1. **Markdown → HTML**：commonmark 解析
2. **HTML → PDF**：openhtmltopdf（基于 pdfbox）渲染
3. **中文字体**：优先加载 classpath 下的 SimHei/SimSun，回退到系统字体（Windows/Linux/macOS 全覆盖）
4. **存储与下载**：PDF 字节存入 `ConcurrentHashMap`，返回 `{{PDF_DOWNLOAD:uuid:filename}}` 标记；前端识别标记渲染下载按钮，用户点击后通过 `/api/chat/pdf/download/{id}` 获取文件（一次性下载，取后即删）
5. **过期清理**：`@Scheduled` 每 10 分钟清理超过 30 分钟未被下载的 PDF

传参用 `record`：

```java
public record PdfEntry(byte[] bytes, String fileName, Instant createdAt) {}
```

---

## 前端与前後端交互

前端采用 React 18 + TypeScript + Vite 构建，核心关注点在于与前端的流式通信和状态同步。

### SSE 流式对话

后端 SSE 协议：

```
event:chatId\ndata:<uuid>\n         ← 首个事件：会话 ID
data:<文本块>\n\n                    ← 后续事件：流式内容
data:<文本块>\n\n
```

前端 `streamChat()`（`frontend/src/api/chat.ts:47`）使用 `fetch` + `ReadableStream` 手动解析 SSE：

1. 逐行读取，以空行作为事件边界
2. 解析 `event:` / `data:` 字段
3. `chatId` 事件 → 记录后端会话 ID，用于后续 Continue 请求
4. 文本块 → 回调 `onChunk`
5. 流结束 → 回调 `onDone`

### 渲染节流

`ChatPage` 中以 50ms 间隔批量更新 Zustand store，避免每个 token 触发一次 React re-render（`flushChunks` + `flushTimerRef`）。

### 状态管理

Zustand store（`frontend/src/store/chatStore.ts`）维护：

- 对话列表与当前活跃对话（前端生成 `id` + 后端返回的 `backendChatId`）
- RAG 开关、流式开关、流式状态
- 侧栏展开/收起状态
- 对话持久化到 `localStorage`

### 前后端 ID 映射

前端自行生成 `conversation.id`（本地唯一），后端返回 `chatId`（UUID）。首次对话后，前端将 `backendChatId` 存入对话对象，后续 Continue 请求携带此 ID，后端通过 `MessageChatMemoryAdvisor` 恢复对话上下文。

### PDF 下载交互

LLM 回复中嵌入 `{{PDF_DOWNLOAD:uuid:filename}}` 标记 → `ChatMessage` 组件解析并渲染下载按钮 → 用户点击 → 调用 File System Access API（`showSaveFilePicker`）或降级为 `<a>` 标签下载 → 后端 `takePdf()` 原子取出并删除。

### 请求体类型定义

```typescript
interface ChatRequest {
  message: string
  chatId?: string       // 继续对话时传入
  useRag?: boolean      // 是否启用知识库检索
}

interface ChatResponse {
  chatId: string
  message: string
  newConversation: boolean
}
```

---

## 项目结构

```
AI_Agent/
├── src/main/java/com/max/ai_agent/
│   ├── AiAgentApplication.java      # 启动类
│   ├── advisor/                     # 自定义 Advisor（日志、Re-Reading）
│   ├── agent/experts/               # 专家子 Agent（老子、阳明、典籍检索）
│   ├── app/                         # 核心编排（MaxApp）
│   ├── config/                      # Redis / RabbitMQ / MyBatis-Plus / Ollama 配置
│   ├── controller/                  # REST 控制器
│   ├── dto/                         # 请求/响应 DTO
│   ├── entity/                      # 数据库实体
│   ├── exception/                   # 全局异常处理
│   ├── mapper/                      # MyBatis-Plus Mapper
│   ├── memory/                      # 对话记忆双层存储
│   ├── mq/                          # RabbitMQ 消费者
│   ├── rag/                         # RAG 知识库（加载、检索、问答）
│   ├── tools/                       # Agent 工具（日期、PDF 导出）
│   └── utils/                       # 工具类
├── src/main/resources/
│   ├── application.yml              # 主配置
│   ├── documents/                   # 知识库源文档（Markdown）
│   ├── fonts/                       # 中文字体（PDF 渲染用）
│   ├── mapper/                      # MyBatis XML
│   └── prompts/                     # Prompt 模板
├── frontend/                        # React 前端
│   └── src/
│       ├── api/chat.ts              # API 层（HTTP + SSE 流式解析）
│       ├── components/              # UI 组件
│       ├── hooks/useSSE.ts          # SSE 流管理 Hook
│       ├── pages/                   # ChatPage / KnowledgePage
│       ├── store/chatStore.ts       # Zustand 状态管理
│       └── types/index.ts           # TypeScript 类型定义
├── exports/                         # 已导出的 PDF 示例
└── pom.xml                          # Maven 构建配置
```

---

## 注意事项

- `application.yml` 含真实密钥，已加入 `.gitignore`，切勿强制提交
- 生产环境请替换所有中间件默认密码（数据库、Redis、RabbitMQ）
- Ollama 专家 Agent 为可选组件，未启动 Ollama 时主对话仍可正常使用（DashScope 云端模型）
- PDF 导出依赖中文字体，确保 `src/main/resources/fonts/` 中有 SimHei.ttf / SimSun.ttf，或系统已安装对应字体
