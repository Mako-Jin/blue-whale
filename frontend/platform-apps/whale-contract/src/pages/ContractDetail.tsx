// 合同详情：基本信息、相对方、版本历史、审批流程记录与状态流转操作

import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Descriptions,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate, useParams } from 'react-router-dom';
import {
  contractApi,
  type Contract,
  type ContractFlow,
  type ContractParty,
  type ContractVersion,
} from '../api/contract';
import {
  categoryLabel,
  formatDate,
  formatTime,
  hasPermission,
  partyTypeLabel,
  statusColor,
  statusLabel,
} from '../utils';

/** 可提交审批的状态 */
const SUBMITTABLE = ['DRAFT', 'REJECTED', 'WITHDRAWN'];

/** 可发起修订的状态 */
const REVISABLE = ['DRAFT', 'REJECTED', 'WITHDRAWN'];

export default function ContractDetail() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [contract, setContract] = useState<Contract | null>(null);
  const [loading, setLoading] = useState(false);
  const [versionForm] = Form.useForm<{ changeNote?: string }>();
  const [versionModalOpen, setVersionModalOpen] = useState(false);

  const load = useCallback(async () => {
    if (!id) {
      return;
    }
    setLoading(true);
    try {
      const detail = await contractApi.detail(id);
      setContract(detail);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [id, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const runAction = async (action: 'submit' | 'withdraw' | 'terminate' | 'archive') => {
    if (!id) {
      return;
    }
    try {
      await contractApi[action](id);
      message.success('操作成功');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const handleCreateVersion = async () => {
    if (!id) {
      return;
    }
    const values = await versionForm.validateFields();
    try {
      await contractApi.createVersion(id, { changeNote: values.changeNote });
      message.success('已生成新版本，合同回到草稿');
      setVersionModalOpen(false);
      versionForm.resetFields();
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const partyColumns: ColumnsType<ContractParty> = [
    {
      title: '类型',
      dataIndex: 'partyType',
      width: 90,
      render: (value: string) => partyTypeLabel(value),
    },
    { title: '名称', dataIndex: 'name' },
    { title: '信用代码', dataIndex: 'creditCode', width: 200, render: (value) => value || '-' },
    { title: '联系人', dataIndex: 'contact', width: 110, render: (value) => value || '-' },
    { title: '联系电话', dataIndex: 'phone', width: 140, render: (value) => value || '-' },
    { title: '签署人', dataIndex: 'signerName', width: 110, render: (value) => value || '-' },
  ];

  const versionColumns: ColumnsType<ContractVersion> = [
    {
      title: '版本',
      dataIndex: 'versionNo',
      width: 80,
      render: (value: number) => `v${value}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value: string) => (
        <Tag color={value === 'EFFECTIVE' ? 'green' : value === 'SUPERSEDED' ? 'default' : 'blue'}>
          {value === 'EFFECTIVE' ? '生效' : value === 'SUPERSEDED' ? '被替代' : '草稿'}
        </Tag>
      ),
    },
    { title: '变更说明', dataIndex: 'changeNote', render: (value) => value || '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 160, render: (value) => formatTime(value) },
  ];

  const flowColumns: ColumnsType<ContractFlow> = [
    { title: '阶段', dataIndex: 'stage', width: 100, render: (value: string) => (value === 'SIGN' ? '签署' : '审批') },
    { title: '流程实例ID', dataIndex: 'processInstanceId', width: 200, render: (value) => value || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={value === 'APPROVED' ? 'green' : value === 'REJECTED' ? 'red' : value === 'RUNNING' ? 'processing' : 'default'}>
          {value === 'RUNNING' ? '运行中' : value === 'APPROVED' ? '已通过' : value === 'REJECTED' ? '已驳回' : '已撤销'}
        </Tag>
      ),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 160, render: (value) => formatTime(value) },
    { title: '更新时间', dataIndex: 'updateTime', width: 160, render: (value) => formatTime(value) },
  ];

  if (!contract) {
    return (
      <div style={{ padding: 16 }}>
        <Button onClick={() => navigate('/')}>返回列表</Button>
        <div style={{ marginTop: 16 }}>{loading ? '加载中…' : '合同不存在'}</div>
      </div>
    );
  }

  return (
    <div style={{ padding: 16 }}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Space wrap>
          <Button onClick={() => navigate('/')}>返回列表</Button>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {contract.code} · {contract.title}
          </Typography.Title>
          <Tag color={statusColor(contract.status)}>{statusLabel(contract.status)}</Tag>
          <Button size="small" onClick={() => void load()}>
            刷新
          </Button>
          {SUBMITTABLE.includes(contract.status) && hasPermission('sys:contract:submit') ? (
            <Button type="primary" size="small" onClick={() => void runAction('submit')}>
              提交审批
            </Button>
          ) : null}
          {contract.status === 'APPROVING' && hasPermission('sys:contract:withdraw') ? (
            <Popconfirm title="撤回该合同的审批？" onConfirm={() => void runAction('withdraw')}>
              <Button size="small">撤回</Button>
            </Popconfirm>
          ) : null}
          {['APPROVED', 'SIGNING', 'SIGNED', 'EFFECTIVE'].includes(contract.status)
            && hasPermission('sys:contract:terminate') ? (
            <Popconfirm title="终止该合同？" onConfirm={() => void runAction('terminate')}>
              <Button size="small" danger>
                终止
              </Button>
            </Popconfirm>
          ) : null}
          {contract.status === 'EFFECTIVE' && hasPermission('sys:contract:archive') ? (
            <Popconfirm title="归档该合同？" onConfirm={() => void runAction('archive')}>
              <Button size="small">归档</Button>
            </Popconfirm>
          ) : null}
          {REVISABLE.includes(contract.status) && hasPermission('sys:contract:edit') ? (
            <Button size="small" onClick={() => setVersionModalOpen(true)}>
              发起修订
            </Button>
          ) : null}
        </Space>

        {contract.status === 'APPROVING' ? (
          <Alert
            type="info"
            showIcon
            message="审批中：请到待办中心处理审批任务，返回本页点击「刷新」可同步审批结果。"
          />
        ) : null}

        <Descriptions bordered size="small" column={3}>
          <Descriptions.Item label="合同编号">{contract.code}</Descriptions.Item>
          <Descriptions.Item label="合同类别">{categoryLabel(contract.category)}</Descriptions.Item>
          <Descriptions.Item label="当前版本">v{contract.currentVersion}</Descriptions.Item>
          <Descriptions.Item label="合同金额">
            {contract.amount == null ? '-' : `${contract.currency ?? 'CNY'} ${contract.amount.toLocaleString()}`}
          </Descriptions.Item>
          <Descriptions.Item label="生效日期">{formatDate(contract.effectiveDate)}</Descriptions.Item>
          <Descriptions.Item label="到期日期">
            {formatDate(contract.expireDate)}
            {contract.daysToExpire != null && contract.daysToExpire >= 0 && contract.daysToExpire <= 30 ? (
              <Tag color="orange" style={{ marginLeft: 8 }}>
                剩余 {contract.daysToExpire} 天
              </Tag>
            ) : null}
          </Descriptions.Item>
          <Descriptions.Item label="负责人ID">{contract.ownerId || '-'}</Descriptions.Item>
          <Descriptions.Item label="部门ID">{contract.deptId || '-'}</Descriptions.Item>
          <Descriptions.Item label="归档条目ID">{contract.archiveItemId || '-'}</Descriptions.Item>
          <Descriptions.Item label="关联业务" span={2}>
            {contract.bizType ? `${contract.bizType} / ${contract.bizId ?? '-'}` : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">{formatTime(contract.createTime)}</Descriptions.Item>
          <Descriptions.Item label="备注" span={3}>
            {contract.remark || '-'}
          </Descriptions.Item>
        </Descriptions>

        <Tabs
          items={[
            {
              key: 'parties',
              label: `相对方（${contract.parties?.length ?? 0}）`,
              children: (
                <Table
                  rowKey={(record) => record.id ?? `${record.partyType}-${record.name}`}
                  size="small"
                  columns={partyColumns}
                  dataSource={contract.parties ?? []}
                  pagination={false}
                />
              ),
            },
            {
              key: 'versions',
              label: `版本（${contract.versions?.length ?? 0}）`,
              children: (
                <Table
                  rowKey="id"
                  size="small"
                  columns={versionColumns}
                  dataSource={contract.versions ?? []}
                  pagination={false}
                />
              ),
            },
            {
              key: 'flows',
              label: `流程记录（${contract.flows?.length ?? 0}）`,
              children: (
                <Table
                  rowKey="id"
                  size="small"
                  columns={flowColumns}
                  dataSource={contract.flows ?? []}
                  pagination={false}
                />
              ),
            },
          ]}
        />
      </Space>

      <Modal
        title="发起修订（版本号 +1，合同回到草稿）"
        open={versionModalOpen}
        onCancel={() => setVersionModalOpen(false)}
        onOk={() => void handleCreateVersion()}
        destroyOnClose
      >
        <Form form={versionForm} layout="vertical">
          <Form.Item name="changeNote" label="变更说明">
            <Input.TextArea rows={3} maxLength={255} placeholder="本次修订的原因与内容" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
