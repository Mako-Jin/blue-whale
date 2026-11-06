// 党政工会工作台（M-P0）：门禁状态 + 里程碑进度 + 领域边界。
//
// 这一页不展示任何业务数据——M-P0 只有底座，工作台的职责是把「服务已经起来了、
// 门禁是开还是关、后面还有哪几块没做、数据边界在哪」一次说清。
// 里程碑状态按已落地清单判断，不从后端推断「做到哪一步」：
// 后端只返回里程碑名字，进度是前端的事实陈述，写死反而不会出现
// 「接口说做完了、实际页面没有」的矛盾。

import { Alert, Card, List, Space, Tag, Typography, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { getMeta, type PartyMeta } from '../api/party';

/** 已落地里程碑（随波次增量；这是前端对交付状态的声明，不是后端返回值） */
const DONE_MILESTONES = new Set(['M-P0 底座', 'M-P1 党建台账']);

/** 党政工会工作台页 */
export default function PartyHomePage() {
  const [meta, setMeta] = useState<PartyMeta | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setMeta(await getMeta());
      setError(null);
    } catch (err) {
      // 403 与网络错误分开提示：前者是权限没配，后者是服务没起，处置方式完全不同
      const text = err instanceof Error ? err.message : '门禁状态加载失败';
      setError(text);
      message.error(text);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error ? (
        <Alert
          type="error"
          showIcon
          message="无法读取门禁状态"
          description={`${error}。若为 403，请确认当前账号拥有 sys:party:browse 权限（migration-5.88-party-m0.sql 已登记给党务管理员与租户管理员）。`}
        />
      ) : null}

      {meta ? (
        <Alert
          type={meta.demandGatePassed ? 'success' : 'warning'}
          showIcon
          message={meta.demandGatePassed ? '需求门禁已通过' : '需求门禁未通过'}
          description={meta.message}
        />
      ) : null}

      <Card title="实施进度" size="small">
        <List
          dataSource={meta?.milestones ?? []}
          locale={{ emptyText: '加载中…' }}
          renderItem={(item) => (
            <List.Item>
              <Space>
                <Tag color={DONE_MILESTONES.has(item) ? 'green' : 'default'}>
                  {DONE_MILESTONES.has(item) ? '已落地' : '待启动'}
                </Tag>
                <span>{item}</span>
              </Space>
            </List.Item>
          )}
        />
      </Card>

      <Card title="领域边界" size="small">
        <List
          dataSource={meta?.boundaries ?? []}
          locale={{ emptyText: '加载中…' }}
          renderItem={(item) => (
            <List.Item>
              <Typography.Text>{item}</Typography.Text>
            </List.Item>
          )}
        />
      </Card>
    </Space>
  );
}
