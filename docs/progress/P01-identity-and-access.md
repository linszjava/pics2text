# P01：身份认证与项目权限

## 1. 阶段信息

| 项目 | 内容 |
|---|---|
| 阶段编号 | P01 |
| 状态 | 已完成 |
| 开始日期 | 2026-09-26 |
| 完成日期 | 2026-09-26 |
| 负责人 | Pics2Text 项目组 |
| 对应版本 | `0.1.0-SNAPSHOT` |

## 2. 阶段目标

完成用户身份、会话、功能权限和项目级数据权限基础，使后续照片、布局和渲染接口可以依赖统一的登录状态与项目成员授权结果。

## 3. 已完成内容

- 建立用户、全局角色、用户状态、项目角色和项目成员领域模型；
- 使用 Sa-Token 完成登录、登出、会话续期和管理员强制下线；
- 使用 Redis 保存 Sa-Token 会话，完成两个 API 实例之间的会话共享验证；
- 使用 BCrypt 12 轮保存密码，密码策略要求 12 至 128 位且包含大小写字母、数字和特殊字符；
- 使用固定耗时的虚拟 BCrypt 校验降低用户名枚举的时间差异；
- 建立系统管理员和普通用户两种全局角色，以及项目管理员、审核员、设计师、只读用户四种项目角色；
- 建立功能权限码和项目级权限检查，项目角色不会跨项目泄漏；
- 实现一次性系统管理员初始化，数据库原子状态可防止多实例重复初始化；
- 使用 Redis 实现按“用户名和客户端地址”组合的登录失败限制；
- 实现用户创建、查询、启停和强制下线 API；
- 实现最小项目创建、成员查询、成员授权、角色修改和移除 API；
- 防止停用最后一名系统管理员及移除最后一名项目管理员；
- 记录管理员初始化、登录、退出、续期、用户管理、项目创建和成员变更审计日志；
- 对外用户响应不包含密码摘要，审计查询不返回 IP 和内部详情；
- 增加 OpenAPI 3.1 接口说明和自动化安全测试。

## 4. 架构与设计决定

1. 用户和项目角色规则位于对应领域模块，Spring、MyBatis-Plus、Sa-Token 和 Redis 适配位于 API 模块的应用层与基础设施层。
2. 全局 `ADMIN` 通过 Sa-Token 功能权限码管理用户和创建项目；普通用户的项目权限必须根据 URL 中的 `projectId` 查询 `project_member`，不能使用不带项目范围的全局角色判断。
3. Sa-Token 使用 `Authorization: Bearer <token>`，Token 和会话状态由 Redis 共享。`is-share=false` 允许用户拥有多个独立终端 Token，管理员踢出会使该用户全部 Token 立即失效。
4. 系统首次初始化不预置默认管理员密码。部署人员必须提供 `IDENTITY_BOOTSTRAP_TOKEN`，成功后数据库中的 `identity_bootstrap` 状态由 `pending` 原子更新为 `complete`。
5. P01 创建的 `mosaic_project` 只包含权限载体所需的最小字段；项目生命周期、业务配置和上传能力由 P02 扩展。

## 5. 代码与模块变化

| 模块 | 变化 | 说明 |
|---|---|---|
| `mosaic-identity` | 修改 | 用户、角色、状态、权限、用户名和密码策略 |
| `mosaic-project` | 修改 | 项目角色、项目权限和成员模型 |
| `mosaic-api` | 修改 | 认证、用户管理、项目成员授权、审计、Redis 限流、UUID 类型处理和 REST 接口 |
| `docs/api/openapi.yaml` | 新增 | P01 API 的 OpenAPI 3.1 契约 |
| `.env.example` | 修改 | 初始化凭据和登录限流配置示例 |

