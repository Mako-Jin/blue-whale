// 画布字段历史栈（R4）：撤销 / 重做 + 快捷键。
// past / future 存于 useRef，入栈出栈不触发渲染；historyVersion 为变更计数，
// 仅用于驱动工具栏按钮禁用态重渲染（栈内容 React 无法感知）；
// fieldsRef 与画布状态同步维护「当前值」，供 commit / 撤销 / 重做在同步上下文中读取快照。
// 从 FormDesigner 拆出的状态 Hook：仅承载字段历史，画布交互仍由容器组件负责。

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { DesignerField } from './types';

/** 撤销 / 重做历史栈深度上限（超出丢弃最旧快照，避免长时间编辑内存膨胀） */
const HISTORY_LIMIT = 50;

/** 画布字段历史栈（容器组件与工具栏共享） */
export interface FieldHistoryApi {
  fields: DesignerField[];
  /** 载入服务端表单（首次 / 保存后回填 / 切换表单）或切换表定义清空：以结果作为新历史基点 */
  resetFields: (initial: DesignerField[]) => void;
  /** 画布字段变更统一入口（无实际变化时不入栈） */
  commit: (next: DesignerField[] | ((prev: DesignerField[]) => DesignerField[])) => void;
  undo: () => void;
  redo: () => void;
  canUndo: boolean;
  canRedo: boolean;
}

export function useFieldHistory(): FieldHistoryApi {
  const [fields, setFields] = useState<DesignerField[]>([]);
  const [historyVersion, setHistoryVersion] = useState(0);
  const pastRef = useRef<DesignerField[][]>([]);
  const futureRef = useRef<DesignerField[][]>([]);
  const fieldsRef = useRef<DesignerField[]>([]);

  /** 载入服务端表单（首次 / 保存后回填 / 切换表单）或切换表定义清空：以结果作为新历史基点，不入栈且清空 past / future */
  const resetFields = useCallback((initial: DesignerField[]) => {
    fieldsRef.current = initial;
    pastRef.current = [];
    futureRef.current = [];
    setFields(initial);
    setHistoryVersion((version) => version + 1);
  }, []);

  /** 画布字段变更统一入口：先把当前值快照压入 past、清空 future，再落地新值（无实际变化时不入栈） */
  const commit = useCallback(
    (next: DesignerField[] | ((prev: DesignerField[]) => DesignerField[])) => {
      const previous = fieldsRef.current;
      const resolved = typeof next === 'function' ? next(previous) : next;
      if (resolved === previous) {
        return;
      }
      pastRef.current = [...pastRef.current, previous].slice(-HISTORY_LIMIT);
      futureRef.current = [];
      fieldsRef.current = resolved;
      setFields(resolved);
      setHistoryVersion((version) => version + 1);
    },
    [],
  );

  /** 撤销：past 弹栈 → 当前值压入 future → 设为弹栈值 */
  const undo = useCallback(() => {
    const past = pastRef.current;
    if (past.length === 0) {
      return;
    }
    const previous = past[past.length - 1];
    pastRef.current = past.slice(0, -1);
    futureRef.current = [...futureRef.current, fieldsRef.current].slice(-HISTORY_LIMIT);
    fieldsRef.current = previous;
    setFields(previous);
    setHistoryVersion((version) => version + 1);
  }, []);

  /** 重做：future 弹栈 → 当前值压入 past → 设为弹栈值 */
  const redo = useCallback(() => {
    const future = futureRef.current;
    if (future.length === 0) {
      return;
    }
    const next = future[future.length - 1];
    futureRef.current = future.slice(0, -1);
    pastRef.current = [...pastRef.current, fieldsRef.current].slice(-HISTORY_LIMIT);
    fieldsRef.current = next;
    setFields(next);
    setHistoryVersion((version) => version + 1);
  }, []);

  // 以 historyVersion 为依赖，在栈增减后重新计算按钮禁用态
  // eslint-disable-next-line react-hooks/exhaustive-deps -- historyVersion 仅用于触发重算，回调内不引用
  const canUndo = useMemo(() => pastRef.current.length > 0, [historyVersion]);
  // eslint-disable-next-line react-hooks/exhaustive-deps -- 同上
  const canRedo = useMemo(() => futureRef.current.length > 0, [historyVersion]);

  // 快捷键（Windows 口径）：Ctrl+Z 撤销，Ctrl+Y / Ctrl+Shift+Z 重做；
  // 事件源为输入控件（顶栏输入框、属性面板等）时直接放行，不劫持原生编辑行为。
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (!event.ctrlKey) {
        return;
      }
      const target = event.target as HTMLElement | null;
      if (
        target !== null &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.tagName === 'SELECT' ||
          target.isContentEditable)
      ) {
        return;
      }
      const key = event.key.toLowerCase();
      if (key === 'z' && !event.shiftKey) {
        event.preventDefault();
        undo();
        return;
      }
      if (key === 'y' || (key === 'z' && event.shiftKey)) {
        event.preventDefault();
        redo();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [undo, redo]);

  return { fields, resetFields, commit, undo, redo, canUndo, canRedo };
}
