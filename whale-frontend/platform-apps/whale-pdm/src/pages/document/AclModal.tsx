// 图文档 ACL 弹窗：目录/文档共用，维护主体、动作与效果

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Table,
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  deleteAcl,
  listAcl,
  saveAcl,
  type AclAction,
  type AclEffect,
  type AclResourceType,
  type AclSubjectType,
  type PdmDocAcl,
} from '../../api/document';
import { hasPermission } from '../../utils/permission';
import { showError } from './utils';

/** 主体类型文案 */
const SUBJECT_LABEL: Record<AclSubjectType, string> = {
  ALL: '全部用户',
  USER: '用户',
  ROLE: '角色',
};

/** 动作文案 */
const ACTION_LABEL: Record<AclAction, string> = {
  VIEW: '查看',
  EDIT: '编辑',
  DOWNLOAD: '下载',
  DELETE: '删除',
  MANAGE: '权限管理',
};

/** 主体类型选项 */
const SUBJECT_OPTIONS = [
  { value: 'USER' as AclSubjectType, label: SUBJECT_LABEL.USER },
  { value: 'ROLE' as AclSubjectType, label: SUBJECT_LABEL.ROLE },
  { value: 'ALL' as AclSubjectType, label: SUBJECT_LABEL.ALL },
];

/** 动作选项 */
const ACTION_OPTIONS = (Object.keys(ACTION_LABEL) as AclAction[]).map((value) => ({
  value,
  label: ACTION_LABEL[value],
}));

/** 效果选项 */
const EFFECT_OPTIONS = [
  { value: 'ALLOW' as AclEffect, label: '允许' },
  { value: 'DENY' as AclEffect, label: '拒绝' },
];

/** 新增规则表单值 */
interface AclFormValues {
  subjectType: AclSubjectType;
  subjectId?: string;
  action: AclAction;
  effect: AclEffect;
}

interface AclModalProps {
  open: boolean;
  resourceType: AclResourceType;
  /** 资源 ID；未选择时为 undefined，弹窗不加载 */
  resourceId?: number;
  title: string;
  onClose: () => void;
  /** 规则变更后回调（刷新文档列表等） */
  onChanged?: () => void;
}

export default function AclModal({
  open,
  resourceType,
  resourceId,
  title,
  onClose,
  onChanged,
}: AclModalProps) {
  const [rules, setRules] = useState<PdmDocAcl[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<AclFormValues>();
  const canManage = hasPermission('sys:pdm:doc:acl:manage');
  const subjectType = Form.useWatch('subjectType', form) ?? 'USER';

  const loadRules = useCallback(async () => {
    if (!open || resourceId === undefined) {
      return;
    }
    setLoading(true);
    try {
      setRules(await listAcl(resourceType, resourceId));
    } catch (error) {
      showError(error);
    } finally {
      setLoading(false);
    }
  }, [open, resourceId, resourceType]);

  useEffect(() => {
    if (!open) {
      setRules([]);
      form.resetFields();
      form.setFieldsValue({ subjectType: 'USER', action: 'VIEW', effect: 'DENY' });
      return;
    }
    form.setFieldsValue({ subjectType: 'USER', action: 'VIEW', effect: 'DENY' });
    void loadRules();
  }, [open, form, loadRules]);

  const handleAdd = async () => {
    if (resourceId === undefined) {
      message.warning('请先选择目录或文档');
      return;
    }
    const values = await form.validateFields();
    const normalizedSubjectId =
      values.subjectType === 'ALL' ? '0' : (values.subjectId ?? '').trim();
    if (values.subjectType !== 'ALL' && !normalizedSubjectId) {
      message.warning('请输入主体标识');
      return;
    }
    setSaving(true);
    try {
      await saveAcl({
        resourceType,
        resourceId,
        subjectType: values.subjectType,
        subjectId: normalizedSubjectId,
        action: values.action,
        effect: values.effect,
      });
      message.success('权限规则已保存');
      form.setFieldsValue({ subjectId: undefined });
      await loadRules();
      onChanged?.();
    } catch (error) {
      showError(error);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteAcl(id);
      message.success('权限规则已删除');
      await loadRules();
      onChanged?.();
    } catch (error) {
      showError(error);
    }
  };

  const columns: TableProps<PdmDocAcl>['columns'] = [
    {
      title: '主体',
      dataIndex: 'subjectType',
      key: 'subjectType',
      width: 110,
      render: (value: AclSubjectType) => SUBJECT_LABEL[value] ?? value,
    },
    { title: '主体标识', dataIndex: 'subjectId', key: 'subjectId', width: 150 },
    {
      title: '动作',
      dataIndex: 'action',
      key: 'action',
      width: 110,
      render: (value: AclAction) => ACTION_LABEL[value] ?? value,
    },
    {
      title: '效果',
      dataIndex: 'effect',
      key: 'effect',
      width: 90,
      render: (value: AclEffect) =>
        value === 'DENY' ? <Tag color="red">拒绝</Tag> : <Tag color="green">允许</Tag>,
    },
    {
      title: '操作',
      key: 'actionButton',
      width: 90,
      render: (_, record) =>
        canManage ? (
          <Popconfirm
            title="删除权限规则"
            description="删除后该主体不再受此规则限制，确认继续？"
            onConfirm={() => void handleDelete(record.id)}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        ) : (
          '-'
        ),
    },
  ];

  return (
    <Modal
      open={open}
      title={`权限设置：${title}`}
      onCancel={onClose}
      width={760}
      destroyOnClose
      footer={<Button onClick={onClose}>关闭</Button>}
    >
      {canManage ? (
        <Card size="small" title="新增规则" style={{ marginBottom: 12 }}>
          <Form
            form={form}
            layout="inline"
            initialValues={{ subjectType: 'USER', action: 'VIEW', effect: 'DENY' }}
          >
            <Form.Item name="subjectType" label="主体类型">
              <Select
                style={{ width: 120 }}
                options={SUBJECT_OPTIONS}
                onChange={(value: AclSubjectType) => {
                  if (value === 'ALL') {
                    form.setFieldsValue({ subjectId: '0' });
                  }
                }}
              />
            </Form.Item>
            <Form.Item
              name="subjectId"
              label="主体标识"
              rules={[
                {
                  required: true,
                  message: '请输入用户ID或角色编码',
                },
              ]}
            >
              <Input
                style={{ width: 180 }}
                placeholder={subjectType === 'USER' ? '用户ID，如 7' : '角色编码，如 pdm_member'}
                disabled={subjectType === 'ALL'}
                maxLength={64}
              />
            </Form.Item>
            <Form.Item name="action" label="动作">
              <Select style={{ width: 120 }} options={ACTION_OPTIONS} />
            </Form.Item>
            <Form.Item name="effect" label="效果">
              <Select style={{ width: 100 }} options={EFFECT_OPTIONS} />
            </Form.Item>
            <Form.Item>
              <Button type="primary" loading={saving} onClick={() => void handleAdd()}>
                添加
              </Button>
            </Form.Item>
          </Form>
        </Card>
      ) : null}
      {rules.length > 0 ? (
        <Table<PdmDocAcl>
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rules}
          pagination={false}
        />
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={loading ? '加载中' : '暂无权限规则，默认按功能权限与属主规则执行'}
        />
      )}
    </Modal>
  );
}
