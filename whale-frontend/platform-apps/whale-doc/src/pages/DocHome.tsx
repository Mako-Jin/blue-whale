// 文档中心首页：M1 骨架阶段展示服务连通性与功能开关，后续接入空间列表与工作台

import { useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Space, Spin, Tag, Typography } from 'antd';
import { docApi, type DocMeta } from '../api/doc';
import { hasPermission } from '../api/client';

/** 字节数转可读文案 */
function formatBytes(bytes: number): string {
  if (bytes <= 0) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / 1024 ** index).toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

export default function DocHome() {
  const [meta, setMeta] = useState<DocMeta | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    docApi
      .meta()
      .then((result) => {
        if (active) {
          setMeta(result);
          setError(null);
        }
      })
      .catch((reason: Error) => {
        if (active) {
          setError(reason.message);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <div style={{ padding: 24, maxWidth: 960, margin: '0 auto' }}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div>
          <Typography.Title level={3} style={{ marginBottom: 4 }}>
            文档中心
          </Typography.Title>
          <Typography.Text type="secondary">
            团队知识库与在线文档。M1 骨架已就绪，空间、页面树与编辑器正在接入。
          </Typography.Text>
        </div>

        {error ? (
          <Alert
            type="warning"
            showIcon
            message="文档服务暂不可用"
            description={`${error}。请确认 whale-doc（8095）已启动，且当前账号拥有 sys:doc:space:list 权限。`}
          />
        ) : null}

        <Card
          title="服务状态"
          extra={
            meta ? (
              <Tag color={meta.collabEnabled ? 'geekblue' : 'default'}>
                {meta.collabEnabled ? '实时协作已开启' : '实时协作 M2 开启'}
              </Tag>
            ) : null
          }
        >
          <Spin spinning={loading}>
            {meta ? (
              <Descriptions
                column={{ xs: 1, sm: 2 }}
                size="small"
                items={[
                  { key: 'service', label: '服务', children: meta.service },
                  { key: 'tenant', label: '租户', children: meta.tenantId },
                  { key: 'user', label: '当前用户', children: meta.userId ?? '-' },
                  {
                    key: 'approval',
                    label: '发布审批',
                    children: meta.approvalEnabled ? '已开启' : 'M3 开启',
                  },
                  {
                    key: 'content',
                    label: '正文上限',
                    children: formatBytes(meta.contentMaxBytes),
                  },
                  {
                    key: 'attachment',
                    label: '附件上限',
                    children: formatBytes(meta.attachmentMaxBytes),
                  },
                  { key: 'depth', label: '树最大层级', children: meta.maxDepth },
                  {
                    key: 'text',
                    label: '纯文本投影上限',
                    children: formatBytes(meta.contentTextMaxBytes),
                  },
                ]}
              />
            ) : (
              <Typography.Text type="secondary">
                {loading ? '正在读取服务信息…' : '未获取到服务信息'}
              </Typography.Text>
            )}
          </Spin>
        </Card>

        <Card title="即将上线">
          <Space direction="vertical" size={8}>
            <Typography.Text>· 文档空间列表与新建空间（B1-2 / F1-3）</Typography.Text>
            <Typography.Text>· 空间工作台与页面树（B1-3 / F1-4）</Typography.Text>
            <Typography.Text>
              · TipTap 编辑器、自动保存与版本冲突提示（B1-4 / F1-5 / F1-6）
            </Typography.Text>
            <Typography.Text>· 评论、附件、搜索与收藏（B1-5 ~ B1-7 / F1-8）</Typography.Text>
          </Space>
          <div style={{ marginTop: 16 }}>
            <Space>
              <Button type="primary" disabled={!hasPermission('sys:doc:space:add')}>
                新建空间
              </Button>
              <Typography.Text type="secondary">
                {hasPermission('sys:doc:space:add')
                  ? '空间创建接口将在 M1 下一阶段接入'
                  : '当前账号无创建空间权限'}
              </Typography.Text>
            </Space>
          </div>
        </Card>
      </Space>
    </div>
  );
}
