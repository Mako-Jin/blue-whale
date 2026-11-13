// 遥测与影子：单设备实时流（SSE 手工分帧）+ 影子快照 + 指令下发与回执
//
// 实时流走 GET /iot/devices/{id}/stream（事件 ready/telemetry/event/command）；
// EventSource 无法带 Authorization 头，这里用 fetch 读流，与基座 token 读取器配合。

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  App,
  Badge,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { subscribeStream } from '../api/client';
import {
  deviceApi,
  type CommandVO,
  type DeviceVO,
  type ShadowVO,
  type TelemetryVO,
} from '../api/iot';

interface StreamLine {
  kind: 'telemetry' | 'event' | 'command' | 'ready';
  text: string;
  time: string;
}

const COMMAND_STATUS: Record<string, { color: string; text: string }> = {
  pending: { color: 'default', text: '待投递' },
  sent: { color: 'processing', text: '已下发' },
  success: { color: 'success', text: '成功' },
  failed: { color: 'error', text: '失败' },
  timeout: { color: 'warning', text: '超时' },
};

export default function TelemetryPage() {
  const { message } = App.useApp();
  const [devices, setDevices] = useState<DeviceVO[]>([]);
  const [deviceId, setDeviceId] = useState<string>();
  const [shadow, setShadow] = useState<ShadowVO | null>(null);
  const [lines, setLines] = useState<StreamLine[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [commands, setCommands] = useState<CommandVO[]>([]);
  const abortRef = useRef<AbortController | null>(null);
  const [commandForm] = Form.useForm<{ identifier: string; params?: string }>();

  useEffect(() => {
    deviceApi
      .page({ current: 1, size: 200 })
      .then((page) => {
        setDevices(page.records ?? []);
        if ((page.records ?? []).length > 0) {
          setDeviceId(page.records[0].id);
        }
      })
      .catch(() => setDevices([]));
  }, []);

  const stopStream = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setStreaming(false);
  }, []);

  useEffect(() => () => stopStream(), [stopStream]);

  const appendLine = useCallback((kind: StreamLine['kind'], text: string) => {
    setLines((prev) =>
      [{ kind, text, time: new Date().toLocaleTimeString() }, ...prev].slice(0, 200),
    );
  }, []);

  const loadSnapshot = useCallback(
    async (id: string) => {
      try {
        const [state, history] = await Promise.all([
          deviceApi.shadow(id),
          deviceApi.commands({ deviceId: id, current: 1, size: 20 }),
        ]);
        setShadow(state);
        setCommands(history.records ?? []);
      } catch (error) {
        message.error(error instanceof Error ? error.message : '实时态加载失败');
      }
    },
    [message],
  );

  useEffect(() => {
    stopStream();
    setLines([]);
    setShadow(null);
    setCommands([]);
    if (deviceId) {
      void loadSnapshot(deviceId);
    }
  }, [deviceId, loadSnapshot, stopStream]);

  const startStream = async () => {
    if (!deviceId || streaming) {
      return;
    }
    const controller = new AbortController();
    abortRef.current = controller;
    setStreaming(true);
    try {
      await subscribeStream(
        `/iot/devices/${deviceId}/stream`,
        (event, data) => {
          appendLine(
            (event as StreamLine['kind']) ?? 'telemetry',
            event === 'ready' ? data : summarize(event, data),
          );
          if (event === 'telemetry') {
            void loadSnapshot(deviceId);
          }
        },
        controller.signal,
      );
      appendLine('ready', '连接已关闭');
    } catch (error) {
      if (!controller.signal.aborted) {
        message.error(error instanceof Error ? error.message : '实时订阅失败');
      }
    } finally {
      setStreaming(false);
      abortRef.current = null;
    }
  };

  const sendCommand = async () => {
    if (!deviceId) {
      return;
    }
    const values = await commandForm.validateFields();
    let params: unknown;
    if (values.params && values.params.trim()) {
      try {
        params = JSON.parse(values.params);
      } catch {
        message.error('参数必须是合法 JSON');
        return;
      }
    }
    try {
      const command = await deviceApi.sendCommand(deviceId, {
        identifier: values.identifier,
        params,
      });
      message.success(`指令已创建：${command.messageId ?? command.id}`);
      commandForm.resetFields();
      void loadSnapshot(deviceId);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '指令下发失败');
    }
  };

  const reported = Object.entries(shadow?.reported ?? {});
  const desired = Object.entries(shadow?.desired ?? {});

  return (
    <>
      <h2 className="iot-page-title">遥测与影子</h2>
      <p className="iot-page-desc">
        选择设备后订阅实时流：遥测落库即刻推送，指令回执按 messageId 收敛（待投递 → 已下发 →
        成功/失败）。
      </p>

      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          style={{ width: 320 }}
          placeholder="选择设备"
          value={deviceId}
          onChange={setDeviceId}
          showSearch
          optionFilterProp="label"
          options={devices.map((item) => ({
            value: item.id,
            label: `${item.deviceName}${item.nickname ? `（${item.nickname}）` : ''}`,
          }))}
        />
        {streaming ? (
          <Button danger onClick={stopStream}>
            停止订阅
          </Button>
        ) : (
          <Button type="primary" disabled={!deviceId} onClick={startStream}>
            开始订阅
          </Button>
        )}
        <Button
          disabled={!deviceId}
          onClick={() => {
            if (deviceId) {
              void loadSnapshot(deviceId);
            }
          }}
        >
          刷新快照
        </Button>
        <Badge
          status={streaming ? 'processing' : 'default'}
          text={streaming ? '订阅中' : '未订阅'}
        />
      </Space>

      <Row gutter={16}>
        <Col span={10}>
          <Card size="small" title="上报快照" style={{ marginBottom: 16 }}>
            {reported.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无上报数据" />
            ) : (
              <div className="iot-shadow-grid">
                {reported.map(([key, value]) => (
                  <div className="iot-shadow-item" key={key}>
                    <div className="iot-shadow-label">{key}</div>
                    <div className="iot-shadow-value">{String(value)}</div>
                  </div>
                ))}
              </div>
            )}
          </Card>
          <Card size="small" title="待同步期望值" style={{ marginBottom: 16 }}>
            {desired.length === 0 ? (
              <Typography.Text type="secondary">无待同步项（期望值达成后自动清除）</Typography.Text>
            ) : (
              <div className="iot-shadow-grid">
                {desired.map(([key, value]) => (
                  <div className="iot-shadow-item" key={key}>
                    <div className="iot-shadow-label">{key}</div>
                    <div className="iot-shadow-value">{String(value)}</div>
                  </div>
                ))}
              </div>
            )}
          </Card>
          <Card size="small" title="指令下发">
            <Form form={commandForm} layout="vertical">
              <Form.Item
                name="identifier"
                label="服务标识符"
                rules={[{ required: true, message: '请输入物模型 service 标识符' }]}
              >
                <Input placeholder="reboot / setInterval" />
              </Form.Item>
              <Form.Item name="params" label="参数（JSON，可空）">
                <Input.TextArea rows={3} className="iot-mono" placeholder='{"interval": 30}' />
              </Form.Item>
              <Button type="primary" block disabled={!deviceId} onClick={sendCommand}>
                下发指令
              </Button>
            </Form>
          </Card>
        </Col>

        <Col span={14}>
          <Card
            size="small"
            title={`实时事件（${lines.length}）`}
            style={{ marginBottom: 16 }}
            extra={
              <Button size="small" type="link" onClick={() => setLines([])}>
                清空
              </Button>
            }
          >
            {lines.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="订阅后实时显示遥测、事件与指令状态"
              />
            ) : (
              <div className="iot-stream-list">
                {lines.map((line, index) => (
                  <div key={`${line.time}-${index}`}>
                    <Typography.Text type="secondary">{line.time}</Typography.Text>{' '}
                    <Tag
                      color={
                        line.kind === 'command'
                          ? 'processing'
                          : line.kind === 'event'
                            ? 'purple'
                            : 'default'
                      }
                    >
                      {line.kind}
                    </Tag>
                    <span className="iot-mono">{line.text}</span>
                  </div>
                ))}
              </div>
            )}
          </Card>
          <Card size="small" title="指令记录">
            <Table<CommandVO>
              size="small"
              rowKey="id"
              dataSource={commands}
              pagination={false}
              locale={{ emptyText: '暂无指令' }}
              columns={[
                { title: '服务', dataIndex: 'identifier', width: 140 },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 100,
                  render: (status: string) => {
                    const meta = COMMAND_STATUS[status] ?? { color: 'default', text: status };
                    return <Tag color={meta.color}>{meta.text}</Tag>;
                  },
                },
                {
                  title: '参数 / 回执',
                  key: 'payload',
                  render: (_, row) => (
                    <span className="iot-mono">
                      {row.params === undefined || row.params === null
                        ? '-'
                        : JSON.stringify(row.params)}
                      {row.reply === undefined || row.reply === null
                        ? ''
                        : ` → ${JSON.stringify(row.reply)}`}
                    </span>
                  ),
                },
                { title: '创建时间', dataIndex: 'createTime', width: 180 },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </>
  );
}

/** 流事件摘要：telemetry 取属性列表，command 取状态，其它原样 */
function summarize(event: string, data: string): string {
  try {
    const parsed = JSON.parse(data) as unknown;
    if (event === 'telemetry' && Array.isArray(parsed)) {
      return (parsed as TelemetryVO[])
        .map((point) => `${point.identifier}=${point.value ?? point.valueText}`)
        .join(', ');
    }
    if (event === 'command' && parsed && typeof parsed === 'object') {
      const record = parsed as Record<string, unknown>;
      return `${String(record.identifier ?? '')} ${String(record.status ?? '')}`;
    }
    return JSON.stringify(parsed);
  } catch {
    return data;
  }
}
