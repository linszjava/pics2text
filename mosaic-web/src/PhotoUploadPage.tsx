import { useState } from 'react';
import { InboxOutlined, UploadOutlined } from '@ant-design/icons';
import { App, Alert, Button, Card, Checkbox, Empty, Progress, Space, Table, Tag, Typography, Upload } from 'antd';
import type { UploadFile, UploadProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OperationsApi, type PhotoSummary } from './operationsApi';

type QueueStatus = 'WAITING' | 'HASHING' | 'UPLOADING' | 'SUCCEEDED' | 'DUPLICATE' | 'FAILED';
type QueueItem = { uid: string; name: string; size: number; status: QueueStatus; progress: number; message?: string };

export function PhotoUploadPage({ api, projectId, photos, onChanged }: { api: OperationsApi; projectId: string; photos: PhotoSummary[]; onChanged: (photos: PhotoSummary[]) => void }) {
  const { message } = App.useApp();
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [queue, setQueue] = useState<QueueItem[]>([]);
  const [authorized, setAuthorized] = useState(false);
  const [running, setRunning] = useState(false);
  const update = (uid: string, patch: Partial<QueueItem>) => setQueue((items) => items.map((item) => item.uid === uid ? { ...item, ...patch } : item));
  const accepted = files.filter((item) => item.originFileObj).map((item) => ({ uid: item.uid, file: item.originFileObj as File }));

  const upload = async () => {
    if (!authorized) { void message.warning('请先确认拥有照片使用授权'); return; }
    const items = accepted.map(({ uid, file }) => ({ uid, name: file.name, size: file.size, status: 'WAITING' as QueueStatus, progress: 0 }));
    setQueue(items); setRunning(true);
    const completed: PhotoSummary[] = [];
    for (const selected of accepted) {
      const { uid, file } = selected;
      let photoId = '';
      try {
        update(uid, { status: 'HASHING', progress: 5 });
        const sha256 = await hash(file);
        const contentType = supportedContentType(file);
        const initiated = await api.initiatePhotoUpload(projectId, { filename: file.name, contentType, byteSize: file.size, sha256, authorizationGranted: true });
        photoId = initiated.photo.id;
        if (initiated.duplicate) {
          update(uid, { status: 'DUPLICATE', progress: 100, message: '项目内已存在相同照片' });
          continue;
        }
        update(uid, { status: 'UPLOADING', progress: 12 });
        const parts: { partNumber: number; eTag: string }[] = [];
        if (initiated.directUpload) {
          await api.putPresigned(initiated.directUpload, file);
          update(uid, { progress: 88 });
        } else {
          const partSize = initiated.partSize;
          if (!partSize) throw new Error('服务端未返回分片大小');
          for (let index = 0; index < initiated.partCount; index += 1) {
            const partNumber = index + 1;
            const signed = await api.presignPhotoPart(projectId, photoId, partNumber);
            const eTag = await api.putPresigned(signed, file.slice(index * partSize, Math.min(file.size, partNumber * partSize)));
            if (!eTag) throw new Error('对象存储未返回 ETag，无法完成分片上传');
            parts.push({ partNumber, eTag });
            update(uid, { progress: 12 + Math.round((partNumber / initiated.partCount) * 76) });
          }
        }
        const result = await api.completePhotoUpload(projectId, photoId, parts);
        completed.push(result); update(uid, { status: 'SUCCEEDED', progress: 100 });
      } catch (cause) {
        update(uid, { status: 'FAILED', message: messageOf(cause) });
        if (photoId) await api.cancelPhotoUpload(projectId, photoId).catch(() => undefined);
      }
    }
    if (completed.length) onChanged([...completed, ...photos.filter((photo) => !completed.some((item) => item.id === photo.id))]);
    setRunning(false); void message.success('批量上传处理完成');
  };

  const uploadProps: UploadProps = {
    multiple: true, accept: 'image/jpeg,image/png,image/webp', fileList: files, disabled: running,
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
  return <Card title="照片批量上传" extra={<Tag color="blue">支持 JPEG / PNG / WebP</Tag>}>
    <Alert type="info" showIcon message="浏览器先计算 SHA-256，文件随后直接上传到对象存储；大文件会自动分片，业务服务不转发图片内容。" />
    <Upload.Dragger {...uploadProps} className="photo-uploader">
      <p className="ant-upload-drag-icon"><InboxOutlined /></p>
      <p className="ant-upload-text">点击或拖拽照片到此区域</p>
      <p className="ant-upload-hint">可以一次选择多张照片，重复内容会在上传前识别。</p>
    </Upload.Dragger>
    <Space className="upload-confirm" orientation="vertical" size="middle">
      <Checkbox checked={authorized} disabled={running} onChange={(event) => setAuthorized(event.target.checked)}>我确认已获得这些照片的存储、处理和展示授权</Checkbox>
      <Button type="primary" icon={<UploadOutlined />} loading={running} disabled={!accepted.length || !authorized} onClick={() => void upload()}>{accepted.length ? `上传 ${accepted.length} 张照片` : '选择照片后上传'}</Button>
    </Space>
    {!!queue.length && <Table className="ops-section" rowKey="uid" columns={columns} dataSource={queue} pagination={false} />}
    <Typography.Paragraph type="secondary" style={{ marginTop: 16 }}>上传完成后，后台会自动执行格式校验、缩略图生成、质量分析和重复检测，可在“照片审核”和“任务中心”查看结果。</Typography.Paragraph>
  </Card>;
}

function isSupported(file: File) { return ['image/jpeg', 'image/png', 'image/webp'].includes(file.type) && file.size > 0; }
function supportedContentType(file: File) { if (!isSupported(file)) throw new Error(`${file.name} 不是受支持的图片格式`); return file.type; }
async function hash(file: File) { const value = await crypto.subtle.digest('SHA-256', await file.arrayBuffer()); return [...new Uint8Array(value)].map((byte) => byte.toString(16).padStart(2, '0')).join(''); }
function formatBytes(value: number) { return value < 1024 * 1024 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`; }
function messageOf(cause: unknown) { return cause instanceof Error ? cause.message : '上传失败'; }
function UploadStatus({ value }: { value: QueueStatus }) {
  const copy = { WAITING: '等待', HASHING: '计算摘要', UPLOADING: '上传中', SUCCEEDED: '完成', DUPLICATE: '重复', FAILED: '失败' }[value];
  const color = value === 'SUCCEEDED' ? 'success' : value === 'FAILED' ? 'error' : value === 'DUPLICATE' ? 'warning' : 'processing';
  return <Tag color={color}>{copy}</Tag>;
}
