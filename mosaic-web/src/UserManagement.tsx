import { useEffect, useState } from 'react';
import { PlusOutlined } from '@ant-design/icons';
import { App, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OperationsApi, type UserSummary } from './operationsApi';

type CreateUserValues = { username: string; password: string; displayName: string; globalRole: 'ADMIN' | 'USER' };

export function UserManagement({ api, currentUserId }: { api: OperationsApi; currentUserId: string }) {
  const { message } = App.useApp();
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<CreateUserValues>();
  const load = () => { setLoading(true); void api.users().then(setUsers)
    .catch((cause: unknown) => void message.error(messageOf(cause))).finally(() => setLoading(false)); };
  useEffect(load, [api, message]);

  const create = async (values: CreateUserValues) => {
    setSaving(true);
    try { await api.createUser(values); setOpen(false); form.resetFields(); load(); void message.success('用户创建成功'); }
    catch (cause) { void message.error(messageOf(cause)); }
    finally { setSaving(false); }
  };
  const toggleStatus = async (user: UserSummary) => {
    try {
      const changed = await api.changeUserStatus(user.id, user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE');
      setUsers((current) => current.map((item) => item.id === changed.id ? changed : item));
      void message.success(changed.status === 'ACTIVE' ? '用户已启用' : '用户已停用');
    } catch (cause) { void message.error(messageOf(cause)); }
  };
  const kick = async (user: UserSummary) => {
    try { await api.kickUser(user.id); void message.success(`已使 ${user.username} 的会话失效`); }
    catch (cause) { void message.error(messageOf(cause)); }
  };
  const columns: ColumnsType<UserSummary> = [
    { title: '用户', render: (_, row) => <div className="table-primary"><strong>{row.displayName}</strong><small>@{row.username}</small></div> },
    { title: '全局角色', dataIndex: 'globalRole', width: 120, render: (value) => <Tag color={value === 'ADMIN' ? 'gold' : 'blue'}>{value}</Tag> },
    { title: '状态', dataIndex: 'status', width: 110, render: (value) => <Tag color={value === 'ACTIVE' ? 'success' : 'default'}>{value}</Tag> },
    { title: '创建时间', dataIndex: 'createdAt', width: 190, render: formatTime },
    { title: '操作', width: 210, render: (_, row) => <Space>
      <Popconfirm title={`${row.status === 'ACTIVE' ? '停用' : '启用'}用户 ${row.username}？`}
        disabled={row.id === currentUserId} onConfirm={() => void toggleStatus(row)}>
        <Button size="small" disabled={row.id === currentUserId}>{row.status === 'ACTIVE' ? '停用' : '启用'}</Button>
      </Popconfirm>
      <Popconfirm title={`强制退出 ${row.username} 的所有会话？`} onConfirm={() => void kick(row)}>
        <Button size="small">踢下线</Button>
      </Popconfirm>
    </Space> },
  ];

  return <Card title="用户与全局角色" extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>新建用户</Button>}>
    <Typography.Paragraph type="secondary">管理员拥有所有功能权限；普通用户仍需通过项目成员角色获得项目访问权限。</Typography.Paragraph>
    <Table rowKey="id" loading={loading} columns={columns} dataSource={users}
      pagination={{ pageSize: 10, hideOnSinglePage: true }} />
    <Modal title="新建用户" open={open} confirmLoading={saving} okText="创建" cancelText="取消"
      onOk={() => form.submit()} onCancel={() => setOpen(false)} destroyOnHidden>
      <Form form={form} layout="vertical" initialValues={{ globalRole: 'USER' }} onFinish={(values) => void create(values)}>
        <Form.Item name="displayName" label="显示名称" rules={[{ required: true }, { max: 100 }]}><Input /></Form.Item>
        <Form.Item name="username" label="用户名" rules={[{ required: true }, { min: 3, max: 64 }]}><Input /></Form.Item>
        <Form.Item name="password" label="初始密码" extra="当前开发阶段允许 5 位以上简单密码"
          rules={[{ required: true }, { min: 5, max: 128 }]}><Input.Password /></Form.Item>
        <Form.Item name="globalRole" label="全局角色" rules={[{ required: true }]}>
          <Select options={[{ value: 'USER', label: '普通用户' }, { value: 'ADMIN', label: '管理员' }]} />
        </Form.Item>
      </Form>
    </Modal>
  </Card>;
}

function formatTime(value: string) { return value ? new Date(value).toLocaleString('zh-CN') : '—'; }
function messageOf(cause: unknown) { return cause instanceof Error ? cause.message : '操作失败'; }
