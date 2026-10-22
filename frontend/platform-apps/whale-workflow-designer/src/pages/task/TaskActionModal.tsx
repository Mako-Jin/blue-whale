// 任务办理弹窗：按动作类型渲染转办 / 委派 / 回退 / 处理完成 / 加签的入参表单。

import { AutoComplete, Form, Input, Modal, type FormInstance } from 'antd';
import { ACTION_TITLES, type ActionFormValues, type TaskAction } from './constants';

interface TaskActionModalProps {
  action: TaskAction | null;
  form: FormInstance<ActionFormValues>;
  submitting: boolean;
  /** 回退目标节点候选 */
  rollbackOptions: { value: string; label: string }[];
  rollbackLoading: boolean;
  onCancel: () => void;
  onSubmit: () => void;
}

export default function TaskActionModal({
  action,
  form,
  submitting,
  rollbackOptions,
  rollbackLoading,
  onCancel,
  onSubmit,
}: TaskActionModalProps) {
  return (
    <Modal
      title={action ? ACTION_TITLES[action] : ''}
      open={action !== null}
      onCancel={onCancel}
      onOk={onSubmit}
      confirmLoading={submitting}
      okText="确定"
      cancelText="取消"
      forceRender
    >
      <Form<ActionFormValues> form={form} layout="vertical" preserve={false}>
        {action === 'transfer' || action === 'delegate' ? (
          <Form.Item
            name="targetUser"
            label="目标人账号"
            rules={[{ required: true, message: '请输入目标人账号' }]}
          >
            <Input placeholder="工号或用户名" maxLength={64} />
          </Form.Item>
        ) : null}

        {action === 'rollback' ? (
          <Form.Item
            name="targetNodeKey"
            label="回退目标节点"
            rules={[{ required: true, message: '请选择或输入目标节点 key' }]}
            extra={
              rollbackLoading
                ? '节点列表加载中…'
                : '可从流程定义中选择审批节点，也可直接输入节点 key'
            }
          >
            <AutoComplete
              options={rollbackOptions}
              optionFilterProp="label"
              placeholder="选择或输入节点 key"
            >
              <Input maxLength={64} />
            </AutoComplete>
          </Form.Item>
        ) : null}

        {action === 'resolve' ? (
          <Form.Item name="comment" label="处理意见">
            <Input.TextArea rows={3} placeholder="可选" maxLength={255} />
          </Form.Item>
        ) : null}

        {action === 'addSign' ? (
          <Form.Item
            name="assignees"
            label="加签人账号"
            rules={[{ required: true, message: '请输入加签人账号' }]}
            extra="多个账号用逗号或换行分隔，单次最多 10 人；加签人全部同意后节点才完成"
          >
            <Input.TextArea rows={3} placeholder="如：zhangsan, lisi" maxLength={700} />
          </Form.Item>
        ) : null}
      </Form>
    </Modal>
  );
}
