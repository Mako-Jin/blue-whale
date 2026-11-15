# whale-file 能力扩展（批次 4：分享链接）

## 一、范围与决策

批次 1~3 已完成存储类型路由、回收站生命周期、重命名/移动、指标与健康检查、孤儿对象对账。
本批次落地路线图阶段 2 首项：文件分享链接。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 创建/撤销分享 | ✅ | 过期时间、下载次数上限、可选密码 |
| 分享列表 | ✅ | 创建人看自己，`sys:file:manage` 看全租户 |
| 凭链接查看元数据 | ✅ | 返回文件名、大小、类型、是否需密码 |
| 凭链接下载 | ✅ | 支持 Range，复用 `FileContentResponses` |
| 密码校验 | ✅ | PBKDF2-HMAC-SHA256，常量时间比较 |
| 下载次数限制 | ✅ | 原子占用，防并发绕过 |
| 匿名访问（无登录态） | ❌ | 需网关白名单与 auth 迁移，单独排期 |
| 分享二维码/短链 | ❌ | 前端基于 token 生成，后端不引入短链服务 |

**安全原则**：令牌服务端随机生成并持久化，不可枚举；密码不明文/不可逆；过期、撤销、
超次数、文件删除或禁用后链接立即失效；访问异常统一按「不存在」响应，避免泄露分享状态。

## 二、设计

### 2.1 表结构（`V5__file_share.sql`）

```sql
CREATE TABLE IF NOT EXISTS file_share (
    id             BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    file_id        BIGINT       NOT NULL               COMMENT '被分享的文件ID（逻辑关联 file_info.id）',
    file_name      VARCHAR(255) NOT NULL               COMMENT '创建时的原始文件名快照',
    token          VARCHAR(64)  NOT NULL               COMMENT '分享令牌（32 字节随机数的 base64url）',
    password_hash  VARCHAR(255) DEFAULT NULL           COMMENT '分享密码哈希；空表示无密码',
    expire_time    DATETIME     DEFAULT NULL           COMMENT '失效时间；空表示长期有效',
    max_downloads  INT          DEFAULT NULL           COMMENT '下载次数上限；空表示不限',
    download_count INT          NOT NULL DEFAULT 0     COMMENT '已下载次数',
    status         TINYINT      NOT NULL DEFAULT 1     COMMENT '状态：1 有效 / 0 已撤销',
    creator_id     BIGINT       DEFAULT NULL           COMMENT '创建人用户ID',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_share_token (token),
    KEY idx_file_share_file (file_id),
    KEY idx_file_share_creator (creator_id, create_time)
) COMMENT='文件分享链接';
```

`tenant_id` 由租户拦截器自动追加与写入，PO 不重复声明；跨租户令牌查不到记录。
`init.sql` 只承载手动初始化基线（file_info + api_log），增量表统一由 Flyway 迁移创建，
基线一致性用例保持「V1 = init.sql」的口径不变。

### 2.2 令牌与密码

- **令牌**：`SecureRandom` 生成 32 字节，Base64 URL 无填充（43 字符），写入唯一索引。
  不采用无状态 HMAC：撤销、下载计数与密码校验必须有服务端状态；随机令牌不可预测，
  且无需在令牌中携带文件信息，泄露面更小。
- **密码**：可选，4~64 字符，PBKDF2WithHmacSHA256 / 120000 次迭代 / 16 字节盐 / 32 字节密钥，
  存储格式 `pbkdf2$120000$<base64盐>$<base64哈希>`；校验使用 `MessageDigest.isEqual` 常量时间比较。
- 密码通过请求头 `X-Share-Password` 传递，避免出现在 URL、访问日志与 Referer 中。

### 2.3 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/file/v1/share` | `sys:file:upload` | 创建分享，返回 token 与失效时间 |
| GET | `/file/v1/share/page` | `sys:file:list` | 分享列表，默认仅创建人，管理权限看全租户 |
| DELETE | `/file/v1/share/{id}` | `sys:file:upload` | 撤销分享，幂等 |
| GET | `/file/v1/share/{token}` | 无注解（需登录） | 链接落地页元数据；需密码时只返回 `needPassword=true` |
| GET | `/file/v1/share/{token}/download` | 无注解（需登录） | 凭链接下载，支持 `Range` |

不新增权限码：创建/撤销复用 `sys:file:upload`、列表复用 `sys:file:list`，与批次 1~3
「避免跨库权限种子与网关改动」的决策一致。访问类接口不标注权限注解——任意登录用户凭令牌即可访问，
但网关未白名单前仍需登录态，匿名开放需运维后续配置。

