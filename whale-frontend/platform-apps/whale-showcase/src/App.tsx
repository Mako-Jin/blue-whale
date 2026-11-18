import { useEffect, useRef, useState } from 'react';
import {
  createShowcase,
  type PresetName,
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

const PRESET_LABEL: Record<PresetName, string> = {
  overview: '全景',
  bridge: '桥头',
  street: '街景',
  dock: '码头',
};

export default function App(): React.ReactElement {
  const hostRef = useRef<HTMLDivElement>(null);
  const handleRef = useRef<ShowcaseHandle | null>(null);
  const [stats, setStats] = useState<SceneStats>({ fps: 0, triangles: 0, calls: 0 });
  const [time, setTime] = useState<TimeName>('dusk');
  const [preset, setPreset] = useState<PresetName>('overview');

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return () => undefined;
    const handle = createShowcase(host, { onStats: setStats });
    handleRef.current = handle;
    return () => {
      handle.dispose();
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

  return (
    <div className="sc-page">
      <div className="sc-stage" ref={hostRef} />

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
        <div className="sc-title-sub">S1 渲染底座 · IBL + Bloom + 调色 · 占位体量</div>
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
      </div>
    </div>
  );
}
