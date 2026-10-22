// 画布历史栈：当前模型 + 撤销栈 + 重做栈，commit 记录一次变更，reset 丢弃历史（加载 / 新建）。
// Ctrl/Cmd+Z 撤销、Ctrl/Cmd+Y 或 Ctrl/Cmd+Shift+Z 重做（输入框内不劫持）。

import { useEffect, useReducer } from 'react';
import type { CanvasModel } from '../model';

/** 空画布模型（新建定义 / 加载失败兜底） */
export const EMPTY_MODEL: CanvasModel = { nodes: [], flows: [], groups: [] };

/** 撤销栈上限（避免长会话内存膨胀） */
const MAX_HISTORY = 50;

/** 画布历史状态：当前模型 + 撤销栈 + 重做栈 */
interface HistoryState {
  model: CanvasModel;
  past: CanvasModel[];
  future: CanvasModel[];
}

/** 历史动作：commit 记录一次变更，reset 丢弃历史（加载 / 新建） */
type HistoryAction =
  | { type: 'commit'; model: CanvasModel }
  | { type: 'reset'; model: CanvasModel }
  | { type: 'undo' }
  | { type: 'redo' };

/** 历史栈 reducer：commit 清空重做栈，undo / redo 在两栈间搬运当前模型 */
function historyReducer(state: HistoryState, action: HistoryAction): HistoryState {
  switch (action.type) {
    case 'commit':
      if (action.model === state.model) {
        return state;
      }
      return {
        model: action.model,
        past: [...state.past, state.model].slice(-MAX_HISTORY),
        future: [],
      };
    case 'reset':
      return { model: action.model, past: [], future: [] };
    case 'undo': {
      const previous = state.past[state.past.length - 1];
      if (!previous) {
        return state;
      }
      return {
        model: previous,
        past: state.past.slice(0, -1),
        future: [state.model, ...state.future].slice(0, MAX_HISTORY),
      };
    }
    case 'redo': {
      const next = state.future[0];
      if (!next) {
        return state;
      }
      return {
        model: next,
        past: [...state.past, state.model].slice(-MAX_HISTORY),
        future: state.future.slice(1),
      };
    }
    default:
      return state;
  }
}

export interface HistoryController {
  model: CanvasModel;
  past: CanvasModel[];
  future: CanvasModel[];
  /** 提交一次画布变更（进入撤销栈并清空重做栈） */
  commit: (updater: (prev: CanvasModel) => CanvasModel) => void;
  /** 丢弃历史并替换当前模型（加载 / 新建） */
  reset: (model: CanvasModel) => void;
  undo: () => void;
  redo: () => void;
}

export function useHistory(): HistoryController {
  const [history, dispatch] = useReducer(historyReducer, {
    model: EMPTY_MODEL,
    past: [],
    future: [],
  });

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const tag = target?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || target?.isContentEditable) {
        return;
      }
      if (!event.ctrlKey && !event.metaKey) {
        return;
      }
      const key = event.key.toLowerCase();
      if (key === 'z') {
        event.preventDefault();
        dispatch({ type: event.shiftKey ? 'redo' : 'undo' });
      } else if (key === 'y') {
        event.preventDefault();
        dispatch({ type: 'redo' });
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  return {
    model: history.model,
    past: history.past,
    future: history.future,
    commit: (updater) => dispatch({ type: 'commit', model: updater(history.model) }),
    reset: (model) => dispatch({ type: 'reset', model }),
    undo: () => dispatch({ type: 'undo' }),
    redo: () => dispatch({ type: 'redo' }),
  };
}
