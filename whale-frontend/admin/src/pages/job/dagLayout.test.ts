// DAG 布局纯函数测试：分层顺序、同层排列、孤立节点、环兜底与空图
import { describe, expect, it } from 'vitest';
import type { JobGraphNodeVO } from '../../api/job';
import { layoutDag } from './dagLayout';

function node(id: string, jobName = `任务${id}`): JobGraphNodeVO {
  return { id, jobName, status: 1 };
}

/** 取节点坐标，断言失败时能直接看到缺失的 ID */
function boxOf(result: ReturnType<typeof layoutDag>, id: string) {
  const box = result.nodes.find((item) => item.node.id === id);
  if (!box) {
    throw new Error(`节点 ${id} 未生成布局`);
  }
  return box;
}

describe('layoutDag', () => {
  it('按依赖链分层：上游在上、下游在下', () => {
    const result = layoutDag(
      [node('1'), node('2'), node('3')],
      [
        { from: '1', to: '2' },
        { from: '2', to: '3' },
      ],
    );

    expect(result.layers).toBe(3);
    expect(boxOf(result, '1').y).toBeLessThan(boxOf(result, '2').y);
    expect(boxOf(result, '2').y).toBeLessThan(boxOf(result, '3').y);
    expect(result.edges).toHaveLength(2);
    expect(result.edges[0].path.startsWith('M ')).toBe(true);
  });

  it('同层节点水平并排且垂直坐标一致', () => {
    // 2、3 同为 1 的下游，应在同一层
    const result = layoutDag(
      [node('1'), node('2'), node('3')],
      [
        { from: '1', to: '2' },
        { from: '1', to: '3' },
      ],
    );

    const second = boxOf(result, '2');
    const third = boxOf(result, '3');
    expect(second.y).toBe(third.y);
    expect(second.x).not.toBe(third.x);
  });

  it('孤立节点落在第一层，不影响其他节点层号', () => {
    const result = layoutDag([node('1'), node('2'), node('9')], [{ from: '1', to: '2' }]);

    expect(boxOf(result, '9').y).toBe(boxOf(result, '1').y);
    expect(result.layers).toBe(2);
  });

  it('环形依赖不会让层号无限增长', () => {
    const result = layoutDag(
      [node('1'), node('2')],
      [
        { from: '1', to: '2' },
        { from: '2', to: '1' },
      ],
    );

    expect(result.layers).toBeLessThanOrEqual(2);
    expect(result.nodes).toHaveLength(2);
  });

  it('端点缺失的边被忽略', () => {
    const result = layoutDag([node('1')], [{ from: '1', to: '404' }]);

    expect(result.nodes).toHaveLength(1);
    expect(result.edges).toHaveLength(0);
  });

  it('空图返回内边距大小的画布', () => {
    const result = layoutDag([], []);

    expect(result.nodes).toHaveLength(0);
    expect(result.layers).toBe(0);
    expect(result.width).toBe(48);
    expect(result.height).toBe(48);
  });
});
