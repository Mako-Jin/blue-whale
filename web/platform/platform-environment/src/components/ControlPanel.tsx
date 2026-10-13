import { useEffect, useState } from 'react';
import {
  FLOW_PRESETS,
  formatClock,
  formatDate,
  sampleEnvironment,
  toDegrees,
  type EnvironmentState,
  type SceneOptions,
  type StageStats,
} from '@/scene';
import { formatViewport, withThousands } from '@/format';
import './ControlPanel.less';

type ControlPanelProps = {
  options: SceneOptions;
  onChange: (patch: Partial<SceneOptions>) => void;
  /** 环境基准（时刻、日期、时间流逝）。与选项一样是"使用者可控的数据" */
  environment: EnvironmentState;
  onEnvironmentChange: (patch: Partial<EnvironmentState>) => void;
  /**
   * 逐帧读取舞台的**当前**环境。时间流逝时它才是真相 ——
   * 手里的 `environment` 只是基准，早就被舞台推进过去了。
   * 舞台尚未就绪（或创建失败）时返回 `null`，此时回落到基准值。
   */
  readEnvironment: () => EnvironmentState | null;
  stats: StageStats | null;
};

type ToggleSpec = {
  key: keyof SceneOptions;
  label: string;
  hint: string;
};

const TOGGLES: ToggleSpec[] = [
  { key: 'autoRotate', label: '自动环绕', hint: '相机绕场景匀速转动' },
  { key: 'shadows', label: '阴影', hint: '开关会触发材质重新编译' },
  { key: 'showGrid', label: '地面网格', hint: '网格辅助线' },
  { key: 'wireframe', label: '线框模式', hint: '看物体拓扑' },
];

/** 一天 1440 分钟。滑块的粒度就是 1 分钟 —— 再粗就拨不出"日落前后那十分钟" */
const MINUTES_PER_DAY = 1440;
const DAYS_PER_YEAR = 365;

/** 两个环境状态是否"显示上等价"（同一天同一分钟）。用于把每秒 60 次的读取压成每分钟一次重渲染 */
function sameMinute(left: EnvironmentState, right: EnvironmentState): boolean {
  return (
    left.dayOfYear === right.dayOfYear &&
    left.flow === right.flow &&
    Math.round(left.localHours * 60) === Math.round(right.localHours * 60)
  );
}

