import { useState } from 'react';
import { ArrowRightOutlined, MoreOutlined, PlusOutlined } from '@ant-design/icons';
import { App, Button, Card, Form, Input, Modal, Popconfirm, Tag, Typography } from 'antd';
import { OperationsApi, type ProjectSummary } from './operationsApi';
import { statusLabel } from './uiLabels';

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
  return <>
    <div className="project-page-heading"><div><Typography.Text className="section-kicker">我的创作空间</Typography.Text>
      <Typography.Title level={1}>每一张照片，都能成为一句话。</Typography.Title>
      <Typography.Paragraph>从文字开始，创作属于你的照片作品。</Typography.Paragraph></div>
      <span className="project-page-number">{String(projects.length).padStart(2, '0')} <small>个作品</small></span>
    </div>
    {loading ? <Card loading /> : <div className="project-gallery">{projects.map((project, index) => <article className="project-artwork" key={project.id}>
        <button className="project-artwork-cover" onClick={() => onOpen(project.id)} aria-label={`打开作品${project.name}`}>
          <span className="project-cover-corner">作品 / {String(index + 1).padStart(2, '0')}</span>
          <span className="project-cover-title">{project.name}</span>
          <span className="project-cover-trail" aria-hidden="true" />
          <span className="project-cover-signal" aria-hidden="true"><i /><i /><i /><i /><i /><i /><i /><i /><i /></span>
          <img className="project-cover-rocket" src="/art/rocket-cover.png" alt="" loading="lazy" />
          <span className="project-cover-bottom">文字 / 照片 / 发射想象</span>
          <span className="project-cover-coordinates" aria-hidden="true">照片文字 · 创作任务</span>
        </button>
        <div className="project-artwork-info"><div><div className="project-artwork-title"><Typography.Title level={3}>{project.name}</Typography.Title>
          {project.status === 'ARCHIVED' && <Tag>{statusLabel(project.status)}</Tag>}</div>
          <Typography.Text type="secondary">更新于 {formatTime(project.updatedAt)}</Typography.Text></div>
          <div className="project-artwork-actions"><Button type="text" onClick={() => onOpen(project.id)}>继续创作 <ArrowRightOutlined /></Button>
            <Popconfirm title={project.status === 'ARCHIVED' ? '恢复这个项目？' : '归档这个项目？'} onConfirm={() => void toggleArchive(project)}>
              <Button type="text" shape="circle" icon={<MoreOutlined />} title={project.status === 'ARCHIVED' ? '恢复项目' : '归档项目'} aria-label={project.status === 'ARCHIVED' ? '恢复项目' : '归档项目'} />
            </Popconfirm></div></div>
      </article>)}<button className="project-create-tile" onClick={() => setOpen(true)}>
        <span className="project-create-symbol"><PlusOutlined /></span><strong>开始下一件作品</strong>
        <span>输入一句话，上传照片，预览它的最终样子。</span><span className="project-create-arrow">新建项目 <ArrowRightOutlined /></span>
      </button></div>}
    <Modal title="新建项目" open={open} confirmLoading={saving} okText="创建" cancelText="取消"
      onOk={() => form.submit()} onCancel={() => setOpen(false)} destroyOnHidden>
      <Form form={form} layout="vertical" onFinish={(values) => void create(values)}>
        <Form.Item label="项目名称" name="name" rules={[{ required: true, message: '请输入项目名称' }, { max: 120 }]}>
          <Input autoFocus placeholder="例如：中国人能飞 · 火箭箭体" />
        </Form.Item>
      </Form>
    </Modal>
  </>;
}

function formatTime(value: string) { return value ? new Date(value).toLocaleString('zh-CN') : '—'; }
function messageOf(cause: unknown) { return cause instanceof Error ? cause.message : '操作失败'; }