## 6. API变化

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| POST | `/api/v1/auth/bootstrap` | 初始化凭据，仅一次 | 创建首个系统管理员 |
| POST | `/api/v1/auth/login` | 匿名 | 登录并签发 Token |
| POST | `/api/v1/auth/logout` | 登录 | 当前 Token 退出 |
| POST | `/api/v1/auth/refresh` | 登录 | 续期 Token 与活跃时间 |
| GET | `/api/v1/auth/me` | 登录 | 查询当前用户 |
| POST | `/api/v1/admin/users` | `user:write` | 创建用户 |
| GET | `/api/v1/admin/users` | `user:read` | 查询用户 |
| PATCH | `/api/v1/admin/users/{userId}/status` | `user:write` | 启停用户 |
| POST | `/api/v1/admin/users/{userId}/kick` | `user:kick` | 强制全部会话下线 |
| POST | `/api/v1/projects` | `project:create` | 创建最小项目及项目管理员关系 |
| GET | `/api/v1/projects/{projectId}` | 项目查看权限 | 查询项目 |
| GET | `/api/v1/projects/{projectId}/members` | 项目查看权限 | 查询项目成员 |
| PUT | `/api/v1/projects/{projectId}/members/{userId}` | 项目成员管理权限 | 新增成员或修改角色 |
| DELETE | `/api/v1/projects/{projectId}/members/{userId}` | 项目成员管理权限 | 移除成员 |
| GET | `/api/v1/admin/audit-logs` | `audit:read` | 查询最近 200 条审计记录 |

完整请求、响应、枚举和错误结构见 `docs/api/openapi.yaml`。

新增主要错误码：`AUTHENTICATION_REQUIRED`、`PERMISSION_DENIED`、`LOGIN_FAILED`、`LOGIN_RATE_LIMITED`、`USER_DISABLED`、`USERNAME_EXISTS`、`PASSWORD_WEAK`、`BOOTSTRAP_COMPLETED`、`PROJECT_ACCESS_DENIED`、`LAST_GLOBAL_ADMIN`、`LAST_PROJECT_ADMIN`。

## 7. 数据库变化

Flyway 脚本 `V2__create_identity_and_access.sql` 新增：

- `app_user`：用户、BCrypt 密码摘要、全局角色和状态；
- `mosaic_project`：项目权限载体；
- `project_member`：项目成员及项目角色，使用项目和用户联合主键；
- `audit_log`：安全和管理操作审计记录；
- `identity_bootstrap` 平台元数据：控制一次性管理员初始化；
- 用户名大小写无关唯一索引、成员反向查询索引及审计查询索引；
- 全局角色、用户状态、项目状态和项目角色数据库检查约束。

MyBatis 增加 PostgreSQL UUID 类型处理器，所有主业务 ID 使用 UUID。

## 8. 消息与事件变化

无。认证和权限操作目前为同步事务，P03 建立可靠消息基础后再评估安全事件异步投递；安全关键状态变更仍将以数据库事务为准。

## 9. 配置与部署变化

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `IDENTITY_BOOTSTRAP_TOKEN` | `pics2text-bootstrap-dev` | 首次初始化凭据，仅允许本地默认值，生产必须替换 |
| `LOGIN_MAXIMUM_ATTEMPTS` | `5` | 限制窗口内最大登录失败次数 |
| `LOGIN_LOCK_DURATION` | `15m` | 登录限制持续时间 |

Sa-Token 配置：Token 名称为 `Authorization`，前缀为 `Bearer`，总有效期 30 天，无操作有效期 30 分钟，单用户最多 5 个登录会话。

## 10. 安全与权限影响

- 密码只保存 BCrypt 摘要，任何 API 响应和审计详情均不返回密码或摘要；
- 不存在用户和密码错误使用相同错误信息，并执行 BCrypt 校验以降低用户枚举风险；
- 登录失败计数键使用用户名、客户端地址组合的 SHA-256，不直接把用户名写入 Redis Key；
- 用户停用时立即踢出全部会话；退出和踢出后的 Token 在所有实例立即失效；
- 普通用户必须拥有目标项目成员关系，并由对应项目角色授权；
- 客户端地址用于安全审计与登录限制。只有在可信反向代理覆盖外部传入头时才应启用 `X-Forwarded-For` 转发；
- 当前本地初始化 Token 只为开发便利保留，生产发布检查必须禁止默认值。

