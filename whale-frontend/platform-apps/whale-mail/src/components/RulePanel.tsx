// 收信规则：条件 + 动作，按优先级从上到下执行，命中第一条即停。
//
// 两个刻意的限制，界面上都要说清楚：
// 1. 规则只改「平台里的归类」，不动服务器上的文件夹。手机客户端或其它 IMAP 客户端
//    看到的仍是原文件夹——这句必须写在页面上，否则用户会以为邮件真的被搬走了。
// 2. 目标文件夹不能选回收站。删信必须是人点的动作，规则静默丢回收站等于「邮件莫名消失」。

import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message as toast,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import {
  applyRules,
  listAccounts,
  listFolders,
  listRules,
  removeRule,
  saveRule,
  setRuleEnabled,
  type MailAccount,
  type MailFolder,
  type MailRule,
  type MailRuleSaveCommand,
} from '../api/mail';

/** 规则表单值（开关用布尔，提交前再归一） */
interface RuleFormValues extends MailRuleSaveCommand {
  enabled: boolean;
  onlyWithAttachment: boolean;
  actionMarkSeen: boolean;
  actionFlagged: boolean;
}

/** 把多值条件渲染成标签，空条件显示为「不限」 */
function renderCondition(value?: string | null): string {
  if (!value) {
    return '不限';
  }
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .join(' / ');
}

