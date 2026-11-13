// 规则引擎：条件（属性 op 阈值 + 持续时长）→ 动作（告警到 whale-alert）
//
// 命中判定在遥测链路内完成：连续满足 durationSec 才触发，同规则受静默期保护；
// 本页只做规则配置与状态查看，历史告警在 whale-alert 的告警记录里查。

import { useEffect, useRef, useState } from 'react';
import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { TableProps } from 'antd';
import { ProTable, SearchForm, type ProTableRef } from '@bluewhale/whale-ui';
import {
  deviceApi,
  productApi,
  ruleApi,
  type DeviceVO,
  type ProductVO,
  type RuleVO,
} from '../api/iot';

interface RuleFormValues {
  name: string;
  productId?: string;
  deviceId?: string;
  identifier: string;
  op: string;
  value: number;
  durationSec: number;
  level: string;
  silenceSeconds?: number;
  enabled: boolean;
}

const LEVEL_META: Record<string, { color: string; text: string }> = {
  INFO: { color: 'default', text: '提示' },
  WARN: { color: 'warning', text: '警告' },
  ERROR: { color: 'error', text: '错误' },
  CRITICAL: { color: 'magenta', text: '严重' },
};

export default function RulePage() {
  const { message } = App.useApp();
  const tableRef = useRef<ProTableRef>(null);
  const [filters, setFilters] = useState<{ productId?: string; keyword?: string }>({});
  const [products, setProducts] = useState<ProductVO[]>([]);
  const [devices, setDevices] = useState<DeviceVO[]>([]);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<RuleVO | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<RuleFormValues>();
  const productId = Form.useWatch('productId', form);

  useEffect(() => {
    productApi
      .page({ current: 1, size: 100 })
      .then((page) => setProducts(page.records ?? []))
      .catch(() => setProducts([]));
    deviceApi
      .page({ current: 1, size: 200 })
      .then((page) => setDevices(page.records ?? []))
      .catch(() => setDevices([]));
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ op: '>', durationSec: 0, level: 'WARN', enabled: true });
    setFormOpen(true);
  };

  const openEdit = (record: RuleVO) => {
    setEditing(record);
    form.setFieldsValue({
      name: record.name,
      productId: record.productId,
      deviceId: record.deviceId,
      identifier: record.condition?.identifier ?? '',
      op: record.condition?.op ?? '>',
      value: record.condition?.value ?? 0,
      durationSec: record.condition?.durationSec ?? 0,
      level: record.actionConfig?.level ?? 'WARN',
      silenceSeconds: record.actionConfig?.silenceSeconds,
      enabled: record.enabled === 1,
    });
    setFormOpen(true);
  };

  const submitForm = async () => {
    const values = await form.validateFields();
    const body = {
      name: values.name,
      productId: values.productId || null,
      deviceId: values.deviceId || null,
      condition: {
        identifier: values.identifier,
        op: values.op,
        value: values.value,
        durationSec: values.durationSec ?? 0,
      },
      actionType: 'alert',
      actionConfig: {
        level: values.level,
        silenceSeconds: values.silenceSeconds ?? null,
      },
      enabled: values.enabled,
    };
    setSaving(true);
    try {
      if (editing) {
        await ruleApi.update(editing.id, body);
        message.success('规则已更新');
      } else {
        await ruleApi.create(body);
        message.success('规则已创建');
      }
      setFormOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const toggle = async (record: RuleVO) => {
    try {
      await ruleApi.enable(record.id, record.enabled !== 1);
      message.success(record.enabled === 1 ? '规则已停用' : '规则已启用');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    }
  };

  const remove = async (record: RuleVO) => {
    try {
      await ruleApi.remove(record.id);
      message.success('规则已删除');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const columns: TableProps<RuleVO>['columns'] = [
    { title: '规则名称', dataIndex: 'name', width: 180 },
    {
      title: '作用域',
      key: 'scope',
      width: 200,
      render: (_, record) => {
        if (record.deviceId) {
          return `设备：${record.deviceName ?? record.deviceId}`;
        }
        if (record.productId) {
          return `产品：${record.productName ?? record.productId}`;
        }
        return <Tag>全部设备</Tag>;
      },
    },
    {
      title: '条件',
      key: 'condition',
      render: (_, record) => {
        const condition = record.condition;
        if (!condition) {
          return <Tag color="error">条件缺失</Tag>;
        }
        const duration = condition.durationSec ? ` 持续 ${condition.durationSec}s` : ' 即时';
        return (
          <span className="iot-mono">
            {condition.identifier} {condition.op} {condition.value}
            {duration}
          </span>
        );
      },
    },
    {
      title: '级别',
      key: 'level',
      width: 90,
      render: (_, record) => {
        const level = record.actionConfig?.level ?? 'WARN';
        const meta = LEVEL_META[level] ?? { color: 'default', text: level };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '静默期',
      key: 'silence',
      width: 100,
      render: (_, record) => `${record.actionConfig?.silenceSeconds ?? '-'}s`,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (enabled: number) =>
        enabled === 1 ? <Tag color="success">启用</Tag> : <Tag>停用</Tag>,
    },
    { title: '触发次数', dataIndex: 'firedCount', width: 100 },
    { title: '最近触发', dataIndex: 'lastFiredTime', width: 180 },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size={4}>
          <Button size="small" type="link" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button size="small" type="link" onClick={() => toggle(record)}>
            {record.enabled === 1 ? '停用' : '启用'}
          </Button>
          <Popconfirm title="确认删除该规则？" onConfirm={() => remove(record)}>
            <Button size="small" type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const scopeDevices = productId ? devices.filter((item) => item.productId === productId) : devices;

  return (
    <>
      <h2 className="iot-page-title">规则引擎</h2>
      <p className="iot-page-desc">
        条件满足 <b>连续 durationSec 秒</b> 才触发，触发后按静默期去重；告警落 whale-alert
        告警记录（source=iot）。
      </p>
      <ProTable<RuleVO, { productId?: string; keyword?: string }>
        ref={tableRef}
        rowKey="id"
        columns={columns}
        params={filters}
        request={async (query) => {
          const result = await ruleApi.page({
            productId: query.productId,
            keyword: query.keyword,
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
              {
                name: 'keyword',
                label: '关键字',
                element: <Input placeholder="规则名称" allowClear />,
              },
            ]}
            onSearch={(values) =>
              setFilters({
                productId: values.productId as string,
                keyword: values.keyword as string,
              })
            }
            onReset={() => setFilters({})}
          />
        }
        toolbar={
          <Button type="primary" onClick={openCreate}>
            新建规则
          </Button>
        }
        onError={(error) => message.error(error instanceof Error ? error.message : '查询失败')}
      />

      <Modal
        title={editing ? '编辑规则' : '新建规则'}
        open={formOpen}
        onCancel={() => setFormOpen(false)}
        onOk={submitForm}
        confirmLoading={saving}
        width={640}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item
            name="name"
            label="规则名称"
            rules={[{ required: true, message: '请输入规则名称' }]}
          >
            <Input placeholder="温度持续超限" maxLength={128} />
          </Form.Item>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item
              name="productId"
              label="限定产品"
              style={{ flex: 1 }}
              extra="留空表示全部产品"
            >
              <Select
                allowClear
                placeholder="全部产品"
                options={products.map((item) => ({ value: item.id, label: item.name }))}
                onChange={() => form.setFieldValue('deviceId', undefined)}
              />
            </Form.Item>
            <Form.Item
              name="deviceId"
              label="限定设备"
              style={{ flex: 1 }}
              extra="留空表示产品下全部设备"
            >
              <Select
                allowClear
                placeholder="全部设备"
                showSearch
                optionFilterProp="label"
                options={scopeDevices.map((item) => ({ value: item.id, label: item.deviceName }))}
              />
            </Form.Item>
          </Space>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item
              name="identifier"
              label="属性标识符"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请输入属性标识符' }]}
              extra="对齐物模型 property 标识符"
            >
              <Input placeholder="temperature" />
            </Form.Item>
            <Form.Item name="op" label="比较符" style={{ width: 100 }}>
              <Select
                options={['>', '>=', '<', '<=', '==', '!='].map((op) => ({ value: op, label: op }))}
              />
            </Form.Item>
            <Form.Item
              name="value"
              label="阈值"
              style={{ width: 120 }}
              rules={[{ required: true }]}
            >
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="durationSec" label="持续(秒)" style={{ width: 120 }} extra="0=即时">
              <InputNumber min={0} max={86400} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="level" label="告警级别" style={{ flex: 1 }}>
              <Select
                options={[
                  { value: 'INFO', label: '提示' },
                  { value: 'WARN', label: '警告' },
                  { value: 'ERROR', label: '错误' },
                  { value: 'CRITICAL', label: '严重' },
                ]}
              />
            </Form.Item>
            <Form.Item
              name="silenceSeconds"
              label="静默期(秒)"
              style={{ flex: 1 }}
              extra="留空取全局默认（300s）"
            >
              <InputNumber min={0} max={86400} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked" style={{ width: 100 }}>
              <Switch />
            </Form.Item>
          </Space>
          <Tooltip title="首期仅支持 alert 动作：命中后调用 whale-alert 内网接口落告警记录">
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              动作：告警（whale-alert）
            </Typography.Text>
          </Tooltip>
        </Form>
      </Modal>
    </>
  );
}
