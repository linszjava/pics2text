# P00：工程骨架与质量基线

## 1. 阶段信息

| 项目 | 内容 |
|---|---|
| 阶段编号 | P00 |
| 状态 | 已完成 |
| 开始日期 | 2026-09-26 |
| 完成日期 | 2026-09-26 |
| 负责人 | Pics2Text 项目组 |
| 对应版本 | `0.1.0-SNAPSHOT` |

## 2. 阶段目标

建立可编译、可测试、可在本机完整启动的 Java 21 工程基础，并固定模块边界、运行进程、数据迁移、可观测性和持续集成入口，为后续身份权限、照片上传及排版算法开发提供稳定基线。

## 3. 已完成内容

- 建立包含一个父工程、七个领域模块和三个启动模块的 Maven 多模块工程；
- 使用 Maven Enforcer 固定 Java 21 和 Maven 3.9+，并提供固定 Maven 3.9.16 的 Wrapper；
- 确立模块化单体加独立计算 Worker 的部署方式；
- 建立 `domain`、`application`、`infrastructure`、`interfaces` 分层约束，并通过 ArchUnit 阻止领域层反向依赖；
- 创建 API、Analysis Worker、Render Worker 三个 Spring Boot 4.1.1 应用；
- API 接入统一异常响应、错误码、参数校验错误、请求 ID 透传和 JSON 结构化日志；
- 三个应用开放健康检查、指标和 Prometheus 端点；
- 接入 MyBatis-Plus、Sa-Token、PostgreSQL、Redis 和 RabbitMQ 基础依赖；
- 接入 Flyway，并在空数据库成功执行首份迁移；
- 创建 PostgreSQL、Redis、RabbitMQ、SeaweedFS 的 Docker Compose 本地环境；
- 建立 GitHub Actions 构建流程、EditorConfig、Makefile 和本地开发说明。

## 4. 架构与设计决定

1. 当前采用模块化单体，不提前拆分微服务。API 负责同步业务入口，Analysis Worker 和 Render Worker 独立进程承载计算密集型任务。模块边界和异步契约稳定后，仍可按模块迁移为独立服务。
2. 业务持久化使用 MyBatis-Plus，不使用 JPA。领域对象不会继承 MyBatis-Plus 类型，持久化细节只能放在基础设施层。
3. 认证授权基线选择 Sa-Token，具体登录、会话、功能权限和项目级数据权限在 P01 实现。
4. 本地对象存储使用 SeaweedFS 的 S3 兼容接口。原计划中的 MinIO 镜像在当前公共镜像源不可稳定获取，因此改用仍在维护且可本地验证的 SeaweedFS；业务代码继续面向 S3 协议，不绑定具体实现。
5. 本机已有服务占用常见端口，因此 Pics2Text 默认使用 `15432`、`16379`、`19000` 和 `18080`～`18082`，避免停止或覆盖其他项目环境。

## 5. 代码与模块变化

| 模块 | 变化 | 说明 |
|---|---|---|
| `mosaic-shared-kernel` | 新增 | 最小共享领域事件和强类型 ID 契约 |
| `mosaic-identity` | 新增 | 身份与权限领域边界 |
| `mosaic-project` | 新增 | 项目领域边界 |
| `mosaic-photoasset` | 新增 | 照片资产领域边界 |
| `mosaic-layout` | 新增 | 蒙版、网格、分配与布局领域边界 |
| `mosaic-surface` | 新增 | 曲面载体与坐标映射领域边界 |
| `mosaic-rendering` | 新增 | 高清渲染与印刷交付领域边界 |
| `mosaic-api` | 新增 | HTTP 入口、异常处理、请求 ID、数据迁移和运行监控 |
| `mosaic-analysis-worker` | 新增 | 照片分析和布局计算启动进程 |
| `mosaic-render-worker` | 新增 | 高清渲染启动进程 |

## 6. API 变化

本阶段未提供业务 API。三个进程均提供 Spring Boot Actuator 端点：

- `/actuator/health`：存活和就绪状态；
- `/actuator/info`：应用信息入口；
- `/actuator/metrics`：运行指标；
- `/actuator/prometheus`：Prometheus 抓取格式指标。

API 响应统一携带 `X-Request-Id`。客户端提供符合规则的请求 ID 时原样返回，否则服务端生成 UUID。后续业务异常采用 `ApiError` 结构，包含错误码、消息、请求 ID、时间和字段校验错误。

## 7. 数据库变化

Flyway 脚本 `V1__create_platform_metadata.sql` 新增：

