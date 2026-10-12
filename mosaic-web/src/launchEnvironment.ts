import * as THREE from 'three';
import { Sky } from 'three/examples/jsm/objects/Sky.js';
import { addLaunchPad } from './launchPad';

/** Presentation geometry uses the same millimetre coordinates as the print surface. */
export function createLaunchEnvironment(scene: THREE.Scene, renderer: THREE.WebGLRenderer,
    r: number, height: number, groundY: number) {
  const root = new THREE.Group();
  root.position.y = groundY;
  root.scale.setScalar(r);
  scene.add(root);
  const materials: THREE.Material[] = [];
  const material = (color: number, roughness = 0.8, metalness = 0) => {
    const m = new THREE.MeshStandardMaterial({ color, roughness, metalness });
    materials.push(m); return m;
  };
  const concrete = material(0xaaa69b);
  const steel = material(0x7a8588, 0.42, 0.7);
  const dark = material(0x292f30, 0.7, 0.35);
  const white = material(0xd8d5ca, 0.55, 0.2);
  const ochre = material(0xbba24e);
  const asphalt = material(0x484a46);
  const glass = material(0x3e555b, 0.22, 0.5);
  const grass = material(0x727b58);
  // Spatial grain modulates real materials without a scenic backdrop or baked lighting.
  for (const [m, scale, strength] of [[concrete, 4, 0.12], [grass, 0.2, 0.28],
    [asphalt, 8, 0.12]] as const) {
    m.onBeforeCompile = (shader) => {
      shader.vertexShader = shader.vertexShader.replace('#include <common>',
        '#include <common>\nvarying vec3 surfacePosition;').replace('#include <begin_vertex>',
        '#include <begin_vertex>\nsurfacePosition = position;');
      shader.fragmentShader = shader.fragmentShader.replace('#include <common>',
        `#include <common>\nvarying vec3 surfacePosition;
        float hash(vec2 p) { return fract(sin(dot(p,vec2(12.9898,78.233)))*43758.5453); }
        float grain(vec3 p) {
          vec2 i=floor(p.xz), f=fract(p.xz); f=f*f*(3.0-2.0*f);
          return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),
            mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);
        }`)
        .replace('#include <color_fragment>', `#include <color_fragment>
        diffuseColor.rgb *= 1.0 - ${strength.toFixed(2)} * grain(surfacePosition * ${scale.toFixed(2)});`);
    };
    m.customProgramCacheKey = () => `site-grain-${scale}-${strength}`;
  }
  const box = (x: number, y: number, z: number, w: number, h: number, d: number,
      m: THREE.Material, parent: THREE.Object3D = root) => {
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), m);
    mesh.position.set(x, y, z); mesh.castShadow = true; mesh.receiveShadow = true;
    parent.add(mesh); return mesh;
  };
  const beam = (a: number[], b: number[], thickness: number, m = steel,
      parent: THREE.Object3D = root) => {
    const start = new THREE.Vector3(...a); const end = new THREE.Vector3(...b);
    const mesh = new THREE.Mesh(new THREE.CylinderGeometry(thickness, thickness,
      start.distanceTo(end), 6), m);
    mesh.position.copy(start).add(end).multiplyScalar(0.5);
    mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), end.sub(start).normalize());
    mesh.castShadow = true; parent.add(mesh); return mesh;
  };

  // A continuous landscape, not a camera-facing scenic image. Flat near the pad,
  // low undulating terrain at the horizon, visible from every orbit direction.
  const terrain = new THREE.PlaneGeometry(2200, 2200, 100, 100);
  terrain.rotateX(-Math.PI / 2);
  const points = terrain.attributes.position;
  for (let i = 0; i < points.count; i++) {
    const x = points.getX(i); const z = points.getZ(i);
    const fade = THREE.MathUtils.smoothstep(Math.hypot(x, z), 65, 240);
    points.setY(i, -0.08 + fade * (10 + 8 * Math.sin(x * 0.019) * Math.cos(z * 0.027)));
  }
  terrain.computeVertexNormals();
  const land = new THREE.Mesh(terrain, grass); land.receiveShadow = true; root.add(land);
  let seed = 8731;
  const random = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 4294967296; };
  const shrubGeometry = new THREE.IcosahedronGeometry(1, 1);
  const shrubVertices = shrubGeometry.attributes.position;
  for (let i = 0; i < shrubVertices.count; i++) {
    const x = shrubVertices.getX(i), y = shrubVertices.getY(i), z = shrubVertices.getZ(i);
    const scale = 0.78 + 0.22 * Math.sin(x * 17 + y * 31 + z * 11);
    shrubVertices.setXYZ(i, x * scale, y * scale, z * scale);
  }
  shrubGeometry.computeVertexNormals();
  const shrubs = new THREE.InstancedMesh(shrubGeometry, material(0x43563a), 1200);
  const transform = new THREE.Object3D();
  for (let i = 0; i < 1200; i++) {
    const angle = random() * Math.PI * 2; const distance = 70 + Math.sqrt(random()) * 400;
    const x = Math.cos(angle) * distance; const z = Math.sin(angle) * distance;
    const fade = THREE.MathUtils.smoothstep(distance, 65, 240);
    const y = -0.08 + fade * (10 + 8 * Math.sin(x * 0.019) * Math.cos(z * 0.027));
    const size = 0.3 + random() * 0.8;
    transform.position.set(x, y + size * 0.3, z); transform.scale.set(size * 1.4, size * 0.8, size);
    transform.rotation.y = angle; transform.updateMatrix(); shrubs.setMatrixAt(i, transform.matrix);
    shrubs.setColorAt(i, new THREE.Color().setHSL(0.2 + random() * 0.06, 0.18, 0.55 + random() * 0.25));
  }
  shrubs.receiveShadow = true; root.add(shrubs);
  box(0, -0.015, 0, 24, 0.08, 30, concrete);
  for (const x of [-22, 22]) {
    box(x, 0.005, 0, 5, 0.06, 160, asphalt);
    for (let z = -78; z < 80; z += 6) box(x, 0.045, z, 0.07, 0.01, 2, ochre);
  }
  box(0, 0.005, 24, 120, 0.06, 5, asphalt);
  // Concrete expansion joints and drainage channels are geometry, not painted scenery.
  for (let x = -10; x <= 10; x += 4) box(x, 0.032, 0, 0.025, 0.015, 30, dark);
  for (let z = -14; z <= 14; z += 4) box(0, 0.033, z, 24, 0.015, 0.025, dark);
  for (const x of [-9, 9]) box(x, 0.045, 0, 0.18, 0.04, 27, dark);

  addLaunchPad(root, materials);
  // True four-sided service gantry, with diagonal bracing and walkable decks.
  const tower = new THREE.Group(); tower.position.set(-4.6, 0, -1.5); root.add(tower);
  const towerHeight = height / r * 0.94 + 3;
  const levels = 12; const step = towerHeight / levels;
  for (const x of [-0.85, 0.85]) for (const z of [-0.85, 0.85]) {
    box(x, towerHeight / 2, z, 0.16, towerHeight, 0.16, steel, tower);
    box(x, 0.15, z, 0.65, 0.3, 0.65, concrete, tower);
  }
  for (let level = 0; level < levels; level++) {
    const y = level * step;
    box(0, y + 0.12, 0, 1.95, 0.12, 1.95, dark, tower);
    for (const side of [-1, 1]) {
      beam([-0.85, y, side * 0.85], [0.85, y + step, side * 0.85], 0.042, steel, tower);
      beam([0.85, y, side * 0.85], [-0.85, y + step, side * 0.85], 0.042, steel, tower);
      beam([side * 0.85, y, -0.85], [side * 0.85, y + step, 0.85], 0.042, steel, tower);
      beam([side * 0.85, y + 0.7, -0.9], [side * 0.85, y + 0.7, 0.9], 0.024, ochre, tower);
    }
    // Stair flight and central elevator enclosure break up the repeated truss silhouette.
    for (let s = 0; s < 9; s++) box(-0.5, y + step * s / 9, -0.7 + s * 0.16,
      0.45, 0.04, 0.2, steel, tower);
  }
  box(0.25, towerHeight / 2, 0, 0.55, towerHeight, 0.7, white, tower);
  for (const x of [-0.28, -0.12]) beam([x, 0, -1], [x, towerHeight, -1], 0.055, white, tower);
  const arms: THREE.Group[] = [];
  for (const fraction of [0.34, 0.59, 0.78]) {
    const arm = new THREE.Group(); arm.position.set(0.85, towerHeight * fraction, 0); tower.add(arm);
    box(1.4, 0, 0, 2.8, 0.15, 0.8, steel, arm);
    for (const z of [-0.38, 0.38]) {
      beam([0, 0.6, z], [2.8, 0.6, z], 0.025, ochre, arm);
      for (let x = 0; x <= 2.8; x += 0.55) beam([x, 0, z], [x, 0.6, z], 0.023, ochre, arm);
      beam([0, -0.8, z], [2.8, 0, z], 0.06, steel, arm);
    }
    arms.push(arm);
  }
  // Site buildings are detailed on all sides, arranged as a working facility.
  for (const [x, z, w, d, h] of [[-36, -24, 15, 22, 9], [33, -30, 18, 12, 3.5],
    [34, 8, 12, 8, 2.8], [-38, 29, 15, 9, 3], [9, -43, 13, 8, 3.8]]) {
    box(x, h / 2, z, w, h, d, white);
    box(x, h + 0.12, z, w + 0.3, 0.24, d + 0.3, dark);
    for (let dx = -w / 2 + 1; dx < w / 2; dx += 1.2) {
      for (const side of [-1, 1]) {
        box(x + dx, h * 0.62, z + side * (d / 2 + 0.02), 0.85, 0.55, 0.04, glass);
        box(x + dx, h / 2, z + side * (d / 2 + 0.05), 0.035, h, 0.06, steel);
      }
    }
    for (const side of [-1, 1]) box(x + side * (w / 2 + 0.02), h * 0.28, z, 0.06, h * 0.55, d * 0.6, steel);
    box(x, h + 0.4, z, 1.8, 0.5, 1.2, steel);
  }
  for (let i = 0; i < 5; i++) {
    const x = 8 + i * 3.7; const z = -18;
    const tank = new THREE.Mesh(new THREE.CapsuleGeometry(1.2, 3.8, 8, 24), white);
    tank.position.set(x, 3.1, z); tank.castShadow = true; root.add(tank);
    box(x, 0.35, z, 2.8, 0.7, 2.8, concrete);
    beam([x, 1, z + 1.1], [x, 1, -9], 0.11);
  }
  beam([0, 0.45, -9], [25, 0.45, -9], 0.16, white);
  // Perimeter fence and floodlight masts establish scale, even behind the rocket.
  for (const z of [-55, 48]) {
    beam([-55, 1.2, z], [55, 1.2, z], 0.025);
    beam([-55, 0.5, z], [55, 0.5, z], 0.018);
    for (let x = -55; x <= 55; x += 2.5) beam([x, 0, z], [x, 1.6, z], 0.035);
  }
  for (const [x, z] of [[-12, -12], [12, -12], [-12, 16], [12, 16]]) {
    beam([x, 0, z], [x, 15, z], 0.08);
    box(x, 15, z, 1.1, 0.13, 0.2, dark);
    for (const dx of [-0.4, 0, 0.4]) box(x + dx, 15, z + 0.13, 0.26, 0.22, 0.07, white);
  }

  const sky = new Sky(); sky.scale.setScalar(height * 80); scene.add(sky);
  const uniforms = sky.material.uniforms;
  uniforms.turbidity.value = 4;
  uniforms.rayleigh.value = 0.65;
  uniforms.mieCoefficient.value = 0.004;
  uniforms.mieDirectionalG.value = 0.78;
  uniforms.cloudCoverage.value = 0.18;
  uniforms.cloudDensity.value = 0.2;
  const sun = new THREE.Vector3(-0.55, 0.46, 0.68).normalize();
  uniforms.sunPosition.value.copy(sun);
  const generator = new THREE.PMREMGenerator(renderer);
  const environmentScene = new THREE.Scene(); environmentScene.add(sky.clone());
  const environment = generator.fromScene(environmentScene, 0.04);
  generator.dispose(); scene.environment = environment.texture;
  scene.environmentIntensity = 0.4;
  const light = new THREE.DirectionalLight(0xffedcf, 3.2);
  light.position.copy(sun).multiplyScalar(height * 2);
  light.target.position.set(0, groundY + height * 0.45, 0);
  light.castShadow = true; light.shadow.mapSize.set(2048, 2048);
  Object.assign(light.shadow.camera, { left: -height * 0.85, right: height * 0.85,
    top: height * 0.85, bottom: -height * 0.85, near: 1, far: height * 5 });
  light.shadow.normalBias = r * 0.008;
  scene.add(light, light.target);
  const ambient = new THREE.HemisphereLight(0xd7e4ed, 0x615642, 0.42); scene.add(ambient);
  return {
    update(progress: number, ascent: number) {
      arms.forEach((arm) => { arm.rotation.y = -THREE.MathUtils.smoothstep(progress, 0, 0.18) * 1.35; });
      // Keep directional-light shadow coverage centered on the ascending vehicle.
      light.target.position.y = groundY + height * 0.45 + ascent;
      light.position.copy(sun).multiplyScalar(height * 2).add(light.target.position);
    },
    dispose() {
      root.traverse((object) => {
        if (object instanceof THREE.Mesh) object.geometry.dispose();
        if (object instanceof THREE.InstancedMesh) object.dispose();
      });
      materials.forEach((m) => m.dispose()); sky.geometry.dispose(); sky.material.dispose();
      environment.dispose(); light.shadow.dispose();
      scene.remove(root, sky, light, light.target, ambient); scene.environment = null;
    },
  };
}

export function addRocketDetails(rocket: THREE.Group, r: number, axial: number,
    upper: number, lower: number, material: THREE.Material) {
  // Thin seams, attachment collars, service conduits and engine cooling ribs.
  const bottom = -axial / 2 - lower;
  for (const y of [bottom + r * 0.15, bottom + lower * 0.32, bottom + lower * 0.7,
    axial / 2 + upper * 0.1, axial / 2 + upper * 0.85, axial / 2 + upper]) {
    const ring = new THREE.Mesh(new THREE.TorusGeometry(r * 1.003, r * 0.008, 6, 96), material);
    ring.rotation.x = Math.PI / 2; ring.position.y = y; rocket.add(ring);
  }
  for (const angle of [Math.PI * 0.6, Math.PI * 1.4]) {
    const rail = new THREE.Mesh(new THREE.CylinderGeometry(r * 0.026, r * 0.026, lower * 0.95, 8), material);
    rail.position.set(Math.sin(angle) * r * 1.012, bottom + lower / 2, Math.cos(angle) * r * 1.012);
    rocket.add(rail);
  }
  rocket.traverse((object) => {
    if (object instanceof THREE.Mesh) { object.castShadow = true; object.receiveShadow = true; }
  });
}
