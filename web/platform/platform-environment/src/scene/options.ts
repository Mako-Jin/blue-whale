/** 场景开关。放在单独文件里是因为组件与舞台都要用它，避免任一方成为"类型的归属地"。 */
export type SceneOptions = {
  /** 相机自动环绕 */
  autoRotate: boolean;
  /** 地面网格辅助线 */
  showGrid: boolean;
  /** 线框模式 */
  wireframe: boolean;
  /** 阴影 */
  shadows: boolean;
};

export const DEFAULT_SCENE_OPTIONS: SceneOptions = {
  autoRotate: true,
  showGrid: true,
  wireframe: false,
  shadows: true,
};
