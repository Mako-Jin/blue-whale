// 管理台：审核台 / 举报工单 / 敏感词库 / 运维
//
// 四个 Tab 对应四组权限（manage:audit / manage:dispose / manage:board / manage:ops）：
// 后端逐接口校验，无权限时返回 403，前端不做「隐藏入口」以外的判断——
// 隐藏入口只影响观感，真正的门在服务端。

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
  Empty,
  Form,
  Input,
  Modal,
  Pagination,
  Segmented,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  deleteSensitiveWord,
  fetchAdminAppeals,
  fetchAuditPage,
  fetchReports,
  fetchSearchStatus,
  fetchSensitiveWords,
  flushNotify,
  handleAppeal,
  handleReport,
  passAudit,
  reconcileCounts,
  rejectAudit,
  reindexSearch,
  reloadSensitiveWords,
  saveSensitiveWord,
  type AppealItem,
  type AuditItem,
  type ReconcileReport,
  type ReportItem,
  type SearchStatus,
  type SensitiveWordItem,
} from '../api/forum';
import { hasPermission } from '../api/client';
import AppealMaterials from '../components/AppealMaterials';

const { Text, Paragraph } = Typography;

/** 页大小 */
const PAGE_SIZE = 10;

/** 举报状态配色 */
function reportStatusTag(status: string) {
  switch (status) {
    case 'PENDING':
      return <Tag color="orange">待受理</Tag>;
    case 'HANDLING':
      return <Tag color="blue">处理中</Tag>;
    case 'RESOLVED':
      return <Tag color="green">已处置</Tag>;
    case 'REJECTED':
      return <Tag>不成立</Tag>;
    default:
      return <Tag>{status}</Tag>;
  }
}

/** 词条动作配色 */
function actionTag(action: string) {
  if (action === 'BLOCK') {
    return <Tag color="red">拦截</Tag>;
  }
  if (action === 'PENDING') {
    return <Tag color="orange">转待审</Tag>;
  }
  return <Tag color="blue">打码</Tag>;
}

export default function AdminPage() {
  return (
    <div className="forum-admin">
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        论坛管理台
      </Typography.Title>
      <Tabs
        items={[
          { key: 'audit', label: '内容审核台', children: <AuditPanel /> },
          { key: 'report', label: '举报工单', children: <ReportPanel /> },
          { key: 'appeal', label: '申诉工单', children: <AppealPanel /> },
          { key: 'word', label: '敏感词库', children: <WordPanel /> },
          { key: 'ops', label: '运维', children: <OpsPanel /> },
        ]}
      />
    </div>
  );
}

/** 审核台：待审帖子/楼层，通过或驳回（驳回必填意见） */
function AuditPanel() {
  const [targetType, setTargetType] = useState<'THREAD' | 'POST'>('THREAD');
  const [rows, setRows] = useState<AuditItem[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);
  const [rejecting, setRejecting] = useState<AuditItem | null>(null);
  const [remark, setRemark] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(
    (page: number, type: 'THREAD' | 'POST') => {
      setLoading(true);
      fetchAuditPage(type, page, PAGE_SIZE)
        .then((data) => {
          setRows(data.records);
          setTotal(data.total);
        })
        .catch((err: unknown) => {
          message.error(err instanceof Error ? err.message : '加载待审队列失败');
        })
        .finally(() => setLoading(false));
    },
    [],
  );

  useEffect(() => {
    load(current, targetType);
  }, [current, targetType, load]);

  const approve = async (row: AuditItem) => {
    try {
      await passAudit({ targetType: row.targetType, targetId: row.targetId });
      message.success('已通过，内容已发布');
      load(current, targetType);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '操作失败');
    }
  };

  const doReject = async () => {
    if (!rejecting) {
      return;
    }
    if (!remark.trim()) {
      message.warning('驳回必须填写意见，否则用户无从修改');
      return;
    }
    setSubmitting(true);
    try {
      await rejectAudit({
        targetType: rejecting.targetType,
        targetId: rejecting.targetId,
        remark: remark.trim(),
      });
      message.success('已驳回并通知作者');
      setRejecting(null);
      setRemark('');
      load(current, targetType);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '操作失败');
    } finally {
      setSubmitting(false);
    }
  };

  const columns: ColumnsType<AuditItem> = [
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '内容摘要', dataIndex: 'summary', ellipsis: true },
    { title: '作者', dataIndex: 'authorId', width: 140 },
    { title: '楼层', dataIndex: 'floor', width: 70 },
    { title: '提交时间', dataIndex: 'createTime', width: 180 },
    {
      title: '操作',
      width: 160,
      render: (_, row) => (
        <Space>
          <Button size="small" type="primary" onClick={() => approve(row)}>
            通过
          </Button>
          <Button size="small" danger onClick={() => setRejecting(row)}>
            驳回
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Segmented
          value={targetType}
          onChange={(value) => {
            setCurrent(1);
            setTargetType(value as 'THREAD' | 'POST');
          }}
          options={[
            { label: '帖子', value: 'THREAD' },
            { label: '楼层', value: 'POST' },
          ]}
        />
        <Text type="secondary">共 {total} 条待审</Text>
        <Button size="small" onClick={() => load(current, targetType)}>
          刷新
        </Button>
      </Space>
      <Table
        rowKey="targetId"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: <Empty description="没有待审内容" /> }}
      />
      <Pagination
        style={{ marginTop: 12, textAlign: 'right' }}
        current={current}
        pageSize={PAGE_SIZE}
        total={total}
        onChange={(page) => setCurrent(page)}
        showSizeChanger={false}
      />

      <Modal
        open={rejecting !== null}
        title="驳回并说明原因"
        okText="确认驳回"
        okButtonProps={{ danger: true, loading: submitting }}
        onOk={doReject}
        onCancel={() => {
          setRejecting(null);
          setRemark('');
        }}
      >
        <Input.TextArea
          rows={4}
          value={remark}
          onChange={(event) => setRemark(event.target.value)}
          placeholder="必填：告诉作者哪里不合规，便于修改后重投"
          maxLength={512}
          showCount
        />
      </Modal>
    </div>
  );
}

