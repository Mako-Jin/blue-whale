/* ==========================================================================
 * 「录名入城」角色面板
 *
 * 纯 DOM 实现（不引入 React 状态，避免与 three 主循环互相牵制）：
 *  - 所有改动实时应用到世界里的角色（onChange）
 *  - 独立预览渲染器，旋转展示当前形象
 *  - 照片取色：不改三维结构，只迁移发/肤/衣三段主色 → 保持古风统一
 *  - 保存仅写 localStorage，不发请求（对 2D 零影响）
 * ========================================================================== */

import {
  DEFAULT_AVATAR,
  HAIR_COLORS,
  ROBE_COLORS,
  SKIN_TONES,
  createAvatarPreview,
} from './avatar';
import type { AvatarParams, AvatarPreview } from './avatar';
import { analyzePhoto } from './profile';

export interface CharPanelOptions {
  params: AvatarParams;
  onChange: (p: AvatarParams) => void;
  onSave: (p: AvatarParams) => void;
  onClose?: () => void;
}

export interface CharPanelHandle {
  open: () => void;
  close: () => void;
  toggle: () => void;
  isOpen: () => boolean;
  setParams: (p: AvatarParams) => void;
  destroy: () => void;
}

const HAIR_LABELS = ['束发', '披发', '冠冕'];
const ACC_LABELS = ['无', '佩玉', '背剑'];

function q<T extends Element>(root: ParentNode, sel: string): T {
  const el = root.querySelector(sel);
  if (!el) {
    throw new Error(`charpanel: 缺少元素 ${sel}`);
  }
  return el as T;
}

