// 证书管理页：发证 / 撤销 / 换发 / 状态筛选。
//
// 权限码与 migration-5.87-lms-m4a.sql 对齐（sys:lms:cert:*）。
// 发证依据由所选模板决定：模板为「考试通过」时必须填作答记录 ID，
// 服务端会核实该次作答属于持证人且成绩及格——前端只做必填提示，不重复判断能否发证。

import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { hasPermission } from '../api/client';
import {
  issueCertificate,
  pageCertTemplates,
  pageCertificates,
  renewCertificate,
  revokeCertificate,
  type CertSource,
  type CertStatus,
  type CertTemplateItem,
  type CertificateItem,
} from '../api/cert';

/** 来源展示 */
const SOURCE_LABEL: Record<CertSource, string> = {
  MANUAL: '手工发放',
  COURSE: '课程完成',
  EXAM: '考试通过',
  TRAINING: '培训完成',
};

/** 状态展示 */
const STATUS_TAG: Record<CertStatus, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '有效' },
  EXPIRED: { color: 'orange', label: '已过期' },
  REVOKED: { color: 'red', label: '已撤销' },
  RENEWED: { color: 'default', label: '已换发' },
};

/** 发证表单值 */
interface IssueValues {
  templateId: number;
  userId: number;
  username?: string;
  sourceId?: number;
  sourceTitle?: string;
  remark?: string;
}

/** 列表行 */
interface Row extends CertificateItem {
  key: string;
}

