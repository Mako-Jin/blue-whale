// OA 建议箱：建议提交、列表浏览、详情回复与关闭
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  List,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import {
  closeSuggestion,
  createSuggestion,
  getSuggestion,
  pageSuggestions,
  replySuggestion,
  type Suggestion,
  type SuggestionCategory,
  type SuggestionDetail,
  type SuggestionStatus,
} from '../../api/culture';
import { hasPermission } from '../../utils/permission';

/** 建议类别文案 */
const CATEGORY_META: Record<SuggestionCategory, { text: string; color: string }> = {
  SUGGESTION: { text: '建议', color: 'blue' },
  COMPLAINT: { text: '投诉', color: 'red' },
  PRAISE: { text: '表扬', color: 'green' },
  OTHER: { text: '其他', color: 'default' },
};

/** 建议状态文案 */
const SUGGESTION_STATUS_META: Record<SuggestionStatus, { text: string; color: string }> = {
  PENDING: { text: '待处理', color: 'default' },
  PROCESSING: { text: '处理中', color: 'processing' },
  REPLIED: { text: '已回复', color: 'success' },
  CLOSED: { text: '已关闭', color: 'warning' },
};

/** 建议表单值 */
interface SuggestionFormValues {
  title: string;
  category: SuggestionCategory;
  content: string;
  anonymous: boolean;
}

/** 回复表单值 */
interface ReplyFormValues {
  content: string;
  status: SuggestionStatus;
}

