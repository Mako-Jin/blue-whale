// 消息模板管理页 - 列表（分页 / 新增 / 编辑 / 启停 / 删除）
// 操作按钮均按权限码渲染（AuthButton / usePermission），无权限自动隐藏

import { App, Button, Form, Input, Popconfirm, Select, Space, Switch, type TableProps } from 'antd';
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
  createMessageTemplate,
  deleteMessageTemplate,
  pageMessageTemplates,
  updateMessageTemplate,
  type MessageTemplateInput,
  type MessageTemplateVO,
} from '../../api/message';
import { useI18n } from '../../i18n';

interface TemplateFormValues {
  templateCode: string;
  templateName: string;
  titleTemplate?: string;
  contentTemplate?: string;
  status?: number;
  remark?: string;
}

type TemplateQuery = { templateCode?: string; templateName?: string; status?: number };

export default function TemplateList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const { t } = useI18n();
  const tableRef = useRef<ProTableRef>(null);

  const statusOptions = [
    { value: 1, label: t('message.template.status.enabled') },
    { value: 0, label: t('message.template.status.disabled') },
  ];

  const [templateCode, setTemplateCode] = useState<string | undefined>(undefined);
  const [templateName, setTemplateName] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<number | undefined>(undefined);

  const [editorOpen, setEditorOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<MessageTemplateVO | null>(null);
  const [editorInitial, setEditorInitial] = useState<Partial<TemplateFormValues> | undefined>(
    undefined,
  );
  const [editorForm] = Form.useForm<TemplateFormValues>();

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const openEditor = (record: MessageTemplateVO | null) => {
    setEditRecord(record);
    setEditorInitial(record ?? { status: 1 });
    setEditorOpen(true);
  };

  const handleToggleStatus = async (row: MessageTemplateVO, next: number) => {
    try {
      await updateMessageTemplate(row.id, {
        id: row.id,
        templateCode: row.templateCode,
        templateName: row.templateName,
        titleTemplate: row.titleTemplate,
        contentTemplate: row.contentTemplate,
        status: next,
        remark: row.remark,
      });
      message.success(
        next === 1 ? t('message.template.msg.enabled') : t('message.template.msg.disabled'),
      );
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('message.template.msg.statusFailed'));
    }
  };

  const handleDelete = async (row: MessageTemplateVO) => {
    try {
      await deleteMessageTemplate(row.id);
      message.success(t('message.template.msg.deleted'));
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('message.template.msg.deleteFailed'));
    }
  };

  const handleSubmit = async (values: TemplateFormValues) => {
    const payload: MessageTemplateInput = {
      templateCode: values.templateCode,
      templateName: values.templateName,
      titleTemplate: values.titleTemplate || undefined,
      contentTemplate: values.contentTemplate || undefined,
      status: values.status,
      remark: values.remark || undefined,
    };
    try {
      if (editRecord) {
        await updateMessageTemplate(editRecord.id, { ...payload, id: editRecord.id });
        message.success(t('message.template.msg.updated'));
      } else {
        await createMessageTemplate(payload);
        message.success(t('message.template.msg.created'));
      }
      setEditorOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(
        error,
        editRecord
          ? t('message.template.msg.updateFailed')
          : t('message.template.msg.createFailed'),
      );
      throw error;
    }
  };

  const columns: TableProps<MessageTemplateVO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: t('message.template.column.templateCode'),
      dataIndex: 'templateCode',
      width: 200,
    },
    {
      title: t('message.template.column.templateName'),
      dataIndex: 'templateName',
      width: 180,
    },
    {
      title: t('message.template.column.titleTemplate'),
      dataIndex: 'titleTemplate',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('message.template.column.contentTemplate'),
      dataIndex: 'contentTemplate',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('message.template.column.status'),
      dataIndex: 'status',
      width: 100,
      render: (value: number | undefined, row) => (
        <Switch
          checked={value === 1}
          disabled={!has('sys:message:template:edit')}
          onChange={(checked) => void handleToggleStatus(row, checked ? 1 : 0)}
        />
      ),
    },
    {
      title: t('message.template.column.updateTime'),
      dataIndex: 'updateTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('message.template.column.action'),
      key: 'action',
      width: 160,
      render: (_, row) => (
        <Space size={4} wrap>
          <AuthButton
            perm="sys:message:template:edit"
            type="link"
            size="small"
            onClick={() => openEditor(row)}
          >
            {t('message.template.action.edit')}
          </AuthButton>
          {has('sys:message:template:delete') && (
            <Popconfirm
              title={t('message.template.confirm.delete')}
              description={t('message.template.confirm.deleteDescription')}
              onConfirm={() => void handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                {t('message.template.action.delete')}
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<MessageTemplateVO, TemplateQuery>
        ref={tableRef}
        rowKey="id"
        title={t('message.template.title')}
        columns={columns}
        request={pageMessageTemplates}
        params={{ templateCode, templateName, status }}
        onError={(error) => showError(error, t('message.template.msg.loadFailed'))}
        search={
          <SearchForm
            items={[
              {
                name: 'templateCode',
                label: t('message.template.column.templateCode'),
                element: (
                  <Input
                    allowClear
                    placeholder={t('message.template.search.templateCodePlaceholder')}
                  />
                ),
              },
              {
                name: 'templateName',
                label: t('message.template.column.templateName'),
                element: (
                  <Input
                    allowClear
                    placeholder={t('message.template.search.templateNamePlaceholder')}
                  />
                ),
              },
              {
                name: 'status',
                label: t('message.template.column.status'),
                element: (
                  <Select
                    allowClear
                    options={statusOptions}
                    placeholder={t('message.template.search.all')}
                    style={{ width: 140 }}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setTemplateCode((values.templateCode as string) || undefined);
              setTemplateName((values.templateName as string) || undefined);
              setStatus(values.status as number | undefined);
            }}
            onReset={() => {
              setTemplateCode(undefined);
              setTemplateName(undefined);
              setStatus(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton
            perm="sys:message:template:add"
            type="primary"
            onClick={() => openEditor(null)}
          >
            {t('message.template.action.create')}
          </AuthButton>
        }
      />

      <FormModal<TemplateFormValues>
        open={editorOpen}
        title={
          editRecord
            ? t('message.template.form.editTitle', { name: editRecord.templateName })
            : t('message.template.action.create')
        }
        form={editorForm}
        initialValues={editorInitial}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item
          name="templateCode"
          label={t('message.template.form.templateCode')}
          rules={[
            { required: true, message: t('message.template.form.templateCodeRequired') },
            {
              pattern: /^[A-Za-z0-9_]+$/,
              message: t('message.template.form.templateCodePattern'),
            },
          ]}
          extra={t('message.template.form.templateCodeExtra')}
        >
          <Input
            placeholder={t('message.template.form.templateCodePlaceholder')}
            disabled={!!editRecord}
          />
        </Form.Item>
        <Form.Item
          name="templateName"
          label={t('message.template.form.templateName')}
          rules={[{ required: true, message: t('message.template.form.templateNameRequired') }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          name="titleTemplate"
          label={t('message.template.form.titleTemplate')}
          extra={t('message.template.form.placeholderExtra')}
        >
          <Input placeholder={t('message.template.form.titleTemplatePlaceholder')} />
        </Form.Item>
        <Form.Item
          name="contentTemplate"
          label={t('message.template.form.contentTemplate')}
          extra={t('message.template.form.placeholderExtra')}
        >
          <Input.TextArea
            rows={4}
            placeholder={t('message.template.form.contentTemplatePlaceholder')}
          />
        </Form.Item>
        <Form.Item name="status" label={t('message.template.form.status')} initialValue={1}>
          <Select options={statusOptions} />
        </Form.Item>
        <Form.Item name="remark" label={t('message.template.form.remark')}>
          <Input.TextArea rows={2} />
        </Form.Item>
      </FormModal>
    </>
  );
}
