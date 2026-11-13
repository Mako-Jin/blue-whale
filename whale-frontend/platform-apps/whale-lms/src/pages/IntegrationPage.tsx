// 集成设置页（M5）：员工只读快照 / 通知投递日志 / 未完成必修催办与请假冲突。
//
// 权限码与 migration-5.87-lms-m5a.sql 对齐：
//   sys:lms:member:manage（快照同步与查询）、sys:lms:notify:list（通知日志）、
//   sys:lms:urge:manage（催办执行与预览）。
// 口径：快照由 ERP 单向同步，LMS 不回写；离职员工保留记录但不再催办；
// 催办按「培训班 + 日期」幂等，同一天重复执行不会重复发。

import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Input,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import type { Dayjs } from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { hasPermission } from '../api/client';
import {
  leaveConflicts,
  pageMembers,
  pageNotifyLogs,
  previewUrge,
  runUrge,
  syncMembers,
  type MemberSnapshotItem,
  type MemberSyncResult,
  type NotifyLogItem,
  type ScheduleConflictItem,
  type UrgeItem,
  type UrgeRunResult,
} from '../api/integration';

/** 在职状态展示 */
const MEMBER_STATUS: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '在职' },
  LEFT: { color: 'default', label: '离职' },
};

/** 通知场景展示 */
const SCENE_LABEL: Record<string, string> = {
  ENROLL: '报名成功',
  CLASS_START: '开班提醒',
  URGE: '学习催办',
  CERT_ISSUE: '证书发放',
};

/** 投递状态展示 */
const SEND_STATUS: Record<string, { color: string; label: string }> = {
  SENT: { color: 'green', label: '已投递' },
  FAILED: { color: 'red', label: '失败待重试' },
};

/** 员工快照面板 */
function MemberPanel() {
  const [rows, setRows] = useState<MemberSnapshotItem[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [lastSync, setLastSync] = useState<MemberSyncResult | null>(null);

  const canManage = hasPermission('sys:lms:member:manage');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMembers({ keyword, status, current, size: 20 });
      setRows(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载员工快照失败');
    } finally {
      setLoading(false);
    }
  }, [keyword, status, current]);

  useEffect(() => {
    void load();
  }, [load]);

  const onSync = async () => {
    setSyncing(true);
    try {
      const result = await syncMembers();
      setLastSync(result);
      message.success(
        `同步完成：新增 ${result.created}，更新 ${result.updated}，跳过 ${result.skipped}`,
      );
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '同步失败');
    } finally {
      setSyncing(false);
    }
  };

  return (
    <Space direction="vertical" size="middle" style={{ display: 'flex' }}>
      <Alert
        type="info"
        showIcon
        message="员工快照由 ERP 单向同步，LMS 不回写"
        description="离职员工保留记录以便学习与证书可追溯，但不再纳入催办；未收录的账号同样不催办，请先执行一次同步。"
      />
      <Space wrap>
        <Button type="primary" loading={syncing} disabled={!canManage} onClick={onSync}>
          同步员工目录
        </Button>
        <Input
          allowClear
          placeholder="员工编号 / 姓名"
          style={{ width: 200 }}
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="在职状态"
          style={{ width: 140 }}
          value={status}
          onChange={(value) => {
            setStatus(value);
            setCurrent(1);
          }}
          options={Object.entries(MEMBER_STATUS).map(([value, item]) => ({
            value,
            label: item.label,
          }))}
        />
      </Space>
      {lastSync && (
        <Descriptions size="small" bordered column={5}>
          <Descriptions.Item label="源头总数">{lastSync.sourceTotal}</Descriptions.Item>
          <Descriptions.Item label="新增">{lastSync.created}</Descriptions.Item>
          <Descriptions.Item label="更新">{lastSync.updated}</Descriptions.Item>
          <Descriptions.Item label="跳过">{lastSync.skipped}</Descriptions.Item>
          <Descriptions.Item label="快照总数">{lastSync.snapshotTotal}</Descriptions.Item>
        </Descriptions>
      )}
      <Table<MemberSnapshotItem>
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={rows}
        pagination={{
          current,
          pageSize: 20,
          total,
          showSizeChanger: false,
          onChange: (page) => setCurrent(page),
        }}
        columns={[
          { title: '员工编号', dataIndex: 'employeeNo', width: 140 },
          { title: '姓名', dataIndex: 'name', width: 120 },
          { title: '组织', dataIndex: 'orgCode', width: 140 },
          { title: '岗位', dataIndex: 'position', width: 140 },
          {
            title: '在职状态',
            dataIndex: 'status',
            width: 100,
            render: (value: string) => (
              <Tag color={MEMBER_STATUS[value]?.color ?? 'default'}>
                {MEMBER_STATUS[value]?.label ?? value}
              </Tag>
            ),
          },
          {
            title: '可催办',
            dataIndex: 'urgeable',
            width: 100,
            render: (value: boolean) => (value ? <Tag color="blue">是</Tag> : <Tag>否</Tag>),
          },
          { title: '最近同步', dataIndex: 'lastSyncedAt', width: 180 },
        ]}
      />
    </Space>
  );
}

