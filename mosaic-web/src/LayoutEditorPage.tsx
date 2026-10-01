import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { LayoutApi, type ViewportQuery } from './api';
import { DraftRecovery, type PendingDraft } from './draftRecovery';
import { PixiLayoutCanvas, type CanvasHandle } from './PixiLayoutCanvas';
import type { ApiFailure, Assignment, EditCommand, EditableLayout } from './types';

const AUTOSAVE_DELAY_MS = 1_200;

export function LayoutEditorPage() {
  const params = useMemo(() => new URLSearchParams(window.location.search), []);
  const projectId = params.get('projectId') ?? '';
  const layoutId = params.get('layoutId') ?? '';
  const token = localStorage.getItem('pics2text.token') ?? '';
  const api = useMemo(() => new LayoutApi('', token), [token]);
  const recovery = useMemo(() => new DraftRecovery(localStorage,
    `pics2text.layout-draft.${projectId}.${layoutId}`), [projectId, layoutId]);
  const canvasRef = useRef<CanvasHandle>(null);
  const layoutRef = useRef<EditableLayout | null>(null);
  const pendingRef = useRef<EditCommand[]>([]);
  const replayDraftRef = useRef<PendingDraft | null>(null);
  const savingRef = useRef(false);
  const viewportSequence = useRef(0);
  const urlCache = useRef(new Map<string, string>());
  const [layout, setLayoutState] = useState<EditableLayout | null>(null);
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [photoUrls, setPhotoUrls] = useState(new Map<string, string>());
  const [selected, setSelected] = useState<Assignment | null>(null);
  const [pendingCount, setPendingCount] = useState(0);
  const [status, setStatus] = useState('正在加载布局…');
  const [error, setError] = useState('');
  const [locateValue, setLocateValue] = useState('');
  const [replacePhotoId, setReplacePhotoId] = useState('');
  const [swapSlot, setSwapSlot] = useState('');
  const [crop, setCrop] = useState({ x: 0, y: 0, width: 1, height: 1 });

  const setLayout = useCallback((value: EditableLayout) => {
    layoutRef.current = value;
    setLayoutState(value);
  }, []);

  const loadPhotoUrls = useCallback(async (items: Assignment[]) => {
    const missing = [...new Set(items.map((item) => item.photoId))]
      .filter((photoId) => !urlCache.current.has(photoId));
    for (let index = 0; index < missing.length; index += 200) {
      const values = await api.photoUrls(projectId, layoutId, missing.slice(index, index + 200));
      values.forEach((value) => urlCache.current.set(value.photoId, value.url));
    }
    setPhotoUrls(new Map(urlCache.current));
  }, [api, layoutId, projectId]);

  const loadViewport = useCallback(async (bounds: ViewportQuery) => {
    const sequence = ++viewportSequence.current;
    if (bounds.maxX <= bounds.minX || bounds.maxY <= bounds.minY) {
      setAssignments([]);
      return;
    }
    try {
      const values = await api.assignments(projectId, layoutId, bounds);
      if (sequence !== viewportSequence.current) return;
      setAssignments(values);
      void loadPhotoUrls(values).catch(() => setStatus('部分缩略图暂时无法加载'));
    } catch (cause) {
      if (sequence === viewportSequence.current) setError(messageOf(cause));
    }
  }, [api, layoutId, loadPhotoUrls, projectId]);

  const reload = useCallback(async () => {
    const value = await api.getLayout(projectId, layoutId);
    setLayout(value);
    setStatus(value.state === 'DRAFT' ? '所有修改已保存' : `布局已${value.state === 'LOCKED' ? '锁定' : '发布'}`);
  }, [api, layoutId, projectId, setLayout]);

  const flush = useCallback(async () => {
    if (savingRef.current || (!replayDraftRef.current && pendingRef.current.length === 0)
        || !layoutRef.current) return;
    savingRef.current = true;
    const draft = replayDraftRef.current ?? {
      clientBatchId: crypto.randomUUID(),
      expectedLockVersion: layoutRef.current.lockVersion,
      commands: pendingRef.current.splice(0, 100),
      savedAt: new Date().toISOString(),
    };
    replayDraftRef.current = draft;
    recovery.save(draft);
    setPendingCount(draft.commands.length + pendingRef.current.length);
    setStatus(`正在保存 ${draft.commands.length} 项修改…`);
    try {
      const result = await api.edit(projectId, layoutId, draft.expectedLockVersion,
        draft.clientBatchId, draft.commands);
      setLayout(result.layout);
      replayDraftRef.current = null;
      recovery.clear();
      setPendingCount(pendingRef.current.length);
      setStatus(`已保存为版本 ${result.version.versionNumber}`);
    } catch (cause) {
      const failure = cause as ApiFailure;
      if (failure.status === 409) {
        setError('检测到并发修改或布局状态变化，未覆盖服务端数据。请检查后重新操作。');
        pendingRef.current = [];
        replayDraftRef.current = null;
        setPendingCount(0);
        recovery.clear();
        await reload().catch(() => undefined);
      } else {
        setPendingCount(draft.commands.length + pendingRef.current.length);
        setError(`自动保存失败，修改已保留在本机：${messageOf(cause)}`);
      }
    } finally {
      savingRef.current = false;
      if (replayDraftRef.current || pendingRef.current.length > 0) {
        window.setTimeout(() => void flush(), AUTOSAVE_DELAY_MS);
      }
    }
  }, [api, layoutId, projectId, recovery, reload, setLayout]);

  const enqueue = useCallback((command: EditCommand) => {
    if (layoutRef.current?.state !== 'DRAFT') return;
    pendingRef.current.push(command);
    setPendingCount(pendingRef.current.length);
    setStatus('有尚未保存的修改');
    setError('');
  }, []);

  useEffect(() => {
    if (!projectId || !layoutId || !token) return;
    void reload().then(() => {
      const draft = recovery.load();
      if (draft?.commands.length) {
        replayDraftRef.current = draft;
        setPendingCount(draft.commands.length);
        setStatus(`已恢复 ${draft.commands.length} 项未完成保存`);
      }
    }).catch((cause) => setError(messageOf(cause)));
  }, [layoutId, projectId, recovery, reload, token]);

  useEffect(() => {
    if (!pendingCount || savingRef.current) return;
    const timer = window.setTimeout(() => void flush(), AUTOSAVE_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [flush, pendingCount]);

  useEffect(() => {
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (!replayDraftRef.current && !pendingRef.current.length && !savingRef.current) return;
      event.preventDefault();
    };
    window.addEventListener('beforeunload', beforeUnload);
    return () => window.removeEventListener('beforeunload', beforeUnload);
  }, []);

  const select = useCallback((value: Assignment) => {
    setSelected(value);
    setCrop({ x: value.cropX, y: value.cropY, width: value.cropWidth, height: value.cropHeight });
  }, []);

  const locate = async () => {
    const raw = locateValue.trim();
    if (!raw) return;
    const slot = /^\d+$/.test(raw) ? Number(raw) : undefined;
    const values = await api.assignments(projectId, layoutId, undefined, slot === undefined ? raw : undefined, slot);
    if (!values[0]) { setError('没有找到对应的槽位或照片'); return; }
    const value = values[0];
    setAssignments((current) => current.some((item) => item.slotIndex === value.slotIndex)
      ? current : [...current, value]);
    await loadPhotoUrls([value]);
    select(value);
    canvasRef.current?.locate(value);
  };

  const applySwap = () => {
    if (!selected || !/^\d+$/.test(swapSlot)) return;
    enqueue({ type: 'SWAP', slotIndex: selected.slotIndex, secondarySlotIndex: Number(swapSlot) });
    setStatus('交换操作将在后台保存；视图将在保存后按需刷新');
  };
  const applyReplace = () => {
    if (!selected || !replacePhotoId.trim()) return;
    enqueue({ type: 'REPLACE', slotIndex: selected.slotIndex, photoId: replacePhotoId.trim() });
    setAssignments((current) => current.map((item) => item.slotIndex === selected.slotIndex
      ? { ...item, photoId: replacePhotoId.trim() } : item));
  };
  const applyCrop = () => {
    if (!selected) return;
    enqueue({ type: 'CROP', slotIndex: selected.slotIndex, cropX: crop.x, cropY: crop.y,
      cropWidth: crop.width, cropHeight: crop.height });
    setAssignments((current) => current.map((item) => item.slotIndex === selected.slotIndex
      ? { ...item, cropX: crop.x, cropY: crop.y, cropWidth: crop.width, cropHeight: crop.height } : item));
  };

  if (!projectId || !layoutId || !token) {
    return <main className="setup-panel"><h1>布局编辑器</h1>
      <p>请通过项目后台进入编辑器，或提供 URL 参数 <code>projectId</code>、<code>layoutId</code>，并在
        <code> localStorage['pics2text.token']</code> 中写入登录令牌。</p></main>;
  }
  if (!layout) return <main className="setup-panel"><h1>布局编辑器</h1><p>{error || status}</p></main>;
  const editable = layout.state === 'DRAFT';

  return <main className="editor-shell">
    <header className="topbar">
      <div><strong>{layout.name}</strong><span className={`state ${layout.state.toLowerCase()}`}>{layout.state}</span>
        <small>版本 {layout.currentVersionNumber} · 修订 {layout.currentRevision}</small></div>
      <div className="toolbar">
        <button onClick={() => canvasRef.current?.zoom(1.2)}>放大</button>
        <button onClick={() => canvasRef.current?.zoom(1 / 1.2)}>缩小</button>
        <button onClick={() => canvasRef.current?.fit()}>适应画布</button>
        <input value={locateValue} onChange={(event) => setLocateValue(event.target.value)}
          placeholder="槽位编号或照片 UUID" />
        <button onClick={() => void locate().catch((cause) => setError(messageOf(cause)))}>定位</button>
      </div>
    </header>
    <section className="workspace">
      <PixiLayoutCanvas ref={canvasRef} canvasWidth={layout.canvasWidth} canvasHeight={layout.canvasHeight}
        assignments={assignments} photoUrls={photoUrls} selectedSlot={selected?.slotIndex ?? null}
        onSelect={select} onViewport={debounceViewport(loadViewport)} />
      <aside className="inspector">
        <h2>槽位编辑</h2>
        {!selected && <p className="muted">点击画布中的照片开始编辑。</p>}
        {selected && <>
          <dl><dt>槽位</dt><dd>{selected.slotIndex}</dd><dt>照片</dt><dd title={selected.photoId}>{selected.photoId}</dd></dl>
          <label>与槽位交换<input disabled={!editable} inputMode="numeric" value={swapSlot}
            onChange={(event) => setSwapSlot(event.target.value)} /></label>
          <button disabled={!editable} onClick={applySwap}>交换</button>
          <label>替换为照片 UUID<input disabled={!editable} value={replacePhotoId}
            onChange={(event) => setReplacePhotoId(event.target.value)} /></label>
          <button disabled={!editable} onClick={applyReplace}>替换</button>
          <fieldset disabled={!editable}><legend>裁剪框（0–1）</legend>
            {(['x', 'y', 'width', 'height'] as const).map((key) => <label key={key}>{key}
              <input type="number" min="0" max="1" step="0.01" value={crop[key]}
                onChange={(event) => setCrop((current) => ({ ...current, [key]: Number(event.target.value) }))} />
            </label>)}
            <button onClick={applyCrop}>应用裁剪</button>
          </fieldset>
        </>}
      </aside>
    </section>
    <footer className="statusbar"><span>{status}{pendingCount ? `（待保存 ${pendingCount}）` : ''}</span>
      <span className="error">{error}</span><span>当前视口 {assignments.length} 个槽位</span></footer>
  </main>;
}

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : '未知错误';
}

function debounceViewport(callback: (bounds: ViewportQuery) => Promise<void>) {
  let timer = 0;
  return (bounds: ViewportQuery) => {
    window.clearTimeout(timer);
    timer = window.setTimeout(() => void callback(bounds), 120);
  };
}
