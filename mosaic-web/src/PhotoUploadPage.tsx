import { useEffect, useRef, useState } from 'react';
import { DeleteOutlined, EditOutlined, EyeOutlined, InboxOutlined, PictureOutlined, UploadOutlined } from '@ant-design/icons';
import { App, Button, Card, Empty, Input, Modal, Popconfirm, Progress, Skeleton, Table, Tag, Upload } from 'antd';
import type { UploadFile, UploadProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ApiRequestError, OperationsApi, type PhotoAnalysisResult, type PhotoSummary } from './operationsApi';
import { MAX_CONCURRENT_UPLOADS, retryWithBackoff, runBoundedQueue, summarizeUploadBatch, type UploadBatchOutcome } from './uploadQueue';

type QueueStatus = 'WAITING' | 'RECEIVING' | 'QUEUED' | 'BACKEND_UPLOADING' | 'SUCCEEDED' | 'DUPLICATE' | 'FAILED';
type QueueItem = { uid: string; name: string; size: number; status: QueueStatus; progress: number; message?: string };
type UploadQueueResult = { uid: string; outcome: UploadBatchOutcome; photo?: PhotoSummary };

export function PhotoUploadPage({ api, projectId, photos, onChanged }: { api: OperationsApi; projectId: string; photos: PhotoSummary[]; onChanged: (photos: PhotoSummary[]) => void }) {
  const { message } = App.useApp();
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [queue, setQueue] = useState<QueueItem[]>([]);
  const [running, setRunning] = useState(false);
  const runningRef = useRef(false);
  const availablePhotos = photos.filter((photo) => photo.status === 'AVAILABLE');
  const update = (uid: string, patch: Partial<QueueItem>) => setQueue((items) => items.map((item) => item.uid === uid ? { ...item, ...patch } : item));
  const accepted = files.filter((item) => item.originFileObj).map((item) => ({ uid: item.uid, file: item.originFileObj as File }));

  const upload = async () => {
    if (runningRef.current) return;
    const selectedItems = accepted;
    if (!selectedItems.length) return;
    runningRef.current = true;
    setRunning(true);
    setQueue(selectedItems.map(({ uid, file }) => ({ uid, name: file.name, size: file.size, status: 'WAITING', progress: 0 })));
    try {
      const uploadOne = async ({ uid, file }: (typeof selectedItems)[number]): Promise<UploadQueueResult> => {
        try {
          supportedContentType(file);
          update(uid, { status: 'RECEIVING', progress: 15, message: '正在发送到业务服务' });
          const accepted = await retryWithBackoff(
            () => api.relayPhotoUpload(projectId, file), capacityRetryDelay,
            { maxAttempts: 8, baseDelayMs: 500, maxDelayMs: 5000 },
          );
          if (accepted.duplicate) {
            update(uid, { status: 'DUPLICATE', progress: 100, message: '项目内已存在相同照片' });
            return { uid, outcome: 'DUPLICATE' as UploadBatchOutcome };
          }
          if (!accepted.taskId) throw new Error('服务端未返回上传任务');
          update(uid, { status: 'QUEUED', progress: 55, message: '服务端已接收，等待上传' });
          let current = accepted;
          while (current.taskStatus !== 'SUCCEEDED' && current.taskStatus !== 'FAILED') {
            await delay(700);
            current = await api.relayPhotoUploadStatus(projectId, accepted.taskId);
            update(uid, current.taskStatus === 'BACKEND_UPLOADING'
              ? { status: 'BACKEND_UPLOADING', progress: 82, message: '服务端正在写入对象存储' }
              : { status: 'QUEUED', progress: 60, message: current.taskStatus === 'RETRY_WAIT' ? '后端上传重试等待中' : '等待后端上传' });
          }
          if (current.taskStatus === 'FAILED') throw new Error(current.failureMessage ?? '后端上传失败');
          const photo = current.photo;
          update(uid, { status: 'SUCCEEDED', progress: 100 });
          return { uid, outcome: 'SUCCEEDED' as UploadBatchOutcome, photo };
        } catch (cause) {
          update(uid, { status: 'FAILED', message: messageOf(cause) });
          return { uid, outcome: 'FAILED' as UploadBatchOutcome };
        }
      };
      const settled = await runBoundedQueue(selectedItems, MAX_CONCURRENT_UPLOADS, uploadOne);
      const results = settled.map((result, index) => {
        if (result.status === 'fulfilled') return result.value;
        const uid = selectedItems[index].uid;
        update(uid, { status: 'FAILED', message: messageOf(result.reason) });
        return { uid, outcome: 'FAILED' as UploadBatchOutcome };
      });
      const completed = results.flatMap((result) => result.photo ? [result.photo] : []);
      const processedUids = new Set(results.filter((result) => result.outcome !== 'FAILED').map((result) => result.uid));
      if (completed.length) {
        onChanged([...completed, ...photos.filter((photo) => !completed.some((item) => item.id === photo.id))]);
      }
      setFiles((current) => current.filter((file) => !processedUids.has(file.uid)));
      const summary = summarizeUploadBatch(results.map((result) => result.outcome));
      if (summary.kind === 'error') void message.error(summary.content);
      else if (summary.kind === 'warning') void message.warning(summary.content);
      else void message.success(summary.content);
    } finally {
      runningRef.current = false;
      setRunning(false);
    }
  };

  const uploadProps: UploadProps = {
    multiple: true, maxCount: 1000, accept: 'image/jpeg,image/png,image/webp', fileList: files, disabled: running,
    beforeUpload: () => false,
    onChange: ({ fileList }) => setFiles(fileList.filter((item) => item.originFileObj && isSupported(item.originFileObj))),
    onRemove: (file) => { setFiles((current) => current.filter((item) => item.uid !== file.uid)); return true; },
  };
  const columns: ColumnsType<QueueItem> = [
    { title: '文件', render: (_, row) => <div className="table-primary"><strong>{row.name}</strong><small>{formatBytes(row.size)}</small></div> },
    { title: '状态', dataIndex: 'status', width: 120, render: (value) => <UploadStatus value={value} /> },
    { title: '进度', dataIndex: 'progress', width: 220, render: (value, row) => <Progress percent={value} size="small" status={row.status === 'FAILED' ? 'exception' : row.status === 'SUCCEEDED' ? 'success' : 'active'} /> },
    { title: '说明', dataIndex: 'message', ellipsis: true, render: (value) => value ?? '—' },
  ];

  if (!projectId) return <Card><Empty description="请先选择项目" /></Card>;
  return <section className="photo-upload-workspace">
    <div className="photo-upload-heading"><div><span className="section-kicker">照片素材 / 03</span><h2>让每一张照片，成为作品的一部分。</h2><p>把照片放进来，系统会自动处理；下一步就能预览照片墙。</p></div>
      <div className="photo-upload-count"><strong>{availablePhotos.length}</strong><span>张已上传</span></div></div>
    <div className="photo-upload-panel">
      <div className="photo-upload-controls"><span className="photo-upload-overline">上传照片</span><h3>从这里开始</h3><p>选择你想放进照片墙的照片。可以一次上传多张，重复照片会自动识别。</p>
        <Upload.Dragger {...uploadProps} className="photo-uploader">
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p className="ant-upload-text">点击选择，或拖拽照片到这里</p>
          <p className="ant-upload-hint">支持 JPEG、PNG、WebP</p>
        </Upload.Dragger>
        <div className="upload-confirm"><Button type="primary" icon={<UploadOutlined />} loading={running} disabled={!accepted.length} onClick={() => void upload()}>{accepted.length ? `上传 ${accepted.length} 张照片` : '选择照片后上传'}</Button></div>
        <p className="photo-upload-note">单次可选择 500 或 1000 张；每张照片独立发送，服务端按固定并发上传并可在重启后继续。</p>
      </div>
    </div>
    {!!queue.length && <Table className="ops-section" rowKey="uid" columns={columns} dataSource={queue} pagination={false} />}
    <section className="photo-library" aria-label="已上传照片">
      <div className="photo-library-heading"><div><span className="section-kicker">照片素材库</span><h3>已上传的照片</h3></div><span>共 {availablePhotos.length} 张</span></div>
      {availablePhotos.length ? <div className="photo-library-grid">
        {availablePhotos.map((photo) => <UploadedPhoto key={photo.id} api={api} projectId={projectId} photo={photo}
          onUpdated={(updated) => onChanged(photos.map((item) => item.id === updated.id ? updated : item))}
          onDeleted={() => onChanged(photos.filter((item) => item.id !== photo.id))} />)}
      </div> : <Empty description="还没有上传照片，选好照片后点击上传即可在这里看到" />}
    </section>
  </section>;
}

