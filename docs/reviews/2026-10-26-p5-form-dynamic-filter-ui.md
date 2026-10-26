# 检查报告：动态字段筛选 in / between 的 UI 暴露

> 日期：2026-10-26 ｜ 应用：`whale-form-designer`（8002）｜ 结论：**PASS（构建 + lint + Playwright 端到端）**
> 关联：[动态字段条件检索报告](2026-10-26-p5-form-dynamic-filter.md) 第五章遗留、优先级执行台账序号 27

---

## 一、问题与范围

上一轮动态字段条件检索只在前端暴露了比较类操作符与 `like` / 空值判断，后端已支持的 `in`（集合）与 `between`（区间）无法从 UI 录入。本轮仅补前端交互，不改后端契约：

- 操作符矩阵按字段类型补齐 `in` / `between`；
- `in` 提供三种录入方式：布尔多选、`select` 选项多选、其余类型标签输入；
- `between` 提供两个边界值：数值用 `InputNumber`，日期/时间用两个文本输入；
- 条件 Tag 对多值做可读展示，增删条件与查询/导出链路保持不变。

## 二、实现

| 文件 | 变更 |
|---|---|
| `src/pages/data/FormDataList.tsx` | 操作符矩阵、`in`/`between` 值控件、多值草稿与校验、Tag 展示、控件 id |
| `e2e/tests/support/api.ts` | 新增 `createTableDefinition` / `publishTableDefinition` / `submitFormData` 预置助手 |
| `e2e/tests/form-dynamic-filter.spec.ts`（新增） | 端到端用例：`between` 数值区间、`in` 选项多选、`in` 文本标签 + API 对账 |

### 2.1 操作符矩阵（与后端 `FormDataType` / `FILTER_OPS` 对齐）

| 字段类型 | 操作符 |
|---|---|
| `number` / `date` / `datetime` | `eq` `ne` `gt` `gte` `lt` `lte` `in` `between` `isNull` `notNull` |
| `bool` | `eq` `ne` `in` `isNull` `notNull` |
| `text` / `textarea` / `select` | `eq` `ne` `like` `in` `isNull` `notNull` |

### 2.2 值控件

- **`in`**
  - `bool`：多选「是 / 否」；
  - `select`（字段配置有选项）：用字段 `options` 渲染多选，提交 `optionValue` 数组；
  - `text` / `textarea` / `number`：`Select mode="tags"`，回车或逗号分隔录入，`number` 提交前转数值。
- **`between`**
  - `number`：两个 `InputNumber`（起始 / 结束）；
  - `date` / `datetime`：两个 `DatePicker`，`date` 格式 `YYYY-MM-DD`，`datetime` 开启 `showTime` 并使用 `YYYY-MM-DD HH:mm:ss`；面板选择或键盘输入均可，清空即移除该边界值。
- **标量日期/时间比较**（`eq` / `ne` / `gt` / `gte` / `lt` / `lte`）：`date` / `datetime` 同样使用单个 `DatePicker`（`datetime` 开启 `showTime`），格式与区间一致，不再使用文本输入。
- **校验**：`in` 去空去重后至少 1 个值；`between` 必须两个非空值；`number` 多值/区间非法时提示「过滤值必须为数值」/「区间值必须为数值」。
- **Tag 展示**：`in` 用逗号连接，`between` 用 `~` 连接（如 `金额 介于 60 ~ 210`）。
- **状态复位**：切换字段/操作符、添加条件、变更表单编码、重置查询时统一清空标量值与多值草稿。

### 2.3 可测试性

为筛选区控件补充稳定 id（`filter-field` / `filter-op` / `filter-value-multi` / `filter-value-number` / `filter-value-text` / `filter-between-from` / `filter-between-to`），避免依赖 antd 占位文案或 DOM 顺序。

## 三、验证

### 3.1 构建与静态检查

- `pnpm --filter whale-form-designer build`：**通过**（`tsc -b` + Vite，2105 modules）。
- `pnpm --filter whale-form-designer lint`：**0 error / 0 warning**。
- `pnpm --filter @bluewhale/e2e lint`：**0 error / 0 warning**。

### 3.2 Playwright 端到端

`pnpm --filter @bluewhale/e2e exec playwright test tests/form-dynamic-filter.spec.ts --retries=0` → **1 passed（13.3s）**。

用例通过 API 预置独立表定义与表单（`amount` number、`stage` select(新建/完成)、`memo` text、`due_date` date），发布后写入 5 条数据（第 5 条 `stage=hold` 用于证明条件生效）：

| 步骤 | UI 操作 | 断言 |
|---|---|---|
| 基线 | 进入 `/data?definitionCode=...` | `共 5 条` |
| between（数值） | 金额 + 介于 + 60 / 210 | `共 3 条`（100/150/200），Tag `金额 介于` 可见 |
| in（选项） | 阶段 + 属于 + 新建/完成 | `共 4 条`（排除 `hold`） |
| in（文本） | 备注 + 属于 + 标签 `beta`、`omega` | `共 2 条`（等值匹配，不含 `beta gamma`） |
| between（日期） | 到期日 + 介于 + DatePicker 2026-03-01 / 2026-12-31 | 渲染 2 个 `.ant-picker`，`共 3 条`，Tag `到期日 介于 2026-03-01 ~ 2026-12-31` |
| 标量日期比较 | 到期日 + 大于 + DatePicker 2026-06-01 | 渲染 1 个 `.ant-picker`，`共 2 条`，Tag `到期日 大于 2026-06-01` |
| API 对账 | `memo in [beta,omega]`、`due_date between [2026-03-01,2026-12-31]` | `total=2` / `total=3`，与 UI 一致 |

用例结束自动清理数据、表单定义与表定义。

### 3.3 过程中发现的环境问题（非产品缺陷）

antd Select 的 `role="option"` 节点位于 `height:0;width:0;overflow:hidden` 的无障碍列表中，Playwright `getByRole('option')` 会解析到不可点击元素；用例改为点击下拉面板内可见的 `.ant-select-item-option`，并通过 Select 外层容器触发展开（有选中值时内部 input 被选中项遮挡）。该问题仅影响自动化定位，浏览器手动操作正常。

## 四、遗留

- 日期/时间区间与标量比较均已接 `DatePicker`（`datetime` 开启 `showTime`）；`in` 的日期/时间集合仍为标签文本输入（需多值选择器时可再迭代）。
- 应用未配置 antd `ConfigProvider` 中文 locale，DatePicker 面板文案沿用全局默认（英文）；如后续统一站点语言，可一处接入。
- `number` 的 `in` 使用标签输入，未做可视化数值校验提示（提交前统一转换）。
- 条件组合仅 AND；深分页上限、多版本合并内存排序等后端约束不变，见[上一轮报告](2026-10-26-p5-form-dynamic-filter.md)第五章。
