// 工艺路线：路线列表 + 工序/工装/SOP 维护 + 发布与 MES 下发状态
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
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
  Table,
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  addProcessStep,
  addProcessTooling,
  createProcessRoute,
  deleteProcessRoute,
  deleteProcessSop,
  deleteProcessStep,
  deleteProcessTooling,
  getProcessRoute,
  listProcessRoutes,
  releaseProcessRoute,
  retryProcessPublish,
  saveProcessSop,
  updateProcessStep,
  withdrawProcessRoute,
  type ProcessPublishStatus,
  type ProcessRoute,
  type ProcessStatus,
  type ProcessStep,
  type ProcessStepInput,
  type ProcessToolingInput,
  type ToolingType,
} from '../../api/processRoute';
import { listParts, type PdmPart } from '../../api/part';
import {
  listVersions,
  pageDocuments,
  type PdmDocument,
  type PdmDocumentVersion,
} from '../../api/document';
import { hasPermission } from '../../utils/permission';

/** 路线状态文案与颜色 */
const STATUS_META: Record<ProcessStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  RELEASED: { text: '已发布', color: 'green' },
  OBSOLETE: { text: '已作废', color: 'red' },
};

/** 下发状态文案与颜色 */
const PUBLISH_META: Record<ProcessPublishStatus, { text: string; color: string }> = {
  SUCCESS: { text: '下发成功', color: 'green' },
  FAILED: { text: '下发失败', color: 'red' },
  WITHDRAWN: { text: '已撤回', color: 'orange' },
};

/** 工装类型文案 */
const TOOLING_LABEL: Record<ToolingType, string> = {
  TOOL: '刀具',
  FIXTURE: '夹具',
  MOLD: '模具',
  GAUGE: '量具',
};

interface Filters {
  routeNo?: string;
  partCode?: string;
  status?: ProcessStatus;
}

/**
 * 汇总一条路线下所有工装（含路线级）。
 *
 * @param route 路线详情
 * @returns 工装列表
 */
function collectToolings(route: ProcessRoute | null) {
  if (!route) {
    return [];
  }
  return route.steps.flatMap((step) => step.toolings ?? []);
}

