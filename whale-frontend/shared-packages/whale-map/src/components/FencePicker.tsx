/**
 * 围栏编辑器：底图点选 + 顶点增删 + 实时面积周长。
 *
 * 交互上刻意**不做「双击闭合」**：底图 SDK 自带双击缩放，两件事抢同一个手势，
 * 结果往往是「想闭合结果放大了两倍」。改用工具栏按钮显式结束绘制，
 * 手机上也不会有「双击被浏览器吞掉」的问题。
 *
 * 点选一律按 GCJ-02 处理（底图坐标系），提交时由 `toFenceSaveParams` 声明 coordType。
 */

import { useEffect, useMemo, useReducer, useRef, useState, type CSSProperties } from 'react';
import {
  draftOutline,
  draftPolygon,
  fenceDraftReducer,
  fenceDraftStats,
  fenceToDraft,
  createFenceDraft,
  formatArea,
  formatDistance,
  validateFenceDraft,
  type FenceDraftState,
  type FenceDraftStats,
} from '../fenceDraft';
import type { FenceVO, GeoPoint } from '../types';
import type { MapEngine, MapEngineFactory, MapMarker, MapPolygon } from '../engine/types';
import { WhaleMap } from './WhaleMap';

export interface FencePickerProps {
  /** 引擎工厂（`createTencentMapEngine(key)` 或自定义） */
  factory: MapEngineFactory;
  /** 重建标识，见 `WhaleMap` */
  engineKey?: string;
  /** 回显已有围栏（编辑场景） */
  initialFence?: FenceVO | null;
  /** 地图高度，默认 420 */
  height?: number | string;
  /** 只读（仅展示，不可增删顶点） */
  readOnly?: boolean;
  /** 草稿变化回调 */
  onChange?: (state: FenceDraftState, stats: FenceDraftStats) => void;
}

const wrapperStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 8 };
const toolbarStyle: CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  alignItems: 'center',
  gap: 8,
  fontSize: 13,
};
const buttonStyle: CSSProperties = {
  padding: '4px 10px',
  fontSize: 13,
  border: '1px solid #d9d9d9',
  borderRadius: 6,
  background: '#fff',
  cursor: 'pointer',
};
const activeButtonStyle: CSSProperties = {
  ...buttonStyle,
  borderColor: '#1677ff',
  color: '#1677ff',
  background: 'rgba(22, 119, 255, 0.06)',
};
const statStyle: CSSProperties = { color: 'rgba(0, 0, 0, 0.65)' };
const warnStyle: CSSProperties = { color: '#d4380d' };
const inputStyle: CSSProperties = {
  width: 88,
  padding: '3px 8px',
  fontSize: 13,
  border: '1px solid #d9d9d9',
  borderRadius: 6,
};

/**
 * 围栏编辑器
 *
 * @param props 组件属性
 * @returns 编辑器
 */
