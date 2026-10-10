import {
  CapsuleGeometry,
  ConeGeometry,
  Group,
  Mesh,
  SphereGeometry,
  type ColorRepresentation,
} from 'three';
import { disposeSubtree, solidMaterial } from '../common';
import type { PlayerPose } from './motor';

/**
 * 主人公造型。
 *
 * 刻意**全是基础几何体**：不引入 GLTF，零网络依赖、零加载等待，也顺带说明
 * "临时块面"完全可以做到可辨识。选斗笠 + 长身作为剪影，是因为第三人称视角
 * 看到的多半是背面上方，靠一张脸认人不如靠轮廓。
 *
 * 朝向由 `group.rotation.y` 表达 —— 因此**模型的正前方必须是本地 +Z**，
 * 与 `motor.ts` 的角度约定一致（`heading = 0` 朝世界 +Z）。
 * 这一点用错了不会报错，只会让人物"横着走"，所以另加了一枚前向标记。
 */

const PALETTE = {
  robe: 0x33527f,
  sash: 0xc7a35c,
  hat: 0xd8b877,
  skin: 0xd9a679,
} as const;

/** 各部件的高度（脚底为 0）。凑成一个约 1.8 世界单位高的人形。 */
const BODY = {
  robeRadius: 0.28,
  robeLength: 0.82,
  headRadius: 0.2,
  hatRadius: 0.44,
  hatHeight: 0.26,
} as const;

const ROBE_CENTER_Y = BODY.robeLength / 2 + BODY.robeRadius;
const HEAD_CENTER_Y = BODY.robeLength + BODY.robeRadius * 2 + BODY.headRadius * 0.62;
const HAT_CENTER_Y = HEAD_CENTER_Y - BODY.headRadius * 0.2 + BODY.hatHeight / 2;

export type PlayerAvatar = {
  group: Group;
  /** 把内核的位姿写进场景图。每帧一次 */
  applyPose(pose: Readonly<PlayerPose>): void;
  /** 释放造型自身拥有的几何体与材质 */
  dispose(): void;
};

function part(
  geometry: CapsuleGeometry | ConeGeometry | SphereGeometry,
  color: ColorRepresentation,
  options: { metalness?: number; roughness?: number } = {},
): Mesh {
  const mesh = new Mesh(
    geometry,
    solidMaterial(color, {
      metalness: options.metalness ?? 0.1,
      roughness: options.roughness ?? 0.7,
    }),
  );
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  return mesh;
}

export function createPlayerAvatar(): PlayerAvatar {
  const group = new Group();
  group.name = 'player-avatar';

  const robe = part(new CapsuleGeometry(BODY.robeRadius, BODY.robeLength, 6, 18), PALETTE.robe, {
    roughness: 0.78,
  });
  robe.position.y = ROBE_CENTER_Y;
  group.add(robe);

  // 腰带：一道窄环，把"人是几段"这件事交代清楚，纯色块会糊成一团
  const sash = part(new CapsuleGeometry(BODY.robeRadius * 1.02, 0.1, 4, 18), PALETTE.sash, {
    metalness: 0.24,
    roughness: 0.5,
  });
  sash.position.y = ROBE_CENTER_Y + 0.1;
  group.add(sash);

  const head = part(new SphereGeometry(BODY.headRadius, 20, 16), PALETTE.skin, {
    metalness: 0,
    roughness: 0.85,
  });
  head.position.y = HEAD_CENTER_Y;
  group.add(head);

  const hat = part(new ConeGeometry(BODY.hatRadius, BODY.hatHeight, 22), PALETTE.hat, {
    metalness: 0.08,
    roughness: 0.62,
  });
  hat.position.y = HAT_CENTER_Y;
  group.add(hat);

  /**
   * 前向标记。
   *
   * `ConeGeometry` 的轴是 +Y，绕 X 转 +90° 之后指向 +Z —— 也就是模型的正面。
   * 它的存在只有一个目的：**让"朝向对不对"一眼可判**。没有它，人物横着走
   * 在纯色块造型上几乎看不出来。
   */
  const facing = part(new ConeGeometry(0.06, 0.18, 10), PALETTE.sash, {
    metalness: 0.3,
    roughness: 0.4,
  });
  facing.rotation.x = Math.PI / 2;
  facing.position.set(0, HEAD_CENTER_Y, BODY.headRadius + 0.03);
  group.add(facing);

  return {
    group,

    applyPose(pose: Readonly<PlayerPose>): void {
      group.position.set(pose.x, 0, pose.z);
      group.rotation.y = pose.heading;
    },

    dispose(): void {
      disposeSubtree(group);
    },
  };
}
