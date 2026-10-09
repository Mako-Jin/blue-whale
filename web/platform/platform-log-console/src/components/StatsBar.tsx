import type { ClientLogLevelCount } from 'common-api';

import { levelClassName, levelLabel } from '../levels';
import { formatDateTime } from '../format';

interface StatsBarProps {
  /** 各级别计数（服务端已补齐没有数据的级别） */
  levels: readonly ClientLogLevelCount[];
  total: number;
  /** 实际生效的时间窗起点 */
  startTime?: number;
  endTime?: number;
  onPickLevel: (level: string | undefined) => void;
}

/**
 * 级别分布条。
 *
 * <p>柱子高度按**相对最大值**归一化，而不是按总数取百分比：日志量集中在 info/error 时，
 * 按总数归一化会让其余柱子全部贴地，看不出任何差异 —— 而"哪个级别在涨"正是要看的。
 */
export function StatsBar({ levels, total, startTime, endTime, onPickLevel }: StatsBarProps) {
  const max = Math.max(1, ...levels.map((item) => item.levelCount ?? 0));

  return (
    <div className="stats">
      <div className="stats__summary">
        <div>
          <span className="stats__total mono">{total}</span>
          <span className="stats__unit">条</span>
        </div>
        <div className="stats__window">
          {formatDateTime(startTime)} ~ {formatDateTime(endTime)}
        </div>
      </div>

      <div className="stats__bars">
        {levels.map((item) => {
          const count = item.levelCount ?? 0;
          const height = Math.max(4, Math.round((count / max) * 100));
          return (
            <button
              key={item.level ?? 'unknown'}
              type="button"
              className="stats__item"
              onClick={() => onPickLevel(item.level)}
              title={`按 ${levelLabel(item.level)} 过滤`}
            >
              <span className="stats__count mono">{count}</span>
              <span className="stats__track">
                <span
                  className={`stats__bar ${levelClassName(item.level)}`}
                  style={{ height: `${height}%` }}
                />
              </span>
              <span className="stats__label">{levelLabel(item.level)}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
