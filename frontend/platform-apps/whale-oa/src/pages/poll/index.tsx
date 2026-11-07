// OA 投票问卷：投票列表、创建/发布/关闭、参与投票（单选/多选）
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Checkbox,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  closePoll,
  createPoll,
  deletePoll,
  getPoll,
  pagePolls,
  publishPoll,
  updatePoll,
  votePoll,
  type Poll,
  type PollDetail,
  type PollInput,
  type PollStatus,
} from '../../api/culture';
import { hasPermission } from '../../utils/permission';

/** 投票状态文案 */
const POLL_STATUS_META: Record<PollStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  PUBLISHED: { text: '进行中', color: 'success' },
  CLOSED: { text: '已关闭', color: 'warning' },
};

/** 投票表单值 */
interface PollFormValues {
  title: string;
  description?: string;
  multipleChoice: boolean;
  anonymous: boolean;
  maxChoices?: number;
  timeRange?: [Dayjs, Dayjs];
  options: Array<{ optionText: string }>;
}

/** 时间展示 */
function timeText(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

/** 起止时间展示 */
function rangeText(start?: string, end?: string): string {
  if (!start && !end) {
    return '-';
  }
  return `${timeText(start)} ~ ${timeText(end)}`;
}

export default function PollPage() {
  const { message } = App.useApp();

  const [data, setData] = useState<Poll[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<PollStatus | ''>('');
  const [loading, setLoading] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Poll | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<PollFormValues>();

  const [voteOpen, setVoteOpen] = useState(false);
  const [voteDetail, setVoteDetail] = useState<PollDetail | null>(null);
  const [voteLoading, setVoteLoading] = useState(false);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [voting, setVoting] = useState(false);

  const canManage = hasPermission('sys:oa:poll:add');
  const canVote = hasPermission('sys:oa:poll:vote');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pagePolls({
        current,
        size: 10,
        keyword: keyword.trim() || undefined,
        status: status || undefined,
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '投票查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      multipleChoice: false,
      anonymous: false,
      maxChoices: 2,
      options: [{ optionText: '' }, { optionText: '' }],
    });
    setModalOpen(true);
  };

  const openEdit = async (record: Poll) => {
    setEditing(record);
    form.resetFields();
    try {
      const detail = await getPoll(record.id);
      form.setFieldsValue({
        title: detail.title,
        description: detail.description,
        multipleChoice: Boolean(detail.multipleChoice),
        anonymous: Boolean(detail.anonymous),
        maxChoices: detail.maxChoices ?? 2,
        timeRange:
          detail.startTime && detail.endTime
            ? [dayjs(detail.startTime), dayjs(detail.endTime)]
            : undefined,
        options: detail.options.map((option) => ({ optionText: option.optionText })),
      });
      setModalOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '投票详情加载失败');
    }
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const multiple = values.multipleChoice;
    const payload: PollInput = {
      title: values.title.trim(),
      description: values.description?.trim() || undefined,
      multipleChoice: multiple,
      anonymous: values.anonymous,
      maxChoices: multiple ? values.maxChoices ?? 2 : 1,
      startTime: values.timeRange?.[0].format('YYYY-MM-DDTHH:mm:ss'),
      endTime: values.timeRange?.[1].format('YYYY-MM-DDTHH:mm:ss'),
      options: values.options.map((item, index) => ({
        optionText: item.optionText.trim(),
        sortNo: index + 1,
      })),
    };
    setSaving(true);
    try {
      if (editing) {
        await updatePoll(editing.id, payload);
        message.success('投票已更新');
      } else {
        await createPoll(payload);
        message.success('投票已创建');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '投票保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handlePublish = async (record: Poll) => {
    try {
      await publishPoll(record.id);
      message.success('投票已发布');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '投票发布失败');
    }
  };

  const handleClose = async (record: Poll) => {
    try {
      await closePoll(record.id);
      message.success('投票已关闭');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '投票关闭失败');
    }
  };

  const handleDelete = async (record: Poll) => {
    try {
      await deletePoll(record.id);
      message.success('投票已删除');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '投票删除失败');
    }
  };

  const openVote = async (record: Poll) => {
    setVoteOpen(true);
    setVoteLoading(true);
    setSelectedIds([]);
    setVoteDetail(null);
    try {
      const detail = await getPoll(record.id);
      setVoteDetail(detail);
      setSelectedIds(detail.options.filter((option) => option.myChoice).map((option) => option.id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '投票详情加载失败');
    } finally {
      setVoteLoading(false);
    }
  };

  const handleVote = async () => {
    if (!voteDetail) {
      return;
    }
    if (selectedIds.length === 0) {
      message.error('请选择投票选项');
      return;
    }
    if (voteDetail.multipleChoice && voteDetail.maxChoices && selectedIds.length > voteDetail.maxChoices) {
      message.error(`最多选择 ${voteDetail.maxChoices} 项`);
      return;
    }
    setVoting(true);
    try {
      await votePoll(voteDetail.id, selectedIds);
      message.success('投票已提交');
      setVoteOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '投票提交失败');
    } finally {
      setVoting(false);
    }
  };

  const votable = canVote && voteDetail?.status === 'PUBLISHED' && !voteDetail.myVoted;

  const columns: TableProps<Poll>['columns'] = [
    { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: PollStatus) => {
        const meta = POLL_STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '起止时间',
      key: 'timeRange',
      width: 300,
      render: (_value, record) => rangeText(record.startTime, record.endTime),
    },
    {
      title: '创建人',
      dataIndex: 'creatorName',
      key: 'creatorName',
      width: 110,
      render: (value: string | undefined, record) => value ?? record.creatorId ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_value, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => void openVote(record)}>
            {record.status === 'PUBLISHED' ? '投票' : '查看'}
          </Button>
          {canManage && record.status === 'DRAFT' ? (
            <Button type="link" size="small" onClick={() => void openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {canManage && record.status === 'DRAFT' ? (
            <Popconfirm title="确认发布该投票？" onConfirm={() => void handlePublish(record)}>
              <Button type="link" size="small">
                发布
              </Button>
            </Popconfirm>
          ) : null}
          {canManage && record.status === 'PUBLISHED' ? (
            <Popconfirm title="确认关闭该投票？" onConfirm={() => void handleClose(record)}>
              <Button type="link" size="small">
                关闭
              </Button>
            </Popconfirm>
          ) : null}
          {canManage && record.status !== 'PUBLISHED' ? (
            <Popconfirm title="确认删除该投票？" onConfirm={() => void handleDelete(record)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input
          allowClear
          placeholder="投票标题"
          style={{ width: 200 }}
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 130 }}
          value={status || undefined}
          options={Object.entries(POLL_STATUS_META).map(([value, meta]) => ({
            label: meta.text,
            value,
          }))}
          onChange={(value) => {
            setStatus((value as PollStatus) ?? '');
            setCurrent(1);
          }}
        />
        {canManage ? (
          <Button type="primary" onClick={openCreate}>
            新建投票
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<Poll>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        scroll={{ x: 1000 }}
        pagination={{
          current,
          pageSize: 10,
          total,
          onChange: (page) => setCurrent(page),
        }}
      />
      <Modal
        open={modalOpen}
        title={editing ? '编辑投票' : '新建投票'}
        width={760}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="title"
            label="标题"
            rules={[{ required: true, message: '请输入投票标题' }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={3} maxLength={1000} showCount placeholder="选填" />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="multipleChoice" label="多选" valuePropName="checked" style={{ flex: 1 }}>
              <Switch checkedChildren="多选" unCheckedChildren="单选" />
            </Form.Item>
            <Form.Item name="anonymous" label="匿名投票" valuePropName="checked" style={{ flex: 1 }}>
              <Switch />
            </Form.Item>
            <Form.Item
              noStyle
              shouldUpdate={(prev, next) => prev.multipleChoice !== next.multipleChoice}
            >
              {({ getFieldValue }) =>
                getFieldValue('multipleChoice') ? (
                  <Form.Item name="maxChoices" label="最多可选数" style={{ flex: 1 }}>
                    <InputNumber min={2} max={20} style={{ width: '100%' }} />
                  </Form.Item>
                ) : null
              }
            </Form.Item>
          </Space>
          <Form.Item
            name="timeRange"
            label="起止时间"
            rules={[{ required: true, message: '请选择起止时间' }]}
          >
            <DatePicker.RangePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.List
            name="options"
            rules={[
              {
                validator: async (_rule, value: Array<{ optionText: string }> | undefined) => {
                  if (!value || value.length < 2) {
                    throw new Error('至少需要 2 个选项');
                  }
                },
              },
            ]}
          >
            {(fields, { add, remove }, { errors }) => (
              <>
                {fields.map((field, index) => (
                  <Space key={field.key} align="baseline" style={{ display: 'flex' }}>
                    <Form.Item
                      name={[field.name, 'optionText']}
                      rules={[{ required: true, message: '请输入选项内容' }]}
                      style={{ flex: 1 }}
                    >
                      <Input placeholder={`选项 ${index + 1}`} maxLength={128} />
                    </Form.Item>
                    <Button
                      danger
                      type="link"
                      disabled={fields.length <= 2}
                      onClick={() => remove(field.name)}
                    >
                      删除
                    </Button>
                  </Space>
                ))}
                <Space>
                  <Button type="dashed" onClick={() => add({ optionText: '' })}>
                    新增选项
                  </Button>
                  <Form.ErrorList errors={errors} />
                </Space>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>
      <Modal
        open={voteOpen}
        title={voteDetail?.title ?? '投票'}
        width={640}
        okText="提交投票"
        onOk={() => void handleVote()}
        confirmLoading={voting}
        okButtonProps={{ disabled: !votable || selectedIds.length === 0 }}
        onCancel={() => setVoteOpen(false)}
        destroyOnClose
      >
        <Spin spinning={voteLoading}>
          {voteDetail ? (
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              {voteDetail.description ? <div>{voteDetail.description}</div> : null}
              <Space wrap size={8}>
                <Tag color={POLL_STATUS_META[voteDetail.status]?.color ?? 'default'}>
                  {POLL_STATUS_META[voteDetail.status]?.text ?? voteDetail.status}
                </Tag>
                <Tag color={voteDetail.multipleChoice ? 'blue' : 'default'}>
                  {voteDetail.multipleChoice ? '多选' : '单选'}
                </Tag>
                {voteDetail.maxChoices && voteDetail.multipleChoice ? (
                  <Tag>最多 {voteDetail.maxChoices} 项</Tag>
                ) : null}
                <Tag color="cyan">共 {voteDetail.totalVotes ?? 0} 票</Tag>
                {voteDetail.myVoted ? <Tag color="success">已投票</Tag> : null}
              </Space>
              {voteDetail.multipleChoice ? (
                <Checkbox.Group
                  value={selectedIds}
                  disabled={!votable}
                  onChange={(values) => setSelectedIds(values.map((value) => Number(value)))}
                  style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
                >
                  {voteDetail.options.map((option) => {
                    const count = option.voteCount ?? 0;
                    const percent = voteDetail.totalVotes
                      ? Math.round((count * 100) / voteDetail.totalVotes)
                      : 0;
                    return (
                      <Checkbox key={option.id} value={option.id}>
                        {option.optionText}
                        <span style={{ color: '#888', marginLeft: 8 }}>
                          {count} 票（{percent}%）
                        </span>
                      </Checkbox>
                    );
                  })}
                </Checkbox.Group>
              ) : (
                <Radio.Group
                  value={selectedIds[0]}
                  disabled={!votable}
                  onChange={(event) => setSelectedIds([Number(event.target.value)])}
                  style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
                >
                  {voteDetail.options.map((option) => {
                    const count = option.voteCount ?? 0;
                    const percent = voteDetail.totalVotes
                      ? Math.round((count * 100) / voteDetail.totalVotes)
                      : 0;
                    return (
                      <Radio key={option.id} value={option.id}>
                        {option.optionText}
                        <span style={{ color: '#888', marginLeft: 8 }}>
                          {count} 票（{percent}%）
                        </span>
                      </Radio>
                    );
                  })}
                </Radio.Group>
              )}
              {!votable ? (
                <span style={{ color: '#888' }}>
                  {voteDetail.myVoted
                    ? '您已参与本次投票'
                    : voteDetail.status !== 'PUBLISHED'
                      ? '该投票当前不可参与'
                      : '暂无投票权限'}
                </span>
              ) : null}
            </Space>
          ) : null}
        </Spin>
      </Modal>
    </Space>
  );
}
