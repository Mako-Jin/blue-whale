import { describe, expect, it } from 'vitest';
import { ConsoleMirror } from '../playground/mirror';
import { createLogger, setLoggerSink } from '../src/logger';

/**
 * 镜像渲染是本包最容易出错的环节：它要把「格式串 + 若干样式串 + 数据参数」
 * 这一串扁平参数按 `%c` 数量正确切分。这里用 jsdom 把它钉住。
 */
function setup(): HTMLElement {
  const container = document.createElement('div');
  setLoggerSink(new ConsoleMirror(container).sink);
  return container;
}

const plain = { showTimestamp: false, colorize: false } as const;

describe('ConsoleMirror', () => {
  it('把 %c 样式串还原为带样式的 DOM 片段', () => {
    const container = setup();
    createLogger('t', { showTimestamp: false }).info('hello');

    expect(container.textContent).toContain('hello');
    const styled = Array.from(container.querySelectorAll('span')).filter((span) =>
      (span.getAttribute('style') ?? '').includes('color:'),
    );
    expect(styled.length).toBeGreaterThan(0);
  });

  it('样式串与数据参数不会互相错位', () => {
    const container = setup();
    const payload = { userId: 1024 };
    createLogger('t', { showTimestamp: false }).info('登录成功', payload);

    expect(container.textContent).toContain('登录成功');
    expect(container.textContent).toContain('{"userId":1024}');
    // 数据参数必须落到 .mirror-data，而不是被当成样式吃掉
    expect(container.querySelector('.mirror-data')?.textContent).toContain('userId');
  });

  it('片段数量随开关变化时仍能正确切分', () => {
    const container = setup();
    createLogger('t', {
      showTimestamp: false,
      showNamespace: false,
      showLevel: false,
    }).info('only message');

    expect(container.textContent).toBe('only message');
    expect(container.querySelector('.mirror-data')).toBeNull();
  });

  it('group 逐层缩进并配对闭合', () => {
    const container = setup();
    const log = createLogger('t', plain);

    log.group('外层', () => {
      log.group('内层', () => {
        log.info('最内');
      });
    });

    const rows = Array.from(container.querySelectorAll<HTMLElement>('.mirror-line'));
    expect(rows).toHaveLength(3);
    expect(rows[0].style.paddingLeft).toBe('0px');
    expect(rows[1].style.paddingLeft).toBe('16px');
    expect(rows[2].style.paddingLeft).toBe('32px');
  });

  it('table 渲染为真实表格', () => {
    const container = setup();
    createLogger('t', plain).table(
      [
        { layer: 'Frontend', technology: 'React' },
        { layer: 'Backend', technology: 'Spring Boot' },
      ],
      ['layer', 'technology'],
    );

    expect(container.querySelectorAll('table')).toHaveLength(1);
    expect(container.querySelectorAll('th')).toHaveLength(2);
    expect(container.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(container.textContent).toContain('Spring Boot');
  });

  it('clear 清空容器并复位缩进', () => {
    const container = setup();
    const log = createLogger('t', plain);

    log.info('a');
    log.clear();
    expect(container.textContent).toBe('');

    log.group('再开一组', () => log.info('b'));
    const rows = Array.from(container.querySelectorAll<HTMLElement>('.mirror-line'));
    expect(rows[0].style.paddingLeft).toBe('0px');
  });

  it('console 方法映射决定行样式（error / warn 可高亮）', () => {
    const container = setup();
    const log = createLogger('t', plain);

    log.error('炸了');
    log.warn('小心');

    expect(container.querySelectorAll('.mirror-line--error')).toHaveLength(1);
    expect(container.querySelectorAll('.mirror-line--warn')).toHaveLength(1);
  });

  it('零宽空格不会出现在页面上', () => {
    const container = setup();
    createLogger('t', plain).info('折扣 50%off');

    expect(container.textContent).toContain('50%off');
    expect(container.textContent).not.toContain('\u200b');
  });
});
