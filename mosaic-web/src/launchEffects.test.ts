import { describe, expect, it } from 'vitest';
import { launchMotion, createExhaustMaterial } from './launchEffects';

describe('launch presentation', () => {
  it('holds on the pad during ignition and clamps out-of-range time', () => {
    expect(launchMotion(-1, 32000)).toEqual({ progress: 0, ascent: 0, complete: false });
    expect(launchMotion(2, 32000).ascent).toBe(0);
    expect(launchMotion(20, 32000)).toEqual({ progress: 1, ascent: 40000, complete: true });
  });
  it('ascends continuously and accelerates without depending on frame rate', () => {
    const samples = [2, 4, 6, 8, 10].map((t) => launchMotion(t, 32000).ascent);
    for (let i = 1; i < samples.length; i++) expect(samples[i]).toBeGreaterThan(samples[i - 1]);
    expect(samples[4] - samples[3]).toBeGreaterThan(samples[2] - samples[1]);
    expect(launchMotion(5, 64000).ascent).toBe(launchMotion(5, 32000).ascent * 2);
  });
  it('uses transparent emissive exhaust without opaque depth occlusion', () => {
    const material = createExhaustMaterial();
    expect(material.depthWrite).toBe(false);
    expect(material.transparent).toBe(true);
    expect(material.uniforms.time.value).toBe(0);
    material.dispose();
  });
});
