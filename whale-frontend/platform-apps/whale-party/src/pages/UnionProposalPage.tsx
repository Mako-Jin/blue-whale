// 职代会提案（M-P3）：提交 → 立案 → 办理 → 反馈 → 评价（单向，评价后终态）。
//
// 管理端只展示可执行动作：立案（SUBMITTED）、办理（FILED）、反馈（HANDLING）；
// 「我的提案」给提交人提交、查看与评价，评价入口只在 FEEDBACK 出现——后端校验提交人本人，
// 前端不猜身份，只按状态收敛入口。

import {
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Rate,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useState, useEffect } from 'react';
import {
  createUnionProposal,
  feedbackUnionProposal,
  fileUnionProposal,
  getUnionProposal,
  getUnionProposalPortal,
  handleUnionProposal,
  pageUnionProposalPortal,
  pageUnionProposals,
  rateUnionProposal,
  type UnionProposalSaveCommand,
  type UnionProposalView,
} from '../api/party';
import { hasPermission } from '../api/client';

/** 每页条数 */
const PAGE_SIZE = 10;

/** 提案状态选项 */
const STATUS_OPTIONS = [
  { value: 'SUBMITTED', label: '已提交' },
  { value: 'FILED', label: '已立案' },
  { value: 'HANDLING', label: '办理中' },
  { value: 'FEEDBACK', label: '已反馈' },
  { value: 'RATED', label: '已评价' },
];

/** 状态标签与颜色 */
const STATUS_LABEL: Record<string, string> = {
  SUBMITTED: '已提交',
  FILED: '已立案',
  HANDLING: '办理中',
  FEEDBACK: '已反馈',
  RATED: '已评价',
};

/** 状态颜色 */
const STATUS_COLOR: Record<string, string> = {
  SUBMITTED: 'default',
  FILED: 'blue',
  HANDLING: 'orange',
  FEEDBACK: 'green',
  RATED: 'purple',
};

/** 管理动作 */
type ActionMode = 'FILE' | 'HANDLE' | 'FEEDBACK' | 'RATE';

/** 管理动作标题 */
const ACTION_TITLE: Record<ActionMode, string> = {
  FILE: '立案',
  HANDLE: '办理',
  FEEDBACK: '反馈办理结果',
  RATE: '评价提案',
};

/** 管理动作确认文案 */
const ACTION_OK_TEXT: Record<ActionMode, string> = {
  FILE: '确认立案',
  HANDLE: '确认办理',
  FEEDBACK: '提交反馈',
  RATE: '提交评价',
};

/** 管理动作表单值 */
interface ActionFormValues {
  handler?: string;
  feedback?: string;
  rating?: number;
}

/**
 * 格式化时间。
 *
 * @param value 时间文本
 * @returns 展示用时间
 */
function formatTime(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—';
}

