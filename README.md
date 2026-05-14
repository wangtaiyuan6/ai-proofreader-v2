# AI Proofreader — 智能文档校对系统

基于大语言模型的中文文档校对工具，支持实时流式输出、差异对比、修改记录追踪。用户上传文档或粘贴文本后，系统自动调用 LLM 进行校对，通过 SSE（Server-Sent Events）实时返回分析过程、校对结果和逐条修改说明。

---

## 目录

- [项目背景](#项目背景)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [核心机制说明](#核心机制说明)
- [安全加固说明](#安全加固说明)
- [项目结构](#项目结构)
- [本地快速启动指南](#本地快速启动指南)
- [环境变量配置](#环境变量配置)
- [测试](#测试)
- [生产环境部署](#生产环境部署)
- [API 接口](#api-接口)

---

## 项目背景

内部团队在日常工作中需要处理大量中文文档（公文、通讯稿、技术文档等），人工校对效率低且容易遗漏。本系统通过接入 DeepSeek / OpenAI 兼容的大模型 API，实现自动化的文档校对，并以流式方式实时展示校对过程，提升用户体验。

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行时 |
| Spring Boot | 3.2.5 | Web 框架 |
| OkHttp | 4.12.0 | HTTP 客户端（调用 LLM API） |
| Jackson | (Spring Boot 管理) | JSON 序列化 |
| Apache PDFBox | 3.0.3 | PDF 文件解析 |
| Apache POI | 5.2.5 | DOCX 文件解析 |
| Maven | — | 构建工具 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue.js | 2.7 | 前端框架 |
| Tailwind CSS | 3.4 | 样式框架 |
| Axios | 1.7 | HTTP 客户端 |
| diff | 7.0 | 文本差异对比 |
| Vue CLI | 5.0 | 构建工具 |

---

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                     Frontend (Vue.js :8081)              │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌────────┐ │
│  │ FileUpload│  │ TextInput│  │  DiffView │  │Changes │ │
│  └─────┬────┘  └─────┬────┘  └───────────┘  └────────┘ │
│        │              │                                  │
│        └──────┬───────┘                                  │
│               │  fetch + SSE                             │
└───────────────┼──────────────────────────────────────────┘
                │
┌───────────────┼──────────────────────────────────────────┐
│               ▼   Backend (Spring Boot :8080)             │
│  ┌─────────────────────────────────────────────┐         │
│  │         SecurityHeadersFilter (Order=1)      │         │
│  │  X-Frame-Options / CSP / HSTS / nosniff     │         │
│  └─────────────────────┬───────────────────────┘         │
│                        ▼                                  │
│  ┌─────────────────────────────────────────────┐         │
│  │          ApiKeyAuthFilter (Order=2)          │         │
│  │  Bearer Token / X-API-Key Header            │         │
│  └─────────────────────┬───────────────────────┘         │
│                        ▼                                  │
│  ┌─────────────────────────────────────────────┐         │
│  │       RateLimitInterceptor                   │         │
│  │  proofread: 10/min  |  parse: 20/min        │         │
│  └──────────┬──────────────────┬───────────────┘         │
│             ▼                  ▼                          │
│  ┌──────────────────┐ ┌──────────────────┐               │
│  │ProofreadController│ │ ParseController  │               │
│  │  SSE 流式响应     │ │  文件上传解析    │               │
│  └────────┬─────────┘ └────────┬─────────┘               │
│           ▼                    ▼                          │
│  ┌──────────────────┐ ┌──────────────────┐               │
│  │   LlmService     │ │ FileParseService │               │
│  │ OkHttp → LLM API │ │ PDFBox / POI     │               │
│  └──────────────────┘ └──────────────────┘               │
└──────────────────────────────────────────────────────────┘
                │
                ▼
┌──────────────────────────┐
│  LLM API (DeepSeek 等)   │
│  OpenAI-compatible 接口   │
└──────────────────────────┘
```

---

## 核心机制说明

### LlmService 架构

`LlmService` 是系统的核心服务，负责与 LLM API 交互并流式处理响应。

**请求流程：**

1. 接收用户文本 → `InputSanitizer.sanitize()` 清洗输入
2. 构建 OpenAI-compatible 请求体（system prompt + user message）
3. 通过 OkHttp 异步发起流式请求（`enqueue`）
4. 在 Callback 中逐行读取 SSE 数据流
5. 通过 `StreamingResponseParser` 增量解析三阶段标签
6. 通过 `SseEmitter` 实时推送事件到前端

**关键设计决策：**

- 使用 OkHttp 而非 Spring 的 `RestTemplate`/`WebClient`，因为需要对 SSE 流有底层字节级控制
- `streamProofread()` 支持 `Consumer<Call>` 回调，允许上层获取 OkHttp Call 引用用于超时取消
- 所有 SSE 发送均通过 `isClosed` AtomicBoolean 防止向已断开的连接写入

### SSE 流式解析机制

LLM 被要求按三阶段 XML 标签格式输出：

```xml
<thinking>分析过程</thinking>
<correction>校对后的完整文档</correction>
<changes>[{"original":"原文","corrected":"修正","reason":"理由"}]</changes>
```

**`StreamingResponseParser.parse()` 增量解析策略：**

- 使用 `indexOf` 而非正则定位标签边界，支持流式场景下的不完整标签
- 三阶段独立追踪状态（`thinkingDone` / `correctionDone` / `changesDone`）
- `<changes>` 内的 JSON 采用"逐对象提取"策略：通过花括号深度匹配提取已完整接收的 JSON 对象，无需等待整个数组闭合
- 自动过滤 LLM 生成的示例修改记录（`filterExampleChanges`）

**前端事件协议：**

| 事件名 | 数据格式 | 说明 |
|--------|----------|------|
| `start` | `{}` | 流开始 |
| `thinking` | `{text, done}` | 分析过程（可流式更新） |
| `correction` | `{text, done}` | 校对结果（可流式更新） |
| `change` | `{change, done}` | 单条修改记录（增量） |
| `changes` | `{changes, done}` | 完整修改列表（结束时） |
| `done` | `{}` | 流结束 |
| `error` | `{error}` | 错误信息 |

### R1 推理模型兼容

系统兼容 DeepSeek R1 等推理模型，这类模型将推理过程放在 `reasoning_content` 字段而非 `content` 字段中。`LlmService` 通过 `extractReasoningContent()` 方法检测并处理这种情况，自动将 `reasoning_content` 映射为 `<thinking>` 阶段。

---

## 安全加固说明

本项目已完成企业级安全审计与加固，覆盖以下防线：

### 1. 业务防刷与资源保护

| 措施 | 实现 |
|------|------|
| IP 限流 | `RateLimitInterceptor`：proofread 10次/分钟，parse 20次/分钟 |
| IP 获取加固 | 默认使用 `remoteAddr`（不可伪造），`rate-limit.trust-proxy=true` 时才信任代理头 |
| 并发连接限制 | `AtomicInteger` 追踪活跃 SSE 连接，上限 30 |
| 线程池有界化 | `ThreadPoolExecutor` 替代 `CachedThreadPool`，proofread 最大 20 线程 + 50 队列 |
| Tomcat 加固 | max-threads=50, max-connections=200, connection-timeout=10s |
| 输入长度校验 | 后端强制拦截 10000 字符上限 |
| Map 容量保护 | RateLimit store 上限 100k 条目 + 自动淘汰 |

### 2. 提示词注入防御

| 措施 | 实现 |
|------|------|
| 输入清洗 | `InputSanitizer`：剥离危险 XML 标签、检测注入模式、角括号全角编码 |
| 中英文双语检测 | 覆盖 "ignore previous instructions" / "忽略以上所有指令" 等 14 种模式 |
| XML 标签隔离 | 用户输入包裹在 `<user_document>` 标签中，与系统指令结构性隔离 |
| 用户输入标签剥离 | `<user_document>` / `</user_document>` 标签也被纳入剥离列表 |

### 3. 敏感信息脱敏

| 措施 | 实现 |
|------|------|
| LLM API 错误脱敏 | 原始错误体仅记日志，前端返回"校对服务暂时不可用" |
| OkHttp 异常脱敏 | 主机名/IP/TLS 细节不泄露，前端返回"校对服务连接失败" |
| 全局异常处理 | `GlobalExceptionHandler` 返回通用模糊提示，不暴露 `getMessage()` |
| 文件解析异常脱敏 | Apache POI/PDFBox 内部类名和栈信息不泄露 |
| JSON 安全序列化 | SSE 错误事件使用 `ObjectMapper` 而非手动字符串拼接 |

### 4. 超时与连接管理

| 措施 | 实现 |
|------|------|
| SSE 超时 | 5 分钟，超时后取消 OkHttp Call 并释放连接 |
| OkHttp callTimeout | 6 分钟硬性上限，覆盖整个调用生命周期 |
| OkHttp readTimeout | 5 分钟，防止慢速上游阻塞 |
| Error Emitter 超时 | 错误响应也有 5 分钟超时，防止无限挂起 |
| SSE onError 处理 | 客户端断开时取消 OkHttp Call + 完成 Emitter + 递减连接计数 |

### 5. Web 安全头

| Header | 值 |
|--------|-----|
| X-Frame-Options | DENY |
| X-Content-Type-Options | nosniff |
| X-XSS-Protection | 1; mode=block |
| Content-Security-Policy | default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self' |
| Strict-Transport-Security | max-age=31536000; includeSubDomains |
| Referrer-Policy | strict-origin-when-cross-origin |
| Permissions-Policy | camera=(), microphone=(), geolocation=() |

### 6. 认证与 CORS

| 措施 | 实现 |
|------|------|
| API Key 认证 | `ApiKeyAuthFilter`：支持 Bearer Token 和 X-API-Key Header |
| API Key 安全 | 已移除 Query 参数传递方式（防止日志泄露） |
| CORS | 允许来源可配置，Headers 限制为 Content-Type / X-API-Key / Authorization / Accept |
| .gitignore | 排除 .env、*.log、target/、node_modules/、dist/ 等敏感和构建文件 |

---

## 项目结构

```
ai-proofreader-v2/
├── README.md
├── .gitignore
├── backend/                            # Spring Boot 后端
│   ├── pom.xml
│   ├── .env.example                    # 环境变量模板（脱敏）
│   ├── .env                            # 实际配置（不入库）
│   └── src/
│       ├── main/
│       │   ├── java/com/aiproofreader/
│       │   │   ├── AiProofreaderApplication.java
│       │   │   ├── config/
│       │   │   │   ├── CorsConfig.java
│       │   │   │   ├── SecurityHeadersFilter.java
│       │   │   │   └── WebMvcConfig.java
│       │   │   ├── controller/
│       │   │   │   ├── ProofreadController.java   # 校对 SSE 端点
│       │   │   │   └── ParseController.java       # 文件解析端点
│       │   │   ├── exception/
│       │   │   │   └── GlobalExceptionHandler.java
│       │   │   ├── model/
│       │   │   │   ├── Change.java
│       │   │   │   └── ParsedResult.java
│       │   │   ├── security/
│       │   │   │   ├── ApiKeyAuthFilter.java
│       │   │   │   ├── InputSanitizer.java
│       │   │   │   └── RateLimitInterceptor.java
│       │   │   └── service/
│       │   │       ├── LlmService.java             # LLM 调用 + 流处理
│       │   │       ├── StreamingResponseParser.java # SSE 响应增量解析
│       │   │       └── FileParseService.java        # PDF/DOCX/TXT 解析
│       │   └── resources/
│       │       ├── application.yml
│       │       └── logback.xml
│       └── test/                       # 76 个单元测试
│           └── java/com/aiproofreader/
│               ├── controller/ProofreadControllerTest.java
│               ├── security/
│               │   ├── InputSanitizerTest.java
│               │   └── RateLimitInterceptorTest.java
│               └── service/
│                   ├── LlmServiceTest.java
│                   ├── FileParseServiceTest.java
│                   └── StreamingResponseParserTest.java
│
└── frontend/                           # Vue.js 前端
    ├── package.json
    ├── vue.config.js                   # 开发服务器 + API 代理
    ├── tailwind.config.js
    ├── .env.example                    # 前端环境变量模板
    └── src/
        ├── main.js
        ├── App.vue                     # 主应用（左右分栏布局）
        ├── api/index.js                # API 客户端（axios + fetch SSE）
        ├── utils/stream.js             # SSE 流解析工具
        └── components/
            ├── AppHeader.vue
            ├── FileUpload.vue          # 文件上传（支持拖拽）
            ├── TextInput.vue           # 文本输入（字符计数）
            ├── ThinkingPanel.vue       # AI 分析过程展示
            ├── DiffView.vue            # 差异对比视图
            └── ChangesList.vue         # 修改记录列表
```

---

## 本地快速启动指南

### 前置环境

| 依赖 | 最低版本 | 检查命令 |
|------|----------|----------|
| JDK | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Node.js | 18+ | `node -v` |
| npm | 9+ | `npm -v` |

### 第一步：配置环境变量

```bash
# 进入后端目录，复制环境变量模板
cd backend
cp .env.example .env
```

编辑 `.env`，填入你的 LLM API Key：

```bash
# 必填：LLM API 密钥
OPENAI_API_KEY=sk-your-actual-api-key-here

# 可选：API 地址（默认 DeepSeek）
OPENAI_BASE_URL=https://api.deepseek.com/v1

# 可选：模型名称
OPENAI_MODEL=deepseek-v4-pro

# 可选：接口鉴权密钥（留空则跳过认证，仅限开发环境）
API_KEY=
```

> **安全提醒：** `.env` 文件包含真实密钥，已被 `.gitignore` 排除。切勿将其提交到版本控制系统。

### 第二步：启动后端

```bash
cd backend
mvn spring-boot:run
```

后端将在 `http://localhost:8080` 启动。

### 第三步：启动前端

```bash
cd frontend
npm install    # 首次运行需要安装依赖
npm run serve
```

前端将在 `http://localhost:8081` 启动，API 请求自动代理到后端。

### 第四步：访问应用

浏览器打开 **http://localhost:8081**，输入文本或上传文件，点击「开始校对」。

---

## 环境变量配置

### 后端 (`backend/.env`)

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `OPENAI_API_KEY` | 是 | — | LLM API 密钥 |
| `OPENAI_BASE_URL` | 否 | `https://api.deepseek.com/v1` | OpenAI-compatible API 地址 |
| `OPENAI_MODEL` | 否 | `deepseek-v4-pro` | 模型名称 |
| `API_KEY` | 否 | (空) | 接口鉴权密钥，留空则跳过认证 |
| `CORS_ORIGINS` | 否 | `http://localhost:8080,8081,8082,3000,https://proofreader-web.zeabur.app` | CORS 允许来源（逗号分隔），生产环境须包含前端域名 |
| `RATE_LIMIT_TRUST_PROXY` | 否 | `false` | 是否信任代理头（生产环境反向代理后设为 true） |

### 前端 (`frontend/.env`)

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `VUE_APP_API_BASE` | 生产环境必填 | (空，走代理) | 后端 API 基础地址。本地开发留空（走 webpack 代理）；**生产部署时必须设为后端域名**，如 `https://ai-proofreader-v2.zeabur.app` |
| `VUE_APP_SSE_BASE` | 否 | 自动回退到 `VUE_APP_API_BASE`，再回退到 `http://localhost:8080` | SSE 直连地址（绕过代理缓冲）。生产环境通常无需设置，会自动继承 `VUE_APP_API_BASE` |
| `VUE_APP_API_KEY` | 否 | (空) | 接口鉴权密钥（需与后端一致） |

> **生产部署关键：** 前端构建时必须设置 `VUE_APP_API_BASE` 指向后端域名，否则 API 请求会发往前端自身域名导致 405 错误。在 Zeabur 等平台，需在前端服务的「环境变量」中配置。

---

## 测试

```bash
cd backend
mvn test
```

当前测试覆盖：**76 个测试用例，全部通过**。

| 测试类 | 用例数 | 覆盖范围 |
|--------|--------|----------|
| `InputSanitizerTest` | 12 | 注入模式检测（中英文）、标签剥离、角括号编码 |
| `RateLimitInterceptorTest` | 8 | 限流逻辑、IP 获取、trustProxy 开关 |
| `ProofreadControllerTest` | 5 | 输入校验、空文本、超长文本 |
| `LlmServiceTest` | 10 | HTTP 请求构建、JSON 解析、推理内容提取 |
| `FileParseServiceTest` | 16 | 文件类型校验、Magic Byte 验证、文本解析 |
| `StreamingResponseParserTest` | 25 | 三阶段标签解析、增量解析、异常输入处理 |

---

## 生产环境部署

### 架构说明

生产环境采用前后端分离部署：

```
浏览器
  │
  ├──→ 前端静态站点（proofreader-web.zeabur.app）── 返回 HTML/JS/CSS
  │
  └──→ 后端 API 服务（ai-proofreader-v2.zeabur.app）── API + SSE 流
```

前后端部署在不同域名（跨域），浏览器直接向后端发起请求（不走前端的反向代理）。

### 前端部署

1. 在 Zeabur 创建**静态站点**服务，关联 frontend 目录
2. **构建环境变量**（在 Zeabur 服务设置中配置）：

| 变量名 | 值 | 说明 |
|--------|-----|------|
| `VUE_APP_API_BASE` | `https://ai-proofreader-v2.zeabur.app` | **必填**，后端 API 地址 |

> `VUE_APP_SSE_BASE` 无需单独设置，会自动回退到 `VUE_APP_API_BASE`。

3. 构建命令：`npm install && npm run build`，输出目录：`dist`

### 后端部署

1. 在 Zeabur 创建**Docker**或**环境变量**服务，关联 backend 目录
2. 环境变量配置：

| 变量名 | 值 | 说明 |
|--------|-----|------|
| `OPENAI_API_KEY` | `sk-...` | LLM API 密钥（必填） |
| `OPENAI_BASE_URL` | `https://api.deepseek.com/v1` | API 地址 |
| `OPENAI_MODEL` | `deepseek-v4-pro` | 模型名称 |
| `CORS_ORIGINS` | `https://proofreader-web.zeabur.app` | CORS 允许来源（必须包含前端域名） |
| `API_KEY` | (可选) | 接口鉴权密钥 |
| `RATE_LIMIT_TRUST_PROXY` | `true` | Zeabur 使用反向代理，需信任代理头 |

### 常见问题

**405 Method Not Allowed**

前端请求返回 405，通常是因为 `VUE_APP_API_BASE` 未设置或设置错误，导致请求发到了前端自身的静态站点域名。确认前端构建时已设置 `VUE_APP_API_BASE=https://ai-proofreader-v2.zeabur.app`。

**CORS 错误**

后端返回 CORS 错误，检查 `CORS_ORIGINS` 是否包含前端域名 `https://proofreader-web.zeabur.app`。

---

## API 接口

### POST `/api/proofread`

校对文本，返回 SSE 流。

**请求：**
```json
{
  "text": "需要校对的文本内容"
}
```

**响应：** `Content-Type: text/event-stream`

```
event: start
data: {}

event: thinking
data: {"text":"分析过程...","done":false}

event: thinking
data: {"text":"完整分析","done":true}

event: correction
data: {"text":"校对后的文本","done":true}

event: change
data: {"change":{"original":"原词","corrected":"修正","reason":"理由"},"done":false}

event: changes
data: {"changes":[...],"done":true}

event: done
data: {}
```

### POST `/api/parse`

上传文件并提取文本。

**请求：** `multipart/form-data`，字段名 `file`，支持 `.txt` / `.docx` / `.pdf`，最大 20MB。

**响应：**
```json
{
  "text": "提取的文本内容",
  "filename": "document.pdf"
}
```
