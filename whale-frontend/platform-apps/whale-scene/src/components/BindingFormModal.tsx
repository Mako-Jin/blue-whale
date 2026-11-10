// 节点入口绑定弹窗：一个节点一个入口，重复保存即覆盖
//
// 入口地址由后端发布校验做协议/内网白名单；前端只做必填与长度提示。

import { App, Form, Input, Modal, Select, Switch } from 'antd';
import { useEffect, useState } from 'react';
import {
  saveNodeBinding,
  type EntryType,
  type NodeBindingSaveCommand,
  type NodeBindingView,
  type SceneNodeView,
} from '../api/scene';

interface BindingFormModalProps {
  open: boolean;
  node: SceneNodeView | null;
  binding: NodeBindingView | null;
  onCancel: () => void;
  onSaved: () => void;
}

const ENTRY_TYPE_OPTIONS = [
  { label: '业务系统', value: 'SYSTEM' },
  { label: '页面路由', value: 'PAGE' },
  { label: '知识库', value: 'KNOWLEDGE' },
  { label: '外部链接', value: 'EXTERNAL' },
];

export default function BindingFormModal({
  open,
  node,
  binding,
  onCancel,
  onSaved,
}: BindingFormModalProps) {
  const { message } = App.useApp();
  const [form] = Form.useForm<NodeBindingSaveCommand>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    form.setFieldsValue(
      binding
        ? {
            entryType: binding.entryType,
            targetPath: binding.targetPath,
            permissionCode: binding.permissionCode ?? undefined,
            enabled: binding.enabled ?? true,
          }
        : {
            entryType: 'SYSTEM' as EntryType,
            targetPath: '',
            permissionCode: undefined,
            enabled: true,
          },
    );
  }, [open, binding, form]);

  const handleOk = async () => {
    if (!node) {
      return;
    }
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await saveNodeBinding(node.id, values);
      message.success('入口已保存');
      onSaved();
      onCancel();
    } catch (reason) {
      message.error(reason instanceof Error ? reason.message : '入口保存失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={node ? `设置入口：${node.name}` : '设置入口'}
      open={open}
      confirmLoading={submitting}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form<NodeBindingSaveCommand> form={form} layout="vertical">
        <Form.Item name="entryType" label="入口类型" rules={[{ required: true, message: '请选择入口类型' }]}>
          <Select options={ENTRY_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item
          name="targetPath"
          label="入口地址"
          extra="仅允许平台内 `/` 路径或 http/https 外链；发布时后端会拒绝内网地址与危险协议。"
          rules={[
            { required: true, message: '请输入入口地址' },
            { max: 512, message: '入口地址不能超过 512 个字符' },
          ]}
        >
          <Input placeholder="例如 /oa 或 https://example.com" />
        </Form.Item>
        <Form.Item
          name="permissionCode"
          label="所需权限码"
          rules={[{ max: 128, message: '权限码不能超过 128 个字符' }]}
          extra="例如 sys:oa:leave:list；留空表示登录用户均可见。"
        >
          <Input placeholder="sys:oa:leave:list" />
        </Form.Item>
        <Form.Item name="enabled" label="是否启用" valuePropName="checked">
          <Switch />
        </Form.Item>
      </Form>
    </Modal>
  );
}