### 2.4 访问与下载语义

- **创建**：仅文件可见者（上传人本人或管理权限）可创建；禁用状态文件拒绝创建；
  `expireHours` 缺省取配置默认值（168 小时），上限截断（720 小时）；
  `maxDownloads` 为空或 `<=0` 表示不限；密码为空表示无密码。
- **访问**：校验状态、过期、文件存在且未禁用；需密码且未携带时只返回 `needPassword=true`，
  不返回文件名/大小；密码错误返回 `FORBIDDEN`；其余异常统一 `NOT_FOUND`。
- **下载**：每次内容请求（含 Range 分段）先原子占用一次下载次数：
  `UPDATE file_share SET download_count = download_count + 1 WHERE id = ? AND status = 1
  AND (expire_time IS NULL OR expire_time > NOW())
  AND (max_downloads IS NULL OR download_count < max_downloads)`；
  影响行数为 0 即视为失效（并发下也不会超发）。视频等分片播放场景建议不设次数上限。
- **Range**：`FileContentResponses` 增加 `ContentSource` 重载，分享下载复用同一套
  单段 Range、ETag、`Content-Disposition` 与 `nosniff` 逻辑，不重复实现。

### 2.5 指标

- `file.share.create{result}`：创建分享成功/失败。
- `file.share.download{result}`：凭链接打开内容成功/失败（含次数耗尽）。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 领域 | 创建参数校验、过期判定、次数耗尽、撤销幂等、密码哈希往返与错误密码 |
| 应用 | 默认有效期与上限截断、密码落库为哈希、禁用文件拒绝分享、访问需密码、下载次数耗尽 |
| 仓储 | 原子占用成功/失败、撤销幂等、列表按创建人过滤 |
| 接口 | 创建/列表/撤销映射、需密码时元数据脱敏、下载密码头传递与 Range 响应 |

## 四、完成情况

- 领域：`FileShareAggregate`（创建、过期/次数/撤销判定、密码校验、管理权限校验）、
  `ShareToken`（32 字节 SecureRandom + Base64URL）、`SharePassword`（PBKDF2-HMAC-SHA256）、
  `ShareStatus`、列表投影与仓储端口。
- 持久化：`V5__file_share.sql`（幂等建表 + 令牌唯一索引）、`FileSharePo`/`FileShareMapper`/
  `FileShareRepositoryImpl`；撤销与下载次数使用条件更新，`consumeDownload` 在单条 SQL 内校验
  状态、有效期与次数上限。
- 应用：`FileShareService` 创建（默认/上限有效期、密码哈希、可见性与禁用校验）、分页
  （创建人 / 管理视角）、撤销（幂等）、访问（需密码时元数据脱敏）、内容读取（原子占用次数、
  按存储类型路由、支持 Range）。
- 接口：`POST /file/v1/share`、`GET /file/v1/share/page`、`DELETE /file/v1/share/{id}`、
  `GET /file/v1/share/{token}`、`GET /file/v1/share/{token}/download`；密码走
  `X-Share-Password` 请求头；`FileContentResponses` 新增 `ContentSource` 重载复用 Range/ETag/
  `Content-Disposition` 逻辑。
- 配置与指标：`whale.file.share.default-expire-hours` / `max-expire-hours`（含环境变量），
  `file.share.create{result}` 与 `file.share.download{result}` 计数器。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
235 项单测零失败（批次 3 为 208，本轮新增 27 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- `init.sql` 增加 `file_share` 导致「V1 基线 = init.sql」用例失败：init.sql 只承载手动初始化基线，
  增量表统一由 Flyway 创建（`audit_log` 同此口径），已回退 init.sql 并保持用例原语义。
- `FileShareAggregate` 构造时误用不存在的 `ShareStatus#value()`，改为直接赋值状态枚举。
- 撤销用例的两个分享共用同一主键，`never()` 断言被前一次调用污染；测试工厂方法补充分享ID。
- 次数耗尽用例数据在 `assertAccessible` 阶段即失败，未覆盖「并发下原子占用失败」分支；
  改为未达上限但 `consumeDownload` 返回 `false`，精确覆盖该分支。

## 七、待做项

- 匿名访问：网关白名单 + auth 侧白名单种子，需运维与安全评估。
- 分享访问审计（来源 IP、User-Agent）与异常频率限流。
- 分享密码错误次数限制（防暴力破解）。
- 批次 1 路线图其余项：批量删除/打包下载、病毒扫描、租户/桶配额、文件版本、存储迁移、CDN、领域事件、多段 Range、加密、策略热更新。
