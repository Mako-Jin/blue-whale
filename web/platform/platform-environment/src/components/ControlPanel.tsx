import type { SceneOptions, StageStats } from '@/scene';
import { formatViewport, withThousands } from '@/format';
import './ControlPanel.less';

type ControlPanelProps = {
  options: SceneOptions;
  onChange: (patch: Partial<SceneOptions>) => void;
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

/** 画布左下角的浮层。用 HUD 样式而不是普通面板：三维画面里白色面板会挡成一块块白斑。 */
export default function ControlPanel({ options, onChange, stats }: ControlPanelProps) {
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

  return (
    <section className="render-panel" aria-label="渲染控制">
      <header className="render-panel__header">
        <h2 className="render-panel__title">渲染控制</h2>
        <span className="render-panel__note">拖拽旋转 · 滚轮缩放 · 右键平移</span>
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
