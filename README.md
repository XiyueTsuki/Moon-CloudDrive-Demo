# Moon-CloudDrive-Demo

Moon-CloudDrive-Demo 是一个前后端分离的云盘文件分享系统，支持文件上传、下载、预览、批量操作、打包下载、回收站、分享链接等功能。删除的文件会进入回收站保留 30 天，分享链接支持提取码、有效期和下载次数限制。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 4.1.0（Java 17） |
| 持久层 | MyBatis-Plus 3.5.16 + MySQL 8.4 |
| 鉴权 | Sa-Token 1.46 + Redis |
| 缓存 | Redis 8.2 + Redisson 3.43（Spring Cache 集成） |
| 消息队列 | RocketMQ（打包下载异步处理） |
| 对象存储 | Aliyun OSS |
| PDF 处理 | Apache PDFBox（服务端 PDF 转图片） |
| 邮件服务 | Spring Boot Mail（验证码） |
| API 文档 | Knife4j |
| 前端框架 | Vue 3.5 + TypeScript 6.0 |
| 构建工具 | Vite 8.2 |
| UI 组件库 | Element Plus 2.14 |
| 状态管理 | Pinia 4.0 |
| 路由 | Vue Router 5.2 |
| HTTP 客户端 | Axios 1.20 |
| 代码高亮 | highlight.js 11.12（文本预览） |

## 项目结构

```
Moon-CloudDrive-Demo/
├── Moon-CloudDrive-Demo-Backend/     # Spring Boot 后端
│   └── src/main/java/com/xiyuetsuki/moonclouddrivedemo/
│       ├── annotation/               # 自定义注解（限流）
│       ├── aspect/                   # AOP 切面
│       ├── config/                   # 配置（OSS、Redisson、RocketMQ、Sa-Token、PDF、Knife4j）
│       ├── consumer/                 # RocketMQ 消费者（打包下载）
│       ├── controller/               # 控制器层
│       ├── domain/
│       │   ├── common/               # 通用响应体
│       │   ├── dto/                  # 数据传输对象
│       │   ├── entity/               # 数据库实体
│       │   └── message/              # RocketMQ 消息体
│       ├── exception/                # 异常处理
│       ├── mapper/                   # MyBatis-Plus Mapper
│       ├── scheduled/                # 定时任务（回收站清理）
│       ├── service/                  # 业务逻辑层
│       └── util/                     # 工具类
├── Moon-CloudDrive-Demo-Web/         # Vue 3 前端
│   └── src/
│       ├── api/                      # API 请求封装（user、file、share）
│       ├── components/               # 公共组件（PDF 预览、上传任务面板）
│       ├── events/                   # 事件总线（文件操作事件）
│       ├── layouts/                  # 布局组件（MainLayout）
│       ├── router/                   # 路由配置（Hash 路由 + 导航守卫）
│       ├── stores/                   # Pinia 状态管理（用户登录态、上传任务追踪）
│       ├── types/                    # TypeScript 类型定义
│       ├── utils/                    # 工具函数（分片上传）
│       └── views/                    # 页面组件（Home、Login、Register、RecycleBin、ShareManage、ShareAccess）
├── sql/                              # 数据库建表脚本
└── docker-compose.yml                # Docker 基础服务编排
```

---

## 功能模块

### 用户模块

| 功能 | API | 说明 |
|------|-----|------|
| 发送邮箱验证码 | `POST /api/user/send-code` | 注册前发送验证码到邮箱 |
| 用户注册 | `POST /api/user/register` | 邮箱 + 验证码 + 密码注册 |
| 用户登录 | `POST /api/user/login` | 邮箱 + 密码登录，返回 Token |
| 修改密码 | `POST /api/user/change-password` | 旧密码 + 新密码，需登录后操作 |

### 文件模块

#### 普通上传

| 功能 | API | 说明 |
|------|-----|------|
| 文件上传 | `POST /api/file/upload` | 异步上传至阿里云 OSS |
| 上传进度 | `GET /api/file/progress?taskId=` | 轮询上传进度 |
| 文件列表 | `GET /api/file/list` | 分页查询文件列表，支持排序和关键词搜索，文件夹始终排前 |
| 文件下载 | `GET /api/file/download?fileId=` | 获取 OSS 预签名下载 URL |
| 文件删除（软删除） | `DELETE /api/file/delete?fileId=` | 将文件/文件夹移入回收站，30 天后自动彻底删除 |
| 文件重命名 | `PUT /api/file/rename?fileId=&newName=` | 重命名文件或文件夹 |

#### 分片上传（大文件）

| 功能 | API | 说明 |
|------|-----|------|
| 初始化分片上传 | `POST /api/file/chunk/init` | 返回 uploadId 和分片信息，文件哈希已存在则秒传 |
| 上传分片 | `POST /api/file/chunk/upload` | 上传单个分片，chunkIndex 从 0 开始 |
| 完成分片上传 | `POST /api/file/chunk/complete` | 所有分片上传完毕后合并文件 |
| 查询分片进度 | `GET /api/file/chunk/progress` | 查询已上传分片列表，用于断点续传 |
| 取消分片上传 | `DELETE /api/file/chunk/abort` | 取消上传并清理 OSS 碎片 |

