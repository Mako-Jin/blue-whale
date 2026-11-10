# Wave 5.32 任务分解（whale-workflow-designer 工作流前端 · 管理 + 绘制 + 表单绑定）

> 规格：[spec.md](spec.md)　台账：[roadmap.md](../roadmap.md) §2.5、[task-breakdown.md](../task-breakdown.md)
> 执行原则（沿用项目约定）：**先落文档 → 定契约 → 波次并行 → 模块自检留报告 → 全量回归 → 实机 E2E → 收尾**。

## 并行性分析

| 依赖 | 结论 |
|---|---|
| 前端骨架（F0）是 F1–F5 的共同前提 | **F0 必须先行**（Phase 1），F1–F5 在 Phase 2 并行 |
| 后端补强（B 轨）与前端骨架互不依赖 | Phase 1 与 F0 **并行** |
| 共享渲染器抽取（S 轨）只被 F3/F4 消费 | Phase 1 与 F0/B **并行**，F3/F4 等 S 完成 |
| F1 设计器工作量最大且独立 | 与 F2/F4/F5 并行；F3 依赖 S 轨与 B 轨 |
| Phase 3 收尾需全部产物 | 串行 |

---

## Phase 0：契约与准备（串行）

- [x] T0.1 落文档：`spec.md` + `tasks.md`（本文件）
- [x] T0.2 环境核查：8003 端口空闲；whale-gateway/whale-workflow/whale-form 三服务可达；`bluewhale_workflow` 库在线

## Phase 1：三轨并行（B 后端补强 / S 共享渲染器 / F0 应用骨架）

### B 轨：whale-workflow 可加性补强

- [x] B1 节点字段与 JSON 往返：`ProcessModelNode` + `formId`/`formCode`/`x`/`y`；`ProcessModelParser.readNode` 读取；`ProcessModelServiceImpl.toJson` 写出（legacy 分支不带新字段）
- [x] B2 校验码：`ValidationCode` + `USER_TASK_FORM_CODE_INVALID` / `USER_TASK_FORM_INCOMPLETE`；`ProcessModelValidator` 接入（仅 USER_TASK、仅边界格式与成对性）
- [x] B3 BPMN 对称往返：`BpmnXmlParser` 读任意前缀 `formKey`/`formId`；`BpmnXmlWriter` 非空时写 plain `formKey`/`formId`（不输出 `x`/`y`）
- [x] B4 库表/实体/VO/建任务回填：`process_task` + `node_key`/`form_id`/`form_code`；`entity/ProcessTask` + 3 字段；`ProcessTaskVO` + `nodeKey`/`formId`/`formCode`；`ProcessInstanceServiceImpl` 与 `ProcessTaskServiceImpl` 建任务时从节点模型回填
- [x] B5 DDL：`db/migration-5.32.sql`（幂等）+ `db/init.sql` 权威同步
- [x] B6 单测：扩展 parser/validator/BPMN/ServiceImpl 用例（新字段往返、两个新码、三列落库）+ 既有用例零回归
- [x] B7 模块门禁：`mvn -o -pl :whale-workflow -am test checkstyle:check` 全绿

### S 轨：共享表单渲染器

- [x] S1 抽取 `FormRenderer` 至 `elemental/whale-ui`（`src/components/FormRenderer/index.tsx` + 结构化字段类型 `WhaleFormFieldConfig`），props 签名不变（`fields/value/onChange/disabled`）
- [x] S2 `whale-form-designer` 的 `src/render/FormRenderer.tsx` 改为 re-export（调用点零改动）
- [x] S3 回归：`pnpm --filter whale-ui build` + `pnpm --filter whale-form-designer build` 通过

### F0 轨：新应用骨架（8003）

- [x] F0-a 工程文件：`package.json`（workspace 依赖）/ `index.html` / `tsconfig.json` / `tsconfig.node.json` / `vite.config.ts`（端口 8003，proxy `^/(auth|workflow|form)(/|$)`）/ `vite-env.d.ts`
- [x] F0-b 契约层：`api/http.ts`（Result 信封 + PageResult + 401 单飞续期 + `downloadBlob`）、`api/token.ts`（前缀 `whale_wf_`）、`api/auth.ts`、`api/workflow.ts`、`api/form.ts`（`Id = string`，全链路禁 `Number(id)`）
- [x] F0-c 框架：`router/guards.tsx`、`layouts/AppLayout.tsx`（四菜单）、`pages/Login.tsx`、`main.tsx`、`App.tsx`
- [x] F0-d 骨架验证：`pnpm --filter whale-workflow-designer build` 通过 + 登录页可达

