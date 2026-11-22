import * as THREE from 'three';

/**
 * 影戏院（S7/T7.2）的幕布：一块真的在放的视频纹理。
 *
 * <p><b>为什么不用静态贴图。</b>影戏院在城里的意思就是"这里在放东西"，
 * 一块不动的画也叫影戏院就说不通。所以幕布用 {@link THREE.VideoTexture}：
 * 画面来自 canvas 的实时皮影戏，经 {@code captureStream} 灌进 {@code <video>}，
 * 再由 three 把视频当纹理逐帧上传。</p>
 *
 * <p><b>为什么片源是程序化画的，不是 mp4。</b>城里有二十多块屏风页，
 * 再挂一个几百 KB 的视频文件意味着首屏多一次下载、离线与软渲染环境多一种
 * 失败可能（编解码器、跨域、自动播放策略）。皮影戏用 2D canvas 现画：
 * 零网络依赖、零解码器依赖、帧内容完全可控——e2e 能靠"帧计数在涨"验它在动。</p>
 *
 * <p><b>降级不是失败。</b>{@code captureStream} 在个别环境不可用（老浏览器、
 * 部分软件渲染配置）。此时直接拿 canvas 当 {@link THREE.CanvasTexture}，
 * 画面照样在动，只是少了"真视频"这一层；{@link StageScreenHandle.probe}
 * 里的 {@code mode} 会写明走的是哪条路，便于排障而不是靠猜。</p>
 *
 * <p><b>走远就停。</b>幕布从城北看只有一个亮点，没必要在 90 米外继续重绘、
 * 继续解码。{@link StageScreenHandle.update} 接收观察距离，超出
 * {@link STAGE_VISIBLE_RANGE} 就暂停视频并停止重绘。</p>
 *
 * @author BlueWhale
 */

/** 幕布画面尺寸（16:9）：480×270 远看够清楚，软渲染下也不至于拖帧 */
const SCREEN_W = 480;

/** 幕布画面高度 */
const SCREEN_H = 270;

/** 重绘节流（秒）：20fps 对皮影戏足够，省下的算力留给主场景 */
const FRAME_INTERVAL = 1 / 20;

/** 走近多少米内才放映：更远则暂停，省解码与重绘 */
export const STAGE_VISIBLE_RANGE = 46;

/** 轮播片目：四秒一换，像戏院门口挂的水牌 */
const SHOWS = ['《鲸背上的城》', '《夜市灯影》', '《海上来客》', '《归潮》'];

/** 单部片目停留秒数 */
const SHOW_SECONDS = 4;

/**
 * 幕布句柄：城市主循环每帧喂一次距离，释放时统一销毁。
 */
export interface StageScreenHandle {
  /** 幕布网格（含立柱与边框的整组，挂到场景即可） */
  group: THREE.Group;
  /**
   * 每帧更新：按观察距离决定放不放映
   *
   * @param dt 距上一帧的秒数
   * @param distance 观察者到幕布的距离（米）
   */
  update(dt: number, distance: number): void;
  /** 释放：停流、停视频、销毁纹理与几何 */
  dispose(): void;
  /**
   * 状态探针（e2e 无法"看"，只能读状态）
   *
   * @returns 放映方式、帧计数、画面尺寸、是否在放、是否走近
   */
  probe(): {
    mode: 'video' | 'canvas';
    frames: number;
    width: number;
    height: number;
    playing: boolean;
    near: boolean;
    shows: number;
    /** captureStream / play 失败原因（null 表示没出错）；排障时先看这一项 */
    videoError: string | null;
  };
}

/**
 * 画一帧皮影戏。
 *
 * <p>纯函数式绘制：清除 → 背景 → 灯箱 → 皮影人 → 水牌字幕 → 颗粒与扫描线。
 * {@code t} 是秒，所有动作都从它派生，因此每一帧都是确定的（e2e 截图可比对）。
 *
 * @param ctx 画布上下文
 * @param t 秒
 */
