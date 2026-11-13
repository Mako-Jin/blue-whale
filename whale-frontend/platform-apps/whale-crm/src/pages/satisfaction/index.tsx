// 满意度问卷：工单关闭后生成一次性令牌 / 追踪答卷 / 低分回访标记
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { hasPermission } from '../../utils/permission';
import {
  getSurvey,
  getSurveyStats,
  pageSurveys,
  revokeSurvey,
  sendSurvey,
  submitSurveyResponse,
  type SatisfactionSurveyView,
  type SurveyPageQuery,
  type SurveyResponseInput,
  type SurveyStatsView,
} from '../../api/service';
import {
  SURVEY_STATUS_COLOR,
  SURVEY_STATUS_LABEL,
  SURVEY_STATUS_OPTIONS,
  formatDateTime,
} from '../../utils/dict';

/** 筛选条件 */
interface SurveyFilterValues {
  ticketId?: number | null;
  status?: string;
}

/** 生成问卷表单值 */
interface SendFormValues {
  ticketId: number;
}

/** 答卷表单值 */
interface ResponseFormValues {
  token: string;
  score: number;
  speedScore?: number | null;
  attitudeScore?: number | null;
  professionalScore?: number | null;
  comment?: string;
}

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 可展示的错误信息
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

interface SurveyDetailDrawerProps {
  surveyId: number | null;
  onClose: () => void;
}

