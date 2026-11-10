// 画布交互层：@dnd-kit 拖入 / 拖动、滚动容器平移（中键 / 空格+左键）、
// 缩放（Ctrl+滚轮 / 工具栏，0.4x~2x）、对齐参考线与内容尺寸自适应。
// 节点落点 / 坐标写回由 Canvas 通过 onMoveNode / onDropNode 上抛。

import {
  PointerSensor,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragMoveEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
} from 'react';
import type { NodeType } from '../../../api/workflow';
import { CANVAS_DROPPABLE_ID, NODE_SIZE, type CanvasModel, type CanvasNode } from '../model';
import {
  CANVAS_MIN_HEIGHT,
  CANVAS_MIN_WIDTH,
  CANVAS_PADDING,
  ZOOM_STEP,
  alignNode,
  clampZoom,
  snap,
  type Alignment,
} from './geometry';

/** 拖拽源 data 结构（Palette 与画布节点共用判别字段 source） */
interface DraggableData {
  source?: 'palette' | 'node';
  nodeType?: NodeType;
  key?: string;
}

/** 平移起点（客户端坐标 + 起始滚动位置） */
interface PanStart {
  x: number;
  y: number;
  left: number;
  top: number;
}

/** 对齐参考线（画布坐标，null 表示该轴无参考线） */
export interface Guide {
  x: number | null;
  y: number | null;
}

export interface CanvasInteraction {
  /** 滚动容器 ref 与 droppable 合并后的回调 */
  attachRef: (element: HTMLDivElement | null) => void;
  isOver: boolean;
  activeType: NodeType | null;
  zoom: number;
  guide: Guide;
  panRef: React.MutableRefObject<PanStart | null>;
  sensors: ReturnType<typeof useSensors>;
  width: number;
  height: number;
  handleDragStart: (event: DragStartEvent) => void;
  handleDragMove: (event: DragMoveEvent) => void;
  handleDragEnd: (event: DragEndEvent) => void;
  handleDragCancel: () => void;
  handlePointerDown: (event: ReactPointerEvent<HTMLDivElement>) => void;
  handlePointerMove: (event: ReactPointerEvent<HTMLDivElement>) => void;
  endPan: (event: ReactPointerEvent<HTMLDivElement>) => void;
  applyZoom: (next: number) => void;
  fitCanvas: () => void;
}

