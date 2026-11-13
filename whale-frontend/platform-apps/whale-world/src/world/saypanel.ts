/**
 * 同城喊话面板 —— 按 Q 打开，选一句或自己写一句。
 *
 * <p>为什么要有预设短语：这个 3D 城是企业平台的门户，玩家多半是同事，
 * 打字交流的需求远低于"路过打个招呼"。预设短语点一下就发出去，不用切输入法、
 * 不用抢焦点；想认真说话时再落到输入框。</p>
 *
 * <p>发送后<b>不关面板</b>：连着说两句很常见，每说一句都要重开面板会很烦。
 * 关面板交给 Esc 或再按一次 Q。</p>
 */

/** 预设短语：都是"路过时说得出口"的短句 */
const PRESETS = [
  '见过主公。',
  '借问一声，数枢殿怎么走？',
  '今日城中可有什么新鲜事？',
  '哪位在户部当值？',
  '失陪了。',
  '就此别过。',
];

export interface SayPanelOptions {
  /** 发送一句；返回 false 表示没发出去（未登录/未连上） */
  onSend: (text: string) => void;
  /** 面板开合回调：场景层据此暂停行走 */
  onOpenChange?: (open: boolean) => void;
}

export interface SayPanelHandle {
  open: () => void;
  close: () => void;
  toggle: () => void;
  isOpen: () => boolean;
  destroy: () => void;
}

/**
 * 挂载喊话面板。
 *
 * @param root 容器（`.bww-say-panel`）
 * @param options 发送回调
 */
export function mountSayPanel(root: HTMLElement, options: SayPanelOptions): SayPanelHandle {
  let opened = false;
  let destroyed = false;

  root.innerHTML = '';

  const inner = document.createElement('div');
  inner.className = 'bww-say-inner';

  const head = document.createElement('div');
  head.className = 'bww-say-head';
  const title = document.createElement('span');
  title.className = 'bww-say-title';
  title.textContent = '同城喊话';
  const closeBtn = document.createElement('button');
  closeBtn.type = 'button';
  closeBtn.className = 'bww-say-close';
  closeBtn.textContent = '×';
  closeBtn.title = '收起（Esc）';
  closeBtn.addEventListener('click', () => close());
  head.append(title, closeBtn);

  const presets = document.createElement('div');
  presets.className = 'bww-say-presets';
  for (const text of PRESETS) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'bww-say-preset';
    btn.textContent = text;
    btn.addEventListener('click', () => {
      options.onSend(text);
      // 发完把焦点还给画布，否则下一次按 WASD 会被当成输入
      blurInput();
    });
    presets.appendChild(btn);
  }

  const form = document.createElement('form');
  form.className = 'bww-say-form';
  const input = document.createElement('input');
  input.className = 'bww-say-input';
  input.type = 'text';
  input.maxLength = 120;
  input.placeholder = '说一句（回车发出）';
  const sendBtn = document.createElement('button');
  sendBtn.type = 'submit';
  sendBtn.className = 'bww-say-send';
  sendBtn.textContent = '说出';
  form.append(input, sendBtn);
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const text = input.value.trim();
    if (!text) {
      return;
    }
    options.onSend(text);
    input.value = '';
  });

  const foot = document.createElement('div');
  foot.className = 'bww-say-foot';
  foot.textContent = '附近的人能看到 · 1.5 秒内只说一句';

  inner.append(head, presets, form, foot);
  root.appendChild(inner);

  function blurInput(): void {
    if (document.activeElement === input) {
      input.blur();
    }
  }

  function open(): void {
    if (opened || destroyed) {
      return;
    }
    opened = true;
    root.classList.add('bww-open');
    options.onOpenChange?.(true);
    // 延后聚焦：刚打开就抢焦点会让浏览器把整页滚动一下
    window.setTimeout(() => {
      if (opened) {
        input.focus();
      }
    }, 120);
  }

  function close(): void {
    if (!opened || destroyed) {
      return;
    }
    opened = false;
    // 收起必须 blur：否则下一次 WASD 会被 isTypingTarget 当成打字吞掉
    blurInput();
    root.classList.remove('bww-open');
    options.onOpenChange?.(false);
  }

  return {
    open,
    close,
    toggle: () => (opened ? close() : open()),
    isOpen: () => opened,
    destroy() {
      destroyed = true;
      root.classList.remove('bww-open');
      root.innerHTML = '';
    },
  };
}
