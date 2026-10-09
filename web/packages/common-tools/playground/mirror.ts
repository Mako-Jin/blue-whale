import type { ConsoleMethod } from '../src/logger';

/** 去掉转义用的零宽空格，让页面显示干净文本。 */
function clean(text: string): string {
  return text.split('\u200b').join('');
}

/**
 * 控制台镜像。
 *
 * 接收 logger 的 sink 回调，把「格式串 + 样式串」这一对还原成 DOM：
 * `%c` 切分出片段，按顺序取对应数量的样式串，剩下的才是数据参数。
 * 这样页面上看到的就是浏览器控制台里真实呈现的颜色与排版。
 */
export class ConsoleMirror {
  private depth = 0;

  constructor(private readonly container: HTMLElement) {}

  /** 供 logger 使用的 sink。 */
  readonly sink = (method: ConsoleMethod, args: unknown[]): void => {
    this.handle(method, args);
  };

  handle(method: ConsoleMethod, args: unknown[]): void {
    switch (method) {
      case 'clear':
        this.container.replaceChildren();
        this.depth = 0;
        return;
      case 'groupEnd':
        this.depth = Math.max(0, this.depth - 1);
        return;
      case 'group':
      case 'groupCollapsed':
        this.container.append(this.renderRow(method, args));
        this.depth += 1;
        return;
      case 'table':
        this.container.append(this.renderTable(args));
        return;
      default:
        this.container.append(this.renderRow(method, args));
    }
  }

  private renderRow(method: ConsoleMethod, args: unknown[]): HTMLElement {
    const row = document.createElement('div');
    row.className = `mirror-line mirror-line--${method}`;
    row.style.paddingLeft = `${this.depth * 16}px`;

    const first = args[0];
    if (typeof first !== 'string') {
      row.textContent = args.map((item) => this.stringify(item)).join(' ');
      return row;
    }

    const segments = first.split('%c');
    const styleCount = segments.length - 1;
    const styles = args.slice(1, 1 + styleCount).map((item) => String(item));
    const dataArgs = args.slice(1 + styleCount);

    segments.forEach((text, index) => {
      const span = document.createElement('span');
      span.textContent = clean(text);
      if (index > 0 && styles[index - 1]) {
        span.setAttribute('style', styles[index - 1]);
      }
      row.append(span);
    });

    for (const item of dataArgs) {
      const span = document.createElement('span');
      span.className = 'mirror-data';
      span.textContent = ` ${this.stringify(item)}`;
      row.append(span);
    }

    return row;
  }

  private renderTable(args: unknown[]): HTMLElement {
    const wrapper = document.createElement('div');
    wrapper.className = 'mirror-table-block';
    wrapper.style.paddingLeft = `${this.depth * 16}px`;

    const rows = Array.isArray(args[0]) ? (args[0] as Record<string, unknown>[]) : [];
    if (rows.length === 0) {
      wrapper.textContent = '(空表)';
      return wrapper;
    }

    const columns = Array.isArray(args[1]) ? (args[1] as string[]) : Object.keys(rows[0] ?? {});

    const table = document.createElement('table');
    table.className = 'mirror-table';

    const head = document.createElement('thead');
    const headRow = document.createElement('tr');
    for (const column of columns) {
      const th = document.createElement('th');
      th.textContent = clean(column);
      headRow.append(th);
    }
    head.append(headRow);
    table.append(head);

    const body = document.createElement('tbody');
    for (const item of rows) {
      const tr = document.createElement('tr');
      for (const column of columns) {
        const td = document.createElement('td');
        td.textContent = clean(this.stringify(item[column]));
        tr.append(td);
      }
      body.append(tr);
    }
    table.append(body);

    wrapper.append(table);
    return wrapper;
  }

  private stringify(value: unknown): string {
    if (typeof value === 'string') return clean(value);
    if (value === null) return 'null';
    if (value === undefined) return 'undefined';
    if (typeof value === 'object') {
      try {
        return JSON.stringify(value);
      } catch {
        return String(value);
      }
    }
    return String(value);
  }
}
