import { useEffect, useState } from 'react';
import { App, Card, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OperationsApi, type AuditSummary } from './operationsApi';

export function AuditLogPage({ api }: { api: OperationsApi }) {
  const { message } = App.useApp();
  const [logs, setLogs] = useState<AuditSummary[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => { void api.auditLogs().then(setLogs)
    .catch((cause: unknown) => void message.error(cause instanceof Error ? cause.message : '审计日志加载失败'))
    .finally(() => setLoading(false)); }, [api, message]);
  const columns: ColumnsType<AuditSummary> = [
    { title: '时间', dataIndex: 'createdAt', width: 190, render: (value) => new Date(value).toLocaleString('zh-CN') },
    { title: '动作', dataIndex: 'action', width: 190 },
    { title: '结果', dataIndex: 'success', width: 90, render: (value) => <Tag color={value ? 'success' : 'error'}>{value ? '成功' : '失败'}</Tag> },
    { title: '目标', render: (_, row) => `${row.targetType}${row.targetId ? ` · ${row.targetId}` : ''}` },
    { title: '操作者', dataIndex: 'actorUserId', ellipsis: true, render: (value) => value || '匿名/系统' },
    { title: '请求 ID', dataIndex: 'requestId', ellipsis: true, render: (value) => value || '—' },
  ];
  return <Card title="安全审计日志">
    <Typography.Paragraph type="secondary">显示最新身份认证、权限管理和关键业务操作记录。</Typography.Paragraph>
    <Table rowKey="id" loading={loading} columns={columns} dataSource={logs}
      pagination={{ pageSize: 15, showSizeChanger: false }} />
  </Card>;
}
