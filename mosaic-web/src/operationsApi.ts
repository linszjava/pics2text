import { authToken, clearSession, redirectToLogin, type AuthenticatedUser } from './auth';
import type { EditableLayout } from './types';

export interface ProjectSummary {
  id: string;
  name: string;
  status: string;
  description: string;
  createdAt: string;
  updatedAt: string;
}

export interface PhotoSummary {
  id: string;
  originalFilename: string;
  byteSize: number;
  status: string;
  analysisStatus: string;
  reviewStatus: string;
  duplicateOf: string | null;
  createdAt: string;
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

export interface UserSummary extends AuthenticatedUser {
  status: 'ACTIVE' | 'DISABLED';
  createdAt: string;
  updatedAt: string;
}

export interface AuditSummary {
  id: number;
  actorUserId: string | null;
  action: string;
  targetType: string;
  targetId: string | null;
  success: boolean;
  requestId: string | null;
  createdAt: string;
}

export type ProjectRole = 'PROJECT_ADMIN' | 'REVIEWER' | 'DESIGNER' | 'VIEWER';

export interface ProjectMember {
  user: UserSummary;
  role: ProjectRole;
  createdAt: string;
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
export interface InitiatedPhotoUpload {
  photo: PhotoSummary; duplicate: boolean; directUpload: PresignedRequest | null;
  partSize: number | null; partCount: number; expiresAt: string;
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
  photos(projectId: string): Promise<PhotoSummary[]> {
    return this.request(`/api/v1/projects/${projectId}/photos?page=0&size=200`);
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
  reviewPhoto(projectId: string, photoId: string, decision: 'APPROVED' | 'REJECTED', note: string): Promise<PhotoSummary> {
    return this.request(`/api/v1/projects/${projectId}/photos/${photoId}/review`, {
      method: 'POST', body: JSON.stringify({ decision, note }),
    });
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
  projectMembers(projectId: string): Promise<ProjectMember[]> {
    return this.request(`/api/v1/projects/${projectId}/members`);
  }
  putProjectMember(projectId: string, userId: string, role: ProjectRole): Promise<ProjectMember> {
    return this.request(`/api/v1/projects/${projectId}/members/${userId}`, {
      method: 'PUT', body: JSON.stringify({ role }),
    });
  }
  async removeProjectMember(projectId: string, userId: string): Promise<void> {
    await this.request(`/api/v1/projects/${projectId}/members/${userId}`, { method: 'DELETE' });
  }
  fonts(): Promise<FontAsset[]> { return this.request('/api/v1/layout/fonts'); }
  textMasks(projectId: string): Promise<TextMaskSummary[]> {
    return this.request(`/api/v1/projects/${projectId}/text-masks?page=0&size=100`);
  }
  createTextMask(projectId: string, input: CreateTextMaskInput): Promise<{ mask: TextMaskSummary; jobId: string }> {
    return this.request(`/api/v1/projects/${projectId}/text-masks`, { method: 'POST', body: JSON.stringify(input) });
  }
  autoLayouts(projectId: string): Promise<AutoLayoutSummary[]> {
    return this.request(`/api/v1/projects/${projectId}/layouts?page=0&size=100`);
  }
  createAutoLayout(projectId: string, input: CreateAutoLayoutInput): Promise<{ layout: AutoLayoutSummary; jobId: string }> {
    return this.request(`/api/v1/projects/${projectId}/layouts`, { method: 'POST', body: JSON.stringify(input) });
  }
  users(): Promise<UserSummary[]> { return this.request('/api/v1/admin/users'); }
  createUser(input: { username: string; password: string; displayName: string; globalRole: 'ADMIN' | 'USER' }): Promise<UserSummary> {
    return this.request('/api/v1/admin/users', { method: 'POST', body: JSON.stringify(input) });
  }
  changeUserStatus(userId: string, status: 'ACTIVE' | 'DISABLED'): Promise<UserSummary> {
    return this.request(`/api/v1/admin/users/${userId}/status`, { method: 'PATCH', body: JSON.stringify({ status }) });
  }
  async kickUser(userId: string): Promise<void> {
    await this.request(`/api/v1/admin/users/${userId}/kick`, { method: 'POST' });
  }
  auditLogs(): Promise<AuditSummary[]> { return this.request('/api/v1/admin/audit-logs'); }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(path, { ...init, headers: {
      'Content-Type': 'application/json', Authorization: `Bearer ${authToken()}`, ...init.headers,
    } });
    if (response.status === 401) {
      clearSession(); redirectToLogin();
    }
    if (!response.ok) {
      const body = await response.json().catch(() => ({})) as { message?: string };
      throw new Error(body.message ?? `请求失败：${response.status}`);
    }
    return response.status === 204 ? undefined as T : response.json() as Promise<T>;
  }
}
