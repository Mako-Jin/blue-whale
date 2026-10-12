import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { auth } from '@/auth';
import { describeAuthError } from '@/auth/describe-error';
import { useSession } from '@/auth/hooks';
import './index.less';

/** 登录成功后要回到的路径。只接受站内路径，避免被 `state.from` 带去外部站点 */
function readRedirectTarget(state: unknown): string {
  if (state && typeof state === 'object' && 'from' in state) {
    const from = (state as { from?: unknown }).from;
    // `//host` 是协议相对 URL，会跳出本站 —— 必须一起挡掉
    if (typeof from === 'string' && from.startsWith('/') && !from.startsWith('//')) return from;
  }
  return '/';
}

export default function Login() {
  const [tenantCode, setTenantCode] = useState('default');
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { authenticated } = useSession();
  const location = useLocation();
  const navigate = useNavigate();
  const target = readRedirectTarget(location.state);

  /*
   * **全页唯一的跳转机制**：登录成功、或本来就已经登录时，都从这里离开。
   * 提交处理函数里不写 `navigate` —— 两处跳转必然有一天会不一致
   * （例如"已登录用户直接访问 /login"只有这条路径能覆盖）。
   */
  useEffect(() => {
    if (authenticated) navigate(target, { replace: true });
  }, [authenticated, navigate, target]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (submitting) return;

    setSubmitting(true);
    setError(null);
    try {
      await auth.login({ tenantCode, username, password });
      // 成功后不手动跳转：会话变化会触发上面的 effect
    } catch (cause) {
      setError(describeAuthError(cause));
      setSubmitting(false);
    }
  }

  return (
    <div className="login">
      <form className="login__card" onSubmit={handleSubmit}>
        <h1 className="login__title">BlueWhale</h1>
        <p className="login__subtitle">登录后由基座把身份与权限下发给各子应用</p>

        <label className="login__field">
          <span className="login__label">租户编码</span>
          <input
            className="login__input"
            name="tenantCode"
            autoComplete="organization"
            value={tenantCode}
            onChange={(event: ChangeEvent<HTMLInputElement>) => setTenantCode(event.target.value)}
            required
          />
        </label>

        <label className="login__field">
          <span className="login__label">用户名</span>
          <input
            className="login__input"
            name="username"
            autoComplete="username"
            value={username}
            onChange={(event: ChangeEvent<HTMLInputElement>) => setUsername(event.target.value)}
            required
          />
        </label>

        <label className="login__field">
          <span className="login__label">口令</span>
          <input
            className="login__input"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event: ChangeEvent<HTMLInputElement>) => setPassword(event.target.value)}
            required
          />
        </label>

        {error ? (
          <p className="login__error" role="alert">
            {error}
          </p>
        ) : null}

        <button className="login__submit" type="submit" disabled={submitting}>
          {submitting ? '登录中…' : '登录'}
        </button>

        {/*
          开发提示：h2 profile 会种子一套账号（见 sts-server-auth 的 DefaultAdminSeeder），
          否则空库没有任何账号可登录，而建账号的接口本身要求已登录。
          换到 mysql / dameng 时不会种，真实环境的账号由运维显式创建。

          刻意把**只读账号**也写出来：它是"权限确实生效"的唯一验证入口 ——
          只有管理员时所有接口对唯一身份都放行，权限配错了看不出来。
          也正因为如此，这两个口令都必须与 DefaultAdminSeeder / AuthProperties 保持一致。
        */}
        <p className="login__hint">
          开发环境（h2 profile）已种子两个账号，租户均为 <code>default</code>：
          <br />
          管理员 <code>admin</code> / <code>Admin@12345</code>（全部权限）
          <br />
          只读审计员 <code>auditor</code> / <code>Auditor@12345</code>（仅读权限，用于对照按钮显隐）
        </p>
      </form>
    </div>
  );
}
