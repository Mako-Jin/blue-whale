// 新建会话：单聊（1 位成员）/ 群聊（标题 + 成员）

import { useState } from 'react';
import { Form, Input, Modal, Segmented } from 'antd';

interface NewConversationModalProps {
  open: boolean;
  onCancel: () => void;
  onCreate: (payload: {
    type: 'SINGLE' | 'GROUP';
    title?: string;
    memberIds: number[];
  }) => Promise<boolean>;
}

/** 解析用户ID输入：支持逗号/空格/中文逗号分隔 */
function parseMemberIds(text: string): number[] {
  return text
    .split(/[\s,，;；]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => Number(item))
    .filter((value) => Number.isInteger(value) && value > 0);
}

export default function NewConversationModal({
  open,
  onCancel,
  onCreate,
}: NewConversationModalProps) {
  const [type, setType] = useState<'SINGLE' | 'GROUP'>('SINGLE');
  const [title, setTitle] = useState('');
  const [members, setMembers] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const reset = () => {
    setType('SINGLE');
    setTitle('');
    setMembers('');
  };

  const handleOk = async () => {
    const memberIds = parseMemberIds(members);
    setSubmitting(true);
    const created = await onCreate({
      type,
      title: type === 'GROUP' ? title.trim() : undefined,
      memberIds,
    });
    setSubmitting(false);
    if (created) {
      reset();
      onCancel();
    }
  };

  const memberIds = parseMemberIds(members);
  const invalid =
    memberIds.length === 0 ||
    (type === 'SINGLE' && memberIds.length !== 1) ||
    (type === 'GROUP' && !title.trim());

  return (
    <Modal
      open={open}
      title="新建会话"
      okText="创建"
      cancelText="取消"
      confirmLoading={submitting}
      okButtonProps={{ disabled: invalid }}
      onOk={() => {
        void handleOk();
      }}
      onCancel={() => {
        reset();
        onCancel();
      }}
      destroyOnClose
    >
      <Form layout="vertical">
        <Form.Item label="会话类型">
          <Segmented
            value={type}
            onChange={(value) => setType(value as 'SINGLE' | 'GROUP')}
            options={[
              { label: '单聊', value: 'SINGLE' },
              { label: '群聊', value: 'GROUP' },
            ]}
          />
        </Form.Item>
        {type === 'GROUP' && (
          <Form.Item label="群名称" required>
            <Input
              maxLength={128}
              placeholder="请输入群名称"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
            />
          </Form.Item>
        )}
        <Form.Item
          label="成员用户ID"
          required
          extra={type === 'SINGLE' ? '单聊仅需 1 位对方用户ID' : '多个 ID 用逗号或空格分隔'}
        >
          <Input
            placeholder="例如：1001,1002"
            value={members}
            onChange={(event) => setMembers(event.target.value)}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
