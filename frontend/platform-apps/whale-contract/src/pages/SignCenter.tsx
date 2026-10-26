// 签署中心：当前用户待签署任务、本地盖章签署与拒签

import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { sealApi, signApi, type Seal, type SignExecuteInput, type SignTask } from '../api/esign';
import {
  categoryLabel,
  formatTime,
  hasPermission,
  signTaskStatusColor,
  signTaskStatusLabel,
} from '../utils';

/** 签署表单值 */
interface SignFormValues {
  sealId: number;
  page?: number;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  rotation?: number;
  crossPage?: boolean;
}

export default function SignCenter() {
  const { message, modal } = App.useApp();
  const [form] = Form.useForm<SignFormValues>();
  const [refuseForm] = Form.useForm<{ reason: string }>();
  const [tasks, setTasks] = useState<SignTask[]>([]);
  const [seals, setSeals] = useState<Seal[]>([]);
  const [loading, setLoading] = useState(false);
  const [signTarget, setSignTarget] = useState<SignTask | null>(null);
  const [signOpen, setSignOpen] = useState(false);
  const [refuseTarget, setRefuseTarget] = useState<SignTask | null>(null);
  const [refuseOpen, setRefuseOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setTasks(await signApi.pending());
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void load();
  }, [load]);

  const loadSeals = async () => {
    try {
      const result = await sealApi.page({ current: 1, size: 200, status: 'ENABLED' });
      setSeals(result.records);
    } catch (error) {
      message.error((error as Error).message);
      setSeals([]);
    }
  };

  const openSign = (record: SignTask) => {
    setSignTarget(record);
    form.resetFields();
    form.setFieldsValue({ page: 1, x: 20, y: 20, width: 120, height: 120, rotation: 0, crossPage: false });
    setSignOpen(true);
    void loadSeals();
  };

  const handleSign = async () => {
    if (!signTarget) {
      return;
    }
    const values = await form.validateFields();
    const payload: SignExecuteInput = {
      sealId: values.sealId,
      page: values.page,
      x: values.x,
      y: values.y,
      width: values.width,
      height: values.height,
      rotation: values.rotation,
      crossPage: values.crossPage,
    };
    try {
      const result = await signApi.sign(signTarget.id, payload);
      setSignOpen(false);
      setSignTarget(null);
      modal.success({
        title: '签署完成',
        content: (
          <Descriptions size="small" column={1} bordered>
            <Descriptions.Item label="证据编号">{result.evidenceNo}</Descriptions.Item>
            <Descriptions.Item label="盖章页数">{result.stampedPages ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="签名算法">{result.algorithm}</Descriptions.Item>
            <Descriptions.Item label="签章件SHA-256">
              <Typography.Text copyable style={{ wordBreak: 'break-all' }}>
                {result.signedSha256}
              </Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="合同状态">{result.contractStatus}</Descriptions.Item>
          </Descriptions>
        ),
      });
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const openRefuse = (record: SignTask) => {
    setRefuseTarget(record);
    refuseForm.resetFields();
    setRefuseOpen(true);
  };

  const handleRefuse = async () => {
    if (!refuseTarget) {
      return;
    }
    const values = await refuseForm.validateFields();
    try {
      await signApi.refuse(refuseTarget.id, values.reason);
      message.success('已拒签，合同保持签署中');
      setRefuseOpen(false);
      setRefuseTarget(null);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const columns: ColumnsType<SignTask> = [
    { title: '合同编号', dataIndex: 'contractCode', width: 180, render: (value) => value || '-' },
    { title: '合同标题', dataIndex: 'contractTitle', ellipsis: true, render: (value) => value || '-' },
    {
      title: '类别',
      dataIndex: 'contractCategory',
      width: 110,
      render: (value: string | null) => categoryLabel(value ?? undefined),
    },
    {
      title: '金额',
      dataIndex: 'contractAmount',
      width: 130,
      align: 'right',
      render: (value: number | null) => (value == null ? '-' : value.toLocaleString()),
    },
    { title: '签署方', dataIndex: 'signerParty', width: 90, render: (value) => value || '-' },
    { title: '签署人', dataIndex: 'signerName', width: 110, render: (value) => value || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => <Tag color={signTaskStatusColor(value)}>{signTaskStatusLabel(value)}</Tag>,
    },
    { title: '任务创建时间', dataIndex: 'createTime', width: 160, render: (value) => formatTime(value) },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (_, record) => (
        <Space size={0}>
          {hasPermission('sys:contract:sign:execute') ? (
            <>
              <Button type="link" size="small" onClick={() => openSign(record)}>
                签署
              </Button>
              <Button type="link" size="small" danger onClick={() => openRefuse(record)}>
                拒签
              </Button>
            </>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 16 }}>
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Space wrap>
          <Typography.Title level={4} style={{ margin: 0 }}>
            签署中心
          </Typography.Title>
          <Button size="small" onClick={() => void load()}>
            刷新
          </Button>
        </Space>
        <Alert
          type="info"
          showIcon
          message="仅展示当前登录人待签署（PENDING/SIGNING）的任务；用章需在印章管理中提前授权，未授权将返回 403 并留审计。"
        />
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={tasks}
          pagination={{ pageSize: 10, showTotal: (count) => `共 ${count} 条` }}
        />
      </Space>

      <Modal
        title={`本地签署${signTarget ? ` · ${signTarget.contractCode ?? ''}` : ''}`}
        open={signOpen}
        width={640}
        onCancel={() => setSignOpen(false)}
        onOk={() => void handleSign()}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="sealId" label="使用印章" rules={[{ required: true, message: '请选择印章' }]}>
            <Select
              placeholder="仅展示启用印章"
              options={seals.map((seal) => ({ value: Number(seal.id), label: seal.name }))}
            />
          </Form.Item>
          <Form.Item name="crossPage" label="骑缝章（按页均分加盖，忽略页码与坐标）" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Space size={12} style={{ width: '100%' }} wrap>
            <Form.Item name="page" label="页码（从 1 起）">
              <InputNumber min={1} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="x" label="X 坐标（点）">
              <InputNumber style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="y" label="Y 坐标（点）">
              <InputNumber style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="rotation" label="旋转角度">
              <InputNumber style={{ width: 120 }} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }}>
            <Form.Item name="width" label="印章宽度（点）">
              <InputNumber min={1} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="height" label="印章高度（点）">
              <InputNumber min={1} style={{ width: 140 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal
        title={`拒签${refuseTarget ? ` · ${refuseTarget.contractCode ?? ''}` : ''}`}
        open={refuseOpen}
        onCancel={() => setRefuseOpen(false)}
        onOk={() => void handleRefuse()}
        destroyOnClose
      >
        <Form form={refuseForm} layout="vertical">
          <Form.Item name="reason" label="拒签原因" rules={[{ required: true, message: '请填写拒签原因' }]}>
            <Input.TextArea rows={3} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
