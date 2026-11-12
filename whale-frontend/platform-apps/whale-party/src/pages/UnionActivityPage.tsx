// 工会活动（M-P3）：管理端「草稿 → 发布 → 结束 / 取消」+ 员工端活动园地报名。
//
// 管理端与园地放同一页的两个 Tab：工会管理员既管排期又要看报名名单，
// 普通员工只需要园地；园地接口只返回 PUBLISHED，草稿不会提前泄露。
// 取消报名由后端按「恢复同一行」处理，前端只负责刷新，避免重复报名产生多条记录。

import {
  Button,
  Card,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import {
  cancelUnionActivity,
  cancelUnionActivitySignup,
  closeUnionActivity,
  createUnionActivity,
  pageMyUnionActivities,
  pageUnionActivities,
  pageUnionActivityPortal,
  pageUnionActivitySignups,
  publishUnionActivity,
  signupUnionActivity,
  updateUnionActivity,
  type UnionActivitySaveCommand,
  type UnionActivityView,
  type UnionSignupView,
} from '../api/party';
import { hasPermission } from '../api/client';

/** 每页条数 */
const PAGE_SIZE = 10;

/** 活动状态选项 */
const STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'CLOSED', label: '已结束' },
  { value: 'CANCELLED', label: '已取消' },
];

/** 状态标签与颜色 */
const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  CLOSED: '已结束',
  CANCELLED: '已取消',
};

/** 状态颜色 */
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  PUBLISHED: 'green',
  CLOSED: 'gray',
  CANCELLED: 'red',
};

/** 表单值：时间字段在表单里是 Dayjs */
type UnionActivityFormValues = Omit<UnionActivitySaveCommand, 'startTime' | 'endTime'> & {
  startTime?: Dayjs;
  endTime?: Dayjs;
};

/**
 * 格式化活动时间。
 *
 * @param value 时间文本
 * @returns 展示用时间
 */
function formatTime(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—';
}

