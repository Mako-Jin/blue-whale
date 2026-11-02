// 系统公告管理页 - 列表（分页 / 发布 / 撤回 / 删除）
// 公告面向当前租户全体用户：发布不枚举接收人，用户收件箱读取时惰性展开
// 操作按钮均按权限码渲染（AuthButton / usePermission），无权限自动隐藏

import { App, Button, Form, Input, Popconfirm, Select, Space, Tag, type TableProps } from 'antd';
import { useRef, useState } from 'react';
import {
  AuthButton,
  FormModal,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  deleteAnnouncement,
  pageAnnouncements,
  publishAnnouncement,
  revokeAnnouncement,
  type AnnouncementStatus,
  type AnnouncementVO,
} from '../../api/message';

interface AnnouncementFormValues {
  title: string;
  content: string;
  bizType?: string;
}

type AnnouncementQuery = { status?: AnnouncementStatus };

const STATUS_OPTIONS = [
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'REVOKED', label: '已撤回' },
];

/** 状态文案与标签颜色 */
const STATUS_META: Record<AnnouncementStatus, { label: string; color: string }> = {
  PUBLISHED: { label: '已发布', color: 'green' },
  REVOKED: { label: '已撤回', color: 'default' },
};

export default function AnnouncementList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [status, setStatus] = useState<AnnouncementStatus | undefined>(undefined);

  const [editorOpen, setEditorOpen] = useState(false);
  const [editorForm] = Form.useForm<AnnouncementFormValues>();

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const handleRevoke = async (row: AnnouncementVO) => {
    try {
      await revokeAnnouncement(row.id);
      message.success('公告已撤回');
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '撤回失败');
    }
  };

  const handleDelete = async (row: AnnouncementVO) => {
    try {
      await deleteAnnouncement(row.id);
      message.success('公告已删除');
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '删除失败');
    }
  };

  const handleSubmit = async (values: AnnouncementFormValues) => {
    try {
      await publishAnnouncement({
        title: values.title,
        content: values.content,
        bizType: values.bizType || undefined,
      });
      message.success('公告已发布');
      setEditorOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '发布失败');
      throw error;
    }
  };

  const columns: TableProps<AnnouncementVO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 180 },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    {
      title: '正文',
      dataIndex: 'content',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '业务类型',
      dataIndex: 'bizType',
      width: 160,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: AnnouncementStatus | undefined) => {
        const meta = value ? STATUS_META[value] : undefined;
        return meta ? <Tag color={meta.color}>{meta.label}</Tag> : '-';
      },
    },
    {
      title: '发布人',
      dataIndex: 'publisherId',
      width: 120,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '发布时间',
      dataIndex: 'publishTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, row) => (
        <Space size={4} wrap>
          {row.status === 'PUBLISHED' && (
            <AuthButton
              perm="sys:message:announce:revoke"
              type="link"
              size="small"
              onClick={() => void handleRevoke(row)}
            >
              撤回
            </AuthButton>
          )}
          {has('sys:message:announce:delete') && (
            <Popconfirm
              title="确认删除该公告？"
              description="已展开的个人消息不受影响"
              onConfirm={() => void handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<AnnouncementVO, AnnouncementQuery>
        ref={tableRef}
        rowKey="id"
        title="系统公告"
        columns={columns}
        request={pageAnnouncements}
        params={{ status }}
        onError={(error) => showError(error, '公告列表加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'status',
                label: '状态',
                element: (
                  <Select
                    allowClear
                    options={STATUS_OPTIONS}
                    placeholder="全部"
                    style={{ width: 140 }}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setStatus((values.status as AnnouncementStatus | undefined) || undefined);
            }}
            onReset={() => {
              setStatus(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton
            perm="sys:message:announce:publish"
            type="primary"
            onClick={() => {
              editorForm.resetFields();
              setEditorOpen(true);
            }}
          >
            发布公告
          </AuthButton>
        }
      />

      <FormModal<AnnouncementFormValues>
        open={editorOpen}
        title="发布系统公告"
        form={editorForm}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item
          name="title"
          label="公告标题"
          rules={[
            { required: true, message: '请输入公告标题' },
            { max: 128, message: '公告标题不能超过 128 字' },
          ]}
        >
          <Input placeholder="如 平台维护通知" />
        </Form.Item>
        <Form.Item
          name="content"
          label="公告正文"
          rules={[{ required: true, message: '请输入公告正文' }]}
        >
          <Input.TextArea rows={5} placeholder="面向本租户全体用户的公告内容" />
        </Form.Item>
        <Form.Item
          name="bizType"
          label="业务类型"
          extra="可选，缺省为 SYSTEM_ANNOUNCE；用于前端区分公告与定向站内信"
          rules={[{ max: 32, message: '业务类型不能超过 32 字' }]}
        >
          <Input placeholder="SYSTEM_ANNOUNCE" />
        </Form.Item>
      </FormModal>
    </>
  );
}
