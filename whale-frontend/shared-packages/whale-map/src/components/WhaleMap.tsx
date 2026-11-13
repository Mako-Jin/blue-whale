/**
 * 底图容器组件。
 *
 * 只做「挂载引擎、同步数据、卸载销毁」，不含任何供应商细节：
 * 底图由 `factory` 提供（`createTencentMapEngine(key)` 或宿主自研），
 * 所以换底图只换一个工厂函数，页面代码不用动。
 *
 * **`engineKey` 决定重建时机**：工厂函数通常是内联创建的（`createTencentMapEngine(key)`），
 * 每次渲染都是新引用，直接拿它当依赖会「每渲染一次重建一次地图」（表现为地图疯狂闪烁、点位闪回）。
 * 所以依赖固定在 `engineKey` 上——真正需要重建时（换 Key、换供应商）改这个字符串即可。
 */

import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import type { GeoPoint } from '../types';
import type { MapEngine, MapEngineFactory, MapMarker, MapPolygon } from '../engine/types';

export interface WhaleMapProps {
  /** 引擎工厂；必须是稳定引用或直接内联（组件不拿它当依赖） */
  factory: MapEngineFactory;
  /** 重建标识：变化时才销毁重建引擎（换底图 Key / 换供应商时改它） */
  engineKey?: string;
  /** 初始中心（GCJ-02），仅首次生效；后续用 `engine.setCenter` */
  center?: GeoPoint;
  /** 初始缩放，仅首次生效 */
  zoom?: number;
  /** 标记点位（GCJ-02） */
  markers?: MapMarker[];
  /** 多边形（GCJ-02） */
  polygons?: MapPolygon[];
  /** 容器高度，默认 420 */
  height?: number | string;
  className?: string;
  style?: CSSProperties;
  /** 引擎就绪回调（把 engine 交给调用方做增量操作） */
  onReady?: (engine: MapEngine) => void;
  /** 地图点击（GCJ-02） */
  onClick?: (point: GeoPoint) => void;
  /** 引擎创建失败回调（默认在容器内显示错误文案） */
  onError?: (error: Error) => void;
  /** 覆盖加载中/失败的占位内容 */
  fallback?: ReactNode;
}

const containerStyle: CSSProperties = { position: 'relative', width: '100%', overflow: 'hidden' };
const canvasStyle: CSSProperties = { position: 'absolute', inset: 0 };
const hintStyle: CSSProperties = {
  position: 'absolute',
  inset: 0,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: 24,
  textAlign: 'center',
  fontSize: 13,
  color: 'rgba(0, 0, 0, 0.45)',
  background: 'rgba(0, 0, 0, 0.02)',
};

/**
 * 底图容器
 *
 * @param props 组件属性
 * @returns 容器
 */
export function WhaleMap({
  factory,
  engineKey = 'default',
  center,
  zoom,
  markers,
  polygons,
  height = 420,
  className,
  style,
  onReady,
  onClick,
  onError,
  fallback,
}: WhaleMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const engineRef = useRef<MapEngine | null>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [errorText, setErrorText] = useState<string | null>(null);

  // 初始视野只在首次创建时用，后续 props 变化不追溯（否则一改 center 就重建地图）
  const initialRef = useRef({ center, zoom });
  const factoryRef = useRef(factory);
  const callbacksRef = useRef({ onReady, onClick, onError });
  const dataRef = useRef({ markers, polygons });

  useEffect(() => {
    factoryRef.current = factory;
  }, [factory]);

  useEffect(() => {
    callbacksRef.current = { onReady, onClick, onError };
  }, [onReady, onClick, onError]);

  useEffect(() => {
    dataRef.current = { markers, polygons };
  }, [markers, polygons]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    let cancelled = false;
    setStatus('loading');
    setErrorText(null);
    factoryRef
      .current(container, {
        center: initialRef.current.center,
        zoom: initialRef.current.zoom,
      })
      .then((engine) => {
        // 创建是异步的，期间组件可能已卸载；不在这里销毁就会留下一张挂在
        // 已移除 DOM 上的地图，SDK 自己也回收不掉（表现为内存持续增长）
        if (cancelled) {
          engine.destroy();
          return;
        }
        engineRef.current = engine;
        engine.setMarkers(dataRef.current.markers ?? []);
        engine.setPolygons(dataRef.current.polygons ?? []);
        engine.onClick((point) => callbacksRef.current.onClick?.(point));
        setStatus('ready');
        callbacksRef.current.onReady?.(engine);
      })
      .catch((error: unknown) => {
        if (cancelled) {
          return;
        }
        const message = error instanceof Error ? error.message : '底图加载失败';
        setStatus('error');
        setErrorText(message);
        callbacksRef.current.onError?.(new Error(message));
      });
    return () => {
      cancelled = true;
      engineRef.current?.destroy();
      engineRef.current = null;
    };
  }, [engineKey]);

  useEffect(() => {
    if (status !== 'ready') {
      return;
    }
    engineRef.current?.setMarkers(markers ?? []);
  }, [markers, status]);

  useEffect(() => {
    if (status !== 'ready') {
      return;
    }
    engineRef.current?.setPolygons(polygons ?? []);
  }, [polygons, status]);

  return (
    <div
      className={className}
      style={{ ...containerStyle, height, ...style }}
      data-map-status={status}
    >
      <div ref={containerRef} style={canvasStyle} />
      {status === 'loading' ? <div style={hintStyle}>{fallback ?? '底图加载中…'}</div> : null}
      {status === 'error' ? (
        <div style={{ ...hintStyle, color: '#cf1322' }}>
          {fallback ?? `底图加载失败：${errorText ?? '未知错误'}`}
        </div>
      ) : null}
    </div>
  );
}
