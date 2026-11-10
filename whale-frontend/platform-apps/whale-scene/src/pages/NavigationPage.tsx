// 城池导航：默认 2D 地图，可切换键盘友好的节点列表视图
//
// 数据来自 `/scene/navigation`（登录即可），服务端已按可见性过滤节点与入口；
// 前端不缓存场景结构。顶部提供 3D 入口，3D 页按需加载 three.js。

import { Alert, App, Button, Card, Empty, Radio, Select, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchNavigation, fetchSceneMeta, type NavigationSceneView, type SceneMeta } from '../api/scene';
import SceneMap2D, { SceneNavigationList } from '../components/SceneMap2D';

type ViewMode = 'map' | 'list';

export default function NavigationPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [scenes, setScenes] = useState<NavigationSceneView[]>([]);
  const [activeKey, setActiveKey] = useState<string>();
  const [meta, setMeta] = useState<SceneMeta | null>(null);
  const [mode, setMode] = useState<ViewMode>('map');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    fetchNavigation()
      .then((data) => {
        setScenes(data);
        setActiveKey((current) => current ?? data[0]?.sceneKey);
        setError(null);
      })
      .catch((reason: unknown) => {
        const text = reason instanceof Error ? reason.message : '场景导航加载失败';
        setError(text);
        message.error(text);
      })
      .finally(() => setLoading(false));
    fetchSceneMeta()
      .then(setMeta)
      .catch(() => {
        // 能力参数只影响提示文案，加载失败不阻断导航
      });
  }, [message]);

  const activeScene = scenes.find((scene) => scene.sceneKey === activeKey) ?? scenes[0];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space align="center" wrap>
        <Typography.Title level={3} style={{ margin: 0 }}>
          城池导航
        </Typography.Title>
        <Tag color="blue">2D 优先</Tag>
        <Button type="primary" onClick={() => navigate('/3d')}>
          切换到 3D
        </Button>
        {meta ? (
          <Typography.Text type="secondary">
            节点上限 {meta.maxNodesPerScene} / 层级上限 {meta.maxNodeDepth}
          </Typography.Text>
        ) : null}
      </Space>

      {meta && !meta.threeDEnabled ? (
        <Alert
          type="info"
          showIcon
          message="当前默认 2D 导航，可切换到 3D 预览"
          description="2D 仍是默认生产力视图；3D 目前用于接入验证，仅渲染一个测试正方体，场景选择会保存为个人偏好。"
        />
      ) : null}

      {error ? <Alert type="warning" showIcon message={error} /> : null}

      {scenes.length === 0 && !loading ? (
        <Empty description="暂无已发布的场景，或当前账号没有可见场景" />
      ) : (
        <Card
          title={
            <Space wrap>
              <span>场景</span>
              <Select
                aria-label="选择场景"
                style={{ minWidth: 220 }}
                value={activeScene?.sceneKey}
                onChange={setActiveKey}
                options={scenes.map((scene) => ({
                  label: `${scene.name}（${scene.sceneKey}）`,
                  value: scene.sceneKey,
                }))}
                placeholder="选择场景"
              />
            </Space>
          }
          extra={
            <Radio.Group
              value={mode}
              onChange={(event) => setMode(event.target.value as ViewMode)}
              optionType="button"
              buttonStyle="solid"
              aria-label="切换地图或列表视图"
            >
              <Radio.Button value="map">地图</Radio.Button>
              <Radio.Button value="list">列表</Radio.Button>
            </Radio.Group>
          }
        >
          {loading ? (
            <Spin tip="加载场景导航…">
              <div style={{ height: 360 }} />
            </Spin>
          ) : activeScene ? (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Typography.Text type="secondary">
                {activeScene.name} · 布局版本 {activeScene.layoutVersion ?? '尚未发布'} · 可见节点{' '}
                {activeScene.nodes?.length ?? 0} 个
              </Typography.Text>
              {mode === 'map' ? (
                <SceneMap2D scene={activeScene} />
              ) : (
                <SceneNavigationList scene={activeScene} />
              )}
            </Space>
          ) : (
            <Empty description="暂无可展示场景" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>
      )}
    </Space>
  );
}
