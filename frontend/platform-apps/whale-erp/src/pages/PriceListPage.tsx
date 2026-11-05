// ERP 价格表页：销售价 / 采购价两个页签，维护往来方 + 物料唯一价目、有效期与启停
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  createPriceList,
  pagePriceLists,
  togglePriceList,
  updatePriceList,
  type PriceListView,
  type PriceType,
} from '../api/scm';
import { listErpItems, listErpSuppliers, type ErpItem } from '../api/erp';
import { pageCustomers } from '../api/masterdata';
import { hasPermission } from '../utils/permission';
import { money } from '../utils/scm';

/** 价目表单值 */
interface PriceListFormValues {
  partnerId: number;
  itemId: string;
  price?: number;
  currency?: string;
  validFrom?: Dayjs;
  validTo?: Dayjs;
  remark?: string;
}

/** 有效期展示 */
function validity(record: PriceListView): string {
  if (!record.validFrom && !record.validTo) return '长期';
  return `${record.validFrom ?? '-'} ~ ${record.validTo ?? '-'}`;
}

/** 价格表页签 */
function PriceListTab({ priceType }: { priceType: PriceType }) {
  const [data, setData] = useState<PriceListView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<PriceListView | null>(null);
  const [partners, setPartners] = useState<{ value: number; label: string }[]>([]);
  const [items, setItems] = useState<ErpItem[]>([]);
  const [form] = Form.useForm<PriceListFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pagePriceLists({ priceType, pageSize: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [priceType, keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue({
        partnerId: editing.partnerId,
        itemId: String(editing.itemId),
        price: editing.price,
        currency: editing.currency,
        validFrom: editing.validFrom ? dayjs(editing.validFrom) : undefined,
        validTo: editing.validTo ? dayjs(editing.validTo) : undefined,
        remark: editing.remark,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        currency: 'CNY',
        validFrom: dayjs(),
        validTo: dayjs().add(1, 'year'),
      });
    }
  }, [open, editing, form]);

  /** 打开弹窗时加载往来方（销售取客户、采购取供应商）与物料 */
  const loadOptions = async () => {
    const [itemList, partnerOptions] = await Promise.all([
      listErpItems(),
      priceType === 'SALES'
        ? pageCustomers({ size: 100 }).then((page) =>
          page.records.map((item) => ({
            value: item.id,
            label: `${item.code} ${item.name}`,
          })))
        : listErpSuppliers().then((list) =>
          list.map((item) => ({
            value: Number(item.id),
            label: `${item.code} ${item.name}`,
          }))),
    ]);
    setItems(itemList);
    setPartners(partnerOptions);
  };

  const openCreate = () => {
    setEditing(null);
    setOpen(true);
    void loadOptions();
  };

  const openEdit = (record: PriceListView) => {
    setEditing(record);
    setOpen(true);
    void loadOptions();
  };

  const handleSave = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    const item = items.find((candidate) => candidate.id === values.itemId);
    const input = {
      priceType,
      partnerId: values.partnerId,
      itemId: Number(values.itemId),
      itemCode: item?.code,
      price: Number(values.price ?? 0),
      currency: values.currency,
      validFrom: values.validFrom?.format('YYYY-MM-DD'),
      validTo: values.validTo?.format('YYYY-MM-DD'),
      remark: values.remark,
    };
    if (editing) {
      await updatePriceList(editing.id, input);
      message.success('价目已更新');
    } else {
      await createPriceList(input);
      message.success('价目已创建');
    }
    setOpen(false);
    setVersion((value) => value + 1);
  };

  const handleToggle = async (record: PriceListView) => {
    await togglePriceList(record.id);
    message.success(record.status === 'ENABLED' ? '价目已停用' : '价目已启用');
    setVersion((value) => value + 1);
  };

  const columns: TableProps<PriceListView>['columns'] = [
    { title: '往来方', dataIndex: 'partnerName', key: 'partnerName' },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 130 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    { title: '单价', dataIndex: 'price', key: 'price', width: 110, render: money },
    { title: '币种', dataIndex: 'currency', key: 'currency', width: 80 },
    { title: '有效期', key: 'validity', width: 220, render: (_, record) => validity(record) },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value) => (
        <Tag color={value === 'ENABLED' ? 'green' : 'default'}>
          {value === 'ENABLED' ? '启用' : '停用'}
        </Tag>
      ),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, record) => (
        <Space size="small">
          {hasPermission('sys:erp:price-list:update') ? (
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {hasPermission('sys:erp:price-list:toggle') ? (
            <Popconfirm
              title={record.status === 'ENABLED' ? '确认停用该价目？' : '确认启用该价目？'}
              onConfirm={() => handleToggle(record)}
            >
              <Button type="link" size="small" danger={record.status === 'ENABLED'}>
                {record.status === 'ENABLED' ? '停用' : '启用'}
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const partnerLabel = priceType === 'SALES' ? '客户' : '供应商';

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder={`${partnerLabel} / 物料编码 / 物料名称`}
          style={{ width: 260 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        <Button onClick={() => setStatus('ENABLED')} type={status === 'ENABLED' ? 'primary' : 'default'}>
          启用
        </Button>
        <Button onClick={() => setStatus('DISABLED')} type={status === 'DISABLED' ? 'primary' : 'default'}>
          停用
        </Button>
        {hasPermission('sys:erp:price-list:create') ? (
          <Button type="primary" onClick={openCreate}>
            新增{priceType === 'SALES' ? '销售价' : '采购价'}
          </Button>
        ) : null}
      </Space>
      <Table<PriceListView> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={open}
        title={editing
          ? `编辑${priceType === 'SALES' ? '销售价' : '采购价'}`
          : `新增${priceType === 'SALES' ? '销售价' : '采购价'}`}
        width={720}
        onOk={handleSave}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="large" wrap>
            <Form.Item
              name="partnerId"
              label={partnerLabel}
              rules={[{ required: true, message: `请选择${partnerLabel}` }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                style={{ width: 240 }}
                options={partners}
              />
            </Form.Item>
            <Form.Item
              name="itemId"
              label="物料编码"
              rules={[{ required: true, message: '请选择物料' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                style={{ width: 240 }}
                options={items.map((item) => ({
                  value: item.id,
                  label: `${item.code} ${item.name}`,
                }))}
              />
            </Form.Item>
            <Form.Item name="price" label="单价" rules={[{ required: true, message: '请输入单价' }]}>
              <InputNumber min={0.0001} precision={4} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="currency" label="币种">
              <Input style={{ width: 100 }} maxLength={8} />
            </Form.Item>
            <Form.Item name="validFrom" label="有效期从">
              <DatePicker />
            </Form.Item>
            <Form.Item name="validTo" label="有效期至">
              <DatePicker />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 价格表页 */
export default function PriceListPage() {
  const [tab, setTab] = useState<PriceType>('SALES');

  return (
    <Tabs
      activeKey={tab}
      onChange={(key) => setTab(key as PriceType)}
      items={[
        { key: 'SALES', label: '销售价', children: <PriceListTab priceType="SALES" /> },
        { key: 'PURCHASE', label: '采购价', children: <PriceListTab priceType="PURCHASE" /> },
      ]}
    />
  );
}
