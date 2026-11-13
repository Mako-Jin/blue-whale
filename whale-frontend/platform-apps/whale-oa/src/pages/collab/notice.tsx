// OA 协同 - 公告：全员/部门/用户公告、发布撤回、已读统计与我的公告
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import {
  createNotice,
  getNotice,
  getNoticeStats,
  listNoticeRecipients,
  markNoticeRead,
  pageMyNotices,
  pageNotices,
  publishNotice,
  updateNotice,
  withdrawNotice,
  type OaNotice,
  type OaNoticeDetail,
  type OaNoticeInput,
  type OaNoticeRecipient,
  type OaNoticeScopeType,
  type OaNoticeStatus,
  type OaUserCandidate,
} from '../../api/collab';
import { hasPermission } from '../../utils/permission';

/** 公告状态文案 */
const NOTICE_STATUS_META: Record<OaNoticeStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  PUBLISHED: { text: '已发布', color: 'success' },
  WITHDRAWN: { text: '已撤回', color: 'warning' },
};

/** 公告范围文案 */
const SCOPE_META: Record<OaNoticeScopeType, { text: string; color: string }> = {
  ALL: { text: '全员', color: 'blue' },
  DEPT: { text: '部门', color: 'purple' },
  USER: { text: '指定用户', color: 'cyan' },
};

/** 公告表单值 */
interface NoticeFormValues {
  title: string;
  content: string;
  scopeType: OaNoticeScopeType;
  scopeValue?: string;
  remark?: string;
  recipients?: number[];
}

