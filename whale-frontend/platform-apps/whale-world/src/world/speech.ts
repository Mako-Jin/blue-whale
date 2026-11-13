/**
 * 同城喊话气泡 —— 别人说话时，在他头顶飘一句话。
 *
 * <p>为什么用 DOM 而不是 3D Sprite：气泡是文字，DOM 的文字渲染有系统字体与子像素抗锯齿，
 * 在 1080p 下比纹理贴图清楚得多；而且「最多显示几条」「几秒淡出」这类规则用 CSS 表达更省事。
 * 代价是每帧要算一次投影，但这和已有的名字标签是同一套逻辑，成本可以忽略。</p>
 *
 * <p>三个体验判断：</p>
 * <ul>
 *   <li><b>同一个人的新话顶掉旧话</b>：否则连说三句会叠成三行，看不清哪句是新的。</li>
 *   <li><b>气泡钉在发言时的坐标上</b>，不跟着人走——人走开了，话还留在原地，
 *       这更像"喊了一嗓子"，也避免气泡跟着模型在屏幕上乱窜。</li>
 *   <li><b>超过 45 米就不显示</b>：远处的人说话时屏幕上冒出一个看不清的小点，只会让人困惑。</li>
 * </ul>
 */

import * as THREE from 'three';
import type { WorldSay } from './net';

/** 气泡存活时长（毫秒） */
const LIFE_MS = 7_000;

/** 淡出时长（毫秒） */
const FADE_MS = 900;

/** 同时最多显示几条（再多就互相遮挡） */
const MAX_BUBBLES = 6;

/** 超出该距离不显示（米） */
const MAX_DIST = 45;

interface Entry {
  el: HTMLDivElement;
  x: number;
  z: number;
  /** 气泡悬停高度（世界坐标 y） */
  y: number;
  bornAt: number;
}

export interface SpeechOptions {
  camera: THREE.Camera;
  /** 画布尺寸（CSS 像素） */
  size: () => { w: number; h: number };
  /** 地形高度：气泡要浮在头顶而不是陷进坡里 */
  groundAt: (x: number, z: number) => number;
}

export interface SpeechHandle {
  say: (message: WorldSay) => void;
  /** 每帧调用：更新投影与过期 */
  update: () => void;
  /** 最近显示的条数（供 e2e 断言） */
  count: () => number;
  dispose: () => void;
}

const _v = new THREE.Vector3();

/**
 * 创建气泡层。
 *
 * @param root DOM 容器（绝对定位、覆盖画布）
 * @param opts 相机与尺寸
 */
export function createSpeech(root: HTMLElement, opts: SpeechOptions): SpeechHandle {
  const entries: Entry[] = [];
  /** 发言人 id → 条目：同一个人的新话顶掉旧话 */
  const bySpeaker = new Map<string, Entry>();

  const remove = (entry: Entry): void => {
    const index = entries.indexOf(entry);
    if (index >= 0) {
      entries.splice(index, 1);
    }
    for (const [id, item] of bySpeaker) {
      if (item === entry) {
        bySpeaker.delete(id);
      }
    }
    entry.el.remove();
  };

  return {
    say(message) {
      const text = message.text.trim();
      if (!text) {
        return;
      }
      // 同一个人的旧气泡先撤掉，避免叠成一片
      const previous = bySpeaker.get(message.from);
      if (previous) {
        remove(previous);
      }
      // 超出上限时挤掉最旧的一条
      while (entries.length >= MAX_BUBBLES) {
        const oldest = entries[0];
        if (!oldest) {
          break;
        }
        remove(oldest);
      }

      const el = document.createElement('div');
      el.className = `bww-speech${message.mine ? ' is-me' : ''}`;
      const who = document.createElement('span');
      who.className = 'bww-speech-who';
      who.textContent = message.mine ? '我' : message.name;
      const body = document.createElement('span');
      body.className = 'bww-speech-body';
      body.textContent = text;
      el.append(who, body);
      root.appendChild(el);

      const entry: Entry = {
        el,
        x: message.x,
        z: message.z,
        y: opts.groundAt(message.x, message.z) + 2.6,
        bornAt: performance.now(),
      };
      entries.push(entry);
      bySpeaker.set(message.from, entry);
    },

    update() {
      const stamp = performance.now();
      const { w, h } = opts.size();
      for (let i = entries.length - 1; i >= 0; i -= 1) {
        const entry = entries[i];
        if (!entry) {
          continue;
        }
        const age = stamp - entry.bornAt;
        if (age > LIFE_MS) {
          remove(entry);
          continue;
        }

        _v.set(entry.x, entry.y, entry.z);
        const dist = opts.camera.position.distanceTo(_v);
        _v.project(opts.camera);
        // z > 1 表示在相机背后，直接不显示
        if (_v.z > 1 || dist > MAX_DIST) {
          entry.el.style.opacity = '0';
          continue;
        }

        const sx = (_v.x * 0.5 + 0.5) * w;
        const sy = (-_v.y * 0.5 + 0.5) * h;
        entry.el.style.transform = `translate(-50%,-100%) translate(${sx}px,${sy}px)`;

        // 远处淡、近处清；最后 900ms 整体淡出
        const distanceFade = 1 - Math.max(0, dist - MAX_DIST * 0.55) / (MAX_DIST * 0.45);
        const lifeFade = age > LIFE_MS - FADE_MS ? (LIFE_MS - age) / FADE_MS : 1;
        entry.el.style.opacity = String(Math.max(0, Math.min(1, distanceFade)) * lifeFade);
      }
    },

    count: () => entries.length,

    dispose() {
      for (const entry of entries) {
        entry.el.remove();
      }
      entries.length = 0;
      bySpeaker.clear();
    },
  };
}
