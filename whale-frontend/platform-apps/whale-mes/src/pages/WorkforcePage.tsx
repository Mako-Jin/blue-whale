// MES 人员绩效页：班组成员 / 排班分配 / 交接班 / 工时管理 / 绩效统计 / 技能矩阵
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TablePaginationConfig, TableProps, TabsProps } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  aggregateMesPerformance,
  cancelMesShiftAssignment,
  changeMesTeamMemberStatus,
  confirmMesHandover,
  createMesHandover,
  listMesMasterDataOptions,
  lockMesPerformance,
  pageMesHandovers,
  pageMesPerformance,
  pageMesShiftAssignments,
  pageMesSkills,
  pageMesTasks,
  pageMesTeamMembers,
  pageMesWorkHours,
  registerMesWorkHour,
  saveMesShiftAssignment,
  saveMesSkillMatrix,
  saveMesTeamMember,
  voidMesWorkHour,
  type MesHandover,
  type MesHandoverInput,
  type MesMasterData,
  type MesPerformanceLevel,
  type MesPerformanceStat,
  type MesShiftAssignment,
  type MesShiftAssignmentInput,
  type MesSkillMatrix,
  type MesSkillMatrixInput,
  type MesTask,
  type MesTeamMember,
  type MesTeamMemberInput,
  type MesWorkHour,
  type MesWorkHourInput,
} from '../api/mes';
import { hasPermission } from '../utils/permission';

/** 人员绩效页签 */
export type WorkforceTab = 'team' | 'shift' | 'handover' | 'work-hour' | 'performance' | 'skill';

/** 班组成员状态文案 */
const MEMBER_STATUS_LABEL: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
};

/** 班组成员状态下拉项 */
const MEMBER_STATUS_OPTIONS = Object.entries(MEMBER_STATUS_LABEL).map(([value, label]) => ({
  value,
  label,
}));

/** 技能等级文案 */
const SKILL_LEVEL_LABEL: Record<string, string> = {
  JUNIOR: '初级',
  MIDDLE: '中级',
  SENIOR: '高级',
  EXPERT: '专家',
};

/** 技能等级下拉项 */
const SKILL_LEVEL_OPTIONS = Object.entries(SKILL_LEVEL_LABEL).map(([value, label]) => ({
  value,
  label,
}));

/** 排班状态文案 */
const SHIFT_STATUS_LABEL: Record<string, string> = {
  ASSIGNED: '已排班',
  CANCELLED: '已取消',
};

/** 排班状态下拉项 */
const SHIFT_STATUS_OPTIONS = Object.entries(SHIFT_STATUS_LABEL).map(([value, label]) => ({
  value,
  label,
}));

/** 交接班状态文案 */
const HANDOVER_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  CONFIRMED: '已确认',
};

/** 交接班状态下拉项 */
const HANDOVER_STATUS_OPTIONS = Object.entries(HANDOVER_STATUS_LABEL).map(([value, label]) => ({
  value,
  label,
}));

/** 工时来源文案 */
const WORK_HOUR_SOURCE_LABEL: Record<string, string> = {
  REPORT: '报工',
  MANUAL: '手工',
};

/** 工时来源下拉项 */
const WORK_HOUR_SOURCE_OPTIONS = Object.entries(WORK_HOUR_SOURCE_LABEL).map(([value, label]) => ({
  value,
  label,
}));

/** 工时状态文案 */
const WORK_HOUR_STATUS_LABEL: Record<string, string> = {
  VALID: '有效',
  VOID: '已作废',
};

/** 工时状态下拉项 */
const WORK_HOUR_STATUS_OPTIONS = Object.entries(WORK_HOUR_STATUS_LABEL).map(([value, label]) => ({
  value,
  label,
}));

/** 主操作标记下拉项 */
const PRIMARY_OPERATOR_OPTIONS = [
  { value: 1, label: '是' },
  { value: 0, label: '否' },
];

/** 绩效层级文案 */
const PERFORMANCE_LEVEL_LABEL: Record<string, string> = {
  DAILY: '日统计',
  MONTHLY: '月统计',
};

/** 绩效层级下拉项 */
const PERFORMANCE_LEVEL_OPTIONS = Object.entries(PERFORMANCE_LEVEL_LABEL).map(([value, label]) => ({
  value,
  label,
}));

/** 技能状态文案 */
const SKILL_STATUS_LABEL: Record<string, string> = {
  ENABLED: '有效',
  EXPIRED: '已失效',
  DISABLED: '停用',
};

/** 技能状态下拉项 */
const SKILL_STATUS_OPTIONS = Object.entries(SKILL_STATUS_LABEL).map(([value, label]) => ({
  value,
  label,
}));

/** 日期展示：`YYYY-MM-DD` */
function formatDate(value?: string): string {
  return value ? String(value).slice(0, 10) : '-';
}

/** 时间展示：ISO 转 `YYYY-MM-DD HH:mm` */
function formatTime(value?: string): string {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '-';
}

/** 秒数展示为小时 */
function formatSeconds(value?: number): string {
  return value === undefined || value === null ? '-' : `${(Number(value) / 3600).toFixed(2)} h`;
}

/** 比率展示：小数转百分比 */
function formatRate(value?: number | string): string {
  const number = Number(value);
  return Number.isFinite(number) ? `${(number * 100).toFixed(2)}%` : '-';
}

/** 数量展示 */
function formatQuantity(value?: number | string): string {
  return value === undefined || value === null || value === '' ? '-' : String(value);
}

/** 日期选择值转 `YYYY-MM-DD` */
function toDateString(value?: Dayjs): string | undefined {
  return value ? value.format('YYYY-MM-DD') : undefined;
}

/** 时间选择值转 ISO `YYYY-MM-DDTHH:mm:ss` */
function toDateTimeString(value?: Dayjs): string | undefined {
  return value ? value.format('YYYY-MM-DDTHH:mm:ss') : undefined;
}

