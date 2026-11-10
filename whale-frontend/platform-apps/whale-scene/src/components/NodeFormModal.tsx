// 场景节点编辑弹窗：新增 / 编辑区域、建筑、入口节点
//
// 坐标 x / y 取值 0–1000，由 2D 地图按百分比渲染；z 为 M3 3D 增强字段，
// M2 界面不暴露，避免用户录入无消费方的数据。

import { App, Form, Input, InputNumber, Modal, Select } from 'antd';
import { useEffect, useState } from 'react';
import {
  createSceneNode,
  updateSceneNode,
  type SceneNodeSaveCommand,
  type SceneNodeType,
  type SceneNodeView,
  type SceneView,
} from '../api/scene';

interface NodeFormModalProps {
  open: boolean;
  scene: SceneView;
  nodes: SceneNodeView[];
  node: SceneNodeView | null;
  onCancel: () => void;
  onSaved: () => void;
}

const NODE_TYPE_OPTIONS = [
  { label: '区域', value: 'REGION' },
  { label: '建筑', value: 'BUILDING' },
  { label: '入口', value: 'ENTRANCE' },
];

export default function NodeFormModal({
  open,
  scene,
  nodes,
  node,
  onCancel,
  onSaved,
}: NodeFormModalProps) {
  const { message } = App.useApp();
  const [form] = Form.useForm<SceneNodeSaveCommand>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    form.setFieldsValue(
      node
        ? {
            nodeKey: node.nodeKey,
            parentKey: node.parentKey ?? undefined,
            nodeType: node.nodeType,
            name: node.name,
            icon: node.icon ?? undefined,
            x: node.x ?? undefined,
            y: node.y ?? undefined,
            sortNo: node.sortNo ?? 0,
          }
        : {
            nodeKey: '',
            parentKey: undefined,
            nodeType: 'REGION' as SceneNodeType,
            name: '',
            icon: undefined,
            x: 500,
            y: 500,
            sortNo: 0,
          },
    );
  }, [open, node, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (node) {
        await updateSceneNode(scene.id, node.id, values);
        message.success('节点已更新');
      } else {
        await createSceneNode(scene.id, values);
        message.success('节点已新增');
      }
      onSaved();
      onCancel();
    } catch (reason) {
      message.error(reason instanceof Error ? reason.message : '节点保存失败');
    } finally {
      setSubmitting(false);
    }
  };

  const parentOptions = nodes
    .filter((item) => item.nodeKey !== node?.nodeKey)
    .map((item) => ({ label: `${item.name}（${item.nodeKey}）`, value: item.nodeKey }));

  return (
    <Modal
      title={node ? `编辑节点：${node.name}` : '新增场景节点'}
      open={open}
      confirmLoading={submitting}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form<SceneNodeSaveCommand> form={form} layout="vertical">
        <Form.Item
          name="nodeKey"
          label="节点标识"
          rules={[
            { required: true, message: '请输入节点标识' },
            { max: 64, message: '节点标识不能超过 64 个字符' },
          ]}
        >
          <Input placeholder="例如 oa-hall（场景内唯一）" />
        </Form.Item>
        <Form.Item name="parentKey" label="父节点">
          <Select allowClear showSearch optionFilterProp="label" options={parentOptions} placeholder="不选则为根节点" />
        </Form.Item>
        <Form.Item name="nodeType" label="节点类型" rules={[{ required: true, message: '请选择节点类型' }]}>
          <Select options={NODE_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item
          name="name"
          label="节点名称"
          rules={[
            { required: true, message: '请输入节点名称' },
            { max: 64, message: '节点名称不能超过 64 个字符' },
          ]}
        >
          <Input placeholder="例如 协同办公" />
        </Form.Item>
        <Form.Item name="icon" label="图标标识" rules={[{ max: 64, message: '图标标识不能超过 64 个字符' }]}>
          <Input placeholder="例如 oa / erp / doc，用于 2D 地图展示" />
        </Form.Item>
        <Form.Item name="x" label="2D 横坐标（0–1000）">
          <InputNumber min={0} max={1000} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="y" label="2D 纵坐标（0–1000）">
          <InputNumber min={0} max={1000} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="sortNo" label="同级排序号">
          <InputNumber min={0} max={9999} style={{ width: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
