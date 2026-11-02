// 物料表单弹窗（Form.tsx）单测：必填校验、提交载荷、编辑回填
// 注：未配置 ConfigProvider locale，Modal 底部确认按钮为默认英文「OK」
import { fireEvent, render, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import FormModal from './Form';
import { saveErpItem, type ErpItem } from '../api/erp';

vi.mock('../api/erp', () => ({
  saveErpItem: vi.fn(),
}));

const mockedSave = vi.mocked(saveErpItem);

const item: ErpItem = {
  id: '1',
  code: 'ERP-001',
  name: '六角螺栓',
  status: 'ENABLED',
  remark: 'M8',
};

function renderForm(initial: ErpItem | null = null) {
  const onCancel = vi.fn();
  const onSaved = vi.fn();
  render(<FormModal open initial={initial} onCancel={onCancel} onSaved={onSaved} />);
  const modal = document.querySelector('.ant-modal') as HTMLElement;
  return { onCancel, onSaved, modal };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedSave.mockResolvedValue(item);
});

describe('FormModal（物料表单）', () => {
  it('新增态：必填校验拦截，未通过时不调保存接口', async () => {
    const { onSaved, modal } = renderForm(null);

    expect(await within(modal).findByText('新增')).toBeInTheDocument();
    fireEvent.click(within(modal).getByRole('button', { name: 'OK' }));

    expect(await within(modal).findByText('请输入编码')).toBeInTheDocument();
    expect(await within(modal).findByText('请输入名称')).toBeInTheDocument();
    expect(mockedSave).not.toHaveBeenCalled();
    expect(onSaved).not.toHaveBeenCalled();
  });

  it('新增提交：携带默认状态保存并回调 onSaved', async () => {
    const { onSaved, modal } = renderForm(null);

    await within(modal).findByText('新增');
    fireEvent.change(within(modal).getByPlaceholderText('如 ERP-001'), {
      target: { value: 'ERP-100' },
    });
    fireEvent.change(within(modal).getByPlaceholderText('请输入名称'), {
      target: { value: '测试物料' },
    });
    fireEvent.click(within(modal).getByRole('button', { name: 'OK' }));

    await waitFor(() =>
      expect(mockedSave).toHaveBeenCalledWith(
        expect.objectContaining({
          code: 'ERP-100',
          name: '测试物料',
          status: 'ENABLED',
        }),
      ),
    );
    expect(mockedSave.mock.calls[0][0].id).toBeUndefined();
    expect(onSaved).toHaveBeenCalledTimes(1);
  });

  it('编辑态：标题为编辑，表单回填且提交携带 id', async () => {
    const { onSaved, modal } = renderForm(item);

    expect(await within(modal).findByText('编辑')).toBeInTheDocument();
    expect(within(modal).getByDisplayValue('ERP-001')).toBeInTheDocument();
    expect(within(modal).getByDisplayValue('六角螺栓')).toBeInTheDocument();
    expect(within(modal).getByDisplayValue('M8')).toBeInTheDocument();

    fireEvent.click(within(modal).getByRole('button', { name: 'OK' }));
    await waitFor(() =>
      expect(mockedSave).toHaveBeenCalledWith(expect.objectContaining({ id: '1' })),
    );
    expect(onSaved).toHaveBeenCalledTimes(1);
  });
});
