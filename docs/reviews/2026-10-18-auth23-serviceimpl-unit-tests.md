# 代码检查报告 — whale-auth 零覆盖 ServiceImpl 单测补齐（AUTH-23 收口 / Wave 5.7）

- 关联需求：REQ-QA-002（每次代码变更必检）、REQ-CICD-001（覆盖率门禁）
- 变更范围：
  - `backend/whale-services/whale-auth/src/test/java/com/bluewhale/auth/service/impl/SysRoleServiceImplTest.java`（新增，21 用例）
  - `.../service/impl/SysPermissionServiceImplTest.java`（新增，13 用例）
  - `.../service/impl/SysDeptServiceImplTest.java`（新增，8 用例）
  - `.../service/impl/SysUserPermissionServiceImplTest.java`（新增，2 用例）
  - 文档：`docs/specs/2026-10-18-wave5.7-auth-serviceimpl-ut/{spec.md,tasks.md,checklist.md}`
- 检查日期：2026-10-18 ｜ 检查人：BlueWhale
- 结论：**通过**（99 用例全绿；`whale-auth` 行覆盖 0.6755 / 分支 0.6718，`-Pcoverage-gate` 通过；全 reactor Checkstyle 0 违规）

---

## 一、验证结果（构建口径）

| 项 | 命令 | 结果 |
|---|---|---|
| 单元测试 | `mvn -f backend/pom.xml -pl :whale-auth -am clean verify -Pcoverage-gate` | 99 run / 0 failures / 0 errors / 0 skipped（既有 55 + 新增 44 用例） |
| 覆盖率门禁 | 同上（`-Pcoverage-gate`） | **All coverage checks have been met** / BUILD SUCCESS（LINE ≥ 0.40、BRANCH ≥ 0.30 均满足） |
| 覆盖率实测 | `target/site/jacoco/jacoco.csv`（最后一次 `clean verify`） | LINE 483/715 = **0.6755**；BRANCH 176/262 = **0.6718** |
| 代码规范 | `mvn -f backend/pom.xml checkstyle:check` | 0 违规（含 E16 Javadoc 门禁）、BUILD SUCCESS |

逐类覆盖（`jacoco.csv`）：`SysRoleServiceImpl` 81/81 覆盖、`SysPermissionServiceImpl` 53/53、`SysDeptServiceImpl` 35/35、`SysUserPermissionServiceImpl` **8/18**（余 10 行为 `replaceUserAttrs` 非空批量写路径，见第五节）。

> **口径说明**：上表数值取自最终 `clean verify`（`clean` 清除历史 `jacoco.exec`）。JaCoCo `check`/`report` 默认 `append=true`，若不清 `target` 而连续多次运行，历史执行数据会累积进报告导致覆盖率虚高（本波次首测曾出现此现象，见第三节 #3）。

---

## 二、逐项检查结果

| 编号 | 检查项 | 结论 | 证据（文件） | 处置 |
|---|---|---|---|---|
| A1–A12 | 协议与接口安全 | 不适用 | 本波次仅新增测试代码，无对外协议变更 | — |
| B1 | SQL 注入 | 不适用 | 测试以 Mockito 桩替代 Mapper，无 SQL 拼接 | — |
| B5 | 硬编码凭据 | 通过 | 测试数据为虚构用户名/编码，无密钥字面量 | — |
| B9 | 异常处理 | 通过 | 异常路径用 `assertThrows` 断言 `BizException`；无空 catch | 各 `*Test` |
| B10 | 空值与边界 | 通过 | 覆盖空关键字、空列表、`null` 分支、非法编码/非法状态等边界 | 各 `*Test` |
| B16 | 并发安全 | 不适用 | 单测无共享可变状态；`@BeforeEach` 重建被测对象 | 各 `*Test` |
| C1 | 圈复杂度 | 通过 | 每个 `@Test` 均为「桩 → 调用 → 断言」线性结构，无分支嵌套 | 各 `*Test` |
| C6 | 重复代码 | 通过 | 测试数据构造下沉为 `private static` 工厂方法（`role()/permission()/dept()/row()`） | 各 `*Test` |
| C7 | 死代码 | 通过 | 无注释代码块、无未使用 import（`mvn compile` 无告警） | — |
| D1/D2 | 魔法值 | 通过 | 关键数值提为 `static final` 常量（`ROLE_ID/USER_ID/PERM_ID/...`） | 各 `*Test` |
| E5 | 注释与文档 | 通过 | 每个测试类含中文 Javadoc + `@author`；每个 `@Test` 含中文 `@DisplayName`（测试源非 Checkstyle Javadoc 门禁范围，与既有 `SysUserServiceImplTest` 一致） | 各 `*Test` |
| E6 | 格式 | 通过 | 4 空格缩进、行宽 ≤ 120 | 各 `*Test` |
| E16 | Javadoc 自动化门禁 | 通过 | `checkstyle:check` 0 违规 | — |

