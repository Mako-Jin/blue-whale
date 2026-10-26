import { Texture, TextureLoader } from 'three';
import { GLTFLoader, type GLTF } from 'three/examples/jsm/loaders/GLTFLoader.js';

/** 加载进度回调 */
export type LoadProgressCallback = (event: ProgressEvent<EventTarget>) => void;

/** 懒创建的 GLTF 加载器（模块级单例，避免重复实例化） */
let gltfLoader: GLTFLoader | null = null;

/** 懒创建的贴图加载器（模块级单例，避免重复实例化） */
let textureLoader: TextureLoader | null = null;

/**
 * 获取 GLTFLoader 单例（首次调用时创建，便于 SSR/Node 环境延迟到浏览器侧初始化）
 *
 * @returns GLTF 加载器
 */
export function getGltfLoader(): GLTFLoader {
  gltfLoader ??= new GLTFLoader();
  return gltfLoader;
}

/**
 * 获取 TextureLoader 单例
 *
 * @returns 贴图加载器
 */
export function getTextureLoader(): TextureLoader {
  textureLoader ??= new TextureLoader();
  return textureLoader;
}

/**
 * 加载 GLTF/GLB 模型
 *
 * @param url 模型地址
 * @param onProgress 进度回调（可选）
 * @returns 模型解析结果
 */
export function loadGltf(url: string, onProgress?: LoadProgressCallback): Promise<GLTF> {
  return new Promise((resolve, reject) => {
    getGltfLoader().load(url, resolve, onProgress, reject);
  });
}

/**
 * 加载贴图
 *
 * @param url 贴图地址
 * @param onProgress 进度回调（可选）
 * @returns three.js 贴图（释放由调用方负责，或随场景 {@code dispose()} 一并释放）
 */
export function loadTexture(url: string, onProgress?: LoadProgressCallback): Promise<Texture> {
  return new Promise((resolve, reject) => {
    getTextureLoader().load(url, resolve, onProgress, reject);
  });
}

/**
 * 释放加载器单例引用（已加载的模型/贴图不受影响，仍由调用方或场景管理器释放）
 */
export function disposeLoaders(): void {
  gltfLoader = null;
  textureLoader = null;
}
