// 邮箱账号设置：绑定 / 编辑 / 解绑 / 单账号同步。
//
// 关键约束：**页面不收集邮箱密码**。表里只存「凭据引用」(`secretRef`)，
// 也就是配置中心 / 环境变量里的键名，真实值由部署侧注入。
// 若在页面里输入密码，密码就会经过浏览器与后端日志、并在库里留下密文，
// 轮换时还得改数据库——这条线一开始就不能开。

import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  message as toast,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import {
  createAccount,
  listAccounts,
  removeAccount,
  syncAccount,
  updateAccount,
  type MailAccount,
  type MailAccountSaveCommand,
} from '../api/mail';
import RulePanel from '../components/RulePanel';

/** 账号表单值 */
type AccountFormValues = MailAccountSaveCommand;

/** 账号设置页 */
export default function AccountPage() {
  const [rows, setRows] = useState<MailAccount[]>([]);
  const [loading, setLoading] = useState(false);
  const [syncingId, setSyncingId] = useState<number | null>(null);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<MailAccount | null>(null);
  const [form] = Form.useForm<AccountFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await listAccounts());
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '账号列表加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  /** 打开新建 / 编辑弹窗 */
  const openForm = (account?: MailAccount) => {
    setEditing(account ?? null);
    setOpen(true);
    if (account) {
      form.setFieldsValue({
        email: account.email,
        displayName: account.displayName ?? undefined,
        accountType: account.accountType ?? 'PERSONAL',
        imapHost: account.imapHost ?? undefined,
        imapPort: account.imapPort ?? undefined,
        smtpHost: account.smtpHost ?? undefined,
        smtpPort: account.smtpPort ?? undefined,
        syncEnabled: account.syncEnabled === 1,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ accountType: 'PERSONAL', syncEnabled: true });
    }
  };

  /** 提交表单 */
  const handleSubmit = async () => {
    let values: AccountFormValues;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    try {
      if (editing) {
        await updateAccount(editing.id, values);
        toast.success('账号已更新');
      } else {
        await createAccount(values);
        toast.success('账号已绑定');
      }
      setOpen(false);
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '保存失败');
    }
  };

  /** 解绑账号 */
  const handleRemove = async (account: MailAccount) => {
    try {
      await removeAccount(account.id);
      toast.success('已解绑');
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '解绑失败');
    }
  };

  /** 单账号手动同步 */
  const handleSync = async (account: MailAccount) => {
    setSyncingId(account.id);
    try {
      const added = await syncAccount(account.id);
      toast.success(added > 0 ? `同步完成，新增 ${added} 封` : '同步完成，没有新邮件');
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '同步失败');
    } finally {
      setSyncingId(null);
    }
  };

  const columns: ColumnsType<MailAccount> = [
    {
      title: '邮箱',
      dataIndex: 'email',
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{row.email}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.displayName || '未设置显示名'}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '协议端点',
      dataIndex: 'imapHost',
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text style={{ fontSize: 12 }}>
            IMAP {row.imapHost || '-'}:{row.imapPort ?? '-'}
          </Typography.Text>
          <Typography.Text style={{ fontSize: 12 }}>
            SMTP {row.smtpHost || '-'}:{row.smtpPort ?? '-'}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'accountType',
      width: 90,
      render: (value: string | null) =>
        value === 'SHARED' ? <Tag color="blue">共享</Tag> : <Tag>个人</Tag>,
    },
    {
      title: '定时同步',
      dataIndex: 'syncEnabled',
      width: 100,
      render: (value: number | null) =>
        value === 1 ? <Tag color="green">开启</Tag> : <Tag color="default">关闭</Tag>,
    },
    {
      title: '最近同步',
      dataIndex: 'lastSyncTime',
      width: 170,
      render: (value: string | null) =>
        value ? dayjs(value).format('YYYY-MM-DD HH:mm') : <Typography.Text type="secondary">从未</Typography.Text>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_, row) => (
        <Space size={4}>
          <Button size="small" type="link" onClick={() => openForm(row)}>
            编辑
          </Button>
          <Button size="small" type="link" loading={syncingId === row.id} onClick={() => handleSync(row)}>
            同步
          </Button>
          <Popconfirm
            title="解绑后本地索引与草稿会一并清理，确定继续？"
            okText="解绑"
            okButtonProps={{ danger: true }}
            cancelText="取消"
            onConfirm={() => handleRemove(row)}
          >
            <Button size="small" type="link" danger>
              解绑
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ width: '100%', overflow: 'auto' }}>
      <Tabs
        items={[
          {
            key: 'account',
            label: '邮箱账号',
            children: (
              <Card
                title="邮箱账号"
                extra={
                  <Button type="primary" onClick={() => openForm()}>
                    绑定账号
                  </Button>
                }
                style={{ borderRadius: 8 }}
              >
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 16 }}
                  message="凭据只存「引用键」，不存密码"
                  description={
                    <span>
                      例如填 <Typography.Text code>mail.credential.zhangsan</Typography.Text>，
                      其值由部署侧注入到环境变量或配置中心。填错会导致同步与发信报「凭据解析失败」，
                      而不是静默用空密码连接。
                    </span>
                  }
                />
                <Table<MailAccount>
                  rowKey="id"
                  size="middle"
                  loading={loading}
                  columns={columns}
                  dataSource={rows}
                  pagination={false}
                  locale={{ emptyText: '还没有绑定邮箱账号' }}
                />
              </Card>
            ),
          },
          { key: 'rule', label: '收信规则', children: <RulePanel /> },
        ]}
      />

      <Modal
        open={open}
        title={editing ? '编辑邮箱账号' : '绑定邮箱账号'}
        width={640}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        okText={editing ? '保存' : '绑定'}
        destroyOnClose
      >
        <Form form={form} layout="vertical" requiredMark={false}>
          <Form.Item
            name="email"
            label="邮箱地址"
            rules={[
              { required: true, message: '请输入邮箱地址' },
              { type: 'email', message: '邮箱地址格式不正确' },
            ]}
          >
            <Input placeholder="zhangsan@example.com" maxLength={190} />
          </Form.Item>
          <Space style={{ width: '100%' }} size={16}>
            <Form.Item name="displayName" label="发件显示名" style={{ flex: 1 }}>
              <Input placeholder="张三" maxLength={96} />
            </Form.Item>
            <Form.Item name="accountType" label="账号类型" style={{ width: 160 }}>
              <Input placeholder="PERSONAL / SHARED" maxLength={16} />
            </Form.Item>
          </Space>
          <Form.Item
            name="secretRef"
            label="凭据引用"
            tooltip="配置中心或环境变量里的键名，不是密码本身"
            rules={[{ required: !editing, message: '请输入凭据引用键名' }]}
          >
            <Input placeholder="mail.credential.zhangsan" maxLength={190} />
          </Form.Item>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            留空协议端点则使用全局默认（WHALE_MAIL_IMAP_HOST / WHALE_MAIL_SMTP_HOST）
          </Typography.Text>
          <Space style={{ width: '100%', marginTop: 8 }} size={16}>
            <Form.Item name="imapHost" label="IMAP 主机" style={{ flex: 1 }}>
              <Input placeholder="imap.example.com" maxLength={128} />
            </Form.Item>
            <Form.Item name="imapPort" label="IMAP 端口" style={{ width: 140 }}>
              <InputNumber min={1} max={65535} style={{ width: '100%' }} placeholder="993" />
            </Form.Item>
            <Form.Item name="imapSsl" label="IMAP SSL" valuePropName="checked" style={{ width: 100 }}>
              <Switch />
            </Form.Item>
          </Space>
          <Space style={{ width: '100%' }} size={16}>
            <Form.Item name="smtpHost" label="SMTP 主机" style={{ flex: 1 }}>
              <Input placeholder="smtp.example.com" maxLength={128} />
            </Form.Item>
            <Form.Item name="smtpPort" label="SMTP 端口" style={{ width: 140 }}>
              <InputNumber min={1} max={65535} style={{ width: '100%' }} placeholder="465" />
            </Form.Item>
            <Form.Item name="smtpSsl" label="SMTP SSL" valuePropName="checked" style={{ width: 100 }}>
              <Switch />
            </Form.Item>
          </Space>
          <Form.Item name="syncEnabled" label="参与定时同步" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
