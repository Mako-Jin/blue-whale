// 流程定义管理页（F2-a）：分页列表 + 按 code/name 过滤 + 新建/编辑/删除 +
// 校验结果展示 + 跳设计器/发起 + BPMN 导入导出。
// 契约层 ../../api/workflow 为冻结实现，本页只读不改；Id 一律按 string 处理（禁止 Number(id)）。
//
// 编排职责在此；列表数据与动作、列表区、弹窗区见同目录下的 hook 与组件。

import DefinitionDialogs from './DefinitionDialogs';
import DefinitionTable from './DefinitionTable';
import { useDefinitionList } from './useDefinitionList';

export default function DefinitionList() {
  const controller = useDefinitionList();

  return (
    <>
      <DefinitionTable controller={controller} />
      <DefinitionDialogs controller={controller} />
    </>
  );
}
