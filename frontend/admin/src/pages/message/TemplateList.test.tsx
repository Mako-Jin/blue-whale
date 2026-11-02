// 消息模板管理页组件测试：列表渲染 / 权限门控 / 启停开关 / 编辑弹窗 / 删除确认
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TemplateList from './TemplateList';
import {
  deleteMessageTemplate,
  pageMessageTemplates,
  updateMessageTemplate,
  type MessageTemplateVO,
} from '../../api/message';
import { pageResult, renderPage } from '../../test/test-utils';

vi.mock('../../api/message', () => ({
  pageMessageTemplates: vi.fn(),
  createMessageTemplate: vi.fn(),
  updateMessageTemplate: vi.fn(),
  deleteMessageTemplate: vi.fn(),
}));

const TEMPLATE_PERMS = [
  'sys:message:template:add',
  'sys:message:template:edit',
  'sys:message:template:delete',
];

const template: MessageTemplateVO = {
  id: '1',
  templateCode: 'ORDER_PAID_NOTICE',
  templateName: '订单支付通知',
  titleTemplate: '【订单】支付成功',
  contentTemplate: '您的订单 ${orderNo} 已支付',
  status: 1,
  updateTime: '2026-11-01 10:00:00',
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(pageMessageTemplates).mockResolvedValue(pageResult([template]));
});

describe('TemplateList 消息模板页', () => {
  it('渲染列表：编码、名称、标题/内容模板与启用开关', async () => {
    renderPage(<TemplateList />, TEMPLATE_PERMS);

    expect(await screen.findByText('ORDER_PAID_NOTICE')).toBeInTheDocument();
    expect(screen.getByText('订单支付通知')).toBeInTheDocument();
    expect(screen.getByText('【订单】支付成功')).toBeInTheDocument();
    expect(screen.getByText('您的订单 ${orderNo} 已支付')).toBeInTheDocument();
    expect(screen.getByText('消息模板')).toBeInTheDocument();
    expect(screen.getByRole('switch')).toBeEnabled();
  });

  it('无权限时隐藏新增/编辑/删除，开关禁用', async () => {
    renderPage(<TemplateList />, []);

    await screen.findByText('ORDER_PAID_NOTICE');
    expect(screen.queryByRole('button', { name: '新增模板' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /编\s*辑/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /删\s*除/ })).not.toBeInTheDocument();
    expect(screen.getByRole('switch')).toBeDisabled();
  });

  it('切换开关携带停用状态调用更新接口并刷新', async () => {
    vi.mocked(updateMessageTemplate).mockResolvedValue({ ...template, status: 0 });
    renderPage(<TemplateList />, TEMPLATE_PERMS);

    fireEvent.click(await screen.findByRole('switch'));

    await waitFor(() => {
      expect(updateMessageTemplate).toHaveBeenCalledWith(
        '1',
        expect.objectContaining({ id: '1', templateCode: 'ORDER_PAID_NOTICE', status: 0 }),
      );
    });
    await waitFor(() => {
      expect(pageMessageTemplates).toHaveBeenCalledTimes(2);
    });
  });

  it('编辑弹窗回填且模板编码锁定', async () => {
    renderPage(<TemplateList />, TEMPLATE_PERMS);

    fireEvent.click(await screen.findByRole('button', { name: /编\s*辑/ }));
    expect(await screen.findByText('编辑模板：订单支付通知')).toBeInTheDocument();
    // 搜索表单也有「模板编码」字段，需限定在弹窗内查询；jsdom 下该 label 关联判定异常，改按值定位
    const modal = document.querySelector('.ant-modal') as HTMLElement;
    const codeInput = within(modal).getByDisplayValue('ORDER_PAID_NOTICE') as HTMLInputElement;
    expect(codeInput.tagName).toBe('INPUT');
    expect(codeInput).toBeDisabled();
  });

  it('删除经确认后调用删除接口', async () => {
    vi.mocked(deleteMessageTemplate).mockResolvedValue(undefined);
    renderPage(<TemplateList />, TEMPLATE_PERMS);

    fireEvent.click(await screen.findByRole('button', { name: /删\s*除/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'OK' }));

    await waitFor(() => {
      expect(deleteMessageTemplate).toHaveBeenCalledWith('1');
    });
  });
});
