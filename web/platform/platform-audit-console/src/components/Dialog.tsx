import type { ReactNode } from 'react';

/**
 * 一个极简的模态框 —— 与 `platform-admin` 的那份同构。
 *
 * <p>本应用只在"看某一条审计记录的完整上下文"时用它，因此没有做成通用组件库；
 * 两个应用各持一份而不是抽公共包，理由见 `src/auth/index.ts` 的说明
 * （多应用共享的 UI 层要连同测试一起搬，收益不该混进管道的实现里）。
 */
export function Dialog({
  title,
  onClose,
  children,
  footer,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <div className="dialog__mask" role="presentation" onClick={onClose}>
      <div
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
      >
        <header className="dialog__head">
          <h3 className="dialog__title">{title}</h3>
          <button type="button" className="dialog__close" aria-label="关闭" onClick={onClose}>
            ×
          </button>
        </header>
        <div className="dialog__body">{children}</div>
        {footer ? <footer className="dialog__foot">{footer}</footer> : null}
      </div>
    </div>
  );
}
