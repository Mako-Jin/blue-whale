// 二次认证（Wave 6.22）：危险操作返回 428/42801 时弹窗验密，
// 换取 step-up 凭证后由 @bluewhale/http 自动带 X-Reauth-Token 重放原请求。
// 凭证 5 分钟窗口内复用——窗口内第二个危险操作不会再弹窗。

import { Input, Modal, message } from 'antd';
import { ApiError, post } from './client';

/** 单飞：并发多个 428 共用同一次弹窗与同一凭证 */
let pending: Promise<string | null> | null = null;

/**
 * 弹出「确认身份」对话框并返回 step-up 凭证。
 *
 * @returns 凭证；用户取消时返回 null（原请求以 42801 失败）
 */
export function promptReauth(): Promise<string | null> {
  if (pending) {
    return pending;
  }
  pending = openDialog().finally(() => {
    pending = null;
  });
  return pending;
}

/** 实际弹窗实现：口令校验失败时弹窗保持打开，允许重试或取消 */
function openDialog(): Promise<string | null> {
  return new Promise((resolve) => {
    let password = '';
    Modal.confirm({
      title: '确认身份',
      width: 420,
      content: (
        <div>
          <p style={{ marginBottom: 12, color: 'rgba(0,0,0,0.65)' }}>
            该操作属于危险操作，需要重新验证当前密码（验证后 5 分钟内有效）。
          </p>
          <Input.Password
            autoFocus
            placeholder="当前登录密码"
            onChange={(e) => {
              password = e.target.value;
            }}
          />
        </div>
      ),
      okText: '验证',
      cancelText: '取消',
      onOk: async () => {
        if (!password) {
          message.warning('请输入当前密码');
          throw new Error('empty-password');
        }
        try {
          const data = await post<{ token: string; expiresIn: number }>('/auth/v1/reauth', { password });
          resolve(data.token);
        } catch (error) {
          message.error(error instanceof ApiError ? error.message : '验证失败，请重试');
          // 抛出使 Modal 不关闭，用户可修正口令后重试
          throw error;
        }
      },
      onCancel: () => resolve(null),
    });
  });
}
