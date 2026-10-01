import { lazy, StrictMode, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import { LayoutEditorPage } from './LayoutEditorPage';
import './styles.css';

const SurfacePreviewPage = lazy(() => import('./SurfacePreviewPage')
  .then((module) => ({ default: module.SurfacePreviewPage })));
const page = new URLSearchParams(window.location.search).get('view') === 'surface'
  ? <Suspense fallback={<main className="setup-panel">正在加载三维引擎…</main>}><SurfacePreviewPage /></Suspense>
  : <LayoutEditorPage />;
createRoot(document.getElementById('root')!).render(<StrictMode>{page}</StrictMode>);
