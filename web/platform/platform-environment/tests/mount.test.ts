import { beforeEach, describe, expect, it } from 'vitest';
import { ROOT_ID, SELF_BUILT_ID, resolveMountPoint } from '@/mount';

/**
 * 挂载点解析是"子应用能同时被基座加载和独立启动"的支点，
 * 四条分支都要各自钉住 —— 写错的表现是白屏，而白屏最难倒推原因。
 */
describe('resolveMountPoint', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
  });

  it('被基座加载时用基座传入的容器', () => {
    const container = document.createElement('div');
    const inner = document.createElement('div');
    inner.id = ROOT_ID;
    container.appendChild(inner);
    document.body.appendChild(container);

    const target = resolveMountPoint(container);

    expect(target.mode).toBe('host');
    expect(target.element).toBe(inner);
  });

  it('基座容器里没有根节点时现场补一个，而不是把容器本身当根节点', () => {
    const container = document.createElement('div');
    document.body.appendChild(container);

    const target = resolveMountPoint(container);

    expect(target.mode).toBe('host');
    expect(target.element).not.toBe(container);
    expect(target.element.parentElement).toBe(container);
    expect(target.element.id).toBe(ROOT_ID);
  });

  it('重复解析同一个容器拿到的是同一个根节点', () => {
    const container = document.createElement('div');

    const first = resolveMountPoint(container);
    const second = resolveMountPoint(container);

    expect(second.element).toBe(first.element);
    expect(container.childElementCount).toBe(1);
  });

  it('独立启动时用 index.html 里声明的自建容器', () => {
    const declared = document.createElement('div');
    declared.id = ROOT_ID;
    document.body.appendChild(declared);

    const target = resolveMountPoint(null);

    expect(target.mode).toBe('standalone');
    expect(target.element).toBe(declared);
  });

  it('独立启动且页面里没有根节点时自建一个挂到 body', () => {
    const target = resolveMountPoint(null);

    expect(target.mode).toBe('standalone');
    expect(target.element.id).toBe(SELF_BUILT_ID);
    expect(document.body.contains(target.element)).toBe(true);
  });

  it('容器之间互不串用：只在自己的容器里找根节点', () => {
    const outer = document.createElement('div');
    const outerRoot = document.createElement('div');
    outerRoot.id = ROOT_ID;
    outer.appendChild(outerRoot);

    const inner = document.createElement('div');
    outerRoot.appendChild(inner);
    document.body.appendChild(outer);

    const target = resolveMountPoint(inner);

    // inner 里没有根节点，应当新建，而不是向上找到 outerRoot
    expect(target.element).not.toBe(outerRoot);
    expect(target.element.parentElement).toBe(inner);
  });
});
