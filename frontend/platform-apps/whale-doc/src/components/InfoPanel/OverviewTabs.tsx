// 信息面板基础页签：页面详情 / 标签 / 反向链接 / 动态

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Descriptions,
  Empty,
  List,
  Pagination,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { Link } from 'react-router-dom';
import { hasPermission } from '../../api/client';
import { insightApi } from '../../api/insight';
import { tagApi } from '../../api/tag';
import type { DocActivity, DocBacklink, DocPage, DocPageStatus, DocTag } from '../../api/types';
import { formatTime } from '../../utils/docContent';

const { Text } = Typography;

/** 状态文案 */
const STATUS_LABEL: Record<DocPageStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档',
  PENDING_APPROVAL: '待审批',
};

/** 动作文案 */
const ACTION_LABEL: Record<string, string> = {
  CREATE: '创建页面',
  SAVE: '保存页面',
  TITLE: '更新标题',
  PUBLISH: '发布页面',
  ARCHIVE: '归档页面',
  RESTORE: '取消归档',
  MOVE: '移动页面',
  TRASH: '移入回收站',
  UNTRASH: '恢复页面',
  COMMENT: '评论',
  TAG: '更新标签',
  ACL: '更新页面权限',
  IMPORT: '导入',
  COLLAB: '协作保存',
};

/** 页面基础信息 */
export function DetailTab({ page }: { page: DocPage }) {
  return (
    <Descriptions column={1} size="small" bordered>
      <Descriptions.Item label="标题">{page.title}</Descriptions.Item>
      <Descriptions.Item label="状态">
        <Space size={4}>
          <Tag
            color={
              page.status === 'PUBLISHED' ? 'green' : page.status === 'DRAFT' ? 'gold' : 'default'
            }
          >
            {STATUS_LABEL[page.status]}
          </Tag>
          {page.restricted && <Tag color="red">页面级权限</Tag>}
        </Space>
      </Descriptions.Item>
      <Descriptions.Item label="路径">{page.slug}</Descriptions.Item>
      <Descriptions.Item label="当前版本">r{page.revision}</Descriptions.Item>
      <Descriptions.Item label="已发布版本">
        {page.publishedRevision === null || page.publishedRevision === undefined
          ? '-'
          : `r${page.publishedRevision}`}
      </Descriptions.Item>
      <Descriptions.Item label="作者 / 最后编辑">
        {page.authorId} / {page.lastEditorId}
      </Descriptions.Item>
      <Descriptions.Item label="发布 / 归档时间">
        {`${formatTime(page.publishedAt)} / ${formatTime(page.archivedAt)}`}
      </Descriptions.Item>
      <Descriptions.Item label="创建 / 更新时间">
        {`${formatTime(page.createTime)} / ${formatTime(page.updateTime)}`}
      </Descriptions.Item>
      <Descriptions.Item label="面包屑">
        {(page.breadcrumb ?? []).map((item) => item.title).join(' / ') || page.title}
      </Descriptions.Item>
    </Descriptions>
  );
}

/** 标签页签：查看并可全量替换页面标签（M2-5） */
export function TagTab({ page }: { page: DocPage }) {
  const { message } = App.useApp();
  const [tags, setTags] = useState<DocTag[]>([]);
  const [selected, setSelected] = useState<number[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const canManage = page.canEdit && hasPermission('sys:doc:tag:manage');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [spaceTags, pageTags] = await Promise.all([
        tagApi.list(page.spaceId),
        tagApi.pageTags(page.id),
      ]);
      setTags(spaceTags);
      setSelected(pageTags.map((tag) => tag.id));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page.spaceId, page.id, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const save = async () => {
    setSaving(true);
    try {
      const result = await tagApi.updatePageTags(page.id, selected);
      setSelected(result.map((tag) => tag.id));
      message.success('页面标签已更新');
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Spin spinning={loading}>
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        {selected.length > 0 ? (
          <Space size={4} wrap>
            {selected.map((id) => {
              const tag = tags.find((item) => item.id === id);
              return tag ? (
                <Tag key={id} color={tag.color ?? 'blue'}>
                  {tag.name}
                </Tag>
              ) : null;
            })}
          </Space>
        ) : (
          <Text type="secondary">该页面暂无标签。</Text>
        )}
        {canManage ? (
          <Space.Compact style={{ width: '100%' }}>
            <Select
              mode="multiple"
              allowClear
              style={{ width: '100%' }}
              placeholder="选择空间标签（最多 20 个）"
              value={selected}
              maxCount={20}
              optionFilterProp="label"
              options={tags.map((tag) => ({
                value: tag.id,
                label: `${tag.name}${tag.pageCount ? `（${tag.pageCount}）` : ''}`,
              }))}
              onChange={(value) => setSelected(value)}
            />
            <Button
              type="primary"
              loading={saving}
              disabled={tags.length === 0}
              onClick={() => void save()}
            >
              保存
            </Button>
          </Space.Compact>
        ) : (
          <Text type="secondary">需要空间编辑者及以上并可管理标签才能修改。</Text>
        )}
      </Space>
    </Spin>
  );
}

/** 反向链接页签：列出仍可访问的引用方页面（M2-5） */
export function BacklinkTab({ page }: { page: DocPage }) {
  const { message } = App.useApp();
  const [items, setItems] = useState<DocBacklink[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await insightApi.backlinks(page.id));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page.id, message]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Spin spinning={loading}>
      <List
        dataSource={items}
        locale={{ emptyText: <Empty description="暂无其他页面引用本页" /> }}
        renderItem={(item) => (
          <List.Item>
            <List.Item.Meta
              title={<Link to={`/space/${item.spaceId}?pageId=${item.pageId}`}>{item.title}</Link>}
              description={
                <Space direction="vertical" size={2}>
                  {item.linkText && <Text type="secondary">「{item.linkText}」</Text>}
                  <Text type="secondary">
                    {item.spaceName} · /{item.slug} · 更新于 {formatTime(item.updateTime)}
                  </Text>
                </Space>
              }
            />
          </List.Item>
        )}
      />
    </Spin>
  );
}

/** 页面动态页签（M2-5） */
export function ActivityTab({ page }: { page: DocPage }) {
  const { message } = App.useApp();
  const [items, setItems] = useState<DocActivity[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await insightApi.activity(page.id, current, 20);
      setItems(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page.id, current, message]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div>
      <Spin spinning={loading}>
        <List
          dataSource={items}
          locale={{ emptyText: <Empty description="暂无页面动态" /> }}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta
                title={`${ACTION_LABEL[item.action] ?? item.action}${item.detail ? ` · ${item.detail}` : ''}`}
                description={`用户 ${item.actorId ?? '-'} · ${formatTime(item.createTime)}`}
              />
            </List.Item>
          )}
        />
      </Spin>
      {total > 20 && (
        <div style={{ textAlign: 'right', marginTop: 12 }}>
          <Pagination
            current={current}
            pageSize={20}
            total={total}
            showSizeChanger={false}
            onChange={setCurrent}
            size="small"
          />
        </div>
      )}
    </div>
  );
}
