# Pics2Text

将大量照片自动排版为指定文字，并支持曲面载体预览及超大印刷文件输出的平台。

P00 工程基础至 P10 运营后台与完整前端流程已经完成，下一阶段为 P11 安全与数据治理。代码采用模块化单体架构，计算密集型任务通过独立 Worker 进程运行；在业务边界和容量要求尚未稳定前不拆分微服务。

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
- OpenCV（照片分析）+ libvips/pyvips（分块 BigTIFF 超大图渲染）
- React 19 + TypeScript + Ant Design 6 + PixiJS + Three.js

## 工程模块

| 模块 | 职责 |
|---|---|
| `mosaic-identity` | 身份、认证和权限领域 |
| `mosaic-project` | 项目与项目成员领域 |
| `mosaic-photoasset` | 照片资产、上传和分析元数据领域 |
| `mosaic-job` | 统一作业状态机、Outbox、RabbitMQ 拓扑、幂等消费与超时恢复 |
| `mosaic-layout` | 文字蒙版、网格、分配和布局领域 |
| `mosaic-surface` | 曲面载体与坐标映射领域 |
| `mosaic-rendering` | 高清渲染和印刷交付领域 |
| `mosaic-api` | HTTP API、认证入口和数据库迁移 |
| `mosaic-analysis-worker` | 照片分析与布局计算进程 |
| `mosaic-render-worker` | 高清渲染进程 |
| `mosaic-web` | React + TypeScript + PixiJS 布局编辑器与 Three.js 曲面预览 |

每个业务模块后续在内部按 `domain`、`application`、`infrastructure`、`interfaces` 分层。领域层不得依赖基础设施层或接口层，该约束由 ArchUnit 测试持续校验。

## 本地开发

前置要求：JDK 21、Node.js 22、Docker Desktop 或兼容的 Docker Compose 环境。项目内置 Maven Wrapper，会自动使用 Maven 3.9.16。

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
make render-worker-docker
make web-install
make web-dev
```

默认服务地址：

| 服务 | 地址 |
|---|---|
| 布局编辑器 | `http://localhost:15173/?projectId=<项目UUID>&layoutId=<布局UUID>` |
| 圆柱三维预览 | `http://localhost:15173/?view=surface&projectId=<项目UUID>&layoutId=<布局UUID>` |
| PostgreSQL | `localhost:15432` |
| Redis | `localhost:16379` |
| RabbitMQ 管理台 | `http://localhost:15672` |
| S3 兼容对象存储 | `http://localhost:19000` |

常用命令：

```bash
make infra-status
make verify
make web-test
make web-build
make infra-down
```

本地数据保护命令：

```bash
# 在线导出 PostgreSQL，并短暂停止 SeaweedFS 生成一致的对象卷快照
make backup

# 仅恢复到临时 PostgreSQL 容器和临时对象卷，不覆盖当前业务数据
make restore-drill BACKUP_DIR=backups/<备份目录>
```

备份默认写入被 Git 忽略的 `backups/`。恢复演练会先验证清单与 SHA-256，再检查业务表、对象文件和 SeaweedFS 健康状态，结束后自动清理临时资源。

## 配置约定

Java 应用可通过环境变量覆盖默认连接信息，例如 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`RABBITMQ_HOST`、`RABBITMQ_PORT`、`S3_ENDPOINT`、`S3_PUBLIC_ENDPOINT`、`S3_ACCESS_KEY` 和 `S3_SECRET_KEY`。生产环境必须由密钥管理系统注入凭据，对象桶必须保持私有。

照片上传使用 S3 预签名 URL：先调用上传初始化接口，小文件直接 PUT 到返回 URL；大文件逐片获取 URL 并上传，最后把分片 ETag 清单提交到完成接口。API 会重新校验实际 SHA-256、大小、魔数和图片尺寸，验真通过后照片才进入 `AVAILABLE`。验真在数据库长事务外流式进行；进程异常中断后，可通过 `PHOTO_VERIFICATION_LEASE`（默认 30 分钟）调整允许重新认领的超时。complete 的单实例容量保护及真实链路压测方法见 [上传容量与压测说明](docs/photo-upload-load-test.md)，完整契约见 [OpenAPI 说明](docs/api/openapi.yaml)。