/** 规则面板 */
export default function RulePanel() {
  const [accounts, setAccounts] = useState<MailAccount[]>([]);
  const [accountId, setAccountId] = useState<number | undefined>();
  const [folders, setFolders] = useState<MailFolder[]>([]);
  const [rows, setRows] = useState<MailRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [applying, setApplying] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<MailRule | null>(null);
  const [form] = Form.useForm<RuleFormValues>();

  const loadAccounts = useCallback(async () => {
    try {
      const list = await listAccounts();
      setAccounts(list);
      setAccountId((prev) => prev ?? list[0]?.id);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '账号列表加载失败');
    }
  }, []);

  const loadRules = useCallback(async () => {
    if (!accountId) {
      return;
    }
    setLoading(true);
    try {
      const [rules, folderList] = await Promise.all([listRules(accountId), listFolders(accountId)]);
      setRows(rules);
      // 回收站不进下拉：规则不允许把邮件静默丢进回收站，后端也会拒
      setFolders(folderList.filter((item) => item.folderCode !== 'TRASH'));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '规则加载失败');
    } finally {
      setLoading(false);
    }
  }, [accountId]);

  useEffect(() => {
    void loadAccounts();
  }, [loadAccounts]);

  useEffect(() => {
    void loadRules();
  }, [loadRules]);

  /** 打开新建 / 编辑弹窗 */
  const openForm = (rule?: MailRule) => {
    setEditing(rule ?? null);
    setOpen(true);
    if (rule) {
      form.setFieldsValue({
        id: rule.id,
        name: rule.name,
        sortNo: rule.sortNo,
        enabled: rule.enabled,
        matchFrom: rule.matchFrom ?? undefined,
        matchSubject: rule.matchSubject ?? undefined,
        matchTo: rule.matchTo ?? undefined,
        onlyWithAttachment: rule.onlyWithAttachment === true,
        actionFolderId: rule.actionFolderId ?? undefined,
        actionMarkSeen: rule.actionMarkSeen === true,
        actionFlagged: rule.actionFlagged === true,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        sortNo: 100,
        enabled: true,
        onlyWithAttachment: false,
        actionMarkSeen: false,
        actionFlagged: false,
      });
    }
  };

  /** 提交表单 */
  const handleSubmit = async () => {
    let values: RuleFormValues;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    try {
      await saveRule({
        ...values,
        accountId,
        actionFolderId: values.actionFolderId ?? undefined,
      });
      toast.success(editing ? '规则已更新' : '规则已创建');
      setOpen(false);
      await loadRules();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '保存失败');
    }
  };

  /** 切换启用状态 */
  const handleToggle = async (rule: MailRule, enabled: boolean) => {
    try {
      await setRuleEnabled(rule.id, enabled);
      setRows((prev) => prev.map((item) => (item.id === rule.id ? { ...item, enabled } : item)));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '状态更新失败');
    }
  };

  /** 删除规则 */
  const handleRemove = async (rule: MailRule) => {
    try {
      await removeRule(rule.id);
      toast.success('规则已删除');
      await loadRules();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '删除失败');
    }
  };

  /** 回填收件箱存量邮件 */
  const handleApply = async () => {
    setApplying(true);
    try {
      const result = await applyRules(accountId);
      if (result.scanned === 0) {
        toast.info('收件箱里没有可套用规则的邮件');
      } else if (result.changed === 0) {
        toast.success(`检查 ${result.scanned} 封，没有需要改动的邮件`);
      } else {
        toast.success(`检查 ${result.scanned} 封，改写 ${result.changed} 封`);
      }
      await loadRules();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '回填失败');
    } finally {
      setApplying(false);
    }
  };

  const columns: ColumnsType<MailRule> = [
    {
      title: '顺序',
      dataIndex: 'sortNo',
      width: 70,
      render: (value: number) => <Typography.Text type="secondary">{value}</Typography.Text>,
    },
    {
      title: '规则',
      dataIndex: 'name',
      render: (_, row) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{row.name}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            发件人 {renderCondition(row.matchFrom)} · 主题 {renderCondition(row.matchSubject)}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            收件人 {renderCondition(row.matchTo)}
            {row.onlyWithAttachment ? ' · 仅含附件' : ''}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '动作',
      key: 'actions',
      width: 240,
      render: (_, row) => (
        <Space size={4} wrap>
          {row.actionFolderId ? (
            <Tag color="blue">归入 {row.actionFolderName ?? '已下线文件夹'}</Tag>
          ) : null}
          {row.actionMarkSeen ? <Tag>标记已读</Tag> : null}
          {row.actionFlagged ? <Tag>加星标</Tag> : null}
        </Space>
      ),
    },
    {
      title: '命中',
      dataIndex: 'hitCount',
      width: 130,
      render: (value: number, row) => (
        <Tooltip
          title={
            row.lastHitTime
              ? `最近 ${dayjs(row.lastHitTime).format('YYYY-MM-DD HH:mm')}`
              : '尚未命中'
          }
        >
          <span>{value} 封</span>
        </Tooltip>
      ),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (value: boolean, row) => (
        <Switch
          size="small"
          checked={value}
          onChange={(checked) => void handleToggle(row, checked)}
        />
      ),
    },
    {
      title: '操作',
      key: 'ops',
      width: 130,
      render: (_, row) => (
        <Space size={4}>
          <Button size="small" type="link" onClick={() => openForm(row)}>
            编辑
          </Button>
          <Popconfirm
            title="删除后已归类的邮件不会自动移回，确定删除该规则？"
            okText="删除"
            okButtonProps={{ danger: true }}
            cancelText="取消"
            onConfirm={() => handleRemove(row)}
          >
            <Button size="small" type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="规则只调整本平台的归类，不会移动服务器上的邮件"
        description={
          <span>
            归类后的邮件在平台里落在目标文件夹，但服务器上仍在原处——手机客户端或第三方 IMAP
            客户端看到的还是原来的位置。正文与附件不受影响：回源位置记在单独的字段上，不随归类改变。
          </span>
        }
      />
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          style={{ width: 240 }}
          placeholder="选择邮箱账号"
          value={accountId}
          onChange={(value) => setAccountId(value)}
          options={accounts.map((item) => ({ label: item.email, value: item.id }))}
        />
        <Button type="primary" onClick={() => openForm()}>
          新建规则
        </Button>
        <Button loading={applying} onClick={handleApply}>
          应用到收件箱存量
        </Button>
      </Space>
      <Table<MailRule>
        rowKey="id"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{
          emptyText: (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="还没有规则。规则按顺序执行，第一条命中即停。"
            />
          ),
        }}
      />
      <Modal
        open={open}
        title={editing ? '编辑规则' : '新建规则'}
        width={680}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        okText="保存"
        destroyOnClose
      >
        <Form form={form} layout="vertical" requiredMark={false}>
          <Space style={{ width: '100%' }} size={16}>
            <Form.Item
              name="name"
              label="规则名称"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请输入规则名称' }]}
            >
              <Input placeholder="发票邮件归档" maxLength={64} />
            </Form.Item>
            <Form.Item
              name="sortNo"
              label="优先级"
              style={{ width: 130 }}
              tooltip="数字小的先执行，命中第一条即停"
            >
              <InputNumber min={1} max={9999} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            条件之间是「同时满足」；同一个框里填多个值用英文逗号分隔，任一命中即可
          </Typography.Text>
          <Form.Item name="matchFrom" label="发件人包含" style={{ marginTop: 8 }}>
            <Input placeholder="finance@example.com, noreply@invoice.com" maxLength={190} />
          </Form.Item>
          <Form.Item name="matchSubject" label="主题包含">
            <Input placeholder="发票, 对账单" maxLength={255} />
          </Form.Item>
          <Form.Item name="matchTo" label="收件人或抄送包含">
            <Input placeholder="team@example.com" maxLength={190} />
          </Form.Item>
          <Form.Item name="onlyWithAttachment" label="仅匹配带附件的邮件" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="actionFolderId" label="归入文件夹">
            <Select
              allowClear
              placeholder="不移动（只做标记）"
              options={folders.map((item) => ({ label: item.name, value: item.id }))}
            />
          </Form.Item>
          <Space size={32}>
            <Form.Item name="actionMarkSeen" label="标记为已读" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="actionFlagged" label="加星标" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="enabled" label="立即启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </>
  );
}
