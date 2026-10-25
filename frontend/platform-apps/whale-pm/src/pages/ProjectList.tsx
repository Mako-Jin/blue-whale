// 项目列表：分页检索、创建/编辑、归档/恢复与删除

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
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
import type { ColumnsType } from 'antd/es/table';
import { useNavigate } from 'react-router-dom';
import { projectApi, type Project } from '../api/pm';
import { formatTime, hasPermission, projectStatusLabel } from '../utils';

const PROJECT_TYPES = [
  { value: 'SCRUM', label: 'Scrum' },
  { value: 'KANBAN', label: 'Kanban' },
];

const VISIBILITY_OPTIONS = [
  { value: 'PRIVATE', label: '私有' },
  { value: 'TEAM', label: '团队可见' },
];

export default function ProjectList() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [form] = Form.useForm<Partial<Project>>();
  const [records, setRecords] = useState<Project[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Project | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await projectApi.page({ current, size, keyword, status });
      setRecords(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [current, size, keyword, status, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ projectType: 'SCRUM', visibility: 'PRIVATE' });
    setModalOpen(true);
  };

  const openEdit = (record: Project) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    try {
      const payload = { ...values, leadUserId: values.leadUserId ? Number(values.leadUserId) : undefined };
      if (editing) {
        await projectApi.update(editing.id, payload);
      } else {
        await projectApi.create(payload);
      }
      message.success(editing ? '项目已更新' : '项目已创建');
      setModalOpen(false);
      setEditing(null);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const toggleArchive = async (record: Project) => {
    try {
      await projectApi.changeStatus(record.id, record.status === 'ARCHIVED' ? 'ACTIVE' : 'ARCHIVED');
      message.success(record.status === 'ARCHIVED' ? '项目已恢复' : '项目已归档');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const handleDelete = async (record: Project) => {
    try {
      await projectApi.remove(record.id);
      message.success('项目已删除');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const columns: ColumnsType<Project> = [
    {
      title: '项目键',
      dataIndex: 'key',
      width: 100,
      render: (value: string, record) => (
        <Typography.Link onClick={() => navigate(`/project/${record.id}/overview`)}>{value}</Typography.Link>
      ),
    },
    { title: '项目名称', dataIndex: 'name' },
    { title: '类型', dataIndex: 'projectType', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={value === 'ARCHIVED' ? 'default' : 'green'}>{projectStatusLabel(value)}</Tag>
      ),
    },
    { title: '负责人', dataIndex: 'leadUserId', width: 100, render: (value?: string) => value ?? '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 170, render: formatTime },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_, record) => (
        <Space size={8}>
          <Button type="link" size="small" onClick={() => navigate(`/project/${record.id}/overview`)}>
            进入
          </Button>
          {hasPermission('sys:pm:project:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          )}
          {hasPermission('sys:pm:project:edit') && (
            <Button type="link" size="small" onClick={() => void toggleArchive(record)}>
              {record.status === 'ARCHIVED' ? '恢复' : '归档'}
            </Button>
          )}
          {hasPermission('sys:pm:project:delete') && (
            <Popconfirm title="确认删除项目？" description="成员、状态与流转配置将一并删除" onConfirm={() => void handleDelete(record)}>
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
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16 }} wrap>
        <Typography.Title level={4} style={{ margin: 0 }}>
          项目管理
        </Typography.Title>
        <Input.Search
          allowClear
          placeholder="搜索项目键或名称"
          style={{ width: 240 }}
          onSearch={(value) => {
            setKeyword(value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="项目状态"
          style={{ width: 140 }}
          options={[
            { value: 'ACTIVE', label: '进行中' },
            { value: 'ARCHIVED', label: '已归档' },
          ]}
          onChange={(value) => {
            setStatus(value);
            setCurrent(1);
          }}
        />
        {hasPermission('sys:pm:project:add') && (
          <Button type="primary" onClick={openCreate}>
            新建项目
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
        title={editing ? '编辑项目' : '新建项目'}
        onCancel={() => setModalOpen(false)}
        onOk={() => void handleSubmit()}
        okText="保存"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="key" label="项目键" rules={[{ required: true, message: '请输入项目键' }]}>
            <Input placeholder="2–10 位大写字母开头，如 BW" disabled={Boolean(editing)} />
          </Form.Item>
          <Form.Item name="name" label="项目名称" rules={[{ required: true, message: '请输入项目名称' }]}>
            <Input placeholder="项目名称" />
          </Form.Item>
          <Form.Item name="projectType" label="项目类型" rules={[{ required: true }]}>
            <Select options={PROJECT_TYPES} />
          </Form.Item>
          <Form.Item name="visibility" label="可见性">
            <Select options={VISIBILITY_OPTIONS} />
          </Form.Item>
          <Form.Item name="leadUserId" label="负责人用户ID">
            <Input placeholder="数字用户ID" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} maxLength={512} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