function isCapacityRejection(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError
    && error.status === 429
    && (error.code === 'PHOTO_UPLOAD_CAPACITY_EXCEEDED' || error.code === 'PHOTO_RELAY_CAPACITY_EXCEEDED');
}

function capacityRetryDelay(error: unknown): number | null {
  return isCapacityRejection(error) ? error.retryAfterMs ?? 1000 : null;
}

function UploadedPhoto({ api, projectId, photo, onUpdated, onDeleted }: {
  api: OperationsApi; projectId: string; photo: PhotoSummary;
  onUpdated: (photo: PhotoSummary) => void; onDeleted: () => void;
}) {
  const { message } = App.useApp();
  const [url, setUrl] = useState('');
  const [fullUrl, setFullUrl] = useState('');
  const [failed, setFailed] = useState(false);
  const [viewing, setViewing] = useState(false);
  const [renaming, setRenaming] = useState(false);
  const [filename, setFilename] = useState(photo.originalFilename);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [analysis, setAnalysis] = useState<PhotoAnalysisResult['analysis'] | null>(null);
  const [analysisError, setAnalysisError] = useState('');
  useEffect(() => {
    let active = true;
    setUrl(''); setFullUrl(''); setFailed(false);
    void api.photoPreview(projectId, photo.id).then((preview) => {
      if (active) { setUrl(preview.thumbnail.url); setFullUrl(preview.preview.url); }
    }).catch(() => { if (active) setFailed(true); });
    return () => { active = false; };
  }, [api, projectId, photo.id]);
  useEffect(() => {
    if (!viewing || photo.analysisStatus !== 'SUCCEEDED') return;
    let active = true;
    setAnalysisError('');
    void api.photoAnalysis(projectId, photo.id).then((result) => {
      if (active) setAnalysis(result.analysis);
    }).catch((error) => { if (active) setAnalysisError(messageOf(error)); });
    return () => { active = false; };
  }, [api, projectId, photo.id, photo.analysisStatus, viewing]);
  const saveName = async () => {
    const normalized = filename.trim();
    if (!normalized || normalized.length > 255 || /[\\/\x00-\x1f\x7f]/.test(normalized)) {
      void message.error('请输入有效的照片名称（最多255字，不能包含路径字符）'); return;
    }
    if (normalized === photo.originalFilename) { setRenaming(false); return; }
    setSaving(true);
    try {
      onUpdated(await api.renamePhoto(projectId, photo.id, normalized));
      setRenaming(false); void message.success('照片名称已更新');
    } catch (error) { void message.error(messageOf(error)); }
    finally { setSaving(false); }
  };
  const deletePhoto = async () => {
    setDeleting(true);
    try {
      await api.deletePhoto(projectId, photo.id);
      onDeleted(); void message.success('照片已删除');
    } catch (error) { void message.error(messageOf(error)); setDeleting(false); }
  };
  return <article className="photo-library-card">
    <div className="photo-library-image">
      {url ? <img src={url} alt={photo.originalFilename} loading="lazy" onError={() => { setUrl(''); setFailed(true); }} />
        : failed ? <div className="photo-library-placeholder"><PictureOutlined /><span>预览暂不可用</span></div>
          : <Skeleton.Image active />}
    </div>
    <div className="photo-library-caption"><strong title={photo.originalFilename}>{photo.originalFilename}</strong><span>{formatBytes(photo.byteSize)}</span></div>
    <div className="photo-library-actions">
      <Button size="small" icon={<EyeOutlined />} onClick={() => setViewing(true)}>查看</Button>
      <Button size="small" icon={<EditOutlined />} onClick={() => { setFilename(photo.originalFilename); setRenaming(true); }}>重命名</Button>
      <Popconfirm title="删除这张照片？" description="原图会被删除；如果已有排版使用它，相关效果也可能受影响。" okText="删除" cancelText="取消"
        okButtonProps={{ danger: true, loading: deleting }} onConfirm={() => void deletePhoto()}>
        <Button size="small" danger icon={<DeleteOutlined />} loading={deleting}>删除</Button>
      </Popconfirm>
    </div>
    <Modal title={photo.originalFilename} open={viewing} onCancel={() => setViewing(false)} footer={null} width={800}>
      {fullUrl && <img className="photo-library-full-image" src={fullUrl} alt={photo.originalFilename} />}
      <p className="photo-library-details">{formatBytes(photo.byteSize)} · 上传于 {new Date(photo.createdAt).toLocaleString('zh-CN')}</p>
      <Tag color={photo.analysisStatus === 'SUCCEEDED' ? 'success' : photo.analysisStatus === 'FAILED' ? 'error' : 'processing'}>
        {photo.analysisStatus === 'SUCCEEDED' ? '分析完成，可用于排版' : photo.analysisStatus === 'FAILED' ? '分析失败' : '分析中'}
      </Tag>
      {photo.analysisStatus === 'SUCCEEDED' ? <section className="photo-analysis-panel" aria-label="照片分析结果">
        <h4>照片分析结果</h4>
        {analysis ? <>
          <div className="photo-analysis-metrics">
            <div><span>亮度</span><strong>{analysis.brightness.toFixed(1)} / 255</strong><small>默认建议区间 20–235</small>
              <Progress percent={Math.round(analysis.brightness / 255 * 100)} showInfo={false}
                status={analysis.brightness >= 20 && analysis.brightness <= 235 ? 'success' : 'exception'} /></div>
            <div><span>清晰度</span><strong>{analysis.sharpness.toFixed(1)}</strong><small>默认建议不低于 35，越高通常越清晰</small></div>
            <div><span>对比度</span><strong>{analysis.contrast.toFixed(1)}</strong><small>灰度标准差，仅供比较</small></div>
            <div><span>平均颜色 Lab</span><strong>{analysis.labL.toFixed(0)} / {analysis.labA.toFixed(0)} / {analysis.labB.toFixed(0)}</strong><small>用于排版时匹配明暗与颜色</small></div>
          </div>
          <p className="photo-analysis-note">建议裁剪区域：左 {Math.round(analysis.cropX * 100)}%、上 {Math.round(analysis.cropY * 100)}%、宽 {Math.round(analysis.cropWidth * 100)}%、高 {Math.round(analysis.cropHeight * 100)}%。
            {analysis.faceCount > 0 ? ` 检测到 ${analysis.faceCount} 张人脸。` : ''}</p>
          <p className="photo-analysis-note">质量检查：{analysisDecision(analysis.autoReviewDecision)}。{analysisReasons(analysis.reviewReasonsJson)}</p>
          <p className="photo-analysis-disclaimer">亮度取灰度均值、清晰度取拉普拉斯方差；这些指标用于自动排版参考，不是照片审美评分。</p>
        </> : <p className="photo-analysis-note">{analysisError || '正在读取分析数据…'}</p>}
      </section> : <p className="photo-analysis-note">{photo.analysisStatus === 'FAILED' ? photo.analysisErrorMessage || '照片分析失败' : '照片正在分析，完成后这里会显示具体指标。'}</p>}
    </Modal>
    <Modal title="重命名照片" open={renaming} onCancel={() => setRenaming(false)} onOk={() => void saveName()}
      okText="保存" confirmLoading={saving} okButtonProps={{ disabled: !filename.trim() }}>
      <Input value={filename} maxLength={255} onChange={(event) => setFilename(event.target.value)} onPressEnter={() => void saveName()} aria-label="照片名称" />
    </Modal>
  </article>;
}

