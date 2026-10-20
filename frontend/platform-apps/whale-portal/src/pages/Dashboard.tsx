// 工作台：顶部统计卡片（我的待办 / 进行中流程 / 未读通知 / 系统服务数）
// + ERP/OA/MES 入口大卡片（点击跳转基座下的兄弟子应用路由）
import { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic } from 'antd';
import { pageMyTask, type WorkflowTask } from '../api/workflow';

/** 门户入口：跳转到基座下已注册的兄弟子应用（/erp /oa /mes） */
const PORTALS = [
  { key: 'erp', title: 'ERP 企业资源计划', desc: '物料 / 采购 / 库存', path: '/erp' },
  { key: 'oa', title: 'OA 协同办公', desc: '流程 / 审批 / 公文', path: '/oa' },
  { key: 'mes', title: 'MES 生产制造', desc: '工单 / 设备 / 质量', path: '/mes' },
];

export default function Dashboard() {
  const [tasks, setTasks] = useState<WorkflowTask[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    void pageMyTask({ pageSize: 100 })
      .then(setTasks)
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic title="我的待办" value={tasks.length} loading={loading} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            {/* TODO: 进行中流程数待接入 /workflow/instance/page 统计，暂用占位常量 */}
            <Statistic title="进行中流程" value={0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            {/* TODO: 未读通知数待接入通知中心，暂用占位常量 */}
            <Statistic title="未读通知" value={0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            {/* TODO: 系统服务数待接入 /monitor/service/list，暂用占位常量 */}
            <Statistic title="系统服务数" value={0} />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginTop: 16 }}>
        {PORTALS.map((portal) => (
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