## 11. 测试与验证结果

| 测试类型 | 命令或方法 | 结果 |
|---|---|---|
| 全量构建 | `./mvnw --batch-mode --no-transfer-progress verify` | 11 个模块成功 |
| 自动化测试 | 同上 | 20 项测试通过，0 失败 |
| 密码与用户名策略 | 领域单元测试 | 7 项通过 |
| 项目角色矩阵 | 领域单元测试 | 3 项通过 |
| 认证失败与初始化保护 | Mockito 单元测试 | 3 项通过 |
| 项目越权 | Mockito 单元测试 | 3 项通过 |
| 多实例会话 | 在 `18080` 和 `18083` 启动两个 API，用实例 1 Token 访问实例 2 | HTTP 200 |
| 踢出与退出 | 踢出普通用户、退出管理员后分别访问两个实例 | 均立即返回 HTTP 401 |
| 项目隔离 | 加入项目前访问、设计师修改成员 | 均返回 HTTP 403 |
| 登录限制 | 连续发送 6 次错误登录 | 前 5 次 HTTP 401，第 6 次 HTTP 429 |
| 一次性初始化 | 第二次调用初始化接口 | HTTP 409 |
| 数据迁移 | 查询 `flyway_schema_history` | V1、V2 均成功 |
| 审计 | 查询验收期间操作聚合 | 初始化、登录、续期、退出、踢出及成员变更均有记录 |

真实联调完成后已经删除验收生成的测试用户、项目、审计记录和 Redis 会话，本地数据库保留 V1、V2 结构，`identity_bootstrap` 恢复为 `pending`，可供开发者自行初始化。

## 12. 运行与使用说明

```bash
docker compose up -d --wait
make api
```

首次使用按照 README 的“首次管理员初始化”执行。登录后把响应中的 `tokenValue` 放入请求头：

```text
Authorization: Bearer <tokenValue>
```

生产环境必须通过密钥管理系统注入初始化凭据，并在初始化完成后撤销该凭据。

## 13. 已知限制与遗留事项

- 尚未实现刷新 Token 与访问 Token 分离；当前“续期”延长同一个 Sa-Token，会话模型满足后台系统基线，移动端或第三方开放平台需要另行设计；
- 登录限制当前按用户名与客户端地址组合，分布式攻击检测、验证码和设备风险评分在 P11 补充；
- 项目模型仅满足权限关联，项目列表、归档、配置和完整生命周期在 P02 实现；
- 审计查询当前固定返回最近 200 条，分页、保留周期和归档在 P11 实现；
- 可信代理名单和真实客户端 IP 获取策略需要部署环境在 P11 明确；
- Actuator 访问控制仍按 P00 基线，生产加固属于 P11。

## 14. 下一阶段注意事项

P02 所有项目、照片和上传接口必须调用项目级权限服务，不能只判断用户是否登录。上传管理通常要求 `PROJECT_ADMIN`，照片审核使用 `REVIEWER`，布局编辑使用 `DESIGNER`；具体动作应继续扩展 `ProjectPermission`，避免在控制器中散落角色名称判断。

禁止修改已经发布的 V1、V2 脚本；后续数据库变更从 V3 开始。

## 15. 验收清单

- [x] 计划功能已经实现
- [x] 自动化测试通过
- [x] 阶段验收项通过
- [x] Flyway 脚本完整并验证
- [x] OpenAPI 说明已更新
- [x] 配置与部署变化已记录
- [x] 安全影响已检查
- [x] 已知限制已记录
- [x] `docs/progress/README.md` 已更新
- [x] 阶段完成文档已自检