function isSupported(file: File) { return ['image/jpeg', 'image/png', 'image/webp'].includes(file.type) && file.size > 0; }
function supportedContentType(file: File) { if (!isSupported(file)) throw new Error(`${file.name} 不是受支持的图片格式`); return file.type; }
function delay(ms: number) { return new Promise<void>((resolve) => window.setTimeout(resolve, ms)); }
function formatBytes(value: number) { return value < 1024 * 1024 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`; }
function messageOf(cause: unknown) { return cause instanceof Error ? cause.message : '上传失败'; }
function analysisDecision(value: string) {
  return { AUTO_APPROVED: '达到自动质量建议', NEEDS_REVIEW: '建议人工检查', AUTO_REJECTED: '质量风险较高' }[value] ?? '已完成';
}
function analysisReasons(value: string) {
  try { return (JSON.parse(value) as string[]).join('；'); } catch { return ''; }
}
function UploadStatus({ value }: { value: QueueStatus }) {
  const copy = { WAITING: '等待', RECEIVING: '接收中', QUEUED: '已排队', BACKEND_UPLOADING: '后端上传中', SUCCEEDED: '完成', DUPLICATE: '重复', FAILED: '失败' }[value];
  const color = value === 'SUCCEEDED' ? 'success' : value === 'FAILED' ? 'error' : value === 'DUPLICATE' ? 'warning' : 'processing';
  return <Tag color={color}>{copy}</Tag>;
}
