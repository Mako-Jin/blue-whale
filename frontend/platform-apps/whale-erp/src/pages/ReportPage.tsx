// ERP 财务报表页：明细账 / 科目余额表 / 资产负债表 / 利润表
import { useCallback, useEffect, useState } from 'react';
import { Button, Input, Select, Space, Table, Tabs, Tag } from 'antd';
import type { TableProps, TabsProps } from 'antd';
import {
  getAccountBalances,
  getBalanceSheet,
  getIncomeStatement,
  getLedger,
  type AccountBalance,
  type FinancialReport,
  type Ledger,
  type LedgerLine,
  type ReportRow,
} from '../api/finance';
import { pageAccounts, type ErpAccount } from '../api/masterdata';

/** 默认期间（当前年月 yyyyMM） */
function currentPeriod(): string {
  return new Date().toISOString().slice(0, 7).replace('-', '');
}

/** 金额格式化 */
function money(value: number | undefined): string {
  return Number(value ?? 0).toFixed(2);
}

/** 科目类型文案 */
const ACCOUNT_TYPE_LABEL: Record<string, string> = {
  ASSET: '资产',
  LIABILITY: '负债',
  EQUITY: '权益',
  REVENUE: '收入',
  EXPENSE: '费用',
};

/** 明细账页签 */
function LedgerTab() {
  const [accounts, setAccounts] = useState<ErpAccount[]>([]);
  const [accountCode, setAccountCode] = useState<string>();
  const [fromPeriod, setFromPeriod] = useState(currentPeriod());
  const [toPeriod, setToPeriod] = useState(currentPeriod());
  const [ledger, setLedger] = useState<Ledger | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    void pageAccounts({ size: 200 }).then((page) => setAccounts(page.records));
  }, []);

  const query = async () => {
    if (!accountCode) return;
    setLoading(true);
    try {
      setLedger(await getLedger(accountCode, fromPeriod, toPeriod));
    } finally {
      setLoading(false);
    }
  };

  const columns: TableProps<LedgerLine>['columns'] = [
    { title: '凭证号', dataIndex: 'voucherNo', key: 'voucherNo', width: 150 },
    { title: '日期', dataIndex: 'voucherDate', key: 'voucherDate', width: 110 },
    { title: '摘要', dataIndex: 'summary', key: 'summary' },
    {
      title: '借方',
      dataIndex: 'debitAmount',
      key: 'debitAmount',
      width: 130,
      render: money,
    },
    {
      title: '贷方',
      dataIndex: 'creditAmount',
      key: 'creditAmount',
      width: 130,
      render: money,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Select
          showSearch
          optionFilterProp="label"
          placeholder="选择科目"
          style={{ width: 240 }}
          value={accountCode}
          onChange={(value) => setAccountCode(value)}
          options={accounts.map((item) => ({
            value: item.code,
            label: `${item.code} ${item.name}`,
          }))}
        />
        <Input
          value={fromPeriod}
          onChange={(event) => setFromPeriod(event.target.value)}
          style={{ width: 110 }}
          placeholder="起始期间"
        />
        <Input
          value={toPeriod}
          onChange={(event) => setToPeriod(event.target.value)}
          style={{ width: 110 }}
          placeholder="结束期间"
        />
        <Button type="primary" onClick={query} loading={loading}>
          查询
        </Button>
      </Space>
      {ledger ? (
        <Space size="large">
          <span>科目：{ledger.accountCode} {ledger.accountName}</span>
          <span>期初：{money(ledger.openingBalance)}</span>
          <span>本期借方：{money(ledger.debitTotal)}</span>
          <span>本期贷方：{money(ledger.creditTotal)}</span>
          <span>期末：{money(ledger.closingBalance)}</span>
        </Space>
      ) : null}
      <Table<LedgerLine>
        rowKey={(record) => `${record.voucherNo}-${record.lineNo}`}
        loading={loading}
        columns={columns}
        dataSource={ledger?.lines ?? []}
      />
    </Space>
  );
}

