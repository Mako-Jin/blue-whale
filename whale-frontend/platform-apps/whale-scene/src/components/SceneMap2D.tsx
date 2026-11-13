// 2D 城池地图：纯 CSS 绝对定位渲染，无 WebGL / Canvas 依赖
//
// 可访问性：每个节点都是原生 <button>，支持 Tab 聚焦与 Enter/Space 激活；
// 无坐标的历史节点按序号在网格内兜底排布（见 resolveNodePosition）。

import { Empty } from 'antd';
import type { NavigationNodeView, NavigationSceneView } from '../api/scene';
import {
  nodeGlyph,
  openNavigationEntry,
  resolveNodePosition,
  sortNodes,
} from '../utils/sceneDisplay';

export default function SceneMap2D({ scene }: { scene: NavigationSceneView }) {
  const nodes = sortNodes(scene.nodes ?? []);

  if (nodes.length === 0) {
    return (
      <div className="scene-map">
        <div className="scene-map-empty">当前场景暂无可访问节点</div>
      </div>
    );
  }

  return (
    <div
      className="scene-map"
      role="group"
      aria-label={`${scene.name} 2D 导航地图（共 ${nodes.length} 个节点）`}
    >
      {nodes.map((node, index) => {
        const position = resolveNodePosition(node, index, nodes.length);
        const entry = node.entries?.[0];
        const entryLabel = entry ? entry.targetPath : '未绑定入口';
        return (
          <button
            key={node.nodeKey}
            type="button"
            className={
              node.nodeType === 'ENTRANCE'
                ? 'scene-map-node scene-map-node-entrance'
                : 'scene-map-node'
            }
            style={position}
            aria-label={`${node.name}，${entry ? `入口 ${entry.targetPath}` : '暂无入口'}`}
            title={entry ? `${node.name} → ${entry.targetPath}` : node.name}
            onClick={() => {
              if (entry) {
                // 非法入口由服务端发布校验拦截，这里静默忽略避免前端跳转到危险协议
                openNavigationEntry(entry);
              }
            }}
          >
            <span className="scene-map-node-icon" aria-hidden>
              {nodeGlyph(node)}
            </span>
            <span className="scene-map-node-name">{node.name}</span>
            <span className="scene-map-node-entry">{entryLabel}</span>
          </button>
        );
      })}
    </div>
  );
}

/**
 * 导航节点列表视图：作为地图的键盘/读屏友好替代，同时展示节点层级。
 *
 * @param scene 导航场景
 * @returns 列表表格
 */
export function SceneNavigationList({ scene }: { scene: NavigationSceneView }) {
  const nodes = sortNodes(scene.nodes ?? []);
  if (nodes.length === 0) {
    return <Empty description="当前场景暂无可访问节点" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }
  return (
    <ul aria-label={`${scene.name} 导航节点列表`} style={{ listStyle: 'none', padding: 0 }}>
      {nodes.map((node: NavigationNodeView) => {
        const entry = node.entries?.[0];
        return (
          <li
            key={node.nodeKey}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '8px 0',
              borderBottom: '1px solid #f0f0f0',
            }}
          >
            <span aria-hidden>{nodeGlyph(node)}</span>
            <span style={{ fontWeight: 600 }}>{node.name}</span>
            {node.parentKey ? (
              <span style={{ color: '#8c8c8c', fontSize: 12 }}>父节点：{node.parentKey}</span>
            ) : null}
            {entry ? (
              <button
                type="button"
                onClick={() => openNavigationEntry(entry)}
                style={{
                  marginLeft: 'auto',
                  border: 'none',
                  background: 'none',
                  color: '#1677ff',
                  cursor: 'pointer',
                }}
              >
                打开 {entry.targetPath}
              </button>
            ) : (
              <span style={{ marginLeft: 'auto', color: '#8c8c8c', fontSize: 12 }}>未绑定入口</span>
            )}
          </li>
        );
      })}
    </ul>
  );
}
