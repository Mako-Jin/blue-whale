// 设备管理：注册（三元组 + 一次性 secret）、状态维护、密钥重置与实时态查看
//
// secret 仅在「注册/重置」响应中出现一次，服务端只存不可逆凭据，页面必须提示用户立即保存。

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  App,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableProps } from 'antd';
import { ProTable, SearchForm, type ProTableRef } from '@bluewhale/whale-ui';
import {
  deviceApi,
  productApi,
  type DeviceCreateVO,
  type DeviceVO,
  type ProductVO,
  type ShadowVO,
  type TelemetryVO,
} from '../api/iot';

interface CredentialInfo {
  title: string;
  productCode?: string;
  deviceName: string;
  secret?: string;
}

export default function DevicePage() {
  const { message } = App.useApp();
  const tableRef = useRef<ProTableRef>(null);
  const [filters, setFilters] = useState<{ productId?: string; keyword?: string; status?: string }>({});
  const [products, setProducts] = useState<ProductVO[]>([]);
  const [formOpen, setFormOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [credential, setCredential] = useState<CredentialInfo | null>(null);
  const [detail, setDetail] = useState<DeviceVO | null>(null);
  const [shadow, setShadow] = useState<ShadowVO | null>(null);
  const [recent, setRecent] = useState<TelemetryVO[]>([]);
  const [form] = Form.useForm<{ productId: string; deviceName: string; nickname?: string }>();

  useEffect(() => {
    productApi
      .page({ current: 1, size: 100 })
      .then((page) => setProducts(page.records ?? []))
      .catch(() => setProducts([]));
  }, []);

  const openCreate = () => {
    form.resetFields();
    setFormOpen(true);
  };

  const submitForm = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const created = await deviceApi.create(values);
      setFormOpen(false);
      tableRef.current?.reset();
      showCredential('设备已注册（密钥仅显示一次）', created);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '注册失败');
    } finally {
      setSaving(false);
    }
  };

  const showCredential = (title: string, device: DeviceCreateVO) => {
    setCredential({
      title,
      productCode: device.productCode,
      deviceName: device.deviceName,
      secret: device.secret,
    });
  };

  const resetSecret = async (record: DeviceVO) => {
    try {
      const result = await deviceApi.resetSecret(record.id);
      showCredential('密钥已重置（旧密钥立即失效）', { ...result, deviceName: record.deviceName });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重置失败');
    }
  };

  const toggleStatus = async (record: DeviceVO) => {
    const next = record.status === 'active' ? 'disabled' : 'active';
    try {
      await deviceApi.update(record.id, { nickname: record.nickname, status: next });
      message.success(next === 'active' ? '设备已启用' : '设备已禁用');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    }
  };

  const remove = async (record: DeviceVO) => {
    try {
      await deviceApi.remove(record.id);
      message.success('设备已删除');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const openDetail = useCallback(
    async (record: DeviceVO) => {
      setDetail(record);
      setShadow(null);
      setRecent([]);
      try {
        const [state, points] = await Promise.all([
          deviceApi.shadow(record.id),
          deviceApi.telemetry(record.id, { limit: 20 }),
        ]);
        setShadow(state);
        setRecent(points ?? []);
      } catch (error) {
        message.error(error instanceof Error ? error.message : '设备实时态加载失败');
      }
    },
    [message],
  );

  const columns: TableProps<DeviceVO>['columns'] = [
    { title: '设备名', dataIndex: 'deviceName', width: 160, render: (v: string) => <code>{v}</code> },
    { title: '昵称', dataIndex: 'nickname', width: 140 },
    { title: '所属产品', dataIndex: 'productName', width: 160 },
    {
      title: '在线',
      dataIndex: 'online',
      width: 90,
      render: (online: number) => (online === 1 ? <Tag color="success">在线</Tag> : <Tag>离线</Tag>),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (status: string) => (status === 'active' ? <Tag color="processing">启用</Tag> : <Tag>禁用</Tag>),
    },
    { title: '最近上报', dataIndex: 'lastReportTime', width: 180 },
    {
      title: '操作',
      key: 'action',
      width: 280,
      render: (_, record) => (
        <Space size={4} wrap>
          <Button size="small" type="link" onClick={() => openDetail(record)}>
            实时态
          </Button>
          <Button size="small" type="link" onClick={() => resetSecret(record)}>
            重置密钥
          </Button>
          <Button size="small" type="link" onClick={() => toggleStatus(record)}>
            {record.status === 'active' ? '禁用' : '启用'}
          </Button>
          <Popconfirm title="确认删除该设备？" onConfirm={() => remove(record)}>
            <Button size="small" type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const reported = Object.entries(shadow?.reported ?? {});
  const desired = Object.entries(shadow?.desired ?? {});

  return (
    <>
      <h2 className="iot-page-title">设备管理</h2>
      <p className="iot-page-desc">
        设备名是三元组的第二段，注册时一次性下发密钥：连接用户名
        <code> {`{productCode}/{deviceName}`}</code>，口令为 HMAC-SHA256(secret, username)。
      </p>
      <ProTable<DeviceVO, { productId?: string; keyword?: string; status?: string }>
        ref={tableRef}
        rowKey="id"
        columns={columns}
        params={filters}
        request={async (query) => {
          const result = await deviceApi.page({
            productId: query.productId,
            keyword: query.keyword,
            status: query.status,
            current: query.current,
            size: query.size,
          });
          return result;
        }}
        search={
          <SearchForm
            items={[
              {
                name: 'productId',
                label: '产品',
                element: (
                  <Select
                    style={{ width: 180 }}
                    placeholder="全部产品"
                    allowClear
                    options={products.map((item) => ({ value: item.id, label: item.name }))}
                  />
                ),
              },
              { name: 'keyword', label: '关键字', element: <Input placeholder="设备名/昵称" allowClear /> },
              {
                name: 'status',
                label: '状态',
                element: (
                  <Select
                    style={{ width: 110 }}
                    placeholder="全部"
                    allowClear
                    options={[
                      { value: 'active', label: '启用' },
                      { value: 'disabled', label: '禁用' },
                    ]}
                  />
                ),
              },
            ]}
            onSearch={(values) =>
              setFilters({
                productId: values.productId as string,
                keyword: values.keyword as string,
                status: values.status as string,
              })
            }
            onReset={() => setFilters({})}
          />
        }
        toolbar={
          <Button type="primary" onClick={openCreate}>
            注册设备
          </Button>
        }
        onError={(error) => message.error(error instanceof Error ? error.message : '查询失败')}
      />

      <Modal
        title="注册设备"
        open={formOpen}
        onCancel={() => setFormOpen(false)}
        onOk={submitForm}
        confirmLoading={saving}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item name="productId" label="所属产品" rules={[{ required: true, message: '请选择产品' }]}>
            <Select
              placeholder="选择产品（已发布产品才能稳定接入）"
              options={products.map((item) => ({ value: item.id, label: `${item.name}（${item.code}）` }))}
            />
          </Form.Item>
          <Form.Item
            name="deviceName"
            label="设备名"
            rules={[
              { required: true, message: '请输入设备名' },
              { pattern: /^[a-zA-Z0-9][a-zA-Z0-9._-]{1,63}$/, message: '字母/数字开头，允许字母/数字/点/下划线/连字符，2-64 位' },
            ]}
          >
            <Input placeholder="sensor-001" />
          </Form.Item>
          <Form.Item name="nickname" label="昵称">
            <Input placeholder="一号车间温湿度" maxLength={128} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={credential?.title ?? '设备凭据'}
        open={Boolean(credential)}
        onCancel={() => setCredential(null)}
        footer={
          <Button type="primary" onClick={() => setCredential(null)}>
            我已保存
          </Button>
        }
      >
        <Typography.Paragraph type="warning" style={{ marginTop: 12 }}>
          密钥仅此一次可见，关闭后无法再次查看；如遗失请重置密钥（旧密钥立即失效）。
        </Typography.Paragraph>
        <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label="用户名">
            <code>{credential?.productCode ? `${credential.productCode}/${credential.deviceName}` : credential?.deviceName}</code>
          </Descriptions.Item>
          <Descriptions.Item label="设备名">
            <code>{credential?.deviceName}</code>
          </Descriptions.Item>
          <Descriptions.Item label="密钥">
            <span className="iot-mono">{credential?.secret}</span>
          </Descriptions.Item>
        </Descriptions>
      </Modal>

      <Drawer
        title={`设备实时态 · ${detail?.deviceName ?? ''}`}
        open={Boolean(detail)}
        onClose={() => setDetail(null)}
        width={760}
      >
        <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
          <Descriptions.Item label="产品">{detail?.productName ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="状态">{detail?.status}</Descriptions.Item>
          <Descriptions.Item label="在线">{detail?.online === 1 ? '在线' : '离线'}</Descriptions.Item>
          <Descriptions.Item label="影子版本">{shadow?.version ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="最近上报">{detail?.lastReportTime ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="影子更新">{shadow?.updatedTime ?? '-'}</Descriptions.Item>
        </Descriptions>

        <Typography.Title level={5}>上报快照（reported）</Typography.Title>
        {reported.length === 0 ? (
          <Typography.Text type="secondary">暂无上报数据</Typography.Text>
        ) : (
          <div className="iot-shadow-grid">
            {reported.map(([key, value]) => (
              <div className="iot-shadow-item" key={key}>
                <div className="iot-shadow-label">{key}</div>
                <div className="iot-shadow-value">{String(value)}</div>
              </div>
            ))}
          </div>
        )}

        <Typography.Title level={5} style={{ marginTop: 20 }}>
          待同步期望值（desired）
        </Typography.Title>
        {desired.length === 0 ? (
          <Typography.Text type="secondary">无待同步项</Typography.Text>
        ) : (
          <div className="iot-shadow-grid">
            {desired.map(([key, value]) => (
              <div className="iot-shadow-item" key={key}>
                <div className="iot-shadow-label">{key}</div>
                <div className="iot-shadow-value">{String(value)}</div>
              </div>
            ))}
          </div>
        )}

        <Typography.Title level={5} style={{ marginTop: 20 }}>
          最近遥测
        </Typography.Title>
        <Table<TelemetryVO>
          size="small"
          rowKey={(row) => `${row.identifier}:${row.ts}`}
          dataSource={recent}
          pagination={false}
          locale={{ emptyText: '暂无遥测数据' }}
          columns={[
            { title: '标识符', dataIndex: 'identifier', width: 180 },
            {
              title: '取值',
              key: 'value',
              render: (_, row) => (row.value === null || row.value === undefined ? row.valueText : row.value),
            },
            { title: '时间', dataIndex: 'ts', width: 200 },
          ]}
        />
      </Drawer>
    </>
  );
}
