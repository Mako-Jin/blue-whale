// 校验结果内联提示：错误（红）与警告（黄）各一条 Alert，列出 code / message / refKey。

import { Alert } from 'antd';
import type { ProcessDefinitionValidate, ValidationIssue } from '../../../api/workflow';

/** 校验问题列表（错误与警告共用） */
function IssueList({ issues }: { issues: ValidationIssue[] }) {
  return (
    <ul style={{ margin: 0, paddingLeft: 18 }}>
      {issues.map((issue, index) => (
        <li key={`${issue.code}-${index}`}>
          {issue.code}：{issue.message}
          {issue.refKey ? `（${issue.refKey}）` : ''}
        </li>
      ))}
    </ul>
  );
}

interface ValidationAlertsProps {
  validation: ProcessDefinitionValidate | null;
}

export default function ValidationAlerts({ validation }: ValidationAlertsProps) {
  return (
    <>
      {validation && validation.errors.length > 0 ? (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 8 }}
          message={`校验未通过（${validation.errors.length} 个错误）`}
          description={<IssueList issues={validation.errors} />}
        />
      ) : null}

      {validation && validation.warnings.length > 0 ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 8 }}
          message={`警告（${validation.warnings.length} 条）`}
          description={<IssueList issues={validation.warnings} />}
        />
      ) : null}
    </>
  );
}
