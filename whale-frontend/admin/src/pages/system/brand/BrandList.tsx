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
import { useI18n } from '../../../i18n';

type BrandQuery = { keyword?: string; targetType?: string; status?: number };

type Translate = ReturnType<typeof useI18n>['t'];

const buildTargetTypes = (t: Translate) => [
  { value: 'PLATFORM', label: t('systemAuth.brand.option.platform') },
  { value: 'TENANT', label: t('systemAuth.brand.option.tenant') },
];

const buildStatusOptions = (t: Translate) => [
  { value: 1, label: t('systemAuth.brand.status.enabled') },
  { value: 0, label: t('systemAuth.brand.status.disabled') },
];

function renderStatus(status: number | undefined, t: Translate) {
  return status === 0 ? (
    <Tag color="default">{t('systemAuth.brand.status.disabled')}</Tag>
  ) : (
    <Tag color="success">{t('systemAuth.brand.status.enabled')}</Tag>
  );
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
  const { t } = useI18n();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const targetTypes = buildTargetTypes(t);
  const statusOptions = buildStatusOptions(t);

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
        message.success(t('systemAuth.brand.updated'));
      } else {
        await createBrand(payload);
        message.success(t('systemAuth.brand.created'));
      }
      setFormOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('systemAuth.brand.saveFailed'));
      throw error;
    }
  };

  const handleDelete = async (row: BrandVO) => {
    try {
      await deleteBrand(row.id);
      message.success(t('systemAuth.brand.deleted'));
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('systemAuth.brand.deleteFailed'));
    }
  };

  const columns: TableProps<BrandVO>['columns'] = [
    {
      title: t('systemAuth.brand.col.brandName'),
      dataIndex: 'brandName',
      width: 160,
      ellipsis: true,
    },
    {
      title: t('systemAuth.brand.col.targetType'),
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
      title: t('systemAuth.brand.col.domain'),
      dataIndex: 'domain',
      width: 180,
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('systemAuth.brand.col.loginTitle'),
      dataIndex: 'loginTitle',
      width: 160,
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('systemAuth.brand.col.primaryColor'),
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
    {
      title: t('systemAuth.brand.col.priority'),
      dataIndex: 'priority',
      width: 80,
      align: 'center',
    },
    {
      title: t('systemAuth.brand.col.status'),
      dataIndex: 'status',
      width: 80,
      render: (value: number | undefined) => renderStatus(value, t),
    },
    {
      title: t('systemAuth.brand.col.updateTime'),
      dataIndex: 'updateTime',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('systemAuth.brand.col.action'),
      key: 'action',
      width: 140,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:brand:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('systemAuth.brand.edit')}
            </Button>
          )}
          {has('sys:brand:delete') && (
            <Popconfirm
              title={t('systemAuth.brand.confirmDelete')}
              onConfirm={() => void handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                {t('systemAuth.brand.delete')}
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
        title={t('systemAuth.brand.title')}
        columns={columns}
        request={pageBrands}
        params={{ keyword, targetType, status }}
        onError={(error) => showError(error, t('systemAuth.brand.loadFailed'))}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('systemAuth.brand.search.keyword'),
                element: (
                  <Input
                    allowClear
                    placeholder={t('systemAuth.brand.search.keywordPlaceholder')}
                  />
                ),
              },
              {
                name: 'targetType',
                label: t('systemAuth.brand.field.targetType'),
                element: (
                  <Select
                    allowClear
                    options={targetTypes}
                    placeholder={t('systemAuth.brand.search.all')}
                    style={{ width: 160 }}
                  />
                ),
              },
              {
                name: 'status',
                label: t('systemAuth.brand.field.status'),
                element: (
                  <Select
                    allowClear
                    options={statusOptions}
                    placeholder={t('systemAuth.brand.search.all')}
                    style={{ width: 120 }}
                  />
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
            {t('systemAuth.brand.create')}
          </AuthButton>
        }
      />

      <FormModal<BrandFormValues>
        open={formOpen}
        title={
          editing
            ? t('systemAuth.brand.editTitle', { name: editing.brandName })
            : t('systemAuth.brand.create')
        }
        form={form}
        initialValues={formInitial}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
      >
        <Form.Item
          name="brandName"
          label={t('systemAuth.brand.field.brandName')}
          rules={[{ required: true, message: t('systemAuth.brand.field.brandNameRequired') }]}
        >
          <Input maxLength={64} placeholder={t('systemAuth.brand.field.brandNamePlaceholder')} />
        </Form.Item>
        <Space style={{ display: 'flex' }}>
          <Form.Item
            name="targetType"
            label={t('systemAuth.brand.field.targetType')}
            rules={[{ required: true, message: t('systemAuth.brand.field.targetTypeRequired') }]}
          >
            <Select options={targetTypes} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item
            name="targetId"
            label={t('systemAuth.brand.field.targetId')}
            extra={t('systemAuth.brand.field.targetIdExtra')}
          >
            <InputNumber
              min={0}
              style={{ width: 160 }}
              placeholder={t('systemAuth.brand.field.targetIdPlaceholder')}
            />
          </Form.Item>
          <Form.Item name="domain" label={t('systemAuth.brand.field.domain')}>
            <Input
              style={{ width: 200 }}
              placeholder={t('systemAuth.brand.field.domainPlaceholder')}
            />
          </Form.Item>
        </Space>
        <Form.Item name="loginTitle" label={t('systemAuth.brand.field.loginTitle')}>
          <Input
            maxLength={64}
            placeholder={t('systemAuth.brand.field.loginTitlePlaceholder')}
          />
        </Form.Item>
        <Form.Item name="institutionName" label={t('systemAuth.brand.field.institutionName')}>
          <Input maxLength={64} />
        </Form.Item>
        <Form.Item name="logoUrl" label="Logo URL">
          <Input maxLength={255} placeholder="https://..." />
        </Form.Item>
        <Form.Item name="backgroundUrl" label={t('systemAuth.brand.field.backgroundUrl')}>
          <Input maxLength={255} placeholder="https://..." />
        </Form.Item>
        <Space style={{ display: 'flex' }}>
          <Form.Item name="primaryColor" label={t('systemAuth.brand.field.primaryColor')}>
            <Input style={{ width: 160 }} placeholder="#1677ff" />
          </Form.Item>
          <Form.Item name="copyright" label={t('systemAuth.brand.field.copyright')}>
            <Input style={{ width: 320 }} maxLength={128} />
          </Form.Item>
        </Space>
        <Space style={{ display: 'flex' }}>
          <Form.Item name="priority" label={t('systemAuth.brand.field.priority')} initialValue={0}>
            <InputNumber min={0} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="status" label={t('systemAuth.brand.field.status')} initialValue={1}>
            <Select options={statusOptions} style={{ width: 120 }} />
          </Form.Item>
        </Space>
      </FormModal>
    </>
  );
}
