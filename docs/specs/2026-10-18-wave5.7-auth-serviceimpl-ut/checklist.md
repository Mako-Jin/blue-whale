# Checklist：Wave 5.7 whale-auth 零覆盖 ServiceImpl 补测（AUTH-23 收口）

> 关联 spec：[spec.md](./spec.md) ｜ 关联 tasks：[tasks.md](./tasks.md)
> 逐项核验，通过则勾选；未通过需在 tasks.md 新增修复任务后重新核验。

## 一、文档先行

- [x] 1.1 本波次 spec.md / tasks.md / checklist.md 已落地，含需求项、覆盖缺口基线、逐类用例设计、残余项
- [x] 1.2 覆盖缺口基线与 Wave 5.6 实测 `jacoco.csv` 一致（SysRole 81 / SysPermission 53 / SysDept 35 / SysUserPermission 18 未覆盖行）

## 二、测试代码

- [x] 2.1 `SysRoleServiceImplTest` 覆盖 `page`（带/不带关键字）、`getDetail`（命中/不存在）、`create`（成功/重复/非法编码）、`update`（成功/不存在）、`delete`（成功/不存在）、`assignPermissions`（空/非空/无效权限 ID/角色不存在）、`listPermissionIds`、`listRoleCodesByUserId`（空/正常）、`listPermissionCodesByUserId`（无角色/无权限/正常）——**实测 21 用例**
- [x] 2.2 `SysPermissionServiceImplTest` 覆盖 `page`（带/不带过滤）、`listAllCodes`、`getDetail`（命中/不存在）、`create`（成功/重复/非法编码）、`update`（成功/不存在/重复）、`delete`（无子/有子）——**实测 13 用例**
- [x] 2.3 `SysDeptServiceImplTest` 覆盖 `tree`（递归组树）、`getDetail`（命中/不存在）、`create`（默认值）、`update`（成功/不存在）、`delete`（无子/有子）——**实测 8 用例**
- [x] 2.4 `SysUserPermissionServiceImplTest` 覆盖 `listByUserId` 与 `replaceUserAttrs`（空属性分支）——**实测 2 用例**
- [x] 2.5 `LambdaQueryWrapper#in` 相关用例已通过 `@BeforeAll` + `TableInfoHelper.initTableInfo` 预注册实体元信息，无 `can not find lambda cache` 异常
- [x] 2.6 4 个测试类均有中文类级 Javadoc + `@author`（Grep 实测），每个 `@Test` 均带中文 `@DisplayName`（测试源不在 Checkstyle Javadoc 门禁范围，与既有 `SysUserServiceImplTest` 一致），满足 E5/E16

## 三、构建与门禁验证

- [x] 3.1 `mvn -f backend/pom.xml -pl :whale-auth -am clean verify`：新增 4 类全绿，既有用例无回归（**99 run / 0 failures / 0 errors / 0 skipped**）
- [x] 3.2 同命令加 `-Pcoverage-gate`：`All coverage checks have been met` / BUILD SUCCESS（`whale-auth` LINE **483/715 = 0.6755** ≥ 0.40、BRANCH **176/262 = 0.6718** ≥ 0.30）
- [x] 3.3 `mvn -f backend/pom.xml checkstyle:check`：全 reactor 0 违规 / BUILD SUCCESS

## 四、留痕与销账

- [x] 4.1 检查报告 `docs/reviews/2026-10-18-auth23-serviceimpl-unit-tests.md` 已产出，逐项对应 [code-review-checklist.md](../../checklists/code-review-checklist.md) 编号，结论含未通过项（#1–#3）与修复记录
- [x] 4.2 `docs/devlog/2026-10-18.md` 已追加本波次条目（§二-27、§三 #53–#55、§四更新），四节（需求项 / 完成项 / 待做项 / 已修正问题项）齐备
- [x] 4.3 台账销账：`whale-auth-backlog.md` AUTH-23 与 `next-steps.md` T5.19 均为「删除线 + ✅」
- [ ] 4.4 变更已 commit 并 push 至 Gitee `origin/main`，工作区干净且 `main == origin/main`

## 五、残余项登记（本波次明确不做）

- [x] 5.1 `replaceUserAttrs` 非空批量写路径（L40–48，余 10 行，依赖真实 Mapper 代理 / Spring 容器）已登记为残余项（spec §6、报告 §五、devlog §三 #54）
- [x] 5.2 控制器层单测、Mapper XML/SQL 覆盖、`-Pcoverage-gate` 纳入 Gitee Go 阻断链路已在 tasks.md「待做项」登记备查

---

## 核验结论

- 核验人：BlueWhale ｜ 核验日期：2026-10-18
- 结论：**通过**（4 类 44 用例全绿；`whale-auth` 覆盖率达标并解除 `coverage-gate` 前置；Checkstyle 0 违规；留痕与台账齐备）
- 复测提示：复测覆盖率须带 `clean`，否则 JaCoCo `append=true` 会累积历史 `jacoco.exec` 导致数值虚高（见报告 §一「口径说明」）
