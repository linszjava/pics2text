export type LayoutState = 'DRAFT' | 'LOCKED' | 'PUBLISHED';

export interface EditableLayout {
  id: string;
  projectId: string;
  sourceAutoLayoutId: string;
  maskId: string;
  name: string;
  state: LayoutState;
  canvasWidth: number;
  canvasHeight: number;
  allowPhotoReuse: boolean;
  maxPhotoReuse: number;
  currentRevision: number;
  lockVersion: number;
  currentVersionNumber: number;
  currentVersionId: string;
}

export interface Assignment {
  ownerId: string;
  slotIndex: number;
  photoId: string;
  x: number;
  y: number;
  width: number;
  height: number;
  cropX: number;
  cropY: number;
  cropWidth: number;
  cropHeight: number;
}

export interface PhotoPreviewUrl {
  photoId: string;
  url: string;
  headers: Record<string, string[]>;
}

export type EditCommand =
  | { type: 'SWAP'; slotIndex: number; secondarySlotIndex: number }
  | { type: 'REPLACE'; slotIndex: number; photoId: string }
  | { type: 'CROP'; slotIndex: number; cropX: number; cropY: number; cropWidth: number; cropHeight: number };

export interface EditResponse {
  layout: EditableLayout;
  version: { id: string; versionNumber: number; contentHash: string };
  idempotentReplay: boolean;
}

export interface ApiFailure extends Error {
  status: number;
  code?: string;
}
