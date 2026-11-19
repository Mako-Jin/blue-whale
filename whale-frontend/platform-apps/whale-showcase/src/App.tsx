import { useEffect, useRef, useState } from 'react';
import { loadAssets } from './scene/loader';
import {
  createShowcase,
  type PresetName,
  type Quality,
  type SceneStats,
  type ShowcaseHandle,
  type TimeName,
} from './scene/world';

const TIME_LABEL: Record<TimeName, string> = {
  dusk: '黄昏',
  day: '白天',
  blue: '蓝调',
  night: '夜晚',
};

const QUALITY_LABEL: Record<Quality, string> = {
  high: '高',
  mid: '中',
  low: '低',
};

const PRESET_LABEL: Record<PresetName, string> = {
  overview: '全景',
  bridge: '桥头',
  street: '街景',
  dock: '码头',
  shop: '铺面',
  alley: '巷口',
  roofs: '俯视',
  bank: '河岸',
};

export default function App(): React.ReactElement {
  const hostRef = useRef<HTMLDivElement>(null);
  const handleRef = useRef<ShowcaseHandle | null>(null);
  const [stats, setStats] = useState<SceneStats>({ fps: 0, triangles: 0, calls: 0 });
  const [time, setTime] = useState<TimeName>('dusk');
  const [preset, setPreset] = useState<PresetName>('overview');
  const [ready, setReady] = useState(false);
  // 支持 ?q=low 直接指定画质：截图脚本调参时用它换帧率，不必改代码
  const initialQuality = ((): Quality => {
    const q = new URLSearchParams(window.location.search).get('q');
    return q === 'high' || q === 'low' ? q : 'mid';
  })();
  const [quality, setQuality] = useState<Quality>(initialQuality);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return () => undefined;

    // 资产先加载再建场景：牌坊与树要走 GLB 分支，建完再替换等于重建整棵场景树。
    // disposed 兜住"加载还没回来组件就卸载"，否则渲染器会挂在 detached 节点上泄漏。
    let disposed = false;
    let handle: ShowcaseHandle | null = null;

    void loadAssets().then((assets) => {
      if (disposed) return;
      handle = createShowcase(host, { onStats: setStats, assets, quality: initialQuality });
      handleRef.current = handle;
      setReady(true);
    });

    return () => {
      disposed = true;
      handle?.dispose();
      handleRef.current = null;
    };
  }, []);

  const pickTime = (name: TimeName) => {
    setTime(name);
    handleRef.current?.setTime(name);
  };

  const pickPreset = (name: PresetName) => {
    setPreset(name);
    handleRef.current?.setPreset(name);
  };

  const pickQuality = (q: Quality) => {
    setQuality(q);
    handleRef.current?.setQuality(q);
  };

  return (
    <div className="sc-page">
      <div className="sc-stage" ref={hostRef} />
      {ready ? null : <div className="sc-loading">资产加载中</div>}

      <div className="sc-hud">
        <div className="sc-hud-row">
          <span className="sc-hud-key">FPS</span>
          <span className="sc-hud-val">{stats.fps}</span>
        </div>
        <div className="sc-hud-row">
          <span className="sc-hud-key">三角面</span>
          <span className="sc-hud-val">{(stats.triangles / 1000).toFixed(1)}k</span>
        </div>
        <div className="sc-hud-row">
          <span className="sc-hud-key">Drawcall</span>
          <span className="sc-hud-val">{stats.calls}</span>
        </div>
      </div>

      <div className="sc-title">
        <div className="sc-title-main">蓝鲸 · 河谷渡口</div>
        <div className="sc-title-sub">S22 河谷渡口 · 水面去油花 / 三档法线</div>
      </div>

      <div className="sc-bar">
        <div className="sc-group">
          {(Object.keys(TIME_LABEL) as TimeName[]).map((k) => (
            <button
              key={k}
              type="button"
              className={`sc-btn${time === k ? ' is-on' : ''}`}
              onClick={() => pickTime(k)}
            >
              {TIME_LABEL[k]}
            </button>
          ))}
        </div>
        <div className="sc-sep" />
        <div className="sc-group">
          {(Object.keys(PRESET_LABEL) as PresetName[]).map((k) => (
            <button
              key={k}
              type="button"
              className={`sc-btn${preset === k ? ' is-on' : ''}`}
              onClick={() => pickPreset(k)}
            >
              {PRESET_LABEL[k]}
            </button>
          ))}
        </div>
        <div className="sc-sep" />
        <div className="sc-group">
          <span className="sc-bar-label">画质</span>
          {(Object.keys(QUALITY_LABEL) as Quality[]).map((k) => (
            <button
              key={k}
              type="button"
              className={`sc-btn${quality === k ? ' is-on' : ''}`}
              onClick={() => pickQuality(k)}
            >
              {QUALITY_LABEL[k]}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