异步作业通过数据库事务同时写入 `processing_job` 与 `outbox_event`，发布器收到 RabbitMQ Publisher Confirm 后才将事件标记为已发布。Worker 使用手动 ACK、消费幂等记录、有限指数退避、专用重试队列和死信队列；API 负责心跳超时接管，并通过 SSE 推送进度。业务代码通过 `JobCoordinator` 创建作业，Worker 通过 `JobHandler` 扩展具体算法。

上传验真通过后会自动创建 `PHOTO_ANALYSIS` 作业。Analysis Worker 对源文件执行字节数和像素数双重限制、EXIF 方向纠正与元数据清理，生成规范化 JPEG 及 256/1024/2048 三档 WebP；同时计算拉普拉斯清晰度、亮度、对比度、Lab 均值、dHash/pHash、主体或人脸保护框和推荐裁剪。可通过 `PHOTO_ANALYSIS_MAX_SOURCE_BYTES`、`PHOTO_ANALYSIS_MAX_PIXELS`、质量阈值及 `PHOTO_FACE_CASCADE_PATH` 调整分析策略；未配置人脸级联模型时使用主体边缘区域兜底。分析结果和人工审核接口见 [OpenAPI 说明](docs/api/openapi.yaml)。

文字蒙版通过 `TEXT_MASK_GENERATION` 作业异步生成，支持横排、竖排、100—900 字重、字距/行距、规则网格和错位网格。生成结果包括灰度 PNG、带槽位框的调试 PNG、内容边界、估算笔画宽度、覆盖率、可读性警告和持久化网格槽位。仓库内置 Noto Sans SC 可变字体，并同时保存 OFL 许可证、来源、版本标识和 SHA-256；蒙版配置摘要包含算法版本与字体校验值，避免字体升级造成静默漂移。

自动布局可导入为可编辑草稿。每批交换、替换或裁剪命令都记录操作者、时间、前后值，并生成完整的不可变 `LayoutVersion`；客户端批次 UUID 支持失败重放，数据库乐观锁防止并发静默覆盖。布局必须先锁定才能发布，锁定或已发布布局不可修改。PixiJS 编辑器按视口查询槽位并分批加载私有缩略图，支持缩放、平移、按槽位或照片定位、本地草稿恢复和自动保存；Render Worker 可根据版本生成最长边不超过 2048 像素的低分辨率颜色预览。

载体表面使用毫米作为唯一物理单位，圆柱模板可配置直径、轴向长度、接缝、安全边距、禁印区、舱段和关键结构。锁定布局映射后会生成不可变的物理坐标、UV 坐标和三维坐标清单；接缝、安全边距或禁印区冲突会阻止发布。发布会将已验证清单与最终不可变版本绑定，Three.js 三维预览和后续 P09 印刷渲染共享该坐标源。

高清印刷渲染以发布版本和曲面坐标清单为不可变输入，Java Worker 负责权限、作业状态、瓦片空间索引、校验和与审批，受限 Python/pyvips 子进程负责按需裁切、ICC 转换、分块合成和 BigTIFF 输出。只有文件头、大小和 SHA-256 全部验证且输出清单上传完成后，成品才进入待审批状态；审批通过后才能获取私有下载 URL。推荐通过 `make render-worker-docker` 启动内置 libvips 与 sRGB ICC 的 Worker。

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

当前开发阶段密码允许 5 至 128 位简单字符串；上线前必须恢复复杂度要求。默认连续失败 5 次后，同一用户名与客户端地址组合将被限制 15 分钟。
公开注册默认开启，新注册账号固定为普通 `USER` 角色；生产环境可设置 `IDENTITY_REGISTRATION_ENABLED=false` 关闭。
