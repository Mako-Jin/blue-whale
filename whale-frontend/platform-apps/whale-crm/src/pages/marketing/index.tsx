// CRM 营销管理（M5）：活动 / 培育任务 / 批量触达 / 渠道伙伴与报单
//
// 菜单 437–440 分别对应四个路由，本页按 tab 渲染对应面板。
// 触达走 whale-message；同一活动同一联系人在同一渠道只发一次（后端幂等拦截，前端展示跳过数）。
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { hasPermission } from '../../utils/permission';
import {
  changeCampaignStatus,
  changeNurtureStatus,
  createCampaign,
  createNurture,
  createPartner,
  createPartnerReport,
  createSend,
  executeSend,
  lockAudience,
  pageCampaigns,
  pageNurture,
  pagePartnerReports,
  pagePartners,
  pageSends,
  previewAudience,
  resolvePartnerReport,
  updateCampaignRoi,
  type AudienceCriteria,
  type AudiencePreviewView,
  type CampaignView,
  type MarketingSendView,
  type NurtureTaskView,
  type PartnerReportView,
  type PartnerView,
} from '../../api/marketing';
import { formatDateTime } from '../../utils/dict';

const CAMPAIGN_TYPE_OPTIONS = [
  { value: 'EXHIBITION', label: '展会' },
  { value: 'LIVE', label: '直播' },
  { value: 'PROMOTION', label: '促销' },
  { value: 'OTHER', label: '其他' },
];

const CAMPAIGN_STATUS_MAP: Record<string, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  RUNNING: { label: '进行中', color: 'processing' },
  ENDED: { label: '已结束', color: 'success' },
  CANCELLED: { label: '已取消', color: 'red' },
};

const SEND_STATUS_MAP: Record<string, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  EXECUTING: { label: '执行中', color: 'processing' },
  COMPLETED: { label: '已完成', color: 'success' },
  FAILED: { label: '失败', color: 'red' },
};

const NURTURE_STATUS_MAP: Record<string, { label: string; color: string }> = {
  OPEN: { label: '待执行', color: 'processing' },
  DONE: { label: '已完成', color: 'success' },
  CANCELLED: { label: '已取消', color: 'default' },
};

const REPORT_STATUS_MAP: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待处理', color: 'processing' },
  APPROVED: { label: '已通过', color: 'success' },
  REJECTED: { label: '已驳回', color: 'default' },
  CONFLICT: { label: '冲突待裁决', color: 'red' },
};

const CHANNEL_OPTIONS = [
  { value: 'EMAIL', label: '邮件' },
  { value: 'SMS', label: '短信' },
  { value: 'INBOX', label: '站内信' },
];

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 可展示的错误信息
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

/** 营销 tab */
export type MarketingTab = 'campaign' | 'nurture' | 'send' | 'partner';

interface PanelProps {
  tab: MarketingTab;
}

