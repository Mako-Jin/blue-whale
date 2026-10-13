import { createConsoleAuth } from 'common-api';

/**
 * 本应用的认证接线 —— **「从哪取令牌」的唯一答案**，没有登录页。
 *
 * <h3>为什么这里没有登录页</h3>
 *
 * 依据 `docs/decisions/0018` 第 8 条：子应用单跑时走**同一份会话实现**，
 * 但不得再写一个登录页。登录逻辑一旦复制出第二份，就会出现两套跳转、
 * 两套错误提示、两套会话清理。独立运行时的起步方式因此是"用开发账号自动登录"
 * （见下面的 `devAccount`），复用的仍是 `common-api` 的 `createAuthChannel`。
 *
 * <h3>审计台比权限管理台更需要它</h3>
 *
 * 本应用**几乎不自己发写请求**（审计只有 GET），但每个 GET 都要带令牌 ——
 * 换句话说，它几乎 100% 的请求都是"已鉴权请求"，认证没接好就一屏 401。
 *
 * <h3>实现已经下沉，本文件只剩一份参数</h3>
 *
 * 上述分支逻辑（宿主桥优先、独立运行自建会话、按需建会话避免两套并存）
 * 此前在本应用与 `platform-admin` 里各写了一份**逐字相同**的副本，
 * 当时是**有意留着的重复**（`platform-*` 之间不得互相依赖，触发下沉的条件
 * 写在 `web/README.md`："出现第三个管理型子应用"）。日志台与事件台是第三、
 * 第四个消费方，`createConsoleAuth` 就是在那时新增的；本轮把这两份旧副本一并收进来
 * （见 `docs/decisions/0034`），四个应用的接线至此**同形**。
 *
 * 收进来的不只是一份代码。旧副本自己实现了**两段式初始化**
 * （`initializeAuth()` 先定端口、`getAuthPort()` 再取），于是每个调用点都要
 * 保证"先初始化、再使用"，`api.ts` 也只好把客户端改成延迟创建。
 * `createConsoleAuth` 的端口是**构造即就绪**的 ——
 * 令牌在**每次请求时**才去问"基座在不在"，因此那两段式与随之而来的
 * 延迟创建都不再需要，少掉的正是"顺序错了会静默降级成独立运行"这类缺陷的落点。
 */
export const consoleAuth = createConsoleAuth({
  name: 'audit',
  /** 开发账号：可用环境变量覆盖，默认指向 h2 profile 的种子管理员 */
  // **只在开发构建里构造**：生产构建下 `import.meta.env.DEV` 是常量假，
  // 整个对象字面量被静态摇掉，口令因此不进入产物（`docs/decisions/0033`，
  // 由 `scripts/verify-bundle-secrets.mjs` 盯着）。**不传 = 不自动登录**。
  devAccount: import.meta.env.DEV
    ? {
        tenantCode: import.meta.env.VITE_DEV_TENANT_CODE ?? 'default',
        username: import.meta.env.VITE_DEV_USERNAME ?? 'admin',
        password: import.meta.env.VITE_DEV_PASSWORD ?? 'Admin@12345',
      }
    : undefined,
});
