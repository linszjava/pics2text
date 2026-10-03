import { lazy, StrictMode, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import { App, ConfigProvider, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { authToken } from './auth';
import 'antd/dist/reset.css';
import './styles.css';

const LoginPage = lazy(() => import('./LoginPage').then((module) => ({ default: module.LoginPage })));
const LayoutEditorPage = lazy(() => import('./LayoutEditorPage').then((module) => ({ default: module.LayoutEditorPage })));
const OperationsDashboard = lazy(() => import('./OperationsDashboard').then((module) => ({ default: module.OperationsDashboard })));
const SurfacePreviewPage = lazy(() => import('./SurfacePreviewPage')
  .then((module) => ({ default: module.SurfacePreviewPage })));
const params = new URLSearchParams(window.location.search);
const view = params.get('view');
const current = `${window.location.pathname}${window.location.search}${window.location.hash}`;
const page = view === 'login' || !authToken()
  ? <LoginPage returnTo={view === 'login' ? undefined : current} />
  : view === 'surface'
    ? <SurfacePreviewPage />
    : view === 'editor' || (params.has('projectId') && params.has('layoutId'))
      ? <LayoutEditorPage />
      : <OperationsDashboard />;
createRoot(document.getElementById('root')!).render(<StrictMode>
  <ConfigProvider locale={zhCN} theme={{ algorithm: theme.defaultAlgorithm, token: {
    colorPrimary: '#147db6', colorBgBase: '#ffffff', colorBgLayout: '#f5f8fb', colorText: '#17344b',
    colorBorder: '#dce3e6', borderRadius: 10,
    fontFamily: 'Inter, "PingFang SC", "Microsoft YaHei", ui-sans-serif, system-ui, sans-serif',
  } }}><App><Suspense fallback={<main className="setup-panel">正在加载应用…</main>}>{page}</Suspense></App></ConfigProvider>
</StrictMode>);
