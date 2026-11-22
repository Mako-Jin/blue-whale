/* ==========================================================================
 * 城中声景 —— WebAudio 程序化合成，不引任何音频文件
 *
 * 为什么不是音效文件：一段 30 秒的环路环境音就要 1–3MB，还得处理授权与
 * 多格式回退；而"风、雨、钟、脚步"这几样用振荡器与噪声就能做到八九分像。
 * 代价是合成音比采样音"干净"一点，故所有声音都过一层低通与轻微失谐。
 *
 * 三条约束：
 *   1. 浏览器自动播放策略：AudioContext 必须在**用户手势**里 resume，
 *      所以对外只暴露 unlock()，由按键/点击统一调用（内部幂等）。
 *   2. 失败必须静默：headless / 无声卡 / 旧浏览器下 AudioContext 可能创建失败
 *      或 resume 抛错。这里一律退化成 no-op 实现，**绝不因为没声音就把场景搞挂**。
 *   3. 音量克制：脚步是走路时最频繁的声音，做重了 30 秒就烦。
 *      脚步声压到 -18dB 左右，钟声才允许响。
 *
 * 环境床（雨/风）常驻两个噪声源，靠 gain 淡入淡出，不反复建节点 ——
 * 每次切换天气重建节点会在切歌瞬间爆一声"咔"。
 * ========================================================================== */

/** 地面材质决定脚步音色：城内铺装脆，城外土坡闷 */
export type GroundKind = 'stone' | 'grass';

/** 天气序号与 city.ts 的 world.weather 一致：0 晴 1 雨 2 雪 3 雾 */
const WEATHER_RAIN = 1;
const WEATHER_SNOW = 2;
const WEATHER_FOG = 3;

const MUTE_KEY = 'bww.audio.muted';

export interface WorldAudio {
  /** 用户手势内调用，解锁音频上下文（幂等） */
  unlock(): void;
  /** 切换静音，返回切换后的静音状态 */
  toggleMuted(): boolean;
  isMuted(): boolean;
  /** 是否已真正出声（未解锁 / 不支持时为 false，HUD 可据此提示） */
  isReady(): boolean;
  /** 一次落地脚步 */
  step(kind: GroundKind): void;
  /** 开城钟声（入城 / 整点） */
  toll(): void;
  /** 传送门启动 */
  portal(): void;
  /** 喊话发出 */
  speak(): void;
  /** 设置天气环境床 */
  setWeather(kind: number): void;
  /** 乐坊琴音：dist 玩家到乐坊的距离（米），bearing 乐坊相对玩家朝向的方位偏角（弧度） */
  setStageProximity(dist: number, bearing: number): void;
  /** 乐坊琴音当前目标增益（0~MUSIC_LEVEL）：e2e 无法听，只能读状态确认距离衰减接线 */
  stageGain(): number;
  /** 每帧推进：脚步节流 + 环境床平滑 */
  update(dt: number, speed: number, ground: GroundKind): void;
  dispose(): void;
}

/* ---------------- 降级实现：环境不支持时用它，调用方无需判空 ---------------- */

const silentAudio: WorldAudio = {
  unlock: () => {},
  toggleMuted: () => false,
  isMuted: () => true,
  isReady: () => false,
  step: () => {},
  toll: () => {},
  portal: () => {},
  speak: () => {},
  setWeather: () => {},
  setStageProximity: () => {},
  stageGain: () => 0,
  update: () => {},
  dispose: () => {},
};

/* ---------------- 工具 ---------------- */

function readMuted(): boolean {
  try {
    return localStorage.getItem(MUTE_KEY) === '1';
  } catch {
    // 隐私模式下 localStorage 会抛，静音偏好失效不影响出声
    return false;
  }
}

function writeMuted(muted: boolean): void {
  try {
    localStorage.setItem(MUTE_KEY, muted ? '1' : '0');
  } catch {
    /* 忽略：存不了就只在本次会话生效 */
  }
}

/** 2 秒白噪声，雨声与脚步共用一个缓冲，避免重复分配 */
function makeNoise(ctx: AudioContext): AudioBuffer {
  const len = Math.floor(ctx.sampleRate * 2);
  const buf = ctx.createBuffer(1, len, ctx.sampleRate);
  const data = buf.getChannelData(0);
  let last = 0;
  for (let i = 0; i < len; i += 1) {
    const white = Math.random() * 2 - 1;
    // 一阶低通把纯白噪声削掉最刺的高频，听感更接近真实风声/雨声
    last = last * 0.72 + white * 0.28;
    data[i] = last * 2.4;
  }
  return buf;
}

/* ---------------- 合成实现 ---------------- */

