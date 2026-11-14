// 品牌配置管理页 - 列表（分页 / 新增 / 编辑 / 删除）
// 权限码：sys:brand:list(2370) / add(2371) / edit(2372) / delete(2373)

import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Tag,
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
  createBrand,
  deleteBrand,
  pageBrands,
  updateBrand,
  type BrandVO,
} from '../../../api/brand';

type BrandQuery = { keyword?: string; targetType?: string; status?: number };

const TARGET_TYPES = [
  { value: 'PLATFORM', label: '平台 PLATFORM' },
  { value: 'TENANT', label: '租户 TENANT' },
];

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

function renderStatus(status: number | undefined) {
  return status === 0 ? <Tag color="default">停用</Tag> : <Tag color="success">启用</Tag>;
}

interface BrandFormValues {
  targetType: string;
  targetId?: string;
  domain?: string;
  brandName: string;
  logoUrl?: string;
  loginTitle?: string;
  institutionName?: string;
  copyright?: string;
  primaryColor?: string;
  backgroundUrl?: string;
  priority?: number;
  status?: number;
}

export default function BrandList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [targetType, setTargetType] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<number | undefined>(undefined);

  const [editing, setEditing] = useState<BrandVO | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formInitial, setFormInitial] = useState<Partial<BrandFormValues> | undefined>(undefined);
  const [form] = Form.useForm<BrandFormValues>();

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const openCreate = () => {
    setEditing(null);
    setFormInitial({ targetType: 'PLATFORM', status: 1, priority: 0 });
    setFormOpen(true);
  };

  const openEdit = (record: BrandVO) => {
    setEditing(record);
    setFormInitial({
      targetType: record.targetType,
      targetId: record.targetId,
      domain: record.domain,
      brandName: record.brandName,
      logoUrl: record.logoUrl,
      loginTitle: record.loginTitle,
      institutionName: record.institutionName,
      copyright: record.copyright,
      primaryColor: record.primaryColor,
      backgroundUrl: record.backgroundUrl,
      priority: record.priority ?? 0,
      status: record.status ?? 1,
    });
    setFormOpen(true);
  };

  const handleSave = async (values: BrandFormValues) => {
    const payload = {
      ...values,
      targetId: values.targetId || undefined,
    };
    try {
      if (editing) {
        await updateBrand(editing.id, payload);
        message.success('品牌配置已更新');
      } else {
        await createBrand(payload);
        message.success('品牌配置已创建');
      }
      setFormOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '保存失败');
      throw error;
    }
  };

  const handleDelete = async (row: BrandVO) => {
    try {
      await deleteBrand(row.id);
      message.success('品牌配置已删除');
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '删除失败');
    }
  };

  const columns: TableProps<BrandVO>['columns'] = [
    { title: '品牌名称', dataIndex: 'brandName', width: 160, ellipsis: true },
    {
      title: '作用域',
      dataIndex: 'targetType',
      width: 140,
      render: (value: string | undefined, row) => (
        <Space size={4}>
          <Tag color={value === 'PLATFORM' ? 'geekblue' : 'blue'}>{value ?? '-'}</Tag>
          {row.targetId ? <span>{row.targetId}</span> : null}
        </Space>
      ),
    },
    {
      title: '域名',
      dataIndex: 'domain',
      width: 180,
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '登录标题',
      dataIndex: 'loginTitle',
      width: 160,
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '主色',
      dataIndex: 'primaryColor',
      width: 110,
      render: (value: string | undefined) =>
        value ? (
          <Space size={4}>
            <span
              style={{
                display: 'inline-block',
                width: 12,
                height: 12,
                borderRadius: 2,
                background: value,
              }}
            />
            {value}
          </Space>
        ) : (
          '-'
        ),
    },
    { title: '优先级', dataIndex: 'priority', width: 80, align: 'center' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (value: number | undefined) => renderStatus(value),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:brand:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          )}
          {has('sys:brand:delete') && (
            <Popconfirm title="确认删除该品牌配置？" onConfirm={() => void handleDelete(row)}>
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
      <ProTable<BrandVO, BrandQuery>
        ref={tableRef}
        rowKey="id"
        title="品牌配置"
        columns={columns}
        request={pageBrands}
        params={{ keyword, targetType, status }}
        onError={(error) => showError(error, '品牌配置加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键字',
                element: <Input allowClear placeholder="品牌名称 / 域名" />,
              },
              {
                name: 'targetType',
                label: '作用域',
                element: (
                  <Select allowClear options={TARGET_TYPES} placeholder="全部" style={{ width: 160 }} />
                ),
              },
              {
                name: 'status',
                label: '状态',
                element: (
                  <Select allowClear options={STATUS_OPTIONS} placeholder="全部" style={{ width: 120 }} />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setTargetType((values.targetType as string) || undefined);
              setStatus((values.status as number | undefined) ?? undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setTargetType(undefined);
              setStatus(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton perm="sys:brand:add" type="primary" onClick={openCreate}>
            新增品牌
          </AuthButton>
        }
      />

      <FormModal<BrandFormValues>
        open={formOpen}
        title={editing ? `编辑品牌：${editing.brandName}` : '新增品牌'}
        form={form}
        initialValues={formInitial}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
      >
        <Form.Item
          name="brandName"
          label="品牌名称"
          rules={[{ required: true, message: '请输入品牌名称' }]}
        >
          <Input maxLength={64} placeholder="如 蓝鲸科技" />
        </Form.Item>
        <Space style={{ display: 'flex' }}>
          <Form.Item
            name="targetType"
            label="作用域"
            rules={[{ required: true, message: '请选择作用域' }]}
          >
            <Select options={TARGET_TYPES} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="targetId" label="作用域 ID" extra="PLATFORM 可留空">
            <InputNumber min={0} style={{ width: 160 }} placeholder="租户 ID" />
          </Form.Item>
          <Form.Item name="domain" label="匹配域名">
            <Input style={{ width: 200 }} placeholder="如 auth.example.com" />
          </Form.Item>
        </Space>
        <Form.Item name="loginTitle" label="登录页标题">
          <Input maxLength={64} placeholder="登录页大标题" />
        </Form.Item>
        <Form.Item name="institutionName" label="机构名称">
          <Input maxLength={64} />
        </Form.Item>
        <Form.Item name="logoUrl" label="Logo URL">
          <Input maxLength={255} placeholder="https://..." />
        </Form.Item>
        <Form.Item name="backgroundUrl" label="背景图 URL">
          <Input maxLength={255} placeholder="https://..." />
        </Form.Item>
        <Space style={{ display: 'flex' }}>
          <Form.Item name="primaryColor" label="主题色">
            <Input style={{ width: 160 }} placeholder="#1677ff" />
          </Form.Item>
          <Form.Item name="copyright" label="版权文案">
            <Input style={{ width: 320 }} maxLength={128} />
          </Form.Item>
        </Space>
        <Space style={{ display: 'flex' }}>
          <Form.Item name="priority" label="优先级" initialValue={0}>
            <InputNumber min={0} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <Select options={STATUS_OPTIONS} style={{ width: 120 }} />
          </Form.Item>
        </Space>
      </FormModal>
    </>
  );
}
