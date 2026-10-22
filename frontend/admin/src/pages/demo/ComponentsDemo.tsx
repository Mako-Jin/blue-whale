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

const CATEGORY_OPTIONS = [
  { value: 'A', label: '分类 A' },
  { value: 'B', label: '分类 B' },
];

const SEED_ROWS: DemoRow[] = Array.from({ length: 26 }, (_, index) => ({
  id: index + 1,
  name: `示例数据 ${index + 1}`,
  category: index % 2 === 0 ? 'A' : 'B',
  amount: (index + 1) * 10,
  status: index % 3 === 0 ? 0 : 1,
}));

export default function ComponentsDemo() {
  const { message } = App.useApp();
  const { permissions } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [rows, setRows] = useState<DemoRow[]>(SEED_ROWS);
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
      total: filtered.length,
      size: params.size,
      current: params.current,
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
      message.success('示例数据已修改（内存）');
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
      message.success('示例数据已新增（内存）');
    }
    setEditorOpen(false);
    tableRef.current?.reload();
  };

  const columns: TableProps<DemoRow>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '名称', dataIndex: 'name' },
    { title: '分类', dataIndex: 'category', width: 120 },
    { title: '金额', dataIndex: 'amount', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: number) =>
        value === 1 ? <Tag color="green">启用</Tag> : <Tag color="red">停用</Tag>,
    },
    {
      title: '操作',
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
          编辑
        </AuthButton>
      ),
    },
  ];

  return (
    <>
      <ProTable<DemoRow, DemoQuery>
        ref={tableRef}
        rowKey="id"
        title="whale-ui 组件示例（内存数据）"
        columns={columns}
        request={request}
        params={{ keyword, category }}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '名称',
                element: <Input allowClear placeholder="名称关键字" />,
              },
              {
                name: 'category',
                label: '分类',
                element: <Select allowClear options={CATEGORY_OPTIONS} style={{ width: 140 }} />,
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
            新增示例数据
          </AuthButton>
        }
      />

      <Space style={{ marginTop: 16 }} direction="vertical">
        <span className="admin-hint">
          按钮级权限演示：当前账号拥有 {permissions.length} 个权限码。 「编辑 / 新增示例数据」需要
          sys:user:edit / sys:user:add，因此可见； 下面这个按钮需要不存在的
          sys:demo:not-exist，故不会渲染。
        </span>
        <AuthButton perm="sys:demo:not-exist" type="primary" danger>
          需要 sys:demo:not-exist 权限的按钮
        </AuthButton>
      </Space>

      <FormModal<DemoFormValues>
        open={editorOpen}
        title={editRecord ? `编辑示例数据：${editRecord.name}` : '新增示例数据'}
        form={editorForm}
        initialValues={editRecord ?? undefined}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
          <Input />
        </Form.Item>
        <Form.Item name="category" label="分类" rules={[{ required: true, message: '请选择分类' }]}>
          <Select options={CATEGORY_OPTIONS} />
        </Form.Item>
        <Form.Item name="amount" label="金额">
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
      </FormModal>
    </>
  );
}