/** 通知日志面板 */
function NotifyPanel() {
  const [rows, setRows] = useState<NotifyLogItem[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [scene, setScene] = useState<string | undefined>();
  const [sendStatus, setSendStatus] = useState<string | undefined>();
  const [keyPrefix, setKeyPrefix] = useState('');
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageNotifyLogs({
        scene,
        sendStatus,
        idempotencyKey: keyPrefix.trim() || undefined,
        current,
        size: 20,
      });
      setRows(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载通知日志失败');
    } finally {
      setLoading(false);
    }
  }, [scene, sendStatus, keyPrefix, current]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Space direction="vertical" size="middle" style={{ display: 'flex' }}>
      <Alert
        type="info"
        showIcon
        message="投递留痕按幂等键去重"
        description="已投递的接收人不会重复发送；标记为失败的记录会在下一轮催办或重发时自动重试。"
      />
      <Space wrap>
        <Select
          allowClear
          placeholder="通知场景"
          style={{ width: 160 }}
          value={scene}
          onChange={(value) => {
            setScene(value);
            setCurrent(1);
          }}
          options={Object.entries(SCENE_LABEL).map(([value, label]) => ({ value, label }))}
        />
        <Select
          allowClear
          placeholder="投递状态"
          style={{ width: 160 }}
          value={sendStatus}
          onChange={(value) => {
            setSendStatus(value);
            setCurrent(1);
          }}
          options={Object.entries(SEND_STATUS).map(([value, item]) => ({
            value,
            label: item.label,
          }))}
        />
        <Input
          allowClear
          placeholder="幂等键（精确匹配）"
          style={{ width: 260 }}
          value={keyPrefix}
          onChange={(event) => {
            setKeyPrefix(event.target.value);
            setCurrent(1);
          }}
        />
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<NotifyLogItem>
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={rows}
        pagination={{
          current,
          pageSize: 20,
          total,
          showSizeChanger: false,
          onChange: (page) => setCurrent(page),
        }}
        columns={[
          {
            title: '场景',
            dataIndex: 'scene',
            width: 120,
            render: (value: string) => SCENE_LABEL[value] ?? value,
          },
          { title: '幂等键', dataIndex: 'idempotencyKey', width: 240 },
          { title: '接收人', dataIndex: 'userId', width: 100 },
          { title: '标题', dataIndex: 'title' },
          {
            title: '状态',
            dataIndex: 'sendStatus',
            width: 120,
            render: (value: string) => (
              <Tag color={SEND_STATUS[value]?.color ?? 'default'}>
                {SEND_STATUS[value]?.label ?? value}
              </Tag>
            ),
          },
          { title: '失败原因', dataIndex: 'failReason', width: 200 },
          { title: '投递时间', dataIndex: 'sentAt', width: 180 },
        ]}
      />
    </Space>
  );
}

