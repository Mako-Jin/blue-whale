// 消息通知：占位列表（3 条静态示例）
import { Card, List, Tag } from 'antd';

interface NoticeItem {
  id: number;
  title: string;
  type: string;
  time: string;
}

// TODO: 接入通知中心（替换为通知服务接口，当前为静态占位数据）
const MOCK_NOTICES: NoticeItem[] = [
  {
    id: 1,
    title: '系统升级通知：平台将于本周六凌晨进行维护',
    type: '系统',
    time: '2026-10-20 09:00',
  },
  { id: 2, title: '您有 3 条待办流程即将超时，请及时处理', type: '流程', time: '2026-10-20 08:30' },
  { id: 3, title: '欢迎使用 Whale 企业门户', type: '公告', time: '2026-10-19 18:00' },
];

export default function Notice() {
  return (
    <Card title="消息通知">
      <List
        dataSource={MOCK_NOTICES}
        renderItem={(item) => (
          <List.Item extra={<Tag>{item.type}</Tag>}>
            <List.Item.Meta title={item.title} description={item.time} />
          </List.Item>
        )}
      />
    </Card>
  );
}
