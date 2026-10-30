// 附件页签：上传、下载与移除

import { useCallback, useEffect, useState } from 'react';
import { App, Button, Empty, List, Space, Spin, Typography, Upload } from 'antd';
import type { UploadProps } from 'antd';
import { DeleteOutlined, DownloadOutlined, PaperClipOutlined, UploadOutlined } from '@ant-design/icons';
import { attachmentApi } from '../../api/attachment';
import { hasPermission } from '../../api/client';
import type { DocAttachment, DocPage } from '../../api/types';

const { Text } = Typography;

/** 附件大小格式化 */
function formatSize(size: number): string {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

/** 附件页签 */
export default function AttachmentTab({ page }: { page: DocPage }) {
  const { message, modal } = App.useApp();
  const [attachments, setAttachments] = useState<DocAttachment[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setAttachments(await attachmentApi.list(page.id));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page.id, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const customRequest: UploadProps['customRequest'] = async (options) => {
    try {
      await attachmentApi.upload(page.id, options.file as File);
      options.onSuccess?.({});
      message.success('附件已上传');
      await load();
    } catch (error) {
      options.onError?.(error as Error);
      message.error((error as Error).message);
    }
  };

  const download = async (attachment: DocAttachment) => {
    try {
      const blob = await attachmentApi.download(attachment.contentUrl);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = attachment.originalName;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const remove = (attachment: DocAttachment) => {
    modal.confirm({
      title: `移除附件「${attachment.originalName}」？`,
      content: '仅解除页面引用，文件实体保留在文件服务中。',
      okText: '移除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await attachmentApi.remove(page.id, attachment.id);
        message.success('附件已移除');
        await load();
      },
    });
  };

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Upload
          showUploadList={false}
          customRequest={customRequest}
          beforeUpload={(file) => {
            const limit = 50 * 1024 * 1024;
            if (file.size > limit) {
              message.error('附件不能超过 50MB');
              return Upload.LIST_IGNORE;
            }
            return true;
          }}
        >
          <Button
            icon={<UploadOutlined />}
            disabled={!page.canEdit || !hasPermission('sys:doc:attachment:manage')}
          >
            上传附件
          </Button>
        </Upload>
        <Text type="secondary">附件经文件服务内网通道代理读取</Text>
      </Space>
      <Spin spinning={loading}>
        <List
          dataSource={attachments}
          locale={{ emptyText: <Empty description="暂无附件" /> }}
          renderItem={(attachment) => (
            <List.Item
              actions={[
                <Button key="download" type="link" size="small" icon={<DownloadOutlined />}
                  onClick={() => void download(attachment)}>
                  下载
                </Button>,
                <Button key="remove" type="link" size="small" danger icon={<DeleteOutlined />}
                  disabled={!page.canEdit || !hasPermission('sys:doc:attachment:manage')}
                  onClick={() => remove(attachment)}>
                  移除
                </Button>,
              ]}
            >
              <List.Item.Meta
                avatar={<PaperClipOutlined />}
                title={attachment.originalName}
                description={`${formatSize(attachment.fileSize)} · ${attachment.contentType}`}
              />
            </List.Item>
          )}
        />
      </Spin>
    </div>
  );
}
