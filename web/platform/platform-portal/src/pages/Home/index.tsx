import { Link, useLocation } from 'react-router-dom';
import { MICRO_APPS } from '@/micro';
import { usePermissions } from '@/auth/hooks';
import './index.less';

/** `RequireMenu` 重定向过来时带的状态 */
type DeniedState = {
  denied?: {
    path: string;
    permission: string;
  };
};

export default function Home() {
  const permissions = usePermissions();
  const location = useLocation();
  const denied = (location.state as DeniedState | null)?.denied;

  /*
   * 概览页本身**不纳管权限**（任何已登录身份都看得到它），但它列出的入口要过滤 ——
   * 否则首页会变成整个应用里唯一的"假开关集合"：一堆点了就被弹回的卡片。
   *
   * 与基座导航栏、`RequireMenu` 读同一个字段，三处永远一致。
   */
  const visibleApps = MICRO_APPS.filter((app) => permissions.includes(app.menuPermission));

  return (
    <section className="home">
      <h1 className="home__title">平台门户已就绪</h1>
      <p className="home__desc">
        主应用是 qiankun 容器，只负责导航骨架与全局状态下发；具体功能由子应用提供。
        下面两个入口既是端到端演示页，也都是**能独立启动**的应用 ——
        单独跑（各自端口）与被主应用加载（本页）走的是同一份代码，这正是要验证的点。
      </p>

      {/*
        被弹回来时解释一句。没有这段的话，敲一个没权限的地址表现为"什么都没发生"，
        而人只会怀疑是应用坏了 —— 这里如实说清是**这个入口对该身份不存在**，
        并给出它需要的那一个权限码（骂人也要指名道姓）。
      */}
      {denied ? (
        <p className="home__denied" data-testid="home-denied">
          你没有进入 <code>{denied.path}</code> 的权限（需要 <code>{denied.permission}</code>
          ），已返回概览。
        </p>
      ) : null}

      <ul className="home__list">
        {visibleApps.map((app) => (
          <li key={app.name} className="home__item">
            <div className="home__item-head">
              <Link className="home__link" to={app.routePath}>
                {app.title}
              </Link>
              <code className="home__name">{app.name}</code>
            </div>
            <p className="home__summary">{app.summary}</p>
          </li>
        ))}
      </ul>

      {visibleApps.length === 0 ? (
        /*
         * 空状态要说三件事，缺一件它就只是一句"你没有权限"：
         * **缺什么**（菜单码）、**到哪补**（权限管理后台的哪一页哪一步）、
         * **补完怎么让它生效**（顶栏重查，不必重新登录）。
         *
         * 第三件此前没有人提，而它恰恰是必须的：权限集合只在登录与刷新令牌时回来，
         * 界面自己算不出来（见 `auth/index.ts` 里那段说明）。所以"管理员授好了"
         * 与"你这里能看见了"之间隔着一次会话更新，不说清这一点，用户会以为
         * 授权没生效，然后重复去找管理员 —— 或者干脆重新登录一遍。
         *
         * 路径写成「应用名 → 标签页 → 按钮」而不是接口路径：读这句话的人是
         * 一个刚登进来、看不到任何入口的人，他能做的只有照着界面点。
         *
         * **每行一句、行尾用 `<br />` 断开**：JSX 会把跨行的文本按空格拼起来，
         * 中文句子被 prettier 折行后会多出一个空格（`另一个 已持有`）。
         * 行内不换行 + 每行在 printWidth 之内，既躲开这个坑，也让 prettier 不动这几行。
         */
        <p className="home__empty" data-testid="home-empty">
          当前身份没有可进入的应用。菜单码（<code>ui:*:view</code>）决定入口出不出现。
          <br />
          它由管理员在「权限管理后台」里授：先到<b>角色</b>页给某个角色做「资源授权」。
          <br />
          勾上对应的菜单码之后，在同一个角色里把当前账号<b>挂为成员</b>。
          <br />
          授完回到本页，在顶栏点「重新检查权限」即可，<b>不必重新登录</b>。
          <br />
          若提示「权限没有变化」，请让管理员核对授的是不是当前这个账号。
        </p>
      ) : null}

      <div className="home__note">
        <p className="home__note-title">首次调试请先起子应用的 dev server</p>
        <pre className="home__code">
          {`pnpm --filter platform-log-console dev     # :3200
pnpm --filter platform-event-console dev   # :3300`}
        </pre>
        <p className="home__note-desc">
          子应用没起时不会白屏：入口会显示"加载失败"并给出端口提示。
        </p>
      </div>
    </section>
  );
}
