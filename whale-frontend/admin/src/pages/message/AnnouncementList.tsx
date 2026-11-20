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
import { useI18n } from '../../i18n';

interface AnnouncementFormValues {
  title: string;
  content: string;
  bizType?: string;
}

type AnnouncementQuery = { status?: AnnouncementStatus };

export default function AnnouncementList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const { t } = useI18n();
  const tableRef = useRef<ProTableRef>(null);

  const statusOptions = [
    { value: 'PUBLISHED', label: t('message.announcement.status.published') },
    { value: 'REVOKED', label: t('message.announcement.status.revoked') },
  ];

  /** 状态文案与标签颜色 */
  const statusMeta: Record<AnnouncementStatus, { label: string; color: string }> = {
    PUBLISHED: { label: t('message.announcement.status.published'), color: 'green' },
    REVOKED: { label: t('message.announcement.status.revoked'), color: 'default' },
  };

  const [status, setStatus] = useState<AnnouncementStatus | undefined>(undefined);

  const [editorOpen, setEditorOpen] = useState(false);
  const [editorForm] = Form.useForm<AnnouncementFormValues>();

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const handleRevoke = async (row: AnnouncementVO) => {
    try {
      await revokeAnnouncement(row.id);
      message.success(t('message.announcement.msg.revoked'));
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('message.announcement.msg.revokeFailed'));
    }
  };

  const handleDelete = async (row: AnnouncementVO) => {
    try {
      await deleteAnnouncement(row.id);
      message.success(t('message.announcement.msg.deleted'));
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('message.announcement.msg.deleteFailed'));
    }
  };

  const handleSubmit = async (values: AnnouncementFormValues) => {
    try {
      await publishAnnouncement({
        title: values.title,
        content: values.content,
        bizType: values.bizType || undefined,
      });
      message.success(t('message.announcement.msg.published'));
      setEditorOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('message.announcement.msg.publishFailed'));
      throw error;
    }
  };

  const columns: TableProps<AnnouncementVO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 180 },
    { title: t('message.announcement.column.title'), dataIndex: 'title', ellipsis: true },
    {
      title: t('message.announcement.column.content'),
      dataIndex: 'content',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('message.announcement.column.bizType'),
      dataIndex: 'bizType',
      width: 160,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('message.announcement.column.status'),
      dataIndex: 'status',
      width: 100,
      render: (value: AnnouncementStatus | undefined) => {
        const meta = value ? statusMeta[value] : undefined;
        return meta ? <Tag color={meta.color}>{meta.label}</Tag> : '-';
      },
    },
    {
      title: t('message.announcement.column.publisher'),
      dataIndex: 'publisherId',
      width: 120,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('message.announcement.column.publishTime'),
      dataIndex: 'publishTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('message.announcement.column.action'),
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
              {t('message.announcement.action.revoke')}
            </AuthButton>
          )}
          {has('sys:message:announce:delete') && (
            <Popconfirm
              title={t('message.announcement.confirm.delete')}
              description={t('message.announcement.confirm.deleteDescription')}
              onConfirm={() => void handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                {t('message.announcement.action.delete')}
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
        title={t('message.announcement.title')}
        columns={columns}
        request={pageAnnouncements}
        params={{ status }}
        onError={(error) => showError(error, t('message.announcement.msg.loadFailed'))}
        search={
          <SearchForm
            items={[
              {
                name: 'status',
                label: t('message.announcement.column.status'),
                element: (
                  <Select
                    allowClear
                    options={statusOptions}
                    placeholder={t('message.announcement.search.all')}
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
            {t('message.announcement.action.publish')}
          </AuthButton>
        }
      />

      <FormModal<AnnouncementFormValues>
        open={editorOpen}
        title={t('message.announcement.publish.title')}
        form={editorForm}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item
          name="title"
          label={t('message.announcement.form.title')}
          rules={[
            { required: true, message: t('message.announcement.form.titleRequired') },
            { max: 128, message: t('message.announcement.form.titleMax') },
          ]}
        >
          <Input placeholder={t('message.announcement.form.titlePlaceholder')} />
        </Form.Item>
        <Form.Item
          name="content"
          label={t('message.announcement.form.content')}
          rules={[
            { required: true, message: t('message.announcement.form.contentRequired') },
          ]}
        >
          <Input.TextArea
            rows={5}
            placeholder={t('message.announcement.form.contentPlaceholder')}
          />
        </Form.Item>
        <Form.Item
          name="bizType"
          label={t('message.announcement.form.bizType')}
          extra={t('message.announcement.form.bizTypeExtra')}
          rules={[{ max: 32, message: t('message.announcement.form.bizTypeMax') }]}
        >
          <Input placeholder="SYSTEM_ANNOUNCE" />
        </Form.Item>
      </FormModal>
    </>
  );
}