/** 活动面板 */
function CampaignPanel() {
  const [rows, setRows] = useState<CampaignView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<Record<string, unknown>>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [formOpen, setFormOpen] = useState(false);
  const [roiTarget, setRoiTarget] = useState<CampaignView | null>(null);
  const [audienceTarget, setAudienceTarget] = useState<CampaignView | null>(null);
  const [preview, setPreview] = useState<AudiencePreviewView | null>(null);
  const [form] = Form.useForm();
  const [roiForm] = Form.useForm();
  const [audienceForm] = Form.useForm();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageCampaigns({ ...filters, ...page });
      setRows(result.records);
      setTotal(Number(result.total));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const changeStatus = async (row: CampaignView, status: string) => {
    try {
      await changeCampaignStatus(row.id, status);
      message.success('活动状态已更新');
      void loadData();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      await createCampaign(values);
      message.success('活动已创建');
      setFormOpen(false);
      void loadData();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    }
  };

  const handleRoi = async () => {
    if (!roiTarget) {
      return;
    }
    try {
      const values = await roiForm.validateFields();
      await updateCampaignRoi(roiTarget.id, values);
      message.success('ROI 已回填');
      setRoiTarget(null);
      void loadData();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    }
  };

  const handlePreview = async () => {
    if (!audienceTarget) {
      return;
    }
    try {
      const values = await audienceForm.validateFields();
      const criteria: AudienceCriteria = {
        industries: values.industries ? [values.industries] : undefined,
        levels: values.levels ? [values.levels] : undefined,
        sources: values.sources ? [values.sources] : undefined,
        lastFollowUpDays: values.lastFollowUpDays ?? null,
        ownerId: values.ownerId ?? null,
      };
      const result = await previewAudience(audienceTarget.id, criteria);
      setPreview(result);
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    }
  };

  const handleLock = async () => {
    if (!audienceTarget) {
      return;
    }
    try {
      const values = await audienceForm.validateFields();
      await lockAudience(audienceTarget.id, {
        industries: values.industries ? [values.industries] : undefined,
        levels: values.levels ? [values.levels] : undefined,
        sources: values.sources ? [values.sources] : undefined,
        lastFollowUpDays: values.lastFollowUpDays ?? null,
        ownerId: values.ownerId ?? null,
      });
      message.success('人群快照已锁定');
      setAudienceTarget(null);
      setPreview(null);
      void loadData();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    }
  };

  const columns: TableProps<CampaignView>['columns'] = [
    { title: '活动编码', dataIndex: 'campaignCode', width: 180 },
    { title: '活动名称', dataIndex: 'campaignName', width: 200, ellipsis: true },
    {
      title: '类型',
      dataIndex: 'campaignType',
      width: 100,
      render: (value?: string) => CAMPAIGN_TYPE_OPTIONS.find((item) => item.value === value)?.label ?? value ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value?: string) => (
        <Tag color={CAMPAIGN_STATUS_MAP[value ?? '']?.color}>
          {CAMPAIGN_STATUS_MAP[value ?? '']?.label ?? value ?? '-'}
        </Tag>
      ),
    },
    { title: '预算', dataIndex: 'budget', width: 120, render: (value?: number) => (value == null ? '-' : value) },
    { title: '实际费用', dataIndex: 'actualCost', width: 120, render: (value?: number) => (value == null ? '-' : value) },
    { title: '人群数', dataIndex: 'audienceCount', width: 90, render: (value?: number) => value ?? '-' },
    { title: '归因赢单额', dataIndex: 'attributedWonAmount', width: 130, render: (value?: number) => value ?? '-' },
    { title: 'ROI', dataIndex: 'roi', width: 100, render: (value?: number) => (value == null ? '-' : value) },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 300,
      render: (_, row) => (
        <Space size={0} wrap>
          {row.status === 'DRAFT' && hasPermission('sys:crm:campaign:status') && (
            <Button size="small" type="link" onClick={() => void changeStatus(row, 'RUNNING')}>启动</Button>
          )}
          {row.status === 'RUNNING' && hasPermission('sys:crm:campaign:status') && (
            <Button size="small" type="link" onClick={() => void changeStatus(row, 'ENDED')}>结束</Button>
          )}
          {(row.status === 'DRAFT' || row.status === 'RUNNING') && hasPermission('sys:crm:campaign:status') && (
            <Button size="small" type="link" danger onClick={() => void changeStatus(row, 'CANCELLED')}>取消</Button>
          )}
          {hasPermission('sys:crm:campaign:audience') && (
            <Button
              size="small"
              type="link"
              onClick={() => {
                setAudienceTarget(row);
                setPreview(null);
                audienceForm.resetFields();
              }}
            >
              人群
            </Button>
          )}
          {hasPermission('sys:crm:campaign:manage') && (
            <Button
              size="small"
              type="link"
              onClick={() => {
                setRoiTarget(row);
                roiForm.setFieldsValue({
                  actualCost: row.actualCost ?? undefined,
                  attributedWonAmount: row.attributedWonAmount ?? undefined,
                  attributedLeadCount: row.attributedLeadCount ?? undefined,
                });
              }}
            >
              ROI 回填
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="营销活动"
      extra={
        hasPermission('sys:crm:campaign:manage') && (
          <Button type="primary" onClick={() => { form.resetFields(); setFormOpen(true); }}>新建活动</Button>
        )
      }
    >
      <Form
        layout="inline"
        style={{ marginBottom: 16, rowGap: 12 }}
        onFinish={(values) => {
          setFilters({
            keyword: values.keyword || undefined,
            status: values.status || undefined,
            campaignType: values.campaignType || undefined,
          });
          setPage((prev) => ({ ...prev, current: 1 }));
        }}
      >
        <Form.Item name="keyword"><Input placeholder="活动编码 / 名称" allowClear style={{ width: 220 }} /></Form.Item>
        <Form.Item name="status">
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 130 }}
            options={Object.entries(CAMPAIGN_STATUS_MAP).map(([value, item]) => ({ value, label: item.label }))}
          />
        </Form.Item>
        <Form.Item name="campaignType">
          <Select placeholder="类型" allowClear style={{ width: 120 }} options={CAMPAIGN_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item><Button type="primary" htmlType="submit">查询</Button></Form.Item>
      </Form>
      <Table<CampaignView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1500 }}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
        ROI = 活动归因赢单金额 ÷ 实际费用；实际费用为 0 时 ROI 记 0。人群快照锁定后不可变更。
      </Typography.Text>

      <Modal
        open={formOpen}
        title="新建营销活动"
        okText="创建"
        cancelText="取消"
        onOk={() => void handleCreate()}
        onCancel={() => setFormOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="campaignName" label="活动名称" rules={[{ required: true, message: '请输入活动名称' }]}>
            <Input maxLength={200} showCount />
          </Form.Item>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="campaignType" label="活动类型" rules={[{ required: true, message: '请选择类型' }]} style={{ flex: 1 }}>
              <Select options={CAMPAIGN_TYPE_OPTIONS} />
            </Form.Item>
            <Form.Item name="channel" label="渠道" style={{ flex: 1 }}>
              <Select allowClear options={CHANNEL_OPTIONS} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="budget" label="预算" style={{ flex: 1 }}>
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="ownerId" label="负责人 ID" style={{ flex: 1 }}>
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="deptId" label="部门 ID" style={{ flex: 1 }}>
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="plannedStart" label="开始日期" style={{ flex: 1 }}>
              <Input placeholder="yyyy-MM-dd" />
            </Form.Item>
            <Form.Item name="plannedEnd" label="结束日期" style={{ flex: 1 }}>
              <Input placeholder="yyyy-MM-dd" />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal
        open={roiTarget !== null}
        title="回填活动 ROI"
        okText="保存"
        cancelText="取消"
        onOk={() => void handleRoi()}
        onCancel={() => setRoiTarget(null)}
        destroyOnClose
      >
        <Form form={roiForm} layout="vertical" preserve={false}>
          <Form.Item name="actualCost" label="实际费用">
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="attributedWonAmount" label="归因赢单金额">
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="attributedLeadCount" label="归因线索数">
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={audienceTarget !== null}
        title="活动人群预览与锁定"
        width={760}
        okText="锁定快照"
        cancelText="关闭"
        onOk={() => void handleLock()}
        onCancel={() => { setAudienceTarget(null); setPreview(null); }}
        destroyOnClose
      >
        <Form form={audienceForm} layout="vertical" preserve={false}>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="industries" label="行业" style={{ flex: 1 }}><Input allowClear /></Form.Item>
            <Form.Item name="levels" label="客户等级" style={{ flex: 1 }}><Input placeholder="A/B/C/D" allowClear /></Form.Item>
            <Form.Item name="sources" label="来源" style={{ flex: 1 }}><Input allowClear /></Form.Item>
          </Space>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="lastFollowUpDays" label="未跟进天数 ≥" style={{ flex: 1 }}>
              <InputNumber min={0} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="ownerId" label="负责人 ID" style={{ flex: 1 }}>
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label=" " style={{ flex: 1 }}>
              <Button onClick={() => void handlePreview()}>预览人数</Button>
            </Form.Item>
          </Space>
        </Form>
        {preview && (
          <>
            <Descriptions bordered size="small" column={1} style={{ marginBottom: 12 }}>
              <Descriptions.Item label="命中数量">{preview.count}</Descriptions.Item>
            </Descriptions>
            <Table
              size="small"
              rowKey="customerId"
              dataSource={preview.samples}
              pagination={false}
              columns={[
                { title: '客户', dataIndex: 'customerName' },
                { title: '等级', dataIndex: 'level', width: 80 },
                { title: '行业', dataIndex: 'industry', width: 130 },
                { title: '来源', dataIndex: 'source', width: 130 },
              ]}
            />
          </>
        )}
      </Modal>
    </Card>
  );
}

