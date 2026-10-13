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
        <p className="home__empty" data-testid="home-empty">
          当前身份没有可进入的应用。请让管理员给这个账号授予对应的菜单码（<code>ui:*:view</code>）。
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
