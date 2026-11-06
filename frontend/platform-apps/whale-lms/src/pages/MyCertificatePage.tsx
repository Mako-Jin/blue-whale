// 我的证书页：学员查看本人已获得的证书（覆盖手工 / 课程 / 考试 / 培训四种来源）。
//
// 权限码 sys:lms:cert:mine；服务端强制持证人为当前登录人，前端不传 userId。
// 过期与已撤销的证书仍会列出——学员需要看到「曾经有过但已失效」的事实，
// 隐藏它们反而会让人以为是系统丢了数据。

import { Card, Empty, Pagination, Select, Space, Tag, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { hasPermission } from '../api/client';
import { myCertificates, type CertSource, type CertStatus, type CertificateItem } from '../api/cert';

const { Text, Title } = Typography;

/** 状态展示 */
const STATUS_TAG: Record<CertStatus, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '有效' },
  EXPIRED: { color: 'orange', label: '已过期' },
  REVOKED: { color: 'red', label: '已撤销' },
  RENEWED: { color: 'default', label: '已换发' },
};

/** 来源展示 */
const SOURCE_LABEL: Record<CertSource, string> = {
  MANUAL: '手工发放',
  COURSE: '课程完成',
  EXAM: '考试通过',
  TRAINING: '培训完成',
};

export default function MyCertificatePage() {
  const [loading, setLoading] = useState(false);
  const [items, setItems] = useState<CertificateItem[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [status, setStatus] = useState<string | undefined>();

  const canView = hasPermission('sys:lms:cert:mine');

  const load = useCallback(async (page = 1, nextStatus = status) => {
    setLoading(true);
    try {
      const data = await myCertificates({ status: nextStatus, current: page, size: 9 });
      setItems(data.records ?? []);
      setTotal(data.total ?? 0);
      setCurrent(page);
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    if (canView) {
      void load(1, undefined);
    }
    // 首次加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canView]);

  if (!canView) {
    return (
      <div style={{ background: '#fff', padding: 32, borderRadius: 8 }}>
        <Empty description="暂无查看证书的权限" />
      </div>
    );
  }

  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Space style={{ marginBottom: 16 }}>
        <Title level={5} style={{ margin: 0 }}>
          我的证书
        </Title>
        <Select
          placeholder="全部状态"
          allowClear
          style={{ width: 130 }}
          options={Object.entries(STATUS_TAG).map(([value, item]) => ({ value, label: item.label }))}
          onChange={(value) => {
            setStatus(value || undefined);
            void load(1, value || undefined);
          }}
        />
      </Space>

      {items.length === 0 && !loading ? (
        <Empty description="还没有证书，完成课程或通过考试后可获得" />
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
          {items.map((item) => {
            const tag = STATUS_TAG[item.status] ?? { color: 'default', label: item.status };
            return (
              <Card
                key={item.id}
                size="small"
                loading={loading}
                title={<span style={{ fontSize: 15 }}>{item.templateName}</span>}
                extra={<Tag color={tag.color}>{tag.label}</Tag>}
              >
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  <Text copyable={{ text: item.certNo }}>编号：{item.certNo}</Text>
                  <Text type="secondary">来源：{SOURCE_LABEL[item.sourceType] ?? item.sourceType}</Text>
                  <Text type="secondary">发证：{item.issuedAt?.replace('T', ' ').slice(0, 10) ?? '-'}</Text>
                  <Text type="secondary">
                    有效期：{item.longTerm ? '长期有效' : `${item.validFrom?.slice(0, 10)} 至 ${item.validTo?.slice(0, 10)}`}
                  </Text>
                  {item.status === 'REVOKED' && (
                    <Text type="danger">撤销原因：{item.revokeReason ?? '-'}</Text>
                  )}
                </Space>
              </Card>
            );
          })}
        </div>
      )}

      {total > 9 && (
        <div style={{ marginTop: 16, textAlign: 'right' }}>
          <Pagination current={current} total={total} pageSize={9} onChange={(page) => void load(page, status)} />
        </div>
      )}
    </div>
  );
}
