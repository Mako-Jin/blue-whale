// PDM 变更通知（ECN）页面：草稿 → 通知 → 生效（旧版本作废） → 关闭
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  cancelChgNotice,
  closeChgNotice,
  createChgNotice,
  deleteChgNotice,
  effectiveChgNotice,
  notifyChgNotice,
  pageChgNotices,
  pageChgNotifyLogs,
  retryChgNotice,
  updateChgNotice,
  type ChgNotice,
  type ChgNoticeInput,
  type ChgNoticeStatus,
  type ChgNotifyLog,
} from '../../api/changeNotice';
import { pageChgOrders, type ChgOrder } from '../../api/changeOrder';
import { hasPermission } from '../../utils/permission';

/** 状态标签 */
const STATUS_META: Record<ChgNoticeStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  NOTIFIED: { text: '已通知', color: 'blue' },
  EFFECTIVE: { text: '已生效', color: 'green' },
  CLOSED: { text: '已关闭', color: 'gray' },
  CANCELLED: { text: '已取消', color: 'red' },
};

/** 表单值 */
interface NoticeFormValues {
  ecoId: number;
  title: string;
  content?: string;
  notifyTargets?: string[];
  notifyChannels?: string[];
  effectiveFrom: string;
}

/** 转换表单值为接口入参 */
function toInput(values: NoticeFormValues): ChgNoticeInput {
  const effectiveFrom =
    values.effectiveFrom.length === 16 ? `${values.effectiveFrom}:00` : values.effectiveFrom;
  return {
    ecoId: values.ecoId,
    title: values.title.trim(),
    content: values.content,
    notifyTargets: (values.notifyTargets ?? []).map((item) => Number(item)).filter((item) => !Number.isNaN(item)),
    notifyChannels: values.notifyChannels ?? [],
    effectiveFrom,
  };
}

/**
 * 变更通知页
 */
