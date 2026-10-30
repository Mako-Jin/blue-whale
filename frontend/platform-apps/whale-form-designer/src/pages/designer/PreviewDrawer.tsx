// 表单预览抽屉（F6）：以交互态渲染当前画布字段，仅用于检验布局与控件。
// 从 FormDesigner 拆出的视图组件：只负责预览展示，画布状态由容器组件传入。

import { Drawer } from 'antd';
import FormRenderer from '../../render/FormRenderer';
import { getToken } from '../../api/token';
import type { DesignerField } from './types';

interface PreviewDrawerProps {
  open: boolean;
  onClose: () => void;
  /** 当前画布字段（实时预览） */
  fields: DesignerField[];
}

export default function PreviewDrawer({ open, onClose, fields }: PreviewDrawerProps) {
  return (
    <Drawer
      open={open}
      width={720}
      title="表单预览"
      onClose={onClose}
      destroyOnClose
    >
      <FormRenderer
        fields={fields}
        authToken={getToken() ?? undefined}
        apiBaseUrl={import.meta.env.VITE_API_BASE_URL ?? ''}
      />
      <div style={{ marginTop: 8, color: '#999' }}>
        预览为交互态，仅用于检验布局与控件；实际提交以填报页为准。
      </div>
    </Drawer>
  );
}