/** 主数据下拉选项（ID 为后端 Long，统一转数字） */
function masterDataOptions(items: MesMasterData[]) {
  return items.map((item) => ({
    value: Number(item.id),
    label: `${item.code} ${item.name}`.trim(),
  }));
}

/** 主数据编码下拉选项（班次等按编码引用） */
function codeOptions(items: MesMasterData[]) {
  return items.map((item) => ({
    value: item.code,
    label: `${item.code} ${item.name}`.trim(),
  }));
}

/** 任务下拉选项 */
function taskOptions(tasks: MesTask[]) {
  return tasks.map((task) => ({
    value: Number(task.id),
    label: `${task.taskNo} ${task.operationName}`.trim(),
  }));
}

/** 业务错误直接展示后端 message（@bluewhale/http 已解包 Result 信封） */
function showError(error: unknown, fallback: string): void {
  if (error instanceof Error && error.message) {
    message.error(error.message);
    return;
  }
  message.error(fallback);
}

/** 服务端分页配置 */
function paginationConfig(
  current: number,
  size: number,
  total: number,
  onChange: (page: number, pageSize: number) => void,
): TablePaginationConfig {
  return {
    current,
    pageSize: size,
    total,
    showSizeChanger: true,
    showTotal: (count) => `共 ${count} 条`,
    onChange,
  };
}

/** 班组成员表单值 */
interface TeamMemberFormValues {
  teamId: number;
  employeeId: number;
  employeeName: string;
  primaryOperator?: number;
  skillLevel?: string;
  joinDate?: Dayjs;
  leaveDate?: Dayjs;
  status?: string;
  remark?: string;
}

