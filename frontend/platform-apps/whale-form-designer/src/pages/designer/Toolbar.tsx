// 设计器顶栏（F6）：编码/名称/所属表定义元信息编辑 + 撤销/重做/保存/发布/预览/填报/返回。
// 从 FormDesigner 拆出的视图组件：只负责展示与交互，状态与动作由容器组件传入。

import { Button, Card, Col, Input, Row, Select, Space } from 'antd';
import type { Id } from '../../api/form';

interface ToolbarProps {
  /** 表单编码（顶栏输入框受控值） */
  code: string;
  /** 表单名称（顶栏输入框受控值） */
  name: string;
  /** 所属表定义 ID */
  tableId: Id | null;
  /** 「所属表定义」下拉数据 */
  tableOptions: { value: Id; label: string }[];
  /** 保存中：保存按钮 loading */
  saving: boolean;
  /** 发布中：发布按钮 loading */
  publishing: boolean;
  /** 撤销 / 重做可用性（历史栈非空） */
  canUndo: boolean;
  canRedo: boolean;
  onCodeChange: (value: string) => void;
  onNameChange: (value: string) => void;
  /** 切换所属表定义（容器内含清理确认） */
  onTableChange: (value: Id) => void;
  onUndo: () => void;
  onRedo: () => void;
  /** 保存（容器内含校验与新建后路由切换） */
  onSave: () => void;
  /** 发布（容器内含先保存后发布的语义） */
  onPublish: () => void;
  onPreview: () => void;
  onRender: () => void;
  onBack: () => void;
}

export default function Toolbar({
  code,
  name,
  tableId,
  tableOptions,
  saving,
  publishing,
  canUndo,
  canRedo,
  onCodeChange,
  onNameChange,
  onTableChange,
  onUndo,
  onRedo,
  onSave,
  onPublish,
  onPreview,
  onRender,
  onBack,
}: ToolbarProps) {
  return (
    <Card size="small">
      <Row gutter={12} align="middle">
        <Col span={5}>
          <Input
            addonBefore="编码"
            value={code}
            onChange={(event) => onCodeChange(event.target.value)}
          />
        </Col>
        <Col span={5}>
          <Input
            addonBefore="名称"
            value={name}
            onChange={(event) => onNameChange(event.target.value)}
          />
        </Col>
        <Col span={7}>
          <Select
            style={{ width: '100%' }}
            placeholder="所属表定义"
            value={tableId ?? undefined}
            onChange={onTableChange}
            options={tableOptions}
            showSearch
            optionFilterProp="label"
          />
        </Col>
        <Col span={7}>
          <Space>
            <Button disabled={!canUndo} title="撤销（Ctrl+Z）" onClick={onUndo}>
              撤销
            </Button>
            <Button disabled={!canRedo} title="重做（Ctrl+Y / Ctrl+Shift+Z）" onClick={onRedo}>
              重做
            </Button>
            <Button type="primary" loading={saving} onClick={onSave}>
              保存
            </Button>
            <Button loading={publishing} onClick={onPublish}>
              发布
            </Button>
            <Button onClick={onPreview}>预览</Button>
            <Button onClick={onRender}>填报</Button>
            <Button onClick={onBack}>返回</Button>
          </Space>
        </Col>
      </Row>
    </Card>
  );
}
