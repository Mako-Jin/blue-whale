// 项目交付物：按项目归档已发布文档/BOM/工艺路线版本
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  archiveDeliverable,
  deleteDeliverable,
  pageDeliverables,
  type Deliverable,
  type DeliverableInput,
  type DeliverableObjectType,
  type DeliverableType,
} from '../../api/collab';
import { hasPermission } from '../../utils/permission';

/** 对象类型文案 */
const OBJECT_META: Record<DeliverableObjectType, { text: string; color: string }> = {
  DOCUMENT: { text: '图文档', color: 'blue' },
  BOM: { text: 'BOM', color: 'purple' },
  ROUTE: { text: '工艺路线', color: 'orange' },
};

/** 交付物类型文案 */
const TYPE_META: Record<DeliverableType, string> = {
  DESIGN: '设计资料',
  DRAWING: '图纸',
  REPORT: '报告',
  OTHER: '其他',
};

/** 对象类型下拉选项 */
const OBJECT_OPTIONS = (Object.keys(OBJECT_META) as DeliverableObjectType[]).map((key) => ({
  value: key,
  label: OBJECT_META[key].text,
}));

/** 交付物类型下拉选项 */
const TYPE_OPTIONS = (Object.keys(TYPE_META) as DeliverableType[]).map((key) => ({
  value: key,
  label: TYPE_META[key],
}));

interface Filters {
  projectId?: number;
  objectType?: DeliverableObjectType;
  keyword?: string;
}

export default function DeliverableCenter() {
  const [filters, setFilters] = useState<Filters>({});
  const [rows, setRows] = useState<Deliverable[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [form] = Form.useForm<DeliverableInput>();

  const canAdd = hasPermission('sys:pdm:collab:deliverable:add');
  const canDelete = hasPermission('sys:pdm:collab:deliverable:delete');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageDeliverables({ ...filters, current, size });
      setRows(result.records);
      setTotal(Number(result.total));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '交付物加载失败');
    } finally {
      setLoading(false);
    }
  }, [filters, current, size]);

  useEffect(() => {
    void load();
  }, [load]);

  const onCreate = async () => {
    const values = await form.validateFields();
    try {
      await archiveDeliverable({ ...values, deliverableType: values.deliverableType ?? 'OTHER' });
      message.success('已归档');
      setCreateOpen(false);
      form.resetFields();
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '归档失败');
    }
  };

  const onDelete = async (id: number) => {
    try {
      await deleteDeliverable(id);
      message.success('已取消归档');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    }
  };

  const columns: TableProps<Deliverable>['columns'] = [
    { title: '项目', dataIndex: 'projectName', width: 160, render: (v: string, row) => v || `#${row.projectId}` },
    { title: '任务', dataIndex: 'workItemName', width: 140, render: (v: string) => v || '-' },
    {
      title: '对象',
      dataIndex: 'objectNo',
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <Space size={4}>
            <Tag color={OBJECT_META[row.objectType].color}>{OBJECT_META[row.objectType].text}</Tag>
            <span>{v || `#${row.objectId}`}</span>
          </Space>
          <Typography.Text type="secondary">{row.objectTitle}</Typography.Text>
        </Space>
      ),
    },
    { title: '版本', dataIndex: 'objectVersionNo', width: 100 },
    {
      title: '类型',
      dataIndex: 'deliverableType',
      width: 110,
      render: (v: DeliverableType) => TYPE_META[v] ?? v,
    },
    { title: '归档时间', dataIndex: 'archivedAt', width: 170, render: (v: string) => v || '-' },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
    {
      title: '操作',
      width: 100,
      render: (_, row) =>
        canDelete ? (
          <Popconfirm title="取消该交付物归档？" onConfirm={() => void onDelete(row.id)}>
            <Button type="link" danger size="small">
              取消归档
            </Button>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <Card
      title="项目交付物"
      extra={
        canAdd ? (
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            归档交付物
          </Button>
        ) : null
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <InputNumber
          placeholder="项目 ID"
          min={1}
          onChange={(value) => setFilters((prev) => ({ ...prev, projectId: value ?? undefined }))}
        />
        <Select
          allowClear
          placeholder="对象类型"
          style={{ width: 140 }}
          options={OBJECT_OPTIONS}
          onChange={(value) => setFilters((prev) => ({ ...prev, objectType: value }))}
        />
        <Input.Search
          placeholder="对象编号 / 名称 / 项目"
          allowClear
          style={{ width: 240 }}
          onSearch={(value) => {
            setCurrent(1);
            setFilters((prev) => ({ ...prev, keyword: value || undefined }));
          }}
        />
      </Space>
      <Table<Deliverable>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) => {
            setCurrent(page);
            setSize(pageSize);
          },
        }}
      />

      <Modal
        title="归档项目交付物"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => void onCreate()}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ deliverableType: 'DESIGN', objectType: 'DOCUMENT' }}>
          <Form.Item name="projectId" label="项目 ID" rules={[{ required: true, message: '请输入项目 ID' }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="projectName" label="项目名称">
            <Input placeholder="项目名称快照" />
          </Form.Item>
          <Form.Item name="workItemId" label="任务 ID">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="objectType" label="对象类型" rules={[{ required: true }]}>
            <Select options={OBJECT_OPTIONS} />
          </Form.Item>
          <Form.Item name="objectId" label="对象 ID" rules={[{ required: true, message: '请输入对象 ID' }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="objectVersionNo"
            label="对象版本号"
            rules={[{ required: true, message: '只能归档已发布版本' }]}
          >
            <Input placeholder="如 V1.0" />
          </Form.Item>
          <Form.Item name="deliverableType" label="交付物类型">
            <Select options={TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