/** 班组成员面板 */
function TeamMemberPanel() {
  const [rows, setRows] = useState<MesTeamMember[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [teams, setTeams] = useState<MesMasterData[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<MesTeamMember | null>(null);
  const [form] = Form.useForm<TeamMemberFormValues>();
  const canManage = hasPermission('sys:mes:workforce:team');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMesTeamMembers({
        employeeName: keyword || undefined,
        status,
        current,
        size,
      });
      setRows(page.records);
      setTotal(page.total);
    } catch (error) {
      showError(error, '班组成员加载失败');
    } finally {
      setLoading(false);
    }
  }, [keyword, status, current, size]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void listMesMasterDataOptions('team')
      .then(setTeams)
      .catch((error: unknown) => showError(error, '班组下拉加载失败'));
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      primaryOperator: 0,
      skillLevel: 'JUNIOR',
      status: 'ENABLED',
    });
    setOpen(true);
  };

  const openEdit = (record: MesTeamMember) => {
    setEditing(record);
    form.setFieldsValue({
      teamId: record.teamId,
      employeeId: record.employeeId,
      employeeName: record.employeeName,
      primaryOperator: record.primaryOperator,
      skillLevel: record.skillLevel,
      joinDate: record.joinDate ? dayjs(record.joinDate) : undefined,
      leaveDate: record.leaveDate ? dayjs(record.leaveDate) : undefined,
      status: record.status,
      remark: record.remark,
    });
    setOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const input: MesTeamMemberInput = {
      id: editing?.id,
      teamId: values.teamId,
      employeeId: values.employeeId,
      employeeName: values.employeeName,
      primaryOperator: values.primaryOperator,
      skillLevel: values.skillLevel,
      joinDate: toDateString(values.joinDate),
      leaveDate: toDateString(values.leaveDate),
      status: values.status,
      remark: values.remark,
    };
    try {
      await saveMesTeamMember(input);
      message.success('班组成员已保存');
      setOpen(false);
      await load();
    } catch (error) {
      showError(error, '班组成员保存失败');
    }
  };

  const toggleStatus = async (record: MesTeamMember) => {
    try {
      await changeMesTeamMemberStatus(record.id, record.status !== 'ENABLED');
      message.success(record.status === 'ENABLED' ? '成员已停用' : '成员已启用');
      await load();
    } catch (error) {
      showError(error, '成员启停失败');
    }
  };

  const columns: TableProps<MesTeamMember>['columns'] = [
    { title: '班组编码', dataIndex: 'teamCode', key: 'teamCode', width: 120 },
    { title: '员工 ID', dataIndex: 'employeeId', key: 'employeeId', width: 100 },
    { title: '员工姓名', dataIndex: 'employeeName', key: 'employeeName', width: 120 },
    {
      title: '主操作员',
      dataIndex: 'primaryOperator',
      key: 'primaryOperator',
      width: 100,
      render: (value: number) => (value === 1 ? <Tag color="blue">主操作</Tag> : <Tag>普通</Tag>),
    },
    {
      title: '技能等级',
      dataIndex: 'skillLevel',
      key: 'skillLevel',
      width: 100,
      render: (value: string) => SKILL_LEVEL_LABEL[value] ?? value ?? '-',
    },
    {
      title: '入组日期',
      dataIndex: 'joinDate',
      key: 'joinDate',
      width: 110,
      render: (value: string) => formatDate(value),
    },
    {
      title: '离组日期',
      dataIndex: 'leaveDate',
      key: 'leaveDate',
      width: 110,
      render: (value: string) => formatDate(value),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'ENABLED' ? 'green' : 'default'}>
          {MEMBER_STATUS_LABEL[value] ?? value}
        </Tag>
      ),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, record) =>
        canManage ? (
          <Space size="small">
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
            <Popconfirm
              title={record.status === 'ENABLED' ? '确认停用该成员？' : '确认启用该成员？'}
              okText="确定"
              cancelText="取消"
              onConfirm={() => void toggleStatus(record)}
            >
              <Button type="link" size="small" danger={record.status === 'ENABLED'}>
                {record.status === 'ENABLED' ? '停用' : '启用'}
              </Button>
            </Popconfirm>
          </Space>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="员工姓名"
          style={{ width: 180 }}
          onSearch={(value) => {
            setCurrent(1);
            setKeyword(value);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 140 }}
          options={MEMBER_STATUS_OPTIONS}
          value={status}
          onChange={(value) => {
            setCurrent(1);
            setStatus(value);
          }}
        />
        <Button onClick={() => void load()}>查询</Button>
        {canManage && (
          <Button type="primary" onClick={openCreate}>
            新增成员
          </Button>
        )}
      </Space>
      <Table<MesTeamMember>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1100 }}
        pagination={paginationConfig(current, size, total, (page, pageSize) => {
          setCurrent(page);
          setSize(pageSize);
        })}
      />

      <Modal
        title={editing ? '编辑班组成员' : '新增班组成员'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void submit()}
        destroyOnHidden
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="teamId" label="班组" rules={[{ required: true, message: '请选择班组' }]}>
            <Select showSearch optionFilterProp="label" options={masterDataOptions(teams)} />
          </Form.Item>
          <Form.Item
            name="employeeId"
            label="员工 ID"
            rules={[{ required: true, message: '请输入员工 ID' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="employeeName"
            label="员工姓名"
            rules={[{ required: true, message: '请输入员工姓名' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="primaryOperator" label="是否主操作员">
            <Select options={PRIMARY_OPERATOR_OPTIONS} />
          </Form.Item>
          <Form.Item name="skillLevel" label="技能等级">
            <Select allowClear options={SKILL_LEVEL_OPTIONS} />
          </Form.Item>
          <Form.Item name="joinDate" label="入组日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="leaveDate" label="离组日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select options={MEMBER_STATUS_OPTIONS} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 排班表单值 */
interface ShiftFormValues {
  shiftDate: Dayjs;
  shiftCode: string;
  teamId: number;
  stationId: number;
  workCenterId?: number;
  employeeId: number;
  employeeName: string;
  primaryOperator?: number;
  startAt?: Dayjs;
  endAt?: Dayjs;
  remark?: string;
}

/** 排班分配面板 */
function ShiftPanel() {
  const [rows, setRows] = useState<MesShiftAssignment[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [shiftDate, setShiftDate] = useState<Dayjs | undefined>(undefined);
  const [shiftCode, setShiftCode] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [teams, setTeams] = useState<MesMasterData[]>([]);
  const [stations, setStations] = useState<MesMasterData[]>([]);
  const [workCenters, setWorkCenters] = useState<MesMasterData[]>([]);
  const [shifts, setShifts] = useState<MesMasterData[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<MesShiftAssignment | null>(null);
  const [form] = Form.useForm<ShiftFormValues>();
  const canManage = hasPermission('sys:mes:workforce:shift');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMesShiftAssignments({
        shiftDate: toDateString(shiftDate),
        shiftCode,
        status,
        current,
        size,
      });
      setRows(page.records);
      setTotal(page.total);
    } catch (error) {
      showError(error, '排班分配加载失败');
    } finally {
      setLoading(false);
    }
  }, [shiftDate, shiftCode, status, current, size]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void Promise.all([
      listMesMasterDataOptions('team'),
      listMesMasterDataOptions('station'),
      listMesMasterDataOptions('work-center'),
      listMesMasterDataOptions('shift'),
    ])
      .then(([teamItems, stationItems, workCenterItems, shiftItems]) => {
        setTeams(teamItems);
        setStations(stationItems);
        setWorkCenters(workCenterItems);
        setShifts(shiftItems);
      })
      .catch((error: unknown) => showError(error, '排班下拉加载失败'));
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      shiftDate: dayjs(),
      primaryOperator: 0,
    });
    setOpen(true);
  };

  const openEdit = (record: MesShiftAssignment) => {
    setEditing(record);
    form.setFieldsValue({
      shiftDate: dayjs(record.shiftDate),
      shiftCode: record.shiftCode,
      teamId: record.teamId,
      stationId: record.stationId,
      workCenterId: record.workCenterId,
      employeeId: record.employeeId,
      employeeName: record.employeeName,
      primaryOperator: record.primaryOperator,
      startAt: record.startAt ? dayjs(record.startAt) : undefined,
      endAt: record.endAt ? dayjs(record.endAt) : undefined,
      remark: record.remark,
    });
    setOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const input: MesShiftAssignmentInput = {
      id: editing?.id,
      shiftDate: values.shiftDate.format('YYYY-MM-DD'),
      shiftCode: values.shiftCode,
      teamId: values.teamId,
      stationId: values.stationId,
      workCenterId: values.workCenterId,
      employeeId: values.employeeId,
      employeeName: values.employeeName,
      primaryOperator: values.primaryOperator,
      startAt: toDateTimeString(values.startAt),
      endAt: toDateTimeString(values.endAt),
      remark: values.remark,
    };
    try {
      await saveMesShiftAssignment(input);
      message.success('排班已保存');
      setOpen(false);
      await load();
    } catch (error) {
      showError(error, '排班保存失败');
    }
  };

  const cancelShift = async (record: MesShiftAssignment) => {
    try {
      await cancelMesShiftAssignment(record.id);
      message.success('排班已取消');
      await load();
    } catch (error) {
      showError(error, '排班取消失败');
    }
  };

  const columns: TableProps<MesShiftAssignment>['columns'] = [
    {
      title: '排班日期',
      dataIndex: 'shiftDate',
      key: 'shiftDate',
      width: 110,
      render: (value: string) => formatDate(value),
    },
    { title: '班次', dataIndex: 'shiftCode', key: 'shiftCode', width: 100 },
    { title: '班组', dataIndex: 'teamCode', key: 'teamCode', width: 110 },
    { title: '工位', dataIndex: 'stationCode', key: 'stationCode', width: 110 },
    { title: '工作中心', dataIndex: 'workCenterCode', key: 'workCenterCode', width: 110 },
    { title: '员工', dataIndex: 'employeeName', key: 'employeeName', width: 110 },
    {
      title: '主操作',
      dataIndex: 'primaryOperator',
      key: 'primaryOperator',
      width: 90,
      render: (value: number) => (value === 1 ? <Tag color="blue">是</Tag> : <Tag>否</Tag>),
    },
    {
      title: '开始时间',
      dataIndex: 'startAt',
      key: 'startAt',
      width: 150,
      render: (value: string) => formatTime(value),
    },
    {
      title: '结束时间',
      dataIndex: 'endAt',
      key: 'endAt',
      width: 150,
      render: (value: string) => formatTime(value),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={value === 'ASSIGNED' ? 'green' : 'default'}>
          {SHIFT_STATUS_LABEL[value] ?? value}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) =>
        canManage && record.status === 'ASSIGNED' ? (
          <Space size="small">
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
            <Popconfirm
              title="确认取消该排班？"
              okText="取消排班"
              cancelText="返回"
              onConfirm={() => void cancelShift(record)}
            >
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          </Space>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <DatePicker
          allowClear
          placeholder="排班日期"
          value={shiftDate}
          onChange={(value) => {
            setCurrent(1);
            setShiftDate(value ?? undefined);
          }}
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="班次"
          style={{ width: 150 }}
          options={codeOptions(shifts)}
          value={shiftCode}
          onChange={(value) => {
            setCurrent(1);
            setShiftCode(value);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 140 }}
          options={SHIFT_STATUS_OPTIONS}
          value={status}
          onChange={(value) => {
            setCurrent(1);
            setStatus(value);
          }}
        />
        <Button onClick={() => void load()}>查询</Button>
        {canManage && (
          <Button type="primary" onClick={openCreate}>
            新增排班
          </Button>
        )}
      </Space>
      <Table<MesShiftAssignment>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1300 }}
        pagination={paginationConfig(current, size, total, (page, pageSize) => {
          setCurrent(page);
          setSize(pageSize);
        })}
      />

      <Modal
        title={editing ? '编辑排班分配' : '新增排班分配'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void submit()}
        destroyOnHidden
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="shiftDate"
            label="排班日期"
            rules={[{ required: true, message: '请选择排班日期' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="shiftCode"
            label="班次编码"
            rules={[{ required: true, message: '请选择班次' }]}
          >
            <Select showSearch optionFilterProp="label" options={codeOptions(shifts)} />
          </Form.Item>
          <Form.Item name="teamId" label="班组" rules={[{ required: true, message: '请选择班组' }]}>
            <Select showSearch optionFilterProp="label" options={masterDataOptions(teams)} />
          </Form.Item>
          <Form.Item
            name="stationId"
            label="工位"
            rules={[{ required: true, message: '请选择工位' }]}
          >
            <Select showSearch optionFilterProp="label" options={masterDataOptions(stations)} />
          </Form.Item>
          <Form.Item name="workCenterId" label="工作中心">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={masterDataOptions(workCenters)}
            />
          </Form.Item>
          <Form.Item
            name="employeeId"
            label="员工 ID"
            rules={[{ required: true, message: '请输入员工 ID' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="employeeName"
            label="员工姓名"
            rules={[{ required: true, message: '请输入员工姓名' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="primaryOperator" label="是否主操作">
            <Select options={PRIMARY_OPERATOR_OPTIONS} />
          </Form.Item>
          <Form.Item name="startAt" label="开始时间">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="endAt" label="结束时间">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 交接班表单值 */
interface HandoverFormValues {
  handoverNo?: string;
  teamId: number;
  fromShiftCode: string;
  toShiftCode: string;
  handoverDate: Dayjs;
  fromEmployeeId?: number;
  fromEmployeeName?: string;
  toEmployeeId?: number;
  toEmployeeName?: string;
  content: string;
  pendingIssues?: string;
  remark?: string;
}

/** 交接班面板 */
function HandoverPanel() {
  const [rows, setRows] = useState<MesHandover[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [handoverDate, setHandoverDate] = useState<Dayjs | undefined>(undefined);
  const [teams, setTeams] = useState<MesMasterData[]>([]);
  const [shifts, setShifts] = useState<MesMasterData[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [confirmTarget, setConfirmTarget] = useState<MesHandover | null>(null);
  const [form] = Form.useForm<HandoverFormValues>();
  const [confirmForm] = Form.useForm<{ confirmedBy: string }>();
  const canManage = hasPermission('sys:mes:workforce:handover');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMesHandovers({
        handoverNo: keyword || undefined,
        status,
        handoverDate: toDateString(handoverDate),
        current,
        size,
      });
      setRows(page.records);
      setTotal(page.total);
    } catch (error) {
      showError(error, '交接班加载失败');
    } finally {
      setLoading(false);
    }
  }, [keyword, status, handoverDate, current, size]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void Promise.all([listMesMasterDataOptions('team'), listMesMasterDataOptions('shift')])
      .then(([teamItems, shiftItems]) => {
        setTeams(teamItems);
        setShifts(shiftItems);
      })
      .catch((error: unknown) => showError(error, '交接班下拉加载失败'));
  }, []);

  const openCreate = () => {
    form.resetFields();
    form.setFieldsValue({ handoverDate: dayjs() });
    setOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const input: MesHandoverInput = {
      handoverNo: values.handoverNo,
      teamId: values.teamId,
      fromShiftCode: values.fromShiftCode,
      toShiftCode: values.toShiftCode,
      handoverDate: values.handoverDate.format('YYYY-MM-DD'),
      fromEmployeeId: values.fromEmployeeId,
      fromEmployeeName: values.fromEmployeeName,
      toEmployeeId: values.toEmployeeId,
      toEmployeeName: values.toEmployeeName,
      content: values.content,
      pendingIssues: values.pendingIssues,
      remark: values.remark,
    };
    try {
      await createMesHandover(input);
      message.success('交接班已创建');
      setOpen(false);
      await load();
    } catch (error) {
      showError(error, '交接班创建失败');
    }
  };

  const submitConfirm = async () => {
    if (!confirmTarget) {
      return;
    }
    const values = await confirmForm.validateFields();
    try {
      await confirmMesHandover(confirmTarget.id, values.confirmedBy);
      message.success('交接班已确认');
      setConfirmTarget(null);
      confirmForm.resetFields();
      await load();
    } catch (error) {
      showError(error, '交接班确认失败');
    }
  };

  const columns: TableProps<MesHandover>['columns'] = [
    { title: '交接单号', dataIndex: 'handoverNo', key: 'handoverNo', width: 160 },
    {
      title: '交接日期',
      dataIndex: 'handoverDate',
      key: 'handoverDate',
      width: 110,
      render: (value: string) => formatDate(value),
    },
    { title: '班组', dataIndex: 'teamCode', key: 'teamCode', width: 110 },
    { title: '交班班次', dataIndex: 'fromShiftCode', key: 'fromShiftCode', width: 100 },
    { title: '接班班次', dataIndex: 'toShiftCode', key: 'toShiftCode', width: 100 },
    { title: '交班人', dataIndex: 'fromEmployeeName', key: 'fromEmployeeName', width: 100 },
    { title: '接班人', dataIndex: 'toEmployeeName', key: 'toEmployeeName', width: 100 },
    { title: '交接内容', dataIndex: 'content', key: 'content', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'CONFIRMED' ? 'green' : 'blue'}>
          {HANDOVER_STATUS_LABEL[value] ?? value}
        </Tag>
      ),
    },
    {
      title: '确认人',
      dataIndex: 'confirmedBy',
      key: 'confirmedBy',
      width: 100,
    },
    {
      title: '确认时间',
      dataIndex: 'confirmedAt',
      key: 'confirmedAt',
      width: 150,
      render: (value: string) => formatTime(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        canManage && record.status === 'DRAFT' ? (
          <Button
            type="link"
            size="small"
            onClick={() => {
              confirmForm.resetFields();
              setConfirmTarget(record);
            }}
          >
            确认
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="交接单号"
          style={{ width: 180 }}
          onSearch={(value) => {
            setCurrent(1);
            setKeyword(value);
          }}
        />
        <DatePicker
          allowClear
          placeholder="交接日期"
          value={handoverDate}
          onChange={(value) => {
            setCurrent(1);
            setHandoverDate(value ?? undefined);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 140 }}
          options={HANDOVER_STATUS_OPTIONS}
          value={status}
          onChange={(value) => {
            setCurrent(1);
            setStatus(value);
          }}
        />
        <Button onClick={() => void load()}>查询</Button>
        {canManage && (
          <Button type="primary" onClick={openCreate}>
            新建交接班
          </Button>
        )}
      </Space>
      <Table<MesHandover>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1400 }}
        pagination={paginationConfig(current, size, total, (page, pageSize) => {
          setCurrent(page);
          setSize(pageSize);
        })}
      />

      <Modal
        title="新建交接班"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void submit()}
        destroyOnHidden
        width={720}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="handoverNo" label="交接单号">
            <Input maxLength={64} placeholder="留空自动生成" />
          </Form.Item>
          <Form.Item name="teamId" label="班组" rules={[{ required: true, message: '请选择班组' }]}>
            <Select showSearch optionFilterProp="label" options={masterDataOptions(teams)} />
          </Form.Item>
          <Form.Item
            name="fromShiftCode"
            label="交班班次"
            rules={[{ required: true, message: '请选择交班班次' }]}
          >
            <Select showSearch optionFilterProp="label" options={codeOptions(shifts)} />
          </Form.Item>
          <Form.Item
            name="toShiftCode"
            label="接班班次"
            rules={[{ required: true, message: '请选择接班班次' }]}
          >
            <Select showSearch optionFilterProp="label" options={codeOptions(shifts)} />
          </Form.Item>
          <Form.Item
            name="handoverDate"
            label="交接日期"
            rules={[{ required: true, message: '请选择交接日期' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="fromEmployeeId" label="交班人 ID">
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="fromEmployeeName" label="交班人姓名">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="toEmployeeId" label="接班人 ID">
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="toEmployeeName" label="接班人姓名">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="content"
            label="交接内容"
            rules={[{ required: true, message: '请输入交接内容' }]}
          >
            <Input.TextArea rows={3} maxLength={1000} />
          </Form.Item>
          <Form.Item name="pendingIssues" label="遗留问题">
            <Input.TextArea rows={2} maxLength={1000} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`确认交接班：${confirmTarget?.handoverNo ?? ''}`}
        open={confirmTarget !== null}
        onCancel={() => setConfirmTarget(null)}
        onOk={() => void submitConfirm()}
        destroyOnHidden
      >
        <Form form={confirmForm} layout="vertical">
          <Form.Item
            name="confirmedBy"
            label="确认人"
            rules={[{ required: true, message: '请输入确认人' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 工时登记表单值 */
interface WorkHourFormValues {
  employeeId: number;
  employeeName: string;
  workDate: Dayjs;
  taskId: number;
  startAt: Dayjs;
  endAt: Dayjs;
  pauseSeconds?: number;
  source?: string;
  remark?: string;
}

/** 工时管理面板 */
function WorkHourPanel() {
  const [rows, setRows] = useState<MesWorkHour[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [workDate, setWorkDate] = useState<Dayjs | undefined>(undefined);
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [source, setSource] = useState<string | undefined>(undefined);
  const [tasks, setTasks] = useState<MesTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<WorkHourFormValues>();
  const canManage = hasPermission('sys:mes:workforce:work-hour');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMesWorkHours({
        workDate: toDateString(workDate),
        status,
        source,
        current,
        size,
      });
      setRows(page.records);
      setTotal(page.total);
    } catch (error) {
      showError(error, '工时记录加载失败');
    } finally {
      setLoading(false);
    }
  }, [workDate, status, source, current, size]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void pageMesTasks({ current: 1, size: 200 })
      .then((page) => setTasks(page.records))
      .catch((error: unknown) => showError(error, '任务下拉加载失败'));
  }, []);

  const openCreate = () => {
    form.resetFields();
    form.setFieldsValue({
      workDate: dayjs(),
      pauseSeconds: 0,
      source: 'MANUAL',
      startAt: dayjs().startOf('hour'),
      endAt: dayjs().startOf('hour').add(1, 'hour'),
    });
    setOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const input: MesWorkHourInput = {
      employeeId: values.employeeId,
      employeeName: values.employeeName,
      workDate: values.workDate.format('YYYY-MM-DD'),
      taskId: values.taskId,
      startAt: values.startAt.format('YYYY-MM-DDTHH:mm:ss'),
      endAt: values.endAt.format('YYYY-MM-DDTHH:mm:ss'),
      pauseSeconds: values.pauseSeconds,
      source: values.source,
      remark: values.remark,
    };
    try {
      await registerMesWorkHour(input);
      message.success('工时已登记');
      setOpen(false);
      await load();
    } catch (error) {
      showError(error, '工时登记失败');
    }
  };

  const voidRecord = async (record: MesWorkHour) => {
    try {
      await voidMesWorkHour(record.id);
      message.success('工时已作废');
      await load();
    } catch (error) {
      showError(error, '工时作废失败');
    }
  };

  const columns: TableProps<MesWorkHour>['columns'] = [
    { title: '员工', dataIndex: 'employeeName', key: 'employeeName', width: 110 },
    {
      title: '工作日期',
      dataIndex: 'workDate',
      key: 'workDate',
      width: 110,
      render: (value: string) => formatDate(value),
    },
    { title: '任务编号', dataIndex: 'taskNo', key: 'taskNo', width: 140 },
    { title: '工单编码', dataIndex: 'workOrderCode', key: 'workOrderCode', width: 140 },
    {
      title: '开始时间',
      dataIndex: 'startAt',
      key: 'startAt',
      width: 150,
      render: (value: string) => formatTime(value),
    },
    {
      title: '结束时间',
      dataIndex: 'endAt',
      key: 'endAt',
      width: 150,
      render: (value: string) => formatTime(value),
    },
    {
      title: '暂停秒数',
      dataIndex: 'pauseSeconds',
      key: 'pauseSeconds',
      width: 100,
    },
    {
      title: '净工时',
      dataIndex: 'netSeconds',
      key: 'netSeconds',
      width: 100,
      render: (value: number) => formatSeconds(value),
    },
    {
      title: '来源',
      dataIndex: 'source',
      key: 'source',
      width: 90,
      render: (value: string) => WORK_HOUR_SOURCE_LABEL[value] ?? value ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={value === 'VALID' ? 'green' : 'default'}>
          {WORK_HOUR_STATUS_LABEL[value] ?? value}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        canManage && record.status === 'VALID' ? (
          <Popconfirm
            title="确认作废该工时记录？"
            okText="作废"
            cancelText="取消"
            onConfirm={() => void voidRecord(record)}
          >
            <Button type="link" size="small" danger>
              作废
            </Button>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <DatePicker
          allowClear
          placeholder="工作日期"
          value={workDate}
          onChange={(value) => {
            setCurrent(1);
            setWorkDate(value ?? undefined);
          }}
        />
        <Select
          allowClear
          placeholder="来源"
          style={{ width: 130 }}
          options={WORK_HOUR_SOURCE_OPTIONS}
          value={source}
          onChange={(value) => {
            setCurrent(1);
            setSource(value);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 130 }}
          options={WORK_HOUR_STATUS_OPTIONS}
          value={status}
          onChange={(value) => {
            setCurrent(1);
            setStatus(value);
          }}
        />
        <Button onClick={() => void load()}>查询</Button>
        {canManage && (
          <Button type="primary" onClick={openCreate}>
            手工登记
          </Button>
        )}
      </Space>
      <Table<MesWorkHour>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1300 }}
        pagination={paginationConfig(current, size, total, (page, pageSize) => {
          setCurrent(page);
          setSize(pageSize);
        })}
      />

      <Modal
        title="手工登记工时"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void submit()}
        destroyOnHidden
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="employeeId"
            label="员工 ID"
            rules={[{ required: true, message: '请输入员工 ID' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="employeeName"
            label="员工姓名"
            rules={[{ required: true, message: '请输入员工姓名' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="workDate"
            label="工作日期"
            rules={[{ required: true, message: '请选择工作日期' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="taskId" label="任务" rules={[{ required: true, message: '请选择任务' }]}>
            <Select showSearch optionFilterProp="label" options={taskOptions(tasks)} />
          </Form.Item>
          <Form.Item
            name="startAt"
            label="开始时间"
            rules={[{ required: true, message: '请选择开始时间' }]}
          >
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="endAt"
            label="结束时间"
            rules={[{ required: true, message: '请选择结束时间' }]}
          >
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="pauseSeconds" label="暂停秒数">
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="source" label="工时来源">
            <Select options={WORK_HOUR_SOURCE_OPTIONS} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 绩效统计面板 */
function PerformancePanel() {
  const [rows, setRows] = useState<MesPerformanceStat[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [statLevel, setStatLevel] = useState<MesPerformanceLevel>('DAILY');
  const [period, setPeriod] = useState<Dayjs>(dayjs());
  const [teams, setTeams] = useState<MesMasterData[]>([]);
  const [teamId, setTeamId] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [aggregating, setAggregating] = useState(false);
  const [periodLocked, setPeriodLocked] = useState(false);
  const [lockOpen, setLockOpen] = useState(false);
  const [lockForm] = Form.useForm<{ lockedBy: string }>();
  const canAggregate = hasPermission('sys:mes:workforce:performance');
  const canLock = hasPermission('sys:mes:workforce:lock');

  const periodCode = statLevel === 'DAILY' ? period.format('YYYYMMDD') : period.format('YYYYMM');
  const monthCode = period.format('YYYYMM');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [page, lockedPage] = await Promise.all([
        pageMesPerformance({ statLevel, periodCode, teamId, current, size }),
        pageMesPerformance({
          statLevel: 'MONTHLY',
          periodCode: monthCode,
          locked: 1,
          teamId,
          current: 1,
          size: 1,
        }),
      ]);
      setRows(page.records);
      setTotal(page.total);
      setPeriodLocked(lockedPage.total > 0);
    } catch (error) {
      showError(error, '绩效统计加载失败');
    } finally {
      setLoading(false);
    }
  }, [statLevel, periodCode, monthCode, teamId, current, size]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void listMesMasterDataOptions('team')
      .then(setTeams)
      .catch((error: unknown) => showError(error, '班组下拉加载失败'));
  }, []);

  const aggregate = async () => {
    setAggregating(true);
    try {
      const result = await aggregateMesPerformance({ statLevel, periodCode, teamId });
      message.success(`期间 ${periodCode} 汇总完成，共 ${result.length} 条`);
      await load();
    } catch (error) {
      showError(error, '绩效汇总失败');
    } finally {
      setAggregating(false);
    }
  };

  const submitLock = async () => {
    const values = await lockForm.validateFields();
    try {
      const result = await lockMesPerformance({
        periodCode: monthCode,
        lockedBy: values.lockedBy,
        teamId,
      });
      message.success(
        `月度锁定完成：本次锁定 ${result.lockedCount} 条，已锁定 ${result.alreadyLockedCount} 条`,
      );
      setLockOpen(false);
      lockForm.resetFields();
      await load();
    } catch (error) {
      showError(error, '月度锁定失败');
    }
  };

  const columns: TableProps<MesPerformanceStat>['columns'] = [
    {
      title: '统计层级',
      dataIndex: 'statLevel',
      key: 'statLevel',
      width: 100,
      render: (value: string) => PERFORMANCE_LEVEL_LABEL[value] ?? value,
    },
    { title: '员工', dataIndex: 'employeeName', key: 'employeeName', width: 110 },
    { title: '班组', dataIndex: 'teamCode', key: 'teamCode', width: 110 },
    {
      title: '统计日期',
      dataIndex: 'workDate',
      key: 'workDate',
      width: 110,
      render: (value: string) => formatDate(value),
    },
    { title: '期间编码', dataIndex: 'periodCode', key: 'periodCode', width: 110 },
    {
      title: '产量',
      dataIndex: 'productionQuantity',
      key: 'productionQuantity',
      width: 100,
      render: (value: number | string) => formatQuantity(value),
    },
    {
      title: '合格数量',
      dataIndex: 'qualifiedQuantity',
      key: 'qualifiedQuantity',
      width: 100,
      render: (value: number | string) => formatQuantity(value),
    },
    {
      title: '不良数量',
      dataIndex: 'defectQuantity',
      key: 'defectQuantity',
      width: 100,
      render: (value: number | string) => formatQuantity(value),
    },
    {
      title: '净工时',
      dataIndex: 'workSeconds',
      key: 'workSeconds',
      width: 100,
      render: (value: number) => formatSeconds(value),
    },
    {
      title: '标准工时',
      dataIndex: 'standardSeconds',
      key: 'standardSeconds',
      width: 100,
      render: (value: number) => formatSeconds(value),
    },
    {
      title: '效率',
      dataIndex: 'efficiency',
      key: 'efficiency',
      width: 100,
      render: (value: number | string) => formatRate(value),
    },
    {
      title: '合格率',
      dataIndex: 'passRate',
      key: 'passRate',
      width: 100,
      render: (value: number | string) => formatRate(value),
    },
    {
      title: '锁定',
      dataIndex: 'locked',
      key: 'locked',
      width: 90,
      render: (value: number) =>
        value === 1 ? <Tag color="orange">已锁定</Tag> : <Tag>未锁定</Tag>,
    },
    {
      title: '锁定人',
      dataIndex: 'lockedBy',
      key: 'lockedBy',
      width: 100,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {periodLocked && (
        <Alert type="warning" showIcon message="该期间月度绩效已锁定，禁止重算与重复锁定" />
      )}
      <Space wrap>
        <Select
          style={{ width: 130 }}
          options={PERFORMANCE_LEVEL_OPTIONS}
          value={statLevel}
          onChange={(value) => {
            setCurrent(1);
            setStatLevel(value as MesPerformanceLevel);
          }}
        />
        <DatePicker
          allowClear={false}
          picker={statLevel === 'DAILY' ? 'date' : 'month'}
          value={period}
          onChange={(value) => {
            if (value) {
              setCurrent(1);
              setPeriod(value);
            }
          }}
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="班组"
          style={{ width: 180 }}
          options={masterDataOptions(teams)}
          value={teamId}
          onChange={(value) => {
            setCurrent(1);
            setTeamId(value);
          }}
        />
        <Button onClick={() => void load()}>查询</Button>
        {canAggregate && (
          <Button type="primary" loading={aggregating} onClick={() => void aggregate()}>
            按期间汇总
          </Button>
        )}
        {canLock && (
          <Button
            disabled={statLevel !== 'MONTHLY' || periodLocked}
            onClick={() => {
              lockForm.resetFields();
              setLockOpen(true);
            }}
          >
            月度锁定
          </Button>
        )}
        {statLevel !== 'MONTHLY' && !periodLocked && (
          <Typography.Text type="secondary">月度锁定需切换到月统计</Typography.Text>
        )}
      </Space>
      <Table<MesPerformanceStat>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1500 }}
        pagination={paginationConfig(current, size, total, (page, pageSize) => {
          setCurrent(page);
          setSize(pageSize);
        })}
      />

      <Modal
        title={`锁定月度绩效：${monthCode}`}
        open={lockOpen}
        onCancel={() => setLockOpen(false)}
        onOk={() => void submitLock()}
        destroyOnHidden
      >
        <Form form={lockForm} layout="vertical">
          <Form.Item
            name="lockedBy"
            label="锁定人"
            rules={[{ required: true, message: '请输入锁定人' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 技能矩阵表单值 */
interface SkillFormValues {
  employeeId: number;
  employeeName: string;
  skillCode: string;
  skillName: string;
  skillLevel?: string;
  certifiedAt?: Dayjs;
  expiryAt?: Dayjs;
  status?: string;
  remark?: string;
}

/** 技能矩阵面板 */
function SkillPanel() {
  const [rows, setRows] = useState<MesSkillMatrix[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [skillLevel, setSkillLevel] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<MesSkillMatrix | null>(null);
  const [form] = Form.useForm<SkillFormValues>();
  const canManage = hasPermission('sys:mes:workforce:skill');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMesSkills({
        skillCode: keyword || undefined,
        skillLevel,
        status,
        current,
        size,
      });
      setRows(page.records);
      setTotal(page.total);
    } catch (error) {
      showError(error, '技能矩阵加载失败');
    } finally {
      setLoading(false);
    }
  }, [keyword, skillLevel, status, current, size]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ skillLevel: 'JUNIOR', status: 'ENABLED' });
    setOpen(true);
  };

  const openEdit = (record: MesSkillMatrix) => {
    setEditing(record);
    form.setFieldsValue({
      employeeId: record.employeeId,
      employeeName: record.employeeName,
      skillCode: record.skillCode,
      skillName: record.skillName,
      skillLevel: record.skillLevel,
      certifiedAt: record.certifiedAt ? dayjs(record.certifiedAt) : undefined,
      expiryAt: record.expiryAt ? dayjs(record.expiryAt) : undefined,
      status: record.status,
      remark: record.remark,
    });
    setOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const input: MesSkillMatrixInput = {
      id: editing?.id,
      employeeId: values.employeeId,
      employeeName: values.employeeName,
      skillCode: values.skillCode,
      skillName: values.skillName,
      skillLevel: values.skillLevel,
      certifiedAt: toDateString(values.certifiedAt),
      expiryAt: toDateString(values.expiryAt),
      status: values.status,
      remark: values.remark,
    };
    try {
      await saveMesSkillMatrix(input);
      message.success('技能矩阵已保存');
      setOpen(false);
      await load();
    } catch (error) {
      showError(error, '技能矩阵保存失败');
    }
  };

  const columns: TableProps<MesSkillMatrix>['columns'] = [
    { title: '员工 ID', dataIndex: 'employeeId', key: 'employeeId', width: 100 },
    { title: '员工姓名', dataIndex: 'employeeName', key: 'employeeName', width: 120 },
    { title: '技能编码', dataIndex: 'skillCode', key: 'skillCode', width: 130 },
    { title: '技能名称', dataIndex: 'skillName', key: 'skillName', width: 150 },
    {
      title: '技能等级',
      dataIndex: 'skillLevel',
      key: 'skillLevel',
      width: 100,
      render: (value: string) => SKILL_LEVEL_LABEL[value] ?? value ?? '-',
    },
    {
      title: '认证日期',
      dataIndex: 'certifiedAt',
      key: 'certifiedAt',
      width: 110,
      render: (value: string) => formatDate(value),
    },
    {
      title: '失效日期',
      dataIndex: 'expiryAt',
      key: 'expiryAt',
      width: 110,
      render: (value: string) => formatDate(value),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={value === 'ENABLED' ? 'green' : value === 'EXPIRED' ? 'orange' : 'default'}>
          {SKILL_STATUS_LABEL[value] ?? value}
        </Tag>
      ),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) =>
        canManage ? (
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="技能编码"
          style={{ width: 180 }}
          onSearch={(value) => {
            setCurrent(1);
            setKeyword(value);
          }}
        />
        <Select
          allowClear
          placeholder="技能等级"
          style={{ width: 140 }}
          options={SKILL_LEVEL_OPTIONS}
          value={skillLevel}
          onChange={(value) => {
            setCurrent(1);
            setSkillLevel(value);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 140 }}
          options={SKILL_STATUS_OPTIONS}
          value={status}
          onChange={(value) => {
            setCurrent(1);
            setStatus(value);
          }}
        />
        <Button onClick={() => void load()}>查询</Button>
        {canManage && (
          <Button type="primary" onClick={openCreate}>
            新增技能
          </Button>
        )}
      </Space>
      <Table<MesSkillMatrix>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1100 }}
        pagination={paginationConfig(current, size, total, (page, pageSize) => {
          setCurrent(page);
          setSize(pageSize);
        })}
      />

      <Modal
        title={editing ? '编辑技能矩阵' : '新增技能矩阵'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void submit()}
        destroyOnHidden
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="employeeId"
            label="员工 ID"
            rules={[{ required: true, message: '请输入员工 ID' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="employeeName"
            label="员工姓名"
            rules={[{ required: true, message: '请输入员工姓名' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="skillCode"
            label="技能编码"
            rules={[{ required: true, message: '请输入技能编码' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="skillName"
            label="技能名称"
            rules={[{ required: true, message: '请输入技能名称' }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="skillLevel" label="技能等级">
            <Select allowClear options={SKILL_LEVEL_OPTIONS} />
          </Form.Item>
          <Form.Item name="certifiedAt" label="认证日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="expiryAt" label="失效日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select options={SKILL_STATUS_OPTIONS} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 人员绩效页 */
export default function WorkforcePage({ initialTab = 'team' }: { initialTab?: WorkforceTab }) {
  const [tab, setTab] = useState<WorkforceTab>(initialTab);

  useEffect(() => {
    setTab(initialTab);
  }, [initialTab]);

  const items: TabsProps['items'] = [
    { key: 'team', label: '班组成员', children: <TeamMemberPanel /> },
    { key: 'shift', label: '排班分配', children: <ShiftPanel /> },
    { key: 'handover', label: '交接班', children: <HandoverPanel /> },
    { key: 'work-hour', label: '工时管理', children: <WorkHourPanel /> },
    { key: 'performance', label: '绩效统计', children: <PerformancePanel /> },
    { key: 'skill', label: '技能矩阵', children: <SkillPanel /> },
  ];

  return (
    <Tabs
      activeKey={tab}
      onChange={(key) => setTab(key as WorkforceTab)}
      items={items}
      destroyInactiveTabPane
    />
  );
}
