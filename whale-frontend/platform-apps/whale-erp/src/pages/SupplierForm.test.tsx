// SupplierForm 弹窗单测：新增/编辑两态、必填校验、提交载荷
// 注：未配置 ConfigProvider locale，Modal 底部确认按钮为默认英文「OK」
import { fireEvent, render, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SupplierFormModal from './SupplierForm';
import { saveErpSupplier, type ErpSupplier } from '../api/erp';

vi.mock('../api/erp', () => ({
  saveErpSupplier: vi.fn(),
}));

const mockedSave = vi.mocked(saveErpSupplier);

const supplier: ErpSupplier = {
  id: '1',
  code: 'SUP-001',
  name: '华东金属材料厂',
  shortName: '华东金属',
  category: 'MATERIAL',
  contactName: '张三',
  status: 'ENABLED',
  rating: 'A',
};

function renderForm(initial: ErpSupplier | null = null) {
  const onCancel = vi.fn();
  const onSaved = vi.fn();
  render(<SupplierFormModal open initial={initial} onCancel={onCancel} onSaved={onSaved} />);
  const modal = document.querySelector('.ant-modal') as HTMLElement;
  return { onCancel, onSaved, modal };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedSave.mockResolvedValue(supplier);
});

describe('SupplierFormModal', () => {
  it('新增态：必填校验拦截，未通过时不调保存接口', async () => {
    const { onSaved, modal } = renderForm(null);

    expect(await within(modal).findByText('新增供应商')).toBeInTheDocument();
    fireEvent.click(within(modal).getByRole('button', { name: 'OK' }));

    expect(await within(modal).findByText('请输入供应商编码')).toBeInTheDocument();
    expect(await within(modal).findByText('请输入供应商名称')).toBeInTheDocument();
    expect(mockedSave).not.toHaveBeenCalled();
    expect(onSaved).not.toHaveBeenCalled();
  });

  it('新增提交：携带默认分类/状态保存，成功后回调 onSaved', async () => {
    const { onSaved, modal } = renderForm(null);

    await within(modal).findByText('新增供应商');
    fireEvent.change(within(modal).getByPlaceholderText('如 SUP-001'), {
      target: { value: 'SUP-100' },
    });
    fireEvent.change(within(modal).getByPlaceholderText('请输入供应商名称'), {
      target: { value: '测试供应商' },
    });
    fireEvent.click(within(modal).getByRole('button', { name: 'OK' }));

    await waitFor(() =>
      expect(mockedSave).toHaveBeenCalledWith(
        expect.objectContaining({
          code: 'SUP-100',
          name: '测试供应商',
          category: 'MATERIAL',
          status: 'ENABLED',
        }),
      ),
    );
    expect(mockedSave.mock.calls[0][0].id).toBeUndefined();
    expect(onSaved).toHaveBeenCalledTimes(1);
  });

  it('编辑态：标题为编辑供应商，表单回填且提交携带 id', async () => {
    const { onSaved, modal } = renderForm(supplier);

    expect(await within(modal).findByText('编辑供应商')).toBeInTheDocument();
    expect(within(modal).getByDisplayValue('SUP-001')).toBeInTheDocument();
    expect(within(modal).getByDisplayValue('华东金属材料厂')).toBeInTheDocument();
    expect(within(modal).getByDisplayValue('华东金属')).toBeInTheDocument();

    fireEvent.click(within(modal).getByRole('button', { name: 'OK' }));
    await waitFor(() =>
      expect(mockedSave).toHaveBeenCalledWith(expect.objectContaining({ id: '1' })),
    );
    expect(onSaved).toHaveBeenCalledTimes(1);
  });
});