export default function ChangeNoticePage() {
  const [rows, setRows] = useState<ChgNotice[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [noticeNo, setNoticeNo] = useState('');
  const [status, setStatus] = useState<ChgNoticeStatus | undefined>();
  const [orders, setOrders] = useState<ChgOrder[]>([]);
  const [editing, setEditing] = useState<ChgNotice | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [logs, setLogs] = useState<ChgNotifyLog[]>([]);
  const [logOpen, setLogOpen] = useState(false);
  const [form] = Form.useForm<NoticeFormValues>();

  /** 加载列表 */
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageChgNotices({ current, size, noticeNo, status });
      setRows(result.records ?? []);
      setTotal(Number(result.total ?? 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '变更通知加载失败');
    } finally {
      setLoading(false);
    }
  }, [current, size, noticeNo, status]);

  useEffect(() => {
    void load();
  }, [load]);

  /** 加载可创建通知的变更单 */
  const loadOrders = useCallback(async () => {
    try {
      const result = await pageChgOrders({ current: 1, size: 200 });
      setOrders((result.records ?? []).filter((item) =>
        ['APPROVED', 'EXECUTING', 'COMPLETED', 'CLOSED'].includes(item.status)));
    } catch {
      setOrders([]);
    }
  }, []);

  /** 打开新建/编辑表单 */
  const openForm = (row?: ChgNotice) => {
    setEditing(row ?? null);
    setFormOpen(true);
    if (row) {
      form.setFieldsValue({
        ecoId: Number(row.ecoId),
        title: row.title,
        content: row.content,
        notifyTargets: (row.notifyTargets ?? []).map((item) => String(item)),
        notifyChannels: row.notifyChannels,
        effectiveFrom: row.effectiveFrom?.slice(0, 16),
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ notifyChannels: ['INBOX'] });
      void loadOrders();
    }
  };

  /** 提交表单 */
  const submitForm = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (editing) {
        await updateChgNotice(editing.id, toInput(values));
        message.success('变更通知已更新');
      } else {
        await createChgNotice(toInput(values));
        message.success('变更通知已创建');
      }
      setFormOpen(false);
      await load();
    } catch (error) {
      if (error instanceof Error) {
        message.error(error.message);
      }
    } finally {
      setSubmitting(false);
    }
  };

  /** 执行状态动作 */
  const runAction = async (action: (id: string) => Promise<unknown>, text: string, id: string) => {
    try {
      await action(id);
      message.success(text);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    }
  };

  /** 打开投递日志 */
  const openLogs = async (row: ChgNotice) => {
    setLogOpen(true);
    try {
      const result = await pageChgNotifyLogs(row.id);
      setLogs(result.records ?? []);
    } catch (error) {
      setLogs([]);
      message.error(error instanceof Error ? error.message : '日志加载失败');
    }
  };

  const columns: ColumnsType<ChgNotice> = [
    { title: '通知编号', dataIndex: 'noticeNo', width: 200 },
    { title: '来源变更单', dataIndex: 'ecoNo', width: 180, render: (value: string) => value || '-' },
    { title: '标题', dataIndex: 'title' },
    {
      title: '生效日期',
      dataIndex: 'effectiveFrom',
      width: 180,
      render: (value: string) => value?.replace('T', ' ') ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: ChgNoticeStatus) => (
        <Tag color={STATUS_META[value]?.color}>{STATUS_META[value]?.text ?? value}</Tag>
      ),
    },
    {
      title: '操作',
      width: 320,
      render: (_, row) => (
        <Space size="small" wrap>
          {row.status === 'DRAFT' && hasPermission('sys:pdm:change:notice:edit') && (
            <Button size="small" onClick={() => openForm(row)}>
              编辑
            </Button>
          )}
          {row.status === 'DRAFT' && hasPermission('sys:pdm:change:notice:notify') && (
            <Button size="small" type="primary" onClick={() => void runAction(notifyChgNotice, '已发送通知', row.id)}>
              通知
            </Button>
          )}
          {row.status === 'NOTIFIED' && hasPermission('sys:pdm:change:notice:notify') && (
            <Button size="small" onClick={() => void runAction(retryChgNotice, '已重试投递', row.id)}>
              重试
            </Button>
          )}
          {row.status === 'NOTIFIED' && hasPermission('sys:pdm:change:notice:effective') && (
            <Popconfirm title="确认生效？生效后旧版本将置为作废" onConfirm={() => void runAction(effectiveChgNotice, '已生效', row.id)}>
              <Button size="small" danger>
                生效
              </Button>
            </Popconfirm>
          )}
          {row.status === 'EFFECTIVE' && hasPermission('sys:pdm:change:notice:edit') && (
            <Button size="small" onClick={() => void runAction(closeChgNotice, '已关闭', row.id)}>
              关闭
            </Button>
          )}
          {hasPermission('sys:pdm:change:notice:log') && (
            <Button size="small" type="link" onClick={() => void openLogs(row)}>
              日志
            </Button>
          )}
          {row.status === 'DRAFT' && hasPermission('sys:pdm:change:notice:edit') && (
            <Popconfirm title="确认取消该通知？" onConfirm={() => void runAction(cancelChgNotice, '已取消', row.id)}>
              <Button size="small" type="link" danger>
                取消
              </Button>
            </Popconfirm>
          )}
          {row.status === 'DRAFT' && hasPermission('sys:pdm:change:notice:edit') && (
            <Popconfirm
              title="确认删除草稿？"
              onConfirm={async () => {
                try {
                  await deleteChgNotice(row.id);
                  message.success('已删除');
                  await load();
                } catch (error) {
                  message.error(error instanceof Error ? error.message : '删除失败');
                }
              }}
            >
              <Button size="small" type="link" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 16 }}>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="变更通知生效后会把同一文档/BOM 的更低版本置为作废；通知失败不回滚生效，可在投递日志中重试。"
      />
      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Input
            allowClear
            placeholder="通知编号"
            value={noticeNo}
            onChange={(event) => setNoticeNo(event.target.value)}
            style={{ width: 220 }}
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 140 }}
            value={status}
            onChange={(value) => setStatus(value)}
            options={Object.entries(STATUS_META).map(([value, meta]) => ({ value, label: meta.text }))}
          />
          <Button type="primary" onClick={() => void load()}>
            查询
          </Button>
          {hasPermission('sys:pdm:change:notice:add') && (
            <Button type="primary" onClick={() => openForm()}>
              新建通知
            </Button>
          )}
        </Space>
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={{
            current,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (page, pageSize) => {
              setCurrent(page);
              setSize(pageSize);
            },
          }}
        />
      </Card>

      <Modal
        title={editing ? '编辑变更通知' : '新建变更通知'}
        open={formOpen}
        confirmLoading={submitting}
        onOk={() => void submitForm()}
        onCancel={() => setFormOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="ecoId" label="来源变更单" rules={[{ required: true, message: '请选择变更单' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="仅已审批通过的变更单"
              options={orders.map((item) => ({ value: Number(item.id), label: `${item.ecoNo} ${item.title}` }))}
            />
          </Form.Item>
          <Form.Item name="title" label="通知标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="content" label="通知内容">
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item name="notifyTargets" label="接收人（用户ID，回车添加）">
            <Select mode="tags" tokenSeparators={[',', ' ']} placeholder="例如 1001" />
          </Form.Item>
          <Form.Item name="notifyChannels" label="通知渠道">
            <Select mode="tags" tokenSeparators={[',', ' ']} placeholder="INBOX / SMS / EMAIL" />
          </Form.Item>
          <Form.Item
            name="effectiveFrom"
            label="生效日期（不早于审批通过日）"
            rules={[{ required: true, message: '请选择生效日期' }]}
          >
            <Input type="datetime-local" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer title="通知投递日志" open={logOpen} onClose={() => setLogOpen(false)} width={640}>
        <Table
          rowKey="id"
          size="small"
          dataSource={logs}
          pagination={false}
          columns={[
            { title: '事件', dataIndex: 'eventType', width: 160 },
            {
              title: '结果',
              dataIndex: 'status',
              width: 90,
              render: (value: string) => (
                <Tag color={value === 'SUCCESS' ? 'green' : 'red'}>{value === 'SUCCESS' ? '成功' : '失败'}</Tag>
              ),
            },
            { title: '重试', dataIndex: 'retryCount', width: 70 },
            { title: '失败原因', dataIndex: 'lastError' },
            { title: '时间', dataIndex: 'createTime', width: 170 },
          ]}
        />
      </Drawer>

      {editing && (
        <Descriptions style={{ marginTop: 16 }} title="当前通知" column={3}>
          <Descriptions.Item label="编号">{editing.noticeNo}</Descriptions.Item>
          <Descriptions.Item label="状态">{STATUS_META[editing.status]?.text ?? editing.status}</Descriptions.Item>
          <Descriptions.Item label="影响对象">{editing.impactObjects?.length ?? 0} 项</Descriptions.Item>
        </Descriptions>
      )}
    </div>
  );
}
