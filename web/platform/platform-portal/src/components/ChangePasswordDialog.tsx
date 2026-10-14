import { useState, type ChangeEvent, type FormEvent } from 'react';

import { auth } from '@/auth';
import { describeAuthError } from '@/auth/describe-error';
import './dialog.less';
import './ChangePasswordDialog.less';

/**
 * 修改口令对话框 —— 本人改自己的口令。
 *
 * <h3>为什么这个表单只有一条客户端校验</h3>
 *
 * "两次新口令一致"是**只有客户端能判**的一条：服务端只收到一个新口令，
 * 它无从知道用户有没有把新口令打错一遍。其余规则（长度、字符类别、不得与旧口令相同、
 * 不得是最近用过的）一律**不在前端重写** —— 它们已经有唯一实现（服务端的
 * `PasswordChanger`），前端再写一份就是同一个规则的两处实现，而 ADR-0020 决策 2
 * 已经为此删掉过 DTO 上的 `@Size(min = 8)`。前端把提交后拿到的错误**原样展示**，
 * 连文案也由服务端给（只有它知道缺哪几项）。
 *
 * <h3>为什么"会被登出"这句话必须在按钮上方</h3>
 *
 * 改密成功后服务端会撤销该账号的**全部**刷新令牌（含本次会话手上这一个），
 * 于是 `AuthChannel.changePassword` 顺手结束了本地会话 —— 页面随即被送到登录页。
 * 界面没有机会在事后解释这件事（那时它已经卸载了），所以这句话只能前置。
 * 反过来，如果把它写在成功回调里，用户看到的会是"改密成功 → 突然回到登录页"。
 *
 * <h3>为什么没有"成功"分支</h3>
 *
 * 成功意味着会话已被清空、页面正在去登录页的路上。此刻再 `setState`
 * 只会写进一个即将卸载的组件 —— 那不是提示，是自我安慰。
 */
export function ChangePasswordDialog({ onClose }: { onClose: () => void }) {
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (submitting) return;

    if (newPassword !== confirmPassword) {
      setError('两次输入的新口令不一致');
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await auth.changePassword(oldPassword, newPassword);
    } catch (cause) {
      setError(describeAuthError(cause, '修改失败，请重试'));
      setSubmitting(false);
    }
  }

  return (
    <div className="bw-dialog-mask" role="presentation" onClick={onClose}>
      <div
        className="bw-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="修改口令"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="bw-dialog__head">
          <h3 className="bw-dialog__title">修改口令</h3>
          <button type="button" className="bw-dialog__close" aria-label="关闭" onClick={onClose}>
            ×
          </button>
        </header>

        <form className="bw-dialog__body" onSubmit={handleSubmit}>
          <label className="change-password__field">
            <span className="change-password__label">当前口令</span>
            <input
              className="change-password__input"
              name="oldPassword"
              type="password"
              autoComplete="current-password"
              value={oldPassword}
              onChange={(event: ChangeEvent<HTMLInputElement>) =>
                setOldPassword(event.target.value)
              }
              required
            />
          </label>

          <label className="change-password__field">
            <span className="change-password__label">新口令</span>
            <input
              className="change-password__input"
              name="newPassword"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(event: ChangeEvent<HTMLInputElement>) =>
                setNewPassword(event.target.value)
              }
              required
            />
          </label>

          <label className="change-password__field">
            <span className="change-password__label">确认新口令</span>
            <input
              className="change-password__input"
              name="confirmPassword"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(event: ChangeEvent<HTMLInputElement>) =>
                setConfirmPassword(event.target.value)
              }
              required
            />
          </label>

          {error ? (
            <p className="bw-dialog__error" role="alert">
              {error}
            </p>
          ) : null}

          {/*
            把后果说在动作之前。与服务端行为一一对应：
            撤销的是**全部**刷新令牌（含本次会话），所以"其他设备"与"当前这个"
            都必须点到，否则用户会以为只有别处被踢。
          */}
          <p className="bw-dialog__notice">
            修改后会结束该账号在所有设备上的登录（含当前这个），需要立刻用新口令重新登录。
          </p>

          <div className="bw-dialog__actions">
            <button type="button" className="bw-dialog__cancel" onClick={onClose}>
              取消
            </button>
            <button className="bw-dialog__submit" type="submit" disabled={submitting}>
              {submitting ? '提交中…' : '确认修改'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
