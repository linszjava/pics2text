import * as THREE from 'three';

// Public configuration reference: CASC, 2026-06-01, CZ-12B first-flight report.
// Nine engines are confirmed; bell dimensions/placement below are visual approximations.
export const CZ12B = { name: '长征十二号乙', heightM: 72, diameterM: 4.37,
  fairingDiameterM: 5.2, engineCount: 9 } as const;

export function engineCenters(radius: number) {
  return [new THREE.Vector2(0, 0), ...Array.from({ length: 8 }, (_, i) => {
    const angle = i * Math.PI / 4;
    return new THREE.Vector2(Math.sin(angle) * radius * 0.69, Math.cos(angle) * radius * 0.69);
  })];
}

export function addRocketTail(rocket: THREE.Group, r: number, axial: number, lower: number) {
  const root = new THREE.Group(); root.name = 'CZ-12B reference engine section'; rocket.add(root);
  const alloy = new THREE.MeshStandardMaterial({ color: 0x727571, roughness: 0.45, metalness: 0.8 });
  const graphite = new THREE.MeshStandardMaterial({ color: 0x282b29, roughness: 0.62,
    metalness: 0.5, side: THREE.DoubleSide });
  const lip = new THREE.MeshStandardMaterial({ color: 0x74675b, roughness: 0.48, metalness: 0.7 });
  const bottom = -axial / 2 - lower;
  const nozzleHeight = r * 0.85;
  const add = (geometry: THREE.BufferGeometry, material: THREE.Material, x: number, y: number, z: number) => {
    const m = new THREE.Mesh(geometry, material); m.position.set(x, y, z);
    m.castShadow = true; m.receiveShadow = true; root.add(m); return m;
  };
  add(new THREE.CylinderGeometry(r * 1.005, r * 0.98, r * 0.58, 96, 1, true), graphite,
    0, bottom + r * 0.27, 0);
  add(new THREE.CylinderGeometry(r * 0.965, r * 0.965, r * 0.05, 96), graphite,
    0, bottom + r * 0.1, 0);
  for (const y of [bottom + r * 0.56, bottom - r * 0.02]) {
    const collar = add(new THREE.TorusGeometry(r * 0.994, r * 0.016, 6, 96), alloy, 0, y, 0);
    collar.rotation.x = Math.PI / 2;
  }
  for (const center of engineCenters(r)) {
    const bellRadius = r * 0.225;
    const profile: THREE.Vector2[] = [];
    for (let i = 0; i <= 24; i++) {
      const t = i / 24;
      profile.push(new THREE.Vector2(bellRadius * (0.35 + 0.65 * t ** 1.5), -nozzleHeight * t));
    }
    add(new THREE.LatheGeometry(profile, 48), graphite, center.x, bottom, center.y);
    const rim = add(new THREE.TorusGeometry(bellRadius, r * 0.008, 6, 48), lip,
      center.x, bottom - nozzleHeight, center.y); rim.rotation.x = Math.PI / 2;
    for (let i = 1; i < 7; i++) {
      const t = i / 7;
      const rib = add(new THREE.TorusGeometry(bellRadius * (0.35 + 0.65 * t ** 1.5),
        r * 0.0035, 5, 40), alloy, center.x, bottom - nozzleHeight * t, center.y);
      rib.rotation.x = Math.PI / 2;
    }
  }
  return { nozzleBottom: bottom - nozzleHeight, centers: engineCenters(r),
    dispose() { alloy.dispose(); graphite.dispose(); lip.dispose(); } };
}
