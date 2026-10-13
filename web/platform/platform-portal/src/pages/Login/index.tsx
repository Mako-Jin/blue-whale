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
  /*
   * "为什么我会在这里" —— 会话在别处被服务端拒绝续期时，那句原因由通道保存着
   * （见 `AuthChannel.consumeAuthNotice`）。
   *
   * 用惰性初值取一次，不用 `useEffect` + `setState`：后者会多一次渲染，
   * 而且 `consumeAuthNotice()` 是"取一次即清空"的语义 —— 放进 effect 里，
   * 严格模式下会被取走两遍，第二次拿到的是 null。
   */
  const [notice] = useState(() => auth.consumeAuthNotice());

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

        {/*
          被"请"到这里来的原因。与下面的 `__error` 分开两处、两种语气：

          - `notice` 解释的是**已经发生的事**（"你刚才为什么会掉出来"），
            它先于本次操作存在，用户还没填任何东西；
          - `error` 说的是**这一次提交失败了**。

          合成一处的话，刚被踢出来的人会以为自己刚才点错了什么。
          文案原样来自服务端 —— 前端另写一套只会变成第二份会过期的说法。
        */}
        {notice ? (
          <p className="login__notice" role="status">
            {notice}
          </p>
        ) : null}

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
          开发提示：**只在 dev 构建里出现**。

          ⚠️ 这段提示曾经是**生产包的一部分** —— `platform-portal` 的 dist 里能直接搜到这两个口令。
          口令本身不是秘密（h2 是开发 profile，真值来源是 `application-h2.yml`），
          但"把口令印在一个会被发布的页面上"是另一件事，所以用 `import.meta.env.DEV` 门控：
          Vite 在生产构建里把它替换成字面量 false，这段连同口令一起被摇掉。

          之所以不干脆删掉：h2 每次启动都是空库（见 sts-server-auth 的 DefaultAdminSeeder），
          没有提示的话第一次跑起来不知道该输什么。但"方便开发"不该以"把口令印在页面上"为代价。

          下面两个口令必须与 `application-h2.yml` 里的种子口令保持一致 ——
          它们是同一个值的两处：后端要口令去种账号，这里要口令去告诉人。
          mysql / dameng 下这段不适用（那些 profile 不自动种，由人显式开一次）。

          刻意也写出只读账号：它是"权限确实生效"的唯一验证入口 ——
          只有管理员时所有接口对唯一身份都放行，权限配错了看不出来。
        */}
        {import.meta.env.DEV ? (
          <p className="login__hint">
            开发环境已种子两个账号（h2 profile 自动种；mysql / dameng 需显式开启一次）， 租户均为{' '}
            <code>default</code>：
            <br />
            管理员 <code>admin</code> / <code>Admin@12345</code>（全部权限）
            <br />
            只读审计员 <code>auditor</code> / <code>Auditor@12345</code>
            （仅读权限，用于对照按钮显隐）
          </p>
        ) : null}
      </form>
    </div>
  );
}
