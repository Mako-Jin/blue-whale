// SLA 设置：三档优先级响应 / 解决时限与 7×8 工作日历
import { useEffect, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  getServiceCalendar,
  listSlaPolicies,
  saveServiceCalendar,
  saveSlaPolicies,
  type ServiceCalendarView,
  type SlaPolicyView,
} from '../../api/service';
import {
  TICKET_PRIORITY_LABEL,
  TICKET_PRIORITY_OPTIONS,
} from '../../utils/dict';

/** 工作日历表单值 */
interface CalendarFormValues {
  calendarCode?: string;
  workdayStart: string;
  workdayEnd: string;
  breakMinutes: number;
  weekdays: string[];
  enabled: boolean;
}

/** 星期下拉选项（ISO 1–7） */
const WEEKDAY_OPTIONS = [
  { value: '1', label: '周一' },
  { value: '2', label: '周二' },
  { value: '3', label: '周三' },
  { value: '4', label: '周四' },
  { value: '5', label: '周五' },
  { value: '6', label: '周六' },
  { value: '7', label: '周日' },
];

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 可展示的错误信息
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

/**
 * 拆分星期字符串为数组。
 *
 * @param weekdays 逗号分隔的 ISO 星期
 * @returns 星期数组
 */
function splitWeekdays(weekdays?: string | null): string[] {
  return (weekdays ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter((item) => item !== '');
}

/** SLA 设置页 */
export default function ServiceSlaPage() {
  const [policyForm] = Form.useForm<{ policies: SlaPolicyView[] }>();
  const [calendarForm] = Form.useForm<CalendarFormValues>();
  const [policies, setPolicies] = useState<SlaPolicyView[]>([]);
  const [calendar, setCalendar] = useState<ServiceCalendarView | null>(null);
  const [loading, setLoading] = useState(false);
  const [savingPolicies, setSavingPolicies] = useState(false);
  const [savingCalendar, setSavingCalendar] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    Promise.all([listSlaPolicies(), getServiceCalendar()])
      .then(([policyRows, calendarView]) => {
        if (!active) {
          return;
        }
        setPolicies(policyRows);
        setCalendar(calendarView);
        policyForm.setFieldsValue({ policies: policyRows });
        calendarForm.setFieldsValue({
          calendarCode: calendarView.calendarCode,
          workdayStart: calendarView.workdayStart,
          workdayEnd: calendarView.workdayEnd,
          breakMinutes: calendarView.breakMinutes,
          weekdays: splitWeekdays(calendarView.weekdays),
          enabled: calendarView.enabled,
        });
      })
      .catch((error: unknown) => {
        message.error(errorText(error));
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [policyForm, calendarForm]);

  const handleSavePolicies = async () => {
    setSavingPolicies(true);
    try {
      const values = await policyForm.validateFields();
      const saved = await saveSlaPolicies({ policies: values.policies });
      setPolicies(saved);
      policyForm.setFieldsValue({ policies: saved });
      message.success('SLA 策略已保存');
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    } finally {
      setSavingPolicies(false);
    }
  };

  const handleSaveCalendar = async () => {
    setSavingCalendar(true);
    try {
      const values = await calendarForm.validateFields();
      const saved = await saveServiceCalendar({
        calendarCode: values.calendarCode,
        workdayStart: values.workdayStart,
        workdayEnd: values.workdayEnd,
        breakMinutes: values.breakMinutes,
        weekdays: values.weekdays.join(','),
        enabled: values.enabled,
      });
      setCalendar(saved);
      calendarForm.setFieldsValue({
        calendarCode: saved.calendarCode,
        workdayStart: saved.workdayStart,
        workdayEnd: saved.workdayEnd,
        breakMinutes: saved.breakMinutes,
        weekdays: splitWeekdays(saved.weekdays),
        enabled: saved.enabled,
      });
      message.success('工作日历已保存');
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    } finally {
      setSavingCalendar(false);
    }
  };

  const columns: TableProps<SlaPolicyView>['columns'] = [
    {
      title: '优先级',
      dataIndex: 'priority',
      width: 140,
      render: (value?: string | null) => TICKET_PRIORITY_LABEL[value ?? ''] ?? value ?? '-',
    },
    { title: '响应时限（工作小时）', dataIndex: 'responseHours', width: 180 },
    { title: '解决时限（工作小时）', dataIndex: 'resolveHours', width: 180 },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 100,
      render: (value?: boolean | null) => (value ? '启用' : '停用'),
    },
  ];

  return (
    <Card title="SLA 设置" loading={loading}>
      <Typography.Text strong style={{ display: 'block', marginBottom: 12 }}>
        SLA 策略（按优先级三档）
      </Typography.Text>
      <Form form={policyForm} layout="vertical">
        <Form.List name="policies">
          {(fields) => (
            <Row gutter={12}>
              {fields.map((field) => {
                const priority = policies[field.name]?.priority ?? TICKET_PRIORITY_OPTIONS[field.name]?.value;
                return (
                  <Col span={8} key={field.key}>
                    <Card size="small" title={TICKET_PRIORITY_LABEL[priority ?? ''] ?? priority ?? '优先级'}>
                      <Form.Item name={[field.name, 'priority']} hidden>
                        <Input />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'responseHours']}
                        label="响应时限（工作小时）"
                        rules={[{ required: true, message: '请输入响应时限' }]}
                      >
                        <InputNumber min={1} max={240} precision={0} style={{ width: '100%' }} />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'resolveHours']}
                        label="解决时限（工作小时）"
                        rules={[{ required: true, message: '请输入解决时限' }]}
                      >
                        <InputNumber min={1} max={720} precision={0} style={{ width: '100%' }} />
                      </Form.Item>
                      <Form.Item name={[field.name, 'enabled']} label="启用" valuePropName="checked">
                        <Switch />
                      </Form.Item>
                    </Card>
                  </Col>
                );
              })}
            </Row>
          )}
        </Form.List>
        <Space style={{ marginTop: 12 }}>
          <Button type="primary" loading={savingPolicies} onClick={() => void handleSavePolicies()}>
            保存策略
          </Button>
        </Space>
      </Form>
      <Table<SlaPolicyView>
        style={{ marginTop: 16 }}
        rowKey="priority"
        size="small"
        pagination={false}
        columns={columns}
        dataSource={policies}
      />

      <Typography.Text strong style={{ display: 'block', margin: '24px 0 12px' }}>
        工作日历（7×8 工作时段）
      </Typography.Text>
      <Form form={calendarForm} layout="vertical">
        <Row gutter={12}>
          <Col span={6}>
            <Form.Item name="calendarCode" label="日历编码">
              <Input maxLength={32} />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item
              name="workdayStart"
              label="上班时间（HH:mm）"
              rules={[
                { required: true, message: '请输入上班时间' },
                { pattern: /^[0-9]{1,2}:[0-9]{2}$/, message: '格式应为 HH:mm' },
              ]}
            >
              <Input placeholder="09:00" maxLength={5} />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item
              name="workdayEnd"
              label="下班时间（HH:mm）"
              rules={[
                { required: true, message: '请输入下班时间' },
                { pattern: /^[0-9]{1,2}:[0-9]{2}$/, message: '格式应为 HH:mm' },
              ]}
            >
              <Input placeholder="18:00" maxLength={5} />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item name="breakMinutes" label="午休分钟数" rules={[{ required: true, message: '请输入午休分钟数' }]}>
              <InputNumber min={0} max={480} precision={0} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={12}>
          <Col span={12}>
            <Form.Item name="weekdays" label="工作日" rules={[{ required: true, message: '请选择工作日' }]}>
              <Select mode="multiple" options={WEEKDAY_OPTIONS} placeholder="请选择工作日" />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item label=" " colon={false}>
              <Button type="primary" loading={savingCalendar} onClick={() => void handleSaveCalendar()}>
                保存日历
              </Button>
            </Form.Item>
          </Col>
        </Row>
      </Form>
      {calendar && (
        <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
          当前日历：{calendar.calendarCode} / {calendar.workdayStart}–{calendar.workdayEnd} / 午休
          {calendar.breakMinutes} 分钟 / 工作日 {calendar.weekdays} / {calendar.enabled ? '启用' : '停用'}
        </Typography.Text>
      )}
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
        工作日历无节假日表；SLA 仅按工作分钟逐日推进，暂停 / 恢复按实际暂停时长顺延截止时间。
      </Typography.Text>
    </Card>
  );
}