/** 画布左下角的浮层。用 HUD 样式而不是普通面板：三维画面里白色面板会挡成一块块白斑。 */
export default function ControlPanel({
  options,
  onChange,
  environment,
  onEnvironmentChange,
  readEnvironment,
  stats,
}: ControlPanelProps) {
  /**
   * 界面上显示的"当前环境"。
   *
   * 为什么不直接用 `props.environment`：时间一流动，舞台手里那份就往前走了，
   * 而 props 里的是**基准**，会一直停在拨动的那个刻度上 —— 时钟不走、滑块不动，
   * 看起来像"时间流逝没生效"。所以这里逐帧读舞台（与小地图读 `interaction()` 同路），
   * 但**只在分钟变化时才落进 state**：每秒 60 次重渲染这个面板毫无意义。
   */
  const [live, setLive] = useState(environment);

  useEffect(() => {
    let frame = 0;
    const tick = () => {
      frame = requestAnimationFrame(tick);
      const next = readEnvironment() ?? environment;
      setLive((previous) => (sameMinute(previous, next) ? previous : next));
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [readEnvironment, environment]);

  const metrics: [string, string][] = [
    ['帧率', stats ? `${stats.fps} fps` : '—'],
    ['绘制调用', stats ? withThousands(stats.drawCalls) : '—'],
    ['三角面', stats ? withThousands(stats.triangles) : '—'],
    ['绘制缓冲', stats ? formatViewport(stats.width, stats.height, stats.pixelRatio) : '—'],
  ];

  /**
   * 单独写这一步是为了类型：`{ [动态键]: 值 }` 会被推断成 `{ [x: string]: boolean }`，
   * 与 `Partial<SceneOptions>` 的"可选布尔"不兼容。先建空对象再按键赋值就没这个问题。
   */
  const emitToggle = (key: keyof SceneOptions, checked: boolean) => {
    const patch: Partial<SceneOptions> = {};
    patch[key] = checked;
    onChange(patch);
  };

  /**
   * 环境读数**就是纯内核的输出**。
   *
   * 这里不重算、不近似、不缓存 —— 直接调 `sampleEnvironment(live)`。
   * 面板上写的"太阳高度 46°"与画面里那个太阳，用的是同一个函数、同一份数据，
   * 所以它不可能说谎。这正是把内核做成纯函数换来的好处。
   */
  const sample = sampleEnvironment(live);
  const altitude = Math.round(toDegrees(sample.solar.altitude));
  const phase = sample.look.night ? '夜间' : sample.look.daylight > 0.5 ? '白昼' : '晨昏';
  const minuteOfDay = Math.round(live.localHours * 60) % MINUTES_PER_DAY;

  return (
    <section className="render-panel" aria-label="渲染控制">
      <header className="render-panel__header">
        <h2 className="render-panel__title">渲染控制</h2>
        {/*
          只写**所有场景都成立**的两条。可操控场景的右键被"设置行动终点"占用
          （见 `scene/player/controls.ts`），把"右键平移"写死在这里就会在古建筑场景里
          说反话 —— 那条提示由小地图的图例承担。
        */}
        <span className="render-panel__note">拖拽旋转 · 滚轮缩放</span>
      </header>

      <ul className="render-panel__toggles">
        {TOGGLES.map((toggle) => (
          <li key={toggle.key} className="render-panel__toggle">
            <label className="render-panel__label">
              <input
                type="checkbox"
                className="render-panel__checkbox"
                checked={options[toggle.key]}
                onChange={(event) => emitToggle(toggle.key, event.target.checked)}
              />
              <span className="render-panel__label-text">{toggle.label}</span>
            </label>
            <span className="render-panel__hint">{toggle.hint}</span>
          </li>
        ))}
      </ul>

      <section className="render-panel__env" aria-label="环境">
        <header className="render-panel__env-head">
          <h3 className="render-panel__env-title">环境</h3>
          <span className="render-panel__note">北京 · 东八区</span>
        </header>

        <label className="render-panel__field">
          <span className="render-panel__field-label">
            时刻
            <em className="render-panel__field-value mono">{formatClock(live.localHours)}</em>
          </span>
          <input
            type="range"
            className="render-panel__range"
            min={0}
            max={MINUTES_PER_DAY - 1}
            step={1}
            value={minuteOfDay}
            aria-label="时刻"
            aria-valuetext={formatClock(live.localHours)}
            onChange={(event) =>
              onEnvironmentChange({ localHours: Number(event.target.value) / 60 })
            }
          />
        </label>

        <label className="render-panel__field">
          <span className="render-panel__field-label">
            日期
            <em className="render-panel__field-value mono">{formatDate(live.dayOfYear)}</em>
          </span>
          <input
            type="range"
            className="render-panel__range"
            min={1}
            max={DAYS_PER_YEAR}
            step={1}
            value={Math.round(live.dayOfYear)}
            aria-label="日期"
            aria-valuetext={formatDate(live.dayOfYear)}
            onChange={(event) => onEnvironmentChange({ dayOfYear: Number(event.target.value) })}
          />
        </label>

        <div className="render-panel__chips" role="group" aria-label="时间流逝">
          {FLOW_PRESETS.map((preset) => (
            <button
              key={preset.value}
              type="button"
              className={
                live.flow === preset.value
                  ? 'render-panel__chip render-panel__chip--active'
                  : 'render-panel__chip'
              }
              aria-pressed={live.flow === preset.value}
              onClick={() => onEnvironmentChange({ flow: preset.value })}
            >
              {preset.label}
            </button>
          ))}
        </div>

        <p className="render-panel__readout mono">{`太阳高度 ${altitude}° · ${phase}`}</p>
      </section>

      <dl className="render-panel__metrics">
        {metrics.map(([label, value]) => (
          <div className="render-panel__metric" key={label}>
            <dt className="render-panel__metric-label">{label}</dt>
            <dd className="render-panel__metric-value mono">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}