#### 文件夹操作

| 功能 | API | 说明 |
|------|-----|------|
| 创建文件夹 | `POST /api/file/folder/create` | 在指定父文件夹下创建新文件夹 |
| 移动文件/文件夹 | `PUT /api/file/folder/move` | 移动到目标目录，后端校验循环引用和同名冲突 |
| 获取文件夹路径 | `GET /api/file/folder/path` | 返回根目录到当前文件夹的面包屑路径（递归 CTE 查询） |

#### 批量操作（原子性）

> 批量操作在 `@Transactional` 事务中执行，确保全部成功或全部回滚。

| 功能 | API | 说明 |
|------|-----|------|
| 批量删除 | `POST /api/file/batch/delete` | 批量移入回收站，文件夹递归删除所有子孙节点 |
| 批量移动 | `POST /api/file/batch/move` | 批量移动到目标目录，校验循环引用和同名冲突 |
| 批量重命名 | `POST /api/file/batch/rename` | 支持序号模板(sequence)、添加前缀(prefix)、添加后缀(suffix)、替换文本(replace) |

#### 打包下载

| 功能 | API | 说明 |
|------|-----|------|
| 提交打包任务 | `POST /api/file/pack/prepare` | 提交多文件打包任务，返回 taskId |
| 查询打包进度 | `GET /api/file/pack/progress` | 轮询打包进度 |
| 下载 ZIP 文件 | `GET /api/file/pack/download` | 下载打包完成的 ZIP 文件 |

#### 文件预览

| 功能 | API | 说明 |
|------|-----|------|
| 获取预览信息 | `GET /api/file/preview/info` | 根据文件类型返回预览方式（图片/视频/音频/PDF/文本/不支持） |
| 流式代理 | `GET /api/file/preview/stream` | 服务端中转文件流，不暴露 OSS URL |
| 文本预览 | `GET /api/file/preview/text` | 返回文本文件内容（最大 1MB），前端代码高亮渲染 |
| PDF 预览信息 | `GET /api/file/preview/pdf` | 返回总页数及每页图片 URL，首次访问触发服务端 PDF→PNG 异步转换 |
| PDF 单页图片 | `GET /api/file/preview/pdf/page/{pageNum}` | 返回指定页 PNG 图片流，浏览器缓存 24 小时 |

### 回收站模块

| 功能 | API | 说明 |
|------|-----|------|
| 回收站列表 | `GET /api/file/recycle-bin/list` | 查询当前用户的回收站文件 |
| 恢复文件 | `PUT /api/file/recycle-bin/restore?fileId=` | 将文件从回收站恢复为正常状态 |
| 彻底删除 | `DELETE /api/file/recycle-bin/permanent-delete?fileId=` | 物理删除数据库记录 + OSS 文件，不可恢复 |
| 自动清理 | `@Scheduled cron: 0 0 2 * * ?` | 每天凌晨 2 点清理超过 30 天的回收站文件 |

### 分享模块

| 功能 | API | 说明 |
|------|-----|------|
| 创建分享 | `POST /api/share/create` | 支持设置提取码、有效时长、最大下载次数 |
| 我的分享 | `GET /api/share/my` | 查询当前用户创建的分享列表 |
| 取消分享 | `DELETE /api/share/{shareCode}` | 手动取消分享链接 |
| 查看分享 | `GET /share/{shareCode}` | 查看分享文件元信息（不计下载次数） |
| 验证提取码 | `POST /share/{shareCode}/verify` | 验证提取码（不计下载次数） |
| 下载分享文件 | `GET /share/{shareCode}/download` | 获取下载链接（消耗一次下载次数） |

---

## 快速开始

### 环境要求

- JDK 17+
- Node.js 20+
- Maven 3.6+
- Docker（用于启动基础设施）

### 1. 克隆项目

```bash
git clone <repository-url>
cd Moon-CloudDrive-Demo
```

### 2. 启动基础设施

```bash
docker compose up -d
```

启动后将创建：
- MySQL 8.4 → `localhost:4000`（root/123）
- Redis 8.2 → `localhost:4001`
- RocketMQ → `localhost:9876`（NameServer，Broker 自动注册）

### 3. 初始化数据库

> 全新部署：依次执行 `sql/` 目录下的建表脚本。

```bash
source sql/tb_user.sql;
source sql/tb_file.sql;
source sql/tb_share.sql;
```

### 4. 配置后端

编辑 `Moon-CloudDrive-Demo-Backend/src/main/resources/application.yml`，按实际情况配置：

