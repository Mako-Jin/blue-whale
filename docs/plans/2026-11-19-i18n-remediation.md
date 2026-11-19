# 国际化整改落地（P0–P2 后端已收口）

> 依据 [2026-11-19-i18n-audit.md](../reviews/2026-11-19-i18n-audit.md) 的差距分级执行。
> P0 + P1 + P3 网关与 P2 全部业务服务已完成；仅剩 DTO 校验文案、前端其余页面与 P3 运维服务。
> 除 P2 第四批为支持自定义 int 错误码给 `BizException` 补了一个重载外，未再改公共代码。

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

### P2 第二批（拼接型消息）

- [x] 七个核心服务 82 处 `"中文" + 变量` 改为 `(ErrorCode, messageKey, 原中文表达式, args...)`：
  资源包用 `{0}` / `{1}` 占位，原表达式继续作为 `getMessage()` 与中文兜底
- [x] 新增 74 个键：message 21、workflow 9、form 5、job 12、im 1、mail 21、pay 5
- [x] 资源文件键数更新为 message 33 / workflow 50 / form 16 / job 19 / im 36 / mail 56 / pay 12，
  三份文件键集逐服务比对一致
- [x] 校验脚本确认所有键化异常引用的键均在对应服务资源包中存在

### P3（网关统一错误响应）

- [x] 新增 `GatewayMessageResolver`：网关自带 `i18n/gateway/messages{,_zh_CN,_en_US}.properties`
      （12 键，三份键集一致），语言口径复用 `LocaleSupport`（`X-Lang` → `Accept-Language` → 默认中文）
- [x] 12 处短路文案本地化：JWT 401 / 路径封禁 403 / 强制改密 403、WS ticket 401、
      actuator 内网限制 403、治理端点鉴权 403、内网接口屏蔽 403、限流 429、熔断 503，
      以及异常兜底的路由 404 / 下游 404 / 502-504 / 500
- [x] 资源未命中时回落调用方中文原文，默认展示与既有断言零变化
- [x] 新增 `GatewayMessageResolverTest`（5 例）与 `GatewayExceptionHandlerTest` 英文断言 1 例

### P2 第三批（scene / music / video / agent / assistant / iot）

- [x] 六个服务接入 `whale-i18n-sdk` 并开启 `whale.i18n`（enabled/auto-init/module-scan）
- [x] 键数 scene 2 / music 6 / video 8 / agent 21 / assistant 15 / iot 18，合计 62 键；
      业务异常按 `(ErrorCode, messageKey, 原中文, args...)` 键化，三份资源键集一致
- [x] 各服务 `mvn test` + `checkstyle:check` 通过

### P2 第四批（archive / doc / forum / map / twin）

- [x] 五个服务接入 SDK，键数 archive 32 / doc 65 / forum 77 / map 35 / twin 39，合计 248 键
- [x] 直接构造的业务异常约 250 处键化；doc 另有 77 处 `DocErrorCode.exception(int)` 调用，
      通过「错误码 → 文案键」映射在 `exception(int)` 内统一携带键，调用方零改动
- [x] `BizException` 新增公开重载 `(int code, messageKey, 中文兜底, args...)`：
      forum 30xxx、map 围栏码等自定义 int 错误码也能键化，**码值与前端契约不变**；
      `new BizException(String)` 统一改为 `ErrorCode.SYSTEM_ERROR`（状态码仍 500）
- [x] 三份资源文件键集逐服务比对一致，代码引用键零缺失（配置键、定时任务 code 属预期误报）

### P2 第五批（DTO 校验文案试点：forum）

- [x] 核实 Spring Boot 4 `ValidationAutoConfiguration` 默认用 `MessageSourceMessageInterpolator`
      包装容器 `messageSource`，插值按 `LocaleContextHolder` 解析，无需自定义 Validator Bean
- [x] forum 9 个 DTO、43 处校验注解改为 `{validation.forum.<领域>.<字段>-<约束>}`，
      新增 41 键（区间约束复用同一键）；三份资源包键集一致（118 键），引用键零缺失
- [x] 新增 `ValidationMessageI18nTest` 2 例，覆盖 zh-CN / en-US 解析
- [x] 中文文案逐字保留；`mvn test` + `checkstyle:check` 通过

### P2 第六批（DTO 校验文案：im / pay / agent / scene / assistant）

- [x] 五个服务共 66 处校验文案键化：im 12、pay 16、agent 1、scene 19、assistant 18
- [x] 各服务三份资源包追加 `validation.<module>.*` 键，键集一致、代码引用零缺失
- [x] 五个服务 `mvn test` + `checkstyle:check` 全部通过

### P2 第七批（DTO 校验文案：music / mail / twin）

