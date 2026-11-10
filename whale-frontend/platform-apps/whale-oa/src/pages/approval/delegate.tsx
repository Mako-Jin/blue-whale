// 审批代理设置：本人可将指定流程在时段内委托给代理人
//
// 委托人与租户由后端强制取当前登录用户；前端只维护流程编码、代理人、时段与启用状态。

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import {
  createDelegateRule,
  deleteDelegateRule,
  pageDelegateRules,
  updateDelegateRule,
} from '../../api/approval';
import type { ApprovalDelegateRule, ApprovalDelegateRuleInput } from '../../api/approval';

/** 表单字段 */
interface DelegateFormValues {
  definitionCode: string;
  toAccount: string;
  period: [Dayjs, Dayjs];
  enabled: boolean;
  remark?: string;
}

/** 时间展示 */
function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-';
}

export default function ApprovalDelegate() {
  const [records, setRecords] = useState<ApprovalDelegateRule[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ApprovalDelegateRule | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<DelegateFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageDelegateRules(current, size);
      setRecords(result.records);
      setTotal(result.total);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '加载代理规则失败');
    } finally {
      setLoading(false);
    }
  }, [current, size]);

  useEffect(() => {
    void load();
  }, [load]);

  /** 打开新建/编辑弹窗 */
  const openModal = (rule?: ApprovalDelegateRule) => {
    setEditing(rule ?? null);
    form.resetFields();
    if (rule) {
      form.setFieldsValue({
        definitionCode: rule.definitionCode,
        toAccount: rule.toAccount,
        period: [dayjs(rule.startTime), dayjs(rule.endTime)],
        enabled: rule.enabled === 1,
        remark: rule.remark,
      });
    } else {
      form.setFieldsValue({ enabled: true });
    }
    setModalOpen(true);
  };

  /** 提交表单 */
  const submit = async () => {
    const values = await form.validateFields();
    const input: ApprovalDelegateRuleInput = {
      definitionCode: values.definitionCode.trim(),
      toAccount: values.toAccount.trim(),
      startTime: values.period[0].format('YYYY-MM-DDTHH:mm:ss'),
      endTime: values.period[1].format('YYYY-MM-DDTHH:mm:ss'),
      enabled: values.enabled,
      remark: values.remark?.trim() || undefined,
    };
    setSubmitting(true);
    try {
      if (editing) {
        await updateDelegateRule(editing.id, input);
        message.success('代理规则已更新');
      } else {
        await createDelegateRule(input);
        message.success('代理规则已创建');
      }
      setModalOpen(false);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存代理规则失败');
    } finally {
      setSubmitting(false);
    }
  };

  /** 删除规则 */
  const remove = async (id: number) => {
    try {
      await deleteDelegateRule(id);
      message.success('代理规则已删除');
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '删除代理规则失败');
    }
  };

  const columns: ColumnsType<ApprovalDelegateRule> = [
    {
      title: '流程编码',
      dataIndex: 'definitionCode',
      width: 170,
    },
    {
      title: '代理人',
      dataIndex: 'toAccount',
      width: 140,
    },
    {
      title: '生效时间',
      key: 'period',
      render: (_, row) => `${formatTime(row.startTime)} ~ ${formatTime(row.endTime)}`,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (value: number | undefined) =>
        value === 1 ? <Tag color="success">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '备注',
      dataIndex: 'remark',
      ellipsis: true,
      render: (value: string | undefined) => value || '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      render: (_, row) => (
        <Space>
          <Button type="link" onClick={() => openModal(row)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该代理规则？" onConfirm={() => void remove(row.id)}>
            <Button type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="审批代理设置"
      extra={
        <Space>
          <Button onClick={() => void load()}>刷新</Button>
          <Button type="primary" onClick={() => openModal()}>
            新建代理规则
          </Button>
        </Space>
      }
    >
      <Table<ApprovalDelegateRule>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={records}
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

      <Modal
        title={editing ? '编辑代理规则' : '新建代理规则'}
        open={modalOpen}
        confirmLoading={submitting}
        onOk={() => void submit()}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="流程编码"
            name="definitionCode"
            rules={[{ required: true, message: '请输入流程编码' }]}
          >
            <Input maxLength={64} placeholder="如 leave_apply" />
          </Form.Item>
          <Form.Item
            label="代理人账号"
            name="toAccount"
            rules={[{ required: true, message: '请输入代理人账号' }]}
          >
            <Input maxLength={64} placeholder="接收审批任务的账号" />
          </Form.Item>
          <Form.Item
            label="生效时段"
            name="period"
            rules={[{ required: true, message: '请选择生效时段' }]}
          >
            <DatePicker.RangePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="启用" name="enabled" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={2} maxLength={200} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
