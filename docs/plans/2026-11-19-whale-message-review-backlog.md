# whale-message DTO 契约评审整改清单（第三批）

> 来源：外部对 whale-message DTO 层与服务契约的增量评审（D1–D18）。
> 本文是该批评审的**唯一执行清单**：每项包含结论、位置、修复方案、验收标准与状态。
> 前两批（35 条 + 增量 40 条）已完成项见 `docs/devlog/2026-11-19.md`，本文不再重复。

## 一、总体结论

| 分类 | 条目 | 数量 |
| --- | --- | --- |
| 确认缺陷，必须修 | D1、D2、D3、D4、D7/D16、D8、D11、D12、D13 | 9 |
| 契约/文档不一致，需定稿 | D5、D6/D15、D10、D18 | 4 |
| 已满足或误判 | D9、D14、D17 | 3 |
| 评审判断需修正 | D11（示例不成立，但问题成立） | 1 |

**最高优先级**：D4（回执键截断导致幂等误判）、D7/D16（偏好批量无上限）、D11（LIKE 注入/误命中）、D3（空消息落库）。

## 二、P0（数据正确性 / 安全，优先修）

### D4 回执派生 key 溢出截断 → 幂等误判

- **位置**：`MessageReceiptServiceImpl` L73 `RECEIPT_KEY_MAX_LENGTH = 160`、L146 `truncate(...)`、L186-192 `buildReceiptKey`；`init.sql` L112 `receipt_key VARCHAR(160)`。
- **结论**：**成立**。显式 `receiptId` 路径最长 16+1+96=113，安全；派生路径最长 16+1+128+1+16+1+29≈192 > 160，被 `truncate` 截断，不同回执可能得到同一 key → 后到回执被当成重复直接丢弃。
- **修复方案（推荐 A）**：
  - A. 派生键改为定长摘要：`channel + ":" + status + ":" + sha256Hex(providerMessageId + "|" + receiptTime)`，长度 ≤ 16+1+16+1+64 = 98，天然不超列宽；哈希输入保留原文语义，碰撞概率可忽略。
  - B. 列宽扩至 256 并把常量同步提高到 256（改动 DDL + Flyway，收益低，不推荐）。
  - 无论 A/B，`truncate` 应加断言/日志：截断发生时 `log.error` 报警，避免再次静默。
- **验收**：
  - 单测覆盖「providerMessageId 128 位 + 无 receiptId」派生键长度 ≤160 且两次生成稳定；
  - 两个不同 receiptTime 的派生键不相等；重复上报同一条回执仍命中幂等。

### D7 / D16 偏好解析 userIds 无上限 → auth 侧 OOM

- **位置**：`PreferenceResolveRequest` L28-29（仅 `@NotEmpty`）；`MessagePreferenceProperties` L33 `maxUsers = 200`；`NotifyPreferenceResolver.resolve`（去重后未校验 size）。
- **结论**：**成立**。注释写「去重后 ≤ 200」，实现只去重不校验。
- **修复方案**：
  - DTO：`@Size(max = 200) List<Long> userIds`（静态兜底）；
  - Service：`resolve` 入口 `if (deduped.size() > properties.getMaxUsers()) throw new BizException(PARAM_ERROR, ...)`，以配置为准（配置调大时仍生效）；
  - 两处上限含义写清楚：DTO 防滥用，Service 防配置漂移。
- **验收**：单测覆盖 201 个 userIds → 400；200 个 → 正常；配置改 50 时 60 个 → 400。

### D11 消息记录 channel 查询的 LIKE 风险

- **位置**：`MessageRecordQueryRequest` L27-28；`MessageRecordServiceImpl` L45-46 `wrapper.like(..., MessageRecord::getChannels, ...)`。
- **结论**：**问题成立，但评审给的例子不成立**——`"EMAIL"` 不包含子串 `"IM"`（E-M-A-I-L），不会误命中。
  真正风险有两类：
  1. 用户输入直接拼进 LIKE：`%` / `_` 被当通配符（`channel=%` 返回全部，`_` 造成宽匹配）；
  2. 前缀通配 `%value%` 无法走索引，channels 字段存的是逗号串，数据量大后全表扫描。
- **修复方案**：
  - `channel` 先 `MessageChannel.parse` 校验（非法直接 400），并加 `@Size(max = 16)`；
  - 查询改精确 token 匹配：`wrapper.apply("FIND_IN_SET({0}, channels) > 0", channelUpper)`（参数绑定，无注入）；
  - 在 `MessageRecordQueryRequest` javadoc 注明 `FIND_IN_SET` 不走索引，量大时需改为查询 `msg_delivery` 或冗余 `channels` 归一表（列入性能专项）。