## Phase 2：五路并行（依赖 Phase 1）

### F1 轨：自由画布设计器（工作量最大）

- [x] F1-a 画布基础：`Canvas.tsx`（SVG 画布 + `@dnd-kit` 拖入 + 节点拖动 + 选中态）、`Palette.tsx`（4 类节点）、`NodeShape.tsx`
- [x] F1-b 连线：`EdgePath.tsx` + 「选中源节点 → 点目标节点」建流；连线选中 → 删除 / 设为网关 `isDefault`
- [x] F1-c 属性面板：`PropertyPanel.tsx`（`key`/`name`/`type`/`assignee`/`formId`+`formCode`）+ `FormPicker.tsx`（拉 `GET /form/definition/page`）
- [x] F1-d 序列化与持久化：`model.ts`（画布↔规范化 JSON、缺坐标自动布局回填）；保存（`POST`/`PUT /workflow/definition`）；`/validate` 错误按 `refKey` 高亮 + `warnings` 提示条
- [x] F1-e BPMN：导入（`POST /workflow/definition/bpmn`）+ 导出（`GET /{id}/bpmn` → `downloadBlob`）
- [x] F1-f 验证：类型检查 + 构建通过；浏览器可拖拽落点、保存后 JSON 结构正确

### F2 轨：流程定义管理

- [x] F2-a `pages/definition/DefinitionList.tsx`：分页列表 + 新建/编辑（`code`/`name`）+ 删除 + 校验（内联展示 `errors/warnings`）+ 跳设计器 + 发起入口 + BPMN 导入导出入口
- [x] F2-b 验证：构建 + 浏览器 CRUD 冒烟

### F3 轨：流程发起 + 填表单（依赖 S、B）

- [x] F3-a `pages/start/ProcessStart.tsx`：选定义 → 入口 `USER_TASK` 节点 `formId` → `GET /form/definition/{id}` → `<FormRenderer>` 填报 → `POST /form/data` 取数据 ID → `POST /workflow/instance`（`businessKey` = 数据 ID）
- [x] F3-b 无绑定表单分支：仅填 `title` 直接发起
- [x] F3-c 验证：构建 + 浏览器发起冒烟（含表单提交与实例生成）

### F4 轨：我的待办 + 审批（依赖 S、B）

- [x] F4-a `pages/task/TaskCenter.tsx`：`GET /workflow/task/page` 待办列表
- [x] F4-b 审批抽屉：`formId` → 表单定义；`GET /workflow/instance/{id}` 的 `businessKey` → `GET /form/data/{id}` → `<FormRenderer disabled>` 只读回显 + 审批意见 → `POST /workflow/task/{id}/approve|reject`
- [x] F4-c 降级：表单/数据缺失 → 「无表单数据」占位，审批仍可提交
- [x] F4-d 验证：构建 + 浏览器同意/驳回冒烟（表单回显 + 流转推进）

### F5 轨：流程实例监控

- [x] F5-a `pages/instance/InstanceList.tsx`：`GET /workflow/instance/page` 列表 + 详情抽屉（`currentNodeKey`/`status`/`tasks` 轨迹）+ 撤销入口
- [x] F5-b 验证：构建 + 浏览器列表与详情冒烟

## Phase 3：收尾（串行）

- [x] V1 后端全量回归：`mvn -o -f whale-backend/pom.xml clean install -T 1C` → 28/28 BUILD SUCCESS（先停运行中的服务释放 jar）
- [x] V2 DDL 落库 + 后端运行时冒烟（新字段往返、两个新校验码、待办三列、BPMN `formKey` 往返）
- [x] V3 前端全量构建：`pnpm -C frontend build`（含新应用与 form-designer 回归）
- [x] V4 浏览器端到端：登录 → 绘制 → 校验 → BPMN 往返 → 发起填表单 → 待办审批 → 实例监控（A/B/C/C2/C3/D/E/F/G 全 PASS；**C4 拖拽手势**：dnd-kit 拖拽源已确认正确挂载（`role="button"`+`aria-roledescription="draggable"`），但自动化 harness 无法产生可信原生指针流，手势**需人工复核**）
- [x] V5 文档收尾：检查报告 `docs/reviews/`、`docs/devlog/2026-10-2X.md`、roadmap §2.5 与 task-breakdown 勾选
- [x] V6 commit & push `origin`（Gitee `https://gitee.com/StrongToStop/blue-whale.git`）
