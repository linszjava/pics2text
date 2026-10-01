import { describe, expect, it } from 'vitest';
import { clampScale, fitTransform, visibleBounds, zoomAt } from './viewport';

describe('viewport', () => {
  it('limits scale and keeps the zoom anchor stable', () => {
    const next = zoomAt({ scale: 1, x: 10, y: 20 }, 110, 220, 2);
    expect(next).toEqual({ scale: 2, x: -90, y: -180 });
    expect(clampScale(0)).toBe(0.05);
    expect(clampScale(99)).toBe(8);
  });

  it('fits the canvas and clips visible bounds', () => {
    expect(fitTransform(1_000, 500, 2_000, 1_000)).toEqual({ scale: 0.46, x: 40, y: 20 });
    expect(visibleBounds({ scale: 1, x: -200, y: -100 }, 500, 300, 1_000, 800, 0))
      .toEqual({ minX: 200, minY: 100, maxX: 700, maxY: 400 });
    expect(visibleBounds({ scale: 1, x: 2_000, y: 2_000 }, 500, 300, 1_000, 800, 0))
      .toEqual({ minX: 0, minY: 0, maxX: 0, maxY: 0 });
  });
});
