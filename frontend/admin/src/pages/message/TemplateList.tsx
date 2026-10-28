// 消息模板管理页 - 列表（分页 / 新增 / 编辑 / 启停 / 删除）
// 操作按钮均按权限码渲染（AuthButton / usePermission），无权限自动隐藏

import {
  App,
  Button,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Switch,
  type TableProps,
} from 'antd';
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

interface TemplateFormValues {
  templateCode: string;
  templateName: string;
  titleTemplate?: string;
  contentTemplate?: string;
  status?: number;
  remark?: string;
}

type TemplateQuery = { templateCode?: string; templateName?: string; status?: number };

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

export default function TemplateList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

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
      message.success(next === 1 ? '模板已启用' : '模板已停用');
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '状态变更失败');
    }
  };

  const handleDelete = async (row: MessageTemplateVO) => {
    try {
      await deleteMessageTemplate(row.id);
      message.success('模板已删除');
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '删除失败');
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
        message.success('模板已更新');
      } else {
        await createMessageTemplate(payload);
        message.success('模板已创建');
      }
      setEditorOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, editRecord ? '更新失败' : '创建失败');
      throw error;
    }
  };

  const columns: TableProps<MessageTemplateVO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '模板编码', dataIndex: 'templateCode', width: 200 },
    { title: '模板名称', dataIndex: 'templateName', width: 180 },
    {
      title: '标题模板',
      dataIndex: 'titleTemplate',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '内容模板',
      dataIndex: 'contentTemplate',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '状态',
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
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
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
            编辑
          </AuthButton>
          {has('sys:message:template:delete') && (
            <Popconfirm
              title="确认删除该模板？"
              description="历史消息保留标题/正文快照，不受影响"
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
      <ProTable<MessageTemplateVO, TemplateQuery>
        ref={tableRef}
        rowKey="id"
        title="消息模板"
        columns={columns}
        request={pageMessageTemplates}
        params={{ templateCode, templateName, status }}
        onError={(error) => showError(error, '模板列表加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'templateCode',
                label: '模板编码',
                element: <Input allowClear placeholder="模板编码（模糊）" />,
              },
              {
                name: 'templateName',
                label: '模板名称',
                element: <Input allowClear placeholder="模板名称（模糊）" />,
              },
              {
                name: 'status',
                label: '状态',
                element: (
                  <Select allowClear options={STATUS_OPTIONS} placeholder="全部" style={{ width: 140 }} />
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
            新增模板
          </AuthButton>
        }
      />

      <FormModal<TemplateFormValues>
        open={editorOpen}
        title={editRecord ? `编辑模板：${editRecord.templateName}` : '新增模板'}
        form={editorForm}
        initialValues={editorInitial}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item
          name="templateCode"
          label="模板编码"
          rules={[
            { required: true, message: '请输入模板编码' },
            {
              pattern: /^[A-Za-z0-9_]+$/,
              message: '仅支持大写字母/数字/下划线（保存时自动转大写）',
            },
          ]}
          extra="租户内唯一，保存时统一转为大写"
        >
          <Input placeholder="如 ORDER_PAID_NOTICE" disabled={!!editRecord} />
        </Form.Item>
        <Form.Item
          name="templateName"
          label="模板名称"
          rules={[{ required: true, message: '请输入模板名称' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="titleTemplate" label="标题模板" extra="支持 ${key} 占位符">
          <Input placeholder="如 【${system}】${title}" />
        </Form.Item>
        <Form.Item name="contentTemplate" label="内容模板" extra="支持 ${key} 占位符">
          <Input.TextArea rows={4} placeholder="如 您的订单 ${orderNo} 已支付，金额 ${amount} 元" />
        </Form.Item>
        <Form.Item name="status" label="状态" initialValue={1}>
          <Select options={STATUS_OPTIONS} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} />
        </Form.Item>
      </FormModal>
    </>
  );
}
