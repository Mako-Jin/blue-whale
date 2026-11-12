// 通知与提醒（M-P4）：通知留痕 + 手动扫描。
//
// 留痕只回标题与投递元数据，不回正文（正文可能含姓名、金额）。
// 扫描接口返回「本次实际发出条数」：同一批数据重复扫描应为 0，
// 页面显式展示这个 0，作为幂等重发的可观测证据。

import { Alert, Button, Card, Input, Select, Space, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import {
  pageNotifyLogs,
  scanPartyNotices,
  scanUnionNotices,
  type NoticeScanResultView,
  type NotifyLogView,
} from '../api/party';
import { hasPermission } from '../api/client';

/** 每页条数 */
const PAGE_SIZE = 10;

/** 通知场景选项 */
const SCENE_OPTIONS = [
  { value: 'PARTY_PROBATION', label: '预备党员转正' },
  { value: 'PARTY_FEE', label: '党费催缴' },
  { value: 'PARTY_ACTIVITY', label: '组织生活通知' },
  { value: 'PARTY_BRANCH_TERM', label: '支部换届提醒' },
  { value: 'UNION_ACTIVITY', label: '工会活动通知' },
  { value: 'UNION_PROPOSAL_FEEDBACK', label: '提案反馈' },
];

/** 投递状态选项 */
const STATUS_OPTIONS = [
  { value: 'SENT', label: '已发送' },
  { value: 'FAILED', label: '失败' },
];

/** 通知列 */
const COLUMNS: ColumnsType<NotifyLogView> = [
  { title: '标题', dataIndex: 'title', key: 'title', width: 220 },
  { title: '场景', dataIndex: 'sceneLabel', key: 'sceneLabel', width: 140 },
  { title: '接收人', dataIndex: 'userId', key: 'userId', width: 90 },
  {
    title: '状态',
    dataIndex: 'sendStatus',
    key: 'sendStatus',
    width: 90,
    render: (status: string, row) => (
      <Tag color={status === 'SENT' ? 'green' : 'red'}>{row.sendStatusLabel}</Tag>
    ),
  },
  { title: '幂等键', dataIndex: 'idempotencyKey', key: 'idempotencyKey', width: 240 },
  {
    title: '投递时间',
    dataIndex: 'sentAt',
    key: 'sentAt',
    width: 170,
    render: (value?: string | null) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—'),
  },
  {
    title: '失败原因',
    dataIndex: 'failReason',
    key: 'failReason',
    render: (value?: string | null) => value ?? '—',
  },
];

/** 通知与提醒页 */
export default function NoticePage() {
  const [rows, setRows] = useState<NotifyLogView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [scene, setScene] = useState<string | undefined>();
  const [sendStatus, setSendStatus] = useState<string | undefined>();
  const [idempotencyKey, setIdempotencyKey] = useState('');
  const [loading, setLoading] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [scanResult, setScanResult] = useState<NoticeScanResultView | null>(null);

  const canManage = hasPermission('sys:party:metric:manage');
  const canManageUnion = hasPermission('sys:union:metric:manage');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageNotifyLogs({
        scene,
        sendStatus,
        idempotencyKey: idempotencyKey || undefined,
        current,
        size: PAGE_SIZE,
      });
      setRows(page.records);
      setTotal(Number(page.total));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '通知留痕加载失败');
    } finally {
      setLoading(false);
    }
  }, [current, idempotencyKey, scene, sendStatus]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleScanParty = async () => {
    setScanning(true);
    try {
      const result = await scanPartyNotices();
      setScanResult(result);
      message.success(`本次发出 ${result.totalSent} 条党务提醒`);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '扫描失败');
    } finally {
      setScanning(false);
    }
  };

  const handleScanUnion = async () => {
    setScanning(true);
    try {
      const result = await scanUnionNotices();
      setScanResult(result);
      message.success(`本次发出 ${result.totalSent} 条工会通知`);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '扫描失败');
    } finally {
      setScanning(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card size="small">
        <Space wrap>
          <Select
            allowClear
            placeholder="通知场景"
            style={{ width: 180 }}
            options={SCENE_OPTIONS}
            value={scene}
            onChange={(value) => {
              setCurrent(1);
              setScene(value);
            }}
          />
          <Select
            allowClear
            placeholder="投递状态"
            style={{ width: 140 }}
            options={STATUS_OPTIONS}
            value={sendStatus}
            onChange={(value) => {
              setCurrent(1);
              setSendStatus(value);
            }}
          />
          <Input.Search
            allowClear
            placeholder="幂等键"
            style={{ width: 240 }}
            value={idempotencyKey}
            onChange={(event) => setIdempotencyKey(event.target.value)}
            onSearch={() => {
              setCurrent(1);
              void load();
            }}
          />
          {canManage ? (
            <Button loading={scanning} onClick={() => void handleScanParty()}>
              扫描党务提醒
            </Button>
          ) : null}
          {canManageUnion ? (
            <Button loading={scanning} onClick={() => void handleScanUnion()}>
              扫描工会通知
            </Button>
          ) : null}
        </Space>
      </Card>

      {scanResult ? (
        <Alert
          type="info"
          showIcon
          message={`本次扫描共发出 ${scanResult.totalSent} 条`}
          description={
            Object.keys(scanResult.sentByScene).length === 0
              ? '所有场景均已投递过（幂等键命中），未重复发送。'
              : Object.entries(scanResult.sentByScene)
                  .map(([key, count]) => `${key}：${count} 条`)
                  .join('，')
          }
        />
      ) : null}

      <Card title="通知留痕" size="small">
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          columns={COLUMNS}
          dataSource={rows}
          pagination={{
            current,
            pageSize: PAGE_SIZE,
            total,
            showSizeChanger: false,
            onChange: setCurrent,
          }}
        />
      </Card>
    </Space>
  );
}