- **验收**：单测/L1 SQL 断言生成语句含 `FIND_IN_SET` 且参数绑定；`channel=sms` 大小写归一；`channel=%` 被拒绝。

### D3 空消息可通过校验

- **位置**：`SendMessageRequest`（无跨字段约束）；`resolveTitle/resolveContent` 回落 `""`。
- **结论**：**成立**。title/content/templateCode 全空时仍会落库并投递空消息。
- **修复方案**：DTO 增加类级校验：
  ```java
  @AssertTrue(message = "templateCode 或 title/content 至少提供一个")
  public boolean isMessageBodyPresent() {
      return StringUtils.hasText(templateCode) || StringUtils.hasText(title) || StringUtils.hasText(content);
  }
  ```
  Service 保留防御：渲染后 title 与 content 仍全空时抛 `PARAM_ERROR`（防止模板渲染为空）。
- **验收**：三者全空 → 400；仅 templateCode → 通过；模板渲染结果为空 → 400。

## 三、P1（契约完整性 / 校验生效）

### D1 channels 元素级校验缺失

- **位置**：`SendMessageRequest` L55-57。
- **现状**：`List<String>` 可含 `null`/空串；`normalizeChannels` 逐条 `warn` 后静默跳过，只有全非法才 400。
- **修复**：`@Size(max = 8) List<@NotBlank @Size(max = 16) String> channels`。非法元素在参数校验阶段 400，Service 的 `normalizeChannels` 只保留「大小写归一 + 去重」。
- **决策**：是否允许「部分非法渠道被忽略」？建议**不允许**，与 API 严格校验语义一致。
- **验收**：`["EMAIL", null]` → 400；`["email"]` → 归一为 `EMAIL`。

### D2 MessageRecipient 允许全空接收人

- **位置**：`MessageRecipient` L22-32（三字段均可空）。
- **现状**：全空接收人经 `resolveRecipient` 得到 `"-"`，各渠道记 SKIPPED，产生 `recipient="-"` 脏记录。
- **修复**：类级 `@AssertTrue`：
  ```java
  @AssertTrue(message = "接收人至少提供 userId/email/phone 之一")
  public boolean isReachable() { ... }
  ```
  Service 侧保留兜底过滤（防内部调用绕过校验）。
- **验收**：全空 recipient → 400；仅 phone → 通过。

### D8 PreferenceResolveRequest 嵌套校验未级联

- **位置**：`PreferenceResolveRequest` L33 `Defaults defaults`、L35 `quietBypassLevels`。
- **修复**：
  - `@Valid Defaults defaults`；
  - `Defaults.channels` 加 `@Size(max = 8)` + `List<@NotBlank @Size(max = 16) String>`；
  - `quietBypassLevels` 加 `@Size(max = 8)`。
- **验收**：`defaults.channels` 传 9 个 → 400；传 null defaults → 通过。

### D12 @GetMapping 分页接口缺 @Valid

- **位置**：`MessageInAppController` L41、`MessageTemplateController` L49、`MessageAnnouncementController` L52、`MessageRecordController` L45。
- **结论**：**成立**，但影响有限：`PageQuery.toPage()` L41-45 已做页码/页大小钳制，不会全表扫描；受影响的是本批新增的 DTO 级校验（`readFlag`、`status`、`channel` 等）不会触发。
- **修复**：四个方法参数加 `@Valid`；`MessageInAppController.markRead` 的 `@PathVariable` 如需约束再加 `@Min(1)`。
- **验收**：`readFlag=2` → 400；`size=10000` → 400（当前是静默钳制为 200）。

### D13 readFlag 取值未约束

- **位置**：`InAppMessageQueryRequest` L19。
- **修复**：`@Min(0) @Max(1)`。
- **验收**：`readFlag=2` → 400。

## 四、P1（语义定稿，需要产品/调用方确认）

### D5 回执批量「整批原子 vs 逐条成功」

- **位置**：`MessageReceiptServiceImpl` L100-101 `@Transactional(rollbackFor = Exception.class)`。
- **现状**：一条非法 `status` 会让同批 200 条全部回滚。
- **建议方案（先做低风险前置校验）**：
  1. `DeliveryReceiptItem.status` 改为 `@Pattern(regexp = "DELIVERED|READ|BOUNCED")`，非法状态在参数校验阶段 400，不进入事务；
  2. 是否改为「逐条独立事务 + 逐条结果」需要产品确认——回执网关通常偏好部分成功，但站内/邮件渠道对账更偏好整批一致。
