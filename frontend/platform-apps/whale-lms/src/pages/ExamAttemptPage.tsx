// 作答页：倒计时 / 逐题作答 / 保存 / 交卷，交卷后就地展示成绩。
//
// 题序完全按服务端返回的 questions 顺序渲染，前端不排序也不分页：
// 服务端已按作答 seed 派生好呈现次序，再排一次会让「刷新题序变化」这种
// 看起来像作弊的现象出现。
//
// 倒计时归零自动交卷一次：服务端对超时交卷仍按截止时刻记账（见 ExamAttempt#submit），
// 所以这里不需要在前端做「超时禁止交卷」的拦截。

import { Alert, Button, Card, Checkbox, Descriptions, Empty, Input, Progress, Radio, Result, Space, Spin, Tag, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  attemptPaper,
  attemptResult,
  saveAnswers,
  submitAttempt,
  type AttemptPaper,
  type AttemptResult,
} from '../api/exam';

/** 题型展示 */
const TYPE_LABEL: Record<string, string> = {
  SINGLE: '单选题',
  MULTIPLE: '多选题',
  JUDGE: '判断题',
  SHORT_ANSWER: '简答题',
};

/** 判断题选项 */
const JUDGE_OPTIONS = [
  { value: 'TRUE', label: '正确' },
  { value: 'FALSE', label: '错误' },
];

/**
 * 秒数格式化为 mm:ss。
 *
 * @param seconds 剩余秒数
 * @returns 形如 59:30 的文本
 */
