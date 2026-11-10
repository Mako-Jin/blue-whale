// 自由画布流程设计器（F1）拼装页：调色板 + SVG 画布 + 属性面板，
// 顶栏提供 保存 / 校验 / 模拟 / 导入 BPMN / 导出 BPMN / 返回列表。
// 路由带 :id → 加载既有定义（GET → 解析 definitionJson）；无 :id → 新建（需填 code/name）。
// 校验按 errors[].refKey 高亮节点 / 连线，并以内联 Alert 列明 message / warnings。
// 模拟按流程变量真实求值选路，回放节点路径（不创建实例）。
//
// 编排职责在此；画布状态 / 历史栈见 store/，弹窗与顶栏等 UI 见 panels/。

import { Card, Spin } from 'antd';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useCurrentUser } from '../../api/token';
import Canvas from './Canvas';
import Palette from './Palette';
import PropertyPanel from './PropertyPanel';
import BpmnImportModal from './panels/BpmnImportModal';
import DefinitionMeta from './panels/DefinitionMeta';
import DesignerToolbar from './panels/DesignerToolbar';
import GroupModal from './panels/GroupModal';
import SimulateModal from './panels/SimulateModal';
import ValidationAlerts from './panels/ValidationAlerts';
import VersionDrawer from './panels/VersionDrawer';
import { useCanvasEditor } from './store/useCanvasEditor';
import { useDefinitionSession } from './store/useDefinitionSession';
import { useHistory } from './store/useHistory';

export default function WorkflowDesigner() {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const user = useCurrentUser();
  // 路由参数即雪花 ID 字符串，禁止 Number() 转换（>2^53 丢精度）
  const definitionId = id ?? null;

  // 画布模型走历史栈：每次结构变更 commit 一次，撤销 / 重做在两栈间搬运
  const history = useHistory();
  const editor = useCanvasEditor(history.model, history.commit, user?.username ?? '');
  const session = useDefinitionSession({
    definitionId,
    model: history.model,
    resetModel: history.reset,
    clearSelection: editor.onClearSelection,
    onCreated: (createdId) => navigate(`/design/${createdId}`, { replace: true }),
  });

  const [simulateOpen, setSimulateOpen] = useState(false);
  const [bpmnOpen, setBpmnOpen] = useState(false);
  const [groupOpen, setGroupOpen] = useState(false);
  const [versionOpen, setVersionOpen] = useState(false);

  return (
    <Card
      title="流程设计器"
      styles={{ body: { padding: 12 } }}
      extra={
        <DesignerToolbar
          canUndo={history.past.length > 0}
          canRedo={history.future.length > 0}
          saving={session.saving}
          validating={session.validating}
          saved={definitionId !== null}
          onUndo={history.undo}
          onRedo={history.redo}
          onSave={() => void session.handleSave()}
          onValidate={() => void session.handleValidate()}
          onOpenGroup={() => setGroupOpen(true)}
          onOpenVersions={() => setVersionOpen(true)}
          onOpenSimulate={() => setSimulateOpen(true)}
          onOpenBpmnImport={() => setBpmnOpen(true)}
          onExportBpmn={() => void session.handleExportBpmn()}
          onBack={() => navigate('/definitions')}
        />
      }
    >
      <Spin spinning={session.loading}>
        <DefinitionMeta
          code={session.code}
          name={session.name}
          onCodeChange={session.setCode}
          onNameChange={session.setName}
          definitionId={definitionId}
        />

        <ValidationAlerts validation={session.validation} />

        <div style={{ display: 'flex', gap: 12, height: 560 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <Canvas
              palette={<Palette />}
              model={history.model}
              selected={editor.selected}
              errorKeys={session.errorKeys}
              linkFrom={editor.linkFrom}
              onSelectNode={editor.onSelectNode}
              onSelectFlow={editor.onSelectFlow}
              onClearSelection={editor.onClearSelection}
              onMoveNode={editor.onMoveNode}
              onDropNode={editor.onDropNode}
            />
          </div>
          <div
            style={{
              flex: '0 0 300px',
              width: 300,
              overflow: 'auto',
              paddingLeft: 8,
              borderLeft: '1px solid #f0f0f0',
            }}
          >
            <PropertyPanel
              model={history.model}
              selection={editor.selected}
              linkFrom={editor.linkFrom}
              onChangeNode={editor.onChangeNode}
              onRenameNode={editor.onRenameNode}
              onDeleteNode={editor.onDeleteNode}
              onChangeFlow={editor.onChangeFlow}
              onDeleteFlow={editor.onDeleteFlow}
              onStartLink={editor.setLinkFrom}
              onCancelLink={() => editor.setLinkFrom(null)}
            />
          </div>
        </div>
      </Spin>

      <SimulateModal
        open={simulateOpen}
        model={history.model}
        onClose={() => setSimulateOpen(false)}
        onLocate={editor.locateNode}
      />

      <BpmnImportModal
        open={bpmnOpen}
        defaultCode={session.code}
        defaultName={session.name}
        onClose={() => setBpmnOpen(false)}
        onImported={(createdId) => navigate(`/design/${createdId}`, { replace: true })}
      />

      <GroupModal
        open={groupOpen}
        model={history.model}
        onClose={() => setGroupOpen(false)}
        onCreate={editor.onCreateGroup}
        onRename={editor.onRenameGroup}
        onDelete={editor.onDeleteGroup}
      />

      <VersionDrawer
        open={versionOpen}
        definitionId={definitionId}
        onClose={() => setVersionOpen(false)}
        onPublished={session.applyDefinition}
      />
    </Card>
  );
}
