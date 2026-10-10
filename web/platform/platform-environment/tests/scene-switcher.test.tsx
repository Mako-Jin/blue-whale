import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import SceneSwitcher from '@/components/SceneSwitcher';
import { localSceneCatalog } from '@/scene';

const SCENES = localSceneCatalog().scenes;

function renderSwitcher(activeId = SCENES[0].meta.id, onSelect = vi.fn()) {
  const view = render(
    <SceneSwitcher scenes={SCENES} activeId={activeId} onSelect={onSelect} source="local" />,
  );
  return { ...view, onSelect };
}

describe('SceneSwitcher', () => {
  it('把清单里的场景全部渲染成卡片', () => {
    renderSwitcher();

    expect(screen.getAllByRole('button')).toHaveLength(SCENES.length);
    SCENES.forEach(({ meta }) => {
      expect(screen.getByText(meta.title)).toBeInTheDocument();
    });
  });

  it('把当前场景标记成按下状态，其余不是', () => {
    const active = SCENES[1].meta;
    renderSwitcher(active.id);

    expect(screen.getByRole('button', { name: new RegExp(active.title) })).toHaveAttribute(
      'aria-pressed',
      'true',
    );

    SCENES.filter((item) => item.meta.id !== active.id).forEach(({ meta }) => {
      expect(screen.getByRole('button', { name: new RegExp(meta.title) })).toHaveAttribute(
        'aria-pressed',
        'false',
      );
    });
  });

  it('点击卡片回调该场景的 id', () => {
    const target = SCENES[2].meta;
    const { onSelect } = renderSwitcher();

    fireEvent.click(screen.getByRole('button', { name: new RegExp(target.title) }));

    expect(onSelect).toHaveBeenCalledWith(target.id);
  });

  it('如实标注清单来源，而不是让人猜数据从哪来', () => {
    renderSwitcher();

    expect(screen.getByText('内置清单')).toBeInTheDocument();
  });

  it('显示当前场景的标签', () => {
    const active = SCENES[0].meta;
    renderSwitcher(active.id);

    active.tags.forEach((tag) => {
      expect(screen.getByText(tag)).toBeInTheDocument();
    });
  });

  it('清单为空时不崩（远端返回空清单是合法状态）', () => {
    render(
      <SceneSwitcher scenes={[]} activeId={'none' as never} onSelect={vi.fn()} source="local" />,
    );

    expect(screen.getByRole('region', { name: '场景选择' })).toBeInTheDocument();
    expect(screen.queryAllByRole('button')).toHaveLength(0);
  });
});
