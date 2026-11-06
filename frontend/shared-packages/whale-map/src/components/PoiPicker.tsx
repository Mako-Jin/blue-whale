/**
 * 地点搜索框：关键词 → POI 列表 → 选中回调（GCJ-02 坐标）。
 *
 * 没有任何底图依赖，所以能在「没有 Key 的表单页」直接用（地址录入、外出地点、供应商地址）。
 * 搜不到时会**回退到地址解析**：用户输入的常常就是一个完整地址而不是 POI 名称，
 * 只报「无结果」会让人以为搜索坏了。
 */

import { useRef, useState, type CSSProperties } from 'react';
import type { MapClient } from '../client';
import type { CoordType, PoiVO } from '../types';

/** 选中结果（POI 与地址解析统一成一种形状） */
export interface PoiSelection {
  /** 展示名称 */
  name: string;
  /** 详细地址 */
  address: string | null;
  lng: number;
  lat: number;
  /** 坐标系（恒为 GCJ02，底图坐标系） */
  coordType: CoordType;
  /** 来源：POI 检索 / 地址解析 */
  source: 'POI' | 'GEOCODE';
  /** POI 自身 ID（地址解析为空） */
  poiId?: string;
}

export interface PoiPickerProps {
  /** 地图客户端 */
  client: MapClient;
  /** 限定城市 */
  city?: string;
  /** 输入框占位文案 */
  placeholder?: string;
  /** 每页条数，默认 10 */
  size?: number;
  /** 搜不到 POI 时是否回退地址解析，默认 true */
  allowGeocodeFallback?: boolean;
  /** 选中回调 */
  onSelect: (selection: PoiSelection) => void;
}

const wrapperStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const inputRowStyle: CSSProperties = { display: 'flex', gap: 8 };
const inputStyle: CSSProperties = {
  flex: 1,
  minWidth: 0,
  padding: '6px 10px',
  fontSize: 14,
  border: '1px solid #d9d9d9',
  borderRadius: 6,
};
const buttonStyle: CSSProperties = {
  padding: '6px 14px',
  fontSize: 14,
  color: '#fff',
  background: '#1677ff',
  border: 'none',
  borderRadius: 6,
  cursor: 'pointer',
};
const listStyle: CSSProperties = {
  margin: 0,
  padding: 0,
  listStyle: 'none',
  border: '1px solid #f0f0f0',
  borderRadius: 6,
  maxHeight: 260,
  overflowY: 'auto',
};
const itemStyle: CSSProperties = {
  padding: '8px 10px',
  fontSize: 13,
  cursor: 'pointer',
  borderBottom: '1px solid #f5f5f5',
};
const metaStyle: CSSProperties = { color: 'rgba(0, 0, 0, 0.45)', fontSize: 12 };
const hintStyle: CSSProperties = { fontSize: 12, color: 'rgba(0, 0, 0, 0.45)' };
const errorStyle: CSSProperties = { fontSize: 12, color: '#cf1322' };

/**
 * 地点搜索框
 *
 * @param props 组件属性
 * @returns 搜索框
 */
export function PoiPicker({
  client,
  city,
  placeholder = '输入地点或地址，回车搜索',
  size = 10,
  allowGeocodeFallback = true,
  onSelect,
}: PoiPickerProps) {
  const [keyword, setKeyword] = useState('');
  const [items, setItems] = useState<PoiVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searched, setSearched] = useState(false);
  // 请求序号：慢的那次响应回来时若已不是最新一次，直接丢弃，避免「后发先至」覆盖结果
  const requestSeq = useRef(0);

  const runGeocodeFallback = async (text: string, seq: number) => {
    const geo = await client.geocode({ address: text, city });
    if (seq !== requestSeq.current) {
      return;
    }
    setItems([]);
    if (!geo) {
      return;
    }
    onSelect({
      name: geo.formattedAddress ?? text,
      address: geo.formattedAddress ?? text,
      lng: geo.lng,
      lat: geo.lat,
      coordType: geo.coordType,
      source: 'GEOCODE',
    });
  };

  const search = async () => {
    const text = keyword.trim();
    if (!text) {
      return;
    }
    requestSeq.current += 1;
    const seq = requestSeq.current;
    setLoading(true);
    setError(null);
    try {
      const page = await client.searchPoi(text, { city, page: 1, size });
      if (seq !== requestSeq.current) {
        return;
      }
      setItems(page.items ?? []);
      setSearched(true);
      if ((page.items ?? []).length === 0 && allowGeocodeFallback) {
        await runGeocodeFallback(text, seq);
      }
    } catch (ex) {
      if (seq !== requestSeq.current) {
        return;
      }
      setError(ex instanceof Error ? ex.message : '搜索失败');
    } finally {
      if (seq === requestSeq.current) {
        setLoading(false);
      }
    }
  };

  return (
    <div style={wrapperStyle}>
      <div style={inputRowStyle}>
        <input
          style={inputStyle}
          value={keyword}
          placeholder={placeholder}
          onChange={(event) => setKeyword(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault();
              void search();
            }
          }}
        />
        <button type="button" style={buttonStyle} onClick={() => void search()} disabled={loading}>
          {loading ? '搜索中…' : '搜索'}
        </button>
      </div>
      {error ? <div style={errorStyle}>{error}</div> : null}
      {items.length > 0 ? (
        <ul style={listStyle}>
          {items.map((item) => (
            <li
              key={item.id}
              style={itemStyle}
              onClick={() =>
                onSelect({
                  name: item.name,
                  address: item.address,
                  lng: item.lng,
                  lat: item.lat,
                  coordType: item.coordType,
                  source: 'POI',
                  poiId: item.id,
                })
              }
            >
              <div>{item.name}</div>
              <div style={metaStyle}>
                {item.address ?? '—'}
                {item.distanceMeters === null ? '' : ` · ${Math.round(item.distanceMeters)} m`}
              </div>
            </li>
          ))}
        </ul>
      ) : null}
      {searched && items.length === 0 && !loading ? (
        <div style={hintStyle}>
          {allowGeocodeFallback ? '未找到同名地点，已尝试按地址解析' : '未找到匹配地点'}
        </div>
      ) : null}
    </div>
  );
}