/** 申诉状态标签 */
function appealStatusTag(status: string) {
  switch (status) {
    case 'PENDING':
      return <Tag color="orange">待复核</Tag>;
    case 'APPROVED':
      return <Tag color="green">已通过</Tag>;
    case 'REJECTED':
      return <Tag>已驳回</Tag>;
    default:
      return <Tag>{status}</Tag>;
  }
}

/** 申诉对象展示名 */
function appealTargetLabel(item: AppealItem) {
  const type = item.targetType === 'BAN' ? '禁言' : item.targetType === 'THREAD' ? '帖子' : '楼层';
  return `${type} #${item.targetId}`;
}

/**
 * 申诉工单复核。
 *
 * <p>通过/驳回只改工单状态；仅禁言申诉通过会由服务端自动解禁，
 * 所以按钮文案要提示这一点，避免版主误以为会顺带恢复帖子。</p>
 */
function AppealPanel() {
  const [items, setItems] = useState<AppealItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState('PENDING');
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState<AppealItem | null>(null);
  const [form] = Form.useForm<{ approved: string; remark?: string }>();

  const load = useCallback(() => {
    setLoading(true);
    fetchAdminAppeals(status || undefined, page, PAGE_SIZE)
      .then((result) => {
        setItems(result.records);
        setTotal(Number(result.total));
      })
      .catch(() => {
        setItems([]);
        setTotal(0);
      })
      .finally(() => setLoading(false));
  }, [status, page]);

  useEffect(() => {
    load();
  }, [load]);

  /** 打开复核弹窗：默认通过，意见从空开始 */
  const openHandle = (item: AppealItem) => {
    setCurrent(item);
    form.setFieldsValue({ approved: 'APPROVED', remark: '' });
  };

  const submitHandle = async () => {
    const values = await form.validateFields();
    if (!current) {
      return;
    }
    try {
      await handleAppeal(current.id, values.approved === 'APPROVED', values.remark?.trim());
      void message.success('已复核');
      setCurrent(null);
      load();
    } catch (err: unknown) {
      void message.error(err instanceof Error ? err.message : '复核失败');
    }
  };

  const columns: ColumnsType<AppealItem> = [
    { title: '申诉人', dataIndex: 'userId', width: 150, render: (value: string) => `鲸友 #${value}` },
    { title: '对象', width: 150, render: (_, row) => appealTargetLabel(row) },
    { title: '理由', dataIndex: 'reason', ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 100, render: (value: string) => appealStatusTag(value) },
    { title: '提交时间', dataIndex: 'createTime', width: 180 },
    {
      title: '操作',
      width: 150,
      render: (_, row) =>
        row.status === 'PENDING' ? (
          <Button size="small" onClick={() => openHandle(row)}>
            复核
          </Button>
        ) : (
          <Text type="secondary" ellipsis>
            {row.handleRemark || '—'}
          </Text>
        ),
    },
  ];

  return (
    <>
      <Space style={{ marginBottom: 12 }}>
        <Segmented
          value={status}
          options={[
            { label: '待复核', value: 'PENDING' },
            { label: '已通过', value: 'APPROVED' },
            { label: '已驳回', value: 'REJECTED' },
            { label: '全部', value: '' },
          ]}
          onChange={(value) => {
            setStatus(String(value));
            setPage(1);
          }}
        />
      </Space>
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={items}
        pagination={false}
        size="small"
      />
      <Pagination
        style={{ marginTop: 12, textAlign: 'right' }}
        current={page}
        pageSize={PAGE_SIZE}
        total={total}
        showSizeChanger={false}
        onChange={(next) => setPage(next)}
      />
      <Modal
        title="复核申诉"
        open={current !== null}
        onOk={submitHandle}
        onCancel={() => setCurrent(null)}
        okText="提交复核"
        cancelText="取消"
        destroyOnClose
      >
        {current ? (
          <>
            <Paragraph type="secondary">
              {appealTargetLabel(current)}｜申诉理由：{current.reason}
            </Paragraph>
            {current.materials && current.materials.length > 0 ? (
              <div style={{ marginBottom: 12 }}>
                <Text strong>申诉材料</Text>
                <div style={{ marginTop: 6 }}>
                  <AppealMaterials materials={current.materials} />
                </div>
              </div>
            ) : null}
          </>
        ) : null}
        <Form form={form} layout="vertical">
          <Form.Item name="approved" label="结论" rules={[{ required: true }]}>
            <Segmented
              options={[
                { label: '通过', value: 'APPROVED' },
                { label: '驳回', value: 'REJECTED' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="remark"
            label="复核意见"
            extra="通过禁言申诉会自动解禁；帖子 / 楼层需到处置模块手工恢复"
          >
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}

/** 举报工单：结案 + 可选处置（删帖/禁言/封号） */
function ReportPanel() {
  const [status, setStatus] = useState('PENDING');
  const [rows, setRows] = useState<ReportItem[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);
  const [handling, setHandling] = useState<ReportItem | null>(null);
  const [form] = Form.useForm<{ status: string; action: string; muteDays: number; remark: string }>();
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback((page: number, state: string) => {
    setLoading(true);
    fetchReports(state, page, PAGE_SIZE)
      .then((data) => {
        setRows(data.records);
        setTotal(data.total);
      })
      .catch((err: unknown) => {
        message.error(err instanceof Error ? err.message : '加载工单失败');
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load(current, status);
  }, [current, status, load]);

  const submit = async () => {
    if (!handling) {
      return;
    }
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await handleReport(handling.id, {
        status: values.status,
        remark: values.remark,
        action: values.action ?? 'NONE',
        muteDays: values.muteDays,
      });
      message.success('已结案，举报人将收到通知');
      setHandling(null);
      form.resetFields();
      load(current, status);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '处理失败');
    } finally {
      setSubmitting(false);
    }
  };

  const columns: ColumnsType<ReportItem> = [
    { title: '被举报对象', dataIndex: 'targetTitle', ellipsis: true },
    { title: '类型', dataIndex: 'targetType', width: 90 },
    { title: '举报类型', dataIndex: 'reportType', width: 110 },
    { title: '说明', dataIndex: 'description', ellipsis: true },
    { title: '举报人', dataIndex: 'reporterId', width: 130 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => reportStatusTag(value),
    },
    { title: '提交时间', dataIndex: 'createTime', width: 180 },
    {
      title: '操作',
      width: 100,
      render: (_, row) =>
        row.status === 'PENDING' || row.status === 'HANDLING' ? (
          <Button size="small" type="primary" onClick={() => setHandling(row)}>
            处理
          </Button>
        ) : (
          <Text type="secondary">已结案</Text>
        ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Select
          style={{ width: 160 }}
          value={status}
          onChange={(value) => {
            setCurrent(1);
            setStatus(value);
          }}
          options={[
            { label: '待受理', value: 'PENDING' },
            { label: '已处置', value: 'RESOLVED' },
            { label: '不成立', value: 'REJECTED' },
            { label: '全部', value: '' },
          ]}
        />
        <Text type="secondary">共 {total} 条</Text>
        <Button size="small" onClick={() => load(current, status)}>
          刷新
        </Button>
        <Button
          size="small"
          onClick={() => {
            flushNotify()
              .then((count) => message.success(`已补投 ${count} 条通知`))
              .catch((err: unknown) => message.error(err instanceof Error ? err.message : '补投失败'));
          }}
        >
          补投通知
        </Button>
      </Space>
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: <Empty description="没有工单" /> }}
      />
      <Pagination
        style={{ marginTop: 12, textAlign: 'right' }}
        current={current}
        pageSize={PAGE_SIZE}
        total={total}
        onChange={(page) => setCurrent(page)}
        showSizeChanger={false}
      />

      <Modal
        open={handling !== null}
        title="处理举报工单"
        okText="提交结案"
        okButtonProps={{ loading: submitting }}
        onOk={submit}
        onCancel={() => {
          setHandling(null);
          form.resetFields();
        }}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ status: 'RESOLVED', action: 'NONE', muteDays: 1 }}
        >
          <Form.Item name="status" label="结案结论" rules={[{ required: true }]}>
            <Select
              options={[
                { label: '举报成立（已处置）', value: 'RESOLVED' },
                { label: '举报不成立', value: 'REJECTED' },
              ]}
            />
          </Form.Item>
          <Form.Item name="action" label="附带处置">
            <Select
              options={[
                { label: '不处置（仅结案）', value: 'NONE' },
                { label: '删除内容', value: 'REMOVE' },
                { label: '禁言用户', value: 'MUTE' },
                { label: '封禁用户', value: 'BAN' },
              ]}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.action !== next.action}>
            {({ getFieldValue }) =>
              getFieldValue('action') === 'MUTE' || getFieldValue('action') === 'BAN' ? (
                <Form.Item name="muteDays" label="天数（0 表示永久）">
                  <Input type="number" min={0} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item
            name="remark"
            label="处理意见"
            rules={[{ required: true, message: '请填写处理意见（会通知举报人）' }]}
          >
            <Input.TextArea rows={3} maxLength={512} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

/** 敏感词库：增删查 + 重载缓存 */
function WordPanel() {
  const [keyword, setKeyword] = useState('');
  const [rows, setRows] = useState<SensitiveWordItem[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm<{ word: string; action: string; category?: string }>();

  const load = useCallback((page: number, key: string) => {
    setLoading(true);
    fetchSensitiveWords(key, page, PAGE_SIZE)
      .then((data) => {
        setRows(data.records);
        setTotal(data.total);
      })
      .catch((err: unknown) => {
        message.error(err instanceof Error ? err.message : '加载词库失败');
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load(current, keyword);
  }, [current, keyword, load]);

  const add = async () => {
    const values = await form.validateFields();
    try {
      await saveSensitiveWord(values);
      message.success('已保存，词库已即时生效');
      form.resetFields();
      load(current, keyword);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存失败');
    }
  };

  const columns: ColumnsType<SensitiveWordItem> = [
    { title: '词', dataIndex: 'word' },
    {
      title: '动作',
      dataIndex: 'action',
      width: 110,
      render: (value: string) => actionTag(value),
    },
    { title: '分类', dataIndex: 'category', width: 120 },
    { title: '创建时间', dataIndex: 'createTime', width: 180 },
    {
      title: '操作',
      width: 90,
      render: (_, row) => (
        <Button
          size="small"
          danger
          onClick={() => {
            deleteSensitiveWord(row.id)
              .then(() => {
                message.success('已删除');
                load(current, keyword);
              })
              .catch((err: unknown) => message.error(err instanceof Error ? err.message : '删除失败'));
          }}
        >
          删除
        </Button>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="搜索词条"
          allowClear
          style={{ width: 200 }}
          onSearch={(value) => {
            setCurrent(1);
            setKeyword(value);
          }}
        />
        <Button
          size="small"
          onClick={() => {
            reloadSensitiveWords()
              .then((count) => message.success(`词库已重载，共 ${count} 条`))
              .catch((err: unknown) => message.error(err instanceof Error ? err.message : '重载失败'));
          }}
        >
          重载缓存
        </Button>
        <Text type="secondary">共 {total} 条</Text>
      </Space>
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: <Empty description="没有词条" /> }}
      />
      <Pagination
        style={{ marginTop: 12, textAlign: 'right' }}
        current={current}
        pageSize={PAGE_SIZE}
        total={total}
        onChange={(page) => setCurrent(page)}
        showSizeChanger={false}
      />

      <Form form={form} layout="inline" style={{ marginTop: 16 }} initialValues={{ action: 'BLOCK' }}>
        <Form.Item name="word" rules={[{ required: true, message: '请输入敏感词' }]}>
          <Input placeholder="敏感词" style={{ width: 180 }} />
        </Form.Item>
        <Form.Item name="action">
          <Select
            style={{ width: 150 }}
            options={[
              { label: '拦截（拒绝发布）', value: 'BLOCK' },
              { label: '打码后放行', value: 'REPLACE' },
              { label: '转人工待审', value: 'PENDING' },
            ]}
          />
        </Form.Item>
        <Form.Item name="category">
          <Input placeholder="分类（可选）" style={{ width: 140 }} />
        </Form.Item>
        <Form.Item>
          <Button type="primary" onClick={add}>
            新增词条
          </Button>
        </Form.Item>
      </Form>
    </div>
  );
}

/** 运维面板：检索索引覆盖与重建、计数对账（需 sys:forum:manage:ops） */
function OpsPanel() {
  const canOps = hasPermission('sys:forum:manage:ops');
  const [status, setStatus] = useState<SearchStatus | null>(null);
  const [report, setReport] = useState<ReconcileReport | null>(null);
  const [busy, setBusy] = useState(false);

  const loadStatus = useCallback(() => {
    fetchSearchStatus()
      .then(setStatus)
      .catch((err: unknown) => {
        message.error(err instanceof Error ? err.message : '读取检索状态失败');
      });
  }, []);

  useEffect(() => {
    loadStatus();
  }, [loadStatus]);

  const runReindex = async () => {
    setBusy(true);
    try {
      const rows = await reindexSearch();
      message.success(`已回填 ${rows} 条检索纯文本`);
      loadStatus();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '重建失败');
    } finally {
      setBusy(false);
    }
  };

  const runReconcile = async (fix: boolean) => {
    setBusy(true);
    try {
      const result = await reconcileCounts(fix);
      setReport(result);
      if (result.diffCount === 0) {
        message.success('核对通过：展示计数与明细一致');
      } else if (fix) {
        message.warning(`发现 ${result.diffCount} 处差异，已修正 ${result.fixedCount} 处`);
      } else {
        message.info(`发现 ${result.diffCount} 处差异（未修正，仅报告）`);
      }
    } catch (err) {
      message.error(err instanceof Error ? err.message : '对账失败');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        size="small"
        title="检索索引"
        extra={
          <Space>
            <Button size="small" onClick={loadStatus} disabled={busy}>
              刷新状态
            </Button>
            <Button size="small" type="primary" onClick={runReindex} disabled={busy || !canOps}>
              回填索引
            </Button>
          </Space>
        }
      >
        {status ? (
          <Descriptions size="small" column={3}>
            <Descriptions.Item label="引擎">{status.engine}</Descriptions.Item>
            <Descriptions.Item label="索引名">{status.indexName}</Descriptions.Item>
            <Descriptions.Item label="分词长度">{status.ngramTokenSize}</Descriptions.Item>
            <Descriptions.Item label="帖子总数">{status.totalThreads}</Descriptions.Item>
            <Descriptions.Item label="已建索引">{status.indexedThreads}</Descriptions.Item>
            <Descriptions.Item label="待回填">{status.pendingBackfill}</Descriptions.Item>
          </Descriptions>
        ) : (
          <Text type="secondary">读取中…</Text>
        )}
        <Paragraph type="secondary" style={{ marginBottom: 0 }}>
          关键词短于分词长度时自动回退 LIKE；回填只是把历史帖的纯文本补上，可重复执行。
        </Paragraph>
      </Card>

      <Card
        size="small"
        title="计数对账"
        extra={
          <Space>
            <Button size="small" onClick={() => runReconcile(false)} disabled={busy}>
              只报告
            </Button>
            <Button
              size="small"
              danger
              onClick={() => runReconcile(true)}
              disabled={busy || !canOps}
            >
              报告并修正
            </Button>
          </Space>
        }
      >
        {report ? (
          <>
            <Paragraph style={{ marginBottom: 8 }}>
              批次 {report.batchNo} · 差异 {report.diffCount} 处 · 修正 {report.fixedCount} 处 ·{' '}
              {report.dryRun ? '仅报告' : '已写回'}
              {report.truncated ? '（明细仅显示前 20 条）' : ''}
            </Paragraph>
            <Table
              size="small"
              rowKey={(row) => `${row.threadId}-${row.metric}`}
              dataSource={report.items}
              pagination={false}
              columns={[
                { title: '帖子', dataIndex: 'threadId' },
                { title: '计数项', dataIndex: 'metric', width: 100 },
                { title: '展示值', dataIndex: 'before', width: 90 },
                { title: '真实值', dataIndex: 'actual', width: 90 },
                {
                  title: '已修正',
                  dataIndex: 'fixed',
                  width: 90,
                  render: (value: boolean) => (value ? <Tag color="green">是</Tag> : <Tag>否</Tag>),
                },
              ]}
            />
          </>
        ) : (
          <Text type="secondary">
            对账以明细表 COUNT 校正回复/投花/收藏三项；浏览数没有逐次流水，不纳入对账。
          </Text>
        )}
      </Card>
    </Space>
  );
}
