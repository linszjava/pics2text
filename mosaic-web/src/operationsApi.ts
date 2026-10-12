import { authToken, clearSession, redirectToLogin, type AuthenticatedUser } from './auth';
import type { EditableLayout } from './types';

export interface ProjectSummary {
  id: string;
  name: string;
  status: string;
  description: string;
  createdAt: string;
  updatedAt: string;
  activeLayoutId: string | null;
  activeTextMaskId: string | null;
}

export interface PhotoSummary {
  id: string;
  originalFilename: string;
  byteSize: number;
  status: string;
  analysisStatus: string;
  duplicateOf: string | null;
  failureCode?: string | null;
  failureMessage?: string | null;
  analysisErrorCode?: string | null;
  analysisErrorMessage?: string | null;
  retentionUntil?: string | null;
  createdAt: string;
}

export interface PhotoAnalysisResult {
  analysis: {
    sharpness: number; brightness: number; contrast: number;
    labL: number; labA: number; labB: number;
    faceCount: number; cropX: number; cropY: number; cropWidth: number; cropHeight: number;
    autoReviewDecision: string; reviewReasonsJson: string; analyzedAt: string;
  };
}

export interface JobSummary {
  id: string;
  type: string;
  status: string;
  progress: number;
  currentAttempt: number;
  maxAttempts: number;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface RenderSummary {
  id: string;
  layoutId: string;
  status: string;
  format: string;
  dpi: number;
  outputByteSize: number | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface CreateRenderInput {
  layoutId: string; dpi: number; bleedMm: number; tileSize: number;
  compression: 'ZSTD' | 'LZW' | 'DEFLATE'; cropMarks: boolean; registrationMarks: boolean;
}

export interface FontAsset {
  id: string;
  family: string;
  version: string;
  license: string;
  minimumWeight: number;
  maximumWeight: number;
}

export interface TextMaskSummary {
  id: string;
  name: string;
  text: string;
  direction: 'HORIZONTAL' | 'VERTICAL';
  canvasWidth: number;
  canvasHeight: number;
  slotCount: number;
  status: string;
  errorMessage: string | null;
  createdAt: string;
}

export interface AutoLayoutSummary {
  id: string;
  maskId: string;
  name: string;
  status: string;
  candidateCount: number;
  slotCount: number;
  assignedCount: number;
  uniquePhotoCount: number | null;
  runtimeMillis: number | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface CreateTextMaskInput {
  name: string; text: string; direction: 'HORIZONTAL' | 'VERTICAL'; fontId: string;
  fontSize: number; fontWeight: number; letterSpacing: number; lineSpacing: number;
  canvasWidth: number; canvasHeight: number; padding: number;
  gridStrategy: 'REGULAR' | 'STAGGERED'; edgePolicy: 'STRICT' | 'INCLUDE_PARTIAL';
  cellWidth: number; cellHeight: number; gap: number; coverageThreshold: number;
}

export interface CreateAutoLayoutInput {
  name: string; maskId: string; randomSeed: number; allowPhotoReuse: boolean; maxPhotoReuse: number;
  targetLabL: number; targetLabA: number; targetLabB: number; candidateWindow: number;
  optimizationIterations: number;
  weights: { quality: number; brightness: number; color: number; crop: number; duplicate: number; adjacent: number };
}

export interface PresignedRequest { url: string; method: 'PUT' | 'GET'; headers: Record<string, string[]> }
export interface PhotoPreview { thumbnail: PresignedRequest; preview: PresignedRequest }
export interface InitiatedPhotoUpload {
  photo: PhotoSummary; duplicate: boolean; directUpload: PresignedRequest | null;
  partSize: number | null; partCount: number; expiresAt: string;
}
export interface RelayPhotoUpload {
  taskId: string | null; taskStatus: 'RECEIVED' | 'QUEUED' | 'BACKEND_UPLOADING' | 'RETRY_WAIT' | 'SUCCEEDED' | 'FAILED';
  attemptCount: number; maxAttempts: number; failureCode: string | null; failureMessage: string | null;
  duplicate: boolean; photo: PhotoSummary;
}

export class ApiRequestError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string | null,
    readonly retryAfterMs: number | null,
  ) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

export class OperationsApi {
  me(): Promise<AuthenticatedUser> { return this.request('/api/v1/auth/me'); }
  projects(): Promise<ProjectSummary[]> { return this.request('/api/v1/projects'); }
  createProject(name: string): Promise<ProjectSummary> {
    return this.request('/api/v1/projects', { method: 'POST', body: JSON.stringify({ name }) });
  }
  setProjectArchived(projectId: string, archived: boolean): Promise<ProjectSummary> {
    return this.request(`/api/v1/projects/${projectId}/${archived ? 'archive' : 'activate'}`, { method: 'POST' });
  }
  selectTextMask(projectId: string, maskId: string): Promise<ProjectSummary> {
    return this.request(`/api/v1/projects/${projectId}/active-text-mask`, {
      method: 'PUT', body: JSON.stringify({ maskId }),
    });
  }
  async photos(projectId: string): Promise<PhotoSummary[]> {
    const all: PhotoSummary[] = [];
    const pageSize = 100;
    for (let page = 0; ; page += 1) {
      const batch = await this.request<PhotoSummary[]>(`/api/v1/projects/${projectId}/photos?page=${page}&size=${pageSize}`);
      all.push(...batch);
      if (batch.length < pageSize) return all;
    }
  }
  initiatePhotoUpload(projectId: string, input: { filename: string; contentType: string; byteSize: number; sha256: string; authorizationGranted: boolean }): Promise<InitiatedPhotoUpload> {
    return this.request(`/api/v1/projects/${projectId}/photos/uploads`, { method: 'POST', body: JSON.stringify(input) });
  }
  presignPhotoPart(projectId: string, photoId: string, partNumber: number): Promise<PresignedRequest> {
    return this.request(`/api/v1/projects/${projectId}/photos/${photoId}/uploads/parts/${partNumber}/presign`, { method: 'POST' });
  }
  completePhotoUpload(projectId: string, photoId: string, parts: { partNumber: number; eTag: string }[]): Promise<PhotoSummary> {
    return this.request(`/api/v1/projects/${projectId}/photos/${photoId}/uploads/complete`, { method: 'POST', body: JSON.stringify({ parts }) });
  }
  async cancelPhotoUpload(projectId: string, photoId: string): Promise<void> {
    await this.request(`/api/v1/projects/${projectId}/photos/${photoId}/uploads`, { method: 'DELETE' });
  }
  async putPresigned(request: PresignedRequest, body: Blob): Promise<string | null> {
    const headers = new Headers();
    Object.entries(request.headers).forEach(([name, values]) => values.forEach((value) => headers.append(name, value)));
    const response = await fetch(request.url, { method: 'PUT', headers, body });
    if (!response.ok) throw new Error(`对象存储上传失败：${response.status}`);
    return response.headers.get('etag');
  }
  async relayPhotoUpload(projectId: string, file: File): Promise<RelayPhotoUpload> {
    const body = new FormData(); body.append('file', file, file.name);
    return this.handle(await fetch(`/api/v1/projects/${projectId}/photos/relay-uploads`, {
      method: 'POST', headers: { Authorization: `Bearer ${authToken()}` }, body,
    }));
  }
  relayPhotoUploadStatus(projectId: string, taskId: string): Promise<RelayPhotoUpload> {
    return this.request(`/api/v1/projects/${projectId}/photos/relay-uploads/${taskId}`);
  }
  photoPreview(projectId: string, photoId: string): Promise<PhotoPreview> {
    return this.request(`/api/v1/projects/${projectId}/photos/${photoId}/preview-urls`, { method: 'POST' });
  }
  photoAnalysis(projectId: string, photoId: string): Promise<PhotoAnalysisResult> {
    return this.request(`/api/v1/projects/${projectId}/photos/${photoId}/analysis`);
  }
  renamePhoto(projectId: string, photoId: string, filename: string): Promise<PhotoSummary> {
    return this.request(`/api/v1/projects/${projectId}/photos/${photoId}`, {
      method: 'PATCH', body: JSON.stringify({ filename }),
    });
  }
  withdrawPhoto(projectId: string, photoId: string): Promise<PhotoSummary> {
    return this.request(`/api/v1/projects/${projectId}/photos/${photoId}/withdraw`, { method: 'POST' });
  }
  async deletePhoto(projectId: string, photoId: string): Promise<void> {
    await this.request(`/api/v1/projects/${projectId}/photos/${photoId}`, { method: 'DELETE' });
  }
  jobs(projectId: string): Promise<JobSummary[]> {
    return this.request(`/api/v1/projects/${projectId}/jobs?page=0&size=100`);
  }
  retryJob(projectId: string, jobId: string): Promise<JobSummary> {
    return this.request(`/api/v1/projects/${projectId}/jobs/${jobId}/retry`, { method: 'POST' });
  }
  cancelJob(projectId: string, jobId: string): Promise<JobSummary> {
    return this.request(`/api/v1/projects/${projectId}/jobs/${jobId}/cancel`, { method: 'POST' });
  }
  renders(projectId: string): Promise<RenderSummary[]> {
    return this.request(`/api/v1/projects/${projectId}/print-renders?page=0&size=100`);
  }
  createRender(projectId: string, input: CreateRenderInput): Promise<{ render: RenderSummary; jobId: string; existing: boolean }> {
    return this.request(`/api/v1/projects/${projectId}/print-renders`, { method: 'POST', body: JSON.stringify(input) });
  }
  decideRender(projectId: string, renderId: string, decision: 'APPROVED' | 'REJECTED', note: string): Promise<RenderSummary> {
    return this.request(`/api/v1/projects/${projectId}/print-renders/${renderId}/decision`, {
      method: 'POST', body: JSON.stringify({ decision, note }),
    });
  }
  renderDownload(projectId: string, renderId: string): Promise<{ url: string }> {
    return this.request(`/api/v1/projects/${projectId}/print-renders/${renderId}/download-url`, { method: 'POST' });
  }
  layouts(projectId: string): Promise<EditableLayout[]> {
    return this.request(`/api/v1/projects/${projectId}/editable-layouts?page=0&size=100`);
  }
  createEditableLayout(projectId: string, sourceAutoLayoutId: string, name: string): Promise<EditableLayout> {
    return this.request(`/api/v1/projects/${projectId}/editable-layouts`, {
      method: 'POST', body: JSON.stringify({ sourceAutoLayoutId, name }),
    });
  }
  lockEditableLayout(projectId: string, layoutId: string, expectedLockVersion: number): Promise<{ layout: EditableLayout }> {
    return this.request(`/api/v1/projects/${projectId}/editable-layouts/${layoutId}/lock`, {
      method: 'POST', body: JSON.stringify({ expectedLockVersion, note: '进入三维预览' }),
    });
  }
  surfaceTemplates(projectId: string): Promise<Array<{ template: { id: string; name: string; diameterMm: number; axialLengthMm: number } }>> {
    return this.request(`/api/v1/projects/${projectId}/surface-templates?page=0&size=100`);
  }
  createSurfaceTemplate(projectId: string): Promise<{ template: { id: string; name: string; diameterMm: number; axialLengthMm: number } }> {
    return this.request(`/api/v1/projects/${projectId}/surface-templates`, {
      method: 'POST', body: JSON.stringify({ name: '演示火箭曲面', diameterMm: 2500, axialLengthMm: 8000,
        seamXmm: 0, seamSafetyMm: 0, safeTopMm: 0, safeRightMm: 0, safeBottomMm: 0, safeLeftMm: 0, regions: [] }),
    });
  }
  bindSurface(projectId: string, layout: EditableLayout, template: { id: string; diameterMm: number; axialLengthMm: number }): Promise<unknown> {
    const circumference = Math.PI * template.diameterMm;
    const scale = Math.min((circumference * .72) / layout.canvasWidth, (template.axialLengthMm * .78) / layout.canvasHeight);
    const printWidthMm = layout.canvasWidth * scale;
    const printHeightMm = layout.canvasHeight * scale;
    return this.request(`/api/v1/projects/${projectId}/editable-layouts/${layout.id}/surface-binding`, {
      method: 'POST', body: JSON.stringify({ templateId: template.id, layoutVersionId: layout.currentVersionId,
        originXmm: (circumference - printWidthMm) / 2, originYmm: (template.axialLengthMm - printHeightMm) / 2,
        printWidthMm, printHeightMm }),
    });
  }
  createLayoutPreview(projectId: string, layoutId: string): Promise<unknown> {
    return this.request(`/api/v1/projects/${projectId}/editable-layouts/${layoutId}/previews`, { method: 'POST' });
  }
  renameEditableLayout(projectId: string, layoutId: string, name: string, expectedLockVersion: number): Promise<EditableLayout> {
    return this.request(`/api/v1/projects/${projectId}/editable-layouts/${layoutId}`, {
      method: 'PATCH', body: JSON.stringify({ name, expectedLockVersion }),
    });
  }
  async deleteEditableLayout(projectId: string, layoutId: string): Promise<void> {
    await this.request(`/api/v1/projects/${projectId}/editable-layouts/${layoutId}`, { method: 'DELETE' });
  }
  activateEditableLayout(projectId: string, layoutId: string): Promise<EditableLayout> {
    return this.request(`/api/v1/projects/${projectId}/editable-layouts/${layoutId}/activate`, { method: 'PUT' });
  }
  fonts(): Promise<FontAsset[]> { return this.request('/api/v1/layout/fonts'); }
  textMasks(projectId: string): Promise<TextMaskSummary[]> {
    return this.request(`/api/v1/projects/${projectId}/text-masks?page=0&size=100`);
  }
  createTextMask(projectId: string, input: CreateTextMaskInput): Promise<{ mask: TextMaskSummary; jobId: string }> {
    return this.request(`/api/v1/projects/${projectId}/text-masks`, { method: 'POST', body: JSON.stringify(input) });
  }
  async deleteTextMask(projectId: string, maskId: string): Promise<void> {
    await this.request(`/api/v1/projects/${projectId}/text-masks/${maskId}`, { method: 'DELETE' });
  }
  textMaskPreview(projectId: string, maskId: string): Promise<PresignedRequest> {
    return this.request(`/api/v1/projects/${projectId}/text-masks/${maskId}/preview-url`, { method: 'POST' });
  }
  autoLayouts(projectId: string): Promise<AutoLayoutSummary[]> {
    return this.request(`/api/v1/projects/${projectId}/layouts?page=0&size=100`);
  }
  createAutoLayout(projectId: string, input: CreateAutoLayoutInput): Promise<{ layout: AutoLayoutSummary; jobId: string }> {
    return this.request(`/api/v1/projects/${projectId}/layouts`, { method: 'POST', body: JSON.stringify(input) });
  }
  async deleteAutoLayout(projectId: string, layoutId: string): Promise<void> {
    await this.request(`/api/v1/projects/${projectId}/layouts/${layoutId}`, { method: 'DELETE' });
  }
  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(path, { ...init, headers: {
      'Content-Type': 'application/json', Authorization: `Bearer ${authToken()}`, ...init.headers,
    } });
    return this.handle(response);
  }
  private async handle<T>(response: Response): Promise<T> {
    if (response.status === 401) {
      clearSession(); redirectToLogin();
    }
    if (!response.ok) {
      const body = await response.json().catch(() => ({})) as { code?: string; message?: string };
      const retryAfterSeconds = Number(response.headers.get('Retry-After'));
      throw new ApiRequestError(
        body.message ?? `请求失败：${response.status}`,
        response.status,
        body.code ?? null,
        Number.isFinite(retryAfterSeconds) && retryAfterSeconds > 0 ? retryAfterSeconds * 1000 : null,
      );
    }
    return response.status === 204 ? undefined as T : response.json() as Promise<T>;
  }
}
