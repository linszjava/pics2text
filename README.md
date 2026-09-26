# Pics2Text

将大量照片自动排版为指定文字，并支持曲面载体预览及超大印刷文件输出的平台。

P00 工程基础、P01 身份认证与项目权限、P02 项目与照片上传已经完成，下一阶段为 P03 异步任务与可靠消息。代码采用模块化单体架构，计算密集型任务通过独立 Worker 进程运行；在业务边界和容量要求尚未稳定前不拆分微服务。

## 项目文档

- [项目开发计划](docs/PROJECT_PLAN.md)
- [OpenAPI 说明](docs/api/openapi.yaml)
- [阶段完成文档模板](docs/templates/PHASE_COMPLETION_TEMPLATE.md)

## 技术基线

- Java 21
- Spring Boot 4.1.x
- Sa-Token（Spring Boot 4 Starter）
- MyBatis-Plus（Spring Boot 4 Starter）
- PostgreSQL
- Redis
- RabbitMQ
- S3 兼容对象存储（开发环境使用 SeaweedFS）
- OpenCV + libvips
- React + TypeScript + PixiJS + Three.js

## 工程模块

| 模块 | 职责 |
|---|---|
| `mosaic-shared-kernel` | 跨领域共享的最小类型与领域事件契约 |
| `mosaic-identity` | 身份、认证和权限领域 |
| `mosaic-project` | 项目与项目成员领域 |
| `mosaic-photoasset` | 照片资产、上传和分析元数据领域 |
| `mosaic-layout` | 文字蒙版、网格、分配和布局领域 |
| `mosaic-surface` | 曲面载体与坐标映射领域 |
| `mosaic-rendering` | 高清渲染和印刷交付领域 |
| `mosaic-api` | HTTP API、认证入口和数据库迁移 |
| `mosaic-analysis-worker` | 照片分析与布局计算进程 |
| `mosaic-render-worker` | 高清渲染进程 |

每个业务模块后续在内部按 `domain`、`application`、`infrastructure`、`interfaces` 分层。领域层不得依赖基础设施层或接口层，该约束由 ArchUnit 测试持续校验。

## 本地开发

前置要求：JDK 21、Docker Desktop 或兼容的 Docker Compose 环境。项目内置 Maven Wrapper，会自动使用 Maven 3.9.16。

```bash
cp .env.example .env
docker compose up -d
./mvnw --batch-mode --no-transfer-progress verify
```

`.env` 仅用于本地覆盖默认配置，不应提交到 Git。默认值均为开发环境凭据，不能用于生产环境。

启动三个 Java 进程：

```bash
make api
make analysis-worker
make render-worker
```

默认服务地址：

| 服务 | 地址 |
|---|---|
| API 健康检查 | `http://localhost:18080/actuator/health` |
| Analysis Worker 健康检查 | `http://localhost:18081/actuator/health` |
| Render Worker 健康检查 | `http://localhost:18082/actuator/health` |
| PostgreSQL | `localhost:15432` |
| Redis | `localhost:16379` |
| RabbitMQ 管理台 | `http://localhost:15672` |
| S3 兼容对象存储 | `http://localhost:19000` |

常用命令：

```bash
make infra-status
make verify
make infra-down
```

## 配置约定

Java 应用可通过环境变量覆盖默认连接信息，例如 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`RABBITMQ_HOST`、`RABBITMQ_PORT`、`S3_ENDPOINT`、`S3_PUBLIC_ENDPOINT`、`S3_ACCESS_KEY` 和 `S3_SECRET_KEY`。生产环境必须由密钥管理系统注入凭据，对象桶必须保持私有。

照片上传使用 S3 预签名 URL：先调用上传初始化接口，小文件直接 PUT 到返回 URL；大文件逐片获取 URL 并上传，最后把分片 ETag 清单提交到完成接口。API 会重新校验实际 SHA-256、大小、魔数和图片尺寸，验真通过后照片才进入 `AVAILABLE`。完整契约见 [OpenAPI 说明](docs/api/openapi.yaml)。

## 首次管理员初始化

生产部署前必须将 `IDENTITY_BOOTSTRAP_TOKEN` 替换为随机高强度值。初始化接口通过数据库原子状态保证只能成功执行一次：

```bash
curl -X POST http://localhost:18080/api/v1/auth/bootstrap \
  -H 'Content-Type: application/json' \
  -H 'X-Bootstrap-Token: pics2text-bootstrap-dev' \
  -d '{"username":"admin","password":"Replace-Me-2026!","displayName":"系统管理员"}'
```

登录成功后使用响应中的 `tokenValue` 访问受保护接口：

```bash
curl http://localhost:18080/api/v1/auth/me \
  -H 'Authorization: Bearer <tokenValue>'
```

密码必须为 12 至 128 位，并同时包含大小写字母、数字和特殊字符。默认连续失败 5 次后，同一用户名与客户端地址组合将被限制 15 分钟。
