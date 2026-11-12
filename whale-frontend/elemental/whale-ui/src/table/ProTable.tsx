// ProTable - 分页表格二次封装：查询区 + 工具栏 + 受控分页 + 命令式刷新

import { Card, Space, Table, type TableProps } from 'antd';
import {
  useCallback,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
  type ReactNode,
  type Ref,
} from 'react';

/** 分页请求参数 */
export interface PageParams {
  current: number;
  size: number;
}

/** 后端统一分页响应体（与 whale-common PageResult 字段一致）
 *  total/size/current 后端是 Long → 全局序列化为字符串（运行时真实类型），
 *  ProTable 内部统一 Number() 强转后维护分页状态。 */
export interface PageResult<T> {
  records: T[];
  total: string;
  size: string;
  current: string;
}

/** 命令式句柄：reload 保持当前页，reset 回到第一页 */
export interface ProTableRef {
  reload: () => void;
  reset: () => void;
}

export interface ProTableProps<T extends object, Q extends object = Record<string, never>> {
  ref?: Ref<ProTableRef>;
  /** 行主键字段名（默认 id 场景下仍建议显式传入） */
  rowKey: string;
  columns: TableProps<T>['columns'];
  /** 数据请求：入参为 { ...params, current, size } */
  request: (params: Q & PageParams) => Promise<PageResult<T>>;
  /** 附加查询条件（对象引用变化会触发重新查询） */
  params?: Q;
  /** 查询区（通常为 SearchForm） */
  search?: ReactNode;
  /** 卡片右上角工具栏（通常为新增等操作按钮） */
  toolbar?: ReactNode;
  title?: ReactNode;
  defaultPageSize?: number;
  scroll?: TableProps<T>['scroll'];
  bordered?: boolean;
  /** 数据请求失败回调；未提供时静默忽略，避免产生未处理的 Promise 拒绝 */
  onError?: (error: unknown) => void;
}

export function ProTable<T extends object, Q extends object = Record<string, never>>({
  ref,
  rowKey,
  columns,
  request,
  params,
  search,
  toolbar,
  title,
  defaultPageSize = 10,
  scroll,
  bordered,
  onError,
}: ProTableProps<T, Q>) {
  const [records, setRecords] = useState<T[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(defaultPageSize);
  const [loading, setLoading] = useState(false);

  // request / params 通过 ref 透传，避免父组件每次渲染重建函数导致重复请求
  const requestRef = useRef(request);
  requestRef.current = request;
  const paramsRef = useRef(params);
  paramsRef.current = params;
  const onErrorRef = useRef(onError);
  onErrorRef.current = onError;

  const load = useCallback(async (page: number, pageSize: number) => {
    setLoading(true);
    try {
      const query = { ...(paramsRef.current ?? {}), current: page, size: pageSize } as Q &
        PageParams;
      const result = await requestRef.current(query);
      setRecords(result?.records ?? []);
      // 后端 PageResult 的 total/current/size 是 Long→String 序列化（运行时为字符串），
      // 此处统一强转 number，保证分页状态始终是数值（一处收口，所有列表页受益）
      setTotal(Number(result?.total ?? 0));
      setCurrent(Number(result?.current ?? page));
      setSize(Number(result?.size ?? pageSize));
    } catch (error) {
      onErrorRef.current?.(error);
    } finally {
      setLoading(false);
    }
  }, []);

  const paramsKey = JSON.stringify(params ?? {});

  useEffect(() => {
    void load(1, defaultPageSize);
  }, [load, defaultPageSize, paramsKey]);

  useImperativeHandle(
    ref,
    () => ({
      reload: () => {
        void load(current, size);
      },
      reset: () => {
        void load(1, size);
      },
    }),
    [load, current, size],
  );

  return (
    <>
      {search}
      <Card
        title={title}
        extra={toolbar ? <Space>{toolbar}</Space> : undefined}
        styles={{ body: { paddingTop: 16 } }}
      >
        <Table<T>
          rowKey={rowKey}
          columns={columns}
          dataSource={records}
          loading={loading}
          bordered={bordered}
          scroll={scroll}
          pagination={{
            current,
            pageSize: size,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
          }}
          onChange={(pagination) => {
            const nextCurrent = pagination.current ?? 1;
            const nextSize = pagination.pageSize ?? size;
            void load(nextCurrent, nextSize);
          }}
        />
      </Card>
    </>
  );
}
