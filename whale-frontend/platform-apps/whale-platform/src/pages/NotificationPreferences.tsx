// NotificationPreferences —— 用户通知偏好设置（Wave 6.17d）
//
// 数据存 auth 通用用户配置（/auth/v1/user-config），语义解释在 whale-message：
// 渠道开关集 + 静默时段（HH:mm，可跨零点）+ 最低级别阈值；站内信不受静默与阈值限制。
// 保存失败只提示，不影响页面其余部分；删除即恢复「按平台默认放行」。

import { Alert, App, Button, Card, Checkbox, Col, Input, Popconfirm, Row, Select, Space, Spin } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import {
  deleteNotifyPreference,
  listNotifyPreferences,
  saveNotifyPreference,
  type NotifyPreference,
} from '../api/preferences';

/** 全部可选渠道（站内信不受静默与阈值限制，语义见 whale-message resolve） */
const CHANNEL_OPTIONS = [
  { value: 'INAPP', label: '站内信' },
  { value: 'EMAIL', label: '邮件' },
  { value: 'SMS', label: '短信' },
  { value: 'IM', label: 'IM' },
];

/** 阈值选项（低于该级别的非站内信渠道被过滤；空=不设阈值） */
const MIN_LEVEL_OPTIONS = [
  { value: '', label: '不设阈值' },
  { value: 'INFO', label: 'INFO 及以上' },
  { value: 'WARN', label: 'WARN 及以上' },
  { value: 'ERROR', label: 'ERROR 及以上' },
  { value: 'CRITICAL', label: '仅 CRITICAL' },
];

/** 已开放自助配置的业务域（与 message resolve 的 bizType 对齐） */
const BIZ_SECTIONS: { bizType: string; title: string; description: string }[] = [
  {
    bizType: 'twin.alert',
    title: '孪生告警',
    description:
      '数字孪生场景的告警外发（门槛/场景静默/冷却仍按平台配置生效；此处只收紧你个人的接收渠道与免打扰时段）。',
  },
];

/** HH:mm 口径校验（合法则原样返回，否则 null） */
function normalizeHHmm(value: string): string | null {
  if (!value) {
    return '';
  }
  const text = value.trim();
  if (!/^\d{2}:\d{2}$/.test(text)) {
    return null;
  }
  const [h, m] = text.split(':').map(Number);
  if (h > 23 || m > 59) {
    return null;
  }
  return text;
}

/** 单个业务域的偏好编辑卡 */
function PreferenceSection({
  bizType,
  title,
  description,
  initial,
  onSaved,
}: {
  bizType: string;
  title: string;
  description: string;
  initial: NotifyPreference | null;
  onSaved: () => void;
}) {
  const { message } = App.useApp();
  const [channels, setChannels] = useState<string[]>(initial?.channels ?? ['INAPP']);
  const [minLevel, setMinLevel] = useState<string>(initial?.minLevel ?? '');
  const [quietStart, setQuietStart] = useState<string>(initial?.quietStart ?? '');
  const [quietEnd, setQuietEnd] = useState<string>(initial?.quietEnd ?? '');
  const [saving, setSaving] = useState(false);

  const toggleChannel = (value: string, checked: boolean) => {
    setChannels((prev) =>
      checked ? [...new Set([...prev, value])] : prev.filter((item) => item !== value),
    );
  };

  const handleSave = async () => {
    const start = normalizeHHmm(quietStart);
    const end = normalizeHHmm(quietEnd);
    if (start === null || end === null) {
      message.error('静默时段必须是 HH:mm（如 22:00），留空表示不静默');
      return;
    }
    if (!channels.length) {
      message.error('至少保留一个渠道，或直接删除偏好恢复平台默认');
      return;
    }
    setSaving(true);
    try {
      const value: NotifyPreference = { channels };
      if (minLevel) {
        value.minLevel = minLevel;
      }
      if (start && end) {
        value.quietStart = start;
        value.quietEnd = end;
      }
      await saveNotifyPreference(`notify.${bizType}`, value);
      message.success('已保存');
      onSaved();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleReset = async () => {
    setSaving(true);
    try {
      await deleteNotifyPreference(`notify.${bizType}`);
      setChannels(['INAPP']);
      setMinLevel('');
      setQuietStart('');
      setQuietEnd('');
      message.success('已恢复平台默认（全部渠道放行、不静默）');
      onSaved();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card
      title={title}
      extra={
        <Popconfirm title="删除偏好并恢复平台默认？" onConfirm={() => void handleReset()}>
          <Button size="small">恢复默认</Button>
        </Popconfirm>
      }
    >
      <p style={{ color: 'var(--ant-color-text-secondary, #8f959e)', marginTop: 0 }}>{description}</p>
      <Row gutter={[16, 12]}>
        <Col span={24}>
          <Space wrap>
            {CHANNEL_OPTIONS.map((option) => (
              <Checkbox
                key={option.value}
                checked={channels.includes(option.value)}
                onChange={(event) => toggleChannel(option.value, event.target.checked)}
              >
                {option.label}
              </Checkbox>
            ))}
          </Space>
        </Col>
        <Col span={12}>
          <Space>
            <span>最低级别阈值：</span>
            <Select
              style={{ width: 180 }}
              value={minLevel}
              options={MIN_LEVEL_OPTIONS}
              onChange={setMinLevel}
            />
          </Space>
        </Col>
        <Col span={12}>
          <Space>
            <span>静默时段：</span>
            <Input
              style={{ width: 90 }}
              placeholder="22:00"
              value={quietStart}
              onChange={(event) => setQuietStart(event.target.value)}
            />
            <span>至</span>
            <Input
              style={{ width: 90 }}
              placeholder="08:00"
              value={quietEnd}
              onChange={(event) => setQuietEnd(event.target.value)}
            />
            <span style={{ color: '#8f959e' }}>可跨零点；站内信不受静默与阈值限制</span>
          </Space>
        </Col>
        <Col span={24}>
          <Button type="primary" loading={saving} onClick={() => void handleSave()}>
            保存
          </Button>
        </Col>
      </Row>
    </Card>
  );
}

/** 通知偏好设置页 */
export default function NotificationPreferences() {
  const [initials, setInitials] = useState<Record<string, NotifyPreference> | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const load = useCallback(async () => {
    setLoadError(null);
    try {
      const items = await listNotifyPreferences();
      const map: Record<string, NotifyPreference> = {};
      items.forEach((item) => {
        try {
          map[item.configKey] = JSON.parse(item.configValue) as NotifyPreference;
        } catch {
          // 单条解析失败忽略：该域按默认渲染
        }
      });
      setInitials(map);
    } catch (error) {
      setInitials({});
      setLoadError(error instanceof Error ? error.message : '偏好加载失败');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load, reloadToken]);

  if (!initials) {
    return (
      <div style={{ padding: 32 }}>
        <Spin />
      </div>
    );
  }

  return (
    <div style={{ padding: 24 }}>
      <h2 style={{ marginTop: 0 }}>通知偏好</h2>
      {loadError && <Alert type="warning" showIcon message={loadError} style={{ marginBottom: 16 }} />}
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {BIZ_SECTIONS.map((section) => (
          <PreferenceSection
            key={section.bizType}
            bizType={section.bizType}
            title={section.title}
            description={section.description}
            initial={initials[`notify.${section.bizType}`] ?? null}
            onSaved={() => setReloadToken((token) => token + 1)}
          />
        ))}
      </Space>
    </div>
  );
}
