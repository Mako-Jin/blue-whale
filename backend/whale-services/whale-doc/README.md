# whale-doc · 在线文档 / Wiki 服务

Wave 5.74 M1 服务骨架。文档空间、空间成员、页面树、版本历史、附件、评论、搜索与收藏；
页面正文以 TipTap / ProseMirror JSON 存储，服务端提取纯文本投影用于搜索。

## 运行参数

| 项 | 值 |
|---|---|
| 服务名 | `whale-doc` |
| 端口 | `8095`（`SERVER_PORT`） |
| 数据库 | `bluewhale_doc`（`MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` / `MYSQL_PASSWORD`） |
| 注册中心 | Nacos（`NACOS_SERVER` / `NACOS_NAMESPACE`） |
| 网关路由 | `Path=/doc/**` → `lb://whale-doc`，服务自持 `/doc` 一级前缀，不 StripPrefix |

## 数据库

首次部署执行：

```bash
mysql -u<user> -p bluewhale_doc < src/main/resources/db/init.sql
```

脚本幂等（`CREATE TABLE IF NOT EXISTS`），含 7 张 M1 业务表与 `audit_log` / `api_log`。

鉴权侧权限、角色与菜单使用 `bluewhale_auth` 增量迁移
`whale-service-auth-starter/src/main/resources/db/migration-5.74-doc.sql`。

## 关键配置

| 环境变量 | 默认 | 说明 |
|---|---|---|
| `WHALE_DOC_CONTENT_MAX_BYTES` | `2097152` | 单页正文 JSON 上限（2MB） |
| `WHALE_DOC_CONTENT_TEXT_MAX_BYTES` | `1048576` | 纯文本投影上限（1MB） |
| `WHALE_DOC_ATTACHMENT_MAX_BYTES` | `52428800` | 单附件上限（50MB） |
| `WHALE_DOC_MAX_DEPTH` | `10` | 页面树最大层级 |
| `WHALE_DOC_COLLAB_ENABLED` | `false` | 实时协作开关（M2） |
| `WHALE_DOC_APPROVAL_ENABLED` | `false` | 发布审批开关（M3） |

## 接口约定

- 平台权限码 `sys:doc:*` 由 starter 的 `RequirePermissionInterceptor` 校验；
- 数据可见性由空间成员角色（`OWNER > ADMIN > EDITOR > READER`）二次校验；
- `/doc/internal/**` 仅供服务间内网直连，网关 `internal-deny` 统一 403；
- 统一响应 `Result<T>`，业务错误码见 `com.bluewhale.doc.domain.exception.DocErrorCode`。

M1 首个接口：

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/doc/meta` | `sys:doc:space:list` | 服务运行参数与功能开关探测 |

## 本地构建

```bash
mvn -pl whale-services/whale-doc -am -DskipTests package
```

## 设计基线

- `docs/design/doc-service.md`
- `docs/plans/2026-10-29-wiki-doc-service.md`