/** 时间展示 */
function timeText(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

/** 提交人展示（匿名时隐藏） */
function submitterText(record: Suggestion): string {
  if (record.anonymous) {
    return '匿名';
  }
  return record.submitterName || (record.submitterId ? String(record.submitterId) : '-');
}

export default function SuggestionPage() {
  const { message } = App.useApp();

  const [data, setData] = useState<Suggestion[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<SuggestionStatus | ''>('');
  const [category, setCategory] = useState<SuggestionCategory | ''>('');
  const [mine, setMine] = useState(false);
  const [loading, setLoading] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<SuggestionFormValues>();

  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<SuggestionDetail | null>(null);
  const [replying, setReplying] = useState(false);
  const [replyForm] = Form.useForm<ReplyFormValues>();

  const canAdd = hasPermission('sys:oa:suggestion:add');
  const canReply = hasPermission('sys:oa:suggestion:reply');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageSuggestions({
        current,
        size: 10,
        keyword: keyword.trim() || undefined,
        status: status || undefined,
        category: category || undefined,
        mine: mine || undefined,
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '建议查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status, category, mine, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    form.resetFields();
    form.setFieldsValue({ category: 'SUGGESTION', anonymous: false });
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await createSuggestion({
        title: values.title.trim(),
        category: values.category,
        content: values.content.trim(),
        anonymous: values.anonymous,
      });
      message.success('建议已提交');
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '建议提交失败');
    } finally {
      setSaving(false);
    }
  };

  const openDetail = async (record: Suggestion) => {
    setDetailOpen(true);
    setDetail(null);
    replyForm.resetFields();
    replyForm.setFieldsValue({ status: 'REPLIED' });
    try {
      setDetail(await getSuggestion(record.id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '建议详情加载失败');
    }
  };

  const handleReply = async () => {
    if (!detail) {
      return;
    }
    const values = await replyForm.validateFields();
    setReplying(true);
    try {
      await replySuggestion(detail.id, {
        content: values.content.trim(),
        status: values.status,
      });
      message.success('回复已提交');
      replyForm.resetFields();
      replyForm.setFieldsValue({ status: 'REPLIED' });
      setDetail(await getSuggestion(detail.id));
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '回复提交失败');
    } finally {
      setReplying(false);
    }
  };

  const handleClose = async () => {
    if (!detail) {
      return;
    }
    try {
      await closeSuggestion(detail.id);
      message.success('建议已关闭');
      setDetail(await getSuggestion(detail.id));
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '建议关闭失败');
    }
  };

  const columns: TableProps<Suggestion>['columns'] = [
    { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
    {
      title: '类别',
      dataIndex: 'category',
      key: 'category',
      width: 100,
      render: (value: SuggestionCategory) => {
        const meta = CATEGORY_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (value: SuggestionStatus) => {
        const meta = SUGGESTION_STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '提交人',
      key: 'submitter',
      width: 110,
      render: (_value, record) => submitterText(record),
    },
    {
      title: '提交时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 160,
      render: (value?: string) => timeText(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_value, record) => (
        <Button type="link" size="small" onClick={() => void openDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input
          allowClear
          placeholder="建议标题"
          style={{ width: 200 }}
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="类别"
          style={{ width: 120 }}
          value={category || undefined}
          options={Object.entries(CATEGORY_META).map(([value, meta]) => ({
            label: meta.text,
            value,
          }))}
          onChange={(value) => {
            setCategory((value as SuggestionCategory) ?? '');
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 120 }}
          value={status || undefined}
          options={Object.entries(SUGGESTION_STATUS_META).map(([value, meta]) => ({
            label: meta.text,
            value,
          }))}
          onChange={(value) => {
            setStatus((value as SuggestionStatus) ?? '');
            setCurrent(1);
          }}
        />
        <Space size={6}>
          <span>只看我的</span>
          <Switch
            checked={mine}
            onChange={(checked) => {
              setMine(checked);
              setCurrent(1);
            }}
          />
        </Space>
        {canAdd ? (
          <Button type="primary" onClick={openCreate}>
            提交建议
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<Suggestion>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        scroll={{ x: 900 }}
        pagination={{
          current,
          pageSize: 10,
          total,
          onChange: (page) => setCurrent(page),
        }}
      />
      <Modal
        open={modalOpen}
        title="提交建议"
        width={640}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item
            name="category"
            label="类别"
            rules={[{ required: true, message: '请选择类别' }]}
          >
            <Select
              options={Object.entries(CATEGORY_META).map(([value, meta]) => ({
                label: meta.text,
                value,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="content"
            label="内容"
            rules={[{ required: true, message: '请输入建议内容' }]}
          >
            <Input.TextArea rows={6} maxLength={5000} showCount />
          </Form.Item>
          <Form.Item name="anonymous" label="匿名提交" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
      <Drawer
        open={detailOpen}
        width={640}
        title={detail?.title ?? '建议详情'}
        onClose={() => setDetailOpen(false)}
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions
              column={2}
              size="small"
              bordered
              items={[
                { key: 'title', label: '标题', children: detail.title, span: 2 },
                {
                  key: 'category',
                  label: '类别',
                  children: CATEGORY_META[detail.category]?.text ?? detail.category,
                },
                {
                  key: 'status',
                  label: '状态',
                  children: SUGGESTION_STATUS_META[detail.status]?.text ?? detail.status,
                },
                ...(detail.anonymous
                  ? []
                  : [
                      {
                        key: 'submitter',
                        label: '提交人',
                        children: submitterText(detail),
                      },
                    ]),
                { key: 'createTime', label: '提交时间', children: timeText(detail.createTime) },
                { key: 'handler', label: '处理人', children: detail.handlerName || '-' },
                { key: 'handledTime', label: '处理时间', children: timeText(detail.handledTime) },
              ]}
            />
            <div>
              <div style={{ fontWeight: 600, marginBottom: 8 }}>内容</div>
              <div style={{ whiteSpace: 'pre-wrap' }}>{detail.content || '-'}</div>
            </div>
            <div>
              <div style={{ fontWeight: 600, marginBottom: 8 }}>回复</div>
              <List
                size="small"
                dataSource={detail.replies}
                locale={{ emptyText: '暂无回复' }}
                renderItem={(item) => (
                  <List.Item>
                    <Space direction="vertical" size={2} style={{ width: '100%' }}>
                      <Space size={8}>
                        <span style={{ fontWeight: 600 }}>
                          {item.replierName || item.replierId || '-'}
                        </span>
                        <span style={{ color: '#888' }}>{timeText(item.createTime)}</span>
                      </Space>
                      <div style={{ whiteSpace: 'pre-wrap' }}>{item.content}</div>
                    </Space>
                  </List.Item>
                )}
              />
            </div>
            {canReply && detail.status !== 'CLOSED' ? (
              <Form form={replyForm} layout="vertical">
                <Form.Item
                  name="content"
                  label="回复内容"
                  rules={[{ required: true, message: '请输入回复内容' }]}
                >
                  <Input.TextArea rows={3} maxLength={1000} showCount />
                </Form.Item>
                <Space size="middle" style={{ display: 'flex' }}>
                  <Form.Item name="status" label="处理状态" style={{ flex: 1 }}>
                    <Select
                      options={[
                        { label: '处理中', value: 'PROCESSING' },
                        { label: '已回复', value: 'REPLIED' },
                      ]}
                    />
                  </Form.Item>
                  <Space>
                    <Button type="primary" loading={replying} onClick={() => void handleReply()}>
                      回复
                    </Button>
                    <Popconfirm title="确认关闭该建议？" onConfirm={() => void handleClose()}>
                      <Button danger>关闭</Button>
                    </Popconfirm>
                  </Space>
                </Space>
              </Form>
            ) : null}
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
