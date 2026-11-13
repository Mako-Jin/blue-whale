// MES 综合看板：生产 / 质量 / 设备 / 人员四域聚合，支持 SSE 实时推送与 CSV 导出
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Row,
  Select,
  Space,
  Statistic,
  Switch,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  exportMesDashboard,
  fetchMesDashboard,
  listMesMasterDataOptions,
  subscribeMesDashboard,
  type MesDashboard,
  type MesMasterData,
} from '../api/mes';
import { hasPermission } from '../utils/permission';

/** 格式化数值（空值显示 -） */
function formatValue(value?: number | string | null): string {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  return String(value);
}

/** 格式化净工时秒数为小时 */
function formatHours(seconds?: number | null): string {
  if (seconds === undefined || seconds === null) {
    return '-';
  }
  return (seconds / 3600).toFixed(2);
}

/** 综合看板页 */
export default function DashboardPage() {
  const [dashboard, setDashboard] = useState<MesDashboard | null>(null);
  const [loading, setLoading] = useState(false);
  const [live, setLive] = useState(false);
  const [lineOptions, setLineOptions] = useState<MesMasterData[]>([]);
  const [teamOptions, setTeamOptions] = useState<MesMasterData[]>([]);
  const [lineId, setLineId] = useState<string | undefined>(undefined);
  const [teamId, setTeamId] = useState<string | undefined>(undefined);
  const canExport = hasPermission('sys:mes:dashboard:export');
  const abortRef = useRef<AbortController | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setDashboard(await fetchMesDashboard({ lineId, teamId }));
    } finally {
      setLoading(false);
    }
  }, [lineId, teamId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void (async () => {
      const [lines, teams] = await Promise.all([
        listMesMasterDataOptions('line'),
        listMesMasterDataOptions('team'),
      ]);
      setLineOptions(lines);
      setTeamOptions(teams);
    })();
  }, []);

  // SSE 实时推送：开启时建立长连接，事件驱动刷新；关闭或筛选变化时断开重建
  useEffect(() => {
    if (!live) {
      return undefined;
    }
    const controller = new AbortController();
    void subscribeMesDashboard(
      { lineId, teamId, intervalSeconds: 10 },
      (snapshot) => {
        setDashboard(snapshot);
        setLoading(false);
      },
      controller.signal,
    ).catch((error: unknown) => {
      // 主动断开（关闭开关/筛选变化/StrictMode 双执行的首次清理）不算失败
      if (controller.signal.aborted) {
        return;
      }
      console.error('[mes-dashboard] SSE 订阅失败', error);
      const text = error instanceof Error ? error.message : String(error);
      message.warning(`实时推送已断开：${text}`);
      setLive(false);
    });
    abortRef.current = controller;
    return () => {
      controller.abort();
      abortRef.current = null;
    };
  }, [live, lineId, teamId]);

  const handleExport = useCallback(async () => {
    try {
      await exportMesDashboard({ lineId, teamId });
      message.success('导出已开始');
    } catch (error: unknown) {
      const text = error instanceof Error ? error.message : String(error);
      message.error(`导出失败：${text}`);
    }
  }, [lineId, teamId]);

  const equipmentStatusEntries = Object.entries(dashboard?.equipment.statusCounts ?? {});

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Typography.Title level={5} style={{ margin: 0 }}>
          综合看板 {dashboard?.boardDate ? `（${String(dashboard.boardDate).slice(0, 10)}）` : ''}
        </Typography.Title>
        <Select
          allowClear
          placeholder="产线"
          style={{ width: 200 }}
          showSearch
          optionFilterProp="label"
          value={lineId}
          onChange={(value) => setLineId(value)}
          options={lineOptions.map((item) => ({
            value: item.id,
            label: `${item.code} ${item.name}`.trim(),
          }))}
        />
        <Select
          allowClear
          placeholder="班组"
          style={{ width: 200 }}
          showSearch
          optionFilterProp="label"
          value={teamId}
          onChange={(value) => setTeamId(value)}
          options={teamOptions.map((item) => ({
            value: item.id,
            label: `${item.code} ${item.name}`.trim(),
          }))}
        />
        <Space size={4}>
          <span>实时推送</span>
          <Switch checked={live} onChange={setLive} checkedChildren="开" unCheckedChildren="关" />
        </Space>
        <Button onClick={() => void load()} loading={loading}>
          刷新
        </Button>
        {canExport && <Button onClick={() => void handleExport()}>导出 CSV</Button>}
      </Space>

      {live && (
        <Alert
          type="success"
          showIcon
          message={`SSE 实时推送已连接，每 10 秒自动刷新${dashboard?.generatedAt ? `（最近快照 ${String(dashboard.generatedAt).slice(11, 19)}）` : ''}`}
        />
      )}

      <Card loading={loading} title="生产域">
        <Row gutter={16}>
          <Col xs={12} md={6}>
            <Statistic title="今日目标" value={formatValue(dashboard?.production.targetQuantity)} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic
              title="今日完工"
              value={formatValue(dashboard?.production.completedQuantity)}
            />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="今日不良" value={formatValue(dashboard?.production.defectQuantity)} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic
              title="达成率"
              value={dashboard?.production.achievementRate ?? 0}
              suffix="%"
              precision={2}
            />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="未关闭异常" value={dashboard?.production.exceptionCount ?? 0} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="执行中任务" value={dashboard?.production.activeTaskCount ?? 0} />
          </Col>
        </Row>
      </Card>

      <Card loading={loading} title="质量域">
        <Row gutter={16}>
          <Col xs={12} md={6}>
            <Statistic title="检验任务" value={dashboard?.quality.inspectionTaskCount ?? 0} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="待检任务" value={dashboard?.quality.pendingTaskCount ?? 0} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="不合格任务" value={dashboard?.quality.failedTaskCount ?? 0} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic
              title="首件通过率"
              value={dashboard?.quality.firstArticlePassRate ?? 0}
              suffix="%"
              precision={2}
            />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="未关闭 NCR" value={dashboard?.quality.ncrOpenCount ?? 0} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="未完成返工" value={dashboard?.quality.reworkOpenCount ?? 0} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="SPC 告警样本" value={dashboard?.quality.spcAlertCount ?? 0} />
          </Col>
        </Row>
      </Card>

      <Card loading={loading} title="设备域">
        <Row gutter={16}>
          <Col xs={12} md={6}>
            <Statistic title="设备总数" value={dashboard?.equipment.totalEquipmentCount ?? 0} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="启用设备" value={dashboard?.equipment.enabledEquipmentCount ?? 0} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="故障设备" value={dashboard?.equipment.faultEquipmentCount ?? 0} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="当日停机单" value={dashboard?.equipment.todayDowntimeCount ?? 0} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic
              title="平均 OEE"
              value={
                dashboard?.equipment.avgOee !== undefined
                  ? Number(dashboard.equipment.avgOee) * 100
                  : 0
              }
              suffix="%"
              precision={2}
            />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="OEE 快照" value={dashboard?.equipment.oeeSnapshotCount ?? 0} />
          </Col>
        </Row>
        {equipmentStatusEntries.length > 0 && (
          <Space wrap style={{ marginTop: 16 }}>
            {equipmentStatusEntries.map(([status, count]) => (
              <Tag key={status}>{`${status}: ${count}`}</Tag>
            ))}
          </Space>
        )}
      </Card>

      <Card loading={loading} title="人员域">
        <Row gutter={16}>
          <Col xs={12} md={6}>
            <Statistic title="当日报工次数" value={dashboard?.workforce.todayReportCount ?? 0} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="有效工时记录" value={dashboard?.workforce.todayWorkHourCount ?? 0} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic
              title="当日净工时"
              value={formatHours(dashboard?.workforce.todayNetWorkSeconds)}
              suffix="h"
            />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="当日有效排班" value={dashboard?.workforce.activeShiftCount ?? 0} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="未确认交接单" value={dashboard?.workforce.draftHandoverCount ?? 0} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic
              title="当月锁定进度"
              value={`${dashboard?.workforce.monthlyLockedCount ?? 0}/${dashboard?.workforce.monthlyStatCount ?? 0}`}
            />
          </Col>
        </Row>
      </Card>
    </Space>
  );
}