/** 问卷详情抽屉 */
function SurveyDetailDrawer({ surveyId, onClose }: SurveyDetailDrawerProps) {
  const [detail, setDetail] = useState<SatisfactionSurveyView | null>(null);

  useEffect(() => {
    if (!surveyId) {
      setDetail(null);
      return;
    }
    let active = true;
    getSurvey(surveyId)
      .then((data) => {
        if (active) {
          setDetail(data);
        }
      })
      .catch((error: unknown) => {
        message.error(errorText(error));
      });
    return () => {
      active = false;
    };
  }, [surveyId]);

  return (
    <Drawer open={Boolean(surveyId)} title="满意度问卷详情" width={640} onClose={onClose}>
      {detail && (
        <>
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="问卷编号" span={2}>
              {detail.surveyNo}
            </Descriptions.Item>
            <Descriptions.Item label="工单 ID">{detail.ticketId}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={SURVEY_STATUS_COLOR[detail.status]}>
                {SURVEY_STATUS_LABEL[detail.status] ?? detail.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="发送时间">{formatDateTime(detail.sentAt)}</Descriptions.Item>
            <Descriptions.Item label="有效期至">
              {formatDateTime(detail.expiresAt)}
            </Descriptions.Item>
            <Descriptions.Item label="答卷时间">{formatDateTime(detail.usedAt)}</Descriptions.Item>
          </Descriptions>
          {detail.response ? (
            <>
              <Typography.Text strong style={{ display: 'block', margin: '16px 0 8px' }}>
                答卷
              </Typography.Text>
              <Descriptions bordered size="small" column={2}>
                <Descriptions.Item label="总分">{detail.response.score ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="低分">
                  {detail.response.lowScore ? (
                    <Tag color="red">低分</Tag>
                  ) : (
                    <Tag color="green">正常</Tag>
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="响应速度">
                  {detail.response.speedScore ?? '-'}
                </Descriptions.Item>
                <Descriptions.Item label="服务态度">
                  {detail.response.attitudeScore ?? '-'}
                </Descriptions.Item>
                <Descriptions.Item label="专业程度">
                  {detail.response.professionalScore ?? '-'}
                </Descriptions.Item>
                <Descriptions.Item label="需回访">
                  {detail.response.followUpRequired ? <Tag color="orange">需回访</Tag> : '否'}
                </Descriptions.Item>
                <Descriptions.Item label="客户评论" span={2}>
                  {detail.response.comment || '-'}
                </Descriptions.Item>
              </Descriptions>
            </>
          ) : (
            <Typography.Text type="secondary" style={{ display: 'block', marginTop: 16 }}>
              尚未答卷。
            </Typography.Text>
          )}
        </>
      )}
    </Drawer>
  );
}

/** 满意度问卷页 */
export default function SatisfactionPage() {
  const [filterForm] = Form.useForm<SurveyFilterValues>();
  const [sendForm] = Form.useForm<SendFormValues>();
  const [responseForm] = Form.useForm<ResponseFormValues>();
  const [rows, setRows] = useState<SatisfactionSurveyView[]>([]);
  const [stats, setStats] = useState<SurveyStatsView>({});
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<SurveyPageQuery>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [sendOpen, setSendOpen] = useState(false);
  const [responseOpen, setResponseOpen] = useState(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [issued, setIssued] = useState<SatisfactionSurveyView | null>(null);
  const [saving, setSaving] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [result, statsView] = await Promise.all([
        pageSurveys({ ...filters, current: page.current, size: page.size }),
        getSurveyStats(),
      ]);
      setRows(result.records);
      setTotal(Number(result.total));
      setStats(statsView);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSearch = (values: SurveyFilterValues) => {
    setFilters({
      ticketId: values.ticketId ?? null,
      status: values.status || undefined,
    });
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    filterForm.resetFields();
    setFilters({});
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleSend = async () => {
    setSaving(true);
    try {
      const values = await sendForm.validateFields();
      const created = await sendSurvey({ ticketId: values.ticketId });
      setSendOpen(false);
      setIssued(created);
      message.success(`问卷已生成：${created.surveyNo}`);
      void loadData();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    } finally {
      setSaving(false);
    }
  };

  const handleResend = (row: SatisfactionSurveyView) => {
    sendForm.setFieldsValue({ ticketId: row.ticketId });
    setSendOpen(true);
  };

  const handleSubmitResponse = async () => {
    setSaving(true);
    try {
      const values = await responseForm.validateFields();
      const payload: SurveyResponseInput = {
        token: values.token,
        score: values.score,
        speedScore: values.speedScore ?? null,
        attitudeScore: values.attitudeScore ?? null,
        professionalScore: values.professionalScore ?? null,
        comment: values.comment || undefined,
      };
      await submitSurveyResponse(payload);
      setResponseOpen(false);
      responseForm.resetFields();
      message.success('答卷已提交');
      void loadData();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    } finally {
      setSaving(false);
    }
  };

  const handleRevoke = async (row: SatisfactionSurveyView) => {
    try {
      await revokeSurvey(row.id);
      message.success('问卷已作废');
      void loadData();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const columns: TableProps<SatisfactionSurveyView>['columns'] = [
    { title: '问卷编号', dataIndex: 'surveyNo', width: 210, fixed: 'left' },
    { title: '工单 ID', dataIndex: 'ticketId', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value: string) => (
        <Tag color={SURVEY_STATUS_COLOR[value]}>{SURVEY_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '总分',
      dataIndex: ['response', 'score'],
      width: 90,
      render: (_: unknown, row) => row.response?.score ?? '-',
    },
    {
      title: '低分 / 回访',
      key: 'lowScore',
      width: 150,
      render: (_, row) => (
        <Space size={4}>
          {row.response?.lowScore ? <Tag color="red">低分</Tag> : null}
          {row.response?.followUpRequired ? <Tag color="orange">需回访</Tag> : null}
          {!row.response?.lowScore && !row.response?.followUpRequired ? '-' : null}
        </Space>
      ),
    },
    {
      title: '客户评论',
      dataIndex: ['response', 'comment'],
      width: 240,
      ellipsis: true,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '发送时间',
      dataIndex: 'sentAt',
      width: 180,
      render: (value?: string | null) => formatDateTime(value),
    },
    {
      title: '有效期至',
      dataIndex: 'expiresAt',
      width: 180,
      render: (value?: string | null) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 280,
      render: (_, row) => (
        <Space size={0} wrap>
          <Button size="small" type="link" onClick={() => setDetailId(row.id)}>
            详情
          </Button>
          {hasPermission('sys:crm:survey:manage') && (
            <Button size="small" type="link" onClick={() => handleResend(row)}>
              重发
            </Button>
          )}
          {hasPermission('sys:crm:survey:manage') && row.status === 'SENT' && (
            <Button
              size="small"
              type="link"
              onClick={() => {
                responseForm.resetFields();
                setResponseOpen(true);
              }}
            >
              录入答卷
            </Button>
          )}
          {hasPermission('sys:crm:survey:manage') && row.status === 'SENT' && (
            <Popconfirm
              title="作废问卷"
              description="作废后令牌立即失效"
              onConfirm={() => void handleRevoke(row)}
            >
              <Button size="small" type="link" danger>
                作废
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="满意度问卷"
      extra={
        hasPermission('sys:crm:survey:manage') && (
          <Button type="primary" onClick={() => setSendOpen(true)}>
            按工单生成问卷
          </Button>
        )
      }
    >
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={5}>
          <Statistic title="已发送" value={stats.sentCount ?? 0} />
        </Col>
        <Col span={5}>
          <Statistic title="已答卷" value={stats.usedCount ?? 0} />
        </Col>
        <Col span={5}>
          <Statistic title="平均分" value={stats.averageScore ?? 0} precision={2} />
        </Col>
        <Col span={5}>
          <Statistic title="低分数" value={stats.lowScoreCount ?? 0} />
        </Col>
        <Col span={4}>
          <Statistic title="需回访" value={stats.followUpCount ?? 0} />
        </Col>
      </Row>

      <Form
        form={filterForm}
        layout="inline"
        onFinish={handleSearch}
        style={{ marginBottom: 16, rowGap: 12 }}
      >
        <Form.Item name="ticketId">
          <InputNumber min={1} precision={0} placeholder="工单 ID" style={{ width: 140 }} />
        </Form.Item>
        <Form.Item name="status">
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 130 }}
            options={SURVEY_STATUS_OPTIONS}
          />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              查询
            </Button>
            <Button onClick={handleReset}>重置</Button>
          </Space>
        </Form.Item>
      </Form>

      <Table<SatisfactionSurveyView>
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
        CLOSED 工单关闭后自动生成待答卷；令牌一次性、默认 7 天有效；低分（总分 ≤3 或任一维度
        ≤2）自动追加工单回访动作。
      </Typography.Text>

      <SurveyDetailDrawer surveyId={detailId} onClose={() => setDetailId(null)} />

      <Modal
        open={sendOpen}
        title="按工单生成问卷"
        confirmLoading={saving}
        okText="生成"
        cancelText="取消"
        onOk={() => void handleSend()}
        onCancel={() => setSendOpen(false)}
        destroyOnClose
      >
        <Form form={sendForm} layout="vertical" preserve={false}>
          <Form.Item
            name="ticketId"
            label="工单 ID"
            rules={[{ required: true, message: '请输入工单 ID' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Typography.Text type="secondary">
            仅已关闭（CLOSED）工单允许生成；重发会作废原待答卷令牌。
          </Typography.Text>
        </Form>
      </Modal>

      <Modal
        open={issued !== null}
        title="问卷令牌"
        okText="关闭"
        cancelButtonProps={{ style: { display: 'none' } }}
        onOk={() => setIssued(null)}
        onCancel={() => setIssued(null)}
      >
        {issued && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Typography.Text>问卷编号：{issued.surveyNo}</Typography.Text>
            <Input readOnly value={issued.token ?? ''} />
            <Typography.Text type="secondary">
              请将令牌随满意度邀请发送给客户；提交一次后立即失效。
            </Typography.Text>
          </Space>
        )}
      </Modal>

      <Modal
        open={responseOpen}
        title="录入满意度答卷"
        confirmLoading={saving}
        okText="提交"
        cancelText="取消"
        onOk={() => void handleSubmitResponse()}
        onCancel={() => setResponseOpen(false)}
        destroyOnClose
      >
        <Form form={responseForm} layout="vertical" preserve={false}>
          <Form.Item
            name="token"
            label="一次性令牌"
            rules={[{ required: true, message: '请输入令牌' }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item
              name="score"
              label="总分（1–5）"
              rules={[{ required: true, message: '请输入总分' }]}
              style={{ flex: 1 }}
            >
              <InputNumber min={1} max={5} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="speedScore" label="响应速度" style={{ flex: 1 }}>
              <InputNumber min={1} max={5} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="attitudeScore" label="服务态度" style={{ flex: 1 }}>
              <InputNumber min={1} max={5} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="professionalScore" label="专业程度" style={{ flex: 1 }}>
              <InputNumber min={1} max={5} precision={0} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="comment" label="客户评论">
            <Input.TextArea rows={3} maxLength={2000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
