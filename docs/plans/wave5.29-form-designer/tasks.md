# Wave 5.29 任务拆分（拖拽式表单设计器 · 并行执行）

> 依赖与并行：[spec.md](spec.md) 冻结契约（F1）后，**后端补强（F2）与前端骨架（F3）两轨并行**；F3 完成后 4 个页面级任务（F4/F5/F7/F8）并行；随后设计器（F6）依赖 F3+F5；末尾门禁收口（F9）依赖全部。
> 唯一权威计划：[.trae/documents/wave5.29-form-designer.md](../../../.trae/documents/wave5.29-form-designer.md)。

## Wave 5.29：拖拽式表单设计器（前后端贯通）

**G1：文档与契约冻结（F1）** — 无依赖

- [x] F1-a `docs/plans/wave5.29-form-designer/spec.md`（冻结 §三 契约 + §七 决策）
- [x] F1-b `docs/plans/wave5.29-form-designer/tasks.md`（本文件）
- [x] F1-c `roadmap.md` §2.5 追加 Wave 5.29 条目
- [x] F1-d `task-breakdown.md` 追加 Wave 5.29-a/b/c 分组
- [x] F1-e 冻结 `whale-form-designer/src/api/form.ts` 类型与接口契约（§四）

**G2：后端补强 || 前端骨架（并行）**

- [x] F2 后端 whale-form 最小补强
  - [x] B1 DDL：`init.sql` 加 `col_span` + 新建 `db/migration-5.29.sql`
  - [x] B2 `FormFieldConfig`/`FormFieldConfigDTO`(+`colSpan`)、`FormFieldConfigVO`(+`colSpan/columnCode/dataType/required/maxLength`)
  - [x] B3 `FormDefinitionServiceImpl`：`toConfig` 写 `colSpan` + 私有 `enrichColumnInfo` 回填列信息（`getDetail/create/update`）
  - [x] 单测：`colSpan` 持久化 + enrich 回填
  - [x] 模块自检 `mvn -f backend/pom.xml -pl whale-services/whale-form -am clean install`（Checkstyle 0 违规）
- [x] F3 前端骨架（新应用 `whale-form-designer`）
  - [x] F3-a 骨架：`package.json`/`index.html`/`tsconfig.json`/`vite.config.ts`（8002，proxy `^/(auth|form)(/|$)`）
  - [x] F3-b `src/api/{token,http,auth}.ts`（复制 admin，storage 前缀 `whale_fd_`）
  - [x] F3-c `src/api/form.ts`（冻结契约类型 + 请求封装）
  - [x] F3-d `src/router/guards.tsx` + `src/layouts/AppLayout.tsx` + `src/pages/Login.tsx` + `src/main.tsx` + `src/App.tsx`
  - [x] F3-e `pnpm -C frontend install` 装 `@dnd-kit/*`

**G3：页面级任务（并行）**

- [x] F4 `pages/table/TableDefinitionList.tsx`（含列编辑器 + 发布 + 「新建表单视图」跳转）
- [x] F5 `pages/form/FormList.tsx`（设计/发布/填报/数据/删除）
- [x] F7 `render/FormRenderer.tsx` + `render/FormRender.tsx`（受控渲染器 + 填报页 + 序列化）
- [x] F8 `pages/data/FormDataList.tsx`（分页 + 详情 Drawer 标签:值 + 删除）

**G4：设计器（F6）** — 依赖 F3、F5

- [x] F6-a `designer/FormDesigner.tsx` 三栏骨架 + 顶栏（保存/发布/预览/填报/返回）
- [x] F6-b `designer/Palette.tsx`（数据字段 + 布局字段 + 新增列）
- [x] F6-c `designer/Canvas.tsx`（`DndContext`+`SortableContext`，Row/Col 栅格，重排）
- [x] F6-d `designer/PropertyPanel.tsx` + `FieldPreview.tsx`（属性编辑 + 选项编辑）
- [x] F6-e 保存/发布/预览/回填贯通 + 前置校验（≥1 数据字段、columnCode 不重复）

**G5：门禁与端到端贯通（F9）** — 依赖 F2/F4/F5/F6/F7/F8

- [x] F9-a 后端全量 `mvn -f backend/pom.xml clean install`
- [x] F9-b 前端 `pnpm -C frontend --filter whale-form-designer build`（`tsc -b && vite build`）
- [x] F9-c 实机 E2E（7 步正向 + 2 条负向，`browser_use` 截图）
- [x] F9-d `docs/reviews/2026-10-20-wave5.29-form-designer.md`
- [x] F9-e `docs/devlog/2026-10-20.md` + roadmap/task-breakdown 勾选
- [x] F9-f commit & push origin（Gitee）

> **F9 追加缺陷修复（实机 E2E 暴露，P0）**：雪花 ID 精度丢失 —— 契约层 Long 类 ID 统一改 `string`（新增 `type Id`），清除 `Number(id)`；详见 [检查报告](../../reviews/2026-10-20-wave5.29-form-designer.md) §2.3。
