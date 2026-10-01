import { useMemo, useState } from 'react';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Form, Input, Segmented, Space, Typography } from 'antd';
import { login, register, safeReturnTo } from './auth';

const { Paragraph, Text, Title } = Typography;
type Credentials = { displayName?: string; username: string; password: string; confirmPassword?: string };

export function LoginPage({ returnTo }: { returnTo?: string }) {
  const params = useMemo(() => new URLSearchParams(window.location.search), []);
  const target = safeReturnTo(returnTo ?? params.get('returnTo'));
  const [form] = Form.useForm<Credentials>();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const submit = async (values: Credentials) => {
    setSubmitting(true); setError('');
    try {
      if (mode === 'register') {
        await register(values.username.trim(), values.password, values.displayName!.trim());
      } else {
        await login(values.username.trim(), values.password);
      }
      window.location.assign(target);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : `${mode === 'register' ? '注册' : '登录'}失败，请稍后重试`);
      setSubmitting(false);
    }
  };

  const switchMode = (value: string | number) => {
    setMode(value as 'login' | 'register'); form.resetFields(); setError('');
  };

  return <main className="login-page">
    <section className="login-brand">
      <span className="brand-mark">P2T</span>
      <p className="eyebrow">PICS TO TEXT</p>
      <h1>让每一张照片，<br />组成共同的表达。</h1>
      <p>照片马赛克布局、曲面预览与大型印刷交付平台</p>
    </section>
    <Card className="login-card" variant="outlined">
      <Space orientation="vertical" size={4} className="login-heading">
        <Text className="eyebrow">工作台</Text>
        <Title level={2}>{mode === 'login' ? '登录' : '创建'} Pics2Text 账号</Title>
        <Paragraph type="secondary">{mode === 'login'
          ? '使用平台账号继续访问当前项目。' : '注册普通用户账号，完成后将自动登录。'}</Paragraph>
      </Space>
      <Segmented block value={mode} onChange={switchMode} options={[
        { label: '账号登录', value: 'login' }, { label: '创建账号', value: 'register' },
      ]} />
      {params.get('reason') === 'expired' && <Alert type="warning" showIcon message="原登录状态已失效，请重新登录。" />}
      {error && <Alert type="error" showIcon message={error} />}
      <Form form={form} layout="vertical" requiredMark={false} onFinish={(values) => void submit(values)}
        autoComplete="off" size="large">
        {mode === 'register' && <Form.Item label="显示名称" name="displayName"
          rules={[{ required: true, message: '请输入显示名称' }, { max: 100 }]}>
          <Input autoFocus placeholder="请输入显示名称" />
        </Form.Item>}
        <Form.Item label="用户名" name="username" rules={[
          { required: true, message: '请输入用户名' }, { min: 3, max: 64, message: '用户名需为 3–64 位' },
        ]}>
          <Input autoFocus={mode === 'login'} prefix={<UserOutlined />} autoComplete="username" placeholder="请输入用户名" />
        </Form.Item>
        <Form.Item label="密码" name="password" extra={mode === 'register' ? '当前开发阶段密码至少 5 位。' : undefined}
          rules={[{ required: true, message: '请输入密码' }, { min: 5, max: 128, message: '密码需为 5–128 位' }]}>
          <Input.Password prefix={<LockOutlined />} autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            placeholder="请输入密码" />
        </Form.Item>
        {mode === 'register' && <Form.Item label="确认密码" name="confirmPassword" dependencies={['password']}
          rules={[{ required: true, message: '请再次输入密码' }, ({ getFieldValue }) => ({
            validator(_, value) { return !value || getFieldValue('password') === value
              ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致')); },
          })]}>
          <Input.Password prefix={<LockOutlined />} autoComplete="new-password" placeholder="请再次输入密码" />
        </Form.Item>}
        <Button type="primary" htmlType="submit" block loading={submitting}>
          {mode === 'login' ? '登录' : '注册并登录'}
        </Button>
      </Form>
      <Text type="secondary" className="login-security-note">登录会话仅保存在当前浏览器；请勿在公共设备上保持登录。</Text>
    </Card>
  </main>;
}
