import { describe, expect, it } from 'vitest';
import {
  createPlayerMotor,
  normalizeAngle,
  obstacleFromRect,
  resolveCollision,
  type PlayerMotorConfig,
} from '@/scene/player/motor';

/**
 * 移动内核的行为契约。
 *
 * 这是本应用里唯一"能靠断言把 bug 摁死"的地方：转身抖动、到点不判、
 * 卡在墙边原地走 —— 这些在浏览器里只能靠肉眼盯，在这里可以逐帧查。
 *
 * 角度约定：0 = 朝 +Z，方向向量 `(sin a, cos a)`。
 */

/** 相机朝北看（azimuth = π）时的世界方向：-Z。后面几个用例都以它为基准。 */
const LOOKING_NORTH = Math.PI;

function motorWith(overrides: Partial<PlayerMotorConfig> = {}) {
  return createPlayerMotor({
    speed: 10,
    // 转身给得很快：这些用例考的是位移，不是转身过程
    turnSpeed: 100,
    arriveRadius: 0.5,
    halfExtent: 20,
    radius: 0.5,
    obstacles: [],
    spawn: { x: 0, z: 0, heading: 0 },
    ...overrides,
  });
}

const near = (actual: number, expected: number, epsilon = 1e-6) =>
  expect(Math.abs(actual - expected)).toBeLessThan(epsilon);

describe('normalizeAngle', () => {
  it('把角度归一到 (-π, π]', () => {
    near(normalizeAngle(0), 0);
    near(normalizeAngle(Math.PI * 2), 0);
    near(normalizeAngle(Math.PI), Math.PI);
    near(normalizeAngle(Math.PI * 3), Math.PI);
    near(normalizeAngle(-Math.PI), Math.PI);
  });

  it('取的是最短转向（3π/2 等价于 -π/2）', () => {
    near(normalizeAngle((Math.PI * 3) / 2), -Math.PI / 2);
  });
});

describe('相机相对的移动意图', () => {
  it('相机朝北时按前进，人物走向 -Z', () => {
    const motor = motorWith();
    motor.setIntent({ forward: 1, right: 0 });

    motor.update(1, LOOKING_NORTH);

    near(motor.pose.x, 0);
    near(motor.pose.z, -10);
  });

  it('相机朝北时按右移，人物走向 +X（屏幕右边就是东）', () => {
    const motor = motorWith();
    motor.setIntent({ forward: 0, right: 1 });

    motor.update(1, LOOKING_NORTH);

    near(motor.pose.x, 10);
    near(motor.pose.z, 0);
  });

  it('相机转了 90°，同一个按键的世界方向跟着转', () => {
    // 相机朝西看（azimuth = -π/2）：按前进应当走向 -X
    const motor = motorWith();
    motor.setIntent({ forward: 1, right: 0 });

    motor.update(1, -Math.PI / 2);

    near(motor.pose.x, -10);
    near(motor.pose.z, 0);
  });

  it('斜向移动同样按速度走，不会因为两个方向叠加而变快', () => {
    const motor = motorWith();
    motor.setIntent({ forward: 1, right: 1 });

    motor.update(1, LOOKING_NORTH);

    near(Math.hypot(motor.pose.x, motor.pose.z), 10);
  });

  it('朝向转到移动方向上', () => {
    const motor = motorWith();
    motor.setIntent({ forward: 1, right: 0 }); // 走向 -Z，对应 heading = π

    motor.update(0.5, LOOKING_NORTH);

    near(motor.pose.heading, Math.PI);
  });

  it('没有输入就不动', () => {
    const motor = motorWith();

    motor.update(1, LOOKING_NORTH);

    near(motor.pose.x, 0);
    near(motor.pose.z, 0);
  });
});

