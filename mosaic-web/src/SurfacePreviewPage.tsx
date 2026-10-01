import { useEffect, useMemo, useState } from 'react';
import { LayoutApi } from './api';
import { authToken } from './auth';
import { ThreeSurfaceCanvas } from './ThreeSurfaceCanvas';
import type { SurfacePreviewData } from './types';

export function SurfacePreviewPage() {
  const params = useMemo(() => new URLSearchParams(window.location.search), []);
  const projectId = params.get('projectId') ?? ''; const layoutId = params.get('layoutId') ?? '';
  const token = authToken();
  const api = useMemo(() => new LayoutApi('', token), [token]);
  const [data, setData] = useState<SurfacePreviewData | null>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!projectId || !layoutId || !token) return;
    void api.surfacePreviewData(projectId, layoutId).then(setData)
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : '加载失败'));
  }, [api, layoutId, projectId, token]);
  if (!projectId || !layoutId || !token) return <main className="setup-panel"><h1>曲面三维预览</h1><p>缺少项目、布局或登录信息。</p></main>;
  if (!data) return <main className="setup-panel"><h1>曲面三维预览</h1><p>{error || '正在加载不可变坐标清单…'}</p></main>;
  const m = data.manifest;
  return <main className="surface-shell">
    <header className="topbar"><div><strong>曲面三维预览</strong>
      <span className={`state ${data.validationStatus === 'VALID' ? 'draft' : 'locked'}`}>{data.validationStatus}</span>
      <small>清单 {data.manifestHash.slice(0, 12)}…</small></div>
      <a className="button-link" href={`?projectId=${projectId}&layoutId=${layoutId}`}>返回二维编辑器</a></header>
    <section className="surface-workspace"><ThreeSurfaceCanvas manifest={m} textureUrl={data.textureUrl} />
      <aside className="inspector"><h2>物理参数</h2><dl>
        <dt>直径</dt><dd>{m.surface.diameterMm.toFixed(2)} mm</dd>
        <dt>周长</dt><dd>{m.surface.circumferenceMm.toFixed(2)} mm</dd>
        <dt>轴长</dt><dd>{m.surface.axialLengthMm.toFixed(2)} mm</dd>
        <dt>X 比例</dt><dd>{m.mapping.scaleXmmPerPx.toFixed(6)} mm/px</dd>
        <dt>Y 比例</dt><dd>{m.mapping.scaleYmmPerPx.toFixed(6)} mm/px</dd>
        <dt>槽位</dt><dd>{m.slots.length}</dd><dt>区域</dt><dd>{m.regions.length}</dd>
      </dl><p className="muted">拖动旋转，滚轮缩放。红线表示接缝；红色区域为禁印区，蓝色为舱段，黄色为关键结构。</p>
      {!data.textureUrl && <p className="error">当前版本尚无 READY 的低清纹理，正在使用材质占位。</p>}</aside></section>
  </main>;
}
