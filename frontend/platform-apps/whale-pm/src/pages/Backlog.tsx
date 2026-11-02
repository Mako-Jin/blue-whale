// Backlog：工作项分页检索、创建/编辑、状态流转与删除

import { useCallback, useEffect, useState } from 'react';
import {
  App,
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
  Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { Link, useParams } from 'react-router-dom';
import {
  itemApi,
  sprintApi,
  workflowApi,
  type Sprint,
  type Status,
  type Transition,
  type WorkItem,
} from '../api/pm';
import {
  PRIORITY_OPTIONS,
  TYPE_OPTIONS,
  formatDate,
  hasPermission,
  priorityColor,
  priorityLabel,
  statusColor,
  typeLabel,
} from '../utils';
import { useWorkspace } from '../workspace-context';

export default function Backlog() {
  const { projectId = '' } = useParams();
  const { statuses } = useWorkspace();
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [transitionForm] = Form.useForm();
  const [records, setRecords] = useState<WorkItem[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [type, setType] = useState<string | undefined>();
  const [priority, setPriority] = useState<string | undefined>();
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [transitions, setTransitions] = useState<Transition[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<WorkItem | null>(null);
  const [transitionItem, setTransitionItem] = useState<WorkItem | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await itemApi.page({ projectId, current, size, keyword, type, priority });
      setRecords(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [projectId, current, size, keyword, type, priority, message]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    Promise.all([
      sprintApi.page({ projectId, current: 1, size: 100 }),
      workflowApi.transitions(projectId),
    ])
      .then(([sprintPage, transitionList]) => {
        setSprints(sprintPage.records);
        setTransitions(transitionList);
      })
      .catch((error: Error) => message.error(error.message));
  }, [projectId, message]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      type: 'TASK',
      priority: 'MEDIUM',
      statusId: statuses.find((status) => status.isDefault)?.id ?? statuses[0]?.id,
    });
    setModalOpen(true);
  };

  const openEdit = (record: WorkItem) => {
    setEditing(record);
    form.setFieldsValue({
      ...record,
      dueDate: record.dueDate ? dayjs(record.dueDate) : null,
      assigneeId: record.assigneeId ?? undefined,
      sprintId: record.sprintId ?? undefined,
      parentId: record.parentId ?? undefined,
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const payload = {
      projectId: Number(projectId),
      type: values.type,
      title: values.title,
      description: values.description,
      statusId: values.statusId ? Number(values.statusId) : undefined,
      priority: values.priority,
      assigneeId: values.assigneeId ? Number(values.assigneeId) : undefined,
      parentId: values.parentId ? Number(values.parentId) : undefined,
      sprintId: values.sprintId ? Number(values.sprintId) : undefined,
      storyPoints: values.storyPoints ?? undefined,
      dueDate: values.dueDate ? dayjs(values.dueDate).format('YYYY-MM-DD') : undefined,
      version: editing?.version,
    };
    try {
      if (editing) {
        await itemApi.update(editing.id, payload);
      } else {
        await itemApi.create(payload);
      }
      message.success(editing ? '工作项已更新' : '工作项已创建');
      setModalOpen(false);
      setEditing(null);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const openTransition = (record: WorkItem) => {
    setTransitionItem(record);
    transitionForm.resetFields();
  };

  const handleTransition = async () => {
    if (!transitionItem) {
      return;
    }
    const values = await transitionForm.validateFields();
    try {
      await itemApi.transition(transitionItem.id, {
        targetStatusId: values.targetStatusId,
        comment: values.comment,
      });
      message.success('状态已流转');
      setTransitionItem(null);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const handleDelete = async (record: WorkItem) => {
    try {
      await itemApi.remove(record.id);
      message.success('工作项已删除');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const targetOptions = (item: WorkItem) => {
    const allowed = transitions.filter((transition) => transition.fromStatusId === item.statusId);
    const source: Status[] = allowed.length
      ? statuses.filter((status) =>
          allowed.some((transition) => transition.toStatusId === status.id),
        )
      : statuses.filter((status) => status.id !== item.statusId);
    return source.map((status) => ({ value: status.id, label: status.name }));
  };

  const columns: ColumnsType<WorkItem> = [
    {
      title: '编号',
      dataIndex: 'key',
      width: 130,
      render: (value: string, record) => (
        <Link to={`/project/${projectId}/item/${record.id}`}>{value}</Link>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 90,
      render: (value: string) => <Tag>{typeLabel(value)}</Tag>,
    },
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      render: (value: string, record) => (
        <Link to={`/project/${projectId}/item/${record.id}`}>{value}</Link>
      ),
    },
    {
      title: '状态',
      dataIndex: 'statusName',
      width: 110,
      render: (value: string, record) => (
        <Tag color={statusColor(record.statusCategory)}>{value}</Tag>
      ),
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      width: 90,
      render: (value: string) => <Tag color={priorityColor(value)}>{priorityLabel(value)}</Tag>,
    },
    {
      title: '负责人',
      dataIndex: 'assigneeId',
      width: 100,
      render: (value?: string) => value ?? '-',
    },
    {
      title: '故事点',
      dataIndex: 'storyPoints',
      width: 90,
      render: (value?: number) => value ?? '-',
    },
    { title: '截止日期', dataIndex: 'dueDate', width: 120, render: formatDate },
    {
      title: '操作',
      key: 'action',
      width: 190,
      render: (_, record) => (
        <Space size={4}>
          {hasPermission('sys:pm:item:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          )}
          {hasPermission('sys:pm:item:transition') && (
            <Button type="link" size="small" onClick={() => openTransition(record)}>
              流转
            </Button>
          )}
          {hasPermission('sys:pm:item:delete') && (
            <Popconfirm title="确认删除该工作项？" onConfirm={() => void handleDelete(record)}>
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
    <div>
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          allowClear
          placeholder="搜索标题或编号"
          style={{ width: 240 }}
          onSearch={(value) => {
            setKeyword(value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="类型"
          style={{ width: 120 }}
          options={TYPE_OPTIONS}
          onChange={(value) => {
            setType(value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="优先级"
          style={{ width: 120 }}
          options={PRIORITY_OPTIONS}
          onChange={(value) => {
            setPriority(value);
            setCurrent(1);
          }}
        />
        {hasPermission('sys:pm:item:add') && (
          <Button type="primary" onClick={openCreate}>
            新建工作项
          </Button>
        )}
      </Space>
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={records}
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
        open={modalOpen}
        title={editing ? '编辑工作项' : '新建工作项'}
        width={640}
        onCancel={() => setModalOpen(false)}
        onOk={() => void handleSubmit()}
        okText="保存"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select options={TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input maxLength={200} showCount />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={4} maxLength={4000} showCount />
          </Form.Item>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="statusId" label="状态" style={{ flex: 1 }}>
              <Select
                options={statuses.map((status) => ({ value: status.id, label: status.name }))}
              />
            </Form.Item>
            <Form.Item name="priority" label="优先级" style={{ flex: 1 }}>
              <Select options={PRIORITY_OPTIONS} />
            </Form.Item>
            <Form.Item name="assigneeId" label="负责人ID" style={{ flex: 1 }}>
              <Input placeholder="数字用户ID" />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="sprintId" label="迭代" style={{ flex: 1 }}>
              <Select
                allowClear
                placeholder="未加入迭代"
                options={sprints.map((sprint) => ({ value: sprint.id, label: sprint.name }))}
              />
            </Form.Item>
            <Form.Item name="storyPoints" label="故事点" style={{ flex: 1 }}>
              <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="dueDate" label="截止日期" style={{ flex: 1 }}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="parentId" label="父工作项ID">
            <Input placeholder="可选，数字工作项ID" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={Boolean(transitionItem)}
        title={`流转：${transitionItem?.key ?? ''}`}
        onCancel={() => setTransitionItem(null)}
        onOk={() => void handleTransition()}
        okText="提交"
        cancelText="取消"
      >
        <Form form={transitionForm} layout="vertical">
          <Form.Item
            name="targetStatusId"
            label="目标状态"
            rules={[{ required: true, message: '请选择目标状态' }]}
          >
            <Select options={transitionItem ? targetOptions(transitionItem) : []} />
          </Form.Item>
          <Form.Item name="comment" label="备注">
            <Input.TextArea rows={3} maxLength={512} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
