// 证据中心：按合同查看签署证据链与验签结果

import { useState } from 'react';
import {
  Alert,
  App,
  Button,
  Descriptions,
  Input,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { signApi, type Evidence, type SignRecord, type SignTask, type VerifyResult } from '../api/esign';
import { formatTime, signTaskStatusColor, signTaskStatusLabel } from '../utils';

export default function EvidenceCenter() {
  const { message } = App.useApp();
  const [contractId, setContractId] = useState('');
  const [evidence, setEvidence] = useState<Evidence | null>(null);
  const [verify, setVerify] = useState<VerifyResult | null>(null);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    if (!contractId.trim()) {
      message.warning('请输入合同ID');
      return;
    }
    setLoading(true);
    try {
      setEvidence(await signApi.evidence(contractId.trim()));
      setVerify(null);
    } catch (error) {
      message.error((error as Error).message);
      setEvidence(null);
    } finally {
      setLoading(false);
    }
  };

  const handleVerify = async () => {
    if (!contractId.trim()) {
      return;
    }
    try {
      setVerify(await signApi.verify(contractId.trim()));
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const recordColumns: ColumnsType<SignRecord> = [
    { title: '证据编号', dataIndex: 'evidenceNo', width: 200, render: (value) => value || '-' },
    { title: '签署人ID', dataIndex: 'signerUserId', width: 110, render: (value) => value || '-' },
    { title: '算法', dataIndex: 'algorithm', width: 140, render: (value) => value || '-' },
    { title: '签署时间', dataIndex: 'signedAt', width: 170, render: (value) => formatTime(value) },
    {
      title: '链式哈希',
      dataIndex: 'chainHash',
      render: (value: string | null) => (
        <Typography.Text copyable style={{ wordBreak: 'break-all' }}>{value || '-'}</Typography.Text>
      ),
    },
  ];

  const taskColumns: ColumnsType<SignTask> = [
    { title: '序号', dataIndex: 'seqNo', width: 80, render: (value) => value ?? '-' },
    { title: '签署方', dataIndex: 'signerParty', width: 90, render: (value) => value || '-' },
    { title: '签署人', dataIndex: 'signerName', width: 120, render: (value) => value || '-' },
    { title: '渠道', dataIndex: 'provider', width: 100, render: (value) => value || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => <Tag color={signTaskStatusColor(value)}>{signTaskStatusLabel(value)}</Tag>,
    },
    { title: '签署时间', dataIndex: 'signedAt', width: 170, render: (value) => formatTime(value) },
  ];

  return (
    <div style={{ padding: 16 }}>
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Space wrap>
          <Typography.Title level={4} style={{ margin: 0 }}>
            证据中心
          </Typography.Title>
          <Input
            placeholder="合同ID"
            value={contractId}
            onChange={(event) => setContractId(event.target.value)}
            style={{ width: 280 }}
          />
          <Button type="primary" loading={loading} onClick={() => void load()}>
            查询证据链
          </Button>
          <Button onClick={() => void handleVerify()} disabled={!evidence}>
            验签
          </Button>
        </Space>
        <Alert
          type="info"
          showIcon
          message="证据链按签署顺序链式哈希；验签会校验签章件哈希、链连续性与 CMS 签名结构。"
        />
        {verify ? (
          <Alert
            type={verify.hashMatch && verify.chainValid && verify.signatureValid ? 'success' : 'error'}
            showIcon
            message={`验签结果：哈希一致 ${verify.hashMatch ? '是' : '否'} / 链连续 ${verify.chainValid ? '是' : '否'} / 签名有效 ${verify.signatureValid ? '是' : '否'}`}
            description={verify.details?.join('；')}
          />
        ) : null}
        {evidence ? (
          <Descriptions size="small" bordered column={3}>
            <Descriptions.Item label="合同ID">{evidence.contractId}</Descriptions.Item>
            <Descriptions.Item label="合同编号">{evidence.contractCode || '-'}</Descriptions.Item>
            <Descriptions.Item label="合同状态">{evidence.contractStatus || '-'}</Descriptions.Item>
            <Descriptions.Item label="链连续性">
              <Tag color={evidence.chainValid ? 'success' : 'error'}>{evidence.chainValid ? '连续' : '中断'}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="签署记录数">{evidence.records.length}</Descriptions.Item>
            <Descriptions.Item label="签署任务数">{evidence.tasks.length}</Descriptions.Item>
          </Descriptions>
        ) : null}
        <Typography.Title level={5} style={{ margin: 0 }}>
          签署证据
        </Typography.Title>
        <Table rowKey="id" columns={recordColumns} dataSource={evidence?.records ?? []} pagination={false} />
        <Typography.Title level={5} style={{ margin: 0 }}>
          签署任务
        </Typography.Title>
        <Table rowKey="id" columns={taskColumns} dataSource={evidence?.tasks ?? []} pagination={false} />
      </Space>
    </div>
  );
}