export function FencePicker({
  factory,
  engineKey,
  initialFence,
  height = 420,
  readOnly = false,
  onChange,
}: FencePickerProps) {
  const [draft, dispatch] = useReducer(fenceDraftReducer, initialFence ?? null, (seed) =>
    seed ? fenceToDraft(seed) : createFenceDraft(),
  );
  const [engine, setEngine] = useState<MapEngine | null>(null);
  const changeRef = useRef(onChange);

  useEffect(() => {
    changeRef.current = onChange;
  }, [onChange]);

  // 切换编辑对象时才重置草稿；依赖用 id 而非整个对象，避免父组件每次渲染都清空用户正在画的图
  const fenceId = initialFence?.id ?? null;
  const loadedIdRef = useRef(fenceId);
  useEffect(() => {
    if (fenceId === loadedIdRef.current) {
      return;
    }
    loadedIdRef.current = fenceId;
    dispatch(initialFence ? { type: 'load', fence: initialFence } : { type: 'clear' });
  }, [fenceId, initialFence]);

  const stats = useMemo(() => fenceDraftStats(draft), [draft]);
  const outline = useMemo(() => draftOutline(draft), [draft]);
  const polygonPoints = useMemo(() => draftPolygon(draft), [draft]);

  useEffect(() => {
    changeRef.current?.(draft, stats);
  }, [draft, stats]);

  // 顶点标记：用 useMemo 稳住引用，否则 WhaleMap 每次渲染都全量重画标记
  const markers = useMemo<MapMarker[]>(() => {
    if (draft.shape === 'CIRCLE') {
      return draft.center
        ? [{ id: 'center', point: draft.center, label: '圆心', kind: 'fence' }]
        : [];
    }
    return polygonPoints.map((item, index) => ({
      id: `v-${index}`,
      point: item,
      label: `顶点 ${index + 1}`,
      kind: 'fence' as const,
    }));
  }, [draft.shape, draft.center, polygonPoints]);

  const polygons = useMemo<MapPolygon[]>(
    () => (outline.length >= 3 ? [{ id: 'draft', points: outline }] : []),
    [outline],
  );

  const handleClick = (point: GeoPoint) => {
    if (readOnly) {
      return;
    }
    if (draft.shape === 'CIRCLE') {
      dispatch({ type: 'setCircle', center: point, radiusMeters: draft.radiusMeters });
      return;
    }
    dispatch({ type: 'addPoint', point });
  };

  const error = validateFenceDraft(draft);

  return (
    <div style={wrapperStyle}>
      <div style={toolbarStyle}>
        <button
          type="button"
          style={draft.shape === 'POLYGON' ? activeButtonStyle : buttonStyle}
          onClick={() => dispatch({ type: 'setShape', shape: 'POLYGON' })}
          disabled={readOnly}
        >
          多边形
        </button>
        <button
          type="button"
          style={draft.shape === 'CIRCLE' ? activeButtonStyle : buttonStyle}
          onClick={() => dispatch({ type: 'setShape', shape: 'CIRCLE' })}
          disabled={readOnly}
        >
          圆形
        </button>
        {draft.shape === 'CIRCLE' ? (
          <label style={statStyle}>
            半径
            <input
              style={{ ...inputStyle, marginLeft: 6 }}
              type="number"
              min={1}
              value={Math.round(draft.radiusMeters)}
              disabled={readOnly}
              onChange={(event) =>
                dispatch({ type: 'setRadius', radiusMeters: Number(event.target.value) || 0 })
              }
            />
            米
          </label>
        ) : null}
        <button
          type="button"
          style={buttonStyle}
          onClick={() => dispatch({ type: 'undo' })}
          disabled={readOnly || draft.points.length === 0}
        >
          撤销
        </button>
        <button
          type="button"
          style={buttonStyle}
          onClick={() => dispatch({ type: 'clear' })}
          disabled={readOnly}
        >
          清空
        </button>
        <span style={statStyle}>
          {draft.shape === 'CIRCLE'
            ? `半径 ${formatDistance(draft.radiusMeters)}`
            : `顶点 ${stats.vertexCount} 个`}
          {' · '}
          面积 {formatArea(stats.areaSquareMeters)}
          {' · '}
          周长 {formatDistance(stats.perimeterMeters)}
        </span>
        {error ? <span style={warnStyle}>{error}</span> : null}
      </div>
      <WhaleMap
        factory={factory}
        engineKey={engineKey}
        height={height}
        markers={markers}
        polygons={polygons}
        onReady={setEngine}
        onClick={handleClick}
      />
      <div style={{ ...statStyle, fontSize: 12 }}>
        {readOnly
          ? '只读：仅供查看围栏范围'
          : draft.shape === 'CIRCLE'
            ? '在地图上点击设置圆心，再调整半径'
            : '在地图上依次点击顶点（至少 3 个），圆点即顶点'}
        {engine ? '' : ' · 底图初始化中'}
      </div>
    </div>
  );
}
