// 歌词解析：支持 LRC 时间戳（[mm:ss]、[mm:ss.xx]、[mm:ss.xxx]）与纯文本
// 纯文本按行输出且不带时间，无法定位高亮行。

/** 歌词行 */
export interface PlayerLyricLine {
  timeMs: number;
  text: string;
}

/** LRC 时间戳正则 */
const LRC_PATTERN = /\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?\]/g;

/** 解析歌词文本为按时间升序的行；无时间戳时全部时间为 -1 */
export function parseLyric(text?: string | null): PlayerLyricLine[] {
  if (!text || !text.trim()) {
    return [];
  }
  const lines: PlayerLyricLine[] = [];
  text.split(/\r?\n/).forEach((raw) => {
    const line = raw.trim();
    if (!line) {
      return;
    }
    const stamps: number[] = [];
    let matched = false;
    let content = line;
    LRC_PATTERN.lastIndex = 0;
    let match = LRC_PATTERN.exec(line);
    while (match) {
      matched = true;
      const minutes = Number(match[1]);
      const seconds = Number(match[2]);
      const fraction = match[3] ? Number(`0.${match[3]}`) : 0;
      stamps.push(Math.round((minutes * 60 + seconds + fraction) * 1000));
      content = content.replace(match[0], '');
      match = LRC_PATTERN.exec(line);
    }
    const lyric = content.trim();
    if (!matched) {
      lines.push({ timeMs: -1, text: line });
      return;
    }
    stamps.forEach((timeMs) => lines.push({ timeMs, text: lyric }));
  });
  return lines.sort((a, b) => a.timeMs - b.timeMs);
}

/** 找到当前播放时间对应的歌词行索引（无时间戳或未开始时返回 -1） */
export function locateLyricLine(lines: PlayerLyricLine[], currentTimeMs: number): number {
  if (lines.length === 0 || lines[0].timeMs < 0) {
    return -1;
  }
  let active = -1;
  for (let index = 0; index < lines.length; index += 1) {
    if (lines[index].timeMs <= currentTimeMs) {
      active = index;
    } else {
      break;
    }
  }
  return active;
}