function formatRemaining(seconds: number): string {
  const safe = Math.max(seconds, 0);
  const minutes = Math.floor(safe / 60);
  const rest = safe % 60;
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`;
}

export default function ExamAttemptPage() {
  const { attemptId = '' } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [paper, setPaper] = useState<AttemptPaper | null>(null);
  const [result, setResult] = useState<AttemptResult | null>(null);
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [remaining, setRemaining] = useState(0);
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const submittedRef = useRef(false);
  // 交卷逻辑依赖 answers，若直接进 setInterval 的依赖数组，学员每敲一个字都会重建定时器，
  // 持续作答时倒计时将永远不推进；因此用 ref 持有最新版本，定时器只依赖 paper。
  const submitRef = useRef<() => Promise<void>>(async () => undefined);

  const loadResult = useCallback(async () => {
    const data = await attemptResult(attemptId);
    setResult(data);
  }, [attemptId]);

  useEffect(() => {
    let alive = true;
    void (async () => {
      try {
        const data = await attemptPaper(attemptId);
        if (!alive) {
          return;
        }
        setPaper(data);
        setRemaining(data.remainingSeconds ?? 0);
        const initial: Record<number, string> = {};
        data.questions.forEach((item) => {
          initial[item.questionId] = item.answer ?? '';
        });
        setAnswers(initial);
        if (data.status !== 'IN_PROGRESS') {
          await loadResult();
        }
      } finally {
        if (alive) {
          setLoading(false);
        }
      }
    })();
    return () => {
      alive = false;
    };
  }, [attemptId, loadResult]);

  const doSubmit = useCallback(async () => {
    if (submittedRef.current) {
      return;
    }
    submittedRef.current = true;
    setSubmitting(true);
    try {
      const payload = Object.entries(answers).map(([questionId, answer]) => ({
        questionId: Number(questionId),
        answer,
      }));
      await saveAnswers(attemptId, { answers: payload });
      const data = await submitAttempt(attemptId);
      setResult(data);
      setPaper((prev) => (prev ? { ...prev, status: data.status } : prev));
      message.success('已交卷');
    } finally {
      setSubmitting(false);
    }
  }, [answers, attemptId]);

  useEffect(() => {
    submitRef.current = doSubmit;
  }, [doSubmit]);

  useEffect(() => {
    if (!paper || paper.status !== 'IN_PROGRESS') {
      return undefined;
    }
    const timer = window.setInterval(() => {
      setRemaining((prev) => {
        if (prev <= 1) {
          window.clearInterval(timer);
          if (!submittedRef.current) {
            void submitRef.current();
          }
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => window.clearInterval(timer);
  }, [paper]);

  const progressPercent = useMemo(() => {
    const total = paper?.questions.length ?? 0;
    if (total === 0) {
      return 0;
    }
    const filled = (paper?.questions ?? []).filter((item) => (answers[item.questionId] ?? '').trim() !== '').length;
    return Math.round((filled / total) * 100);
  }, [answers, paper]);

  const onSave = async () => {
    setSaving(true);
    try {
      const payload = Object.entries(answers).map(([questionId, answer]) => ({
        questionId: Number(questionId),
        answer,
      }));
      const saved = await saveAnswers(attemptId, { answers: payload });
      message.success(`已保存 ${saved} 题`);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: 60, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }

  if (!paper) {
    return <Empty description="作答记录不存在或无权访问" />;
  }

  if (result) {
    return (
      <div style={{ maxWidth: 960, margin: '0 auto' }}>
        <Result
          status={result.passed ? 'success' : 'warning'}
          title={result.passed ? '已通过' : '未通过'}
          subTitle={
            result.pendingGrade
              ? '客观题已判分，主观题待教师判分，最终成绩可能变化'
              : `本次得分 ${result.totalScore ?? 0} / 及格线 ${result.passScore ?? 0}（成绩版本 v${result.scoreVersion ?? 1}）`
          }
          extra={[
            <Button key="back" onClick={() => navigate('/exam')}>
              返回考试列表
            </Button>,
            <Button key="mine" type="primary" onClick={() => navigate('/my-exam')}>
              我的考试
            </Button>,
          ]}
        />
        <Card title="答题明细" size="small">
          {result.questions.map((item, index) => (
            <Card key={item.questionId} size="small" style={{ marginBottom: 8 }} type="inner">
              <Space style={{ marginBottom: 6 }}>
                <Tag>{TYPE_LABEL[item.type] ?? item.type}</Tag>
                <Typography.Text strong>
                  {index + 1}. {item.stem}
                </Typography.Text>
              </Space>
              <Descriptions column={1} size="small">
                <Descriptions.Item label="我的作答">{item.answer || '（未作答）'}</Descriptions.Item>
                <Descriptions.Item label="得分">
                  {item.graded ? `${item.score ?? 0} / ${item.maxScore}` : `待判分（满分 ${item.maxScore}）`}
                </Descriptions.Item>
              </Descriptions>
              {item.graded && item.correct !== undefined && item.correct !== null && (
                <Tag color={item.correct ? 'green' : 'red'}>{item.correct ? '正确' : '错误'}</Tag>
              )}
            </Card>
          ))}
        </Card>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 960, margin: '0 auto' }}>
      <Card
        size="small"
        style={{ marginBottom: 12 }}
        title={paper.examTitle ?? '考试作答'}
        extra={
          <Space>
            <Tag color={remaining <= 300 ? 'red' : 'blue'}>剩余 {formatRemaining(remaining)}</Tag>
            <Button size="small" onClick={() => void onSave()} loading={saving}>
              保存
            </Button>
            <Button size="small" type="primary" loading={submitting} onClick={() => void doSubmit()}>
              交卷
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Progress percent={progressPercent} size="small" format={(value) => `已答 ${value}%`} />
          {paper.shuffle && <Alert type="info" showIcon message="本场考试题序已按你的作答随机排列，交卷后判分与题序无关" />}
        </Space>
      </Card>

      {paper.questions.map((item, index) => (
        <Card key={item.questionId} size="small" style={{ marginBottom: 8 }}>
          <Space style={{ marginBottom: 8 }} align="start">
            <Tag>{TYPE_LABEL[item.type] ?? item.type}</Tag>
            <Typography.Text strong>
              {index + 1}. {item.stem}
            </Typography.Text>
            <Typography.Text type="secondary">（{item.score} 分）</Typography.Text>
          </Space>
          {item.type === 'SHORT_ANSWER' && (
            <Input.TextArea
              rows={4}
              maxLength={2000}
              placeholder="请输入作答"
              value={answers[item.questionId] ?? ''}
              onChange={(event) => setAnswers((prev) => ({ ...prev, [item.questionId]: event.target.value }))}
            />
          )}
          {item.type === 'MULTIPLE' && (
            <Checkbox.Group
              value={(answers[item.questionId] ?? '').split(',').filter(Boolean)}
              onChange={(values) => setAnswers((prev) => ({ ...prev, [item.questionId]: values.join(',') }))}
              options={item.options.map((option) => ({ value: option.optionKey, label: `${option.optionKey}. ${option.content}` }))}
            />
          )}
          {item.type === 'SINGLE' && (
            <Radio.Group
              value={answers[item.questionId] ?? ''}
              onChange={(event) => setAnswers((prev) => ({ ...prev, [item.questionId]: event.target.value }))}
              options={item.options.map((option) => ({ value: option.optionKey, label: `${option.optionKey}. ${option.content}` }))}
            />
          )}
          {item.type === 'JUDGE' && (
            <Radio.Group
              value={answers[item.questionId] ?? ''}
              onChange={(event) => setAnswers((prev) => ({ ...prev, [item.questionId]: event.target.value }))}
              options={JUDGE_OPTIONS}
            />
          )}
        </Card>
      ))}
    </div>
  );
}
