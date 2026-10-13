import { useState } from 'react';
import { AuditActions } from 'common-api';
import type { AuditLog } from 'common-api';

import { api } from '../api';
import { describeError } from '../errors';
import { formatClock, formatDuration } from '../format';

/**
 * 演练区 —— 这一页唯一会**改数据**的地方，而它改的不是审计表。
 *
 * <h3>它要证明什么</h3>
 *
 * 审计台最容易停留在"看起来在展示审计"的层面：列表有数据、聚合有条目，
 * 但没人知道那些记录是**谁在什么时候、因为什么**写进去的。
 * 本区把这条因果链当场跑一遍 ——
 * 建一个租户（真实调用写接口）→ 等审计表里出现对应的 `auth.tenant.create` → 删掉它 →
 * 再等一条 `auth.tenant.delete`。两个动作都是别人（后端拦截器）记的，
 * 本应用全程没有、也不能往审计表里写任何东西。
 *
 * <h3>为什么按钮不按权限显隐</h3>
 *
 * 权限管理台的按钮是"没权限就不渲染"（见 `platform-admin` 的 `PermissionButton`）。
 * 这里**刻意反过来**：按钮永远在，点下去该被拒就被拒 ——
 * 因为"有人尝试做一件他没资格做的事"正是审计要留下的东西。
 * 把按钮藏起来，等于把最值得看的那类记录从演示里抹掉了。
 * 用审计员账号跑一遍，本区产出的就是两条 FAILURE 记录（业务码 40301）。
 *
 * <h3>为什么是"等"而不是"查一次"</h3>
 *
 * 审计记录写在请求的收尾阶段（`afterCompletion`），与写操作的响应几乎是同时发生的，
 * 但两者之间没有"先落库再返回"的强保证。这里轮询几次而不是断言"一定立刻可见"：
 * 演示里出现一次"没查到"比多等 300 毫秒代价大得多，
 * 而**等待本身也是要展示的信息** —— 它说明审计是"随后落库"而不是"前端补写"。
 */

type StepKind = 'info' | 'ok' | 'fail';

type Step = {
  kind: StepKind;
  text: string;
  at: number;
};

/** 等待审计记录出现：300 毫秒一轮，最多 5 秒 */
const POLL_INTERVAL_MS = 300;
const POLL_TIMEOUT_MS = 5000;

async function waitForRecord(action: string, targetId: string): Promise<AuditLog | null> {
  const deadline = Date.now() + POLL_TIMEOUT_MS;
  for (;;) {
    const result = await api.audit.page({ action, targetId, page: 1, size: 1 });
    const found = (result.records ?? [])[0];
    if (found) return found;
    if (Date.now() >= deadline) return null;
    await new Promise((resolve) => {
      setTimeout(resolve, POLL_INTERVAL_MS);
    });
  }
}

export function DrillPanel({ onRecorded }: { onRecorded: () => void }) {
  const [running, setRunning] = useState(false);
  const [steps, setSteps] = useState<Step[]>([]);

  const push = (kind: StepKind, text: string): void => {
    setSteps((prev) => [...prev, { kind, text, at: Date.now() }]);
  };

  const run = async (): Promise<void> => {
    setRunning(true);
    setSteps([]);

    // 编码要以字母开头（后端有正则约束），因此拿时间戳的 36 进制拼一个可读且不重复的码
    const tenantCode = `drill${Date.now().toString(36)}`;

    try {
      push('info', `真实调用 POST /api/auth/tenants 建一个租户（tenantCode=${tenantCode}）`);
      const created = await api.access.tenants.create({
        tenantCode,
        tenantName: '审计演练租户',
        description: '由审计日志台的演练区创建，随即删除',
      });
      const tenantId = created.id ?? null;
      push('ok', `租户已创建：id=${String(tenantId)}，业务码 0（成功）`);

      if (tenantId === null) {
        push('fail', '返回体里没有 id，无法继续删除');
        return;
      }

      const createRecord = await waitForRecord(AuditActions.TENANT_CREATE, String(tenantId));
      if (createRecord) {
        push(
          'ok',
          `审计表已出现 ${createRecord.action} · targetId=${String(createRecord.targetId)} · ` +
            `操作人=${String(createRecord.operatorName)} · 耗时 ${formatDuration(createRecord.durationMs)}`,
        );
      } else {
        push(
          'fail',
          `等待 ${POLL_TIMEOUT_MS / 1000} 秒仍未见到 ${AuditActions.TENANT_CREATE} 记录`,
        );
      }

      push('info', `真实调用 DELETE /api/auth/tenants/${tenantId}`);
      await api.access.tenants.remove(tenantId);
      push('ok', '租户已删除：业务码 0（成功）');

      const deleteRecord = await waitForRecord(AuditActions.TENANT_DELETE, String(tenantId));
      if (deleteRecord) {
        push(
          'ok',
          `审计表已出现 ${deleteRecord.action} · targetId=${String(deleteRecord.targetId)} · ` +
            `操作人=${String(deleteRecord.operatorName)}`,
        );
      } else {
        push(
          'fail',
          `等待 ${POLL_TIMEOUT_MS / 1000} 秒仍未见到 ${AuditActions.TENANT_DELETE} 记录`,
        );
      }
    } catch (cause) {
      /*
       * 被拒绝也是**预期结果之一**：用审计员账号跑，第一次调用就会收到 40301。
       * 此时审计表里会留下一条 FAILURE —— 这正是本区想让舰长看到的东西，
       * 所以这里不要说成"演练失败"，要说清"被拒绝，并且它已经留痕"。
       */
      push('fail', `写操作被拒绝：${describeError(cause)}`);
      push('info', '这次尝试不会因为被拒绝而不留痕 —— 去看流水列表里的 FAILURE 行');
    } finally {
      // 无论走到哪一步都刷新：被拒绝的那次同样产生了新记录
      onRecorded();
      setRunning(false);
    }
  };

  return (
    <section className="panel" data-testid="drill-panel">
      <header className="panel__head">
        <div>
          <h2 className="panel__title">演练区 · 写一次，看它被记下来</h2>
          <p className="panel__hint">
            这里真实调用 <code>POST /api/auth/tenants</code> 与{' '}
            <code>DELETE /api/auth/tenants/{'{id}'}</code>，然后等待审计表里出现对应记录。
            <strong>本区按钮刻意不按权限显隐</strong>：用只读账号点下去会被拒绝（40301），
            而那次拒绝本身也会被记成一条 FAILURE —— 审计要看的就是"谁试过"。
          </p>
        </div>
        <div className="panel__actions">
          <button
            type="button"
            className="btn btn--primary drill__run"
            disabled={running}
            onClick={() => void run()}
          >
            {running ? '演练中…' : '跑一次演练'}
          </button>
        </div>
      </header>

      {steps.length === 0 ? (
        <p className="banner" data-testid="drill-idle">
          点「跑一次演练」：建租户 → 等审计记录 → 删租户 → 再等一条。
        </p>
      ) : (
        <ol className="steps" data-testid="drill-steps">
          {steps.map((step) => (
            <li
              key={`${String(step.at)}-${step.text}`}
              className={`steps__item steps__item--${step.kind}`}
            >
              <span className="steps__time mono">{formatClock(step.at)}</span>
              <span className="steps__text">{step.text}</span>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
