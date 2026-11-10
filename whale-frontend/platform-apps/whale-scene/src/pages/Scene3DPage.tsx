// 3D 城池视图（本阶段仅用于验证 three.js 接入）
//
// - 入口：2D 导航页「切换到 3D」按钮
// - 场景选择：可选项来自 `/scene/navigation`（当前用户可见的已发布场景）
// - 个人设置：选择结果写入 `/scene/preference`，下次进入优先恢复
// - 渲染：whale-common-three 初始化场景，当前只放一个旋转的正方体

import { Alert, App, Button, Card, Empty, Select, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BoxGeometry, Mesh, MeshStandardMaterial } from 'three';
import { WhaleSceneManager } from '@bluewhale/whale-common-three';
import {
  fetchNavigation,
  fetchScenePreference,
  saveScenePreference,
  type NavigationSceneView,
} from '../api/scene';

export default function Scene3DPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [scenes, setScenes] = useState<NavigationSceneView[]>([]);
  const [activeKey, setActiveKey] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [webglError, setWebglError] = useState<string | null>(null);

  // three.js 初始化：页面挂载一次，只放一个测试正方体做自转
  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    let manager: WhaleSceneManager | null = null;
    try {
      manager = new WhaleSceneManager({ container, backgroundColor: 0x0f1b2d }).init();
      const cube = new Mesh(
        new BoxGeometry(1, 1, 1),
        new MeshStandardMaterial({ color: 0x2f8fdc }),
      );
      manager.getScene().add(cube);
      manager.onFrame((delta) => {
        cube.rotation.x += delta * 0.4;
        cube.rotation.y += delta * 0.6;
      });
      manager.start();
    } catch (reason) {
      setWebglError(reason instanceof Error ? reason.message : 'WebGL 初始化失败');
    }
    return () => {
      manager?.dispose();
    };
  }, []);

  // 场景列表 + 个人偏好：偏好中的默认场景优先，失效时回退首个可见场景
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([fetchNavigation(), fetchScenePreference().catch(() => null)])
      .then(([navigationScenes, preference]) => {
        if (cancelled) {
          return;
        }
        setScenes(navigationScenes);
        const preferredKey = preference?.defaultSceneKey;
        const initialKey =
          navigationScenes.find((scene) => scene.sceneKey === preferredKey)?.sceneKey ??
          navigationScenes[0]?.sceneKey;
        setActiveKey(initialKey);
        setError(null);
      })
      .catch((reason: unknown) => {
        if (cancelled) {
          return;
        }
        const text = reason instanceof Error ? reason.message : '3D 场景加载失败';
        setError(text);
        message.error(text);
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [message]);

  const activeScene = scenes.find((scene) => scene.sceneKey === activeKey) ?? scenes[0];

  /** 切换场景并写入个人偏好（保存失败不阻断当前渲染） */
  const handleSceneChange = (sceneKey: string) => {
    setActiveKey(sceneKey);
    saveScenePreference({ viewMode: '3D', defaultSceneKey: sceneKey }).catch((reason: unknown) => {
      const text = reason instanceof Error ? reason.message : '场景偏好保存失败';
      message.warning(text);
    });
  };

  /** 返回 2D 导航，并把视图模式记回 2D */
  const backTo2D = () => {
    saveScenePreference({ viewMode: '2D', defaultSceneKey: activeScene?.sceneKey ?? null })
      .catch(() => {
        // 偏好属于增强能力，保存失败不影响返回 2D
      })
      .finally(() => navigate('/navigation'));
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space align="center" wrap>
        <Typography.Title level={3} style={{ margin: 0 }}>
          城池导航 3D
        </Typography.Title>
        <Tag color="green">3D 预览</Tag>
        <Button onClick={backTo2D}>返回 2D</Button>
      </Space>

      <Alert
        type="info"
        showIcon
        message="本阶段仅验证 3D 接入"
        description="3D 视图中目前只渲染一个测试正方体；场景选择会保存为你的个人偏好，下次进入自动恢复。"
      />

      {webglError ? (
        <Alert type="warning" showIcon message="当前环境无法初始化 WebGL" description={webglError} />
      ) : null}
      {error ? <Alert type="warning" showIcon message={error} /> : null}

      <Card
        title={
          <Space wrap>
            <span>场景</span>
            <Select
              aria-label="选择 3D 场景"
              style={{ minWidth: 220 }}
              value={activeScene?.sceneKey}
              onChange={handleSceneChange}
              options={scenes.map((scene) => ({
                label: `${scene.name}（${scene.sceneKey}）`,
                value: scene.sceneKey,
              }))}
              placeholder="选择场景"
              disabled={scenes.length === 0}
            />
          </Space>
        }
        extra={
          activeScene ? (
            <Typography.Text type="secondary">
              布局版本 {activeScene.layoutVersion ?? '尚未发布'} · 可见节点 {activeScene.nodes?.length ?? 0} 个
            </Typography.Text>
          ) : null
        }
      >
        {loading ? (
          <Spin tip="加载 3D 场景…">
            <div style={{ height: 480 }} />
          </Spin>
        ) : null}
        {!loading && scenes.length === 0 ? (
          <Empty description="暂无已发布的场景，或当前账号没有可见场景" />
        ) : null}
        <div
          ref={containerRef}
          aria-label="3D 场景画布"
          style={{
            width: '100%',
            height: 480,
            borderRadius: 8,
            overflow: 'hidden',
            display: !loading && scenes.length > 0 ? 'block' : 'none',
          }}
        />
      </Card>
    </Space>
  );
}
