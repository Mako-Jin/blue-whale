import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button, Result } from 'antd';

/**
 * 全局错误边界（P0-2 修复）。
 *
 * <p>捕获子树渲染期异常，展示降级 UI，避免整页白屏。
 * 所有应用入口（含 qiankun 主/子应用 main.tsx 的 App 外层）都应包裹本组件。</p>
 *
 * <p>使用方式：</p>
 * <pre>
 *   &lt;ErrorBoundary&gt;
 *     &lt;App /&gt;
 *   &lt;/ErrorBoundary&gt;
 * </pre>
 *
 * <p>可选传入 {@code fallback} 自定义降级 UI，或 {@code onError} 上报错误
 * （如接入 whale-log-sdk 审计通道）。未传 fallback 时使用内置 antd Result 降级页，
 * 提供「重新加载」按钮（整页刷新）。</p>
 */
export interface ErrorBoundaryProps {
  /** 受保护的业务子树 */
  children: ReactNode;
  /** 自定义降级 UI；不传时使用内置 antd Result */
  fallback?: ReactNode;
  /** 错误上报回调（componentDidCatch 触发，生产接入审计/监控用） */
  onError?: (error: Error, info: ErrorInfo) => void;
  /** 内置降级页重试按钮文案 */
  resetText?: string;
}

interface ErrorBoundaryState {
  error: Error | null;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // 组件级错误必须留痕：audit SDK 的 window.onerror 不覆盖 React 渲染异常
    console.error('[ErrorBoundary] 渲染异常已捕获:', error, info.componentStack);
    this.props.onError?.(error, info);
  }

  private handleReset = () => {
    // 整页刷新是唯一可靠的恢复手段：React 状态树已不可信
    window.location.reload();
  };

  render() {
    const { error } = this.state;
    if (error) {
      if (this.props.fallback) {
        return this.props.fallback;
      }
      return (
        <Result
          status="error"
          title="页面出现异常"
          subTitle={error.message || '渲染过程中发生未知错误，请重试'}
          extra={
            <Button type="primary" onClick={this.handleReset}>
              {this.props.resetText ?? '重新加载'}
            </Button>
          }
        />
      );
    }
    return this.props.children;
  }
}

export default ErrorBoundary;