export default function ProcessRouteCenter() {
  const [filters, setFilters] = useState<Filters>({});
  const [rows, setRows] = useState<ProcessRoute[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  const [parts, setParts] = useState<PdmPart[]>([]);
  const [documents, setDocuments] = useState<PdmDocument[]>([]);
  const [docVersions, setDocVersions] = useState<PdmDocumentVersion[]>([]);

  const [createOpen, setCreateOpen] = useState(false);
  const [createForm] = Form.useForm<{ partId: string; routeNo?: string; remark?: string }>();

  const [detail, setDetail] = useState<ProcessRoute | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const [stepModalOpen, setStepModalOpen] = useState(false);
  const [editingStep, setEditingStep] = useState<ProcessStep | null>(null);
  const [stepForm] = Form.useForm<ProcessStepInput>();

  const [toolingModalOpen, setToolingModalOpen] = useState(false);
  const [toolingForm] = Form.useForm<ProcessToolingInput>();

  const [sopModalOpen, setSopModalOpen] = useState(false);
  const [sopForm] = Form.useForm<{ stepId: string; documentId: string; documentVersionNo: string }>();

  const canAdd = hasPermission('sys:pdm:process:add');
  const canEdit = hasPermission('sys:pdm:process:edit');
  const canDelete = hasPermission('sys:pdm:process:delete');
  const canRelease = hasPermission('sys:pdm:process:release');
  const canPublish = hasPermission('sys:pdm:process:publish');
  const canWithdraw = hasPermission('sys:pdm:process:withdraw');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await listProcessRoutes({ ...filters, current, size });
      setRows(page.records);
      setTotal(Number(page.total));
    } finally {
      setLoading(false);
    }
  }, [filters, current, size]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  useEffect(() => {
    void listParts({ current: 1, size: 200, status: 'ENABLED' }).then((page) =>
      setParts(page.records),
    );
    void pageDocuments({ current: 1, size: 200, status: 'RELEASED' }).then((page) =>
      setDocuments(page.records),
    );
  }, []);

  const refreshDetail = useCallback(async (id: string) => {
    const route = await getProcessRoute(id);
    setDetail(route);
    return route;
  }, []);

  const openDetail = async (id: string) => {
    await refreshDetail(id);
    setDetailOpen(true);
  };

  const submitCreate = async () => {
    const values = await createForm.validateFields();
    await createProcessRoute({ ...values, partId: String(values.partId) });
    message.success('工艺路线已创建');
    setCreateOpen(false);
    setRefreshKey((key) => key + 1);
  };

  const submitStep = async () => {
    if (!detail) {
      return;
    }
    const values = await stepForm.validateFields();
    const route = editingStep
      ? await updateProcessStep(detail.id, editingStep.id, values)
      : await addProcessStep(detail.id, values);
    setDetail(route);
    setStepModalOpen(false);
    message.success('工序已保存');
  };

  const submitTooling = async () => {
    if (!detail) {
      return;
    }
    const values = await toolingForm.validateFields();
    const route = await addProcessTooling(detail.id, {
      ...values,
      stepId: values.stepId ? String(values.stepId) : undefined,
    });
    setDetail(route);
    setToolingModalOpen(false);
    message.success('工装已保存');
  };

  const submitSop = async () => {
    if (!detail) {
      return;
    }
    const values = await sopForm.validateFields();
    const route = await saveProcessSop(detail.id, {
      stepId: String(values.stepId),
      documentId: String(values.documentId),
      documentVersionNo: values.documentVersionNo,
    });
    setDetail(route);
    setSopModalOpen(false);
    message.success('SOP 已保存');
  };

  const submitRelease = async () => {
    if (!detail) {
      return;
    }
    const route = await releaseProcessRoute(detail.id);
    setDetail(route);
    setRefreshKey((key) => key + 1);
    if (route.publish?.status === 'FAILED') {
      message.warning('路线已发布，但 MES 下发失败，可重试');
    } else {
      message.success('路线已发布并下发 MES');
    }
  };

  const submitRetry = async () => {
    if (!detail) {
      return;
    }
    const route = await retryProcessPublish(detail.id);
    setDetail(route);
    setRefreshKey((key) => key + 1);
    message.success('已重试下发');
  };

  const submitWithdraw = async () => {
    if (!detail) {
      return;
    }
    const route = await withdrawProcessRoute(detail.id);
    setDetail(route);
    setRefreshKey((key) => key + 1);
    message.success('路线已撤回');
  };

  const toolingList = collectToolings(detail);

  const columns: TableProps<ProcessRoute>['columns'] = [
    { title: '路线编号', dataIndex: 'routeNo', width: 170 },
    {
      title: '零件',
      render: (_, record) => (
        <span>
          {record.partCode}
          {record.partName ? ` / ${record.partName}` : ''}
        </span>
      ),
    },
    { title: '版本', dataIndex: 'versionNo', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: ProcessStatus) => (
        <Tag color={STATUS_META[status]?.color}>{STATUS_META[status]?.text ?? status}</Tag>
      ),
    },
    {
      title: 'MES 下发',
      width: 130,
      render: (_, record) =>
        record.publish ? (
          <Tag color={PUBLISH_META[record.publish.status]?.color}>
            {PUBLISH_META[record.publish.status]?.text ?? record.publish.status}
          </Tag>
        ) : (
          <Tag>未下发</Tag>
        ),
    },
    { title: '更新时间', dataIndex: 'updateTime', width: 170 },
    {
      title: '操作',
      width: 140,
      render: (_, record) => (
        <Space>
          <Button type="link" onClick={() => void openDetail(record.id)}>
            查看/维护
          </Button>
          {canDelete && record.status === 'DRAFT' ? (
            <Popconfirm
              title="确认删除该草稿路线？"
              onConfirm={async () => {
                await deleteProcessRoute(record.id);
                message.success('删除成功');
                setRefreshKey((key) => key + 1);
              }}
            >
              <Button type="link" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const stepColumns: TableProps<ProcessStep>['columns'] = [
    { title: '序号', dataIndex: 'stepNo', width: 70 },
    { title: '工序', dataIndex: 'name' },
    { title: '工作中心', dataIndex: 'workCenter', width: 120 },
    { title: '标准工时', dataIndex: 'standardHours', width: 100 },
    { title: '准备工时', dataIndex: 'setupHours', width: 100 },
    {
      title: 'SOP',
      width: 150,
      render: (_, step) =>
        step.sop ? `${step.sop.documentTitle ?? `文档 ${step.sop.documentId}`} ${step.sop.documentVersionNo}` : '-',
    },
    {
      title: '操作',
      width: 200,
      render: (_, step) =>
        canEdit && detail?.status === 'DRAFT' ? (
          <Space size={0}>
            <Button
              type="link"
              onClick={() => {
                setEditingStep(step);
                stepForm.setFieldsValue({
                  stepNo: step.stepNo,
                  name: step.name,
                  workCenter: step.workCenter,
                  standardHours: step.standardHours,
                  setupHours: step.setupHours,
                  description: step.description,
                });
                setStepModalOpen(true);
              }}
            >
              编辑
            </Button>
            <Button
              type="link"
              onClick={() => {
                sopForm.setFieldsValue({ stepId: step.id, documentId: undefined, documentVersionNo: undefined });
                setDocVersions([]);
                setSopModalOpen(true);
              }}
            >
              SOP
            </Button>
            {step.sop ? (
              <Button
                type="link"
                danger
                onClick={async () => {
                  if (!detail) {
                    return;
                  }
                  const route = await deleteProcessSop(detail.id, step.sop!.id);
                  setDetail(route);
                }}
              >
                移除 SOP
              </Button>
            ) : null}
            <Popconfirm
              title="确认删除该工序？其工装与 SOP 一并删除。"
              onConfirm={async () => {
                if (!detail) {
                  return;
                }
                const route = await deleteProcessStep(detail.id, step.id);
                setDetail(route);
              }}
            >
              <Button type="link" danger>
                删除
              </Button>
            </Popconfirm>
          </Space>
        ) : null,
    },
  ];

  const toolingColumns: TableProps<(typeof toolingList)[number]>['columns'] = [
    { title: '类型', dataIndex: 'toolingType', width: 90, render: (type: ToolingType) => TOOLING_LABEL[type] ?? type },
    { title: '工装编码', dataIndex: 'toolingCode' },
    { title: '规格', dataIndex: 'spec' },
    { title: '数量', dataIndex: 'quantity', width: 80 },
    { title: '工序', width: 110, render: (_, tooling) => detail?.steps.find((step) => step.id === tooling.stepId)?.name ?? '路线级' },
    {
      title: '操作',
      width: 90,
      render: (_, tooling) =>
        canEdit && detail?.status === 'DRAFT' ? (
          <Button
            type="link"
            danger
            onClick={async () => {
              if (!detail) {
                return;
              }
              const route = await deleteProcessTooling(detail.id, tooling.id);
              setDetail(route);
            }}
          >
            删除
          </Button>
        ) : null,
    },
  ];

  const statusOptions = useMemo(
    () => Object.entries(STATUS_META).map(([value, meta]) => ({ value, label: meta.text })),
    [],
  );

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        size="small"
        title="工艺路线"
        extra={
          canAdd ? (
            <Button
              type="primary"
              onClick={() => {
                createForm.resetFields();
                setCreateOpen(true);
              }}
            >
              新建路线
            </Button>
          ) : null
        }
      >
        <Space wrap style={{ marginBottom: 12 }}>
          <Input
            allowClear
            placeholder="路线编号"
            style={{ width: 170 }}
            onChange={(event) => setFilters((prev) => ({ ...prev, routeNo: event.target.value }))}
          />
          <Input
            allowClear
            placeholder="零件编码"
            style={{ width: 160 }}
            onChange={(event) => setFilters((prev) => ({ ...prev, partCode: event.target.value }))}
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 140 }}
            options={statusOptions}
            onChange={(value) => setFilters((prev) => ({ ...prev, status: value }))}
          />
          <Button
            type="primary"
            onClick={() => {
              setCurrent(1);
              setRefreshKey((key) => key + 1);
            }}
          >
            查询
          </Button>
        </Space>
        <Table
          size="small"
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

      <Modal title="新建工艺路线" open={createOpen} onOk={() => void submitCreate()} onCancel={() => setCreateOpen(false)}>
        <Form form={createForm} layout="vertical">
          <Form.Item name="partId" label="零件/产品" rules={[{ required: true, message: '请选择零件' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={parts.map((part) => ({ value: part.id, label: `${part.code} / ${part.name}` }))}
            />
          </Form.Item>
          <Form.Item name="routeNo" label="路线编号（留空按零件编码生成）">
            <Input placeholder="例如 RT-P-001" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={detail ? `${detail.routeNo} ${detail.versionNo}` : '工艺路线'}
        width={960}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        extra={
          detail ? (
            <Space>
              {canRelease && detail.status === 'DRAFT' ? (
                <Popconfirm title="发布后不可修改，并立即下发 MES。确认？" onConfirm={() => void submitRelease()}>
                  <Button type="primary">发布</Button>
                </Popconfirm>
              ) : null}
              {canPublish && detail.publish?.status === 'FAILED' ? (
                <Button onClick={() => void submitRetry()}>重试下发</Button>
              ) : null}
              {canWithdraw && detail.status === 'RELEASED' ? (
                <Popconfirm title="撤回将作废路线并通知 MES 撤回。确认？" onConfirm={() => void submitWithdraw()}>
                  <Button danger>撤回</Button>
                </Popconfirm>
              ) : null}
            </Space>
          ) : null
        }
      >
        {detail ? (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions size="small" column={3} bordered>
              <Descriptions.Item label="零件">{`${detail.partCode} ${detail.partName ?? ''}`}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={STATUS_META[detail.status]?.color}>{STATUS_META[detail.status]?.text}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="备注">{detail.remark ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="发布人">{detail.releasedBy ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="发布时间">{detail.releasedAt ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="下发状态">
                {detail.publish ? (
                  <Tag color={PUBLISH_META[detail.publish.status]?.color}>
                    {PUBLISH_META[detail.publish.status]?.text}
                  </Tag>
                ) : (
                  '未下发'
                )}
              </Descriptions.Item>
            </Descriptions>
            {detail.publish?.lastError ? (
              <Alert type="error" showIcon message={`MES 下发失败：${detail.publish.lastError}`} />
            ) : null}

            <Card
              size="small"
              title="工序"
              extra={
                canEdit && detail.status === 'DRAFT' ? (
                  <Button
                    size="small"
                    onClick={() => {
                      setEditingStep(null);
                      stepForm.resetFields();
                      setStepModalOpen(true);
                    }}
                  >
                    新增工序
                  </Button>
                ) : null
              }
            >
              <Table size="small" rowKey="id" columns={stepColumns} dataSource={detail.steps} pagination={false} />
            </Card>

            <Card
              size="small"
              title="工装夹具"
              extra={
                canEdit && detail.status === 'DRAFT' ? (
                  <Button
                    size="small"
                    onClick={() => {
                      toolingForm.resetFields();
                      setToolingModalOpen(true);
                    }}
                  >
                    新增工装
                  </Button>
                ) : null
              }
            >
              <Table
                size="small"
                rowKey="id"
                columns={toolingColumns}
                dataSource={toolingList}
                pagination={false}
              />
            </Card>
          </Space>
        ) : null}
      </Drawer>

      <Modal
        title={editingStep ? '编辑工序' : '新增工序'}
        open={stepModalOpen}
        onOk={() => void submitStep()}
        onCancel={() => setStepModalOpen(false)}
      >
        <Form form={stepForm} layout="vertical">
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="stepNo" label="工序序号">
                <InputNumber min={1} style={{ width: '100%' }} placeholder="留空自动递增" />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="name" label="工序名称" rules={[{ required: true, message: '请输入工序名称' }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="workCenter" label="工作中心">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="standardHours" label="标准工时（小时）">
                <InputNumber min={0} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="setupHours" label="准备工时（小时）">
                <InputNumber min={0} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="工序说明">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新增工装"
        open={toolingModalOpen}
        onOk={() => void submitTooling()}
        onCancel={() => setToolingModalOpen(false)}
      >
        <Form form={toolingForm} layout="vertical">
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="toolingType" label="类型" initialValue="TOOL" rules={[{ required: true }]}>
                <Select
                  options={Object.entries(TOOLING_LABEL).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="quantity" label="数量" initialValue={1}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="toolingCode" label="工装编码" rules={[{ required: true, message: '请输入工装编码' }]}>
            <Input />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="spec" label="规格">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="stepId" label="适用工序（留空为路线级）">
                <Select
                  allowClear
                  options={(detail?.steps ?? []).map((step) => ({
                    value: step.id,
                    label: `${step.stepNo} ${step.name}`,
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="remark" label="备注">
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="工序 SOP（仅已发布文档版本）"
        open={sopModalOpen}
        onOk={() => void submitSop()}
        onCancel={() => setSopModalOpen(false)}
      >
        <Form form={sopForm} layout="vertical">
          <Form.Item name="stepId" label="工序" rules={[{ required: true, message: '请选择工序' }]}>
            <Select
              options={(detail?.steps ?? []).map((step) => ({
                value: step.id,
                label: `${step.stepNo} ${step.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="documentId" label="SOP 文档" rules={[{ required: true, message: '请选择文档' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={documents.map((doc) => ({ value: String(doc.id), label: `${doc.docNo} ${doc.title}` }))}
              onChange={(value) => {
                sopForm.setFieldsValue({ documentVersionNo: undefined });
                setDocVersions([]);
                void listVersions(Number(value)).then((versions) =>
                  setDocVersions(versions.filter((version) => version.status === 'RELEASED')),
                );
              }}
            />
          </Form.Item>
          <Form.Item
            name="documentVersionNo"
            label="文档版本"
            rules={[{ required: true, message: '请选择已发布版本' }]}
          >
            <Select
              options={docVersions.map((version) => ({ value: version.versionNo, label: version.versionNo }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