function drawFrame(ctx: CanvasRenderingContext2D, t: number): void {
  ctx.clearRect(0, 0, SCREEN_W, SCREEN_H);

  // 灯箱底色：暖黄中心 + 两侧压暗，像油灯打在薄纱上
  const glow = ctx.createRadialGradient(SCREEN_W / 2, SCREEN_H * 0.55, 20, SCREEN_W / 2, SCREEN_H * 0.55,
    SCREEN_W * 0.72);
  glow.addColorStop(0, '#f6e2b8');
  glow.addColorStop(0.55, '#e0c07e');
  glow.addColorStop(1, '#8a6a3a');
  ctx.fillStyle = glow;
  ctx.fillRect(0, 0, SCREEN_W, SCREEN_H);

  // 皮影人：三个关节按正弦摆动，像有人提着签子在幕后走
  const sway = Math.sin(t * 1.6);
  const cx = SCREEN_W * 0.5 + Math.sin(t * 0.5) * 26;
  const groundY = SCREEN_H * 0.86;
  ctx.strokeStyle = 'rgba(58, 34, 18, 0.92)';
  ctx.fillStyle = 'rgba(58, 34, 18, 0.92)';
  ctx.lineWidth = 4;
  ctx.lineCap = 'round';

  // 头
  ctx.beginPath();
  ctx.arc(cx, groundY - 116 + sway * 2, 13, 0, Math.PI * 2);
  ctx.fill();
  // 躯干
  ctx.beginPath();
  ctx.moveTo(cx, groundY - 103 + sway * 2);
  ctx.lineTo(cx + sway * 3, groundY - 52);
  ctx.stroke();
  // 双臂：一手提线一手甩袖
  ctx.beginPath();
  ctx.moveTo(cx, groundY - 92);
  ctx.lineTo(cx - 34 - sway * 8, groundY - 78 + sway * 6);
  ctx.moveTo(cx, groundY - 92);
  ctx.lineTo(cx + 30 + sway * 10, groundY - 104 - sway * 5);
  ctx.stroke();
  // 双腿：迈步
  ctx.beginPath();
  ctx.moveTo(cx + sway * 3, groundY - 52);
  ctx.lineTo(cx - 16 - sway * 10, groundY);
  ctx.moveTo(cx + sway * 3, groundY - 52);
  ctx.lineTo(cx + 18 + sway * 12, groundY);
  ctx.stroke();

  // 地面：一条深色横带，稳住画面下缘
  ctx.fillStyle = 'rgba(52, 32, 16, 0.55)';
  ctx.fillRect(0, groundY + 2, SCREEN_W, SCREEN_H - groundY);

  // 水牌字幕：底部一条深色带，轮播片目
  const show = SHOWS[Math.floor(t / SHOW_SECONDS) % SHOWS.length];
  ctx.fillStyle = 'rgba(28, 18, 10, 0.78)';
  ctx.fillRect(0, SCREEN_H - 34, SCREEN_W, 34);
  ctx.fillStyle = '#f4d98f';
  ctx.font = '600 20px "Noto Sans SC", "Microsoft YaHei", sans-serif';
  ctx.textBaseline = 'middle';
  ctx.fillText(show, 16, SCREEN_H - 16);

  // 胶片颗粒：每帧随机十几粒，静止画面也有"在放"的呼吸感
  for (let i = 0; i < 14; i += 1) {
    const gx = Math.random() * SCREEN_W;
    const gy = Math.random() * SCREEN_H;
    ctx.fillStyle = `rgba(255, 255, 255, ${0.03 + Math.random() * 0.05})`;
    ctx.fillRect(gx, gy, 2, 2);
  }

  // 扫描线：两道缓慢下移的亮带，模拟老放映机的抖动
  const band = ((t * 26) % (SCREEN_H + 80)) - 40;
  ctx.fillStyle = 'rgba(255, 255, 255, 0.06)';
  ctx.fillRect(0, band, SCREEN_W, 26);
}

/**
 * 建一块幕布。
 *
 * <p>返回的 {@code group} 以幕布底边中点为原点，调用方只需把整组摆到世界里并
 * 给一个朝向；内部所含的视频元素不入 DOM 树（{@code <video>} 不插入文档也能解码，
 * 少了它就不参与布局，也不会挡住屏风页）。</p>
 *
 * @param width 幕布宽（米）
 * @param height 幕布高（米）
 * @returns 幕布句柄
 */