export function createWorldAudio(): WorldAudio {
  let ctx: AudioContext | null = null;
  let master: GainNode | null = null;
  let noise: AudioBuffer | null = null;

  let muted = readMuted();
  let ready = false;
  let disposed = false;

  /** 环境床：雨、风（雾/雪）两组增益，靠 target 平滑 */
  let rainGain: GainNode | null = null;
  let windGain: GainNode | null = null;
  let rainTarget = 0;
  let windTarget = 0;

  /** 乐坊琴音链：拨弦 → 音量门 → 立体声场 → 主链；距离/方位每帧平滑 */
  let musicGain: GainNode | null = null;
  let musicPan: StereoPannerNode | null = null;
  let musicTarget = 0;
  let musicPanTarget = 0;
  /** 下一个拨弦的调度时刻（AudioContext 时钟）；离开乐坊时推到“现在”防补播轰炸 */
  let musicNextAt = 0;
  /** 五声音阶（宫商角徵羽 + 高八度宫），拨弦在相邻音间随机漫步，不跳大跳 */
  let scaleIndex = 2;

  /** 距离衰减：6 米内全量，30 米外归零（正好贴屏风读数时最响，绕过坊墙就淡出） */
  const MUSIC_NEAR = 6;
  const MUSIC_FAR = 30;
  const MUSIC_LEVEL = 0.16;

  /** 宫调五声音阶频率（Hz）：C4 D4 E4 G4 A4 C5，吟游而不成调，避免像在放歌 */
  const PENTATONIC = [261.63, 293.66, 329.63, 392.0, 440.0, 523.25];

  /** 脚步节流：按速度换算步频，跑步时自然加快 */
  let stepAccum = 0;

  function ensure(): boolean {
    if (disposed) return false;
    if (ctx) return ready;
    try {
      const Ctor =
        window.AudioContext ??
        (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!Ctor) return false;

      ctx = new Ctor();
      noise = makeNoise(ctx);

      // 主链：各路声音 → 压缩器 → 主增益 → 输出
      // 压缩器防的是"钟声 + 雨声 + 脚步同时出现"时的削顶爆音
      const comp = ctx.createDynamicsCompressor();
      comp.threshold.value = -18;
      comp.knee.value = 12;
      comp.ratio.value = 3.2;
      comp.attack.value = 0.006;
      comp.release.value = 0.25;

      master = ctx.createGain();
      master.gain.value = muted ? 0 : 0.55;

      comp.connect(master);
      master.connect(ctx.destination);

      // 乐坊琴音常驻节点：音量门 + 立体声场。StereoPanner 不支持时（旧 Safari）
      // 退化为直连——方位感丢了但声音还在，不算事故。
      musicGain = ctx.createGain();
      musicGain.gain.value = 0;
      const panCtor = (ctx as AudioContext & { createStereoPanner?: () => StereoPannerNode })
        .createStereoPanner;
      if (panCtor) {
        musicPan = panCtor.call(ctx);
        musicPan.pan.value = 0;
        musicGain.connect(musicPan);
        musicPan.connect(comp);
      } else {
        musicGain.connect(comp);
      }

      // 环境床常驻节点：雨
      const rainSrc = ctx.createBufferSource();
      rainSrc.buffer = noise;
      rainSrc.loop = true;
      const rainLp = ctx.createBiquadFilter();
      rainLp.type = 'lowpass';
      rainLp.frequency.value = 5200;
      const rainHp = ctx.createBiquadFilter();
      rainHp.type = 'highpass';
      rainHp.frequency.value = 600;
      rainGain = ctx.createGain();
      rainGain.gain.value = 0;
      rainSrc.connect(rainHp);
      rainHp.connect(rainLp);
      rainLp.connect(rainGain);
      rainGain.connect(comp);
      rainSrc.start();

      // 环境床常驻节点：风（雾与雪共用，只是强弱不同）
      const windSrc = ctx.createBufferSource();
      windSrc.buffer = noise;
      windSrc.loop = true;
      windSrc.playbackRate.value = 0.62;
      const windLp = ctx.createBiquadFilter();
      windLp.type = 'lowpass';
      windLp.frequency.value = 380;
      const windLfo = ctx.createOscillator();
      windLfo.frequency.value = 0.07;
      const windLfoGain = ctx.createGain();
      windLfoGain.gain.value = 0.035;
      windGain = ctx.createGain();
      windGain.gain.value = 0;
      windSrc.connect(windLp);
      windLp.connect(windGain);
      windGain.connect(comp);
      windLfo.connect(windLfoGain);
      windLfoGain.connect(windGain.gain);
      windSrc.start();
      windLfo.start();

      return true;
    } catch {
      // 无 audio 设备 / 构造失败：彻底退化成 no-op，不干扰场景
      ctx = null;
      master = null;
      return false;
    }
  }

  /** 短噪声爆发 + 带通，脚步/喊话共用的骨架 */
  function burst(opts: {
    freq: number;
    q: number;
    dur: number;
    level: number;
    type?: BiquadFilterType;
    sweepTo?: number;
  }): void {
    if (!ctx || !master || !noise) return;
    const src = ctx.createBufferSource();
    src.buffer = noise;
    // 从缓冲中随机位置起播，否则每步听起来一模一样
    src.loop = true;
    const filter = ctx.createBiquadFilter();
    filter.type = opts.type ?? 'bandpass';
    filter.frequency.value = opts.freq;
    filter.Q.value = opts.q;
    if (opts.sweepTo) {
      filter.frequency.setValueAtTime(opts.freq, ctx.currentTime);
      filter.frequency.exponentialRampToValueAtTime(opts.sweepTo, ctx.currentTime + opts.dur);
    }
    const env = ctx.createGain();
    const now = ctx.currentTime;
    env.gain.setValueAtTime(0, now);
    env.gain.linearRampToValueAtTime(opts.level, now + Math.min(0.012, opts.dur * 0.2));
    env.gain.exponentialRampToValueAtTime(0.0001, now + opts.dur);

    src.connect(filter);
    filter.connect(env);
    env.connect(master);
    src.start(now);
    src.stop(now + opts.dur + 0.02);
  }

  /** 落地一步。抽成独立函数，避免 update 里用 this（外部解构调用时会丢上下文） */
  function doStep(kind: GroundKind): void {
    if (!ready || muted) return;
    // 石板：高频脆响 + 一点低频落脚感；城外土坡：闷
    if (kind === 'stone') {
      burst({ freq: 2100, q: 1.5, dur: 0.075, level: 0.1 });
      burst({ freq: 320, q: 0.9, dur: 0.055, level: 0.06, type: 'lowpass' });
    } else {
      burst({ freq: 760, q: 1.1, dur: 0.085, level: 0.085 });
    }
  }

  /** 一记拨弦：三角波 + 快起慢落包络，经乐坊琴音链（音量门 + 声场）出声 */
  function doPluck(freq: number, when: number): void {
    if (!ctx || !musicGain) return;
    const osc = ctx.createOscillator();
    osc.type = 'triangle';
    osc.frequency.value = freq;
    const env = ctx.createGain();
    env.gain.setValueAtTime(0, when);
    env.gain.linearRampToValueAtTime(0.9, when + 0.008);
    env.gain.exponentialRampToValueAtTime(0.0001, when + 1.8);
    osc.connect(env);
    env.connect(musicGain);
    osc.start(when);
    osc.stop(when + 1.85);
  }

  return {
    unlock() {
      if (disposed) return;
      if (!ensure() || !ctx) return;
      if (ctx.state === 'suspended') {
        // resume 是 Promise；失败也只是没声音，不该冒泡到场景
        void ctx.resume().then(
          () => {
            ready = true;
          },
          () => {
            ready = false;
          },
        );
      } else {
        ready = true;
      }
    },

    toggleMuted() {
      muted = !muted;
      writeMuted(muted);
      if (master && ctx) {
        // 用短斜坡而非直接赋值，避免"啪"的爆音
        master.gain.cancelScheduledValues(ctx.currentTime);
        master.gain.setTargetAtTime(muted ? 0 : 0.55, ctx.currentTime, 0.04);
      }
      return muted;
    },

    isMuted: () => muted,
    isReady: () => ready && !muted,
    stageGain: () => musicTarget,

    step: doStep,

    toll() {
      if (!ready || muted || !ctx || !master) return;
      const now = ctx.currentTime;
      // 青铜钟的非谐分音：1 / 2.0 / 2.76 / 5.4，实测比纯谐波更像"钟"
      const partials: Array<[number, number, number]> = [
        [1, 0.34, 4.6],
        [2.0, 0.17, 3.4],
        [2.76, 0.1, 2.6],
        [5.4, 0.045, 1.7],
      ];
      const f0 = 118;
      for (const [ratio, level, decay] of partials) {
        const osc = ctx.createOscillator();
        osc.type = 'sine';
        osc.frequency.value = f0 * ratio;
        const env = ctx.createGain();
        env.gain.setValueAtTime(0, now);
        env.gain.linearRampToValueAtTime(level, now + 0.012);
        env.gain.exponentialRampToValueAtTime(0.0001, now + decay);
        osc.connect(env);
        env.connect(master);
        osc.start(now);
        osc.stop(now + decay + 0.05);
      }
    },

    portal() {
      if (!ready || muted || !ctx || !master) return;
      const now = ctx.currentTime;
      // 上行扫频 + 反向噪声：进出传送门那一下"吸"的感觉
      const osc = ctx.createOscillator();
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(280, now);
      osc.frequency.exponentialRampToValueAtTime(920, now + 0.42);
      const env = ctx.createGain();
      env.gain.setValueAtTime(0, now);
      env.gain.linearRampToValueAtTime(0.13, now + 0.05);
      env.gain.exponentialRampToValueAtTime(0.0001, now + 0.6);
      osc.connect(env);
      env.connect(master);
      osc.start(now);
      osc.stop(now + 0.65);

      burst({ freq: 1400, q: 0.6, dur: 0.4, level: 0.06, sweepTo: 3200 });
    },

    speak() {
      if (!ready || muted) return;
      // 一句"喂"的上扬：短、轻，反复喊话不该吵
      burst({ freq: 520, q: 1.8, dur: 0.13, level: 0.07, sweepTo: 880 });
    },

    setWeather(kind) {
      if (kind === WEATHER_RAIN) {
        rainTarget = 0.16;
        windTarget = 0.02;
      } else if (kind === WEATHER_SNOW) {
        // 雪天最静：只剩一点风
        rainTarget = 0;
        windTarget = 0.05;
      } else if (kind === WEATHER_FOG) {
        rainTarget = 0;
        windTarget = 0.075;
      } else {
        rainTarget = 0;
        windTarget = 0.012;
      }
    },

    setStageProximity(dist, bearing) {
      if (dist <= MUSIC_FAR) {
        // 线性衰减：6 米内全量、30 米外归零；方位用正弦映射到 [-1,1] 声场
        musicTarget = Math.max(0, 1 - (dist - MUSIC_NEAR) / (MUSIC_FAR - MUSIC_NEAR)) * MUSIC_LEVEL;
        musicPanTarget = Math.max(-1, Math.min(1, Math.sin(bearing)));
      } else {
        musicTarget = 0;
      }
    },

    update(dt, speed, ground) {
      if (!ready || muted) return;

      // 环境床平滑：3 秒量级过渡，避免切天气时"咔"一下
      if (ctx && rainGain && windGain) {
        rainGain.gain.setTargetAtTime(rainTarget, ctx.currentTime, 0.8);
        windGain.gain.setTargetAtTime(windTarget, ctx.currentTime, 1.2);
      }

      // 乐坊琴音：音量门/声场平滑 + lookahead 音序调度。
      // 必须放在脚步提前 return 之前——玩家站着听的时候脚步不动，琴音不能停。
      if (ctx && musicGain) {
        musicGain.gain.setTargetAtTime(musicTarget, ctx.currentTime, 0.5);
        if (musicPan) {
          musicPan.pan.setTargetAtTime(musicPanTarget, ctx.currentTime, 0.4);
        }
        if (musicTarget > 0) {
          // 0.12s lookahead：到点前一撮先排下去，避免主线程抖动造成断音
          while (musicNextAt < ctx.currentTime + 0.12) {
            const when = Math.max(musicNextAt, ctx.currentTime + 0.02);
            doPluck(PENTATONIC[scaleIndex], when);
            // 相邻音漫步：±1 或原地重复，不成调不成曲
            scaleIndex = Math.max(
              0,
              Math.min(PENTATONIC.length - 1, scaleIndex + (Math.random() < 0.5 ? -1 : 1)),
            );
            musicNextAt = when + 0.5 + Math.random() * 0.55;
          }
        } else {
          // 离开了乐坊：调度点推到"现在"，回来时不补播缺席期间欠下的一串音
          musicNextAt = ctx.currentTime;
        }
      }

      // 步频跟速度走：慢走约 1.6 步/秒，疾行约 3.2 步/秒
      if (speed < 0.6) {
        stepAccum = 0;
        return;
      }
      stepAccum += dt;
      const interval = clampStep(speed);
      if (stepAccum >= interval) {
        stepAccum = 0;
        doStep(ground);
      }
    },

    dispose() {
      disposed = true;
      ready = false;
      try {
        void ctx?.close();
      } catch {
        /* 关闭失败无所谓，页面已在卸载 */
      }
      ctx = null;
      master = null;
      noise = null;
      rainGain = null;
      windGain = null;
      musicGain = null;
      musicPan = null;
    },
  };
}

/** 速度越快步频越高，但夹在 0.62–0.24 秒之间，太密会糊成噪声 */
function clampStep(speed: number): number {
  const interval = 0.62 - Math.min(speed, 16) * 0.024;
  return Math.max(0.24, Math.min(0.62, interval));
}

export { silentAudio };
