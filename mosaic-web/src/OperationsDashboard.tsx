import { useCallback, useEffect, useMemo, useState } from 'react';
import { AuditOutlined, ClockCircleOutlined, CloudUploadOutlined, DashboardOutlined, FileImageOutlined, FontSizeOutlined,
  LogoutOutlined, MenuOutlined, ProjectOutlined, RocketOutlined, SafetyCertificateOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, Avatar, Button, Card, Col, Drawer, Empty, Flex, Layout, Menu, Progress, Row,
  Select, Skeleton, Space, Statistic, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { logout, type AuthenticatedUser } from './auth';
import { OperationsApi, type JobSummary, type PhotoSummary, type ProjectSummary,
  type RenderSummary } from './operationsApi';
import type { EditableLayout } from './types';
import { ProjectManagement } from './ProjectManagement';
import { UserManagement } from './UserManagement';
import { AuditLogPage } from './AuditLogPage';
import { PhotoReviewPage, RenderDeliveryPage, TaskCenterPage } from './WorkflowPages';
import { ProjectMembersPage } from './ProjectMembersPage';
import { LayoutWorkflowPage } from './LayoutWorkflowPage';
import { PhotoUploadPage } from './PhotoUploadPage';

const { Header, Sider, Content } = Layout;
const { Text, Title } = Typography;

export function OperationsDashboard() {
  const api = useMemo(() => new OperationsApi(), []);
  const [user, setUser] = useState<AuthenticatedUser | null>(null);
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [projectId, setProjectId] = useState('');
  const [photos, setPhotos] = useState<PhotoSummary[]>([]);
  const [jobs, setJobs] = useState<JobSummary[]>([]);
  const [renders, setRenders] = useState<RenderSummary[]>([]);
  const [layouts, setLayouts] = useState<EditableLayout[]>([]);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState('');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [section, setSection] = useState<'overview' | 'projects' | 'members' | 'design' | 'upload' | 'photos' | 'tasks' | 'renders' | 'users' | 'audit'>('overview');

  useEffect(() => {
    void Promise.all([api.me(), api.projects()]).then(([me, values]) => {
      setUser(me); setProjects(values);
      setProjectId(values.find((item) => item.status === 'ACTIVE')?.id ?? values[0]?.id ?? '');
    }).catch((cause: unknown) => setError(messageOf(cause))).finally(() => setLoading(false));
  }, [api]);

  const loadProject = useCallback(async (id: string) => {
    if (!id) { setPhotos([]); setJobs([]); setRenders([]); setLayouts([]); return; }
    setDetailLoading(true); setError('');
    try {
      const values = await Promise.all([api.photos(id), api.jobs(id), api.renders(id), api.layouts(id)]);
      setPhotos(values[0]); setJobs(values[1]); setRenders(values[2]); setLayouts(values[3]);
    } catch (cause) { setError(messageOf(cause)); }
    finally { setDetailLoading(false); }
  }, [api]);

  useEffect(() => { void loadProject(projectId); }, [loadProject, projectId]);
  useEffect(() => {
    if (!projectId || !['overview', 'tasks'].includes(section)) return;
    const timer = window.setInterval(() => void loadProject(projectId), 10_000);
    return () => window.clearInterval(timer);
  }, [loadProject, projectId, section]);
  const project = projects.find((item) => item.id === projectId);
  const sectionCopy = {
    overview: ['工作概览', '集中查看项目、照片、任务与印刷交付状态'],
    projects: ['项目与布局', '创建、归档项目并进入布局工作区'],
    members: ['项目成员', '配置当前项目成员及其业务权限'],
    design: ['文字与排版', '生成文字蒙版并启动照片自动排版'],
    upload: ['照片上传', '批量直传照片并自动进入分析流水线'],
    photos: ['照片审核', '处理照片分析、重复检测与人工审核'],
    tasks: ['任务中心', '查看长任务进度、失败详情并执行重试'],
    renders: ['渲染交付', '审批印刷成品并获取受控下载地址'],
    users: ['用户与角色', '管理平台账号、全局角色和登录状态'],
    audit: ['审计日志', '查询身份、权限及关键业务操作记录'],
  }[section];
  const pendingJobs = jobs.filter((item) => !['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(item.status)).length;
  const reviewPhotos = photos.filter((item) => !['APPROVED', 'REJECTED'].includes(item.reviewStatus)).length;
  const readyRenders = renders.filter((item) => ['READY_FOR_APPROVAL', 'APPROVED'].includes(item.status)).length;
  const menuItems = [
    { key: 'overview', icon: <DashboardOutlined />, label: '工作概览' },
    { key: 'projects', icon: <ProjectOutlined />, label: '项目与布局' },
    { key: 'members', icon: <SafetyCertificateOutlined />, label: '项目成员' },
    { key: 'design', icon: <FontSizeOutlined />, label: '文字与排版' },
    { key: 'upload', icon: <CloudUploadOutlined />, label: '照片上传' },
    { key: 'photos', icon: <FileImageOutlined />, label: '照片审核' },
    { key: 'tasks', icon: <ClockCircleOutlined />, label: '任务中心' },
    { key: 'renders', icon: <RocketOutlined />, label: '渲染交付' },
    ...(user?.globalRole === 'ADMIN' ? [
      { key: 'users', icon: <TeamOutlined />, label: '用户与角色' },
      { key: 'audit', icon: <AuditOutlined />, label: '审计日志' },
    ] : []),
  ];
  const selectSection = (key: string) => { setSection(key as typeof section); setMobileMenuOpen(false); };

  const jobColumns: ColumnsType<JobSummary> = [
    { title: '任务类型', dataIndex: 'type', ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 140, render: (value: string) => <StatusTag value={value} /> },
    { title: '进度', dataIndex: 'progress', width: 150,
      render: (value: number) => <Progress percent={value} size="small" status={value === 100 ? 'success' : 'active'} /> },
    { title: '尝试', width: 80, render: (_, row) => `${row.currentAttempt}/${row.maxAttempts}` },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: formatTime },
  ];
  const layoutColumns: ColumnsType<EditableLayout> = [
    { title: '布局', dataIndex: 'name', ellipsis: true },
    { title: '状态', dataIndex: 'state', width: 120, render: (value: string) => <StatusTag value={value} /> },
    { title: '版本', dataIndex: 'currentVersionNumber', width: 80 },
    { title: '操作', width: 190, render: (_, row) => <Space>
      <Button size="small" href={`?view=editor&projectId=${projectId}&layoutId=${row.id}`}>二维编辑</Button>
      <Button size="small" href={`?view=surface&projectId=${projectId}&layoutId=${row.id}`}>三维预览</Button>
    </Space> },
  ];

  return <Layout className="ops-shell">
    <Sider width={224} className="ops-sider">
      <div className="ops-logo"><span>P2T</span><div><strong>Pics2Text</strong><small>运营工作台</small></div></div>
      <Menu theme="dark" mode="inline" selectedKeys={[section]} onClick={({ key }) => selectSection(key)} items={menuItems} />
      <div className="ops-sider-foot"><Text type="secondary">P10 · 前端运营流程</Text></div>
    </Sider>
    <Layout>
      <Header className="ops-header">
        <Button className="ops-mobile-menu" type="text" icon={<MenuOutlined />} aria-label="打开导航" onClick={() => setMobileMenuOpen(true)} />
        <div className="ops-heading"><Title level={4}>{sectionCopy[0]}</Title><Text type="secondary">{sectionCopy[1]}</Text></div>
        <Space size="middle"><Avatar icon={<UserOutlined />} />
          <div className="ops-user"><strong>{user?.displayName ?? '加载中'}</strong><small>{user?.globalRole}</small></div>
          <Button icon={<LogoutOutlined />} onClick={() => void logout()}>退出</Button></Space>
      </Header>
      <Content className="ops-content">
        {error && <Alert type="error" showIcon closable title={error} onClose={() => setError('')} />}
        {!loading && !['projects', 'users', 'audit', 'overview'].includes(section) && <ProjectSwitcher
          projectId={projectId} projects={projects} project={project} onChange={setProjectId} />}
        {loading ? <Skeleton active /> : section === 'projects'
          ? <ProjectManagement api={api} projects={projects} loading={loading} onChanged={setProjects}
              onOpen={(id) => { setProjectId(id); setSection('overview'); }} />
          : section === 'photos'
            ? <PhotoReviewPage api={api} projectId={projectId} photos={photos} loading={detailLoading} onChanged={setPhotos} />
          : section === 'upload'
            ? <PhotoUploadPage api={api} projectId={projectId} photos={photos} onChanged={setPhotos} />
          : section === 'members'
            ? <ProjectMembersPage api={api} projectId={projectId} />
          : section === 'design'
            ? <LayoutWorkflowPage api={api} projectId={projectId} />
          : section === 'tasks'
            ? <TaskCenterPage api={api} projectId={projectId} jobs={jobs} loading={detailLoading} onChanged={setJobs} />
          : section === 'renders'
            ? <RenderDeliveryPage api={api} projectId={projectId} renders={renders} layouts={layouts} loading={detailLoading} onChanged={setRenders} />
          : section === 'users' && user?.globalRole === 'ADMIN'
            ? <UserManagement api={api} currentUserId={user.id} />
            : section === 'audit' && user?.globalRole === 'ADMIN'
              ? <AuditLogPage api={api} />
              : <>
          <ProjectSwitcher projectId={projectId} projects={projects} project={project} onChange={setProjectId} />
          {!projectId ? <Empty description="当前账号下暂无项目" /> : <>
            <Row gutter={[16, 16]}>
              <Col xs={24} sm={12} xl={6}><Card><Statistic title="照片总数" value={photos.length} prefix={<FileImageOutlined />} /></Card></Col>
              <Col xs={24} sm={12} xl={6}><Card><Statistic title="待审核照片" value={reviewPhotos} /></Card></Col>
              <Col xs={24} sm={12} xl={6}><Card><Statistic title="运行中任务" value={pendingJobs} /></Card></Col>
              <Col xs={24} sm={12} xl={6}><Card><Statistic title="待交付成品" value={readyRenders} prefix={<RocketOutlined />} /></Card></Col>
            </Row>
            <Card className="ops-section" title="布局工作区" extra={<Text type="secondary">{layouts.length} 个布局</Text>}>
              <Table rowKey="id" columns={layoutColumns} dataSource={layouts} loading={detailLoading}
                pagination={{ pageSize: 5, hideOnSinglePage: true }} locale={{ emptyText: '暂无可编辑布局' }} />
            </Card>
            <Card className="ops-section" title="最近任务" extra={<Text type="secondary">自动刷新将在任务中心阶段接入</Text>}>
              <Table rowKey="id" columns={jobColumns} dataSource={jobs} loading={detailLoading}
                pagination={{ pageSize: 8, hideOnSinglePage: true }} locale={{ emptyText: '暂无任务记录' }} />
            </Card>
          </>}
        </>}
      </Content>
    </Layout>
    <Drawer className="ops-mobile-drawer" title="Pics2Text" placement="left" size={270} open={mobileMenuOpen} onClose={() => setMobileMenuOpen(false)}>
      <Menu mode="inline" selectedKeys={[section]} items={menuItems} onClick={({ key }) => selectSection(key)} />
    </Drawer>
  </Layout>;
}

function ProjectSwitcher({ projectId, projects, project, onChange }: { projectId: string; projects: ProjectSummary[]; project?: ProjectSummary; onChange: (id: string) => void }) {
  return <Flex className="ops-project-switcher" align="center" justify="space-between" gap={16} wrap>
    <div><Text type="secondary">当前项目</Text><Title level={3}>{project?.name ?? '暂无项目'}</Title></div>
    <Select value={projectId || undefined} placeholder="选择项目" style={{ minWidth: 280 }}
      options={projects.map((item) => ({ value: item.id, label: `${item.name}${item.status === 'ARCHIVED' ? '（已归档）' : ''}` }))}
      onChange={onChange} />
  </Flex>;
}

function StatusTag({ value }: { value: string }) {
  const color = value.includes('FAIL') || value.includes('REJECT') || value === 'INVALID' ? 'error'
    : value.includes('RUN') || value.includes('PROCESS') || value.includes('PENDING') ? 'processing'
      : value.includes('SUCCEED') || value.includes('APPROVED') || value === 'PUBLISHED' || value === 'ACTIVE' ? 'success'
        : 'default';
  return <Tag color={color}>{value}</Tag>;
}

function formatTime(value: string) { return value ? new Date(value).toLocaleString('zh-CN') : '—'; }
function messageOf(cause: unknown) { return cause instanceof Error ? cause.message : '加载失败'; }
