// 工作台：顶部统计卡片（我的待办 / 进行中流程 / 未读通知 / 系统服务数）
// + ERP/OA/MES 入口大卡片（点击跳转基座下的兄弟子应用路由）
//
// 统计全部接真实接口；无权限或接口不可用时展示 `—`，不阻塞页面。
import { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic } from 'antd';
import { getCurrentUser, hasPermission } from '../api/client';
import { countServices } from '../api/monitor';
import { listConversations, pageAlertRecords } from '../api/notice';
import { countRunningInstances, pageMyTaskPage } from '../api/workflow';

/** 门户入口：跳转到基座下已注册的兄弟子应用（/erp /oa /mes），按权限裁剪 */
const PORTALS = [
  {
    key: 'erp',
    title: 'ERP 企业资源计划',
    desc: '物料 / 采购 / 库存',
    path: '/erp',
    permission: 'sys:erp:item:list',
  },
  {
    key: 'oa',
    title: 'OA 协同办公',
    desc: '流程 / 审批 / 公文',
    path: '/oa',
    permission: 'sys:oa:leave:list',
  },
  {
    key: 'mes',
    title: 'MES 生产制造',
    desc: '工单 / 设备 / 质量',
    path: '/mes',
    permission: 'sys:mes:workorder:list',
  },
];

/** 统计值：数字或不可用占位 */
type StatValue = number | '—';

const UNAVAILABLE: StatValue = '—';

export default function Dashboard() {
  const [todoTotal, setTodoTotal] = useState<StatValue>(0);
  const [runningTotal, setRunningTotal] = useState<StatValue>(0);
  const [unreadTotal, setUnreadTotal] = useState<StatValue>(0);
  const [serviceTotal, setServiceTotal] = useState<StatValue>(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const username = getCurrentUser()?.username;
    const tasks: Promise<unknown>[] = [];

    // 我的待办：当前用户的待办任务总数
    if (username) {
      tasks.push(
        pageMyTaskPage({ pageSize: 1, assignee: username }).then((page) =>
          setTodoTotal(page.total),
        ),
      );
    } else {
      setTodoTotal(UNAVAILABLE);
    }

    // 进行中流程：实例状态 0（运行中）
    tasks.push(
      countRunningInstances()
        .then(setRunningTotal)
        .catch(() => setRunningTotal(UNAVAILABLE)),
    );

    // 未读通知：优先 IM 未读汇总，其次告警触发中数量
    if (hasPermission('sys:im:chat')) {
      tasks.push(
        listConversations()
          .then((list) => setUnreadTotal(list.reduce((sum, item) => sum + (item.unread ?? 0), 0)))
          .catch(() => setUnreadTotal(UNAVAILABLE)),
      );
    } else if (hasPermission('sys:alert:record:list')) {
      tasks.push(
        pageAlertRecords({ pageSize: 1, status: 'FIRING' })
          .then((page) => setUnreadTotal(Number(page.total) || 0))
          .catch(() => setUnreadTotal(UNAVAILABLE)),
      );
    } else {
      setUnreadTotal(UNAVAILABLE);
    }

    // 系统服务数：Nacos 注册服务摘要
    if (hasPermission('sys:monitor:list')) {
      tasks.push(
        countServices()
          .then(setServiceTotal)
          .catch(() => setServiceTotal(UNAVAILABLE)),
      );
    } else {
      setServiceTotal(UNAVAILABLE);
    }

    void Promise.allSettled(tasks).then(() => setLoading(false));
  }, []);

  return (
    <div>
      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic title="我的待办" value={todoTotal} loading={loading} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="进行中流程" value={runningTotal} loading={loading} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="未读通知" value={unreadTotal} loading={loading} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="系统服务数" value={serviceTotal} loading={loading} />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginTop: 16 }}>
        {PORTALS.filter((portal) => hasPermission(portal.permission)).map((portal) => (
          <Col span={8} key={portal.key}>
            <Card
              hoverable
              onClick={() => {
                // 跳转基座路由，由基座激活对应子应用
                window.location.href = portal.path;
              }}
            >
              <Card.Meta title={portal.title} description={portal.desc} />
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  );
}