export default function CertificateListPage() {
  const [form] = Form.useForm<IssueValues>();
  const [revokeForm] = Form.useForm<{ reason: string }>();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<Row[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState<string | undefined>();
  const [status, setStatus] = useState<string | undefined>();
  const [templates, setTemplates] = useState<CertTemplateItem[]>([]);
  const [issueOpen, setIssueOpen] = useState(false);
  const [issuing, setIssuing] = useState(false);
  const [revoking, setRevoking] = useState<CertificateItem | null>(null);

  const canIssue = hasPermission('sys:lms:cert:issue');
  const canAdjust = hasPermission('sys:lms:cert:revoke');
  const templateId = Form.useWatch('templateId', form);
  const selected = templates.find((item) => item.id === templateId);
  const basis: CertSource = selected?.issueBasis ?? 'MANUAL';

  const load = useCallback(
    async (page = 1, nextKeyword = keyword, nextStatus = status) => {
      setLoading(true);
      try {
        const data = await pageCertificates({
          keyword: nextKeyword,
          status: nextStatus,
          current: page,
          size: 10,
        });
        setRows((data.records ?? []).map((item) => ({ ...item, key: String(item.id) })));
        setTotal(data.total ?? 0);
        setCurrent(page);
      } finally {
        setLoading(false);
      }
    },
    [keyword, status],
  );

  useEffect(() => {
    void load(1, undefined, undefined);
    // 首次加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openIssue = async () => {
    if (templates.length === 0) {
      const data = await pageCertTemplates({ status: 'ENABLED', current: 1, size: 100 });
      setTemplates(data.records ?? []);
    }
    form.resetFields();
    setIssueOpen(true);
  };

  const onIssue = async (values: IssueValues) => {
    setIssuing(true);
    try {
      await issueCertificate({
        templateId: values.templateId,
        userId: values.userId,
        username: values.username,
        sourceType: basis,
        sourceId: basis === 'MANUAL' ? undefined : values.sourceId,
        sourceTitle: values.sourceTitle,
        remark: values.remark,
      });
      message.success('证书已发放');
      setIssueOpen(false);
      await load(1, keyword, status);
    } finally {
      setIssuing(false);
    }
  };

  const onRevoke = async (values: { reason: string }) => {
    if (!revoking) {
      return;
    }
    await revokeCertificate(revoking.id, values.reason);
    message.success('证书已撤销');
    setRevoking(null);
    revokeForm.resetFields();
    await load(current, keyword, status);
  };

  const onRenew = async (row: Row) => {
    const newId = await renewCertificate(row.id);
    message.success(`已换发，新证书 ID：${newId}`);
    await load(current, keyword, status);
  };

  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="证书编号 / 持证人 / 名称"
          allowClear
          style={{ width: 260 }}
          onSearch={(value) => {
            setKeyword(value || undefined);
            void load(1, value || undefined, status);
          }}
        />
        <Select
          placeholder="状态"
          allowClear
          style={{ width: 130 }}
          options={Object.entries(STATUS_TAG).map(([value, item]) => ({
            value,
            label: item.label,
          }))}
          onChange={(value) => {
            setStatus(value || undefined);
            void load(1, keyword, value || undefined);
          }}
        />
        {canIssue && (
          <Button type="primary" onClick={() => void openIssue()}>
            发放证书
          </Button>
        )}
      </Space>

      <Table<Row>
        rowKey="key"
        loading={loading}
        dataSource={rows}
        pagination={{
          current,
          total,
          pageSize: 10,
          onChange: (page) => void load(page, keyword, status),
        }}
        columns={[
          { title: '证书编号', dataIndex: 'certNo', width: 190 },
          { title: '证书名称', dataIndex: 'templateName' },
          {
            title: '持证人',
            dataIndex: 'username',
            width: 120,
            render: (value, row) => value ?? `#${row.userId}`,
          },
          {
            title: '来源',
            dataIndex: 'sourceType',
            width: 110,
            render: (value: CertSource) => SOURCE_LABEL[value] ?? value,
          },
          {
            title: '发证时间',
            dataIndex: 'issuedAt',
            width: 170,
            render: (value: string) => (value ? value.replace('T', ' ').slice(0, 19) : '-'),
          },
          {
            title: '有效期至',
            dataIndex: 'validTo',
            width: 130,
            render: (value: string, row) => (row.longTerm ? '长期' : (value?.slice(0, 10) ?? '-')),
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (value: CertStatus) => {
              const tag = STATUS_TAG[value] ?? { color: 'default', label: value };
              return <Tag color={tag.color}>{tag.label}</Tag>;
            },
          },
          {
            title: '操作',
            width: 160,
            render: (_, row) => (
              <Space size="small">
                {canAdjust && (row.status === 'ACTIVE' || row.status === 'EXPIRED') && (
                  <Button size="small" type="link" onClick={() => setRevoking(row)}>
                    撤销
                  </Button>
                )}
                {canAdjust && (row.status === 'ACTIVE' || row.status === 'EXPIRED') && (
                  <Popconfirm
                    title="确认换发？原证书将标记为「已换发」，新证书按模板有效期重新起算"
                    onConfirm={() => void onRenew(row)}
                  >
                    <Button size="small" type="link">
                      换发
                    </Button>
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title="发放证书"
        open={issueOpen}
        forceRender
        confirmLoading={issuing}
        onCancel={() => setIssueOpen(false)}
        onOk={() => form.submit()}
        width={620}
      >
        <Form form={form} layout="vertical" onFinish={onIssue}>
          <Form.Item
            name="templateId"
            label="证书模板"
            rules={[{ required: true, message: '请选择模板' }]}
          >
            <Select
              placeholder="选择启用的模板"
              options={templates.map((item) => ({
                value: item.id,
                label: `${item.name}（${SOURCE_LABEL[item.issueBasis]}）`,
              }))}
            />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="userId"
              label="持证人用户 ID"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请输入持证人用户 ID' }]}
            >
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="username" label="持证人用户名" style={{ flex: 1 }}>
              <Input placeholder="用于列表展示" />
            </Form.Item>
          </Space>
          {basis !== 'MANUAL' && (
            <Form.Item
              name="sourceId"
              label="来源业务 ID"
              rules={[{ required: true, message: '该模板要求提供来源业务 ID' }]}
              extra={
                basis === 'EXAM'
                  ? '填考试作答记录 ID：服务端会核实该次作答属于持证人且成绩及格'
                  : basis === 'COURSE'
                    ? '填课程 ID：服务端会核实已报名且章节全部学完'
                    : '填培训班 ID：服务端会核实已分配到该班'
              }
            >
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
          )}
          <Form.Item name="sourceTitle" label="来源标题">
            <Input placeholder="如 2026 年安全生产考试（仅记录）" maxLength={200} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`撤销证书 ${revoking?.certNo ?? ''}`}
        open={revoking !== null}
        forceRender
        onCancel={() => setRevoking(null)}
        onOk={() => revokeForm.submit()}
      >
        <Form form={revokeForm} layout="vertical" onFinish={onRevoke}>
          <Form.Item
            name="reason"
            label="撤销原因"
            rules={[{ required: true, message: '撤销原因必填，作为合规留痕' }]}
          >
            <Input.TextArea rows={3} maxLength={500} placeholder="如 考试存在违纪行为" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
