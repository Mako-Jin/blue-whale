// 培训计划列表页：分页 / 关键字检索 / 状态筛选 / 新建 / 发布·结课 / 删除。
//
// 权限码与 migration-5.87-lms-m2.sql 对齐（sys:lms:training:*）。
// 状态口径：仅草稿可编辑与删除；发布前必须有场次；已结课为终态。

import { Button, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd';
import type { Dayjs } from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { hasPermission } from '../api/client';
import {
  changeTrainingClassStatus,
  createTrainingClass,
  deleteTrainingClass,
  pageTrainingClasses,
  type TrainingClassItem,
} from '../api/training';

/** 状态展示映射 */
const STATUS_TAG: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PUBLISHED: { color: 'green', label: '已发布' },
  CLOSED: { color: 'orange', label: '已结课' },
};

/** 形式展示映射 */
const MODE_TAG: Record<string, { color: string; label: string }> = {
  ONLINE: { color: 'blue', label: '线上' },
  OFFLINE: { color: 'geekblue', label: '面授' },
  BLENDED: { color: 'purple', label: '混合' },
};

/** 列表行 */
interface Row extends TrainingClassItem {
  key: string;
}

/** 新建表单值 */
interface CreateValues {
  classCode: string;
  title: string;
  mode?: string;
  lecturer?: string;
  period: [Dayjs, Dayjs];
  location?: string;
  capacity?: number;
  remark?: string;
}

export default function TrainingClassListPage() {
  const navigate = useNavigate();
  const [form] = Form.useForm<CreateValues>();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<Row[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [filters, setFilters] = useState<{ keyword?: string; status?: string; mode?: string }>({});
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);

  const canCreate = hasPermission('sys:lms:training:create');
  const canStatus = hasPermission('sys:lms:training:status');
  const canDelete = hasPermission('sys:lms:training:delete');

  const load = useCallback(
    async (page = current, query = filters) => {
      setLoading(true);
      try {
        const data = await pageTrainingClasses({ ...query, current: page, size: 10 });
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

  const onCreate = async (values: CreateValues) => {
    setCreating(true);
    try {
      await createTrainingClass({
        classCode: values.classCode,
        title: values.title,
        mode: values.mode,
        lecturer: values.lecturer,
        startDate: values.period[0].format('YYYY-MM-DD'),
        endDate: values.period[1].format('YYYY-MM-DD'),
        location: values.location,
        capacity: values.capacity,
        remark: values.remark,
      });
      message.success('培训班已创建（草稿态），请先添加场次再发布');
      setCreateOpen(false);
      form.resetFields();
      await load(1, filters);
    } finally {
      setCreating(false);
    }
  };

  const onStatus = async (row: Row, action: 'PUBLISH' | 'CLOSE') => {
    await changeTrainingClassStatus(row.id, action);
    message.success(action === 'PUBLISH' ? '培训班已发布' : '培训班已结课');
    refresh();
  };

  const onDelete = async (row: Row) => {
    await deleteTrainingClass(row.id);
    message.success('培训班已删除');
    refresh();
  };

  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="培训班编码 / 名称"
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
            { value: 'CLOSED', label: '已结课' },
          ]}
          onChange={(status) => {
            const next = { ...filters, status: status || undefined };
            setFilters(next);
            void load(1, next);
          }}
        />
        <Select
          placeholder="形式"
          allowClear
          style={{ width: 120 }}
          options={[
            { value: 'ONLINE', label: '线上' },
            { value: 'OFFLINE', label: '面授' },
            { value: 'BLENDED', label: '混合' },
          ]}
          onChange={(mode) => {
            const next = { ...filters, mode: mode || undefined };
            setFilters(next);
            void load(1, next);
          }}
        />
        {canCreate && (
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            新建培训班
          </Button>
        )}
      </Space>

      <Table<Row>
        rowKey="key"
        loading={loading}
        dataSource={rows}
        pagination={{ current, total, pageSize: 10, onChange: (page) => void load(page, filters) }}
        columns={[
          { title: '编码', dataIndex: 'classCode', width: 140 },
          {
            title: '培训班名称',
            dataIndex: 'title',
            render: (text: string, row) => <a onClick={() => navigate(`/training/${row.id}`)}>{text}</a>,
          },
          {
            title: '形式',
            dataIndex: 'mode',
            width: 90,
            render: (mode: string) => {
              const tag = MODE_TAG[mode] ?? { color: 'default', label: mode };
              return <Tag color={tag.color}>{tag.label}</Tag>;
            },
          },
          {
            title: '起止日期',
            dataIndex: 'startDate',
            width: 200,
            render: (_: string, row) => `${row.startDate} ~ ${row.endDate}`,
          },
          { title: '场次', dataIndex: 'sessionCount', width: 70 },
          {
            title: '已分配',
            dataIndex: 'assignedCount',
            width: 90,
            render: (count: number, row) => (row.capacity ? `${count ?? 0}/${row.capacity}` : count ?? 0),
          },
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
            width: 240,
            render: (_, row) => (
              <Space size="small">
                <Button size="small" type="link" onClick={() => navigate(`/training/${row.id}`)}>
                  详情
                </Button>
                {canStatus && row.status === 'DRAFT' && (
                  <Popconfirm title="确认发布？发布前需至少 1 个场次" onConfirm={() => void onStatus(row, 'PUBLISH')}>
                    <Button size="small" type="link">
                      发布
                    </Button>
                  </Popconfirm>
                )}
                {canStatus && row.status === 'PUBLISHED' && (
                  <Popconfirm title="确认结课？结课后为终态，不可再分配与签到" onConfirm={() => void onStatus(row, 'CLOSE')}>
                    <Button size="small" type="link">
                      结课
                    </Button>
                  </Popconfirm>
                )}
                {canDelete && row.status === 'DRAFT' && (
                  <Popconfirm title="确认删除培训班及其场次？" onConfirm={() => void onDelete(row)}>
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
        title="新建培训班"
        open={createOpen}
        confirmLoading={creating}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={onCreate} initialValues={{ mode: 'OFFLINE' }}>
          <Form.Item name="classCode" label="培训班编码" rules={[{ required: true, message: '请输入培训班编码' }]}>
            <Input placeholder="如 TR-2026-001（自动大写归一）" />
          </Form.Item>
          <Form.Item name="title" label="培训班名称" rules={[{ required: true, message: '请输入培训班名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="mode" label="形式">
            <Select
              options={[
                { value: 'OFFLINE', label: '面授' },
                { value: 'ONLINE', label: '线上' },
                { value: 'BLENDED', label: '混合' },
              ]}
            />
          </Form.Item>
          <Form.Item name="lecturer" label="主讲讲师">
            <Input />
          </Form.Item>
          <Form.Item name="period" label="起止日期" rules={[{ required: true, message: '请选择起止日期' }]}>
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="location" label="培训地点">
            <Input />
          </Form.Item>
          <Form.Item name="capacity" label="计划人数（留空为不限）">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
