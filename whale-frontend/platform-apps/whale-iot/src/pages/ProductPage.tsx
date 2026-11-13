// 产品与物模型：产品 CRUD（草稿→发布→停用）+ 物模型整包编辑（JSON）
//
// 物模型是接入契约：设备上报按 property.data_type 分流数值/文本，规则条件按
// property.identifier 匹配。已发布产品禁止改物模型（后端拦截），需先停用。

import { useCallback, useRef, useState } from 'react';
import {
  App,
  Button,
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
import { productApi, type ProductVO, type ThingModelVO } from '../api/iot';

const STATUS_META: Record<string, { color: string; text: string }> = {
  draft: { color: 'default', text: '草稿' },
  published: { color: 'success', text: '已发布' },
  disabled: { color: 'warning', text: '已停用' },
};

export default function ProductPage() {
  const { message } = App.useApp();
  const tableRef = useRef<ProTableRef>(null);
  const [filters, setFilters] = useState<{ keyword?: string; status?: string }>({});
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<ProductVO | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<{ code: string; name: string; description?: string }>();

  const [modelProduct, setModelProduct] = useState<ProductVO | null>(null);
  const [modelJson, setModelJson] = useState('[]');
  const [modelLoading, setModelLoading] = useState(false);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setFormOpen(true);
  };

  const openEdit = (record: ProductVO) => {
    setEditing(record);
    form.setFieldsValue({ code: record.code, name: record.name, description: record.description });
    setFormOpen(true);
  };

  const submitForm = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await productApi.update(editing.id, { name: values.name, description: values.description });
        message.success('产品已更新');
      } else {
        await productApi.create(values);
        message.success('产品已创建（草稿态）');
      }
      setFormOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const changeStatus = async (record: ProductVO, action: 'publish' | 'disable') => {
    try {
      await (action === 'publish' ? productApi.publish(record.id) : productApi.disable(record.id));
      message.success(action === 'publish' ? '产品已发布' : '产品已停用');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    }
  };

  const remove = async (record: ProductVO) => {
    try {
      await productApi.remove(record.id);
      message.success('产品已删除');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const openModel = useCallback(
    async (record: ProductVO) => {
      setModelProduct(record);
      setModelLoading(true);
      try {
        const items = await productApi.thingModel(record.id);
        setModelJson(JSON.stringify(items ?? [], null, 2));
      } catch (error) {
        message.error(error instanceof Error ? error.message : '物模型加载失败');
        setModelJson('[]');
      } finally {
        setModelLoading(false);
      }
    },
    [message],
  );

  const saveModel = async () => {
    if (!modelProduct) {
      return;
    }
    let items: ThingModelVO[];
    try {
      const parsed = JSON.parse(modelJson) as unknown;
      if (!Array.isArray(parsed)) {
        throw new Error('物模型必须是数组');
      }
      items = parsed as ThingModelVO[];
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'JSON 格式错误');
      return;
    }
    setModelLoading(true);
    try {
      await productApi.saveThingModel(modelProduct.id, items);
      message.success('物模型已保存（整包覆盖）');
      setModelProduct(null);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setModelLoading(false);
    }
  };

  const columns: TableProps<ProductVO>['columns'] = [
    { title: '产品名称', dataIndex: 'name', width: 180 },
    { title: '编码', dataIndex: 'code', width: 160, render: (v: string) => <code>{v}</code> },
    { title: '协议', dataIndex: 'protocol', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: string) => {
        const meta = STATUS_META[status] ?? { color: 'default', text: status };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    { title: '设备数', dataIndex: 'deviceCount', width: 90 },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', width: 180 },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_, record) => (
        <Space size={4} wrap>
          <Button size="small" type="link" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button size="small" type="link" onClick={() => openModel(record)}>
            物模型
          </Button>
          {record.status === 'draft' && (
            <Button size="small" type="link" onClick={() => changeStatus(record, 'publish')}>
              发布
            </Button>
          )}
          {record.status === 'published' && (
            <Button size="small" type="link" onClick={() => changeStatus(record, 'disable')}>
              停用
            </Button>
          )}
          <Popconfirm title="确认删除该产品？" onConfirm={() => remove(record)}>
            <Button size="small" type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <h2 className="iot-page-title">产品与物模型</h2>
      <p className="iot-page-desc">
        产品是接入契约的载体：先建产品（草稿）→ 编辑物模型 → 发布 →
        注册设备。已发布产品的物模型需先停用才能修改。
      </p>
      <ProTable<ProductVO, { keyword?: string; status?: string }>
        ref={tableRef}
        rowKey="id"
        columns={columns}
        params={filters}
        request={async (query) => {
          const result = await productApi.page({
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
                name: 'keyword',
                label: '关键字',
                element: <Input placeholder="名称/编码" allowClear />,
              },
              {
                name: 'status',
                label: '状态',
                element: (
                  <Select
                    style={{ width: 120 }}
                    placeholder="全部"
                    allowClear
                    options={[
                      { value: 'draft', label: '草稿' },
                      { value: 'published', label: '已发布' },
                      { value: 'disabled', label: '已停用' },
                    ]}
                  />
                ),
              },
            ]}
            onSearch={(values) =>
              setFilters({ keyword: values.keyword as string, status: values.status as string })
            }
            onReset={() => setFilters({})}
          />
        }
        toolbar={
          <Button type="primary" onClick={openCreate}>
            新建产品
          </Button>
        }
        onError={(error) => message.error(error instanceof Error ? error.message : '查询失败')}
      />

      <Modal
        title={editing ? '编辑产品' : '新建产品'}
        open={formOpen}
        onCancel={() => setFormOpen(false)}
        onOk={submitForm}
        confirmLoading={saving}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item
            name="code"
            label="产品编码"
            rules={[
              { required: !editing, message: '请输入产品编码' },
              {
                pattern: /^[a-z][a-z0-9-]{1,63}$/,
                message: '小写字母开头的小写字母/数字/连字符，2-64 位',
              },
            ]}
            extra={editing ? '编码创建后不可修改' : '如 temp-sensor-v1，设备三元组的第一段'}
          >
            <Input disabled={Boolean(editing)} placeholder="temp-sensor-v1" />
          </Form.Item>
          <Form.Item
            name="name"
            label="产品名称"
            rules={[{ required: true, message: '请输入产品名称' }]}
          >
            <Input placeholder="温湿度传感器" maxLength={128} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} maxLength={512} placeholder="接入协议、安装场景等" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={`物模型 · ${modelProduct?.name ?? ''}`}
        open={Boolean(modelProduct)}
        onClose={() => setModelProduct(null)}
        width={720}
        extra={
          <Space>
            <Button onClick={() => setModelProduct(null)}>取消</Button>
            <Button type="primary" loading={modelLoading} onClick={saveModel}>
              保存
            </Button>
          </Space>
        }
      >
        <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
          整包覆盖式保存：数组每项{' '}
          {`{ kind: property|event|service, identifier, name, dataType, spec, required }`}。
          property 的 dataType 决定上报分流（int/double 存数值列，其余存文本列）。
        </Typography.Paragraph>
        {modelJson !== '[]' && (
          <Table<ThingModelVO>
            size="small"
            rowKey={(row) => `${row.kind}:${row.identifier}`}
            style={{ marginBottom: 12 }}
            pagination={false}
            dataSource={safeParse(modelJson)}
            columns={[
              { title: '类别', dataIndex: 'kind', width: 90 },
              { title: '标识符', dataIndex: 'identifier', width: 160 },
              { title: '名称', dataIndex: 'name' },
              { title: '类型', dataIndex: 'dataType', width: 90 },
            ]}
          />
        )}
        <Input.TextArea
          value={modelJson}
          onChange={(event) => setModelJson(event.target.value)}
          autoSize={{ minRows: 16, maxRows: 28 }}
          className="iot-mono"
          spellCheck={false}
        />
      </Drawer>
    </>
  );
}

/** 预览用：JSON 解析失败返回空数组（不打断编辑） */
function safeParse(json: string): ThingModelVO[] {
  try {
    const parsed = JSON.parse(json) as unknown;
    return Array.isArray(parsed) ? (parsed as ThingModelVO[]) : [];
  } catch {
    return [];
  }
}
