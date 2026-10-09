import { levelClassName, levelLabel } from '../levels';

/** 级别标签。配色由 CSS 类决定，因此新增级别只需改 `levels.ts` 与样式表 */
export function LevelTag({ level }: { level: string | undefined }) {
  return <span className={`level-tag ${levelClassName(level)}`}>{levelLabel(level)}</span>;
}
