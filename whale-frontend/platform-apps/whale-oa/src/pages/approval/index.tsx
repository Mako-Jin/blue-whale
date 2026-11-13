// 审批中心：我发起 / 待我审批 / 我已审批 / 抄送我的 四类工作台
//
// 列表事实源在 Workflow 内网门面；业务标题与单号由 OA 本地审批实例批量补全。
// 抄送列表点击未读记录时先标记已读，再打开详情。

import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Empty, Input, Space, Table, Tabs, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import ApprovalDetailDrawer from './detail';
import { markCcRead, pageApproved, pageCc, pageInitiated, pagePending } from '../../api/approval';
import type { ApprovalCcRow, ApprovalInstanceRow, ApprovalTaskRow } from '../../api/approval';

/** 当前页签 */
type CenterTab = 'initiated' | 'pending' | 'approved' | 'cc';

/** 列表行（四类视图字段并集） */
interface CenterRow {
  key: string;
  workflowInstanceId: number;
  title?: string;
  initiator?: string;
  statusText?: string;
  nodeName?: string;
  createTime?: string;
  canOperate?: boolean;
  readFlag?: number;
  ccId?: number;
}

/** 页签定义 */
const TABS: Array<{ key: CenterTab; label: string }> = [
  { key: 'pending', label: '待我审批' },
  { key: 'initiated', label: '我发起的' },
  { key: 'approved', label: '我已审批' },
  { key: 'cc', label: '抄送我的' },
];

/** 实例状态文案 */
const INSTANCE_STATUS: Record<number, string> = {
  0: '运行中',
  1: '已通过',
  2: '已驳回',
  3: '已撤销',
};

/** 本地审批状态文案 */
const LOCAL_STATUS: Record<string, string> = {
  RUNNING: '运行中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已撤销',
  BROKEN: '异常',
};

/** 时间展示 */
function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-';
}

/** 实例行转列表行 */
function fromInstance(row: ApprovalInstanceRow): CenterRow {
  return {
    key: `instance-${row.workflowInstanceId}`,
    workflowInstanceId: row.workflowInstanceId,
    title: row.businessTitle ?? row.title,
    initiator: row.initiator,
    statusText:
      row.statusName ?? (row.status === undefined ? undefined : INSTANCE_STATUS[row.status]),
    nodeName: row.currentNodeKey,
    createTime: row.createTime,
  };
}

/** 任务行转列表行 */
function fromTask(row: ApprovalTaskRow, kind: 'pending' | 'approved'): CenterRow {
  return {
    key: `task-${kind}-${row.id}`,
    workflowInstanceId: row.instanceId,
    title: row.businessTitle,
    initiator: row.initiatorName,
    statusText: row.instanceStatus
      ? (LOCAL_STATUS[row.instanceStatus] ?? row.instanceStatus)
      : undefined,
    nodeName: row.nodeName,
    createTime: row.createTime,
    canOperate: row.canOperate,
  };
}

/** 抄送行转列表行 */
function fromCc(row: ApprovalCcRow): CenterRow {
  return {
    key: `cc-${row.id}`,
    workflowInstanceId: row.instanceId,
    title: row.businessTitle,
    initiator: row.initiatorName,
    statusText: row.instanceStatus
      ? (LOCAL_STATUS[row.instanceStatus] ?? row.instanceStatus)
      : undefined,
    nodeName: row.nodeName,
    createTime: row.createTime,
    readFlag: row.readFlag,
    ccId: row.id,
  };
}

export default function ApprovalCenter() {
  const [activeTab, setActiveTab] = useState<CenterTab>('pending');
  const [rows, setRows] = useState<CenterRow[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = { current, size, keyword: keyword.trim() || undefined };
      if (activeTab === 'pending') {
        const result = await pagePending(params);
        setRows(result.records.map((row) => fromTask(row, 'pending')));
        setTotal(result.total);
      } else if (activeTab === 'approved') {
        const result = await pageApproved(params);
        setRows(result.records.map((row) => fromTask(row, 'approved')));
        setTotal(result.total);
      } else if (activeTab === 'cc') {
        const result = await pageCc(params);
        setRows(result.records.map(fromCc));
        setTotal(result.total);
      } else {
        const result = await pageInitiated(params);
        setRows(result.records.map(fromInstance));
        setTotal(result.total);
      }
    } catch (err) {
      message.error(err instanceof Error ? err.message : '加载审批列表失败');
    } finally {
      setLoading(false);
    }
  }, [activeTab, current, keyword, size]);

  useEffect(() => {
    void load();
  }, [load]);

  /** 切换页签时重置分页 */
  const changeTab = (key: string) => {
    setActiveTab(key as CenterTab);
    setCurrent(1);
    setRows([]);
    setTotal(0);
  };

  /** 打开详情：抄送未读先标记已读 */
  const openDetail = async (row: CenterRow) => {
    if (activeTab === 'cc' && row.readFlag === 0 && row.ccId) {
      try {
        await markCcRead(row.ccId);
        void load();
      } catch (err) {
        message.warning(err instanceof Error ? err.message : '标记抄送已读失败');
      }
    }
    setDetailId(row.workflowInstanceId);
    setDetailOpen(true);
  };

  const columns: ColumnsType<CenterRow> = [
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      render: (value: string | undefined) => value || '-',
    },
    {
      title: '发起人',
      dataIndex: 'initiator',
      width: 110,
      render: (value: string | undefined) => value || '-',
    },
    {
      title: '状态',
      dataIndex: 'statusText',
      width: 100,
      render: (value: string | undefined) =>
        value ? <Tag color={value === '运行中' ? 'processing' : 'default'}>{value}</Tag> : '-',
    },
    {
      title: activeTab === 'pending' || activeTab === 'approved' ? '审批节点' : '当前节点',
      dataIndex: 'nodeName',
      width: 130,
      render: (value: string | undefined) => value || '-',
    },
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 170,
      render: (value: string | undefined) => formatTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_, row) => (
        <Button type="link" onClick={() => void openDetail(row)}>
          查看
        </Button>
      ),
    },
    ...(activeTab === 'cc'
      ? [
          {
            title: '已读',
            dataIndex: 'readFlag',
            width: 70,
            render: (value: number | undefined) => (value === 1 ? '已读' : '未读'),
          },
        ]
      : []),
  ];

  return (
    <Card
      title="审批中心"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="搜索标题或业务单号"
            style={{ width: 240 }}
            onSearch={(value) => {
              setKeyword(value);
              setCurrent(1);
            }}
          />
          <Button onClick={() => void load()}>刷新</Button>
        </Space>
      }
    >
      <Tabs
        activeKey={activeTab}
        onChange={changeTab}
        items={TABS.map((tab) => ({ key: tab.key, label: tab.label }))}
      />
      <Table<CenterRow>
        rowKey="key"
        loading={loading}
        columns={columns}
        dataSource={rows}
        rowClassName={(row) =>
          activeTab === 'cc' && row.readFlag === 0 ? 'approval-unread-row' : ''
        }
        locale={{
          emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无审批记录" />,
        }}
        pagination={{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (page, pageSize) => {
            setCurrent(page);
            setSize(pageSize);
          },
        }}
      />
      <ApprovalDetailDrawer
        workflowInstanceId={detailId}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        onChanged={() => void load()}
      />
    </Card>
  );
}