/** 职代会提案页 */
export default function UnionProposalPage() {
  const canManage = hasPermission('sys:union:proposal:manage');
  const canList = hasPermission('sys:union:proposal:list');

  const [activeTab, setActiveTab] = useState(canList ? 'manage' : 'mine');

  const [rows, setRows] = useState<UnionProposalView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);

  const [myRows, setMyRows] = useState<UnionProposalView[]>([]);
  const [myTotal, setMyTotal] = useState(0);
  const [myCurrent, setMyCurrent] = useState(1);
  const [myLoading, setMyLoading] = useState(false);

  const [detail, setDetail] = useState<UnionProposalView | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [createForm] = Form.useForm<UnionProposalSaveCommand>();

  const [actionMode, setActionMode] = useState<ActionMode | null>(null);
  const [actionTarget, setActionTarget] = useState<UnionProposalView | null>(null);
  const [actionForm] = Form.useForm<ActionFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageUnionProposals({
        keyword: keyword || undefined,
        status,
        current,
        size: PAGE_SIZE,
      });
      setRows(page.records);
      setTotal(Number(page.total));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '提案列表加载失败');
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status]);

  const loadMine = useCallback(async () => {
    setMyLoading(true);
    try {
      const page = await pageUnionProposalPortal({ current: myCurrent, size: PAGE_SIZE });
      setMyRows(page.records);
      setMyTotal(Number(page.total));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '我的提案加载失败');
    } finally {
      setMyLoading(false);
    }
  }, [myCurrent]);

  useEffect(() => {
    if (activeTab === 'manage') {
      void load();
    }
  }, [activeTab, load]);

  useEffect(() => {
    if (activeTab === 'mine') {
      void loadMine();
    }
  }, [activeTab, loadMine]);

  const reloadCurrent = useCallback(async () => {
    if (activeTab === 'mine') {
      await loadMine();
    } else {
      await load();
    }
  }, [activeTab, load, loadMine]);

  const openDetail = async (row: UnionProposalView, portal: boolean) => {
    try {
      setDetail(portal ? await getUnionProposalPortal(row.id) : await getUnionProposal(row.id));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '提案详情加载失败');
    }
  };

  const openAction = (row: UnionProposalView, mode: ActionMode) => {
    setActionTarget(row);
    actionForm.resetFields();
    setActionMode(mode);
  };

  const submitProposal = async () => {
    const values = await createForm.validateFields();
    try {
      await createUnionProposal({ title: values.title, content: values.content });
      message.success('提案已提交');
      setCreateOpen(false);
      createForm.resetFields();
      await loadMine();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '提交失败');
    }
  };

  const confirmAction = async () => {
    if (!actionTarget || !actionMode) {
      return;
    }
    const values = await actionForm.validateFields();
    try {
      if (actionMode === 'FILE') {
        await fileUnionProposal(actionTarget.id, values.handler?.trim() || undefined);
      } else if (actionMode === 'HANDLE') {
        await handleUnionProposal(actionTarget.id, values.handler ?? '');
      } else if (actionMode === 'FEEDBACK') {
        await feedbackUnionProposal(actionTarget.id, values.feedback ?? '');
      } else {
        await rateUnionProposal(actionTarget.id, values.rating ?? 0);
      }
      message.success(`${ACTION_TITLE[actionMode]}已提交`);
      setActionMode(null);
      setActionTarget(null);
      actionForm.resetFields();
      await reloadCurrent();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '操作失败');
    }
  };

  const manageColumns: ColumnsType<UnionProposalView> = [
    { title: '提案编号', dataIndex: 'proposalNo', width: 130 },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    {
      title: '提交人',
      dataIndex: 'submitterName',
      width: 100,
      render: (value) => value ?? '—',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={STATUS_COLOR[value] ?? 'default'}>{STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    { title: '承办人', dataIndex: 'handler', width: 100, render: (value) => value ?? '—' },
    { title: '反馈', dataIndex: 'feedback', ellipsis: true, render: (value) => value ?? '—' },
    {
      title: '评分',
      dataIndex: 'rating',
      width: 80,
      render: (value?: number | null) => (value ? `${value} 星` : '—'),
    },
    {
      title: '提交时间',
      dataIndex: 'createTime',
      width: 160,
      render: (value?: string | null) => formatTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 190,
      render: (_value, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => void openDetail(row, false)}>
            查看
          </Button>
          {canManage && row.status === 'SUBMITTED' ? (
            <Button type="link" size="small" onClick={() => openAction(row, 'FILE')}>
              立案
            </Button>
          ) : null}
          {canManage && row.status === 'FILED' ? (
            <Button type="link" size="small" onClick={() => openAction(row, 'HANDLE')}>
              办理
            </Button>
          ) : null}
          {canManage && row.status === 'HANDLING' ? (
            <Button type="link" size="small" onClick={() => openAction(row, 'FEEDBACK')}>
              反馈
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  const myColumns: ColumnsType<UnionProposalView> = [
    { title: '提案编号', dataIndex: 'proposalNo', width: 130 },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={STATUS_COLOR[value] ?? 'default'}>{STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    { title: '反馈', dataIndex: 'feedback', ellipsis: true, render: (value) => value ?? '—' },
    {
      title: '评分',
      dataIndex: 'rating',
      width: 80,
      render: (value?: number | null) => (value ? `${value} 星` : '—'),
    },
    {
      title: '提交时间',
      dataIndex: 'createTime',
      width: 160,
      render: (value?: string | null) => formatTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      render: (_value, row) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => void openDetail(row, true)}>
            查看
          </Button>
          {row.status === 'FEEDBACK' ? (
            <Button type="link" size="small" onClick={() => openAction(row, 'RATE')}>
              评价
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  const manageTab = (
    <Card
      title="提案管理"
      size="small"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="提案编号 / 标题"
            style={{ width: 200 }}
            onSearch={(value) => {
              setCurrent(1);
              setKeyword(value.trim());
            }}
          />
          <Select
            allowClear
            placeholder="全部状态"
            style={{ width: 120 }}
            options={STATUS_OPTIONS}
            value={status}
            onChange={(value) => {
              setCurrent(1);
              setStatus(value);
            }}
          />
        </Space>
      }
    >
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={manageColumns}
        dataSource={rows}
        scroll={{ x: 1200 }}
        pagination={{
          current,
          pageSize: PAGE_SIZE,
          total,
          showSizeChanger: false,
          onChange: setCurrent,
        }}
      />
    </Card>
  );

  const mineTab = (
    <Card
      title="我的提案"
      size="small"
      extra={
        <Button type="primary" onClick={() => setCreateOpen(true)}>
          提交提案
        </Button>
      }
    >
      <Table
        rowKey="id"
        size="small"
        loading={myLoading}
        columns={myColumns}
        dataSource={myRows}
        scroll={{ x: 1000 }}
        locale={{ emptyText: '您还没有提交提案' }}
        pagination={{
          current: myCurrent,
          pageSize: PAGE_SIZE,
          total: myTotal,
          showSizeChanger: false,
          onChange: setMyCurrent,
        }}
      />
    </Card>
  );

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={
          canList
            ? [
                { key: 'manage', label: '提案管理', children: manageTab },
                { key: 'mine', label: '我的提案', children: mineTab },
              ]
            : [{ key: 'mine', label: '我的提案', children: mineTab }]
        }
      />

      <Modal
        title="提交提案"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => void submitProposal()}
        okText="提交"
        destroyOnClose
      >
        <Form form={createForm} layout="vertical" preserve={false}>
          <Form.Item
            name="title"
            label="提案标题"
            rules={[{ required: true, message: '请输入标题' }]}
          >
            <Input maxLength={200} showCount />
          </Form.Item>
          <Form.Item
            name="content"
            label="提案内容"
            rules={[{ required: true, message: '请输入内容' }]}
          >
            <Input.TextArea rows={6} maxLength={2000} showCount />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`${actionMode ? ACTION_TITLE[actionMode] : ''} · ${actionTarget?.title ?? ''}`}
        open={actionMode !== null}
        onCancel={() => setActionMode(null)}
        onOk={() => void confirmAction()}
        okText={actionMode ? ACTION_OK_TEXT[actionMode] : '确认'}
        destroyOnClose
      >
        <Form form={actionForm} layout="vertical" preserve={false}>
          {actionMode === 'FILE' || actionMode === 'HANDLE' ? (
            <Form.Item
              name="handler"
              label="承办人"
              rules={
                actionMode === 'HANDLE' ? [{ required: true, message: '请填写承办人' }] : undefined
              }
            >
              <Input maxLength={64} placeholder="办理部门 / 承办人" />
            </Form.Item>
          ) : null}
          {actionMode === 'FEEDBACK' ? (
            <Form.Item
              name="feedback"
              label="办理反馈"
              rules={[{ required: true, message: '请填写反馈内容' }]}
            >
              <Input.TextArea rows={5} maxLength={1000} showCount />
            </Form.Item>
          ) : null}
          {actionMode === 'RATE' ? (
            <Form.Item
              name="rating"
              label="满意度评分"
              rules={[{ required: true, message: '请选择评分' }]}
            >
              <Rate count={5} />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>

      <Drawer title="提案详情" width={640} open={detail !== null} onClose={() => setDetail(null)}>
        {detail ? (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Descriptions size="small" column={2}>
              <Descriptions.Item label="提案编号">{detail.proposalNo}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={STATUS_COLOR[detail.status] ?? 'default'}>
                  {STATUS_LABEL[detail.status] ?? detail.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="提交人">{detail.submitterName ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="提交时间">
                {formatTime(detail.createTime)}
              </Descriptions.Item>
              <Descriptions.Item label="承办人">{detail.handler ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="立案时间">{formatTime(detail.filedAt)}</Descriptions.Item>
              <Descriptions.Item label="办理时间">{formatTime(detail.handledAt)}</Descriptions.Item>
              <Descriptions.Item label="反馈时间">
                {formatTime(detail.feedbackAt)}
              </Descriptions.Item>
              <Descriptions.Item label="评分">
                {detail.rating ? <Rate disabled value={detail.rating} /> : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="评价时间">{formatTime(detail.ratedAt)}</Descriptions.Item>
            </Descriptions>
            <Typography.Title level={5} style={{ margin: 0 }}>
              {detail.title}
            </Typography.Title>
            <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
              {detail.content || '（暂无内容）'}
            </Typography.Paragraph>
            {detail.feedback ? (
              <>
                <Typography.Text strong>办理反馈</Typography.Text>
                <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                  {detail.feedback}
                </Typography.Paragraph>
              </>
            ) : null}
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
