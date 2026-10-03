import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AimOutlined, CompressOutlined, LogoutOutlined, ZoomInOutlined, ZoomOutOutlined } from '@ant-design/icons';
import { Alert, Button, Empty, Input, InputNumber, Modal, Space, Tag, Typography } from 'antd';
import { LayoutApi, type ViewportQuery } from './api';
import { OperationsApi, type PhotoSummary } from './operationsApi';
import { authToken, logout } from './auth';
import { DraftRecovery, type PendingDraft } from './draftRecovery';
import { PixiLayoutCanvas, type CanvasHandle } from './PixiLayoutCanvas';
import type { ApiFailure, Assignment, EditCommand, EditableLayout } from './types';
import { statusLabel } from './uiLabels';

const AUTOSAVE_DELAY_MS = 1_200;

export function LayoutEditorPage() {
  const params = useMemo(() => new URLSearchParams(window.location.search), []);
  const projectId = params.get('projectId') ?? '';
  const layoutId = params.get('layoutId') ?? '';
  const token = authToken();
  const api = useMemo(() => new LayoutApi('', token), [token]);
  const photoApi = useMemo(() => new OperationsApi(), []);
  const recovery = useMemo(() => new DraftRecovery(localStorage,
    `pics2text.layout-draft.${projectId}.${layoutId}`), [projectId, layoutId]);
  const canvasRef = useRef<CanvasHandle>(null);
  const layoutRef = useRef<EditableLayout | null>(null);
  const pendingRef = useRef<EditCommand[]>([]);
  const replayDraftRef = useRef<PendingDraft | null>(null);
  const savingRef = useRef(false);
  const viewportSequence = useRef(0);
  const lastViewport = useRef<ViewportQuery | null>(null);
  const urlCache = useRef(new Map<string, string>());
  const [layout, setLayoutState] = useState<EditableLayout | null>(null);
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [photoUrls, setPhotoUrls] = useState(new Map<string, string>());
  const [selected, setSelected] = useState<Assignment | null>(null);
  const [pendingCount, setPendingCount] = useState(0);
  const [status, setStatus] = useState('正在加载布局…');
  const [error, setError] = useState('');
  const [locateValue, setLocateValue] = useState('');
  const [libraryPhotos, setLibraryPhotos] = useState<PhotoSummary[]>([]);
  const [newerLayout, setNewerLayout] = useState<EditableLayout | null>(null);
  const [replaceOpen, setReplaceOpen] = useState(false);
  const [photoSearch, setPhotoSearch] = useState('');
  const [unlocking, setUnlocking] = useState(false);
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
    lastViewport.current = bounds;
    const sequence = ++viewportSequence.current;
    if (bounds.maxX <= bounds.minX || bounds.maxY <= bounds.minY) {
      setAssignments([]);
      return;
    }
    try {
      const values = await api.assignments(projectId, layoutId, bounds);
      if (sequence !== viewportSequence.current) return;
      setAssignments(values);
      setSelected((current) => current ? values.find((item) => item.slotIndex === current.slotIndex) ?? current : null);
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
      if (lastViewport.current) await loadViewport(lastViewport.current);
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
  }, [api, layoutId, loadViewport, projectId, recovery, reload, setLayout]);

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
    if (!projectId || !token) return;
    void photoApi.photos(projectId).then(setLibraryPhotos).catch((cause) => setError(messageOf(cause)));
  }, [photoApi, projectId, token]);
  useEffect(() => {
    if (!layout || !projectId) return;
    void photoApi.layouts(projectId).then((values) => setNewerLayout(
      values.find((item) => item.id !== layout.id && item.maskId === layout.maskId && item.state === 'DRAFT') ?? null,
    )).catch(() => undefined);
  }, [layout?.id, layout?.maskId, photoApi, projectId]);

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
  const applyReplace = (photoId: string) => {
    if (!selected || selected.photoId === photoId) return;
    enqueue({ type: 'REPLACE', slotIndex: selected.slotIndex, photoId });
    setAssignments((current) => current.map((item) => item.slotIndex === selected.slotIndex
      ? { ...item, photoId } : item));
    setSelected({ ...selected, photoId });
    setReplaceOpen(false);
  };
  const unlock = async () => {
    if (!layout || layout.state !== 'LOCKED') return;
    setUnlocking(true);
    try { setLayout(await api.unlockLayout(projectId, layoutId, layout.lockVersion)); setStatus('布局已解锁，可以更换照片'); }
    catch (cause) { setError(messageOf(cause)); }
    finally { setUnlocking(false); }
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
  const eligiblePhotos = libraryPhotos.filter((photo) => photo.status === 'AVAILABLE' && photo.analysisStatus === 'SUCCEEDED');
  const matchingPhotos = eligiblePhotos.filter((photo) => photo.originalFilename.toLocaleLowerCase().includes(photoSearch.toLocaleLowerCase())).slice(0, 60);
  const selectedPhoto = selected ? libraryPhotos.find((photo) => photo.id === selected.photoId) : null;

  return <main className="editor-shell">
    <header className="topbar">
      <div><Typography.Title level={5}>{layout.name}</Typography.Title>
        <Tag color={layout.state === 'PUBLISHED' ? 'success' : layout.state === 'LOCKED' ? 'warning' : 'processing'}>{statusLabel(layout.state)}</Tag>
        <Typography.Text type="secondary">版本 {layout.currentVersionNumber} · 修订 {layout.currentRevision}</Typography.Text></div>
      <Space className="toolbar" wrap>
        <Button href="?view=dashboard">工作台</Button>
        {layout.state === 'LOCKED' && <Button loading={unlocking} onClick={() => void unlock()}>解锁并编辑</Button>}
        <Button type="primary" href={`?view=dashboard&step=surface&projectId=${projectId}`}>下一步：三维效果</Button>
        <Button icon={<ZoomInOutlined />} onClick={() => canvasRef.current?.zoom(1.2)}>放大</Button>
        <Button icon={<ZoomOutOutlined />} onClick={() => canvasRef.current?.zoom(1 / 1.2)}>缩小</Button>
        <Button icon={<CompressOutlined />} onClick={() => canvasRef.current?.fit()}>适应画布</Button>
        <Input value={locateValue} onChange={(event) => setLocateValue(event.target.value)}
          placeholder="槽位编号或照片 UUID" />
        <Button icon={<AimOutlined />} onClick={() => void locate().catch((cause) => setError(messageOf(cause)))}>定位</Button>
        <Button icon={<LogoutOutlined />} onClick={() => void logout()}>退出</Button>
      </Space>
    </header>
    <section className="workspace">
      <PixiLayoutCanvas ref={canvasRef} canvasWidth={layout.canvasWidth} canvasHeight={layout.canvasHeight}
        assignments={assignments} photoUrls={photoUrls} selectedSlot={selected?.slotIndex ?? null}
        onSelect={select} onViewport={debounceViewport(loadViewport)} />
      <aside className="inspector">
        <Typography.Title level={5}>槽位编辑</Typography.Title>
        {newerLayout && layout.state !== 'DRAFT' && <Alert type="warning" showIcon title="正在查看旧版排版"
          description={<a href={`?view=editor&projectId=${projectId}&layoutId=${newerLayout.id}`}>打开使用新照片生成的可编辑排版</a>} />}
        {!selected && <Typography.Paragraph type="secondary">点击画布中的照片开始编辑。</Typography.Paragraph>}
        {selected && <>
          <dl><dt>槽位</dt><dd>{selected.slotIndex}</dd><dt>照片</dt><dd title={selectedPhoto?.originalFilename ?? selected.photoId}>{selectedPhoto?.originalFilename ?? selected.photoId}</dd></dl>
          {photoUrls.get(selected.photoId) && <img className="inspector-photo" src={photoUrls.get(selected.photoId)} alt={selectedPhoto?.originalFilename ?? '当前照片'} />}
          {!editable && <Alert type="info" showIcon title="此布局已锁定" description="点击上方“解锁并编辑”后，可以更换槽位照片。" />}
          <label>与槽位交换<Input disabled={!editable} inputMode="numeric" value={swapSlot}
            onChange={(event) => setSwapSlot(event.target.value)} /></label>
          <Button block disabled={!editable} onClick={applySwap}>交换</Button>
          <Button block type="primary" disabled={!editable} onClick={() => {
            setPhotoSearch(''); setReplaceOpen(true);
            void photoApi.photos(projectId).then(setLibraryPhotos).catch((cause) => setError(messageOf(cause)));
          }}>从素材库更换照片</Button>
          <fieldset disabled={!editable}><legend>裁剪框（0–1）</legend>
            {(['x', 'y', 'width', 'height'] as const).map((key) => <label key={key}>{key}
              <InputNumber min={0} max={1} step={0.01} value={crop[key]}
                onChange={(value) => setCrop((current) => ({ ...current, [key]: value ?? 0 }))} />
            </label>)}
            <Button block type="primary" onClick={applyCrop}>应用裁剪</Button>
          </fieldset>
        </>}
      </aside>
    </section>
    <Modal title={`替换槽位 ${selected?.slotIndex ?? ''} 的照片`} open={replaceOpen} onCancel={() => setReplaceOpen(false)} footer={null} width={760}>
      <Input.Search placeholder="按照片名称搜索" value={photoSearch} onChange={(event) => setPhotoSearch(event.target.value)} allowClear />
      <p className="photo-picker-hint">可选择 {eligiblePhotos.length} 张已完成分析的照片；点击缩略图即可替换并自动保存。</p>
      {matchingPhotos.length ? <div className="photo-picker-grid">
        {replaceOpen && matchingPhotos.map((photo) => <PhotoChoice key={photo.id} api={photoApi} projectId={projectId} photo={photo}
          active={photo.id === selected?.photoId} onChoose={() => applyReplace(photo.id)} />)}
      </div> : <Empty description={eligiblePhotos.length ? '没有符合搜索条件的照片' : '照片分析完成后才能选择'} />}
      {matchingPhotos.length === 60 && <p className="photo-picker-hint">仅显示前 60 张，请输入名称缩小范围。</p>}
    </Modal>
    <footer className="statusbar"><Typography.Text>{status}{pendingCount ? `（待保存 ${pendingCount}）` : ''}</Typography.Text>
      <span>{error && <Alert type="error" showIcon title={error} />}</span>
      <Typography.Text type="secondary">当前视口 {assignments.length} 个槽位</Typography.Text></footer>
  </main>;
}

function PhotoChoice({ api, projectId, photo, active, onChoose }: {
  api: OperationsApi; projectId: string; photo: PhotoSummary; active: boolean; onChoose: () => void;
}) {
  const [url, setUrl] = useState('');
  useEffect(() => {
    let live = true;
    void api.photoPreview(projectId, photo.id).then((preview) => {
      if (live) setUrl(preview.thumbnail.url);
    }).catch(() => undefined);
    return () => { live = false; };
  }, [api, projectId, photo.id]);
  return <button type="button" className={`photo-picker-item${active ? ' active' : ''}`} disabled={active} onClick={onChoose}>
    {url ? <img src={url} alt="" loading="lazy" /> : <span className="photo-picker-placeholder">预览加载中</span>}
    <span title={photo.originalFilename}>{photo.originalFilename}</span>
  </button>;
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
