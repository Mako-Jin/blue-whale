// 集成对账：BOM 拉取、MES 下发、OA 审批一致性检查，差异可重新通知/重推/补偿
import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Modal, Radio, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { TableProps } from 'antd';
import {
  compensateOa,
  notifyBom,
  pageReconcile,
  repushRoute,
  runBomReconcile,
  runOaReconcile,
  runRouteReconcile,
  type MatchStatus,
  type ReconcileRow,
  type SyncChannel,
} from '../../api/integration';
import { hasPermission } from '../../utils/permission';

/** 对账结论文案与颜色 */
const MATCH_META: Record<MatchStatus, { text: string; color: string }> = {
  MATCH: { text: '一致', color: 'green' },
  DIFF: { text: '差异', color: 'red' },
  MISSING: { text: '缺失', color: 'orange' },
};

const { Text } = Typography;

export default function ReconcileCenter() {
  const [rows, setRows] = useState<ReconcileRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [channel, setChannel] = useState<SyncChannel | undefined>();
  const [matchStatus, setMatchStatus] = useState<MatchStatus | undefined>();
  const [compensating, setCompensating] = useState<ReconcileRow | undefined>();
  const [oaBizType, setOaBizType] = useState<'REQUEST' | 'ORDER'>('REQUEST');

  const canRun = hasPermission('sys:pdm:int:reconcile:bom');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageReconcile({ current, size, channel, matchStatus });
      setRows(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '对账结果加载失败');
    } finally {
      setLoading(false);
    }
  }, [channel, current, matchStatus, size]);

  useEffect(() => {
    void load();
  }, [load]);

  const runReconcile = async (action: () => Promise<ReconcileRow[]>) => {
    try {
      await action();
      message.success('对账完成');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '对账失败');
    }
  };

  const handleNotify = async (row: ReconcileRow) => {
    if (!row.versionNo) {
      message.warning('缺少版本号，无法通知');
      return;
    }
    try {
      await notifyBom(row.bizNo, row.versionNo);
      message.success('已通知 ERP 重新拉取');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '通知失败');
    }
  };

  const handleRepush = async (row: ReconcileRow) => {
    if (!row.bizId) {
      message.warning('缺少路线ID，无法重推');
      return;
    }
    try {
      await repushRoute(row.bizId);
      message.success('已重推 MES 路线');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重推失败');
    }
  };

  const handleCompensate = async () => {
    if (!compensating?.bizId) {
      message.warning('缺少业务ID，无法补偿');
      return;
    }
    try {
      await compensateOa(oaBizType, compensating.bizId);
      message.success('已按 OA 审批状态补偿');
      setCompensating(undefined);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '补偿失败');
    }
  };

  const columns: TableProps<ReconcileRow>['columns'] = [
    { title: '通道', dataIndex: 'channel', width: 80 },
    { title: '类型', dataIndex: 'bizType', width: 90 },
    { title: '业务编号', dataIndex: 'bizNo', width: 180 },
    { title: '版本', dataIndex: 'versionNo', width: 90, render: (value?: string) => value || '-' },
    { title: 'PDM 状态', dataIndex: 'localStatus', width: 120, render: (value?: string) => value || '-' },
    { title: '外部状态', dataIndex: 'remoteStatus', width: 120, render: (value?: string) => value || '-' },
    {
      title: '结论',
      dataIndex: 'matchStatus',
      width: 90,
      render: (value: MatchStatus) => {
        const meta = MATCH_META[value];
        return <Tag color={meta?.color ?? 'default'}>{meta?.text ?? value}</Tag>;
      },
    },
    {
      title: '差异说明',
      dataIndex: 'diffSummary',
      render: (value?: string) =>
        value ? (
          <Text type="danger" ellipsis={{ tooltip: value }}>
            {value}
          </Text>
        ) : (
          '-'
        ),
    },
    { title: '对账时间', dataIndex: 'checkedAt', width: 170, render: (value?: string) => value || '-' },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_, record) => {
        if (!canRun) {
          return '-';
        }
        if (record.channel === 'ERP') {
          return (
            <Button type="link" size="small" onClick={() => void handleNotify(record)}>
              重新通知
            </Button>
          );
        }
        if (record.channel === 'MES') {
          return (
            <Button type="link" size="small" onClick={() => void handleRepush(record)}>
              重推
            </Button>
          );
        }
        if (record.channel === 'OA' && hasPermission('sys:pdm:int:oa:compensate')) {
          return (
            <Button type="link" size="small" onClick={() => setCompensating(record)}>
              状态补偿
            </Button>
          );
        }
        return '-';
      },
    },
  ];

  return (
    <Card
      title="集成对账"
      extra={
        <Space>
          <Button disabled={!canRun} onClick={() => void runReconcile(runBomReconcile)}>
            对账 BOM
          </Button>
          <Button disabled={!canRun} onClick={() => void runReconcile(runRouteReconcile)}>
            对账 MES
          </Button>
          <Button disabled={!canRun} onClick={() => void runReconcile(runOaReconcile)}>
            对账 OA
          </Button>
        </Space>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="通道"
          style={{ width: 120 }}
          options={[
            { label: 'ERP', value: 'ERP' },
            { label: 'MES', value: 'MES' },
            { label: 'OA', value: 'OA' },
          ]}
          value={channel}
          onChange={(value) => {
            setChannel(value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="对账结论"
          style={{ width: 120 }}
          options={[
            { label: '一致', value: 'MATCH' },
            { label: '差异', value: 'DIFF' },
            { label: '缺失', value: 'MISSING' },
          ]}
          value={matchStatus}
          onChange={(value) => {
            setMatchStatus(value);
            setCurrent(1);
          }}
        />
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<ReconcileRow>
        rowKey="id"
        size="small"
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
      <Modal
        title="OA 审批状态补偿"
        open={Boolean(compensating)}
        onCancel={() => setCompensating(undefined)}
        onOk={() => void handleCompensate()}
        okText="补偿"
      >
        <Space direction="vertical">
          <Text>
            按已保存的 OA 审批状态回写业务状态，仅支持变更申请与变更单：{compensating?.bizNo}
          </Text>
          <Radio.Group value={oaBizType} onChange={(event) => setOaBizType(event.target.value)}>
            <Radio value="REQUEST">变更申请</Radio>
            <Radio value="ORDER">变更单</Radio>
          </Radio.Group>
        </Space>
      </Modal>
    </Card>
  );
}
