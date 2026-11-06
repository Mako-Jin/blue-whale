// 我的考试页：我的作答记录 + 我的成绩（按版本）。
//
// 作答记录里未交卷的可以直接点「继续作答」回到答题页——服务端对该场考试
// 会复用同一条未交卷记录，不会因为刷新页面多消耗一次作答次数。

import { Button, Space, Table, Tabs, Tag, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { myAttempts, myScores, type AttemptItem, type ScoreItem } from '../api/exam';

/** 作答状态展示 */
const ATTEMPT_TAG: Record<string, { color: string; label: string }> = {
  IN_PROGRESS: { color: 'blue', label: '作答中' },
  SUBMITTED: { color: 'orange', label: '已交卷待判' },
  GRADED: { color: 'green', label: '已判分' },
  EXPIRED: { color: 'red', label: '已失效' },
};

/** 作答行 */
interface AttemptRow extends AttemptItem {
  key: string;
}

/** 成绩行 */
interface ScoreRow extends ScoreItem {
  key: string;
}

export default function MyExamPage() {
  const navigate = useNavigate();
  const [attempts, setAttempts] = useState<AttemptRow[]>([]);
  const [attemptTotal, setAttemptTotal] = useState(0);
  const [attemptPage, setAttemptPage] = useState(1);
  const [scores, setScores] = useState<ScoreRow[]>([]);
  const [scoreTotal, setScoreTotal] = useState(0);
  const [scorePage, setScorePage] = useState(1);
  const [loading, setLoading] = useState(false);

  const loadAttempts = useCallback(async (page = attemptPage) => {
    setLoading(true);
    try {
      const data = await myAttempts({ current: page, size: 10 });
      setAttempts((data.records ?? []).map((item) => ({ ...item, key: String(item.id) })));
      setAttemptTotal(data.total ?? 0);
      setAttemptPage(page);
    } finally {
      setLoading(false);
    }
  }, [attemptPage]);

  const loadScores = useCallback(async (page = scorePage) => {
    setLoading(true);
    try {
      const data = await myScores({ current: page, size: 10 });
      setScores((data.records ?? []).map((item) => ({ ...item, key: String(item.id) })));
      setScoreTotal(data.total ?? 0);
      setScorePage(page);
    } finally {
      setLoading(false);
    }
  }, [scorePage]);

  useEffect(() => {
    void loadAttempts(1);
    void loadScores(1);
    // 首次加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Tabs
        items={[
          {
            key: 'attempt',
            label: '我的作答',
            children: (
              <Table<AttemptRow>
                rowKey="key"
                loading={loading}
                dataSource={attempts}
                pagination={{
                  current: attemptPage,
                  total: attemptTotal,
                  pageSize: 10,
                  onChange: (page) => void loadAttempts(page),
                }}
                columns={[
                  { title: '考试', dataIndex: 'examTitle' },
                  { title: '第几次', dataIndex: 'attemptNo', width: 90 },
                  { title: '开始时间', dataIndex: 'startedAt', width: 180 },
                  {
                    title: '状态',
                    dataIndex: 'status',
                    width: 120,
                    render: (value: string) => {
                      const tag = ATTEMPT_TAG[value] ?? { color: 'default', label: value };
                      return <Tag color={tag.color}>{tag.label}</Tag>;
                    },
                  },
                  {
                    title: '得分',
                    dataIndex: 'totalScore',
                    width: 100,
                    render: (value: number | undefined) => (value === undefined || value === null ? '-' : value),
                  },
                  {
                    title: '操作',
                    width: 140,
                    render: (_, row) => (
                      <Space size="small">
                        <Button size="small" type="link" onClick={() => navigate(`/attempt/${row.id}`)}>
                          {row.status === 'IN_PROGRESS' ? '继续作答' : '查看结果'}
                        </Button>
                      </Space>
                    ),
                  },
                ]}
              />
            ),
          },
          {
            key: 'score',
            label: '我的成绩',
            children: (
              <Table<ScoreRow>
                rowKey="key"
                loading={loading}
                dataSource={scores}
                pagination={{
                  current: scorePage,
                  total: scoreTotal,
                  pageSize: 10,
                  onChange: (page) => void loadScores(page),
                }}
                columns={[
                  { title: '考试', dataIndex: 'examTitle' },
                  {
                    title: '版本',
                    dataIndex: 'versionNo',
                    width: 90,
                    render: (value: number) => <Tag>v{value}</Tag>,
                  },
                  { title: '客观分', dataIndex: 'objectiveScore', width: 100 },
                  { title: '主观分', dataIndex: 'subjectiveScore', width: 100 },
                  { title: '总分', dataIndex: 'totalScore', width: 100 },
                  {
                    title: '是否及格',
                    dataIndex: 'passed',
                    width: 110,
                    render: (value: boolean) => (
                      <Tag color={value ? 'green' : 'red'}>{value ? '及格' : '不及格'}</Tag>
                    ),
                  },
                  { title: '判分时间', dataIndex: 'gradedAt', width: 180 },
                  { title: '备注', dataIndex: 'remark' },
                ]}
              />
            ),
          },
        ]}
      />
      <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
        成绩按版本留存：人工判分或重判后会生成新版本，历史版本仍可追溯。
      </Typography.Paragraph>
    </div>
  );
}