---

## 三、未通过项与修复记录（本波次自检发现并修复）

| # | 问题 | 定位 | 根因 | 修复方式 | 复验结论 |
|---|---|---|---|---|---|
| 1 | `SysRoleServiceImplTest` 4 例报 `MybatisPlusException: can not find lambda cache for this entity` | `SysRoleServiceImpl` 的 `in(SysPermission::getId, ...)` / `in(SysRole::getId, ...)` / `in(SysRolePermission::getRoleId, ...)` | `AbstractLambdaWrapper#in` 会**即时**解析列名，需 MyBatis-Plus 实体 `TableInfo` 缓存；纯单测无 MyBatis 环境 | 测试类 `@BeforeAll` 用 `TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ...)` 预注册 `SysRole/SysPermission/SysRolePermission/SysUserRole` 元信息 | 通过（21/21 绿） |
| 2 | `SysUserPermissionServiceImplTest` 的非空 `saveBatch` 用例报 `Unable to retrieve the mapperInterface and sqlSession properties from userPermissionMapper` | `SysUserPermissionServiceImpl#replaceUserAttrs` 第 48 行 | `saveBatch → CrudRepository.getSqlStatement → MybatisUtils.getMapperProxy(baseMapper)` 要求 `baseMapper` 为**真实 MyBatis `MapperProxy`**，Mockito 桩无法满足 | 删除该用例；仅保留 `listByUserId` 与「空属性」分支；在 spec §3.4/§6 与 backlog 登记为**残余项**（待容器/MyBatis 环境就绪由 IT 覆盖） | 通过（2/2 绿） |
| 3 | 覆盖率/用例数口径虚高：一度记录为「104 用例、LINE 491/715＝0.6867、BRANCH 177/262＝0.6756」 | `target/site/jacoco/jacoco.csv`、`docs/reviews/`、`docs/devlog/2026-10-18.md`、`whale-auth-backlog.md` | ① JaCoCo `append=true`，未 `clean` 的连续运行把**已删除用例**（`saveBatch` 非空分支，L40–48）的历史执行数据累积进 `jacoco.exec`；② 用例数按「既有 60 + 新增 44」估算，与实测既有 55 不符 | 统一以**最终 `clean verify`** 实测为准：99 用例、LINE **483/715 = 0.6755**、BRANCH **176/262 = 0.6718**、`SysUserPermissionServiceImpl` **8/18**；同步更正报告/日志/台账数值，并在验证命令中固定使用 `clean` | 通过（门禁仍达标） |

> 说明：问题 1 已在测试代码层修复且不涉及生产代码；问题 2 属**测试可行性边界**，非缺陷，按「以代码事实为准」原则调整用例范围并留痕，不修改生产代码以迎合测试；问题 3 为**度量口径勘误**，以实测数据覆盖估算数据。

---

## 四、覆盖率门禁（REQ-CICD-001）影响

| 项 | Wave 5.6 基线 | Wave 5.7 现状 | 结论 |
|---|---|---|---|
| `whale-auth` LINE | 267/715 = 0.3734（未达 0.40） | 483/715 = **0.6755** | ✅ 达门禁 |
| `whale-auth` BRANCH | 111/262 = 0.4237 | 176/262 = **0.6718** | ✅ 达门禁 |

`-Pcoverage-gate` 已可对 `whale-auth` 生效；AUTH-24 台账中「coverage-gate 接入 CI 待 AUTH-23 达标后启用」的前置条件已具备。

---

## 五、遗留（P3 建议项）

- 控制器层（6 个 `Controller`）单测未覆盖。
- `SysUserPermissionServiceImpl#replaceUserAttrs` 非空分支（`saveBatch` 批量写，L40–48，余 10 行）需 Spring/MyBatis 环境，记入 backlog。
- `SysUserServiceImpl`（余 65 行）与 `SysMenuServiceImpl`（余 37 行）覆盖率仍偏低，可按需补测。
- `-Pcoverage-gate` 纳入 Gitee Go CI 阻断链路（AUTH-28 流水线兼容性待 Gitee 侧验证）。