export function useCanvasInteraction(
  model: CanvasModel,
  onMoveNode: (key: string, x: number, y: number) => void,
  onDropNode: (type: NodeType, x: number, y: number) => void,
): CanvasInteraction {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const { setNodeRef, isOver } = useDroppable({ id: CANVAS_DROPPABLE_ID });
  const [activeType, setActiveType] = useState<NodeType | null>(null);
  const [zoom, setZoom] = useState(1);
  const [guide, setGuide] = useState<Guide>({ x: null, y: null });
  const panRef = useRef<PanStart | null>(null);
  const spaceRef = useRef(false);
  // distance 4：区分「点击选中」与「拖拽」
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }));

  const nodesByKey = useMemo(
    () => new Map(model.nodes.map((node) => [node.key, node])),
    [model.nodes],
  );

  const { width, height } = useMemo(() => {
    let maxX = CANVAS_MIN_WIDTH - CANVAS_PADDING;
    let maxY = CANVAS_MIN_HEIGHT - CANVAS_PADDING;
    model.nodes.forEach((node) => {
      const size = NODE_SIZE[node.type];
      maxX = Math.max(maxX, node.x + size.width);
      maxY = Math.max(maxY, node.y + size.height);
    });
    return { width: maxX + CANVAS_PADDING, height: maxY + CANVAS_PADDING };
  }, [model.nodes]);

  // Ctrl / Cmd + 滚轮：以指针位置为中心缩放（原生监听以允许 preventDefault）
  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    const onWheel = (event: WheelEvent) => {
      if (!event.ctrlKey && !event.metaKey) {
        return;
      }
      event.preventDefault();
      const rect = container.getBoundingClientRect();
      const pointerX = event.clientX - rect.left + container.scrollLeft;
      const pointerY = event.clientY - rect.top + container.scrollTop;
      const next = clampZoom(zoom * (event.deltaY < 0 ? ZOOM_STEP : 1 / ZOOM_STEP));
      if (next !== zoom) {
        container.scrollLeft = (pointerX * next) / zoom - (event.clientX - rect.left);
        container.scrollTop = (pointerY * next) / zoom - (event.clientY - rect.top);
        setZoom(next);
      }
    };
    container.addEventListener('wheel', onWheel, { passive: false });
    return () => container.removeEventListener('wheel', onWheel);
  }, [zoom]);

  // 空格按下即进入「抓手平移」模式（输入框内不劫持）
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const tag = target?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || target?.isContentEditable) {
        return;
      }
      if (event.code === 'Space') {
        spaceRef.current = true;
      }
    };
    const onKeyUp = (event: KeyboardEvent) => {
      if (event.code === 'Space') {
        spaceRef.current = false;
      }
    };
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('keyup', onKeyUp);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('keyup', onKeyUp);
    };
  }, []);

  const attachRef = (element: HTMLDivElement | null) => {
    containerRef.current = element;
    setNodeRef(element);
  };

  /** 平移：中键或空格 + 左键拖动 */
  const handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    const container = containerRef.current;
    const panning = event.button === 1 || (event.button === 0 && spaceRef.current);
    if (!container || !panning) {
      return;
    }
    event.preventDefault();
    panRef.current = {
      x: event.clientX,
      y: event.clientY,
      left: container.scrollLeft,
      top: container.scrollTop,
    };
    container.setPointerCapture(event.pointerId);
  };

  const handlePointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const container = containerRef.current;
    const start = panRef.current;
    if (!container || !start) {
      return;
    }
    container.scrollLeft = start.left - (event.clientX - start.x);
    container.scrollTop = start.top - (event.clientY - start.y);
  };

  const endPan = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!panRef.current) {
      return;
    }
    panRef.current = null;
    containerRef.current?.releasePointerCapture(event.pointerId);
  };

  /** 以缩放比例换算拖拽位移（屏幕 px → 画布坐标） */
  const dragTarget = (node: CanvasNode, deltaX: number, deltaY: number): Alignment => {
    const others = model.nodes.filter((item) => item.key !== node.key);
    return alignNode(node, node.x + deltaX / zoom, node.y + deltaY / zoom, others);
  };

  const handleDragStart = (event: DragStartEvent) => {
    const data = event.active.data.current as DraggableData | undefined;
    setActiveType(data?.source === 'palette' ? (data.nodeType ?? null) : null);
  };

  const handleDragMove = (event: DragMoveEvent) => {
    const data = event.active.data.current as DraggableData | undefined;
    if (data?.source !== 'node' || !data.key) {
      return;
    }
    const node = nodesByKey.get(data.key);
    if (!node) {
      return;
    }
    const target = dragTarget(node, event.delta.x, event.delta.y);
    setGuide({ x: target.guideX, y: target.guideY });
  };

  const handleDragEnd = (event: DragEndEvent) => {
    setActiveType(null);
    setGuide({ x: null, y: null });
    const data = event.active.data.current as DraggableData | undefined;

    if (data?.source === 'node' && data.key) {
      const node = nodesByKey.get(data.key);
      if (!node) {
        return;
      }
      const target = dragTarget(node, event.delta.x, event.delta.y);
      // 命中参考线的轴按对齐值落位，另一轴按网格吸附
      const x = target.guideX === null ? snap(target.x) : Math.round(target.x);
      const y = target.guideY === null ? snap(target.y) : Math.round(target.y);
      onMoveNode(node.key, x, y);
      return;
    }

    if (data?.source === 'palette' && data.nodeType) {
      const translated = event.active.rect.current.translated;
      const container = containerRef.current;
      if (!translated || !container) {
        return;
      }
      const rect = container.getBoundingClientRect();
      // 落点 = 拖拽拖影中心（客户端坐标）→ 画布局部坐标（按缩放还原）
      const centerX =
        (translated.left + translated.width / 2 - rect.left + container.scrollLeft) / zoom;
      const centerY =
        (translated.top + translated.height / 2 - rect.top + container.scrollTop) / zoom;
      const size = NODE_SIZE[data.nodeType];
      onDropNode(data.nodeType, snap(centerX - size.width / 2), snap(centerY - size.height / 2));
    }
  };

  const handleDragCancel = () => {
    setActiveType(null);
    setGuide({ x: null, y: null });
  };

  /** 缩放到指定值（保持滚动位置近似不变） */
  const applyZoom = (next: number) => {
    setZoom(clampZoom(next));
  };

  /** 适应画布：按容器可视区与内容尺寸取最小值（不超过 1x） */
  const fitCanvas = () => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    const scale = Math.min(container.clientWidth / width, container.clientHeight / height, 1);
    setZoom(clampZoom(Math.floor(scale * 100) / 100));
    container.scrollLeft = 0;
    container.scrollTop = 0;
  };

  return {
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
  };
}