/** 催办与冲突面板 */
function UrgePanel() {
  const [day, setDay] = useState<Dayjs | null>(null);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<UrgeRunResult | null>(null);
  const [classId, setClassId] = useState<number | null>(null);
  const [preview, setPreview] = useState<UrgeItem | null>(null);
  const [conflicts, setConflicts] = useState<ScheduleConflictItem[]>([]);

  const canManage = hasPermission('sys:lms:urge:manage');

  const onRun = async () => {
    setRunning(true);
    try {
      const data = await runUrge(day ? day.format('YYYY-MM-DD') : undefined);
      setResult(data);
      message.success(`催办完成：通知 ${data.notified} 人次`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '催办执行失败');
    } finally {
      setRunning(false);
    }
  };

  const onInspect = async () => {
    if (!classId) {
      message.warning('请先填写培训班 ID');
      return;
    }
    try {
      const [item, list] = await Promise.all([previewUrge(classId), leaveConflicts(classId)]);
      setPreview(item);
      setConflicts(list);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '查询失败');
    }
  };

  return (
    <Space direction="vertical" size="middle" style={{ display: 'flex' }}>
      <Alert
        type="info"
        showIcon
        message="催办按天幂等，可安全补跑"
        description="系统每日 09:00 自动执行一轮；补跑请选择对应日期，同一天重复执行不会重复发送。离职与未收录账号的人不会收到通知。"
      />
      <Space wrap>
        <DatePicker
          value={day}
          onChange={(value) => setDay(value)}
          placeholder="催办归属日期（默认当天）"
        />
        <Button type="primary" loading={running} disabled={!canManage} onClick={onRun}>
          执行催办
        </Button>
        <Input
          allowClear
          type="number"
          placeholder="培训班 ID"
          style={{ width: 160 }}
          value={classId ?? ''}
          onChange={(event) => setClassId(event.target.value ? Number(event.target.value) : null)}
        />
        <Button disabled={!canManage} onClick={onInspect}>
          预览名单与请假冲突
        </Button>
      </Space>

      {preview && (
        <Card
          size="small"
          title={`培训班 ${preview.classCode ?? preview.classId}（未完成 ${preview.pendingCount} 人）`}
        >
          <Row gutter={16}>
            <Col span={4}>
              <Statistic title="因离职跳过" value={preview.skippedOffboard} />
            </Col>
            <Col span={4}>
              <Statistic title="未收录跳过" value={preview.skippedUnrecorded} />
            </Col>
            <Col span={8}>
              <Statistic
                title="可催办人数"
                value={preview.pendingCount - preview.skippedOffboard - preview.skippedUnrecorded}
              />
            </Col>
          </Row>
          {preview.error && (
            <Alert type="warning" showIcon message={preview.error} style={{ marginTop: 12 }} />
          )}
        </Card>
      )}

      {conflicts.length > 0 && (
        <Card size="small" title={`场次与请假冲突（${conflicts.length} 条）`}>
          <Table<ScheduleConflictItem>
            rowKey={(row) => `${row.sessionId}-${row.userId}`}
            size="small"
            pagination={false}
            dataSource={conflicts}
            columns={[
              { title: '场次', dataIndex: 'sessionTitle', width: 160 },
              { title: '开始时间', dataIndex: 'startTime', width: 180 },
              { title: '学员', dataIndex: 'username', width: 140 },
              { title: '请假单', dataIndex: 'leaveCode', width: 160 },
              { title: '假期类型', dataIndex: 'leaveType', width: 120 },
              {
                title: '请假区间',
                width: 200,
                render: (_value, row) => `${row.leaveStart ?? ''} ~ ${row.leaveEnd ?? ''}`,
              },
            ]}
          />
        </Card>
      )}

      {result && (
        <Card size="small" title={`执行结果（${result.day}）`}>
          <Row gutter={16} style={{ marginBottom: 12 }}>
            <Col span={4}>
              <Statistic title="扫描班级" value={result.scannedClasses} />
            </Col>
            <Col span={4}>
              <Statistic title="有未完成" value={result.urgentClasses} />
            </Col>
            <Col span={4}>
              <Statistic title="已通知人次" value={result.notified} />
            </Col>
            <Col span={4}>
              <Statistic title="因离职跳过" value={result.skippedOffboard} />
            </Col>
            <Col span={4}>
              <Statistic title="未收录跳过" value={result.skippedUnrecorded} />
            </Col>
            <Col span={4}>
              <Statistic title="失败班级" value={result.failedClasses} />
            </Col>
          </Row>
          <Table<UrgeItem>
            rowKey="classId"
            size="small"
            pagination={false}
            dataSource={result.items}
            columns={[
              { title: '培训班', dataIndex: 'className', width: 200 },
              { title: '未完成', dataIndex: 'pendingCount', width: 100 },
              { title: '已通知', dataIndex: 'notified', width: 100 },
              { title: '离职跳过', dataIndex: 'skippedOffboard', width: 100 },
              { title: '未收录', dataIndex: 'skippedUnrecorded', width: 100 },
              { title: '错误', dataIndex: 'error' },
            ]}
          />
        </Card>
      )}
    </Space>
  );
}

export default function IntegrationPage() {
  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Tabs
        items={[
          { key: 'member', label: '员工快照', children: <MemberPanel /> },
          { key: 'notify', label: '通知日志', children: <NotifyPanel /> },
          { key: 'urge', label: '催办与冲突', children: <UrgePanel /> },
        ]}
      />
    </div>
  );
}
