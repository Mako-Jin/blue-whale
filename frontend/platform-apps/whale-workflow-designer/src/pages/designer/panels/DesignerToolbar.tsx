// 设计器顶栏：撤销 / 重做、保存 / 校验、分组 / 版本、模拟 / BPMN 导入导出、返回列表。
// 纯展示组件：按钮可用性由页面状态决定，点击回调上抛。

import { Button, Space, Tooltip } from 'antd';

interface DesignerToolbarProps {
  canUndo: boolean;
  canRedo: boolean;
  /** 保存中 */
  saving: boolean;
  /** 校验中 */
  validating: boolean;
  /** 已保存（有定义 id）才允许导出 BPMN / 版本管理 */
  saved: boolean;
  onUndo: () => void;
  onRedo: () => void;
  onSave: () => void;
  onValidate: () => void;
  onOpenGroup: () => void;
  onOpenVersions: () => void;
  onOpenSimulate: () => void;
  onOpenBpmnImport: () => void;
  onExportBpmn: () => void;
  onBack: () => void;
}

export default function DesignerToolbar({
  canUndo,
  canRedo,
  saving,
  validating,
  saved,
  onUndo,
  onRedo,
  onSave,
  onValidate,
  onOpenGroup,
  onOpenVersions,
  onOpenSimulate,
  onOpenBpmnImport,
  onExportBpmn,
  onBack,
}: DesignerToolbarProps) {
  return (
    <Space wrap>
      <Tooltip title="撤销（Ctrl+Z）">
        <Button disabled={!canUndo} onClick={onUndo}>
          撤销
        </Button>
      </Tooltip>
      <Tooltip title="重做（Ctrl+Y / Ctrl+Shift+Z）">
        <Button disabled={!canRedo} onClick={onRedo}>
          重做
        </Button>
      </Tooltip>
      <Button type="primary" loading={saving} onClick={onSave}>
        保存
      </Button>
      <Button loading={validating} onClick={onValidate}>
        校验
      </Button>
      <Button onClick={onOpenGroup}>分组</Button>
      <Button disabled={!saved} onClick={onOpenVersions}>
        版本
      </Button>
      <Button onClick={onOpenSimulate}>模拟</Button>
      <Button onClick={onOpenBpmnImport}>导入 BPMN</Button>
      <Button disabled={!saved} onClick={onExportBpmn}>
        导出 BPMN
      </Button>
      <Button onClick={onBack}>返回列表</Button>
    </Space>
  );
}
