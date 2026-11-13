// 我的学习页：报名列表 + 章节进度明细（进度只增不减，由后端强校验）。

import { Card, Empty, Progress, Space, Table, Tag, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { myEnrollments, type EnrollmentItem } from '../api/course';

/** 列表行 */
interface Row extends EnrollmentItem {
  key: string;
}

export default function MyLearningPage() {
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<Row[]>([]);
  const [current, setCurrent] = useState(1);

  const load = useCallback(async (page = 1) => {
    setLoading(true);
    try {
      const data = await myEnrollments({ current: page, size: 10 });
      setRows((data.records ?? []).map((item) => ({ ...item, key: String(item.id) })));
      setCurrent(page);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(1);
  }, [load]);

  return (
    <Card title="我的学习">
      <Table<Row>
        rowKey="key"
        loading={loading}
        dataSource={rows}
        pagination={{
          current,
          pageSize: 10,
          total: rows.length,
          onChange: (page) => void load(page),
        }}
        expandable={{
          expandedRowRender: (row) =>
            row.chapters && row.chapters.length > 0 ? (
              <Space direction="vertical" style={{ width: '100%' }} size={8}>
                {row.chapters.map((chapter) => (
                  <Space
                    key={chapter.chapterId}
                    style={{ width: '100%', justifyContent: 'space-between' }}
                  >
                    <span>{chapter.chapterTitle}</span>
                    <Progress
                      percent={chapter.percent}
                      style={{ width: 240, marginInlineEnd: 24 }}
                    />
                  </Space>
                ))}
              </Space>
            ) : (
              <Empty description="课程暂无章节" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ),
        }}
        columns={[
          { title: '课程', dataIndex: 'courseTitle' },
          {
            title: '课程状态',
            dataIndex: 'courseStatus',
            width: 110,
            render: (status: string) => {
              const map: Record<string, string> = {
                DRAFT: '草稿',
                PUBLISHED: '已发布',
                ARCHIVED: '已归档',
              };
              return <Tag>{map[status] ?? status}</Tag>;
            },
          },
          {
            title: '整体进度',
            dataIndex: 'overallPercent',
            width: 260,
            render: (percent: number) => <Progress percent={percent ?? 0} />,
          },
          { title: '报名时间', dataIndex: 'enrolledAt', width: 180 },
          {
            title: '操作',
            width: 100,
            render: () => (
              <span
                style={{ color: '#999', fontSize: 12 }}
                onClick={() => message.info('进度只增不减：重复学习按更优值保留')}
              >
                进度说明
              </span>
            ),
          },
        ]}
      />
    </Card>
  );
}