- **输出**：定稿后在本文件更新结论，并同步 `MessageReceiptService` javadoc。

### D6 / D15 DeliveryReceiptRequest.tenantId 契约矛盾

- **位置**：`DeliveryReceiptRequest` L24-25（注释「必须显式传入」）；Service 用 `TenantContext.get()` 兜底。
- **修复（二选一，推荐 A）**：
  - A. DTO 加 `@NotNull`，Service 删除兜底，缺少租户直接 400 —— 契约显式，便于排障；
  - B. 保留兜底，注释改为「缺省取当前租户上下文」。
- **验收**：内网调用不传 tenantId 的行为与定稿一致。

### D10 手机号格式校验口径

- **位置**：`MessageRecipient` L30-31（仅 `@Size(max = 32)`）。
- **决策**：
  - A. 加宽松正则 `^\+?[0-9][0-9\-\s]{4,31}$`（覆盖国际区号与分隔符）；
  - B. 明确「平台不校验手机号格式，由短信网关兜底」并写入 javadoc。
  - 建议 A 的宽松版 + 文档说明，不做运营商级号段校验。
- **验收**：`+86 138-0000-0000` 通过；`abc` 拒绝。

### D18 模板更新时 status=null 的语义

- **位置**：`MessageTemplateSaveRequest`、`MessageTemplateServiceImpl.update`。
- **现状**：已实现「null 保留原值」（见 devlog 第二批），但 DTO 注释只写了新增默认启用。
- **修复**：DTO/接口 javadoc 补「更新时 status 为空表示不修改」。
- **验收**：更新不传 status，库中状态不变，返回 VO 状态非空。

## 五、已满足 / 误判（无需改码）

| 项 | 结论 |
| --- | --- |
| D9 模板 DTO 校验 | 已满足：`templateCode @NotBlank`、`status @Min(0) @Max(1)`，服务层二次校验已补 |
| D14 SendMessageRequest.tenantId 注释 | 已满足：注释与 `send` 的 `TenantContext` 兜底一致 |
| D17 MessageRecipient 注释与 SKIPPED 行为 | 已满足 |
| D11 具体示例 | 误判：「EMAIL」不含「IM」子串；但 LIKE 通配符风险成立，按 D11 修复 |
| N36 Service 接口缺失 | 已关闭：`MessageSendService`/`MessageReceiptService`/`MessageInAppService` 等均存在 |
| N38-N40（BaseEntity/TenantContext/DDL） | 已核验：`PageQuery` 含 `@Min/@Max` 且 `toPage()` 钳制；`TenantContext` 在 starter；DDL 唯一索引齐全 |

## 六、P2（防御性加固，随下批一并做）

| 项 | 内容 | 方案 |
| --- | --- | --- |
| DTO 回调地址格式 | `callbackUrl` 只有 `@Size(max=512)` | 加 `@Pattern(regexp = "^https?://.+")`，与 `CallbackUrlGuard` 形成「格式 + 黑名单」双层 |
| N23 senderMap 缓存 | 每次 dispatch/retry 重建 Map | `@PostConstruct` 构建一次 |
| N26 回执批量查询 | 逐条查 record/delivery | 回执批次内按 messageId 批量预取 |
| N14 多实例派发 | 仅注释说明 | 需要时实现 `DISPATCHING` 抢占 + 超时回收 |
| #12 providerMessageId | EMAIL/SMS/IM 回执无法自动匹配 | 按供应商协议解析并回写；在此之前保持 UNMATCHED 对账 |

## 七、实施顺序与验证

1. **第一批（P0）**：D4 → D7/D16 → D11 → D3。
2. **第二批（P1）**：D1、D2、D8、D12、D13，同步补 D5 的状态 `@Pattern` 前置校验。
3. **第三批（语义定稿）**：D5 事务粒度、D6/D15 租户必填、D10 手机号口径、D18 文档。
4. **第四批（P2）**：DTO 回调 `@Pattern`、N23、N26。

每批统一验证口径：

```powershell
mvn -o -pl whale-services/whale-message test checkstyle:check
```

- 新增/调整用例必须覆盖：回执键长度与幂等、偏好上限、FIND_IN_SET 生成 SQL、空消息拒绝、
  渠道/接收人元素级校验、`@Valid` 生效（readFlag/size 越界 400）。
- 每批完成后在 `docs/devlog/YYYY-MM-DD.md` 追加记录并推送 Gitee（GitHub 镜像顺带同步）。
