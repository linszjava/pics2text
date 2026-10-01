import { useState } from 'react';
import { PlusOutlined } from '@ant-design/icons';
import { App, Button, Card, Form, Input, Modal, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OperationsApi, type ProjectSummary } from './operationsApi';

export function ProjectManagement({ api, projects, loading, onChanged, onOpen }:
  { api: OperationsApi; projects: ProjectSummary[]; loading: boolean;
    onChanged: (projects: ProjectSummary[]) => void; onOpen: (projectId: string) => void }) {
  const { message } = App.useApp();
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<{ name: string }>();

  const create = async ({ name }: { name: string }) => {
    setSaving(true);
    try {
      const project = await api.createProject(name.trim());
      onChanged([...projects, project]); setOpen(false); form.resetFields();
      void message.success('项目创建成功');
    } catch (cause) { void message.error(messageOf(cause)); }
    finally { setSaving(false); }
  };
  const toggleArchive = async (project: ProjectSummary) => {
    try {
      const changed = await api.setProjectArchived(project.id, project.status !== 'ARCHIVED');
      onChanged(projects.map((item) => item.id === changed.id ? changed : item));
      void message.success(changed.status === 'ARCHIVED' ? '项目已归档' : '项目已恢复');
    } catch (cause) { void message.error(messageOf(cause)); }
  };
  const columns: ColumnsType<ProjectSummary> = [
    { title: '项目名称', dataIndex: 'name', render: (value, row) => <Button type="link" onClick={() => onOpen(row.id)}>{value}</Button> },
    { title: '状态', dataIndex: 'status', width: 110, render: (value) => <Tag color={value === 'ACTIVE' ? 'success' : 'default'}>{value}</Tag> },
    { title: '说明', dataIndex: 'description', ellipsis: true, render: (value) => value || '—' },
    { title: '更新时间', dataIndex: 'updatedAt', width: 190, render: formatTime },
    { title: '操作', width: 150, render: (_, row) => <Popconfirm
      title={row.status === 'ARCHIVED' ? '恢复这个项目？' : '归档这个项目？'}
      onConfirm={() => void toggleArchive(row)}>
      <Button size="small">{row.status === 'ARCHIVED' ? '恢复' : '归档'}</Button>
    </Popconfirm> },
  ];

  return <Card title="项目管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>新建项目</Button>}>
    <Typography.Paragraph type="secondary">创建、归档并进入项目工作区。项目成员与详细配置将在后续子阶段接入。</Typography.Paragraph>
    <Table rowKey="id" loading={loading} columns={columns} dataSource={projects}
      pagination={{ pageSize: 10, hideOnSinglePage: true }} locale={{ emptyText: '暂无项目' }} />
    <Modal title="新建项目" open={open} confirmLoading={saving} okText="创建" cancelText="取消"
      onOk={() => form.submit()} onCancel={() => setOpen(false)} destroyOnHidden>
      <Form form={form} layout="vertical" onFinish={(values) => void create(values)}>
        <Form.Item label="项目名称" name="name" rules={[{ required: true, message: '请输入项目名称' }, { max: 120 }]}>
          <Input autoFocus placeholder="例如：中国人能飞 · 火箭箭体" />
        </Form.Item>
      </Form>
    </Modal>
  </Card>;
}

function formatTime(value: string) { return value ? new Date(value).toLocaleString('zh-CN') : '—'; }
function messageOf(cause: unknown) { return cause instanceof Error ? cause.message : '操作失败'; }
