import type { Assignment, EditCommand, EditResponse, EditableLayout, PhotoPreviewUrl,
  SurfacePreviewData } from './types';

export interface ViewportQuery {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

export class LayoutApi {
  constructor(private readonly baseUrl: string, private readonly token: string) {}

  getLayout(projectId: string, layoutId: string): Promise<EditableLayout> {
    return this.request(`/api/v1/projects/${projectId}/editable-layouts/${layoutId}`);
  }

  assignments(projectId: string, layoutId: string, viewport?: ViewportQuery,
      photoId?: string, slotIndex?: number): Promise<Assignment[]> {
    const params = new URLSearchParams({ limit: '5000' });
    if (viewport) Object.entries(viewport).forEach(([key, value]) => params.set(key, String(Math.round(value))));
    if (photoId) params.set('photoId', photoId);
    if (slotIndex !== undefined) params.set('slotIndex', String(slotIndex));
    return this.request(`/api/v1/projects/${projectId}/editable-layouts/${layoutId}/assignments?${params}`);
  }

  photoUrls(projectId: string, layoutId: string, photoIds: string[]): Promise<PhotoPreviewUrl[]> {
    return this.request(`/api/v1/projects/${projectId}/editable-layouts/${layoutId}/photo-preview-urls`, {
      method: 'POST', body: JSON.stringify({ photoIds }),
    });
  }

  edit(projectId: string, layoutId: string, expectedLockVersion: number,
      clientBatchId: string, commands: EditCommand[]): Promise<EditResponse> {
    return this.request(`/api/v1/projects/${projectId}/editable-layouts/${layoutId}/edits`, {
      method: 'POST',
      body: JSON.stringify({ expectedLockVersion, clientBatchId, note: '编辑器自动保存', commands }),
    });
  }

  surfacePreviewData(projectId: string, layoutId: string): Promise<SurfacePreviewData> {
    return this.request(`/api/v1/projects/${projectId}/editable-layouts/${layoutId}/surface-preview-data`);
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.token}`,
        ...init.headers,
      },
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      const failure = new Error(body.message ?? `请求失败：${response.status}`) as Error & { status: number; code?: string };
      failure.status = response.status;
      failure.code = body.code;
      throw failure;
    }
    return response.json() as Promise<T>;
  }
}
