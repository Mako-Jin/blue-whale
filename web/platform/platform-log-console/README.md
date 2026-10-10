# platform-log-console — 日志控制台

把「**前端产生日志 → 批量上报 → 服务端落库 → 查询与链路追踪**」整条链路跑通并可视化的演示应用。

它不是产品功能，而是一个**可运行的验证工具**，同时是 `common-tools/http` 与 `common-api` 的活体示例。

---

## 一、为什么左栏和右栏要并排

左栏是**客户端视角**（我产生了什么、上报了多少），右栏是**服务端视角**（后端实际收到了什么）。

只看其中一边永远无法判断"上报到底成没成"：左栏有、右栏没有，是上报通道断了；右栏有、左栏没有，是查询条件不对。两者对照，一眼见分晓。

---

## 二、跑起来

```bash
# 1. 后端（默认 8080）。默认 profile 是 h2 内存库，无需装数据库
cd server && mvn -DskipTests install
java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar

#    想让数据留在磁盘上（演示「重启后数据还在」），改用 mysql profile
BW_DB_PASSWORD=<密码> java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=mysql

# 2. 前端
cd web && pnpm install
pnpm --filter platform-log-console dev     # http://127.0.0.1:3200
```

开发期 `/api` 由 vite 代理到 `127.0.0.1:8080`，请求与服务端同源 —— 排查日志功能时被跨域问题挡住是最不划算的事。

> 演示页本身不感知数据源：`h2` 与 `mysql` 两种 profile 下行为一致，差别只在**重启后数据还在不在**（h2 会清空）。这正好是演示两种数据源差异的好素材。数据源说明见 `server/README.md` 第六节。

---

## 三、能演示什么

| 操作                      | 验证了什么                                                             |
| ------------------------- | ---------------------------------------------------------------------- |
| 点级别按钮（TRACE…ERROR） | 日志走真实的 `common-tools` logger：级别过滤、命名空间层级都生效       |
| 随机 10 / 60 条           | 批量上报：攒够 20 条或 3 秒即发；缓冲上限 500，超出丢最旧              |
| 抛异常（带堆栈）          | `Error` 对象在记录里完整保留（`name` / `message` / `stack`）           |
| 模拟成功 / 失败链路       | 同一个 `traceId` 串起多条日志；点表格里的链路 id 可回看整条链路        |
| 关掉自动上报              | 日志只留在本地缓冲，`待上报` 计数上涨而 `已落库` 不动                  |
| 右栏点级别柱子 / 筛选框   | 服务端查询：级别、命名空间（精确）、关键词（模糊）、分页               |
| 点时间查看详情            | 完整字段、`data` / `context` 的 JSON、整条链路                         |
| 后端停掉再刷新            | 顶部横幅给出生动指引；上报失败自动指数退避（最多 30s），恢复后自动清零 |

左栏底部五个计数（待上报 / 已落库 / 被拒 / 失败批次 / 丢弃）就是上报通道的完整状态 —— 上报是"尽力而为"的通道，它的健康状况必须可见，否则出问题时无从判断。

---

## 四、目录结构

```
src/
├── main.tsx / App.tsx        入口与主容器（订阅、轮询、查询、交互）
├── console.ts                单例装配：api + reporter + logSource，及 sanitize
├── reporter.ts               LogReporter：批量、有界、指数退避、不抛异常
├── log-source.ts             LogSource：按级别产生真实日志、模拟链路
├── levels.ts                 级别元数据（新增级别时这里会编译失败，防止漏显示）
├── format.ts                 时间与 JSON 格式化
├── styles/                   设计令牌（variables.less 由 vite 注入）与全局样式
├── App.less                  页面样式（按组件块组织）
└── components/               GeneratorPanel · ServerLogPanel · StatsBar · LogDetailDrawer · LevelTag
tests/                        Vitest（25 例，含渲染测试）
```

---

## 五、设计取舍

- **上报通道有四条硬约束**：批量、有界、指数退避、不抛异常不自我记录。全部来自真实故障 —— 违反任何一条，一次后端故障就会演变成客户端与服务端之间的雪崩。见 `src/reporter.ts` 的类注释与 `docs/decisions/0004`。
- **上报必须走 `onRecord` 而不是 `LogSink`**：sink 拿到的是渲染后的字符串（级别信息被压扁、元数据被拼进去），只能反向解析；`onRecord` 拿到的是结构化事实。
- **脏数据在 `sanitize()` 一次性洗干净**：日志里夹带函数、循环引用、DOM 节点是常事，`JSON.stringify` 遇到会抛错，结果是整批 500 条一起失败。递归转安全值，让脏数据只影响它自己。
- **`perf` 级别单独分派**：它的签名是 `(label, value, unit)`，与其余级别的 `(message, ...data)` 不是一回事，按级别名统一调用会把数值写进消息位置。
- **客户端时钟不可信**：`timestamp` 仅作展示，排序与统计一律用服务端接收时间。