/** 培育任务面板 */
function NurturePanel() {
  const [rows, setRows] = useState<NurtureTaskView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<Record<string, unknown>>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [formOpen, setFormOpen] = useState(false);
  const [form] = Form.useForm();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageNurture({ ...filters, ...page });
      setRows(result.records);
      setTotal(Number(result.total));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const changeStatus = async (row: NurtureTaskView, status: string) => {
    try {
      await changeNurtureStatus(row.id, status);
      message.success('培育任务状态已更新');
      void loadData();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      await createNurture(values);
      message.success('培育任务已创建');
      setFormOpen(false);
      void loadData();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    }
  };

  const columns: TableProps<NurtureTaskView>['columns'] = [
    { title: '任务编号', dataIndex: 'taskNo', width: 190 },
    { title: '客户', dataIndex: 'customerName', width: 180, render: (value?: string, row?: NurtureTaskView) => value || row?.customerId || '-' },
    { title: '渠道', dataIndex: 'channel', width: 100, render: (value?: string) => value ?? '-' },
    { title: '计划日期', dataIndex: 'planDate', width: 120, render: (value?: string) => value ?? '-' },
    { title: '执行人', dataIndex: 'assigneeName', width: 120, render: (value?: string) => value || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value?: string) => (
        <Tag color={NURTURE_STATUS_MAP[value ?? '']?.color}>
          {NURTURE_STATUS_MAP[value ?? '']?.label ?? value ?? '-'}
        </Tag>
      ),
    },
    { title: '备注', dataIndex: 'note', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, row) => (
        <Space size={0}>
          {row.status === 'OPEN' && hasPermission('sys:crm:nurture:manage') && (
            <>
              <Button size="small" type="link" onClick={() => void changeStatus(row, 'DONE')}>完成</Button>
              <Button size="small" type="link" danger onClick={() => void changeStatus(row, 'CANCELLED')}>取消</Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="线索培育任务"
      extra={
        hasPermission('sys:crm:nurture:manage') && (
          <Button type="primary" onClick={() => { form.resetFields(); setFormOpen(true); }}>新建培育任务</Button>
        )
      }
    >
      <Form
        layout="inline"
        style={{ marginBottom: 16, rowGap: 12 }}
        onFinish={(values) => {
          setFilters({ status: values.status || undefined, customerId: values.customerId ?? null });
          setPage((prev) => ({ ...prev, current: 1 }));
        }}
      >
        <Form.Item name="status">
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 130 }}
            options={Object.entries(NURTURE_STATUS_MAP).map(([value, item]) => ({ value, label: item.label }))}
          />
        </Form.Item>
        <Form.Item name="customerId"><InputNumber min={1} precision={0} placeholder="客户 ID" style={{ width: 130 }} /></Form.Item>
        <Form.Item><Button type="primary" htmlType="submit">查询</Button></Form.Item>
      </Form>
      <Table<NurtureTaskView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
        培育不做复杂自动化：按计划日期生成人工任务，内容引用 whale-doc 文档版本，触达走消息服务。
      </Typography.Text>

      <Modal
        open={formOpen}
        title="新建培育任务"
        okText="创建"
        cancelText="取消"
        onOk={() => void handleCreate()}
        onCancel={() => setFormOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="customerId" label="客户 ID" rules={[{ required: true, message: '请输入客户 ID' }]} style={{ flex: 1 }}>
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="leadId" label="线索 ID" style={{ flex: 1 }}>
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="channel" label="渠道" style={{ flex: 1 }}>
              <Select allowClear options={CHANNEL_OPTIONS} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="contentDocId" label="内容文档 ID" style={{ flex: 1 }}>
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="contentDocVersion" label="文档版本" style={{ flex: 1 }}>
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="planDate" label="计划日期" style={{ flex: 1 }}>
              <Input placeholder="yyyy-MM-dd" />
            </Form.Item>
          </Space>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="assigneeId" label="执行人 ID" style={{ flex: 1 }}>
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="assigneeName" label="执行人姓名" style={{ flex: 1 }}><Input maxLength={64} /></Form.Item>
          </Space>
          <Form.Item name="note" label="备注"><Input.TextArea rows={2} maxLength={500} showCount /></Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

/** 批量触达面板 */
function SendPanel() {
  const [rows, setRows] = useState<MarketingSendView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<Record<string, unknown>>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [formOpen, setFormOpen] = useState(false);
  const [detail, setDetail] = useState<MarketingSendView | null>(null);
  const [form] = Form.useForm();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageSends({ ...filters, ...page });
      setRows(result.records);
      setTotal(Number(result.total));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      await createSend(values);
      message.success('触达任务已创建');
      setFormOpen(false);
      void loadData();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    }
  };

  const handleExecute = async (row: MarketingSendView) => {
    try {
      const result = await executeSend(row.id);
      message.success(`执行完成：成功 ${result.sentCount ?? 0}，跳过 ${result.skippedCount ?? 0}，失败 ${result.failedCount ?? 0}`);
      void loadData();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const columns: TableProps<MarketingSendView>['columns'] = [
    { title: '批次号', dataIndex: 'sendNo', width: 190 },
    { title: '活动 ID', dataIndex: 'campaignId', width: 100 },
    { title: '渠道', dataIndex: 'channel', width: 100, render: (value?: string) => value ?? '-' },
    { title: '主题', dataIndex: 'subject', width: 220, ellipsis: true, render: (value?: string) => value || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value?: string) => (
        <Tag color={SEND_STATUS_MAP[value ?? '']?.color}>
          {SEND_STATUS_MAP[value ?? '']?.label ?? value ?? '-'}
        </Tag>
      ),
    },
    { title: '总数', dataIndex: 'totalCount', width: 80, render: (value?: number) => value ?? '-' },
    { title: '成功', dataIndex: 'sentCount', width: 80, render: (value?: number) => value ?? '-' },
    { title: '跳过', dataIndex: 'skippedCount', width: 80, render: (value?: number) => value ?? '-' },
    { title: '失败', dataIndex: 'failedCount', width: 80, render: (value?: number) => value ?? '-' },
    { title: '执行时间', dataIndex: 'executedAt', width: 180, render: (value?: string) => formatDateTime(value) },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 160,
      render: (_, row) => (
        <Space size={0}>
          <Button size="small" type="link" onClick={() => setDetail(row)}>明细</Button>
          {row.status !== 'COMPLETED' && hasPermission('sys:crm:marketing:send') && (
            <Button size="small" type="link" onClick={() => void handleExecute(row)}>执行</Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="批量触达"
      extra={
        hasPermission('sys:crm:marketing:send') && (
          <Button type="primary" onClick={() => { form.resetFields(); setFormOpen(true); }}>新建触达任务</Button>
        )
      }
    >
      <Form
        layout="inline"
        style={{ marginBottom: 16, rowGap: 12 }}
        onFinish={(values) => {
          setFilters({
            status: values.status || undefined,
            channel: values.channel || undefined,
            campaignId: values.campaignId ?? null,
          });
          setPage((prev) => ({ ...prev, current: 1 }));
        }}
      >
        <Form.Item name="campaignId"><InputNumber min={1} precision={0} placeholder="活动 ID" style={{ width: 130 }} /></Form.Item>
        <Form.Item name="status">
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 130 }}
            options={Object.entries(SEND_STATUS_MAP).map(([value, item]) => ({ value, label: item.label }))}
          />
        </Form.Item>
        <Form.Item name="channel">
          <Select placeholder="渠道" allowClear style={{ width: 120 }} options={CHANNEL_OPTIONS} />
        </Form.Item>
        <Form.Item><Button type="primary" htmlType="submit">查询</Button></Form.Item>
      </Form>
      <Table<MarketingSendView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1500 }}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
        幂等键为「活动 + 联系人 + 渠道」；已触达联系人在后续批次自动计入跳过数，不会重复发送。
        消息服务不可用时按失败记录，不伪造成功。
      </Typography.Text>

      <Modal
        open={formOpen}
        title="新建触达任务"
        okText="创建"
        cancelText="取消"
        onOk={() => void handleCreate()}
        onCancel={() => setFormOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="campaignId" label="活动 ID" rules={[{ required: true, message: '请输入活动 ID' }]} style={{ flex: 1 }}>
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="audienceId" label="人群快照 ID（可选）" style={{ flex: 1 }}>
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="channel" label="渠道" rules={[{ required: true, message: '请选择渠道' }]} style={{ flex: 1 }}>
              <Select options={CHANNEL_OPTIONS} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="templateCode" label="模板编码" style={{ flex: 1 }}><Input maxLength={64} /></Form.Item>
            <Form.Item name="contentRefDocId" label="内容文档 ID" style={{ flex: 1 }}>
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="subject" label="主题"><Input maxLength={200} showCount /></Form.Item>
        </Form>
      </Modal>

      <Modal
        open={detail !== null}
        title={detail ? `触达明细：${detail.sendNo}` : '触达明细'}
        width={820}
        footer={null}
        onCancel={() => setDetail(null)}
      >
        {detail && (
          <>
            <Descriptions bordered size="small" column={2} style={{ marginBottom: 12 }}>
              <Descriptions.Item label="渠道">{detail.channel || '-'}</Descriptions.Item>
              <Descriptions.Item label="状态">{SEND_STATUS_MAP[detail.status]?.label ?? detail.status}</Descriptions.Item>
              <Descriptions.Item label="总数">{detail.totalCount ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="成功 / 跳过 / 失败">
                {(detail.sentCount ?? 0) + ' / ' + (detail.skippedCount ?? 0) + ' / ' + (detail.failedCount ?? 0)}
              </Descriptions.Item>
              <Descriptions.Item label="错误信息" span={2}>{detail.lastError || '-'}</Descriptions.Item>
            </Descriptions>
            <Table
              size="small"
              rowKey="id"
              dataSource={detail.items ?? []}
              pagination={false}
              columns={[
                { title: '联系人', dataIndex: 'contactName', render: (value?: string) => value || '-' },
                { title: '接收方', dataIndex: 'recipient', render: (value?: string) => value || '-' },
                { title: '状态', dataIndex: 'status', width: 100 },
                { title: '错误', dataIndex: 'error', render: (value?: string) => value || '-' },
              ]}
            />
          </>
        )}
      </Modal>
    </Card>
  );
}

/** 渠道伙伴与报单面板 */
function PartnerPanel() {
  const [rows, setRows] = useState<PartnerView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<Record<string, unknown>>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [formOpen, setFormOpen] = useState(false);
  const [reports, setReports] = useState<PartnerReportView[]>([]);
  const [reportTotal, setReportTotal] = useState(0);
  const [reportPage, setReportPage] = useState({ current: 1, size: 10 });
  const [reportLoading, setReportLoading] = useState(false);
  const [reportFormOpen, setReportFormOpen] = useState(false);
  const [form] = Form.useForm();
  const [reportForm] = Form.useForm();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pagePartners({ ...filters, ...page });
      setRows(result.records);
      setTotal(Number(result.total));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  const loadReports = useCallback(async () => {
    setReportLoading(true);
    try {
      const result = await pagePartnerReports(reportPage);
      setReports(result.records);
      setReportTotal(Number(result.total));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setReportLoading(false);
    }
  }, [reportPage]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    void loadReports();
  }, [loadReports]);

  const handleCreatePartner = async () => {
    try {
      const values = await form.validateFields();
      await createPartner(values);
      message.success('渠道伙伴已创建');
      setFormOpen(false);
      void loadData();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    }
  };

  const handleCreateReport = async () => {
    try {
      const values = await reportForm.validateFields();
      await createPartnerReport(values);
      message.success('报单已提交');
      setReportFormOpen(false);
      void loadReports();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    }
  };

  const resolveReport = async (row: PartnerReportView, status: string) => {
    try {
      await resolvePartnerReport(row.id, { status, approvedAmount: row.estimatedAmount ?? null });
      message.success('报单已裁决');
      void loadReports();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const columns: TableProps<PartnerView>['columns'] = [
    { title: '伙伴编码', dataIndex: 'partnerCode', width: 170 },
    { title: '伙伴名称', dataIndex: 'partnerName', width: 200 },
    { title: '类型', dataIndex: 'partnerType', width: 100, render: (value?: string) => value ?? '-' },
    { title: '等级', dataIndex: 'level', width: 80, render: (value?: string) => value || '-' },
    { title: '区域', dataIndex: 'region', width: 120, render: (value?: string) => value || '-' },
    { title: '联系人', dataIndex: 'contactName', width: 120, render: (value?: string) => value || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value?: string) => <Tag color={value === 'ENABLED' ? 'success' : 'default'}>{value === 'ENABLED' ? '启用' : '停用'}</Tag>,
    },
  ];

  const reportColumns: TableProps<PartnerReportView>['columns'] = [
    { title: '报单号', dataIndex: 'reportNo', width: 180 },
    { title: '伙伴', dataIndex: 'partnerName', width: 160, render: (value?: string, row?: PartnerReportView) => value || row?.partnerId || '-' },
    { title: '客户', dataIndex: 'customerName', width: 160, render: (value?: string, row?: PartnerReportView) => value || row?.customerId || '-' },
    { title: '项目', dataIndex: 'projectName', width: 200, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '预计金额', dataIndex: 'estimatedAmount', width: 120, render: (value?: number) => value ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (value?: string) => (
        <Tag color={REPORT_STATUS_MAP[value ?? '']?.color}>
          {REPORT_STATUS_MAP[value ?? '']?.label ?? value ?? '-'}
        </Tag>
      ),
    },
    { title: '冲突原因', dataIndex: 'conflictReason', ellipsis: true, render: (value?: string) => value || '-' },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, row) => (
        <Space size={0}>
          {row.status === 'CONFLICT' && hasPermission('sys:crm:partner:report') && (
            <>
              <Button size="small" type="link" onClick={() => void resolveReport(row, 'APPROVED')}>通过</Button>
              <Button size="small" type="link" danger onClick={() => void resolveReport(row, 'REJECTED')}>驳回</Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Card
        title="渠道伙伴"
        style={{ marginBottom: 16 }}
        extra={
          hasPermission('sys:crm:partner:manage') && (
            <Button type="primary" onClick={() => { form.resetFields(); setFormOpen(true); }}>新建伙伴</Button>
          )
        }
      >
        <Form
          layout="inline"
          style={{ marginBottom: 16, rowGap: 12 }}
          onFinish={(values) => {
            setFilters({ keyword: values.keyword || undefined, status: values.status || undefined });
            setPage((prev) => ({ ...prev, current: 1 }));
          }}
        >
          <Form.Item name="keyword"><Input placeholder="编码 / 名称" allowClear style={{ width: 200 }} /></Form.Item>
          <Form.Item name="status">
            <Select
              placeholder="状态"
              allowClear
              style={{ width: 120 }}
              options={[{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }]}
            />
          </Form.Item>
          <Form.Item><Button type="primary" htmlType="submit">查询</Button></Form.Item>
        </Form>
        <Table<PartnerView>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={{
            current: page.current,
            pageSize: page.size,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
            onChange: (current, size) => setPage({ current, size }),
          }}
        />
      </Card>

      <Card
        title="渠道报单"
        extra={
          hasPermission('sys:crm:partner:report') && (
            <Button onClick={() => { reportForm.resetFields(); setReportFormOpen(true); }}>新建报单</Button>
          )
        }
      >
        <Table<PartnerReportView>
          rowKey="id"
          loading={reportLoading}
          columns={reportColumns}
          dataSource={reports}
          pagination={{
            current: reportPage.current,
            pageSize: reportPage.size,
            total: reportTotal,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
            onChange: (current, size) => setReportPage({ current, size }),
          }}
        />
        <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
          同一客户同一项目存在有效报单时，新报单进入「冲突待裁决」，不自动覆盖；佣金结算不在 CRM 范围。
        </Typography.Text>
      </Card>

      <Modal
        open={formOpen}
        title="新建渠道伙伴"
        okText="创建"
        cancelText="取消"
        onOk={() => void handleCreatePartner()}
        onCancel={() => setFormOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="partnerCode" label="伙伴编码" rules={[{ required: true, message: '请输入伙伴编码' }]} style={{ flex: 1 }}>
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="partnerName" label="伙伴名称" rules={[{ required: true, message: '请输入伙伴名称' }]} style={{ flex: 1 }}>
              <Input maxLength={200} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="partnerType" label="类型" style={{ flex: 1 }}>
              <Select
                allowClear
                options={[{ value: 'AGENT', label: '代理商' }, { value: 'DEALER', label: '经销商' }, { value: 'OTHER', label: '其他' }]}
              />
            </Form.Item>
            <Form.Item name="level" label="等级" style={{ flex: 1 }}><Input maxLength={16} /></Form.Item>
            <Form.Item name="region" label="区域" style={{ flex: 1 }}><Input maxLength={64} /></Form.Item>
          </Space>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="contactName" label="联系人" style={{ flex: 1 }}><Input maxLength={64} /></Form.Item>
            <Form.Item name="contactPhone" label="联系电话" style={{ flex: 1 }}><Input maxLength={32} /></Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal
        open={reportFormOpen}
        title="新建渠道报单"
        okText="提交"
        cancelText="取消"
        onOk={() => void handleCreateReport()}
        onCancel={() => setReportFormOpen(false)}
        destroyOnClose
      >
        <Form form={reportForm} layout="vertical" preserve={false}>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="partnerId" label="伙伴 ID" rules={[{ required: true, message: '请输入伙伴 ID' }]} style={{ flex: 1 }}>
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="customerId" label="客户 ID" rules={[{ required: true, message: '请输入客户 ID' }]} style={{ flex: 1 }}>
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="estimatedAmount" label="预计金额" style={{ flex: 1 }}>
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="projectName" label="项目名称"><Input maxLength={200} /></Form.Item>
        </Form>
      </Modal>
    </>
  );
}

/** 营销管理页（按菜单 tab 渲染） */
export default function MarketingPage({ tab }: PanelProps) {
  if (tab === 'nurture') {
    return <NurturePanel />;
  }
  if (tab === 'send') {
    return <SendPanel />;
  }
  if (tab === 'partner') {
    return <PartnerPanel />;
  }
  return <CampaignPanel />;
}
