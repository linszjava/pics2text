import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { CloudUploadOutlined, DownOutlined, EditOutlined,
  FontSizeOutlined, LogoutOutlined, ProjectOutlined, RocketOutlined,
  UserOutlined } from '@ant-design/icons';
import { Alert, App, Avatar, Button, Card, Dropdown, Empty, Flex, Layout,
  Select, Skeleton, Typography } from 'antd';
import { logout, type AuthenticatedUser } from './auth';
import { OperationsApi, type AutoLayoutSummary, type CreateAutoLayoutInput, type PhotoSummary, type ProjectSummary, type TextMaskSummary } from './operationsApi';
import type { EditableLayout } from './types';
import { ProjectManagement } from './ProjectManagement';
import { LayoutWorkflowPage } from './LayoutWorkflowPage';
import { PhotoUploadPage } from './PhotoUploadPage';

const { Header, Content } = Layout;
const { Text } = Typography;

export function OperationsDashboard() {
  const initialQuery = useMemo(() => new URLSearchParams(window.location.search), []);
  const { modal } = App.useApp();
  const api = useMemo(() => new OperationsApi(), []);
  const [user, setUser] = useState<AuthenticatedUser | null>(null);
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [projectId, setProjectId] = useState('');
  const [photos, setPhotos] = useState<PhotoSummary[]>([]);
  const [layouts, setLayouts] = useState<EditableLayout[]>([]);
  const [masks, setMasks] = useState<TextMaskSummary[]>([]);
  const [autoLayouts, setAutoLayouts] = useState<AutoLayoutSummary[]>([]);
  const [preparing, setPreparing] = useState(false);
  const [surfacePreparing, setSurfacePreparing] = useState(false);
  const pendingRequest = useRef('');
  const resumeSurface = useRef(initialQuery.get('step') === 'surface');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [section, setSection] = useState<'projects' | 'design' | 'upload' | 'editor' | 'surface'>(
    initialQuery.get('step') === 'surface' ? 'editor' : 'projects');

  useEffect(() => {
    void Promise.all([api.me(), api.projects()]).then(([me, values]) => {
      setUser(me); setProjects(values);
      const requestedId = initialQuery.get('projectId');
      setProjectId(values.find((item) => item.id === requestedId)?.id
        ?? values.find((item) => item.status === 'ACTIVE')?.id ?? values[0]?.id ?? '');
    }).catch((cause: unknown) => setError(messageOf(cause))).finally(() => setLoading(false));
  }, [api, initialQuery]);

  const loadProject = useCallback(async (id: string) => {
    if (!id) { setPhotos([]); setLayouts([]); setMasks([]); setAutoLayouts([]); return; }
    try {
      const [photoValues, layoutValues, maskValues, autoValues] = await Promise.all([
        api.photos(id), api.layouts(id), api.textMasks(id), api.autoLayouts(id),
      ]);
      setPhotos(photoValues); setLayouts(layoutValues); setMasks(maskValues); setAutoLayouts(autoValues);
    } catch (cause) { setError(messageOf(cause)); }
    finally { /* 页面数据已完成刷新 */ }
  }, [api]);

  useEffect(() => { void loadProject(projectId); }, [loadProject, projectId]);
  useEffect(() => {
    if (!projectId || !['upload', 'editor', 'surface'].includes(section)) return;
    const timer = window.setInterval(() => void loadProject(projectId), 10_000);
    return () => window.clearInterval(timer);
  }, [loadProject, projectId, section]);
  const project = projects.find((item) => item.id === projectId);
  const activeMask = masks.find((item) => item.id === project?.activeTextMaskId);
  const maskAutoLayouts = autoLayouts.filter((item) => item.maskId === activeMask?.id);
  const readyAutoLayout = maskAutoLayouts.find((item) => item.status === 'READY');
  const selectedLayout = layouts.find((item) => item.sourceAutoLayoutId === readyAutoLayout?.id && item.maskId === activeMask?.id);
  const usablePhotos = photos.filter((item) => item.status === 'AVAILABLE' && item.analysisStatus === 'SUCCEEDED');
  const needsRematch = !!readyAutoLayout && usablePhotos.length > readyAutoLayout.candidateCount;
  const prepareSurface = async () => {
    if (!projectId || !selectedLayout || needsRematch || surfacePreparing) return;
    setSurfacePreparing(true); setError('');
    try {
      const locked = selectedLayout.state === 'DRAFT'
        ? (await api.lockEditableLayout(projectId, selectedLayout.id, selectedLayout.lockVersion)).layout
        : selectedLayout;
      if (locked.state === 'LOCKED') {
        const templates = await api.surfaceTemplates(projectId);
        const template = templates.find((item) => item.template.name === '演示火箭曲面')
          ?? await api.createSurfaceTemplate(projectId);
        await api.bindSurface(projectId, locked, template.template);
        await api.createLayoutPreview(projectId, locked.id);
      }
      setLayouts((current) => current.map((item) => item.id === locked.id ? locked : item));
      setSection('surface');
    } catch (cause) { setError(messageOf(cause)); void loadProject(projectId); }
    finally { setSurfacePreparing(false); }
  };
  useEffect(() => {
    if (!resumeSurface.current || !selectedLayout) return;
    resumeSurface.current = false;
    void prepareSurface();
  }, [selectedLayout]);
  useEffect(() => {
    if (!projectId || !activeMask || !['editor', 'surface'].includes(section) || preparing) return;
    if (activeMask.status !== 'READY' || !usablePhotos.length) return;
    const latestAutoLayout = maskAutoLayouts[0];
    if (!latestAutoLayout || (needsRematch && !['PENDING', 'RUNNING', 'FAILED'].includes(latestAutoLayout.status))) {
      const key = `auto:${projectId}:${activeMask.id}:${usablePhotos.length}`;
      if (pendingRequest.current === key) return;
      pendingRequest.current = key;
      setPreparing(true);
      const input: CreateAutoLayoutInput = {
        name: `${activeMask.text}照片排版（${usablePhotos.length}张）`, maskId: activeMask.id, randomSeed: 20261002,
        allowPhotoReuse: true, maxPhotoReuse: 10000, targetLabL: 128, targetLabA: 128, targetLabB: 128,
        candidateWindow: 64, optimizationIterations: 1000,
        weights: { quality: 1, brightness: 1, color: 1, crop: 1, duplicate: 1, adjacent: 1 },
      };
      void api.createAutoLayout(projectId, input).then(({ layout }) => {
        setAutoLayouts((current) => current.some((item) => item.id === layout.id) ? current : [layout, ...current]);
      }).catch((cause: unknown) => { setError(messageOf(cause)); pendingRequest.current = ''; })
        .finally(() => setPreparing(false));
      return;
    }
    if (readyAutoLayout && !selectedLayout) {
      const key = `editable:${projectId}:${readyAutoLayout.id}`;
      if (pendingRequest.current === key) return;
      pendingRequest.current = key;
      setPreparing(true);
      void api.createEditableLayout(projectId, readyAutoLayout.id, `${readyAutoLayout.name}编辑稿`).then((layout) => {
        setLayouts((current) => current.some((item) => item.id === layout.id) ? current : [layout, ...current]);
      }).catch((cause: unknown) => { setError(messageOf(cause)); pendingRequest.current = ''; void loadProject(projectId); })
        .finally(() => setPreparing(false));
    }
  }, [activeMask, api, loadProject, maskAutoLayouts, needsRematch, preparing, projectId, readyAutoLayout, section, selectedLayout, usablePhotos.length]);
  const menuItems = [
    { key: 'projects', icon: <ProjectOutlined />, label: '1. 创建项目' },
    { key: 'design', icon: <FontSizeOutlined />, label: '2. 设置文字' },
    { key: 'upload', icon: <CloudUploadOutlined />, label: '3. 上传照片' },
    { key: 'editor', icon: <EditOutlined />, label: '4. 二维效果' },
    { key: 'surface', icon: <RocketOutlined />, label: '5. 三维效果' },
  ];
  const selectSection = (key: string) => key === 'surface' ? void prepareSurface() : setSection(key as typeof section);

  return <Layout className="ops-shell">
      <Header className="ops-header">
        <div className="ops-brand"><span>P2T</span><div><strong>Pics2Text</strong><small>照片文字创作</small></div></div>
        <div className="ops-heading"><Text type="secondary">创作空间 / </Text><strong>{section === 'projects' ? '我的作品' : project?.name ?? '选择项目'}</strong></div>
        <Dropdown trigger={['click']} menu={{ items: [{ key: 'logout', icon: <LogoutOutlined />, label: '退出登录' }],
          onClick: () => modal.confirm({ title: '退出当前账号？', content: '确认后会返回登录页面。', okText: '退出登录', cancelText: '继续创作', onOk: () => logout() }) }}>
          <Button className="ops-account" type="text" aria-label="账户菜单"><Avatar size="small" icon={<UserOutlined />} />
            <span>{user?.displayName ?? '加载中'}</span><DownOutlined /></Button>
        </Dropdown>
      </Header>
      <Content className="ops-content">
        {error && <Alert type="error" showIcon closable title={error} onClose={() => setError('')} />}
        {!loading && <nav className="workflow-rail" aria-label="创作步骤">
          {menuItems.map((item, index) => {
            const activeIndex = menuItems.findIndex((entry) => entry.key === section);
            const state = index < activeIndex ? 'done' : index === activeIndex ? 'active' : '';
            const disabled = index === 1 ? !projectId : index === 2 ? !activeMask
              : index === 3 ? !activeMask || photos.length === 0
              : index === 4 ? !selectedLayout || needsRematch : false;
            return <button key={item.key} className={`workflow-rail-item ${state}`} disabled={disabled} onClick={() => selectSection(item.key)}>
              <span className="workflow-rail-index">{index < activeIndex ? '✓' : index + 1}</span>
              <span>{item.label.replace(/^\d+\.\s*/, '')}</span>
            </button>;
          })}
        </nav>}
        {!loading && section !== 'projects' && <ProjectSwitcher
          projectId={projectId} projects={projects} onChange={setProjectId} />}
        {loading ? <Skeleton active /> : section === 'projects'
          ? <ProjectManagement api={api} projects={projects} loading={loading} onChanged={setProjects}
              onOpen={(id) => { setProjectId(id); setSection('design'); }} />
          : section === 'upload'
            ? <PhotoUploadPage api={api} projectId={projectId} photos={photos} onChanged={setPhotos} />
          : section === 'design'
            ? <LayoutWorkflowPage api={api} projectId={projectId} activeTextMaskId={project?.activeTextMaskId ?? null}
                onProjectChanged={(changed) => { setProjects((current) => current.map((item) => item.id === changed.id ? changed : item)); void loadProject(changed.id); }}
                onNext={() => setSection('upload')} />
          : <PreviewStep projectId={projectId} layout={needsRematch ? undefined : selectedLayout} mode={section} photos={photos.length}
              mask={activeMask} autoLayout={maskAutoLayouts[0]} preparing={preparing || surfacePreparing} photosReady={usablePhotos.length}
              needsRematch={needsRematch}
              onBack={() => setSection(section === 'surface' ? 'editor' : !activeMask ? 'design' : 'upload')}
              onNext={() => void prepareSurface()} />}
      </Content>
  </Layout>;
}