/** 工会活动页 */
export default function UnionActivityPage() {
  const [activeTab, setActiveTab] = useState(
    hasPermission('sys:union:activity:list') ? 'manage' : 'portal',
  );

  const [rows, setRows] = useState<UnionActivityView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);

  const [editing, setEditing] = useState<UnionActivityView | null>(null);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<UnionActivityFormValues>();

  const [signupTarget, setSignupTarget] = useState<UnionActivityView | null>(null);
  const [signups, setSignups] = useState<UnionSignupView[]>([]);
  const [signupTotal, setSignupTotal] = useState(0);
  const [signupCurrent, setSignupCurrent] = useState(1);
  const [signupLoading, setSignupLoading] = useState(false);

  const [portalRows, setPortalRows] = useState<UnionActivityView[]>([]);
  const [portalTotal, setPortalTotal] = useState(0);
  const [portalCurrent, setPortalCurrent] = useState(1);
  const [portalKeyword, setPortalKeyword] = useState('');
  const [portalLoading, setPortalLoading] = useState(false);

  const [myOpen, setMyOpen] = useState(false);
  const [myRows, setMyRows] = useState<UnionSignupView[]>([]);
  const [myTotal, setMyTotal] = useState(0);
  const [myCurrent, setMyCurrent] = useState(1);
  const [myLoading, setMyLoading] = useState(false);

  const canManage = hasPermission('sys:union:activity:manage');
  const canList = hasPermission('sys:union:activity:list');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageUnionActivities({
        keyword: keyword || undefined,
        status,
        current,
        size: PAGE_SIZE,
      });
      setRows(page.records);
      setTotal(Number(page.total));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '工会活动加载失败');
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status]);

  const loadPortal = useCallback(async () => {
    setPortalLoading(true);
    try {
      const page = await pageUnionActivityPortal({
        keyword: portalKeyword || undefined,
        current: portalCurrent,
        size: PAGE_SIZE,
      });
      setPortalRows(page.records);
      setPortalTotal(Number(page.total));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '活动园地加载失败');
    } finally {
      setPortalLoading(false);
    }
  }, [portalCurrent, portalKeyword]);

  const loadSignups = useCallback(async () => {
    if (!signupTarget) {
      return;
    }
    setSignupLoading(true);
    try {
      const page = await pageUnionActivitySignups(signupTarget.id, {
        current: signupCurrent,
        size: PAGE_SIZE,
      });
      setSignups(page.records);
      setSignupTotal(Number(page.total));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '报名名单加载失败');
    } finally {
      setSignupLoading(false);
    }
  }, [signupCurrent, signupTarget]);

  const loadMy = useCallback(async () => {
    setMyLoading(true);
    try {
      const page = await pageMyUnionActivities({ current: myCurrent, size: PAGE_SIZE });
      setMyRows(page.records);
      setMyTotal(Number(page.total));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '我的报名加载失败');
    } finally {
      setMyLoading(false);
    }
  }, [myCurrent]);

  useEffect(() => {
    if (activeTab === 'manage') {
      void load();
    }
  }, [activeTab, load]);

  useEffect(() => {
    if (activeTab === 'portal') {
      void loadPortal();
    }
  }, [activeTab, loadPortal]);

  useEffect(() => {
    void loadSignups();
  }, [loadSignups]);

  useEffect(() => {
    if (myOpen) {
      void loadMy();
    }
  }, [loadMy, myOpen]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ quota: 0 });
    setOpen(true);
  };

  const openEdit = (row: UnionActivityView) => {
    setEditing(row);
    form.setFieldsValue({
      title: row.title,
      activityType: row.activityType ?? undefined,
      startTime: dayjs(row.startTime),
      endTime: dayjs(row.endTime),
      location: row.location ?? undefined,
      quota: row.quota,
      remark: row.remark ?? undefined,
    });
    setOpen(true);
  };

  const submitEditor = async () => {
    const values = await form.validateFields();
    const payload: UnionActivitySaveCommand = {
      title: values.title,
      activityType: values.activityType ?? null,
      startTime: values.startTime ? values.startTime.format('YYYY-MM-DDTHH:mm:ss') : '',
      endTime: values.endTime ? values.endTime.format('YYYY-MM-DDTHH:mm:ss') : '',
      location: values.location ?? null,
      quota: values.quota ?? null,
      remark: values.remark ?? null,
    };
    try {
      if (editing) {
        await updateUnionActivity(editing.id, payload);
        message.success('活动已更新');
      } else {
        await createUnionActivity(payload);
        message.success('活动草稿已创建');
      }
      setOpen(false);
      await load();
      await loadPortal();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存失败');
    }
  };

  const publish = async (row: UnionActivityView) => {
    try {
      await publishUnionActivity(row.id);
      message.success('活动已发布，员工可在园地报名');
      await load();
      await loadPortal();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '发布失败');
    }
  };

  const close = async (row: UnionActivityView) => {
    try {
      await closeUnionActivity(row.id);
      message.success('活动已结束');
      await load();
      await loadPortal();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '关闭失败');
    }
  };

  const cancel = async (row: UnionActivityView) => {
    try {
      await cancelUnionActivity(row.id);
      message.success('活动已取消');
      await load();
      await loadPortal();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '取消失败');
    }
  };

  const openSignups = (row: UnionActivityView) => {
    setSignupCurrent(1);
    setSignupTarget(row);
  };

  const signup = async (row: UnionActivityView) => {
    try {
      await signupUnionActivity(row.id);
      message.success('报名成功');
      await loadPortal();
      if (myOpen) {
        await loadMy();
      }
    } catch (err) {
      message.error(err instanceof Error ? err.message : '报名失败');
    }
  };

  const cancelSignup = async (activityId: number) => {
    try {
      await cancelUnionActivitySignup(activityId);
      message.success('已取消报名，可在活动开始前重新报名');
      await loadPortal();
      if (myOpen) {
        await loadMy();
      }
    } catch (err) {
      message.error(err instanceof Error ? err.message : '取消报名失败');
    }
  };

  const columns: ColumnsType<UnionActivityView> = [
    { title: '活动编号', dataIndex: 'activityNo', width: 130 },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '类型', dataIndex: 'activityType', width: 110, render: (value) => value ?? '—' },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      width: 150,
      render: (value: string) => formatTime(value),
    },
    {
      title: '结束时间',
      dataIndex: 'endTime',
      width: 150,
      render: (value: string) => formatTime(value),
    },
    { title: '地点', dataIndex: 'location', width: 130, render: (value) => value ?? '—' },
    {
      title: '名额',
      dataIndex: 'quota',
      width: 100,
      render: (value: number, row) =>
        value > 0 ? `${row.signupCount ?? 0} / ${value}` : '不限',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={STATUS_COLOR[value] ?? 'default'}>{STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 230,
      render: (_value, row) => (
        <Space size={4} wrap>
          {canManage && row.status === 'DRAFT' ? (
            <>
              <Button type="link" size="small" onClick={() => openEdit(row)}>
                编辑
              </Button>
              <Button type="link" size="small" onClick={() => void publish(row)}>
                发布
              </Button>
            </>
          ) : null}
          {row.status !== 'DRAFT' ? (
            <Button type="link" size="small" onClick={() => openSignups(row)}>
              报名名单
            </Button>
          ) : null}
          {canManage && row.status === 'PUBLISHED' ? (
            <Popconfirm title="结束后不可再报名，确认？" onConfirm={() => void close(row)}>
              <Button type="link" size="small">
                结束
              </Button>
            </Popconfirm>
          ) : null}
          {canManage && (row.status === 'DRAFT' || row.status === 'PUBLISHED') ? (
            <Popconfirm title="取消为终态，已报名员工将看到活动取消，确认？" onConfirm={() => void cancel(row)}>
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const portalColumns: ColumnsType<UnionActivityView> = [
    { title: '活动编号', dataIndex: 'activityNo', width: 130 },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '类型', dataIndex: 'activityType', width: 110, render: (value) => value ?? '—' },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      width: 150,
      render: (value: string) => formatTime(value),
    },
    { title: '地点', dataIndex: 'location', width: 130, render: (value) => value ?? '—' },
    {
      title: '报名情况',
      dataIndex: 'quota',
      width: 110,
      render: (value: number, row) =>
        value > 0 ? `${row.signupCount ?? 0} / ${value}` : `${row.signupCount ?? 0} 人`,
    },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      render: (_value, row) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => void signup(row)}>
            报名
          </Button>
          <Button type="link" size="small" danger onClick={() => void cancelSignup(row.id)}>
            取消
          </Button>
        </Space>
      ),
    },
  ];

  const signupColumns: ColumnsType<UnionSignupView> = [
    { title: '姓名', dataIndex: 'memberName', width: 110, render: (value) => value ?? '—' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={value === 'SIGNED' ? 'green' : 'default'}>
          {value === 'SIGNED' ? '已报名' : '已取消'}
        </Tag>
      ),
    },
    { title: '报名时间', dataIndex: 'signupAt', width: 160, render: (value) => formatTime(value) },
    {
      title: '取消时间',
      dataIndex: 'cancelledAt',
      width: 160,
      render: (value) => formatTime(value),
    },
  ];

  const myColumns: ColumnsType<UnionSignupView> = [
    { title: '活动', dataIndex: 'activityTitle', ellipsis: true, render: (value) => value ?? '—' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={value === 'SIGNED' ? 'green' : 'default'}>
          {value === 'SIGNED' ? '已报名' : '已取消'}
        </Tag>
      ),
    },
    { title: '报名时间', dataIndex: 'signupAt', width: 160, render: (value) => formatTime(value) },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_value, row) =>
        row.status === 'SIGNED' ? (
          <Popconfirm title="确认取消报名？" onConfirm={() => void cancelSignup(row.activityId)}>
            <Button type="link" size="small" danger>
              取消
            </Button>
          </Popconfirm>
        ) : (
          '—'
        ),
    },
  ];

  const manageTab = (
    <Card
      title="活动管理"
      size="small"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="活动编号 / 标题"
            style={{ width: 200 }}
            onSearch={(value) => {
              setCurrent(1);
              setKeyword(value.trim());
            }}
          />
          <Select
            allowClear
            placeholder="全部状态"
            style={{ width: 120 }}
            options={STATUS_OPTIONS}
            value={status}
            onChange={(value) => {
              setCurrent(1);
              setStatus(value);
            }}
          />
          {canManage ? (
            <Button type="primary" onClick={openCreate}>
              新建活动
            </Button>
          ) : null}
        </Space>
      }
    >
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1400 }}
        pagination={{
          current,
          pageSize: PAGE_SIZE,
          total,
          showSizeChanger: false,
          onChange: setCurrent,
        }}
      />
    </Card>
  );

  const portalTab = (
    <Card
      title="活动园地"
      size="small"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="活动标题关键字"
            style={{ width: 200 }}
            onSearch={(value) => {
              setPortalCurrent(1);
              setPortalKeyword(value.trim());
            }}
          />
          <Button
            onClick={() => {
              setMyCurrent(1);
              setMyOpen(true);
            }}
          >
            我的报名
          </Button>
        </Space>
      }
    >
      <Table
        rowKey="id"
        size="small"
        loading={portalLoading}
        columns={portalColumns}
        dataSource={portalRows}
        scroll={{ x: 1000 }}
        locale={{ emptyText: '暂无已发布的活动' }}
        pagination={{
          current: portalCurrent,
          pageSize: PAGE_SIZE,
          total: portalTotal,
          showSizeChanger: false,
          onChange: setPortalCurrent,
        }}
      />
    </Card>
  );

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={
          canList
            ? [
                { key: 'manage', label: '活动管理', children: manageTab },
                { key: 'portal', label: '活动园地', children: portalTab },
              ]
            : [{ key: 'portal', label: '活动园地', children: portalTab }]
        }
      />

      <Drawer
        title={editing ? '编辑活动' : '新建活动'}
        width={560}
        open={open}
        onClose={() => setOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setOpen(false)}>取消</Button>
            <Button type="primary" onClick={() => void submitEditor()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="title" label="活动标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="activityType" label="活动类型">
            <Input maxLength={64} placeholder="如：文体活动 / 志愿服务 / 技能培训" />
          </Form.Item>
          <Form.Item
            name="startTime"
            label="开始时间"
            rules={[{ required: true, message: '请选择开始时间' }]}
          >
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="endTime"
            label="结束时间"
            rules={[{ required: true, message: '请选择结束时间' }]}
          >
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="location" label="活动地点">
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="quota" label="名额（0 表示不限）">
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={`报名名单 · ${signupTarget?.title ?? ''}`}
        width={620}
        open={signupTarget !== null}
        onClose={() => setSignupTarget(null)}
      >
        <Table
          rowKey="id"
          size="small"
          loading={signupLoading}
          columns={signupColumns}
          dataSource={signups}
          locale={{ emptyText: '尚无人报名' }}
          pagination={{
            current: signupCurrent,
            pageSize: PAGE_SIZE,
            total: signupTotal,
            showSizeChanger: false,
            onChange: setSignupCurrent,
          }}
        />
      </Drawer>

      <Drawer title="我的报名" width={620} open={myOpen} onClose={() => setMyOpen(false)}>
        <Table
          rowKey="id"
          size="small"
          loading={myLoading}
          columns={myColumns}
          dataSource={myRows}
          locale={{ emptyText: '您还没有报名记录' }}
          pagination={{
            current: myCurrent,
            pageSize: PAGE_SIZE,
            total: myTotal,
            showSizeChanger: false,
            onChange: setMyCurrent,
          }}
        />
      </Drawer>
    </Space>
  );
}
