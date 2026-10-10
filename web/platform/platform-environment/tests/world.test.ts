import { describe, expect, it, vi } from 'vitest';
import {
  GridHelper,
  Mesh,
  type BufferGeometry,
  type Material,
  type MeshStandardMaterial,
  type Object3D,
} from 'three';
import { createWorld, disposeSubtree } from '@/scene/world';

function findGrid(root: Object3D): GridHelper | null {
  let found: GridHelper | null = null;
  root.traverse((child) => {
    if (child instanceof GridHelper) {
      found = child;
    }
  });
  return found;
}

function findMeshes(root: Object3D): Mesh[] {
  const meshes: Mesh[] = [];
  root.traverse((child) => {
    if (child instanceof Mesh) {
      meshes.push(child);
    }
  });
  return meshes;
}

function meshMaterials(root: Object3D): MeshStandardMaterial[] {
  return findMeshes(root).map((mesh) => mesh.material as MeshStandardMaterial);
}

describe('createWorld', () => {
  it('搭出带背景与雾的场景', () => {
    const world = createWorld();

    expect(world.scene.background).not.toBeNull();
    expect(world.scene.fog).not.toBeNull();
    // 主体 1 个 + 环绕 3 个（地面与网格辅助线不计入）
    expect(world.meshCount).toBe(4);
  });

  it('相机有高度，不会把主体顶出画面', () => {
    const world = createWorld();

    expect(world.camera.position.length()).toBeGreaterThan(0);
    expect(world.camera.position.y).toBeGreaterThan(1);
  });

  it('update 是 elapsed 的纯函数：同一时刻推两次结果完全一致', () => {
    const world = createWorld();

    world.update(3.5);
    const first = world.scene.children.map((child) => child.position.toArray());
    world.update(3.5);
    const second = world.scene.children.map((child) => child.position.toArray());

    expect(second).toEqual(first);
  });

  it('update 会让场景动起来', () => {
    const world = createWorld();

    world.update(0);
    const before = world.scene.children.map((child) => child.position.toArray());
    world.update(2);
    const after = world.scene.children.map((child) => child.position.toArray());

    expect(after).not.toEqual(before);
  });

  it('setWireframe 切换场景内所有物体的材质', () => {
    const world = createWorld();
    const materials = meshMaterials(world.scene);
    expect(materials.length).toBeGreaterThan(0);

    world.setWireframe(true);
    expect(materials.every((material) => material.wireframe)).toBe(true);

    world.setWireframe(false);
    expect(materials.every((material) => material.wireframe)).toBe(false);
  });

  it('setGridVisible 只切网格辅助线的可见性', () => {
    const world = createWorld();
    const grid = findGrid(world.scene);
    expect(grid).not.toBeNull();

    world.setGridVisible(false);
    expect(grid?.visible).toBe(false);

    world.setGridVisible(true);
    expect(grid?.visible).toBe(true);
  });

  it('dispose 释放全部几何体并清空场景', () => {
    const world = createWorld();
    const geometries: BufferGeometry[] = findMeshes(world.scene).map((mesh) => mesh.geometry);
    expect(geometries.length).toBeGreaterThan(0);

    const spies = geometries.map((geometry) => vi.spyOn(geometry, 'dispose'));
    world.dispose();

    spies.forEach((spy) => expect(spy).toHaveBeenCalled());
    expect(world.scene.children).toHaveLength(0);
  });
});

describe('disposeSubtree', () => {
  it('把子树的几何体与材质一并释放', () => {
    const world = createWorld();
    const mesh = findMeshes(world.scene)[0];
    expect(mesh).toBeDefined();

    const geometrySpy = vi.spyOn(mesh.geometry, 'dispose');
    const materialSpy = vi.spyOn(mesh.material as Material, 'dispose');

    disposeSubtree(mesh);

    expect(geometrySpy).toHaveBeenCalled();
    expect(materialSpy).toHaveBeenCalled();
    expect(mesh.children).toHaveLength(0);
  });
});
