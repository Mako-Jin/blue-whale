// 组件示例页 - whale-ui 二次封装组件演示（T5.22 验收）
// 采用内存 mock 数据，不依赖后端；用于演示 ProTable / SearchForm / FormModal / AuthButton

import { App, Form, Input, InputNumber, Select, Space, Tag, type TableProps } from 'antd';
import { useRef, useState } from 'react';
import {
  AuthButton,
  FormModal,
  ProTable,
  SearchForm,
  usePermission,
  type PageResult,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import { useI18n } from '../../i18n';

interface DemoRow {
  id: number;
  name: string;
  category: string;
  amount: number;
  status: number;
}

interface DemoFormValues {
  name: string;
  category: string;
  amount?: number;
}

interface DemoQuery {
  keyword?: string;
  category?: string;
}

export default function ComponentsDemo() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const { permissions } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const categoryOptions = [
    { value: 'A', label: t('demo.category.a') },
    { value: 'B', label: t('demo.category.b') },
  ];

  const [rows, setRows] = useState<DemoRow[]>(() =>
    Array.from({ length: 26 }, (_, index) => ({
      id: index + 1,
      name: t('demo.seed.name', { index: index + 1 }),
      category: index % 2 === 0 ? 'A' : 'B',
      amount: (index + 1) * 10,
      status: index % 3 === 0 ? 0 : 1,
    })),
  );
  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [category, setCategory] = useState<string | undefined>(undefined);

  const [editorOpen, setEditorOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<DemoRow | null>(null);
  const [editorForm] = Form.useForm<DemoFormValues>();

  const request = async (
    params: DemoQuery & { current: number; size: number },
  ): Promise<PageResult<DemoRow>> => {
    const filtered = rows.filter(
      (row) =>
        (!params.keyword || row.name.includes(params.keyword)) &&
        (!params.category || row.category === params.category),
    );
    const start = (params.current - 1) * params.size;
    return {
      records: filtered.slice(start, start + params.size),
      // 本地分页也与运行时对齐：total/size/current 为字符串
      total: String(filtered.length),
      size: String(params.size),
      current: String(params.current),
    };
  };

  const handleSubmit = (values: DemoFormValues) => {
    if (editRecord) {
      setRows((prev) =>
        prev.map((row) =>
          row.id === editRecord.id
            ? {
                ...row,
                name: values.name,
                category: values.category,
                amount: values.amount ?? row.amount,
              }
            : row,
        ),
      );
      message.success(t('demo.msg.updated'));
    } else {
      setRows((prev) => [
        {
          id: prev.length ? Math.max(...prev.map((row) => row.id)) + 1 : 1,
          name: values.name,
          category: values.category,
          amount: values.amount ?? 0,
          status: 1,
        },
        ...prev,
      ]);
      message.success(t('demo.msg.created'));
    }
    setEditorOpen(false);
    tableRef.current?.reload();
  };

  const columns: TableProps<DemoRow>['columns'] = [
    { title: t('demo.col.id'), dataIndex: 'id', width: 80 },
    { title: t('demo.col.name'), dataIndex: 'name' },
    { title: t('demo.col.category'), dataIndex: 'category', width: 120 },
    { title: t('demo.col.amount'), dataIndex: 'amount', width: 120 },
    {
      title: t('demo.col.status'),
      dataIndex: 'status',
      width: 100,
      render: (value: number) =>
        value === 1 ? (
          <Tag color="green">{t('demo.status.enabled')}</Tag>
        ) : (
          <Tag color="red">{t('demo.status.disabled')}</Tag>
        ),
    },
    {
      title: t('demo.col.action'),
      key: 'action',
      width: 120,
      render: (_, row) => (
        <AuthButton
          perm="sys:user:edit"
          type="link"
          size="small"
          onClick={() => {
            setEditRecord(row);
            setEditorOpen(true);
          }}
        >
          {t('demo.action.edit')}
        </AuthButton>
      ),
    },
  ];

  return (
    <>
      <ProTable<DemoRow, DemoQuery>
        ref={tableRef}
        rowKey="id"
        title={t('demo.table.title')}
        columns={columns}
        request={request}
        params={{ keyword, category }}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('demo.field.name'),
                element: <Input allowClear placeholder={t('demo.search.keywordPlaceholder')} />,
              },
              {
                name: 'category',
                label: t('demo.col.category'),
                element: <Select allowClear options={categoryOptions} style={{ width: 140 }} />,
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setCategory((values.category as string) || undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setCategory(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton
            perm="sys:user:add"
            type="primary"
            onClick={() => {
              setEditRecord(null);
              setEditorOpen(true);
            }}
          >
            {t('demo.btn.add')}
          </AuthButton>
        }
      />

      <Space style={{ marginTop: 16 }} direction="vertical">
        <span className="admin-hint">{t('demo.hint', { count: permissions.length })}</span>
        <AuthButton perm="sys:demo:not-exist" type="primary" danger>
          {t('demo.btn.permissionDemo')}
        </AuthButton>
      </Space>

      <FormModal<DemoFormValues>
        open={editorOpen}
        title={
          editRecord
            ? t('demo.modal.editTitle', { name: editRecord.name })
            : t('demo.modal.createTitle')
        }
        form={editorForm}
        initialValues={editRecord ?? undefined}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item
          name="name"
          label={t('demo.field.name')}
          rules={[{ required: true, message: t('demo.field.nameRequired') }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          name="category"
          label={t('demo.col.category')}
          rules={[{ required: true, message: t('demo.field.categoryRequired') }]}
        >
          <Select options={categoryOptions} />
        </Form.Item>
        <Form.Item name="amount" label={t('demo.col.amount')}>
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
      </FormModal>
    </>
  );
}
