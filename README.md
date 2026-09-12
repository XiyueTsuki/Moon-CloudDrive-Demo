# Moon-CloudDrive-Demo

Moon-CloudDrive-Demo 是一个前后端分离的云盘文件分享系统，支持文件上传、下载、删除、重命名以及创建带提取码的分享链接，可供他人访问和下载。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 4.1.0（Java 17） |
| 持久层 | MyBatis-Plus 3.5.16 + MySQL 8.4 |
| 鉴权 | Sa-Token 1.46 + Redis |
| 缓存/锁 | Redis 8.2 + Redisson 3.43 |
| 对象存储 | Aliyun OSS |
| 邮件服务 | Spring Boot Mail（验证码） |
| 前端框架 | Vue 3.5 + TypeScript 6.0 |
| 构建工具 | Vite 8.2 |
| UI 组件库 | Element Plus 2.14 |
| 状态管理 | Pinia 4.0 |
| 路由 | Vue Router 5.2 |
| HTTP 客户端 | Axios 1.20 |

## 项目结构

```
Moon-CloudDrive-Demo/
├── Moon-CloudDrive-Demo-Backend/     # Spring Boot 后端
│   └── src/main/java/com/xiyuetsuki/moonclouddrivedemo/
│       ├── annotation/               # 自定义注解（限流）
│       ├── aspect/                   # AOP 切面
│       ├── config/                   # 配置类（OSS、Redisson、Sa-Token）
│       ├── controller/               # 控制器层
│       ├── domain/                   # 实体、DTO、通用响应
│       ├── exception/                # 异常处理
│       ├── mapper/                   # MyBatis-Plus Mapper
│       ├── service/                  # 业务逻辑层
│       └── util/                     # 工具类
├── Moon-CloudDrive-Demo-Web/         # Vue 3 前端
│   └── src/
│       ├── api/                      # API 请求封装
│       ├── router/                   # 路由配置
│       ├── stores/                   # Pinia 状态管理
│       ├── types/                    # TypeScript 类型定义
│       └── views/                    # 页面组件
├── sql/                              # 数据库建表脚本
├── postman/                          # Postman 接口集合
├── jmeter/                           # JMeter 压测脚本
└── docker-compose.yml                # Docker 基础服务编排
```

## 功能模块

### 用户模块

| 功能 | API | 说明 |
|------|-----|------|
| 发送邮箱验证码 | `POST /api/user/send-code` | 注册前发送验证码到邮箱 |
| 用户注册 | `POST /api/user/register` | 邮箱 + 验证码 + 密码注册 |
| 用户登录 | `POST /api/user/login` | 邮箱 + 密码登录，返回 Token |

### 文件模块

| 功能 | API | 说明 |
|------|-----|------|
| 文件上传 | `POST /api/file/upload` | 异步上传至阿里云 OSS |
| 上传进度 | `GET /api/file/progress?taskId=` | 轮询上传进度 |
| 文件列表 | `GET /api/file/list` | 查询当前用户的文件列表 |
| 文件下载 | `GET /api/file/download?fileId=` | 获取 OSS 预签名下载 URL |
| 文件删除 | `DELETE /api/file/delete?fileId=` | 删除文件（OSS + 数据库） |
| 文件重命名 | `PUT /api/file/rename?fileId=&newName=` | 重命名文件 |

### 分享模块

| 功能 | API | 说明 |
|------|-----|------|
| 创建分享 | `POST /api/share/create` | 支持设置提取码、有效时长、最大下载次数 |
| 我的分享 | `GET /api/share/my` | 查询当前用户创建的分享列表 |
| 取消分享 | `DELETE /api/share/{shareCode}` | 手动取消分享链接 |
| 查看分享 | `GET /share/{shareCode}` | 查看分享文件元信息（不计下载次数） |
| 验证提取码 | `POST /share/{shareCode}/verify` | 验证提取码（不计下载次数） |
| 下载分享文件 | `GET /share/{shareCode}/download` | 获取下载链接（消耗一次下载次数） |

## 快速开始

### 环境要求

- JDK 17+
- Node.js 20+
- Maven 3.6+
- Docker（用于启动 MySQL 和 Redis）

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

### 3. 初始化数据库

执行 `sql/` 目录下的建表脚本：

```bash
# 连接 MySQL 后依次执行
source sql/tb_user.sql;
source sql/tb_file.sql;
source sql/tb_share.sql;
```

### 4. 配置后端

编辑 `Moon-CloudDrive-Demo-Backend/src/main/resources/application.yml`，按实际情况配置：

- 数据库连接地址、用户名、密码
- Redis 连接地址
- 阿里云 OSS（AccessKey、Bucket、Endpoint）
- 邮箱 SMTP（用于发送验证码）

### 5. 启动后端

```bash
cd Moon-CloudDrive-Demo-Backend
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`。

### 6. 启动前端

```bash
cd Moon-CloudDrive-Demo-Web
npm install
npm run dev
```

前端开发服务器运行在 `http://localhost:5173`。

### 7. 访问

浏览器打开 `http://localhost:5173`，注册账号后即可使用。

## 前端路由

| 路径 | 页面 | 是否需要登录 |
|------|------|:----------:|
| `/login` | 登录页 | 否 |
| `/register` | 注册页 | 否 |
| `/` | 文件管理首页（上传、列表、下载、重命名、删除） | 是 |
| `/shares` | 分享管理（查看/取消分享） | 是 |
| `/share/:shareCode` | 分享文件访问页（提取码验证 + 下载） | 否 |

## 架构说明

- **前后端分离**：Vue 3 前端通过 Axios 调用 Spring Boot REST API
- **Hash 路由**：采用 `createWebHashHistory`，避免部署时刷新 404
- **Token 鉴权**：Sa-Token + Redis 实现分布式 Session
- **异步上传**：文件上传到 OSS 后异步处理，前端轮询进度
- **分享链接**：8 位 Base62 随机码，支持提取码保护、有效期和下载次数限制
- **下载计数**：仅在用户实际点击下载时递增，验证提取码不消耗次数
- **自动失效**：过期或达到最大下载次数后自动将链接状态置为失效
- **API 限流**：通过自定义 `@RateLimit` 注解 + AOP 实现 IP 级别限流

## Postman 接口调试

项目 `postman/` 目录下提供了完整的接口集合，可直接导入 Postman 使用，包含用户、文件、分享三大模块的所有接口及请求示例。

## License

暂无