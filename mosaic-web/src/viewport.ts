export interface ViewTransform { scale: number; x: number; y: number }
export interface Bounds { minX: number; minY: number; maxX: number; maxY: number }

export function clampScale(scale: number): number {
  return Math.min(64, Math.max(0.05, scale));
}

export function visibleBounds(transform: ViewTransform, width: number, height: number,
    canvasWidth: number, canvasHeight: number, overscan = 160): Bounds {
  const minX = Math.min(canvasWidth, Math.max(0, (-transform.x - overscan) / transform.scale));
  const minY = Math.min(canvasHeight, Math.max(0, (-transform.y - overscan) / transform.scale));
  const maxX = Math.max(0, Math.min(canvasWidth, (width - transform.x + overscan) / transform.scale));
  const maxY = Math.max(0, Math.min(canvasHeight, (height - transform.y + overscan) / transform.scale));
  return { minX, minY, maxX, maxY };
}

export function zoomAt(transform: ViewTransform, screenX: number, screenY: number,
    factor: number): ViewTransform {
  const nextScale = clampScale(transform.scale * factor);
  const worldX = (screenX - transform.x) / transform.scale;
  const worldY = (screenY - transform.y) / transform.scale;
  return {
    scale: nextScale,
    x: screenX - worldX * nextScale,
    y: screenY - worldY * nextScale,
  };
}

export function fitTransform(width: number, height: number,
    canvasWidth: number, canvasHeight: number): ViewTransform {
  const scale = clampScale(Math.min(width / canvasWidth, height / canvasHeight) * 0.92);
  return { scale, x: (width - canvasWidth * scale) / 2, y: (height - canvasHeight * scale) / 2 };
}
