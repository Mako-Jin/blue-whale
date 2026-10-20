// 服务监控面板：服务概览表 + 点击行加载实例列表 + 刷新

import { useCallback, useEffect, useState } from 'react';
import { App, Button, Card, Space, Table, Tag, type TableProps } from 'antd';
import { getInstances, getServiceList, type ServiceInstanceVO, type ServiceSummaryVO } from '../api/monitor';

const serviceColumns: TableProps<ServiceSummaryVO>['columns'] = [
  { title: '服务名', dataIndex: 'serviceName' },
  { title: '实例数', dataIndex: 'instanceCount', width: 120 },
  {
    title: '健康实例数',
    dataIndex: 'healthyInstanceCount',
    width: 140,
    render: (value: number, row) => (
      <Tag color={value === row.instanceCount && value > 0 ? 'success' : 'warning'}>{value}</Tag>
    ),
  },
];

const instanceColumns: TableProps<ServiceInstanceVO>['columns'] = [
  {
    title: 'IP:PORT',
    key: 'endpoint',
    render: (_, row) => `${row.host}:${row.port}`,
  },
  {
    title: '健康状态',
    dataIndex: 'healthy',
    width: 120,
    render: (healthy: boolean) => <Tag color={healthy ? 'success' : 'error'}>{healthy ? '健康' : '不健康'}</Tag>,
  },
  { title: '状态', dataIndex: 'status', width: 120, render: (value?: string) => value ?? '-' },
  { title: '元数据摘要', dataIndex: 'serviceName', render: (value?: string) => value ?? '-' },
];

export default function Monitor() {
  const { message } = App.useApp();
  const [services, setServices] = useState<ServiceSummaryVO[]>([]);
  const [servicesLoading, setServicesLoading] = useState(false);
  const [selectedService, setSelectedService] = useState<string | null>(null);
  const [instances, setInstances] = useState<ServiceInstanceVO[]>([]);
  const [instancesLoading, setInstancesLoading] = useState(false);

  const loadServices = useCallback(async () => {
    setServicesLoading(true);
    try {
      setServices(await getServiceList());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '服务列表加载失败');
      setServices([]);
    } finally {
      setServicesLoading(false);
    }
  }, [message]);

  const loadInstances = useCallback(
    async (serviceName: string) => {
      setInstancesLoading(true);
      try {
        setInstances(await getInstances(serviceName));
      } catch (error) {
        message.error(error instanceof Error ? error.message : '实例列表加载失败');
        setInstances([]);
      } finally {
        setInstancesLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    void loadServices();
  }, [loadServices]);

  const handleSelect = (row: ServiceSummaryVO) => {
    setSelectedService(row.serviceName);
    void loadInstances(row.serviceName);
  };

  const handleRefresh = () => {
    void loadServices();
    if (selectedService) {
      void loadInstances(selectedService);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        title="服务概览"
        extra={<Button onClick={handleRefresh}>刷新</Button>}
      >
        <Table<ServiceSummaryVO>
          rowKey="serviceName"
          columns={serviceColumns}
          dataSource={services}
          loading={servicesLoading}
          pagination={false}
          onRow={(row) => ({
            onClick: () => handleSelect(row),
            style: {
              cursor: 'pointer',
              background: row.serviceName === selectedService ? 'rgba(24, 144, 255, 0.08)' : undefined,
            },
          })}
        />
      </Card>

      <Card title={selectedService ? `实例列表：${selectedService}` : '实例列表（点击上方服务行查看）'}>
        <Table<ServiceInstanceVO>
          rowKey={(row) => `${row.host}:${row.port}`}
          columns={instanceColumns}
          dataSource={instances}
          loading={instancesLoading}
          pagination={false}
          locale={{ emptyText: selectedService ? '暂无实例' : '请选择服务' }}
        />
      </Card>
    </Space>
  );
}
