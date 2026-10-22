// 自由画布（SVG 自绘 + @dnd-kit 拖入 / 拖动）：
// - 整块画布为 droppable：从 Palette 拖入落点按 pointer 换算坐标并新建节点；
// - 节点为可拖拽源（拖动结束写回 x/y，10px 网格吸附 + 6px 对齐吸附并显示参考线）；
// - 连线（EdgePath）在下层，节点在上层；选中态高亮、校验 errorKeys 标红；
// - 缩放（工具栏 / Ctrl+滚轮，0.4x~2x）与平移（空格+拖动 / 中键拖动）。
//
// 渲染层见 canvas/CanvasScene，交互层见 canvas/useCanvasInteraction。

import { DndContext, DragOverlay } from '@dnd-kit/core';
import type { ReactNode } from 'react';
import type { NodeType } from '../../api/workflow';
import CanvasScene from './canvas/CanvasScene';
import { ZOOM_STEP } from './canvas/geometry';
import { useCanvasInteraction } from './canvas/useCanvasInteraction';
import { NODE_TYPE_COLORS, NODE_TYPE_LABELS, type CanvasModel, type Selection } from './model';

interface CanvasProps {
  /** 左侧调色板（须与画布处于同一 DndContext 内，故由 Canvas 渲染） */
  palette: ReactNode;
  model: CanvasModel;
  selected: Selection | null;
  /** 校验 error 的 refKey 集合（节点 key 或流转 key） */
  errorKeys: Set<string>;
  /** 连线起点节点 key（非空时点击目标节点即建流） */
  linkFrom: string | null;
  onSelectNode: (key: string) => void;
  onSelectFlow: (key: string) => void;
  onClearSelection: () => void;
  onMoveNode: (key: string, x: number, y: number) => void;
  onDropNode: (type: NodeType, x: number, y: number) => void;
}

export default function Canvas({
  palette,
  model,
  selected,
  errorKeys,
  linkFrom,
  onSelectNode,
  onSelectFlow,
  onClearSelection,
  onMoveNode,
  onDropNode,
}: CanvasProps) {
  const {
    attachRef,
    isOver,
    activeType,
    zoom,
    guide,
    panRef,
    sensors,
    width,
    height,
    handleDragStart,
    handleDragMove,
    handleDragEnd,
    handleDragCancel,
    handlePointerDown,
    handlePointerMove,
    endPan,
    applyZoom,
    fitCanvas,
  } = useCanvasInteraction(model, onMoveNode, onDropNode);

  return (
    <DndContext
      sensors={sensors}
      onDragStart={handleDragStart}
      onDragMove={handleDragMove}
      onDragEnd={handleDragEnd}
      onDragCancel={handleDragCancel}
    >
      <div style={{ display: 'flex', gap: 12, height: '100%' }}>
        <div
          style={{
            flex: '0 0 208px',
            width: 208,
            overflow: 'auto',
            paddingRight: 8,
            borderRight: '1px solid #f0f0f0',
          }}
        >
          {palette}
        </div>
        <div
          ref={attachRef}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={endPan}
          onPointerCancel={endPan}
          style={{
            position: 'relative',
            flex: 1,
            minWidth: 0,
            overflow: 'auto',
            border: `1px dashed ${isOver ? '#1677ff' : '#d9d9d9'}`,
            borderRadius: 6,
            background: '#fafafa',
            cursor: panRef.current ? 'grabbing' : 'default',
          }}
        >
          <CanvasScene
            model={model}
            width={width}
            height={height}
            zoom={zoom}
            guide={guide}
            selected={selected}
            errorKeys={errorKeys}
            onSelectNode={onSelectNode}
            onSelectFlow={onSelectFlow}
            onClearSelection={onClearSelection}
          />

          {model.nodes.length === 0 ? (
            <div
              style={{
                position: 'absolute',
                inset: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#bfbfbf',
                pointerEvents: 'none',
              }}
            >
              从左侧拖入节点开始绘制流程
            </div>
          ) : null}

          {linkFrom ? (
            <div
              style={{
                position: 'absolute',
                top: 8,
                left: 8,
                padding: '2px 8px',
                borderRadius: 4,
                background: 'rgba(22,119,255,0.1)',
                color: '#1677ff',
                fontSize: 12,
              }}
            >
              连线中：点击目标节点完成连线（点击空白取消）
            </div>
          ) : null}

          <div
            onPointerDown={(event) => event.stopPropagation()}
            onClick={(event) => event.stopPropagation()}
            style={{
              position: 'absolute',
              right: 12,
              bottom: 12,
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              padding: '4px 6px',
              borderRadius: 6,
              background: 'rgba(255,255,255,0.92)',
              border: '1px solid #e5e5e5',
              fontSize: 12,
            }}
          >
            <button type="button" title="缩小" onClick={() => applyZoom(zoom / ZOOM_STEP)}>
              −
            </button>
            <span style={{ minWidth: 40, textAlign: 'center' }}>{Math.round(zoom * 100)}%</span>
            <button type="button" title="放大" onClick={() => applyZoom(zoom * ZOOM_STEP)}>
              +
            </button>
            <button type="button" title="适应画布" onClick={fitCanvas}>
              适应
            </button>
            <button type="button" title="重置为 100%" onClick={() => applyZoom(1)}>
              重置
            </button>
          </div>
        </div>
      </div>

      <DragOverlay dropAnimation={null}>
        {activeType ? (
          <div
            style={{
              padding: '4px 10px',
              border: `2px solid ${NODE_TYPE_COLORS[activeType]}`,
              borderRadius: 4,
              background: '#fff',
              fontSize: 13,
            }}
          >
            {NODE_TYPE_LABELS[activeType]}
          </div>
        ) : null}
      </DragOverlay>
    </DndContext>
  );
}
