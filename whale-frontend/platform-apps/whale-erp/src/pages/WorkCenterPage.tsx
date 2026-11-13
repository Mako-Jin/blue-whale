// ERP 工作中心页：列表、新增、编辑、删除（日产能 / 效率 / 工作日）
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  createWorkCenter,
  deleteWorkCenter,
  pageWorkCenters,
  updateWorkCenter,
  type WorkCenterInput,
  type WorkCenterView,
} from '../api/mfg';
import { hasPermission } from '../utils/permission';
import { quantity } from '../utils/scm';

/** 工作中心页 */
export default function WorkCenterPage() {
  const [data, setData] = useState<WorkCenterView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [editing, setEditing] = useState<WorkCenterView | null>(null);
  const [open, setOpen] = useState(false);
  const [version, setVersion] = useState(0);
  const [form] = Form.useForm<WorkCenterInput>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageWorkCenters({ size: 100, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ efficiencyRate: 100, workDays: '1,2,3,4,5', status: 'ENABLED' });
    setOpen(true);
  };

  const openEdit = (record: WorkCenterView) => {
    setEditing(record);
    form.setFieldsValue({ ...record });
    setOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    if (editing) {
      await updateWorkCenter(editing.id, values);
      message.success('工作中心已更新');
    } else {
      await createWorkCenter(values);
      message.success('工作中心已创建');
    }
    setOpen(false);
    setVersion((value) => value + 1);
  };

  const handleDelete = async (id: number) => {
    await deleteWorkCenter(id);
    message.success('工作中心已删除');
    setVersion((value) => value + 1);
  };

  const columns: TableProps<WorkCenterView>['columns'] = [
    { title: '编码', dataIndex: 'code', key: 'code', width: 140 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '部门', dataIndex: 'department', key: 'department', width: 140 },
    {
      title: '日产能(小时)',
      dataIndex: 'dailyCapacityHours',
      key: 'dailyCapacityHours',
      width: 130,
      render: quantity,
    },
    {
      title: '效率(%)',
      dataIndex: 'efficiencyRate',
      key: 'efficiencyRate',
      width: 100,
      render: quantity,
    },
    { title: '工作日', dataIndex: 'workDays', key: 'workDays', width: 140 },
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
      width: 140,
      render: (_, record) => (
        <Space size="small">
          {hasPermission('sys:erp:mfg:workcenter:edit') ? (
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {hasPermission('sys:erp:mfg:workcenter:delete') ? (
            <Popconfirm title="确认删除该工作中心？" onConfirm={() => handleDelete(record.id)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="编码 / 名称 / 部门"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        <Button
          onClick={() => setStatus('ENABLED')}
          type={status === 'ENABLED' ? 'primary' : 'default'}
        >
          启用
        </Button>
        <Button
          onClick={() => setStatus('DISABLED')}
          type={status === 'DISABLED' ? 'primary' : 'default'}
        >
          停用
        </Button>
        {hasPermission('sys:erp:mfg:workcenter:add') ? (
          <Button type="primary" onClick={openCreate}>
            新增工作中心
          </Button>
        ) : null}
      </Space>
      <Table<WorkCenterView> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={open}
        title={editing ? `编辑工作中心 ${editing.code}` : '新增工作中心'}
        width={680}
        onOk={handleSave}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="large" wrap>
            <Form.Item name="code" label="编码" rules={[{ required: true, message: '请输入编码' }]}>
              <Input style={{ width: 200 }} maxLength={64} />
            </Form.Item>
            <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
              <Input style={{ width: 240 }} maxLength={128} />
            </Form.Item>
          </Space>
          <Space size="large" wrap>
            <Form.Item name="department" label="所属部门">
              <Input style={{ width: 220 }} maxLength={128} />
            </Form.Item>
            <Form.Item
              name="dailyCapacityHours"
              label="日产能（小时）"
              rules={[{ required: true, message: '请输入日产能' }]}
            >
              <InputNumber min={0.01} precision={2} style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="efficiencyRate" label="效率（%）">
              <InputNumber min={0} max={100} precision={2} style={{ width: 120 }} />
            </Form.Item>
          </Space>
          <Space size="large" wrap>
            <Form.Item name="workDays" label="工作日（1=周一 … 7=周日）">
              <Input style={{ width: 200 }} maxLength={20} placeholder="1,2,3,4,5" />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select
                style={{ width: 120 }}
                options={[
                  { value: 'ENABLED', label: '启用' },
                  { value: 'DISABLED', label: '停用' },
                ]}
              />
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
