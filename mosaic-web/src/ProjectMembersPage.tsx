import { useEffect, useMemo, useState } from 'react';
import { PlusOutlined } from '@ant-design/icons';
import { App, Button, Card, Empty, Form, Modal, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OperationsApi, type ProjectMember, type ProjectRole, type UserSummary } from './operationsApi';

const roles: { value: ProjectRole; label: string }[] = [
  { value: 'PROJECT_ADMIN', label: '项目管理员' },
  { value: 'DESIGNER', label: '设计人员' },
  { value: 'REVIEWER', label: '审核人员' },
  { value: 'VIEWER', label: '只读成员' },
];

export function ProjectMembersPage({ api, projectId }: { api: OperationsApi; projectId: string }) {
  const { message } = App.useApp();
  const [members, setMembers] = useState<ProjectMember[]>([]);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<{ userId: string; role: ProjectRole }>();

  const load = () => {
    if (!projectId) return;
    setLoading(true);
    void Promise.all([api.projectMembers(projectId), api.users()]).then(([memberValues, userValues]) => {
      setMembers(memberValues); setUsers(userValues.filter((item) => item.status === 'ACTIVE'));
    }).catch((cause: unknown) => void message.error(messageOf(cause))).finally(() => setLoading(false));
  };
  useEffect(load, [api, message, projectId]);

  const memberIds = useMemo(() => new Set(members.map((item) => item.user.id)), [members]);
  const candidates = users.filter((item) => !memberIds.has(item.id));
  const save = async (values: { userId: string; role: ProjectRole }) => {
    setSaving(true);
    try {
      await api.putProjectMember(projectId, values.userId, values.role);
      setOpen(false); form.resetFields(); load(); void message.success('项目成员已添加');
    } catch (cause) { void message.error(messageOf(cause)); }
    finally { setSaving(false); }
  };
  const changeRole = async (member: ProjectMember, role: ProjectRole) => {
    try {
      const changed = await api.putProjectMember(projectId, member.user.id, role);
      setMembers((current) => current.map((item) => item.user.id === member.user.id ? changed : item));
      void message.success('项目角色已更新');
    } catch (cause) { void message.error(messageOf(cause)); }
  };
  const remove = async (member: ProjectMember) => {
    try {
      await api.removeProjectMember(projectId, member.user.id);
      setMembers((current) => current.filter((item) => item.user.id !== member.user.id));
      void message.success('项目成员已移除');
    } catch (cause) { void message.error(messageOf(cause)); }
  };
  const columns: ColumnsType<ProjectMember> = [
    { title: '成员', render: (_, row) => <div className="table-primary"><strong>{row.user.displayName}</strong><small>@{row.user.username}</small></div> },
    { title: '全局角色', dataIndex: ['user', 'globalRole'], width: 120, render: (value) => <Tag color={value === 'ADMIN' ? 'gold' : 'blue'}>{value}</Tag> },
    { title: '项目角色', width: 180, render: (_, row) => <Select value={row.role} options={roles} style={{ width: 150 }} onChange={(value) => void changeRole(row, value)} /> },
    { title: '加入时间', dataIndex: 'createdAt', width: 190, render: formatTime },
    { title: '操作', width: 100, render: (_, row) => <Popconfirm title={`从项目移除 ${row.user.username}？`} onConfirm={() => void remove(row)}>
      <Button danger size="small">移除</Button>
    </Popconfirm> },
  ];

  if (!projectId) return <Card><Empty description="请先选择项目" /></Card>;
  return <Card title="项目成员与权限" extra={<Button type="primary" icon={<PlusOutlined />} disabled={!candidates.length} onClick={() => setOpen(true)}>添加成员</Button>}>
    <Typography.Paragraph type="secondary">项目角色控制照片审核、蒙版设计、布局编辑及项目管理权限；平台管理员不受项目角色限制。</Typography.Paragraph>
    <Table rowKey={(row) => row.user.id} loading={loading} columns={columns} dataSource={members} pagination={false} />
    <Modal title="添加项目成员" open={open} confirmLoading={saving} okText="添加" cancelText="取消"
      onOk={() => form.submit()} onCancel={() => setOpen(false)} destroyOnHidden>
      <Form form={form} layout="vertical" initialValues={{ role: 'VIEWER' }} onFinish={(values) => void save(values)}>
        <Form.Item name="userId" label="用户" rules={[{ required: true, message: '请选择用户' }]}>
          <Select showSearch optionFilterProp="label" options={candidates.map((item) => ({ value: item.id, label: `${item.displayName} (@${item.username})` }))} />
        </Form.Item>
        <Form.Item name="role" label="项目角色" rules={[{ required: true }]}><Select options={roles} /></Form.Item>
      </Form>
    </Modal>
  </Card>;
}

function formatTime(value: string) { return value ? new Date(value).toLocaleString('zh-CN') : '—'; }
function messageOf(cause: unknown) { return cause instanceof Error ? cause.message : '操作失败'; }
