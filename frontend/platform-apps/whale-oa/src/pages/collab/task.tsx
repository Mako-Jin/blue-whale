// OA 协同 - 个人任务：任务列表、状态流转、优先级/截止日期、评论
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
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
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  addTaskComment,
  changeTaskStatus,
  createTask,
  deleteTask,
  deleteTaskComment,
  listTaskComments,
  pageTasks,
  updateTask,
  type OaTask,
  type OaTaskComment,
  type OaTaskInput,
  type OaTaskPriority,
  type OaTaskSource,
  type OaTaskStatus,
} from '../../api/collab';
import { getCurrentUser } from '../../api/client';
import { hasPermission } from '../../utils/permission';

/** 任务状态文案 */
const TASK_STATUS_META: Record<OaTaskStatus, { text: string; color: string }> = {
  TODO: { text: '待办', color: 'default' },
  DOING: { text: '进行中', color: 'processing' },
  DONE: { text: '已完成', color: 'success' },
  CANCELLED: { text: '已取消', color: 'warning' },
};

/** 优先级文案 */
const PRIORITY_META: Record<OaTaskPriority, { text: string; color: string }> = {
  HIGH: { text: '高', color: 'error' },
  NORMAL: { text: '中', color: 'blue' },
  LOW: { text: '低', color: 'default' },
};

/** 来源文案 */
const SOURCE_META: Record<OaTaskSource, string> = {
  PERSONAL: '个人',
  MEETING: '会议',
  NOTICE: '公告',
};

/** 任务表单值 */
interface TaskFormValues {
  title: string;
  description?: string;
  ownerId: number;
  ownerName?: string;
  priority: OaTaskPriority;
  dueDate?: Dayjs;
  source: OaTaskSource;
  sourceId?: number;
}

