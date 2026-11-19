# 国际化整改落地（P0/P1）

> 依据 [2026-11-19-i18n-audit.md](../reviews/2026-11-19-i18n-audit.md) 的差距分级执行。
> 本轮完成 P0 + P1；P2/P3 保留为分阶段待办，机制已就绪，不再改公共代码。

## 一、需求项

| 编号 | 级别 | 需求 | 验收口径 |
| --- | --- | --- | --- |
| R-01 | P0 | 打通错误响应 i18n 链路：`ErrorCode` 增加 `messageKey`，`GlobalExceptionHandler` 注入 `MessageSource` 并按请求 Locale 解析，未命中回落中文常量 | `Accept-Language: en-US` 时 401/403/404/405/参数错误/业务异常返回英文；缺省中文 |
| R-02 | P0 | 语言上下文透传：网关解析 `Accept-Language` 并写入 `X-Lang`，服务端过滤器写入 `LocaleContextHolder`，请求结束清理；异步线程显式传递 | 带 `X-Lang: en-US` 或 `Accept-Language: en-US` 的请求命中英文；异步落库线程语言一致 |
| R-03 | P1 | 公共错误码资源包：`i18n/common/messages{,_zh_CN,_en_US}.properties` 随 starter 分发，覆盖通用错误码 | 未接入 i18n SDK 的服务同样能返回英文公共错误 |
| R-04 | P1 | 管理端前端 i18n 框架：轻量字典 + Provider，语言持久化，antd / dayjs 语言同步，登录页与导航/通用文案接入 | 切换语言后登录页、导航、系统提示即时生效；默认中文 |
| R-05 | P1 | 前端请求携带语言：统一 HTTP 客户端注入 `X-Lang` / `Accept-Language` | 浏览器切英文后，后端错误消息为英文 |

## 二、完成项

- [x] R-01 `ErrorCode` 增加 `messageKey`；`GlobalExceptionHandler` 走 `MessageSource` → 公共资源包 → 中文兜底三级解析
- [x] R-01 公共错误码资源包（starter `i18n/common/messages*`），新增 `CommonMessageResolver`
- [x] R-02 网关 `LocaleGlobalFilter` 归一化并注入 `X-Lang`（默认 zh-CN）
- [x] R-02 服务端 `LocaleContextFilter` 写入 `LocaleContextHolder`，`HIGHEST_PRECEDENCE + 5` 早于身份上下文
- [x] R-02 异步传递：`LocaleContextTaskDecorator` + `CompositeTaskDecorator` 接入审计/日志线程池
- [x] R-04 admin 轻量 i18n（`src/i18n`）：字典、`I18nProvider`、`useI18n`、`LanguageSwitcher`、antd/dayjs 同步、`localStorage` 持久化
- [x] R-04 登录页、主框架导航/头部/提示文案迁移；动态菜单文案仍由后端下发
- [x] R-05 `@bluewhale/http` 支持 `getLocale`，admin 注入当前语言头
- [x] 单测：`CommonMessageResolverTest`、`LocaleContextFilterTest`、`LocaleGlobalFilterTest`、`GlobalExceptionHandlerTest` 英文化断言、admin `translate.test.ts`
- [x] 更新审计文档进度、开发日志；构建 + lint + 单测通过后提交推送

### P2 第一批（服务级资源包 + 关键页面）

- [x] `BizException` 支持 `(ErrorCode, messageKey, 中文兜底, args...)` 构造，`GlobalExceptionHandler` 透传占位参数
- [x] 7 个核心服务接入 `whale-i18n-sdk` 并启用 `whale.i18n`（enabled/auto-init/module-scan）
  - message：12 键 / 12 处改写
  - workflow：41 键 / 55 处改写
  - form：11 键 / 12 处改写
  - job：7 键 / 7 处改写
  - im：35 键 / 44 处改写
  - mail：35 键 / 37 处改写
  - pay：7 键 / 7 处改写
  - 合计 148 键、174 处业务异常迁移；中文兜底逐字保留，`getMessage()` 行为不变
- [x] 每个服务三份资源文件（`messages` / `messages_zh_CN` / `messages_en_US`）键集完全对齐
- [x] admin 前端第二批：仪表盘（Home）文案迁移；登录页、导航、头部已于 P1 完成

## 三、待做项（P2 剩余 / P3）

| 级别 | 事项 | 说明 |
| --- | --- | --- |
| P2 | 拼接型业务消息 | 7 个核心服务中约 83 处 `"中文" + 变量` 未迁移（保持原文，避免破坏默认文案）；需改为 `{0}` 占位参数 |
| P2 | 其余后端服务 | agent / archive / assistant / doc / forum / iot / map / music / scene / twin / video 仍未接入 SDK（auth、file 已接入） |
| P2 | DTO 校验文案迁移 | 现有 `@NotBlank(message="中文")` 仍为字面量；后续统一改为 `{validation.xxx}` 由 MessageSource 插值（机制已支持） |
| P2 | 前端其余页面 | admin 剩余 50+ 页面、portal 与子应用按模块分批替换硬编码文案 |
| P3 | 网关统一错误响应 i18n | `GatewayResponseWriter` 的 401/403/429 文案本地化 |
| P3 | 运维服务 | audit / monitor / alert 视对外暴露程度再定 |

## 四、已修正问题项

| 问题 | 处理 |
| --- | --- |
| `GlobalExceptionHandler` 直接 `Result.fail(code, 中文常量)`，资源包无消费方 | 改为按 Locale 解析，保留中文兜底 |
| 服务端无 `LocaleContextHolder` 解析，`Accept-Language` 被忽略 | 新增过滤器，网关统一注入 `X-Lang` |
| 未接入 i18n SDK 的服务拿不到公共错误文案 | 公共资源包随 starter 分发，`CommonMessageResolver` 独立解析 |
| 前端零 i18n、antd 内置文案与页面语言不一致 | 引入轻量字典 + `ConfigProvider locale` + dayjs locale 同步 |
| 业务异常无法携带国际化键（仅通用错误码可翻译） | 扩展 `BizException` 键 + 兜底 + 占位参数构造器，handler 统一定位解析 |

## 五、验证结果

- `mvn -o -pl whale-common/whale-common-core,whale-common/whale-spring-boot-starter -am test`：BUILD SUCCESS；
  新增/更新单测 10 例（`LocaleSupportTest` 3、`CommonMessageResolverTest` 3、`LocaleContextFilterTest` 3、
  `GlobalExceptionHandlerTest` +1）。
- `mvn -o -pl whale-services/whale-service-gateway -am test`：BUILD SUCCESS，网关 211 例全绿
  （新增 `LocaleGlobalFilterTest` 3 例）。
- `mvn ... checkstyle:check`：4 个改动模块 0 违规。
- P2：message / workflow / form / job / im / mail / pay 七个服务 `mvn test` 与 `checkstyle:check` 全部成功；
  全后端 `mvn -o -DskipTests compile -T 1C` BUILD SUCCESS。
- `pnpm --filter @bluewhale/http build`、`pnpm --filter whale-admin lint`、
  `pnpm --filter whale-admin test`（12 文件 / 56 例全绿）、`pnpm --filter whale-admin build`：全部通过。