- 数据库连接地址、用户名、密码
- Redis 连接地址
- RocketMQ NameServer 地址
- 阿里云 OSS（AccessKey、Bucket、Endpoint）
- 邮箱 SMTP（用于发送验证码）

### 5. 启动后端

```bash
cd Moon-CloudDrive-Demo-Backend
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`，Knife4j API 文档：`http://localhost:8080/doc.html`。

### 6. 启动前端

```bash
cd Moon-CloudDrive-Demo-Web
npm install
npm run dev
```

前端开发服务器运行在 `http://localhost:5173`。

### 7. 访问

浏览器打开 `http://localhost:5173`，注册账号后即可使用。

---

## 前端路由

| 路径 | 页面 | 描述 | 需登录 |
|------|------|------|:------:|
| `/login` | 登录页 | 邮箱 + 密码登录 | 否 |
| `/register` | 注册页 | 邮箱 + 验证码 + 密码注册 | 否 |
| `/` | 文件管理首页 | 上传、列表、预览、下载、重命名、删除、创建文件夹、批量操作、打包下载 | 是 |
| `/shares` | 分享管理 | 查看/取消自己创建的分享链接 | 是 |
| `/recycle-bin` | 回收站 | 恢复/彻底删除已删除的文件 | 是 |
| `/share/:shareCode` | 分享访问页 | 提取码验证 + 下载分享文件 | 否 |

---

## 架构说明

### 上传体系
- **普通上传**：文件通过 `@Async` 异步上传至 OSS，前端轮询进度。支持 SHA-256 秒传——相同哈希值的文件直接复用已有 OSS 记录
- **分片上传**：大文件自动分片，支持断点续传，所有分片上传完毕后服务端调用 OSS 合并 API，同样支持秒传

### 缓存策略（Redisson Spring Cache）
- **缓存区域**：`fileList`（2min）、`folderPath`（10min）、`recycleBin`（2min）
- **缓存穿透防护**：空结果同样缓存（如空列表），不存在的数据直接抛异常不缓存
- **缓存击穿防护**：不同缓存区域使用不同 TTL，Redisson RMapCache 原子写入避免并发加载
- **缓存雪崩防护**：各区域使用差异化 TTL，避免同时大面积失效
- **失效时机**：所有写入操作（上传、删除、重命名、移动、创建文件夹、批量操作等）均会清除对应缓存

### 文件预览
- **图片/视频/音频**：直接返回 OSS 预签名 URL 或流式代理
- **文本/代码**：服务端读取内容返回，前端使用 highlight.js 代码高亮
- **PDF**：服务端使用 Apache PDFBox 将 PDF 逐页转为 PNG 图片存储至 OSS，首次访问触发异步转换，后续访问直接读取；前端按需懒加载，浏览器缓存 24 小时

### 批量操作原子性
- 批量删除、移动、重命名均在 `@Transactional` 事务中执行，确保全部成功或全部回滚
- 移动操作校验目标目录存在性、非自身、非子孙节点（防循环引用）
- 重命名操作校验空名称、特殊字符及同目录下同名冲突

### 打包下载
- 前端提交文件 ID 列表，服务端通过 RocketMQ 消息队列异步处理
- 消费者逐一下载 OSS 文件 → 内存打包为 ZIP → 上传至 OSS
- 前端轮询进度，完成后下载 ZIP 文件

### 前端架构

- **路由设计**：`createWebHashHistory` 避免刷新 404；`/` 嵌套路由共享 `MainLayout` 导航栏；`beforeEach` 守卫校验登录态，未登录跳转 `/login`，已登录访问登录/注册页重定向到 `/`
- **状态管理**：`userStore` 管理登录 Token 和用户信息；`uploadStore` 管理上传任务列表，轮询各任务进度并更新 UI
- **分片上传**：`utils/chunkUpload.ts` 封装分片上传流程（init → 并发上传分片 → complete），前端计算 SHA-256 哈希用于秒传判定
- **批量操作**：Home 页支持多选文件后批量删除、移动、重命名，移动时通过 `el-tree` 懒加载展示完整目录树
- **PDF 预览**：`PdfImageViewer.vue` 使用 IntersectionObserver 实现按需懒加载，仅渲染可视区域内的页面，关闭时清理 DOM 引用防止内存泄漏
- **代码高亮**：文本预览使用 `highlight.js` 自动检测语言并渲染高亮，支持 190+ 编程语言
- **事件总线**：`events/fileEvents.ts` 用于跨组件通信，如上传完成后通知文件列表刷新
- **上传任务面板**：`UploadTaskPanel.vue` 悬浮展示所有进行中的上传任务，支持查看进度和取消任务

### 其他特性
- **软删除**：文件删除进入回收站保留 30 天
- **定时清理**：`@Scheduled` 每天凌晨 2 点清理过期回收站文件
- **分享安全**：8 位 Base62 随机码，支持提取码、有效期、下载次数限制
- **API 限流**：`@RateLimit` 注解 + AOP，IP 级别限流

## License

暂无