// 迭代管理：创建/编辑/启动/关闭/删除，以及迭代工作项增删

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  List,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useParams } from 'react-router-dom';
import { itemApi, sprintApi, type Sprint, type WorkItem } from '../api/pm';
import {
  formatDate,
  hasPermission,
  sprintStatusColor,
  sprintStatusLabel,
  typeLabel,
} from '../utils';

export default function SprintPage() {
  const { projectId = '' } = useParams();
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [records, setRecords] = useState<Sprint[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [status, setStatus] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Sprint | null>(null);
  const [managing, setManaging] = useState<Sprint | null>(null);
  const [sprintItems, setSprintItems] = useState<WorkItem[]>([]);
  const [candidates, setCandidates] = useState<WorkItem[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await sprintApi.page({ projectId, current, size: 10, status });
      setRecords(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [projectId, current, status, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: Sprint) => {
    setEditing(record);
    form.setFieldsValue({
      name: record.name,
      goal: record.goal,
      dates:
        record.startDate && record.endDate
          ? [dayjs(record.startDate), dayjs(record.endDate)]
          : undefined,
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const payload = {
      projectId: Number(projectId),
      name: values.name,
      goal: values.goal,
      startDate: values.dates?.[0]?.format('YYYY-MM-DD'),
      endDate: values.dates?.[1]?.format('YYYY-MM-DD'),
    };
    try {
      if (editing) {
        await sprintApi.update(editing.id, payload);
      } else {
        await sprintApi.create(payload);
      }
      message.success(editing ? '迭代已更新' : '迭代已创建');
      setModalOpen(false);
      setEditing(null);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const runAction = async (action: 'start' | 'close' | 'remove', record: Sprint) => {
    try {
      if (action === 'start') {
        await sprintApi.start(record.id);
        message.success('迭代已启动');
      } else if (action === 'close') {
        await sprintApi.close(record.id);
        message.success('迭代已关闭');
      } else {
        await sprintApi.remove(record.id);
        message.success('迭代已删除');
      }
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const openManaging = async (record: Sprint) => {
    try {
      const [items, all] = await Promise.all([
        sprintApi.items(record.id),
        itemApi.page({ projectId, current: 1, size: 200 }),
      ]);
      setSprintItems(items);
      setCandidates(all.records.filter((item) => !item.sprintId));
      setSelectedIds([]);
      setManaging(record);
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const addSelected = async () => {
    if (!managing || selectedIds.length === 0) {
      return;
    }
    try {
      const added = await sprintApi.addItems(managing.id, selectedIds);
      message.success(`已加入 ${added} 个工作项`);
      await openManaging(managing);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const removeItem = async (itemId: string) => {
    if (!managing) {
      return;
    }
    try {
      await sprintApi.removeItem(managing.id, itemId);
      message.success('已移出迭代');
      await openManaging(managing);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const columns: ColumnsType<Sprint> = [
    { title: '迭代名称', dataIndex: 'name' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={sprintStatusColor(value)}>{sprintStatusLabel(value)}</Tag>
      ),
    },
    {
      title: '区间',
      key: 'range',
      width: 220,
      render: (_, record) =>
        `${formatDate(record.startDate)} ~ ${formatDate(record.endDate ?? undefined)}`,
    },
    { title: '目标', dataIndex: 'goal', ellipsis: true, render: (value?: string) => value ?? '-' },
    {
      title: '操作',
      key: 'action',
      width: 300,
      render: (_, record) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => void openManaging(record)}>
            工作项
          </Button>
          {hasPermission('sys:pm:sprint:manage') && record.status === 'FUTURE' && (
            <>
              <Button type="link" size="small" onClick={() => openEdit(record)}>
                编辑
              </Button>
              <Button type="link" size="small" onClick={() => void runAction('start', record)}>
                启动
              </Button>
              <Popconfirm title="删除该迭代？" onConfirm={() => void runAction('remove', record)}>
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            </>
          )}
          {hasPermission('sys:pm:sprint:manage') && record.status === 'ACTIVE' && (
            <Popconfirm
              title="关闭后工作项操作将冻结，确认？"
              onConfirm={() => void runAction('close', record)}
            >
              <Button type="link" size="small">
                关闭
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          allowClear
          placeholder="迭代状态"
          style={{ width: 140 }}
          options={[
            { value: 'FUTURE', label: '未开始' },
            { value: 'ACTIVE', label: '进行中' },
            { value: 'CLOSED', label: '已关闭' },
          ]}
          onChange={(value) => {
            setStatus(value);
            setCurrent(1);
          }}
        />
        {hasPermission('sys:pm:sprint:manage') && (
          <Button type="primary" onClick={openCreate}>
            新建迭代
          </Button>
        )}
      </Space>
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={records}
        pagination={{ current, pageSize: 10, total, onChange: setCurrent }}
      />

      <Modal
        open={modalOpen}
        title={editing ? '编辑迭代' : '新建迭代'}
        onCancel={() => setModalOpen(false)}
        onOk={() => void handleSubmit()}
        okText="保存"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="迭代名称"
            rules={[{ required: true, message: '请输入迭代名称' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="dates" label="起止日期">
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="goal" label="迭代目标">
            <Input.TextArea rows={3} maxLength={512} showCount />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={Boolean(managing)}
        title={`迭代工作项：${managing?.name ?? ''}`}
        width={760}
        onCancel={() => setManaging(null)}
        footer={null}
      >
        <Card size="small" title="已加入" style={{ marginBottom: 12 }}>
          <List
            size="small"
            dataSource={sprintItems}
            locale={{ emptyText: '暂无工作项' }}
            renderItem={(item) => (
              <List.Item
                actions={[
                  <Button
                    key="remove"
                    type="link"
                    size="small"
                    danger
                    onClick={() => void removeItem(item.id)}
                  >
                    移出
                  </Button>,
                ]}
              >
                <Space size={8}>
                  <Tag>{typeLabel(item.type)}</Tag>
                  <Typography.Text>{item.key}</Typography.Text>
                  <Typography.Text>{item.title}</Typography.Text>
                </Space>
              </List.Item>
            )}
          />
        </Card>
        <Card size="small" title="从 Backlog 加入">
          <Space direction="vertical" style={{ width: '100%' }}>
            <Select
              mode="multiple"
              style={{ width: '100%' }}
              placeholder="选择未加入迭代的工作项"
              value={selectedIds}
              onChange={setSelectedIds}
              options={candidates.map((item) => ({
                value: item.id,
                label: `${item.key} ${item.title}`,
              }))}
            />
            <Button
              type="primary"
              disabled={selectedIds.length === 0}
              onClick={() => void addSelected()}
            >
              加入迭代
            </Button>
          </Space>
        </Card>
      </Modal>
    </div>
  );
}