/** 科目余额表页签 */
function AccountBalanceTab() {
  const [period, setPeriod] = useState(currentPeriod());
  const [data, setData] = useState<AccountBalance[]>([]);
  const [loading, setLoading] = useState(false);

  const query = async () => {
    setLoading(true);
    try {
      setData(await getAccountBalances(period));
    } finally {
      setLoading(false);
    }
  };

  const columns: TableProps<AccountBalance>['columns'] = [
    { title: '科目编码', dataIndex: 'accountCode', key: 'accountCode', width: 110 },
    { title: '科目名称', dataIndex: 'accountName', key: 'accountName' },
    {
      title: '类型',
      dataIndex: 'accountType',
      key: 'accountType',
      width: 90,
      render: (value) => ACCOUNT_TYPE_LABEL[value as string] ?? value,
    },
    { title: '本期借方', dataIndex: 'debitTotal', key: 'debitTotal', width: 130, render: money },
    { title: '本期贷方', dataIndex: 'creditTotal', key: 'creditTotal', width: 130, render: money },
    { title: '余额', dataIndex: 'balance', key: 'balance', width: 140, render: money },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space>
        <Input
          value={period}
          onChange={(event) => setPeriod(event.target.value)}
          style={{ width: 120 }}
          placeholder="期间 yyyyMM"
        />
        <Button type="primary" onClick={query} loading={loading}>
          查询
        </Button>
      </Space>
      <Table<AccountBalance>
        rowKey="accountCode"
        loading={loading}
        columns={columns}
        dataSource={data}
      />
    </Space>
  );
}

/** 报表行表格列 */
const REPORT_COLUMNS: TableProps<ReportRow>['columns'] = [
  { title: '科目编码', dataIndex: 'accountCode', key: 'accountCode', width: 130 },
  { title: '项目', dataIndex: 'accountName', key: 'accountName' },
  {
    title: '类型',
    dataIndex: 'accountType',
    key: 'accountType',
    width: 90,
    render: (value) => ACCOUNT_TYPE_LABEL[value as string] ?? value,
  },
  { title: '金额', dataIndex: 'amount', key: 'amount', width: 160, render: money },
];

/** 资产负债表 / 利润表页签 */
function ReportTab({ kind }: { kind: 'BALANCE_SHEET' | 'INCOME_STATEMENT' }) {
  const [period, setPeriod] = useState(currentPeriod());
  const [report, setReport] = useState<FinancialReport | null>(null);
  const [loading, setLoading] = useState(false);

  const query = useCallback(async () => {
    setLoading(true);
    try {
      setReport(
        kind === 'BALANCE_SHEET' ? await getBalanceSheet(period) : await getIncomeStatement(period),
      );
    } finally {
      setLoading(false);
    }
  }, [kind, period]);

  const summary =
    kind === 'BALANCE_SHEET'
      ? [
          { label: '资产合计', value: report?.totalAssets },
          { label: '负债合计', value: report?.totalLiabilities },
          { label: '权益合计', value: report?.totalEquity },
        ]
      : [
          { label: '收入合计', value: report?.totalRevenue },
          { label: '费用合计', value: report?.totalExpenses },
          { label: '净利润', value: report?.netProfit },
        ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space>
        <Input
          value={period}
          onChange={(event) => setPeriod(event.target.value)}
          style={{ width: 120 }}
          placeholder="期间 yyyyMM"
        />
        <Button type="primary" onClick={query} loading={loading}>
          查询
        </Button>
        {report ? <Tag color="blue">{report.periodCode}</Tag> : null}
      </Space>
      {report ? (
        <Space size="large">
          {summary.map((item) => (
            <span key={item.label}>
              {item.label}：{money(item.value)}
            </span>
          ))}
        </Space>
      ) : null}
      <Table<ReportRow>
        rowKey="accountCode"
        loading={loading}
        columns={REPORT_COLUMNS}
        dataSource={report?.rows ?? []}
      />
    </Space>
  );
}

/** 财务报表页 */
export default function ReportPage() {
  const items: TabsProps['items'] = [
    { key: 'ledger', label: '明细账', children: <LedgerTab /> },
    { key: 'balance', label: '科目余额表', children: <AccountBalanceTab /> },
    {
      key: 'balance-sheet',
      label: '资产负债表',
      children: <ReportTab kind="BALANCE_SHEET" />,
    },
    {
      key: 'income',
      label: '利润表',
      children: <ReportTab kind="INCOME_STATEMENT" />,
    },
  ];

  return <Tabs items={items} />;
}