- `platform_metadata` 表；
- `metadata_key` 主键；
- `metadata_value`、`created_at`、`updated_at` 字段；
- `schema_initialized=true` 初始化记录。

空 PostgreSQL 18 数据库启动 API 后生成 `flyway_schema_history`，版本 1 状态为成功。

## 8. 消息与事件变化

本阶段仅接入 RabbitMQ 客户端和本地 RabbitMQ 4.1，尚未创建业务交换机、队列和路由键。可靠消息、重试、死信和幂等策略在 P03 实现。

## 9. 配置与部署变化

本地 Compose 服务及默认端口：

| 服务 | 镜像 | 本机端口 |
|---|---|---|
| PostgreSQL | `postgres:18-alpine` | `15432` |
| Redis | `redis:8.2-alpine` | `16379` |
| RabbitMQ | `rabbitmq:4.1-management-alpine` | `5672`、`15672` |
| SeaweedFS S3 | `chrislusf/seaweedfs:4.47` | `19000` |

应用默认端口为 API `18080`、Analysis Worker `18081`、Render Worker `18082`。数据库、Redis、RabbitMQ 和端口均支持环境变量覆盖，示例见 `.env.example`。默认凭据只适用于本地开发。

## 10. 安全与权限影响

- Sa-Token 已纳入依赖管理，但本阶段没有开放登录接口或受保护业务资源；
- 请求 ID 只接受长度不超过 128 的字母、数字及有限安全符号，避免日志注入；
- 未处理异常仅在服务端记录堆栈，对外返回固定中文消息；
- `.env` 被 Git 忽略，仓库只保存无生产价值的开发示例凭据；
- Actuator 当前用于本地基础验收，生产暴露范围和访问控制将在 P11 收紧。

## 11. 测试与验证结果

| 测试类型 | 命令或方法 | 结果 |
|---|---|---|
| 单元测试 | `./mvnw --batch-mode --no-transfer-progress verify` | 3 项请求 ID 测试通过 |
| 架构测试 | 同上 | 1 项 ArchUnit 领域依赖约束测试通过 |
| 全量构建 | 同上 | 11 个 Reactor 模块全部成功 |
| 依赖集成 | `docker compose up -d --wait` | 4 个依赖容器全部 healthy |
| 应用验收 | 启动三个可执行 JAR 并访问健康端点 | 三个端点均返回 `status=UP` |
| 数据迁移 | 查询 `flyway_schema_history` 和 `\dt` | 版本 1 成功，目标表存在 |
| 指标验收 | 访问 API `/actuator/prometheus` | HTTP 200 |

复现步骤：

```bash
docker compose up -d --wait
./mvnw --batch-mode --no-transfer-progress verify
make api
make analysis-worker
make render-worker
```

三个 `make` 启动命令需要在三个终端分别运行。然后访问 README 中列出的健康检查地址。

## 12. 运行与使用说明

首次运行可复制 `.env.example` 为 `.env`，再执行 `docker compose up -d --wait`。本地依赖数据保存在 Docker 命名卷中，普通 `docker compose down` 不会删除数据；只有明确执行 `docker compose down --volumes` 才会删除本项目数据卷。

完整命令、端口、环境变量和服务地址见项目根目录 README。

## 13. 已知限制与遗留事项

- 当前领域模块只有边界标记和共享基础类型，业务模型从 P01、P02 开始实现；
- Worker 尚未声明业务队列或消费者，P03 完成可靠任务基础后接入；
- 对象存储只完成服务可用性验证，桶生命周期、预签名上传和对象 Key 规范在 P02 实现；
- OpenCV、libvips 和 GPU 运行时尚未接入，分别在 P04、P06、P09 按算法需求引入；
- 当前没有前端工程，完整用户流程在 P10 交付；
- 本地 SeaweedFS 未启用静态数据加密，生产对象存储安全策略在 P11 定义。

## 14. 下一阶段注意事项

P01 可以依赖 Java 21、Spring Boot 4、Sa-Token、Redis、MyBatis-Plus、Flyway 和统一异常结构。新增身份代码必须保持领域层无框架依赖；数据库变更只能新增顺序递增的 Flyway 脚本，禁止修改已执行的 V1。

## 15. 验收清单

- [x] 计划功能已经实现
- [x] 自动化测试通过
- [x] 阶段验收项通过
- [x] Flyway 脚本完整并验证
- [x] API 说明已更新
- [x] 配置与部署变化已记录
- [x] 安全影响已检查
- [x] 已知限制已记录
- [x] `docs/progress/README.md` 已更新
- [x] 阶段完成文档已自检