/** 时间格式化为展示文本 */
function timeText(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

/** 候选用户展示名 */
function candidateName(item: OaUserCandidate): string {
  return item.nickname || item.username || String(item.userId);
}

export default function CollabNoticePage() {
  const { message } = App.useApp();

  const [tab, setTab] = useState<'all' | 'mine'>('all');
  const [notices, setNotices] = useState<OaNotice[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<OaNoticeStatus | ''>('');
  const [scopeType, setScopeType] = useState<OaNoticeScopeType | ''>('');
  const [loading, setLoading] = useState(false);

  const [modal, setModal] = useState(false);
  const [editingNotice, setEditingNotice] = useState<OaNotice | null>(null);
  const [candidates, setCandidates] = useState<OaUserCandidate[]>([]);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<OaNoticeDetail | null>(null);
  const [saving, setSaving] = useState(false);

  const [form] = Form.useForm<NoticeFormValues>();

  const canManage = hasPermission('sys:oa:notice:manage');
  const canStats = hasPermission('sys:oa:notice:stats');

  const loadNotices = useCallback(async () => {
    try {
      const params = {
        current: page,
        size: 10,
        keyword: keyword.trim() || undefined,
        status,
        scopeType,
      };
      const result = tab === 'mine' ? await pageMyNotices(params) : await pageNotices(params);
      setNotices(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '公告查询失败');
      setNotices([]);
      setTotal(0);
    }
  }, [tab, page, keyword, status, scopeType, message]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      await loadNotices();
    } finally {
      setLoading(false);
    }
  }, [loadNotices]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const searchCandidates = async (value?: string) => {
    try {
      setCandidates(
        await listNoticeRecipients({ keyword: value?.trim() || undefined, limit: 100 }),
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : '通讯录候选查询失败');
    }
  };

  const openNotice = async (record: OaNotice | null) => {
    setEditingNotice(record);
    form.resetFields();
    let recipientIds: number[] = [];
    if (record) {
      try {
        const data = await getNotice(record.id);
        recipientIds = data.recipients.map((item) => item.userId);
      } catch (error) {
        message.error(error instanceof Error ? error.message : '公告详情加载失败');
        return;
      }
    }
    form.setFieldsValue({
      title: record?.title,
      content: record?.content,
      scopeType: record?.scopeType ?? 'ALL',
      scopeValue: record?.scopeValue,
      remark: record?.remark,
      recipients: recipientIds,
    });
    setModal(true);
    void searchCandidates();
  };

  const saveNotice = async () => {
    const values = await form.validateFields();
    const recipients = (values.recipients ?? []).map((userId) => {
      const candidate = candidates.find((item) => item.userId === userId);
      return {
        userId,
        userName: candidate ? candidateName(candidate) : undefined,
      };
    });
    if (values.scopeType === 'USER' && recipients.length === 0) {
      message.error('指定用户公告需选择接收人');
      return;
    }
    const payload: OaNoticeInput = {
      title: values.title.trim(),
      content: values.content,
      scopeType: values.scopeType,
      scopeValue: values.scopeType === 'ALL' ? undefined : values.scopeValue?.trim() || undefined,
      remark: values.remark?.trim() || undefined,
      recipients,
    };
    setSaving(true);
    try {
      if (editingNotice) {
        await updateNotice(editingNotice.id, payload);
        message.success('公告已更新');
      } else {
        await createNotice(payload);
        message.success('公告草稿已创建');
      }
      setModal(false);
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '公告保存失败');
    } finally {
      setSaving(false);
    }
  };

  const publish = async (record: OaNotice) => {
    try {
      await publishNotice(record.id);
      message.success('公告已发布');
      await refresh();
      if (detailOpen && detail?.id === record.id) {
        await openDetail(record.id, canStats);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '公告发布失败');
    }
  };

  const withdraw = async (record: OaNotice) => {
    try {
      await withdrawNotice(record.id);
      message.success('公告已撤回');
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '公告撤回失败');
    }
  };

  const openDetail = async (id: number, withStats: boolean) => {
    setDetailOpen(true);
    try {
      const data = withStats ? await getNoticeStats(id) : await getNotice(id);
      setDetail(data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '公告详情加载失败');
    }
  };

  const markRead = async () => {
    if (!detail) {
      return;
    }
    try {
      await markNoticeRead(detail.id);
      message.success('已标记为已读');
      await openDetail(detail.id, canStats);
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '标记已读失败');
    }
  };

  const columns: TableProps<OaNotice>['columns'] = [
    { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
    {
      title: '范围',
      dataIndex: 'scopeType',
      key: 'scopeType',
      width: 100,
      render: (value: OaNoticeScopeType) => {
        const meta = SCOPE_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '范围值',
      dataIndex: 'scopeValue',
      key: 'scopeValue',
      width: 110,
      render: (value?: string) => value ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: OaNoticeStatus) => {
        const meta = NOTICE_STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '发布人',
      dataIndex: 'publisher',
      key: 'publisher',
      width: 110,
      render: (value?: string) => value ?? '-',
    },
    {
      title: '发布时间',
      dataIndex: 'publishTime',
      key: 'publishTime',
      width: 160,
      render: (value?: string) => timeText(value),
    },
    {
      title: '接收/已读',
      key: 'readStat',
      width: 110,
      render: (_value, record) =>
        record.recipientCount === undefined
          ? '-'
          : `${record.readCount ?? 0}/${record.recipientCount}`,
    },
    {
      title: '阅读状态',
      dataIndex: 'myReadFlag',
      key: 'myReadFlag',
      width: 100,
      render: (value?: number) =>
        tab === 'mine' ? (
          <Tag color={value === 1 ? 'success' : 'processing'}>{value === 1 ? '已读' : '未读'}</Tag>
        ) : (
          '-'
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 230,
      render: (_value, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => void openDetail(record.id, canStats)}>
            详情
          </Button>
          {canManage && record.status === 'DRAFT' ? (
            <Button type="link" size="small" onClick={() => void openNotice(record)}>
              编辑
            </Button>
          ) : null}
          {canManage && record.status === 'DRAFT' ? (
            <Popconfirm title="确认发布该公告？" onConfirm={() => void publish(record)}>
              <Button type="link" size="small">
                发布
              </Button>
            </Popconfirm>
          ) : null}
          {canManage && record.status === 'PUBLISHED' ? (
            <Popconfirm title="确认撤回该公告？" onConfirm={() => void withdraw(record)}>
              <Button type="link" size="small" danger>
                撤回
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const recipientColumns: TableProps<OaNoticeRecipient>['columns'] = [
    { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 100 },
    {
      title: '姓名',
      dataIndex: 'userName',
      key: 'userName',
      render: (value?: string) => value ?? '-',
    },
    {
      title: '阅读状态',
      dataIndex: 'readFlag',
      key: 'readFlag',
      width: 100,
      render: (value?: number) => (
        <Tag color={value === 1 ? 'success' : 'default'}>{value === 1 ? '已读' : '未读'}</Tag>
      ),
    },
    {
      title: '阅读时间',
      dataIndex: 'readTime',
      key: 'readTime',
      width: 160,
      render: (value?: string) => timeText(value),
    },
  ];

  return (
    <>
      <Tabs
        activeKey={tab}
        onChange={(key) => {
          setTab(key as 'all' | 'mine');
          setPage(1);
        }}
        items={[
          { key: 'all', label: '公告列表' },
          { key: 'mine', label: '我的公告' },
        ]}
      />
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Space wrap>
          <Input
            allowClear
            placeholder="公告标题"
            style={{ width: 200 }}
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value);
              setPage(1);
            }}
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 130 }}
            value={status || undefined}
            options={Object.entries(NOTICE_STATUS_META).map(([value, meta]) => ({
              label: meta.text,
              value,
            }))}
            onChange={(value) => {
              setStatus((value as OaNoticeStatus) ?? '');
              setPage(1);
            }}
          />
          <Select
            allowClear
            placeholder="范围"
            style={{ width: 130 }}
            value={scopeType || undefined}
            options={Object.entries(SCOPE_META).map(([value, meta]) => ({
              label: meta.text,
              value,
            }))}
            onChange={(value) => {
              setScopeType((value as OaNoticeScopeType) ?? '');
              setPage(1);
            }}
          />
          {canManage ? (
            <Button type="primary" onClick={() => void openNotice(null)}>
              新建公告
            </Button>
          ) : null}
          <Button onClick={() => void refresh()}>刷新</Button>
        </Space>
        <Table<OaNotice>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={notices}
          scroll={{ x: 1200 }}
          pagination={{
            current: page,
            pageSize: 10,
            total,
            onChange: (next) => setPage(next),
          }}
        />
      </Space>
      <Modal
        open={modal}
        title={editingNotice ? '编辑公告' : '新建公告'}
        width={720}
        onOk={() => void saveNotice()}
        confirmLoading={saving}
        onCancel={() => setModal(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="title"
            label="公告标题"
            rules={[{ required: true, message: '请输入公告标题' }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item
            name="content"
            label="公告正文"
            rules={[{ required: true, message: '请输入公告正文' }]}
          >
            <Input.TextArea rows={6} maxLength={5000} showCount />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="scopeType" label="发布范围" style={{ flex: 1 }}>
              <Select
                options={Object.entries(SCOPE_META).map(([value, meta]) => ({
                  label: meta.text,
                  value,
                }))}
              />
            </Form.Item>
            <Form.Item noStyle shouldUpdate={(prev, next) => prev.scopeType !== next.scopeType}>
              {({ getFieldValue }) =>
                getFieldValue('scopeType') === 'ALL' ? null : (
                  <Form.Item
                    name="scopeValue"
                    label="范围值（部门ID/范围说明）"
                    style={{ flex: 1 }}
                    rules={
                      getFieldValue('scopeType') === 'DEPT'
                        ? [{ required: true, message: '请输入部门ID' }]
                        : []
                    }
                  >
                    <Input maxLength={64} />
                  </Form.Item>
                )
              }
            </Form.Item>
          </Space>
          <Form.Item name="recipients" label="接收人（全员公告可留空自动解析）">
            <Select
              mode="multiple"
              allowClear
              showSearch
              filterOption={false}
              placeholder="搜索并选择接收人"
              onSearch={(value) => void searchCandidates(value)}
              options={candidates.map((item) => ({
                label: `${candidateName(item)}（ID:${item.userId}）`,
                value: item.userId,
              }))}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <Drawer
        open={detailOpen}
        width={720}
        title={detail?.title ?? '公告详情'}
        onClose={() => setDetailOpen(false)}
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="范围">
                <Tag color={SCOPE_META[detail.scopeType]?.color ?? 'default'}>
                  {SCOPE_META[detail.scopeType]?.text ?? detail.scopeType}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={NOTICE_STATUS_META[detail.status]?.color ?? 'default'}>
                  {NOTICE_STATUS_META[detail.status]?.text ?? detail.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="发布人">{detail.publisher ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="发布时间">{timeText(detail.publishTime)}</Descriptions.Item>
              <Descriptions.Item label="接收/已读" span={2}>
                {detail.readCount ?? 0} / {detail.recipientCount ?? 0}
              </Descriptions.Item>
            </Descriptions>
            <Input.TextArea value={detail.content} rows={8} readOnly />
            {tab === 'mine' && detail.status === 'PUBLISHED' && detail.myReadFlag !== 1 ? (
              <Button type="primary" onClick={() => void markRead()}>
                标记为已读
              </Button>
            ) : null}
            <Table<OaNoticeRecipient>
              rowKey="userId"
              size="small"
              pagination={false}
              columns={recipientColumns}
              dataSource={detail.recipients}
            />
          </Space>
        ) : null}
      </Drawer>
    </>
  );
}
