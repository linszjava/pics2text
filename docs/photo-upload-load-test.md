# 照片上传容量保护与本地压测

`complete` 接口在每个 API 进程内使用无等待容量闸门，保护 multipart 合并和整对象流式验真：

| 环境变量 | 默认值 | 含义 |
|---|---:|---|
| `PHOTO_COMPLETION_GLOBAL_LIMIT` | `4` | 单个 API 进程允许同时执行的 complete 数量 |
| `PHOTO_COMPLETION_PER_USER_LIMIT` | `3` | 单用户在该进程内允许同时执行的 complete 数量 |
| `PHOTO_COMPLETION_RETRY_AFTER` | `1s` | 容量拒绝返回的 `Retry-After` |
| `PHOTO_VERIFICATION_LEASE` | `30m` | 异常中断的 VERIFYING 状态允许重新认领前的等待时间 |

容量已满时立即返回 `429 PHOTO_UPLOAD_CAPACITY_EXCEEDED`，不排入内存等待队列，也不会认领、失败化或删除照片。许可在成功和异常路径都会释放。限制是单 JVM 实例范围；部署多个 API 副本时总容量约为实例数乘以单实例上限。当前架构没有引入 Redis 分布式信号量，部署时应按每实例 CPU、对象存储吞吐和副本数设置上限。

Web 客户端仅对该错误按 `Retry-After` 有界退避，最多尝试 8 次，退避不超过 5 秒。容量重试耗尽时保留可恢复上传，不自动调用取消接口。

## 本地真实链路压测

脚本 [photo_upload_load_test.py](../scripts/photo_upload_load_test.py) 只允许 localhost，生成内容不同且可解码的 PNG，通过真实 API 获取预签名 URL、直传 SeaweedFS，并调用 complete。硬限制为 1000 张、24 客户端并发、30 分钟和 512 MiB 生成数据。

建议在专用本地环境关闭分析派发，只启动依赖和 API；这不会修改分析 Worker 的并发配置：

```bash
docker compose up -d postgres redis rabbitmq object-storage
JOB_ANALYSIS_DISPATCH_ENABLED=false \
PHOTO_COMPLETION_GLOBAL_LIMIT=2 \
PHOTO_COMPLETION_PER_USER_LIMIT=2 \
./mvnw -pl mosaic-api -am spring-boot:run
```

先运行冒烟，再运行 500 张；下面的两个用户会各自创建明确标记的测试项目，多客户端会同时竞争全局闸门：

```bash
export PICS2TEXT_LOAD_PASSWORD='仅本地测试密码'
python3 scripts/photo_upload_load_test.py \
  --username-prefix p2t-load-smoke \
  --users 2 --count 20 --concurrency 8 --multipart-count 2 \
  --timeout-seconds 600 --report build/loadtest/smoke.json

python3 scripts/photo_upload_load_test.py \
  --username-prefix p2t-load-500 \
  --users 2 --count 500 --concurrency 24 --multipart-count 2 \
  --timeout-seconds 1800 --report build/loadtest/500.json
```

报告分别记录初始化、对象上传、complete 和端到端耗时的 P50/P95/最大值，实际状态、429 重试次数、字节分布，以及传入 `--api-pid` 时 API 进程的 RSS 和 CPU 峰值。`--cleanup-photos` 只删除本轮脚本记录到的照片对象；项目、账户、审计与作业记录需要按报告中的精确 ID 在专用本地数据库中定向清理。不要对共享或生产数据库执行清理 SQL。

压测结果只能描述当次机器与配置。没有同机器、同数据、同并发的旧实现基线时，不得声称性能提升倍数。