describe('行动终点（右键设点）', () => {
  it('朝终点走，到达后停下并清除终点', () => {
    const motor = motorWith();
    motor.setDestination(0, -6);

    motor.update(0.5, LOOKING_NORTH); // 走 5
    expect(motor.destination).not.toBeNull();

    motor.update(0.5, LOOKING_NORTH); // 再走 5，足够到
    expect(motor.destination).toBeNull();
    near(motor.pose.z, -6);
  });

  it('单帧位移不会越过终点（否则会在终点上来回抖）', () => {
    const motor = motorWith();
    motor.setDestination(0, -1);

    // 一帧就能走 10，远大于 1 —— 必须只走 1
    motor.update(1, LOOKING_NORTH);

    near(motor.pose.z, -1);
    expect(motor.destination).toBeNull();
  });

  it('到点后继续 update 不再移动', () => {
    const motor = motorWith();
    motor.setDestination(0, -3);
    motor.update(1, LOOKING_NORTH); // 到点

    const { x, z } = motor.pose;
    motor.update(1, LOOKING_NORTH);

    near(motor.pose.x, x);
    near(motor.pose.z, z);
  });

  it('按键会取消已设的终点 —— 手控优先', () => {
    const motor = motorWith();
    motor.setDestination(5, 5);
    expect(motor.destination).not.toBeNull();

    motor.setIntent({ forward: 1, right: 0 });

    expect(motor.destination).toBeNull();
  });

  it('终点会被夹到可行走范围内', () => {
    const motor = motorWith({ halfExtent: 10, radius: 0.5 });
    motor.setDestination(999, 0);

    expect(motor.destination?.x).toBe(9.5);
  });

  it('直线上过不去时清除终点，而不是贴着墙原地走', () => {
    const motor = motorWith({
      halfExtent: 30,
      obstacles: [obstacleFromRect(0, 0, 20, 4)],
      spawn: { x: 0, z: 6, heading: 0 },
    });
    motor.setDestination(0, -6); // 墙在中间，直线穿不过去

    for (let step = 0; step < 40 && motor.destination; step += 1) {
      motor.update(0.1, LOOKING_NORTH);
    }

    expect(motor.destination).toBeNull();
    // 停在墙外，而不是穿进去
    expect(motor.pose.z).toBeGreaterThanOrEqual(2.5 - 1e-6);
  });
});

describe('边界与障碍（resolveCollision）', () => {
  const config = {
    halfExtent: 20,
    radius: 0.5,
    obstacles: [obstacleFromRect(0, 0, 4, 4)],
  };

  it('超出广场范围会被夹回来', () => {
    const resolved = resolveCollision(100, -100, config);
    expect(resolved).toEqual({ x: 19.5, z: -19.5 });
  });

  it('落到障碍里会沿穿透最浅的一边弹出去', () => {
    // 从 +X 侧压进来最深：应当从 +X 出去，而不是被弹到对面
    const resolved = resolveCollision(1.6, 0, config);
    expect(resolved.x).toBeCloseTo(2.5, 6);
    expect(resolved.z).toBeCloseTo(0, 6);
  });

  it('障碍外的点原样返回', () => {
    const resolved = resolveCollision(10, 10, config);
    expect(resolved).toEqual({ x: 10, z: 10 });
  });

  it('半径计入外扩量：贴着障碍表面时不算穿透', () => {
    // 半宽 2 + 半径 0.5 = 2.5，正好在表面上
    const resolved = resolveCollision(2.5, 0, config);
    expect(resolved.x).toBeCloseTo(2.5, 6);
  });

  it('obstacleFromRect 以中心与尺寸描述矩形', () => {
    expect(obstacleFromRect(3, -2, 4, 6)).toEqual({
      minX: 1,
      maxX: 5,
      minZ: -5,
      maxZ: 1,
    });
  });
});

describe('稳定性', () => {
  it('delta 为 0 或负数时不动（避免 NaN 卡死）', () => {
    const motor = motorWith();
    motor.setIntent({ forward: 1, right: 0 });

    motor.update(0, LOOKING_NORTH);
    motor.update(-1, LOOKING_NORTH);

    near(motor.pose.x, 0);
    near(motor.pose.z, 0);
  });

  it('一路按着前进，走到边界就停住而不是走出去', () => {
    const motor = motorWith({ halfExtent: 5, radius: 0.5 });
    motor.setIntent({ forward: 1, right: 0 });

    for (let step = 0; step < 20; step += 1) {
      motor.update(0.5, LOOKING_NORTH);
    }

    near(motor.pose.z, -4.5);
  });
});