- [x] 三个服务共 98 处校验文案键化：music 33、mail 32、twin 33
- [x] 三份资源包键集一致（music 39、mail 88、twin 72），引用键零缺失；重复文案复用同一键
- [x] 三个服务 `mvn test` + `checkstyle:check` 全部通过

### P2 第八批（DTO 校验文案：iot / video）

- [x] 两个服务 73 处校验文案键化：iot 38 处 / 36 键、video 35 处 / 34 键
- [x] 三份资源包键集一致（iot 54、video 42），引用键零缺失
- [x] 两个服务 `mvn test` + `checkstyle:check` 全部通过

### P2 第九批（DTO 校验文案：archive / doc / map）

- [x] 三个服务共 151 处校验文案键化：archive 47 处 / 40 键、doc 51 处 / 47 键、map 53 处 / 49 键
- [x] 三份资源包键集一致（archive 72、doc 112、map 84）；中文原文逐字命中、引用键零缺失
- [x] 三个服务 `mvn test` + `checkstyle:check` 全部通过

## 三、待做项（P2 剩余 / P3）

| 级别 | 事项 | 说明 |
| --- | --- | --- |
| P2 | DTO 校验文案迁移 | Boot 4 已自动装配 `MessageSourceMessageInterpolator`；已完成 forum、im、pay、agent、scene、assistant、music、mail、twin、iot、video、archive、doc、map，其余模块按同样式铺开 |
| P2 | 前端其余页面 | admin 剩余 50+ 页面、portal 与子应用按模块分批替换硬编码文案 |
| P3 | 运维服务 | audit / monitor / alert 视对外暴露程度再定 |

## 四、已修正问题项

| 问题 | 处理 |
| --- | --- |
| `GlobalExceptionHandler` 直接 `Result.fail(code, 中文常量)`，资源包无消费方 | 改为按 Locale 解析，保留中文兜底 |
| 服务端无 `LocaleContextHolder` 解析，`Accept-Language` 被忽略 | 新增过滤器，网关统一注入 `X-Lang` |
| 未接入 i18n SDK 的服务拿不到公共错误文案 | 公共资源包随 starter 分发，`CommonMessageResolver` 独立解析 |
| 前端零 i18n、antd 内置文案与页面语言不一致 | 引入轻量字典 + `ConfigProvider locale` + dayjs locale 同步 |
| 业务异常无法携带国际化键（仅通用错误码可翻译） | 扩展 `BizException` 键 + 兜底 + 占位参数构造器，handler 统一定位解析 |
| 网关 401/403/429/503 等短路响应文案硬编码中文，英文请求仍返回中文 | 新增网关自有资源包与 `GatewayMessageResolver`，12 处短路文案按请求语言解析 |

## 五、验证结果

- `mvn -o -pl whale-common/whale-common-core,whale-common/whale-spring-boot-starter -am test`：BUILD SUCCESS；
  新增/更新单测 10 例（`LocaleSupportTest` 3、`CommonMessageResolverTest` 3、`LocaleContextFilterTest` 3、
  `GlobalExceptionHandlerTest` +1）。
- `mvn -o -pl whale-services/whale-service-gateway -am test`：BUILD SUCCESS，网关 211 例全绿
  （新增 `LocaleGlobalFilterTest` 3 例）。
- `mvn ... checkstyle:check`：4 个改动模块 0 违规。
- P2：message / workflow / form / job / im / mail / pay 七个服务 `mvn test` 与 `checkstyle:check` 全部成功；
  全后端 `mvn -o -DskipTests compile -T 1C` BUILD SUCCESS。
- P2 第二批：上述七个服务拼接型消息键化后 `mvn test` 与 `checkstyle:check` 再次全部成功；
  资源包键集与代码引用键的比对脚本零缺失。
- P3：`mvn -o -pl whale-services/whale-service-gateway test` BUILD SUCCESS（新增 6 例），
  `checkstyle:check` 0 违规；三份网关资源文件键集一致。
- P2 第三、四批：scene / music / video / agent / assistant / iot 与
  archive / doc / forum / map / twin 共 11 个服务 `mvn test` + `checkstyle:check` 全部成功；
  三份资源键集比对 0 差异，代码引用键零缺失；`whale-common-core` 新增构造器单测 1 例，
  checkstyle 0 违规（原全量构造器与 `(int, String, String, Object...)` 同为 `Object[]` 擦除，
  已合并为单一全量构造器，`getMessage()` 行为不变）。
- 全后端 `mvn -o -DskipTests compile -T 1C` BUILD SUCCESS。
- `pnpm --filter @bluewhale/http build`、`pnpm --filter whale-admin lint`、
  `pnpm --filter whale-admin test`（12 文件 / 56 例全绿）、`pnpm --filter whale-admin build`：全部通过。
