// 课程详情页：章节 / 课件树维护 + 报名 + 进度上报。
//
// 发布态课程内容冻结（后端强校验），界面在 PUBLISHED 态隐藏编辑入口。

import { Button, Card, Collapse, Empty, Form, Input, Modal, Popconfirm, Progress, Select, Space, Tag, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  addChapter,
  addWare,
  changeCourseStatus,
  courseDetail,
  enroll,
  removeChapter,
  removeWare,
  reportProgress,
  type CourseDetail,
} from '../api/course';
import { getCurrentUser, hasPermission } from '../api/client';

/** 状态展示映射 */
const STATUS_TAG: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PUBLISHED: { color: 'green', label: '已发布' },
  ARCHIVED: { color: 'orange', label: '已归档' },
};

export default function CourseDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<CourseDetail | null>(null);
  const [chapterForm] = Form.useForm();
  const [wareForm] = Form.useForm();
  const [chapterOpen, setChapterOpen] = useState(false);
  const [wareTarget, setWareTarget] = useState<number | null>(null);

  const canChapter = hasPermission('sys:lms:chapter:manage');
  const canWare = hasPermission('sys:lms:ware:manage');
  const canEnroll = hasPermission('sys:lms:learning:enroll');
  const canProgress = hasPermission('sys:lms:learning:progress');
  const canStatus = hasPermission('sys:lms:course:status');

  const load = useCallback(async () => {
    if (!id) {
      return;
    }
    setDetail(await courseDetail(id));
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  const editable = detail ? detail.course.status === 'DRAFT' : false;

  const onAddChapter = async (values: { title: string; remark?: string }) => {
    await addChapter(Number(id), values);
    message.success('章节已新增');
    setChapterOpen(false);
    chapterForm.resetFields();
    await load();
  };

  const onAddWare = async (values: { name: string; wareType: string; fileRefId?: string; linkUrl?: string }) => {
    if (!wareTarget) {
      return;
    }
    await addWare(wareTarget, values);
    message.success('课件已新增');
    setWareTarget(null);
    wareForm.resetFields();
    await load();
  };

  const onEnroll = async () => {
    const view = await enroll(Number(id));
    message.success(`报名成功，整体进度 ${view.overallPercent ?? 0}%`);
    await load();
  };

  const onReport = async (chapterId: number) => {
    await reportProgress(Number(id), chapterId, 100);
    message.success('进度已上报（只增不减）');
    await load();
  };

  if (!detail) {
    return <Card loading />;
  }

  const { course } = detail;

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Card
        title={
          <Space>
            <span>{course.title}</span>
            <Tag color={STATUS_TAG[course.status]?.color}>{STATUS_TAG[course.status]?.label}</Tag>
          </Space>
        }
        extra={
          <Space>
            {canStatus && course.status === 'DRAFT' && (
              <Popconfirm
                title="确认发布？发布前需至少 1 个章节"
                onConfirm={async () => {
                  await changeCourseStatus(course.id, 'PUBLISH');
                  message.success('课程已发布');
                  await load();
                }}
              >
                <Button size="small" type="primary">
                  发布
                </Button>
              </Popconfirm>
            )}
            {canStatus && course.status === 'PUBLISHED' && (
              <Popconfirm
                title="确认归档下架？"
                onConfirm={async () => {
                  await changeCourseStatus(course.id, 'ARCHIVE');
                  message.success('课程已归档');
                  await load();
                }}
              >
                <Button size="small">归档</Button>
              </Popconfirm>
            )}
            {canEnroll && course.status === 'PUBLISHED' && (
              <Button size="small" type="primary" onClick={() => void onEnroll()}>
                报名学习
              </Button>
            )}
            <Button size="small" onClick={() => navigate('/course')}>
              返回
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" size={4}>
          <span>编码：{course.courseCode}</span>
          {course.category && <span>分类：{course.category}</span>}
          {course.lecturer && <span>讲师：{course.lecturer}</span>}
          {course.description && <span>简介：{course.description}</span>}
        </Space>
      </Card>

      <Card
        title={`章节课件（${detail.chapterCount}）`}
        extra={
          canChapter && editable && (
            <Button size="small" type="primary" onClick={() => setChapterOpen(true)}>
              新增章节
            </Button>
          )
        }
      >
        {detail.chapters.length === 0 ? (
          <Empty description="暂无章节（发布前至少配置 1 个章节）" />
        ) : (
          <Collapse
            items={detail.chapters.map((chapter) => ({
              key: String(chapter.id),
              label: (
                <Space>
                  <span>
                    {chapter.sortNo}. {chapter.title}
                  </span>
                  {canProgress && course.status === 'PUBLISHED' && (
                    <Button
                      size="small"
                      type="link"
                      onClick={(event) => {
                        event.stopPropagation();
                        void onReport(chapter.id);
                      }}
                    >
                      完成 100%
                    </Button>
                  )}
                  {canChapter && editable && (
                    <Popconfirm
                      title="确认删除章节及其课件？"
                      onConfirm={async () => {
                        await removeChapter(chapter.id);
                        message.success('章节已删除');
                        await load();
                      }}
                    >
                      <Button
                        size="small"
                        type="link"
                        danger
                        onClick={(event) => event.stopPropagation()}
                      >
                        删除章节
                      </Button>
                    </Popconfirm>
                  )}
                </Space>
              ),
              children: (
                <Space direction="vertical" style={{ width: '100%' }} size={8}>
                  {chapter.wares.length === 0 && <Empty description="暂无课件" image={Empty.PRESENTED_IMAGE_SIMPLE} />}
                  {chapter.wares.map((ware) => (
                    <Space key={ware.id} style={{ width: '100%', justifyContent: 'space-between' }}>
                      <Space size={8}>
                        <Tag>{ware.wareType}</Tag>
                        <span>{ware.name}</span>
                        {ware.linkUrl && (
                          <a href={ware.linkUrl} target="_blank" rel="noreferrer">
                            打开外链
                          </a>
                        )}
                        {ware.fileRefId && <span style={{ color: '#999' }}>文件引用：{ware.fileRefId}</span>}
                      </Space>
                      {canWare && editable && (
                        <Popconfirm
                          title="确认删除课件？"
                          onConfirm={async () => {
                            await removeWare(ware.id);
                            message.success('课件已删除');
                            await load();
                          }}
                        >
                          <Button size="small" type="link" danger>
                            删除
                          </Button>
                        </Popconfirm>
                      )}
                    </Space>
                  ))}
                  {canWare && editable && (
                    <Button size="small" onClick={() => setWareTarget(chapter.id)}>
                      新增课件
                    </Button>
                  )}
                </Space>
              ),
            }))}
          />
        )}
      </Card>

      <Modal
        title="新增章节"
        open={chapterOpen}
        onCancel={() => setChapterOpen(false)}
        onOk={() => chapterForm.submit()}
      >
        <Form form={chapterForm} layout="vertical" onFinish={onAddChapter}>
          <Form.Item name="title" label="章节标题" rules={[{ required: true, message: '请输入章节标题' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新增课件"
        open={wareTarget !== null}
        onCancel={() => setWareTarget(null)}
        onOk={() => wareForm.submit()}
      >
        <Form form={wareForm} layout="vertical" onFinish={onAddWare}>
          <Form.Item name="name" label="课件名称" rules={[{ required: true, message: '请输入课件名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="wareType" label="类型" rules={[{ required: true, message: '请选择类型' }]}>
            <Select
              options={[
                { value: 'DOCUMENT', label: '文档' },
                { value: 'VIDEO', label: '视频' },
                { value: 'LINK', label: '外链' },
              ]}
              onChange={() => wareForm.setFieldsValue({ fileRefId: undefined, linkUrl: undefined })}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.wareType !== next.wareType}>
            {({ getFieldValue }) =>
              getFieldValue('wareType') === 'LINK' ? (
                <Form.Item name="linkUrl" label="外部链接" rules={[{ required: true, message: '请输入 URL' }]}>
                  <Input placeholder="https://" />
                </Form.Item>
              ) : (
                <Form.Item name="fileRefId" label="whale-file 文件引用 ID" rules={[{ required: true, message: '请输入文件引用 ID' }]}>
                  <Input placeholder="先经 whale-file 上传取得引用 ID（M1 暂手填，M5 接上传控件）" />
                </Form.Item>
              )
            }
          </Form.Item>
        </Form>
      </Modal>

      {/* 报名后可在「我的学习」页查看整体进度（当前用户即学员） */}
      {course.status === 'PUBLISHED' && getCurrentUser() && (
        <Card size="small" title="整体进度">
          <Progress percent={0} status="normal" />
          <span style={{ color: '#999' }}>报名后进入「我的学习」查看章节进度明细。</span>
        </Card>
      )}
    </Space>
  );
}