export function mountCharPanel(container: HTMLElement, opts: CharPanelOptions): CharPanelHandle {
  let params: AvatarParams = { ...opts.params };
  let preview: AvatarPreview | null = null;
  let opened = false;

  const swatches = (palette: readonly string[], key: 'skin' | 'robe' | 'hair') =>
    palette
      .map(
        (c) =>
          `<button type="button" class="bww-cp-sw" data-swatch="${key}" data-color="${c}" style="--sw:${c}" title="${c}"></button>`,
      )
      .join('');

  const chips = (values: number[], key: string, labels: string[]) =>
    values
      .map(
        (v, i) =>
          `<button type="button" class="bww-cp-chip" data-chip="${key}" data-value="${v}">${labels[i]}</button>`,
      )
      .join('');

  container.innerHTML = `
    <div class="bww-cp-inner">
      <div class="bww-cp-stage">
        <canvas class="bww-cp-canvas"></canvas>
        <div class="bww-cp-tag">
          <strong data-cp-nameview>${params.name}</strong>
          <span>城民 · BlueWhale</span>
        </div>
      </div>
      <div class="bww-cp-form">
        <div class="bww-cp-head">
          <h2>录 名 入 城</h2>
          <button type="button" class="bww-cp-close" aria-label="关闭">×</button>
        </div>
        <p class="bww-cp-tip">调形貌即改城中形象；也可传一张照片自动取色。形象只存本机，不入业务库。</p>

        <div class="bww-cp-row">
          <label for="bww-cp-name">姓名</label>
          <input id="bww-cp-name" class="bww-cp-name" maxlength="8" value="${params.name}" />
        </div>

        <div class="bww-cp-group">
          <div class="bww-cp-label">身形</div>
          <div class="bww-cp-slider"><span>身高</span><input type="range" data-slider="height" min="0.9" max="1.15" step="0.01" value="${params.height}" /><i data-out="height"></i></div>
          <div class="bww-cp-slider"><span>体宽</span><input type="range" data-slider="build" min="0.85" max="1.18" step="0.01" value="${params.build}" /><i data-out="build"></i></div>
          <div class="bww-cp-slider"><span>脸宽</span><input type="range" data-slider="faceWidth" min="0.9" max="1.12" step="0.01" value="${params.faceWidth}" /><i data-out="faceWidth"></i></div>
        </div>

        <div class="bww-cp-group">
          <div class="bww-cp-label">发式</div>
          <div class="bww-cp-chips" data-chips="hairStyle">${chips([0, 1, 2], 'hairStyle', HAIR_LABELS)}</div>
          <label class="bww-cp-check"><input type="checkbox" data-check="beard" /> 蓄须</label>
        </div>

        <div class="bww-cp-group">
          <div class="bww-cp-label">肤色</div>
          <div class="bww-cp-swatches">${swatches(SKIN_TONES, 'skin')}</div>
          <div class="bww-cp-label">衣色</div>
          <div class="bww-cp-swatches">${swatches(ROBE_COLORS, 'robe')}</div>
          <div class="bww-cp-label">发色</div>
          <div class="bww-cp-swatches">${swatches(HAIR_COLORS, 'hair')}</div>
        </div>

        <div class="bww-cp-group">
          <div class="bww-cp-label">配饰</div>
          <div class="bww-cp-chips" data-chips="accessory">${chips([0, 1, 2], 'accessory', ACC_LABELS)}</div>
        </div>

        <div class="bww-cp-photo">
          <input type="file" accept="image/*" class="bww-cp-file" hidden />
          <button type="button" class="bww-cp-photo-btn">照片取色</button>
          <img class="bww-cp-thumb" alt="" />
          <span class="bww-cp-note"></span>
        </div>

        <div class="bww-cp-actions">
          <button type="button" class="bww-cp-save">保存 · 入城</button>
          <button type="button" class="bww-cp-reset">重置</button>
        </div>
      </div>
    </div>
  `;

  const nameInput = q<HTMLInputElement>(container, '.bww-cp-name');
  const nameView = q<HTMLElement>(container, '[data-cp-nameview]');
  const canvas = q<HTMLCanvasElement>(container, '.bww-cp-canvas');
  const fileInput = q<HTMLInputElement>(container, '.bww-cp-file');
  const thumb = q<HTMLImageElement>(container, '.bww-cp-thumb');
  const note = q<HTMLElement>(container, '.bww-cp-note');
  const beardInput = q<HTMLInputElement>(container, '[data-check="beard"]');
  const outEls = Array.from(container.querySelectorAll<HTMLElement>('[data-out]'));

  /** 同步所有控件的选中态与数值显示 */
  const sync = () => {
    nameView.textContent = params.name;
    nameInput.value = params.name;
    beardInput.checked = params.beard;

    for (const out of outEls) {
      const key = out.dataset.out as 'height' | 'build' | 'faceWidth';
      out.textContent = params[key].toFixed(2);
    }
    for (const input of Array.from(container.querySelectorAll<HTMLInputElement>('[data-slider]'))) {
      const key = input.dataset.slider as 'height' | 'build' | 'faceWidth';
      input.value = String(params[key]);
    }
    for (const sw of Array.from(container.querySelectorAll<HTMLElement>('[data-swatch]'))) {
      const key = sw.dataset.swatch as 'skin' | 'robe' | 'hair';
      sw.classList.toggle(
        'is-on',
        params[key].toLowerCase() === (sw.dataset.color || '').toLowerCase(),
      );
    }
    for (const chip of Array.from(container.querySelectorAll<HTMLElement>('[data-chip]'))) {
      const key = chip.dataset.chip as 'hairStyle' | 'accessory';
      chip.classList.toggle('is-on', String(params[key]) === chip.dataset.value);
    }
  };

  const emit = () => {
    preview?.setParams(params);
    sync();
    opts.onChange(params);
  };

  /* ---- 输入事件 ---- */
  const onInput = (e: Event) => {
    const el = e.target;
    if (!(el instanceof HTMLInputElement)) {
      return;
    }
    if (el.dataset.slider) {
      const key = el.dataset.slider as 'height' | 'build' | 'faceWidth';
      params = { ...params, [key]: Number(el.value) };
      emit();
    }
    if (el.dataset.check === 'beard') {
      params = { ...params, beard: el.checked };
      emit();
    }
  };
  container.addEventListener('input', onInput);

  const onClick = (e: Event) => {
    const target = e.target;
    if (!(target instanceof HTMLElement)) {
      return;
    }
    const sw = target.closest<HTMLElement>('[data-swatch]');
    if (sw) {
      const key = sw.dataset.swatch as 'skin' | 'robe' | 'hair';
      params = { ...params, [key]: sw.dataset.color || params[key] };
      emit();
      return;
    }
    const chip = target.closest<HTMLElement>('[data-chip]');
    if (chip) {
      const key = chip.dataset.chip;
      const value = Number(chip.dataset.value);
      if (key === 'hairStyle' && (value === 0 || value === 1 || value === 2)) {
        params = { ...params, hairStyle: value };
        emit();
      } else if (key === 'accessory' && (value === 0 || value === 1 || value === 2)) {
        params = { ...params, accessory: value };
        emit();
      }
      return;
    }
    if (target.closest('.bww-cp-close')) {
      handle.close();
      return;
    }
    if (target.closest('.bww-cp-photo-btn')) {
      fileInput.click();
      return;
    }
    if (target.closest('.bww-cp-reset')) {
      params = { ...DEFAULT_AVATAR, name: params.name };
      emit();
      note.textContent = '';
      thumb.classList.remove('is-on');
      return;
    }
    if (target.closest('.bww-cp-save')) {
      opts.onSave(params);
      handle.close();
    }
  };
  container.addEventListener('click', onClick);

  const onNameInput = () => {
    const v = nameInput.value.trim().slice(0, 8);
    params = { ...params, name: v || DEFAULT_AVATAR.name };
    nameView.textContent = params.name;
    opts.onChange(params);
  };
  nameInput.addEventListener('input', onNameInput);

  const onFile = async () => {
    const file = fileInput.files?.[0];
    if (!file) {
      return;
    }
    note.textContent = '取色中…';
    try {
      const colors = await analyzePhoto(file);
      thumb.src = colors.thumb;
      thumb.classList.add('is-on');
      params = { ...params, skin: colors.skin, robe: colors.robe, hair: colors.hair };
      emit();
      note.textContent = `已取色：肤 ${colors.skin} · 衣 ${colors.robe}`;
    } catch {
      note.textContent = '这张读不出来，换一张试试';
    } finally {
      fileInput.value = '';
    }
  };
  fileInput.addEventListener('change', () => {
    void onFile();
  });

  sync();

  const handle: CharPanelHandle = {
    open() {
      if (opened) {
        return;
      }
      opened = true;
      container.classList.add('bww-open');
      if (!preview) {
        preview = createAvatarPreview(canvas, params);
      }
      // 等布局稳定后再取尺寸
      requestAnimationFrame(() => preview?.resize());
    },
    close() {
      if (!opened) {
        return;
      }
      opened = false;
      container.classList.remove('bww-open');
      // 面板关闭即释放预览渲染器：闲置的第二个 WebGL context 不该继续占用 GPU
      preview?.dispose();
      preview = null;
      opts.onClose?.();
    },
    toggle() {
      if (opened) {
        handle.close();
      } else {
        handle.open();
      }
    },
    isOpen() {
      return opened;
    },
    setParams(p) {
      params = { ...p };
      sync();
      preview?.setParams(params);
    },
    destroy() {
      container.removeEventListener('input', onInput);
      container.removeEventListener('click', onClick);
      nameInput.removeEventListener('input', onNameInput);
      preview?.dispose();
      preview = null;
      container.innerHTML = '';
    },
  };

  return handle;
}
