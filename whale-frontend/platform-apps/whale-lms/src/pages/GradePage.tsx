// 判分页：待判队列 + 判分抽屉。
//
// 只有主观题可填分：客观题在交卷时已按标准答案判完，界面把它标成「自动判分」
// 并禁用输入，避免教师误以为可以改分（后端也会拒绝并返回 42049）。
// 每次提交都会生成新的成绩版本，重判同样走这里，备注里写清原因即可。

import {
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Input,
  InputNumber,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { hasPermission } from '../api/client';
import {
  gradeDetail,
  pendingGrades,
  submitGrade,
  type AttemptItem,
  type GradeDetail,
} from '../api/exam';

/** 列表行 */
interface Row extends AttemptItem {
  key: string;
}

/** 题型展示 */
const TYPE_LABEL: Record<string, string> = {
  SINGLE: '单选题',
  MULTIPLE: '多选题',
  JUDGE: '判断题',
  SHORT_ANSWER: '简答题',
};

export default function GradePage() {
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<Row[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [detail, setDetail] = useState<GradeDetail | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [scores, setScores] = useState<Record<number, number>>({});
  const [remark, setRemark] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const canGrade = hasPermission('sys:lms:grade:submit');

  const load = useCallback(
    async (page = current) => {
      setLoading(true);
      try {
        const data = await pendingGrades({ current: page, size: 10 });
        setRows((data.records ?? []).map((item) => ({ ...item, key: String(item.id) })));
        setTotal(data.total ?? 0);
        setCurrent(page);
      } finally {
        setLoading(false);
      }
    },
    [current],
  );

  useEffect(() => {
    void load(1);
    // 首次加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openDetail = async (row: Row) => {
    const data = await gradeDetail(row.id);
    setDetail(data);
    const initial: Record<number, number> = {};
    data.answers.forEach((item) => {
      if (!item.objective) {
        initial[item.questionId] = item.score ?? 0;
      }
    });
    setScores(initial);
    setRemark('');
    setDetailOpen(true);
  };

  const onSubmit = async () => {
    if (!detail) {
      return;
    }
    const items = Object.entries(scores).map(([questionId, score]) => ({
      questionId: Number(questionId),
      score,
    }));
    if (items.length === 0) {
      message.warning('没有需要判分的主观题');
      return;
    }
    setSubmitting(true);
    try {
      await submitGrade(detail.attemptId, { items, remark: remark || undefined });
      message.success('判分已提交，已生成新的成绩版本');
      setDetailOpen(false);
      await load(current);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Space style={{ marginBottom: 12 }}>
        <Typography.Title level={5} style={{ margin: 0 }}>
          待判分（已交卷且存在主观题）
        </Typography.Title>
        <Button size="small" onClick={() => void load(current)}>
          刷新
        </Button>
      </Space>

      <Table<Row>
        rowKey="key"
        loading={loading}
        dataSource={rows}
        pagination={{ current, total, pageSize: 10, onChange: (page) => void load(page) }}
        columns={[
          { title: '学员', dataIndex: 'username', width: 140 },
          { title: '考试', dataIndex: 'examTitle' },
          { title: '第几次', dataIndex: 'attemptNo', width: 90 },
          { title: '交卷时间', dataIndex: 'submittedAt', width: 180 },
          { title: '切屏', dataIndex: 'switchCount', width: 80 },
          { title: '客观分', dataIndex: 'objectiveScore', width: 90 },
          {
            title: '待判',
            dataIndex: 'pendingGradeCount',
            width: 90,
            render: (value: number) => (
              <Tag color={value > 0 ? 'orange' : 'green'}>{value ?? 0} 题</Tag>
            ),
          },
          {
            title: '操作',
            width: 120,
            render: (_, row) => (
              <Button
                size="small"
                type="link"
                disabled={!canGrade}
                onClick={() => void openDetail(row)}
              >
                判分
              </Button>
            ),
          },
        ]}
      />

      <Drawer
        title={detail ? `判分 · ${detail.username ?? ''}` : '判分'}
        width={720}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setDetailOpen(false)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={() => void onSubmit()}>
              提交判分
            </Button>
          </Space>
        }
      >
        {!detail ? (
          <Empty />
        ) : (
          <>
            <Descriptions column={2} size="small" style={{ marginBottom: 12 }}>
              <Descriptions.Item label="考试">{detail.examTitle}</Descriptions.Item>
              <Descriptions.Item label="第几次作答">{detail.attemptNo}</Descriptions.Item>
              <Descriptions.Item label="交卷时间">{detail.submittedAt}</Descriptions.Item>
              <Descriptions.Item label="切屏次数">{detail.switchCount ?? 0}</Descriptions.Item>
              <Descriptions.Item label="客观题得分">{detail.objectiveScore ?? 0}</Descriptions.Item>
              <Descriptions.Item label="待判题数">{detail.pendingGradeCount}</Descriptions.Item>
            </Descriptions>

            {detail.answers.map((item, index) => (
              <Card key={item.questionId} size="small" style={{ marginBottom: 8 }} type="inner">
                <Space style={{ marginBottom: 6 }} wrap>
                  <Tag>{TYPE_LABEL[item.type] ?? item.type}</Tag>
                  <Typography.Text strong>
                    {index + 1}. {item.stem}
                  </Typography.Text>
                  <Typography.Text type="secondary">（满分 {item.maxScore}）</Typography.Text>
                </Space>
                <Descriptions column={1} size="small">
                  <Descriptions.Item label="学员作答">
                    {item.answer || '（未作答）'}
                  </Descriptions.Item>
                  <Descriptions.Item label="标准答案">
                    {item.standardAnswer || '（主观题无标准答案）'}
                  </Descriptions.Item>
                </Descriptions>
                <Space>
                  <Typography.Text>本题得分</Typography.Text>
                  {item.objective ? (
                    <Tag color="green">自动判分 {item.score ?? 0} 分</Tag>
                  ) : (
                    <InputNumber
                      min={0}
                      max={item.maxScore}
                      step={0.5}
                      value={scores[item.questionId] ?? 0}
                      onChange={(value) =>
                        setScores((prev) => ({ ...prev, [item.questionId]: Number(value ?? 0) }))
                      }
                    />
                  )}
                </Space>
              </Card>
            ))}

            <Input.TextArea
              rows={2}
              maxLength={500}
              placeholder="判分备注（重判请写明原因，会记入成绩版本）"
              value={remark}
              onChange={(event) => setRemark(event.target.value)}
            />
          </>
        )}
      </Drawer>
    </div>
  );
}
