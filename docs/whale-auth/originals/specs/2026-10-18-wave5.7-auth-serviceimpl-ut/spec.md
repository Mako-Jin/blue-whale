# Wave 5.7 · whale-auth 零覆盖 ServiceImpl 补测规格（AUTH-23 收口）

| 项 | 内容 |
|---|---|
| 关联需求 | REQ-QA-002（每次代码变更必检）、REQ-CICD-001（覆盖率门禁） |
| 任务映射 | AUTH-23（`whale-auth` 单测补全）↔ T5.18（JaCoCo 门禁前置条件） |
| 生成日期 | 2026-10-18 ｜ 作者：BlueWhale |
| 依据 | [whale-auth-backlog.md](../../plans/whale-auth-backlog.md) AUTH-23、[next-steps.md](../../plans/next-steps.md) T5.19、[cicd.md](../../design/cicd.md) §3.3、Wave 5.6 规格 [../2026-10-18-wave5.6-crosscutting-infra/spec.md](../2026-10-18-wave5.6-crosscutting-infra/spec.md) |
| 执行原则 | 文档先行（REQ-DOC-002）→ 编码 → 按 [code-review-checklist.md](../../checklists/code-review-checklist.md) 自检留报告（REQ-QA-002）→ 构建验证 → 提交推送 |

---

## 一、需求项（本波次范围）

Wave 5.6 已接入 JaCoCo 并定义 `coverage-gate` profile（行 ≥ 40%、分支 ≥ 30%）。当前 `whale-auth` 行覆盖率为 **0.3734（267/715）**，未达门禁阈值，根因是 **4 个 `ServiceImpl` 零覆盖**。本波次为这 4 个类补齐单元测试，使 `whale-auth` 行覆盖率 ≥ 0.40，并保持分支覆盖率 ≥ 0.30，为后续将 `-Pcoverage-gate` 纳入 CI 提供前置条件。

| 需求 | 本波次覆盖点 | 验收口径 |
|---|---|---|
| REQ-QA-002 | 4 个零覆盖 `ServiceImpl` 单测 + 检查报告 | `docs/reviews/2026-10-18-auth23-serviceimpl-unit-tests.md` 逐项对应清单编号 |
| REQ-CICD-001 | `whale-auth` 覆盖率达标 | `mvn -pl :whale-auth -am -Pcoverage-gate verify` 通过（行 ≥ 0.40、分支 ≥ 0.30） |

---

## 二、覆盖缺口基线（Wave 5.6 实测 `jacoco.csv`）

| 类 | LINE 未覆盖 | 主要未覆盖方法 |
|---|---|---|
| `SysRoleServiceImpl` | 81 | `page` / `getDetail` / `create` / `update` / `delete` / `assignPermissions` / `listPermissionIds` / `listRoleCodesByUserId` / `listPermissionCodesByUserId` / `listRoleIdsByUserId` |
| `SysPermissionServiceImpl` | 53 | `page` / `listAllCodes` / `getDetail` / `create` / `update` / `delete` / `copyProperties` |
| `SysDeptServiceImpl` | 35 | `tree` / `buildChildren` / `getDetail` / `create` / `update` / `delete` / `copyProperties` |
| `SysUserPermissionServiceImpl` | 18 | `listByUserId` / `replaceUserAttrs` |

**目标**：`whale-auth` 行覆盖 267 → ≥ 286（+19 即可达标，本波次预期覆盖上述绝大部分行，实际增量远超阈值）。

---

## 三、测试设计（逐类）

> 统一约束：JUnit 5 + Mockito；`@ExtendWith(MockitoExtension.class)`；中文 Javadoc（类 + 每个 `@Test` 方法，E5）；无 Spring 上下文，构造被测对象后以 `ReflectionTestUtils` 注入 `baseMapper`；被测类均继承 `ServiceImpl<Mapper, Entity>`，其 `save/getById/list/removeById/remove/updateById` 为接口 default 方法，均委派 `baseMapper`，故 mock `baseMapper` 即可。

### 3.1 `SysRoleServiceImplTest`

构造：`new SysRoleServiceImpl(userRoleMapper, rolePermissionMapper, permissionMapper)`，`baseMapper` ← `SysRoleMapper` mock。

| 用例 | 覆盖路径 |
|---|---|
| `page`：带关键字 | `StringUtils.hasText` 真分支 + `selectPage` + `RoleVO.from` 映射 + `PageResult.of` |
| `page`：无关键字（total=0） | `hasText` 假分支 + 空记录 |
| `getDetail`：存在 | `getById` 命中 + 转换 |
| `getDetail`：不存在 | `BizException(NOT_FOUND)` |
| `create`：成功 | `RoleCode.of` + `selectCount=0` + 默认 sortNo=0/status=1 + `save` |
| `create`：编码重复 | `selectCount>0` → `PARAM_ERROR` |
| `create`：非法编码 | `RoleCode.of` 抛 `BizException`（值对象不变量） |
| `update`：成功 | `getById` + 条件赋值 + `updateById` |
| `update`：不存在 | `BizException(NOT_FOUND)` |
| `delete`：成功 | `removeById` + `rolePermissionMapper.delete` |
| `delete`：不存在 | `BizException(NOT_FOUND)` |
| `assignPermissions`：成功（空列表） | 校验通过 + 关联清零，无 insert |
| `assignPermissions`：成功（非空且校验通过） | `permissionMapper.selectCount == size` + 逐条 `insert` |
| `assignPermissions`：权限 ID 无效 | `selectCount != size` → `PARAM_ERROR` |
| `assignPermissions`：角色不存在 | `BizException(NOT_FOUND)` |
| `listPermissionIds` | 关联查询映射 |
| `listRoleCodesByUserId`：无角色 | `roleIds` 空 → `List.of()` |
| `listRoleCodesByUserId`：有角色 | `list` 过滤 status=1 → 映射 roleCode |
| `listPermissionCodesByUserId`：无角色 | `roleIds` 空 → `List.of()` |
| `listPermissionCodesByUserId`：无权限关联 | `permissionIds` 空 → `List.of()` |
| `listPermissionCodesByUserId`：正常 | 关联→权限点 ids→`permCode` 去重 |

