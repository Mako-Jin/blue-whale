import { useState } from 'react';

import { auth } from '@/auth';
import { describeAuthError } from '@/auth/describe-error';
import './dialog.less';

/**
 * 「退出所有设备」的确认对话框。
 *
 * <h3>为什么这个动作要一次确认，而登出不要</h3>
 *
 * 登出是可以从界面上重做的：再点一次登录就回来了，代价是输一遍口令。
 * 退出所有设备不是"重做就抵消"的事 —— 它的对象是**别的设备上那些会话**，
 * 而在别的设备上被踢下线的人是用户自己，且他当时可能正在填一张表单。
 * 一个只有一个按钮、点下去就影响到用户看不见的地方的动作，必须先把后果说出来。
 *
 * <h3>为什么失败时留在原地、且不清本地会话</h3>
 *
 * `auth.logoutAll()` 失败时会抛（与 `logout` 刻意相反，见 `AuthChannel.logoutAll`）：
 * 这个动作的意图是"别处也下线"，而清本地会话**达不成**它。
 * 若像登出那样吞掉失败再清本地，用户会带着"我已经把别人踢了"的错觉回到登录页 ——
 * 一次静默的安全失败，且恰好发生在他怀疑账号被人登着的时候。
 * 所以这里把错误显示出来、按钮恢复可点，让他能直接再试一次。
 *
 * <h3>为什么没有"成功"分支</h3>
 *
 * 与 `ChangePasswordDialog` 同一个理由：成功意味着本地会话已被清空、页面正在去登录页
 * 的路上。此刻再 `setState` 只会写进一个即将卸载的组件 —— 那不是提示，是自我安慰。
 */
export function LogoutAllDialog({ onClose }: { onClose: () => void }) {
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleConfirm(): Promise<void> {
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await auth.logoutAll();
    } catch (cause) {
      setError(describeAuthError(cause, '退出所有设备失败，请重试'));
      setSubmitting(false);
    }
  }

  return (
    <div className="bw-dialog-mask" role="presentation" onClick={onClose}>
      <div
        className="bw-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="退出所有设备"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="bw-dialog__head">
          <h3 className="bw-dialog__title">退出所有设备</h3>
          <button type="button" className="bw-dialog__close" aria-label="关闭" onClick={onClose}>
            ×
          </button>
        </header>

        <div className="bw-dialog__body">
          {/*
            两句后果都要点到，各对应服务端的一半行为：
            前一句是"撤了哪些会话"，后一句是"什么时候生效" ——
            访问令牌是无状态的、仍能用到最长一个令牌有效期（默认 2 小时），
            所以别的设备上**正在打开**的页面不会立刻掉线。
            只说"立刻退出"会让用户以为没生效，从而重复点。
          */}
          <p className="bw-dialog__notice">
            将结束该账号在所有设备上的登录，包括当前这个。其他设备正在打开
            的页面不会立刻掉线，它们会在下一次请求时被送回登录页。
          </p>
          <p className="bw-dialog__notice">
            口令不变。想连口令一起换，用旁边的「修改口令」；
            怀疑账号正被别人使用时，两件事一起做最稳妥。
          </p>

          {error ? (
            <p className="bw-dialog__error" role="alert">
              {error}
            </p>
          ) : null}

          <div className="bw-dialog__actions">
            <button type="button" className="bw-dialog__cancel" onClick={onClose}>
              取消
            </button>
            <button
              type="button"
              className="bw-dialog__submit"
              data-testid="confirm-logout-all"
              disabled={submitting}
              onClick={() => void handleConfirm()}
            >
              {submitting ? '处理中…' : '确认退出'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
