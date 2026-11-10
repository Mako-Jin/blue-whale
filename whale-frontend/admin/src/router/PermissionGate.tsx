// PermissionGate - 进入受保护区域时加载 GET /auth/me，把权限码注入 whale-ui 权限上下文

import { Spin } from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { PermissionProvider } from '@bluewhale/whale-ui';
import { fetchMe } from '../api/auth';
import { clearAuth } from '../api/token';

export default function PermissionGate({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [permissions, setPermissions] = useState<string[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchMe()
      .then((me) => {
        if (!cancelled) {
          setPermissions(me.permissions ?? []);
        }
      })
      .catch(() => {
        if (cancelled) {
          return;
        }
        // 令牌失效（含黑名单命中）→ 清态并回登录页
        clearAuth();
        navigate('/login', { replace: true });
      });
    return () => {
      cancelled = true;
    };
  }, [navigate]);

  if (!permissions) {
    return (
      <div className="admin-loading">
        <Spin size="large" />
      </div>
    );
  }

  return <PermissionProvider permissions={permissions}>{children}</PermissionProvider>;
}
