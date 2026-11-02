// 空间成员管理：查看成员、批量添加、调整角色、移除

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Form,
  Input,
  List,
  Modal,
  Popconfirm,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd';
import { spaceApi } from '../api/space';
import type { DocMember, DocMemberRole, DocSpace } from '../api/types';

const { Text } = Typography;

/** 角色文案 */
const ROLE_LABEL: Record<DocMemberRole, string> = {
  OWNER: '所有者',
  ADMIN: '管理员',
  EDITOR: '编辑者',
  READER: '阅读者',
};

/** 可选角色 */
const ROLE_OPTIONS = (['ADMIN', 'EDITOR', 'READER'] as DocMemberRole[]).map((value) => ({
  value,
  label: ROLE_LABEL[value],
}));

/** 成员面板属性 */
interface MemberPanelProps {
  space: DocSpace | null;
  open: boolean;
  onClose: () => void;
}

export default function MemberPanel({ space, open, onClose }: MemberPanelProps) {
  const { message } = App.useApp();
  const [form] = Form.useForm<{ userIds: string; role: DocMemberRole }>();
  const [members, setMembers] = useState<DocMember[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const isOwner = space?.memberRole === 'OWNER';
  const canManage = isOwner || space?.memberRole === 'ADMIN';

  const load = useCallback(async () => {
    if (!space) {
      return;
    }
    setLoading(true);
    try {
      setMembers(await spaceApi.members(space.id));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [space, message]);

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue({ role: 'EDITOR' });
      void load();
    }
  }, [open, form, load]);

  const addMembers = async () => {
    if (!space) {
      return;
    }
    const values = await form.validateFields();
    const userIds = values.userIds
      .split(/[,，\s]+/)
      .map((text) => Number(text.trim()))
      .filter((value) => Number.isFinite(value) && value > 0);
    if (userIds.length === 0) {
      message.warning('请输入至少一个用户ID');
      return;
    }
    setSubmitting(true);
    try {
      await spaceApi.addMembers(space.id, userIds, values.role);
      message.success('成员已添加');
      form.resetFields();
      form.setFieldsValue({ role: 'EDITOR' });
      await load();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  const changeRole = async (member: DocMember, role: DocMemberRole) => {
    if (!space) {
      return;
    }
    try {
      await spaceApi.updateMember(space.id, member.id, role);
      message.success('角色已更新');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const removeMember = async (member: DocMember) => {
    if (!space) {
      return;
    }
    try {
      await spaceApi.removeMember(space.id, member.id);
      message.success('成员已移除');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  return (
    <Modal
      title={`成员管理 · ${space?.name ?? ''}`}
      open={open}
      onCancel={onClose}
      footer={null}
      width={640}
    >
      {canManage && (
        <Form form={form} layout="inline" style={{ marginBottom: 16, rowGap: 8 }}>
          <Form.Item
            name="userIds"
            rules={[{ required: true, message: '请输入用户ID' }]}
            style={{ flex: 1, minWidth: 220 }}
          >
            <Input placeholder="用户ID，多个用逗号分隔" />
          </Form.Item>
          <Form.Item name="role" rules={[{ required: true }]}>
            <Select options={ROLE_OPTIONS} style={{ width: 120 }} />
          </Form.Item>
          <Button type="primary" loading={submitting} onClick={() => void addMembers()}>
            添加成员
          </Button>
        </Form>
      )}
      <List
        loading={loading}
        dataSource={members}
        locale={{ emptyText: '暂无成员' }}
        renderItem={(member) => (
          <List.Item
            actions={
              canManage && member.memberRole !== 'OWNER'
                ? [
                    <Select
                      key="role"
                      size="small"
                      value={member.memberRole}
                      style={{ width: 100 }}
                      options={
                        isOwner
                          ? [...ROLE_OPTIONS, { value: 'OWNER', label: ROLE_LABEL.OWNER }]
                          : ROLE_OPTIONS
                      }
                      onChange={(role) => void changeRole(member, role as DocMemberRole)}
                    />,
                    <Popconfirm
                      key="remove"
                      title="移除该成员？"
                      okText="移除"
                      onConfirm={() => void removeMember(member)}
                    >
                      <Button size="small" danger type="link">
                        移除
                      </Button>
                    </Popconfirm>,
                  ]
                : undefined
            }
          >
            <Space direction="vertical" size={2}>
              <Space>
                <Text strong>用户 {member.userId}</Text>
                <Tag color={member.memberRole === 'OWNER' ? 'gold' : 'blue'}>
                  {ROLE_LABEL[member.memberRole]}
                </Tag>
              </Space>
              <Text type="secondary">加入时间：{member.createTime ?? '-'}</Text>
            </Space>
          </List.Item>
        )}
      />
      {!canManage && <Text type="secondary">仅空间管理员可调整成员。</Text>}
    </Modal>
  );
}
