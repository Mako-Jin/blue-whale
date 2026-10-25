// OA 请假列表：分页 + 单号/状态筛选；PENDING 可编辑、通过、驳回、删除
import { useCallback, useEffect, useState } from 'react';
import { App, Button, Input, Popconfirm, Select, Space, Table, Tag } from 'antd';
import type { TableProps } from 'antd';
import {
  deleteLeave,
  pageLeaves,
  updateLeaveStatus,
  type OaLeave,
  type OaLeaveStatus,
} from '../api/oa';

/** 状态文案 */
const STATUS_LABEL: Record<OaLeaveStatus, string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已撤销',
};

/** 状态颜色 */
const STATUS_COLOR: Record<OaLeaveStatus, string> = {
  PENDING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  CANCELLED: 'default',
};

/** 请假类型文案 */
const TYPE_LABEL: Record<string, string> = {
  ANNUAL: '年假',
  SICK: '病假',
  PERSONAL: '事假',
};

interface ListProps {
  /** 父组件保存成功后 +1，触发重新查询 */
  refreshKey: number;
  /** 新增入口 */
  onCreate: () => void;
  /** 编辑入口 */
  onEdit: (leave: OaLeave) => void;
}

interface ListQuery {
  current: number;
  size: number;
  code: string;
  status: OaLeaveStatus | '';
}

export default function List({ refreshKey, onCreate, onEdit }: ListProps) {
  const { message } = App.useApp();
  const [data, setData] = useState<OaLeave[]>([]);
  const [total, setTotal] = useState(0);
  const [query, setQuery] = useState<ListQuery>({ current: 1, size: 10, code: '', status: '' });
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageLeaves(query);
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '请假查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [query, message]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  /** 审批 / 撤销 */
  const changeStatus = async (record: OaLeave, status: OaLeaveStatus) => {
    try {
      await updateLeaveStatus(record.id, status);
      message.success(`已${STATUS_LABEL[status]}`);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '状态变更失败');
    }
  };

  /** 删除（逻辑删除） */
  const remove = async (record: OaLeave) => {
    try {
      await deleteLeave(record.id);
      message.success('已删除');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const columns: TableProps<OaLeave>['columns'] = [
    { title: '申请单号', dataIndex: 'code', key: 'code' },
    { title: '申请人', dataIndex: 'applicant', key: 'applicant', width: 120 },
    {
      title: '类型',
      dataIndex: 'leaveType',
      key: 'leaveType',
      width: 90,
      render: (value: string) => <Tag>{TYPE_LABEL[value] ?? value}</Tag>,
    },
    {
      title: '请假区间',
      key: 'range',
      width: 200,
      render: (_value, record) => `${record.startDate} ~ ${record.endDate}`,
    },
    { title: '天数', dataIndex: 'days', key: 'days', width: 80 },
    { title: '事由', dataIndex: 'reason', key: 'reason', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: OaLeaveStatus) => (
        <Tag color={STATUS_COLOR[value]}>{STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_value, record) => {
        const pending = record.status === 'PENDING';
        return (
          <Space size={4}>
            <Button type="link" size="small" disabled={!pending} onClick={() => onEdit(record)}>
              编辑
            </Button>
            <Popconfirm
              title="确认通过该申请？"
              disabled={!pending}
              onConfirm={() => void changeStatus(record, 'APPROVED')}
            >
              <Button type="link" size="small" disabled={!pending}>
                通过
              </Button>
            </Popconfirm>
            <Popconfirm
              title="确认驳回该申请？"
              disabled={!pending}
              onConfirm={() => void changeStatus(record, 'REJECTED')}
            >
              <Button type="link" size="small" danger disabled={!pending}>
                驳回
              </Button>
            </Popconfirm>
            <Popconfirm
              title="确认删除该申请？"
              disabled={!pending}
              onConfirm={() => void remove(record)}
            >
              <Button type="link" size="small" danger disabled={!pending}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="搜索申请单号"
          style={{ width: 220 }}
          onSearch={(value) => setQuery((prev) => ({ ...prev, current: 1, code: value.trim() }))}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 140 }}
          value={query.status || undefined}
          options={Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label }))}
          onChange={(value) =>
            setQuery((prev) => ({ ...prev, current: 1, status: (value as OaLeaveStatus) ?? '' }))
          }
        />
        <Button type="primary" onClick={onCreate}>
          新增申请
        </Button>
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<OaLeave>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={{
          current: query.current,
          pageSize: query.size,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) =>
            setQuery((prev) => ({ ...prev, current: page, size: pageSize })),
        }}
      />
    </Space>
  );
}
