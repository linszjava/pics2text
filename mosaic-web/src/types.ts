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

export interface SurfaceManifest {
  schemaVersion: string;
  unit: 'MILLIMETER';
  layoutId: string;
  layoutVersionId: string;
  templateId: string;
  templateVersion: number;
  geometryHash: string;
  surface: { type: 'CYLINDER'; diameterMm: number; radiusMm: number; circumferenceMm: number;
    axialLengthMm: number; seamXmm: number; seamSafetyMm: number };
  canvas: { widthPx: number; heightPx: number };
  mapping: { originXmm: number; originYmm: number; printWidthMm: number; printHeightMm: number;
    scaleXmmPerPx: number; scaleYmmPerPx: number };
  regions: Array<{ regionIndex: number; type: 'NO_PRINT' | 'COMPARTMENT' | 'STRUCTURE' | 'REFERENCE';
    name: string; blocking: boolean; xMm: number; yMm: number; widthMm: number; heightMm: number }>;
  slots: Array<{ slotIndex: number; photoId: string;
    physical: { x: number; y: number; width: number; height: number };
    crop: { x: number; y: number; width: number; height: number } }>;
}

export interface SurfacePreviewData {
  manifest: SurfaceManifest;
  manifestHash: string;
  validationStatus: 'VALID' | 'INVALID';
  violationCount: number;
  layoutPreviewId: string | null;
  textureUrl: string | null;
}