export function createStageScreen(width: number, height: number): StageScreenHandle {
  const canvas = document.createElement('canvas');
  canvas.width = SCREEN_W;
  canvas.height = SCREEN_H;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('影戏院幕布需要 2D canvas 上下文');
  }
  drawFrame(ctx, 0);

  const group = new THREE.Group();

  // 幕布本体：先挂 canvas 纹理，captureStream 成功后再升级成 VideoTexture
  const fallback = new THREE.CanvasTexture(canvas);
  fallback.colorSpace = THREE.SRGBColorSpace;
  const screenMat = new THREE.MeshBasicMaterial({ map: fallback, toneMapped: false });
  const screenMesh = new THREE.Mesh(new THREE.PlaneGeometry(width, height), screenMat);
  screenMesh.position.y = height / 2;
  group.add(screenMesh);

  // 边框与立柱：四面木框 + 两根落地柱，远看能读出"这是一块银幕"
  const frameMat = new THREE.MeshStandardMaterial({ color: 0x241a12, roughness: 0.8 });
  const postGeo = new THREE.BoxGeometry(0.22, height + 0.5, 0.22);
  for (const side of [-1, 1]) {
    const post = new THREE.Mesh(postGeo, frameMat);
    post.position.set(side * (width / 2 + 0.16), (height + 0.5) / 2, 0);
    post.castShadow = true;
    group.add(post);
  }
  const beamGeo = new THREE.BoxGeometry(width + 0.6, 0.2, 0.24);
  for (const y of [height + 0.1, 0.02]) {
    const beam = new THREE.Mesh(beamGeo, frameMat);
    beam.position.y = y;
    group.add(beam);
  }
  // 幕布自身微微发光，夜里从城北也能看见这一处亮着
  const lamp = new THREE.PointLight(0xffd9a0, 6, 22, 2);
  lamp.position.set(0, height * 0.55, 0.6);
  group.add(lamp);

  let mode: 'video' | 'canvas' = 'canvas';
  let video: HTMLVideoElement | null = null;
  let stream: MediaStream | null = null;
  let frames = 0;
  let acc = 0;
  let near = true;
  let disposed = false;
  let videoError: string | null = null;
  /** 是否已升级成 VideoTexture（只升级一次：替换 map 后再换会白丢一张纹理） */
  let upgraded = false;
  /** 视频当前是否真的在播（`video.paused` 在 play() 未 resolve 时仍为 true，不可靠） */
  let playing = false;
  /** 正在等 play() 的 Promise：同一时刻只发一次，避免 play/pause 互踩 */
  let pending: Promise<void> | null = null;

  /**
   * 开始放映：play() → 升级 VideoTexture。
   *
   * <p><b>AbortError 不是失败。</b>{@code play()} 返回 Promise 期间若调了
   * {@code pause()}，浏览器会以 {@code AbortError} 拒绝它——这不是"放不了"，
   * 只是"这次被打断了"。早先把它当失败处理，结果第一帧就被后面的 pause 打断，
   * 之后再也不重试，幕布永远停在 canvas 纹理上（探针 mode 一直是 canvas）。
   * 所以这里只对非 AbortError 记错，AbortError 留给下次 update 再试。</p>
   */
  function startPlayback(): void {
    if (!video || playing || pending) {
      return;
    }
    const applied = video;
    pending = applied.play().then(() => {
      pending = null;
      playing = true;
      if (disposed || upgraded) {
        return;
      }
      const texture = new THREE.VideoTexture(applied);
      texture.colorSpace = THREE.SRGBColorSpace;
      screenMat.map = texture;
      screenMat.needsUpdate = true;
      fallback.dispose();
      upgraded = true;
      mode = 'video';
    }).catch((err: unknown) => {
      pending = null;
      playing = false;
      const name = (err as { name?: string } | null)?.name;
      if (name === 'AbortError') {
        return;
      }
      /* 自动播放被拒等硬失败：留在 canvas 纹理上，画面一样动；原因留给探针，别静默 */
      videoError = `video.play 失败：${String(err)}`;
    });
  }

  /**
   * 停止放映：pause() 前必须确认"真的在播"。
   *
   * <p>若 play() 还挂着就 pause()，等于主动制造 AbortError；这也是
   * 上一版永远升不了级的一半原因。</p>
   */
  function stopPlayback(): void {
    if (!video || !playing) {
      return;
    }
    video.pause();
    playing = false;
  }

  /* captureStream 把 canvas 当作直播源，`<video>` 只做"解码中转"，不进 DOM。
     muted + playsInline 是自动播放的前提。注意：这里**不**立刻 play()——
     开场的 play/pause 互踩就是 AbortError 的来源，第一次 update 再决定放不放。 */
  const capture = typeof canvas.captureStream === 'function' ? canvas.captureStream(24) : null;
  if (!capture) {
    videoError = 'captureStream 不可用，回落 canvas 纹理';
  }
  if (capture) {
    video = document.createElement('video');
    video.muted = true;
    video.playsInline = true;
    video.autoplay = false;
    video.srcObject = capture;
    stream = capture;
  }

  return {
    group,
    update(dt: number, distance: number): void {
      if (disposed) {
        return;
      }
      near = distance <= STAGE_VISIBLE_RANGE;
      if (!near) {
        /* 走远即停：不重绘、不解码。`playing` 探针据此区分"没接线"与"走远了" */
        stopPlayback();
        return;
      }
      startPlayback();
      acc += dt;
      if (acc < FRAME_INTERVAL) {
        return;
      }
      acc = 0;
      frames += 1;
      drawFrame(ctx, performance.now() / 1000);
      if (!upgraded) {
        /* 升级前 canvas 是纹理源；升级后由 VideoTexture 自己逐帧上传 */
        fallback.needsUpdate = true;
      }
    },
    dispose(): void {
      disposed = true;
      stream?.getTracks().forEach((track) => track.stop());
      stream = null;
      if (video) {
        video.pause();
        video.srcObject = null;
        video = null;
      }
      screenMat.map?.dispose();
      screenMat.dispose();
      screenMesh.geometry.dispose();
      frameMat.dispose();
      postGeo.dispose();
      beamGeo.dispose();
    },
    probe() {
      return {
        mode,
        frames,
        width: SCREEN_W,
        height: SCREEN_H,
        playing,
        near,
        shows: SHOWS.length,
        videoError,
      };
    },
  };
}