### 3.2 `SysPermissionServiceImplTest`

构造：`new SysPermissionServiceImpl()`，`baseMapper` ← `SysPermissionMapper` mock。

| 用例 | 覆盖路径 |
|---|---|
| `page`：关键字 + 类型 | 两个 `hasText` 真分支 + 分页映射 |
| `page`：无关键字/无类型 | 两个假分支 |
| `listAllCodes` | `list` + `distinct` 映射 |
| `getDetail`：存在 / 不存在 | 命中转换 / `NOT_FOUND` |
| `create`：成功（默认值） | `PermCode.of` + `selectCount=0` + `copyProperties` 默认 parentId=0/sortNo=0/status=1 + `save` |
| `create`：编码重复 | `PARAM_ERROR` |
| `create`：非法编码 | `PermCode.of` 抛 `BizException` |
| `update`：成功 | `getById` + `selectCount`（`ne id`）→ `updateById` |
| `update`：不存在 | `NOT_FOUND` |
| `update`：编码重复 | `PARAM_ERROR` |
| `delete`：成功 | 子节点计数 0 → `removeById` |
| `delete`：存在子权限 | `PARAM_ERROR` |

### 3.3 `SysDeptServiceImplTest`

构造：`new SysDeptServiceImpl()`，`baseMapper` ← `SysDeptMapper` mock。

| 用例 | 覆盖路径 |
|---|---|
| `tree`：两级 | `list` 排序 + `buildChildren` 递归（`peek` 组树） |
| `getDetail`：存在 / 不存在 | 命中转换 / `NOT_FOUND` |
| `create`：默认值 | `copyProperties` 默认 parentId=0/sort=0 + `save` |
| `update`：成功 / 不存在 | `getById` + `updateById` / `NOT_FOUND` |
| `delete`：成功（无子） | 子节点 0 → `removeById` |
| `delete`：存在子部门 | `PARAM_ERROR` |

### 3.4 `SysUserPermissionServiceImplTest`

构造：`new SysUserPermissionServiceImpl()`，`baseMapper` ← `SysUserPermissionMapper` mock。

| 用例 | 覆盖路径 |
|---|---|
| `listByUserId` | `list` + `UserAttrVO.from` 映射 |
| `replaceUserAttrs`：attrs 为空 | `remove` 物理删除 + 提前返回（不触发批量写） |

> **实现偏差（以代码事实为准）**：`replaceUserAttrs` 的非空分支走 `saveBatch → CrudRepository.getSqlStatement → MybatisUtils.getMapperProxy(baseMapper)`，要求 `baseMapper` 为真实 MyBatis `MapperProxy`；纯 Mock 单测注入的是 Mockito 桩，`getSqlStatement` 处即抛 `MybatisPlusException(Unable to retrieve the mapperInterface...)`，无法在无容器单测中构造。故本波次仅覆盖「空属性」分支，非空批量写路径记入残余项（§6），待 Spring/MyBatis 环境就绪后由集成测试覆盖。

---

## 四、执行顺序与依赖

```
1) 文档先行：本 spec + tasks 落地
2) 编写 4 个测试类（互相独立，可并行）
3) mvn -f whale-backend/pom.xml -pl :whale-auth -am test（全绿）
4) mvn -f whale-backend/pom.xml -pl :whale-auth -am -Pcoverage-gate verify（行≥0.40、分支≥0.30 通过）
5) mvn -f whale-backend/pom.xml checkstyle:check（全 reactor 0 违规）
6) 检查报告 docs/reviews/2026-10-18-auth23-serviceimpl-unit-tests.md
7) devlog/2026-10-18.md 追加 §（需求项/完成项/待做项/已修正问题项）
8) 台账销账：whale-auth-backlog.md AUTH-23、next-steps.md T5.19
9) commit + push（Gitee origin/main）
```

---

## 五、测试计划与验证口径

| 项 | 验证方式 | 通过标准 |
|---|---|---|
| 单测回归 | `mvn -pl :whale-auth -am test` | 新增 4 类全绿；既有 55 用例不回归 |
| 覆盖率门禁 | `-Pcoverage-gate verify` | `whale-auth` LINE ≥ 0.40、BRANCH ≥ 0.30 |
| 代码规范 | `mvn checkstyle:check` | 全 reactor 0 违规（含 E16 Javadoc 门禁）</br> |
| 留痕 | 检查报告 + devlog | 四类信息齐备 |

---

## 六、风险与残余项

| 风险 | 影响 | 缓解 |
|---|---|---|
| `LambdaQueryWrapper#in` 需实体元信息 | 纯单测解析列名失败（`can not find lambda cache`） | 测试类 `@BeforeAll` 以 `TableInfoHelper.initTableInfo` 预注册实体元信息 |
| `saveBatch` 依赖真实 Mapper 代理 | `replaceUserAttrs` 非空分支无法纯 Mock 覆盖 | 仅覆盖空属性分支；非空分支记入残余项，待容器就绪由 IT 覆盖 |
| 覆盖率口径为模块级 | 单类未全覆盖也可能达标 | 验收以模块级 `coverage-gate` 通过为准；单类覆盖缺口记入 backlog |
| 本机无 Docker | 端到端/中间件联调仍阻塞 | 本波次仅静态可验证的单测与覆盖率，不依赖中间件 |