/** 时间格式化为展示文本 */
function timeText(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

export default function CollabTaskPage() {
  const { message } = App.useApp();
  const user = getCurrentUser();

  const [tasks, setTasks] = useState<OaTask[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [mine, setMine] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<OaTaskStatus | ''>('');
  const [priority, setPriority] = useState<OaTaskPriority | ''>('');
  const [source, setSource] = useState<OaTaskSource | ''>('');
  const [loading, setLoading] = useState(false);

  const [modal, setModal] = useState(false);
  const [editingTask, setEditingTask] = useState<OaTask | null>(null);
  const [saving, setSaving] = useState(false);

  const [commentOpen, setCommentOpen] = useState(false);
  const [commentTask, setCommentTask] = useState<OaTask | null>(null);
  const [comments, setComments] = useState<OaTaskComment[]>([]);
  const [commentContent, setCommentContent] = useState('');

  const [form] = Form.useForm<TaskFormValues>();

  const canManage = hasPermission('sys:oa:task:manage');
  const canComment = hasPermission('sys:oa:task:comment');

  const loadTasks = useCallback(async () => {
    try {
      const result = await pageTasks({
        current: page,
        size: 10,
        mine,
        keyword: keyword.trim() || undefined,
        status,
        priority,
        source,
      });
      setTasks(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '任务查询失败');
      setTasks([]);
      setTotal(0);
    }
  }, [page, mine, keyword, status, priority, source, message]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      await loadTasks();
    } finally {
      setLoading(false);
    }
  }, [loadTasks]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const openTask = (record: OaTask | null) => {
    setEditingTask(record);
    form.resetFields();
    form.setFieldsValue({
      title: record?.title,
      description: record?.description,
      ownerId: record?.ownerId ?? (typeof user?.userId === 'number' ? user.userId : undefined),
      ownerName: record?.ownerName ?? user?.nickname ?? user?.username,
      priority: record?.priority ?? 'NORMAL',
      dueDate: record?.dueDate ? dayjs(record.dueDate) : undefined,
      source: record?.source ?? 'PERSONAL',
      sourceId: record?.sourceId,
    });
    setModal(true);
  };

  const saveTask = async () => {
    const values = await form.validateFields();
    const payload: OaTaskInput = {
      title: values.title.trim(),
      description: values.description?.trim() || undefined,
      ownerId: values.ownerId,
      ownerName: values.ownerName?.trim() || undefined,
      priority: values.priority,
      dueDate: values.dueDate?.format('YYYY-MM-DD'),
      source: values.source,
      sourceId: values.sourceId,
    };
    setSaving(true);
    try {
      if (editingTask) {
        await updateTask(editingTask.id, payload);
        message.success('任务已更新');
      } else {
        await createTask(payload);
        message.success('任务已创建');
      }
      setModal(false);
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '任务保存失败');
    } finally {
      setSaving(false);
    }
  };

  const changeStatus = async (record: OaTask, target: OaTaskStatus) => {
    try {
      await changeTaskStatus(record.id, target);
      message.success('任务状态已更新');
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '任务状态更新失败');
    }
  };

  const removeTask = async (record: OaTask) => {
    try {
      await deleteTask(record.id);
      message.success('任务已删除');
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '任务删除失败');
    }
  };

  const openComments = async (record: OaTask) => {
    setCommentTask(record);
    setCommentContent('');
    setCommentOpen(true);
    try {
      setComments(await listTaskComments(record.id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评论加载失败');
      setComments([]);
    }
  };

  const submitComment = async () => {
    if (!commentTask) {
      return;
    }
    if (!commentContent.trim()) {
      message.error('请输入评论内容');
      return;
    }
    try {
      await addTaskComment(commentTask.id, commentContent.trim());
      setCommentContent('');
      setComments(await listTaskComments(commentTask.id));
      message.success('评论已发布');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评论发布失败');
    }
  };

  const removeComment = async (commentId: number) => {
    if (!commentTask) {
      return;
    }
    try {
      await deleteTaskComment(commentTask.id, commentId);
      setComments(await listTaskComments(commentTask.id));
      message.success('评论已删除');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评论删除失败');
    }
  };

  const columns: TableProps<OaTask>['columns'] = [
    { title: '任务标题', dataIndex: 'title', key: 'title', ellipsis: true },
    { title: '负责人', dataIndex: 'ownerName', key: 'ownerName', width: 110, render: (value?: string) => value ?? '-' },
    {
      title: '优先级',
      dataIndex: 'priority',
      key: 'priority',
      width: 90,
      render: (value: OaTaskPriority) => {
        const meta = PRIORITY_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: OaTaskStatus) => {
        const meta = TASK_STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    { title: '截止日期', dataIndex: 'dueDate', key: 'dueDate', width: 120, render: (value?: string) => value ?? '-' },
    {
      title: '来源',
      dataIndex: 'source',
      key: 'source',
      width: 90,
      render: (value: OaTaskSource) => SOURCE_META[value] ?? value,
    },
    {
      title: '操作',
      key: 'action',
      width: 300,
      render: (_value, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => void openComments(record)}>
            评论
          </Button>
          {canManage && record.status !== 'DONE' && record.status !== 'CANCELLED' ? (
            <Button type="link" size="small" onClick={() => void openTask(record)}>
              编辑
            </Button>
          ) : null}
          {canManage && record.status === 'TODO' ? (
            <Button type="link" size="small" onClick={() => void changeStatus(record, 'DOING')}>
              开始
            </Button>
          ) : null}
          {canManage && record.status === 'DOING' ? (
            <Button type="link" size="small" onClick={() => void changeStatus(record, 'TODO')}>
              暂停
            </Button>
          ) : null}
          {canManage && record.status !== 'DONE' && record.status !== 'CANCELLED' ? (
            <Button type="link" size="small" onClick={() => void changeStatus(record, 'DONE')}>
              完成
            </Button>
          ) : null}
          {canManage && record.status !== 'DONE' && record.status !== 'CANCELLED' ? (
            <Popconfirm title="确认取消该任务？" onConfirm={() => void changeStatus(record, 'CANCELLED')}>
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          ) : null}
          {canManage ? (
            <Popconfirm title="确认删除该任务？" onConfirm={() => void removeTask(record)}>
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
    <>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Space wrap>
          <Space size={4}>
            <span>只看我的</span>
            <Switch
              checked={mine}
              onChange={(checked) => {
                setMine(checked);
                setPage(1);
              }}
            />
          </Space>
          <Input
            allowClear
            placeholder="任务标题"
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
            options={Object.entries(TASK_STATUS_META).map(([value, meta]) => ({ label: meta.text, value }))}
            onChange={(value) => {
              setStatus((value as OaTaskStatus) ?? '');
              setPage(1);
            }}
          />
          <Select
            allowClear
            placeholder="优先级"
            style={{ width: 120 }}
            value={priority || undefined}
            options={Object.entries(PRIORITY_META).map(([value, meta]) => ({ label: meta.text, value }))}
            onChange={(value) => {
              setPriority((value as OaTaskPriority) ?? '');
              setPage(1);
            }}
          />
          <Select
            allowClear
            placeholder="来源"
            style={{ width: 120 }}
            value={source || undefined}
            options={Object.entries(SOURCE_META).map(([value, label]) => ({ label, value }))}
            onChange={(value) => {
              setSource((value as OaTaskSource) ?? '');
              setPage(1);
            }}
          />
          {canManage ? (
            <Button type="primary" onClick={() => openTask(null)}>
              新建任务
            </Button>
          ) : null}
          <Button onClick={() => void refresh()}>刷新</Button>
        </Space>
        <Table<OaTask>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={tasks}
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
        title={editingTask ? '编辑任务' : '新建任务'}
        width={640}
        onOk={() => void saveTask()}
        confirmLoading={saving}
        onCancel={() => setModal(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="任务标题" rules={[{ required: true, message: '请输入任务标题' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="description" label="任务描述">
            <Input.TextArea rows={4} maxLength={2000} showCount />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="ownerId"
              label="负责人ID"
              rules={[{ required: true, message: '请输入负责人ID' }]}
              style={{ flex: 1 }}
            >
              <InputNumber style={{ width: '100%' }} min={1} precision={0} />
            </Form.Item>
            <Form.Item name="ownerName" label="负责人姓名" style={{ flex: 1 }}>
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="priority" label="优先级" style={{ flex: 1 }}>
              <Select
                options={Object.entries(PRIORITY_META).map(([value, meta]) => ({ label: meta.text, value }))}
              />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="dueDate" label="截止日期" style={{ flex: 1 }}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="source" label="来源" style={{ flex: 1 }}>
              <Select options={Object.entries(SOURCE_META).map(([value, label]) => ({ label, value }))} />
            </Form.Item>
            <Form.Item name="sourceId" label="来源业务ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
      <Drawer
        open={commentOpen}
        width={520}
        title={commentTask ? `评论：${commentTask.title}` : '任务评论'}
        onClose={() => setCommentOpen(false)}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {canComment ? (
            <Space.Compact style={{ width: '100%' }}>
              <Input
                value={commentContent}
                maxLength={500}
                placeholder="输入评论内容"
                onChange={(event) => setCommentContent(event.target.value)}
                onPressEnter={() => void submitComment()}
              />
              <Button type="primary" onClick={() => void submitComment()}>
                发布
              </Button>
            </Space.Compact>
          ) : null}
          <List<OaTaskComment>
            dataSource={comments}
            locale={{ emptyText: '暂无评论' }}
            renderItem={(item) => (
              <List.Item
                actions={
                  canComment
                    ? [
                        <Button
                          key="delete"
                          type="link"
                          size="small"
                          danger
                          onClick={() => void removeComment(item.id)}
                        >
                          删除
                        </Button>,
                      ]
                    : undefined
                }
              >
                <List.Item.Meta
                  title={`${item.userName ?? item.userId ?? '-'} · ${timeText(item.createTime)}`}
                  description={item.content}
                />
              </List.Item>
            )}
          />
        </Space>
      </Drawer>
    </>
  );
}
