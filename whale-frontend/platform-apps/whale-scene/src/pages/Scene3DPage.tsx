// 3D 城池视图（M1：接入制造看板实时数据）
//
// - 入口：2D 导航页「切换到 3D」按钮
// - 场景选择：可选项来自 `/scene/v1/navigation`（当前用户可见的已发布场景）
// - 个人设置：选择结果写入 `/scene/v1/preference`，下次进入优先恢复
// - 制造数据：`/scene/v1/board/manufacturing`（ERP 生产订单 / 库存余额同源），
//   数据源不可用时显式降级提示，不渲染任何假数量

import { Alert, App, Button, Card, Empty, Select, Space, Spin, Table, Tag, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BoxGeometry, Group, Mesh, MeshStandardMaterial } from 'three';
import { WhaleSceneManager } from '@bluewhale/whale-common-three';
import {
  fetchManufacturingBoard,
  fetchNavigation,
  fetchScenePreference,
  saveScenePreference,
  type MfgBoardView,
  type NavigationSceneView,
} from '../api/scene';

export default function Scene3DPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const managerRef = useRef<WhaleSceneManager | null>(null);
  const [scenes, setScenes] = useState<NavigationSceneView[]>([]);
  const [activeKey, setActiveKey] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [webglError, setWebglError] = useState<string | null>(null);
  const [board, setBoard] = useState<MfgBoardView | null>(null);
  const [boardError, setBoardError] = useState<string | null>(null);

  // three.js 初始化：页面挂载一次，业务柱体由制造数据驱动（见下一个 effect）
  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    let manager: WhaleSceneManager | null = null;
    try {
      manager = new WhaleSceneManager({ container, backgroundColor: 0x0f1b2d }).init();
      manager.getCamera().position.set(0, 0.4, 8);
      manager.getCamera().lookAt(0, 0, 0);
      manager.start();
      managerRef.current = manager;
    } catch (reason) {
      setWebglError(reason instanceof Error ? reason.message : 'WebGL 初始化失败');
    }
    return () => {
      managerRef.current = null;
      manager?.dispose();
    };
  }, []);

  // 制造看板数据：独立加载，失败只提示，不影响场景导航
  useEffect(() => {
    let cancelled = false;
    fetchManufacturingBoard()
      .then((data) => {
        if (!cancelled) {
          setBoard(data);
          setBoardError(null);
        }
      })
      .catch((reason: unknown) => {
        if (!cancelled) {
          setBoardError(reason instanceof Error ? reason.message : '制造看板数据加载失败');
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // 数据驱动柱体：高度按完工数量占比，颜色区分终态；数据降级时不渲染任何柱体
  useEffect(() => {
    const manager = managerRef.current;
    if (!manager || !board || board.degraded) {
      return;
    }
    const scene = manager.getScene();
    const group = new Group();
    const orders = board.orders.slice(0, 12);
    const maxPlanned = Math.max(...orders.map((order) => Number(order.plannedQuantity) || 0), 1);
    orders.forEach((order, index) => {
      const completed = Number(order.completedQuantity) || 0;
      const height = 0.3 + Math.min(completed / maxPlanned, 1) * 3.2;
      const bar = new Mesh(
        new BoxGeometry(0.6, height, 0.6),
        new MeshStandardMaterial({ color: order.status === 'COMPLETED' ? 0x35c27a : 0x2f8fdc }),
      );
      bar.position.set((index - (orders.length - 1) / 2) * 0.9, height / 2 - 1.2, 0);
      group.add(bar);
    });
    scene.add(group);
    return () => {
      scene.remove(group);
      group.children.forEach((child) => {
        const bar = child as Mesh<BoxGeometry, MeshStandardMaterial>;
        bar.geometry.dispose();
        bar.material.dispose();
      });
    };
  }, [board]);

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
        <Tag color={board?.degraded ? 'orange' : 'green'}>
          {board?.degraded ? '数据降级' : '制造实时数据'}
        </Tag>
        <Button onClick={backTo2D}>返回 2D</Button>
      </Space>

      <Alert
        type="info"
        showIcon
        message="3D 看板已接入制造实时数据"
        description="柱体高度按生产订单完工数量占比渲染，数量来自 ERP 生产订单与库存余额；数据源不可用时页面会显式降级，不显示任何假数据。"
      />

      {webglError ? (
        <Alert
          type="warning"
          showIcon
          message="当前环境无法初始化 WebGL"
          description={webglError}
        />
      ) : null}
      {error ? <Alert type="warning" showIcon message={error} /> : null}
      {board?.degraded ? (
        <Alert
          type="warning"
          showIcon
          message="制造数据源不可用，已降级"
          description={board.message ?? 'ERP 未响应或未授权，页面不展示产量数字'}
        />
      ) : null}
      {boardError ? <Alert type="warning" showIcon message={boardError} /> : null}

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
              布局版本 {activeScene.layoutVersion ?? '尚未发布'} · 可见节点{' '}
              {activeScene.nodes?.length ?? 0} 个
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

      <Card
        size="small"
        title="制造实时数量"
        extra={
          <Typography.Text type="secondary">
            数据时间 {board?.generatedAt ? board.generatedAt.replace('T', ' ') : '—'}
          </Typography.Text>
        }
      >
        {board && !board.degraded ? (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Table
              size="small"
              rowKey="orderNo"
              pagination={false}
              dataSource={board.orders}
              locale={{ emptyText: '暂无生产订单' }}
              columns={[
                { title: '生产订单', dataIndex: 'orderNo' },
                { title: '物料', dataIndex: 'itemCode' },
                { title: '计划', dataIndex: 'plannedQuantity' },
                { title: '完工', dataIndex: 'completedQuantity' },
                {
                  title: '状态',
                  dataIndex: 'status',
                  render: (value: string) => <Tag>{value}</Tag>,
                },
              ]}
            />
            <Table
              size="small"
              rowKey={(row) => `${row.warehouseId}-${row.itemCode}`}
              pagination={false}
              dataSource={board.stocks}
              locale={{ emptyText: '暂无库存余额' }}
              columns={[
                { title: '物料', dataIndex: 'itemCode' },
                { title: '仓库ID', dataIndex: 'warehouseId' },
                { title: '现货数量', dataIndex: 'quantity' },
              ]}
            />
          </Space>
        ) : (
          <Empty description="制造数据源不可用，暂不展示数量" />
        )}
      </Card>
    </Space>
  );
}
