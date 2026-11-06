// 课程管理列表页：分页 / 关键字检索 / 状态筛选 / 新建 / 发布·归档 / 删除。
//
// 权限码与基座菜单对齐（migration-5.87-lms-m0.sql：sys:lms:course:*）。

import { Button, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { changeCourseStatus, createCourse, deleteCourse, pageCourses, type CourseItem } from '../api/course';
import { hasPermission } from '../api/client';

/** 状态展示映射 */
const STATUS_TAG: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PUBLISHED: { color: 'green', label: '已发布' },
  ARCHIVED: { color: 'orange', label: '已归档' },
};

/** 列表行 */
interface Row extends CourseItem {
  key: string;
}

export default function CourseListPage() {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<Row[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [filters, setFilters] = useState<{ keyword?: string; status?: string }>({});
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);

  const canManage = hasPermission('sys:lms:course:create');
  const canStatus = hasPermission('sys:lms:course:status');
  const canDelete = hasPermission('sys:lms:course:delete');

  const load = useCallback(
    async (page = current, query = filters) => {
      setLoading(true);
      try {
        const data = await pageCourses({ ...query, current: page, size: 10 });
        setRows((data.records ?? []).map((item) => ({ ...item, key: String(item.id) })));
        setTotal(data.total ?? 0);
        setCurrent(page);
      } finally {
        setLoading(false);
      }
    },
    [current, filters],
  );

  useEffect(() => {
    void load(1, {});
    // 首次加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const refresh = () => load(current, filters);

  const onCreate = async (values: { courseCode?: string; title: string; category?: string; lecturer?: string }) => {
    setCreating(true);
    try {
      await createCourse(values);
      message.success('课程已创建（草稿态）');
      setCreateOpen(false);
      form.resetFields();
      await load(1, filters);
    } finally {
      setCreating(false);
    }
  };

  const onStatus = async (row: Row, action: 'PUBLISH' | 'ARCHIVE') => {
    await changeCourseStatus(row.id, action);
    message.success(action === 'PUBLISH' ? '课程已发布' : '课程已归档');
    refresh();
  };

  const onDelete = async (row: Row) => {
    await deleteCourse(row.id);
    message.success('课程已删除');
    refresh();
  };

  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="课程编码 / 名称"
          allowClear
          style={{ width: 240 }}
          onSearch={(keyword) => {
            const next = { ...filters, keyword: keyword || undefined };
            setFilters(next);
            void load(1, next);
          }}
        />
        <Select
          placeholder="状态"
          allowClear
          style={{ width: 120 }}
          options={[
            { value: 'DRAFT', label: '草稿' },
            { value: 'PUBLISHED', label: '已发布' },
            { value: 'ARCHIVED', label: '已归档' },
          ]}
          onChange={(status) => {
            const next = { ...filters, status: status || undefined };
            setFilters(next);
            void load(1, next);
          }}
        />
        {canManage && (
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            新建课程
          </Button>
        )}
      </Space>

      <Table<Row>
        rowKey="key"
        loading={loading}
        dataSource={rows}
        pagination={{ current, total, pageSize: 10, onChange: (page) => void load(page, filters) }}
        columns={[
          { title: '编码', dataIndex: 'courseCode', width: 140 },
          {
            title: '课程名称',
            dataIndex: 'title',
            render: (text: string, row) => (
              <a onClick={() => navigate(`/course/${row.id}`)}>{text}</a>
            ),
          },
          { title: '分类', dataIndex: 'category', width: 120 },
          { title: '讲师', dataIndex: 'lecturer', width: 120 },
          {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (status: string) => {
              const tag = STATUS_TAG[status] ?? { color: 'default', label: status };
              return <Tag color={tag.color}>{tag.label}</Tag>;
            },
          },
          {
            title: '操作',
            width: 260,
            render: (_, row) => (
              <Space size="small">
                <Button size="small" type="link" onClick={() => navigate(`/course/${row.id}`)}>
                  详情
                </Button>
                {canStatus && row.status === 'DRAFT' && (
                  <Popconfirm title="确认发布？发布前需至少 1 个章节" onConfirm={() => void onStatus(row, 'PUBLISH')}>
                    <Button size="small" type="link">
                      发布
                    </Button>
                  </Popconfirm>
                )}
                {canStatus && row.status === 'PUBLISHED' && (
                  <Popconfirm title="确认归档下架？" onConfirm={() => void onStatus(row, 'ARCHIVE')}>
                    <Button size="small" type="link">
                      归档
                    </Button>
                  </Popconfirm>
                )}
                {canDelete && row.status !== 'PUBLISHED' && (
                  <Popconfirm title="确认删除课程及其章节课件？" onConfirm={() => void onDelete(row)}>
                    <Button size="small" type="link" danger>
                      删除
                    </Button>
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title="新建课程"
        open={createOpen}
        confirmLoading={creating}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={onCreate}>
          <Form.Item name="courseCode" label="课程编码" rules={[{ required: true, message: '请输入课程编码' }]}>
            <Input placeholder="如 SAFE-01（自动大写归一）" />
          </Form.Item>
          <Form.Item name="title" label="课程名称" rules={[{ required: true, message: '请输入课程名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="category" label="分类">
            <Input />
          </Form.Item>
          <Form.Item name="lecturer" label="讲师">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