function PreviewStep({ projectId, layout, mask, autoLayout, mode, photos, photosReady, needsRematch, preparing, onBack, onNext }: {
  projectId: string; layout?: EditableLayout; mask?: TextMaskSummary; autoLayout?: AutoLayoutSummary;
  mode: 'editor' | 'surface'; photos: number; photosReady: number; needsRematch: boolean; preparing: boolean;
  onBack: () => void; onNext: () => void;
}) {
  if (!projectId) return <Card><Empty description="请先创建项目" /></Card>;
  const is3d = mode === 'surface';
  const pendingMessage = !mask ? '请先在第二步保存文字与排版方案。'
    : mask.status !== 'READY' ? '文字方案正在生成，请稍候。'
    : photos === 0 ? '请先上传照片。'
    : photosReady === 0 ? '照片正在处理，完成后会自动填入文字。'
    : needsRematch ? `已有 ${photosReady} 张照片分析完成，正在重新匹配全部照片；原排版会保留。`
    : autoLayout?.status === 'FAILED' ? '照片排版生成失败，请检查照片并重试。'
    : preparing || !autoLayout ? '正在按已保存的方案生成照片排版。'
    : autoLayout.status !== 'READY' ? '正在将照片填入文字，请稍候。'
    : '正在准备可编辑的预览。';
  return <section className="result-workspace">
    <div className="result-heading"><span className="section-kicker">{is3d ? '成品预览 / 05' : '照片排版 / 04'}</span>
      <h2>{is3d ? '让作品贴上火箭。' : '看见照片组成的文字。'}</h2>
      <p>{is3d ? '旋转查看照片墙贴合箭体的效果。' : '检查每张照片的位置，再决定是否进入三维预览。'}</p></div>
    <div className="result-panel"><div className={`result-art ${is3d ? 'rocket' : 'mosaic'}`} aria-hidden="true">
      <div className="result-orbit" />{is3d ? <RocketOutlined /> : <div className="result-pixels">{Array.from({ length: 56 }, (_, index) => <i key={index} />)}</div>}
      <span>{is3d ? '三维曲面预览' : '二维照片布局'}</span></div>
      <div className="result-content"><span className="section-kicker">{layout ? '当前作品' : '创作进行中'}</span>
        <h3>{layout?.name ?? (autoLayout?.status === 'FAILED' ? '照片排版生成失败'
          : photosReady > 0 ? '正在准备可预览的二维排版' : '还没有可预览的照片布局')}</h3>
        <p>{layout ? `已上传 ${photos} 张照片，其中 ${photosReady} 张完成分析；当前排版使用 ${autoLayout?.candidateCount ?? photosReady} 张。点击下方按钮打开实际${is3d ? '三维' : '二维'}效果。`
          : pendingMessage}</p>
        {layout ? <div className="result-actions"><Button size="large" type="primary"
          href={`?view=${is3d ? 'surface' : 'editor'}&projectId=${projectId}&layoutId=${layout.id}`}>
          打开{is3d ? '三维效果' : '二维效果'}
          </Button>{!is3d && <Button size="large" loading={preparing} onClick={onNext}>继续查看三维效果</Button>}</div>
          : <Button size="large" type="primary" onClick={onBack}>{is3d ? '返回二维效果' : !mask ? '返回设置文字' : '返回上传照片'}</Button>}</div>
    </div>
  </section>;
}

function ProjectSwitcher({ projectId, projects, onChange }: { projectId: string; projects: ProjectSummary[]; onChange: (id: string) => void }) {
  return <Flex className="ops-project-switcher" align="center" gap={12} wrap>
    <Text type="secondary">当前项目</Text>
    <Select value={projectId || undefined} placeholder="选择项目" style={{ minWidth: 220 }}
      options={projects.map((item) => ({ value: item.id, label: `${item.name}${item.status === 'ARCHIVED' ? '（已归档）' : ''}` }))}
      onChange={onChange} />
  </Flex>;
}

function messageOf(cause: unknown) { return cause instanceof Error ? cause.message : '加载失败'; }
