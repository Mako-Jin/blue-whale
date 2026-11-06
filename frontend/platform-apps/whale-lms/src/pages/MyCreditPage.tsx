// 我的学分页：年度汇总 + 本人台账明细。
//
// 权限码 sys:lms:cert:mine（语义为「我的证书与学分」，与证书页共用 1948）。
// 服务端强制学员为当前登录人，前端不传 userId；汇总从台账聚合，不存快照。

import { Card, Col, Empty, Row, Select, Space, Statistic, Table, Tag, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { hasPermission } from '../api/client';
import {
  creditSummary,
  myCredits,
  type CreditLedgerItem,
  type CreditSource,
  type CreditSummary,
} from '../api/credit';

const { Title } = Typography;

/** 来源展示 */
const SOURCE_LABEL: Record<CreditSource, string> = {
  COURSE: '课程完成',
  EXAM: '考试通过',
  TRAINING: '培训完成',
};

/** 台账行 */
interface Row extends CreditLedgerItem {
  key: string;
}

export default function MyCreditPage() {
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<Row[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [periodYear, setPeriodYear] = useState<number | undefined>(new Date().getFullYear());
  const [summary, setSummary] = useState<CreditSummary | null>(null);
  const canView = hasPermission('sys:lms:cert:mine');

  const load = useCallback(async (page = 1, nextYear = periodYear) => {
    setLoading(true);
    try {
      const data = await myCredits({ periodYear: nextYear, current: page, size: 10 });
      setRows((data.records ?? []).map((item) => ({ ...item, key: String(item.id) })));
      setTotal(data.total ?? 0);
      setCurrent(page);
      const summaryData = await creditSummary({ periodYear: nextYear });
      setSummary(summaryData);
    } finally {
      setLoading(false);
    }
  }, [periodYear]);

  useEffect(() => {
    if (canView) {
      void load(1, new Date().getFullYear());
    }
    // 首次加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canView]);

  if (!canView) {
    return (
      <div style={{ background: '#fff', padding: 32, borderRadius: 8 }}>
        <Empty description="暂无查看学分的权限" />
      </div>
    );
  }

  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Space style={{ marginBottom: 16 }} wrap>
        <Title level={5} style={{ margin: 0 }}>
          我的学分
        </Title>
        <Select
          placeholder="全部年度"
          allowClear
          style={{ width: 120 }}
          value={periodYear}
          options={[0, 1, 2].map((offset) => {
            const year = new Date().getFullYear() - offset;
            return { value: year, label: `${year} 年` };
          })}
          onChange={(value) => {
            setPeriodYear(value ?? undefined);
            void load(1, value ?? undefined);
          }}
        />
      </Space>

      <Row gutter={12} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title={`合计学分（${periodYear ?? '全部年度'}）`}
              value={summary?.totalCredit ?? 0}
              precision={1}
            />
          </Card>
        </Col>
        {(summary?.items ?? []).map((item) => (
          <Col span={6} key={item.sourceType}>
            <Card size="small">
              <Statistic
                title={`${SOURCE_LABEL[item.sourceType] ?? item.sourceType}（${item.count} 笔）`}
                value={item.credit}
                precision={1}
              />
            </Card>
          </Col>
        ))}
      </Row>

      <Table<Row>
        rowKey="key"
        loading={loading}
        dataSource={rows}
        pagination={{ current, total, pageSize: 10, onChange: (page) => void load(page, periodYear) }}
        columns={[
          {
            title: '来源',
            dataIndex: 'sourceType',
            width: 120,
            render: (value: CreditSource) => (
              <Tag>{SOURCE_LABEL[value] ?? value}</Tag>
            ),
          },
          { title: '来源对象', dataIndex: 'sourceId', width: 120, render: (value) => `#${value}` },
          { title: '命中规则', dataIndex: 'ruleCode', width: 160 },
          { title: '学分', dataIndex: 'credit', width: 100 },
          {
            title: '时间',
            dataIndex: 'occurredAt',
            width: 180,
            render: (value: string) => (value ? value.replace('T', ' ').slice(0, 19) : '-'),
          },
          { title: '年度', dataIndex: 'periodYear', width: 90 },
          { title: '备注', dataIndex: 'remark' },
        ]}
      />
    </div>
  );
}
