import * as THREE from 'three';

/** Visual launch-table assembly, not a flight-certified engineering model.
 * Coordinates are in rocket-radius units, with the site ground at zero. */
export function addLaunchPad(parent: THREE.Group, materials: THREE.Material[]) {
  const root = new THREE.Group(); root.name = 'Launch table and flame deflector'; parent.add(root);
  const mat = (color: number, roughness: number, metalness = 0) => {
    const m = new THREE.MeshStandardMaterial({ color, roughness, metalness }); materials.push(m); return m;
  };
  const concrete = mat(0x817e75, 0.96);
  const steel = mat(0x485252, 0.58, 0.65);
  const edge = mat(0x737d7c, 0.45, 0.7);
  const scorched = mat(0x302c27, 0.95);
  const yellow = mat(0xbd923b, 0.72, 0.1);
  const pipe = mat(0x96978a, 0.44, 0.6);
  const box = (x: number, y: number, z: number, w: number, h: number, d: number, m: THREE.Material) => {
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), m);
    mesh.position.set(x, y, z); mesh.castShadow = true; mesh.receiveShadow = true; root.add(mesh); return mesh;
  };
  const tube = (a: number[], b: number[], radius: number, m: THREE.Material) => {
    const start = new THREE.Vector3(...a), end = new THREE.Vector3(...b);
    const mesh = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, start.distanceTo(end), 10), m);
    mesh.position.copy(start).add(end).multiplyScalar(0.5);
    mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), end.sub(start).normalize());
    mesh.castShadow = true; root.add(mesh); return mesh;
  };
  // Broad chamfered deck with an actual through-opening, rather than four floating bars.
  const outline = new THREE.Shape();
  outline.moveTo(-3.7, -3.5); outline.lineTo(-2.9, -4.3); outline.lineTo(2.9, -4.3);
  outline.lineTo(3.7, -3.5); outline.lineTo(3.7, 3.5); outline.lineTo(2.9, 4.3);
  outline.lineTo(-2.9, 4.3); outline.lineTo(-3.7, 3.5); outline.closePath();
  const opening = new THREE.Path(); opening.absarc(0, 0, 1.65, 0, Math.PI * 2, true); outline.holes.push(opening);
  const deck = new THREE.Mesh(new THREE.ExtrudeGeometry(outline,
    { depth: 0.42, bevelEnabled: true, bevelSize: 0.035, bevelThickness: 0.035, bevelSegments: 2, steps: 1 }), steel);
  deck.rotation.x = -Math.PI / 2; deck.position.y = 2.8;
  deck.castShadow = true; deck.receiveShadow = true; root.add(deck);
  const collar = new THREE.Mesh(new THREE.CylinderGeometry(1.73, 1.73, 0.2, 64, 1, true), scorched);
  collar.position.y = 3.24; root.add(collar);
  // Reinforced foundations and structural ribs give the assembly visible mass.
  for (const x of [-2.9, 2.9]) for (const z of [-3.2, 3.2]) {
    box(x, 0.18, z, 1.9, 0.36, 2.1, concrete);
    box(x, 1.5, z, 1.25, 2.65, 1.45, concrete);
    box(x, 2.73, z, 1.45, 0.16, 1.65, edge);
    for (const dx of [-0.53, 0.53]) for (const dz of [-0.6, 0.6]) {
      tube([x + dx, 2.75, z + dz], [x + dx, 2.86, z + dz], 0.055, scorched);
    }
    for (let y = 0.7; y < 2.7; y += 0.58) box(x, y, z + Math.sign(z) * 0.728, 1.25, 0.015, 0.012, scorched);
  }
  for (const x of [-3.2, 3.2]) {
    box(x, 2.46, 0, 0.2, 0.72, 7.2, steel);
    for (let z = -3; z <= 3; z += 0.6) box(x, 2.46, z, 0.38, 0.68, 0.045, edge);
    tube([x, 0.55, -2.6], [x, 2.45, 2.6], 0.075, steel);
    tube([x, 2.45, -2.6], [x, 0.55, 2.6], 0.075, steel);
  }
  // Sloped refractory deflector channels exhaust out through the open ends.
  const section = new THREE.Shape(); section.moveTo(-5.5, 0.12); section.lineTo(-5.5, 0.32);
  section.lineTo(-0.7, 1.6); section.quadraticCurveTo(0, 1.95, 0.7, 1.6);
  section.lineTo(5.5, 0.32); section.lineTo(5.5, 0.12); section.closePath();
  const deflector = new THREE.Mesh(new THREE.ExtrudeGeometry(section,
    { depth: 2.7, bevelEnabled: false, steps: 1 }), scorched);
  deflector.rotation.y = Math.PI / 2; deflector.position.x = -1.35;
  deflector.castShadow = true; deflector.receiveShadow = true; root.add(deflector);
  for (const x of [-1.65, 1.65]) box(x, 0.6, 0, 0.3, 1.2, 12.8, concrete);
  // Grated service decks, slim guardrails and toe plates.
  for (const x of [-3.48, 3.48]) {
    tube([x, 4.05, -3.4], [x, 4.05, 3.4], 0.022, yellow);
    tube([x, 3.67, -3.4], [x, 3.67, 3.4], 0.018, yellow);
    box(x, 3.31, 0, 0.045, 0.16, 6.8, yellow);
    for (let z = -3.4; z <= 3.4; z += 0.85) tube([x, 3.24, z], [x, 4.05, z], 0.025, yellow);
    for (let z = -3.3; z <= 3.3; z += 0.12) box(x * 0.83, 3.255, z, 0.82, 0.035, 0.035, edge);
  }
  // Offset access stair, with stringers and two continuous handrails.
  for (let i = 0; i < 18; i++) box(4.35, 0.18 + i * 0.18, 5.9 - i * 0.23, 0.94, 0.065, 0.27, edge);
  for (const x of [3.86, 4.84]) {
    tube([x, 0.08, 6], [x, 3.17, 1.92], 0.07, steel);
    tube([x, 0.96, 6], [x, 4.07, 1.92], 0.025, yellow);
    for (let i = 0; i < 18; i += 3) tube([x, 0.18 + i * 0.18, 5.9 - i * 0.23],
      [x, 1.06 + i * 0.18, 5.9 - i * 0.23], 0.022, yellow);
  }
  box(4, 3.2, 1.8, 1.6, 0.1, 1.1, steel);
  // Service pipework with coupling flanges and a valve wheel, all true geometry.
  for (const x of [-4.5, 5.3]) {
    tube([x, 0.42, -8], [x, 0.42, 2.3], 0.14, pipe);
    tube([x, 0.42, 2.3], [x, 2.35, 2.3], 0.14, pipe);
    tube([x, 2.35, 2.3], [Math.sign(x) * 2, 2.35, 2.3], 0.14, pipe);
    for (const z of [-6, -2, 1.5]) {
      const flange = new THREE.Mesh(new THREE.TorusGeometry(0.19, 0.04, 8, 20), edge);
      flange.position.set(x, 0.42, z); root.add(flange);
      box(x, 0.17, z, 0.45, 0.25, 0.35, concrete);
    }
    const valve = new THREE.Mesh(new THREE.TorusGeometry(0.22, 0.025, 8, 24), yellow);
    valve.position.set(x, 1.4, 2.54); root.add(valve);
    tube([x - 0.2, 1.4, 2.54], [x + 0.2, 1.4, 2.54], 0.018, yellow);
  }
  // Hold-down supports meet the engine skirt without blocking the exhaust opening.
  for (let i = 0; i < 4; i++) {
    const angle = Math.PI / 4 + i * Math.PI / 2;
    const x = Math.sin(angle), z = Math.cos(angle);
    box(x * 1.92, 3.7, z * 1.92, 0.42, 0.9, 0.42, steel);
    tube([x * 1.92, 4.05, z * 1.92], [x * 1.04, 4.65, z * 1.04], 0.12, edge);
  }
  return root;
}
