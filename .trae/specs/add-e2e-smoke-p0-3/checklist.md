# Checklist：P0-3 E2E 冒烟（第一批）

## 功能
- [x] `frontend/e2e` 为独立 workspace 包，`pnpm e2e` 可从 `frontend/` 根触发
- [x] webServer 自动拉起 8001/8002，已有实例时复用
- [x] 前置服务缺失时 global setup 给出明确失败信息（gateway/auth/form）
- [x] 用例 A 覆盖：未登录跳转、登录成功、菜单与 `/auth/me` 一致、子菜单可进入
- [x] 用例 B 覆盖：建表、加列、发布、填报、数据列表、详情值

## 质量
- [x] 无硬编码数据库/Redis/Nacos 凭据
- [x] 用例使用唯一编码，可重复执行
- [x] 失败时保留 trace 与截图
- [x] `lint`、`format:check` 通过

## 验证
- [x] `pnpm e2e` 一次通过（2 passed）
- [x] 反向验证：改坏菜单权限渲染 → 用例 A 失败 → 恢复通过
- [x] devlog / 整改清单已更新
- [x] 代码已提交并推送 Gitee
