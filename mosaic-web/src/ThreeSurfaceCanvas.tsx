import { useEffect, useRef } from 'react';
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import type { SurfaceManifest } from './types';

interface Props {
  manifest: SurfaceManifest;
  textureUrl: string | null;
  photoUrls: Map<string, string>;
  useSimulationTexture?: boolean;
  simulationText?: string;
  simulationDirection?: 'HORIZONTAL' | 'VERTICAL';
  focusMode?: 'ROCKET' | 'TEXT';
  showEngineeringGuides?: boolean;
}

export function ThreeSurfaceCanvas({ manifest, textureUrl, photoUrls, useSimulationTexture = false,
    simulationText = '', simulationDirection = 'VERTICAL', focusMode = 'ROCKET', showEngineeringGuides = false }: Props) {
  const hostRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 3));
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    host.appendChild(renderer.domElement);
    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0xdce8f5);
    const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 100_000);
    const { radiusMm, axialLengthMm } = manifest.surface;
    const displayAxialLengthMm = useSimulationTexture
      ? Math.max(axialLengthMm, radiusMm * 5.2) : axialLengthMm;
    const upperBodyHeight = Math.max(radiusMm * 2.8, axialLengthMm * 0.7);
    const lowerBodyHeight = Math.max(radiusMm * 3.6, axialLengthMm);
    const fairingHeight = Math.max(radiusMm * 3.6, axialLengthMm * 0.9);
    const totalHeight = upperBodyHeight + displayAxialLengthMm + lowerBodyHeight
      + fairingHeight;
    const fullRocketDistance = Math.max(radiusMm * 8, totalHeight * 1.35);
    const textFocusDistance = Math.max(radiusMm * 3.4, displayAxialLengthMm * 1.42);
    const distance = focusMode === 'TEXT' ? textFocusDistance : fullRocketDistance;
    camera.near = Math.max(1, radiusMm * 0.005);
    camera.far = Math.max(totalHeight * 6, fullRocketDistance * 4);
    camera.updateProjectionMatrix();
    camera.position.set(focusMode === 'TEXT' ? 0 : distance * 0.35,
      focusMode === 'TEXT' ? 0 : totalHeight * 0.02, distance);
    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.target.set(0, 0, 0);
    controls.minDistance = radiusMm + Math.max(20, radiusMm * 0.02);
    controls.maxDistance = fullRocketDistance * 2.4;
    const rocket = new THREE.Group();
    scene.add(rocket);
    const photoLayer = new THREE.Group();
    rocket.add(photoLayer);
    const shellMaterial = new THREE.MeshStandardMaterial({ color: 0xf3f6fa, roughness: 0.58,
      metalness: 0.06, side: THREE.DoubleSide });
    const lowerMaterial = new THREE.MeshStandardMaterial({ color: 0xe7ecf2, roughness: 0.6,
      metalness: 0.08, side: THREE.DoubleSide });
    const engineMaterial = new THREE.MeshStandardMaterial({ color: 0x46515e, roughness: 0.7,
      metalness: 0.28, side: THREE.DoubleSide });
    const navyMaterial = new THREE.MeshStandardMaterial({ color: 0x173f78, roughness: 0.5,
      metalness: 0.04, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 });
    const redMaterial = new THREE.MeshStandardMaterial({ color: 0xc92f3c, roughness: 0.54,
      metalness: 0.03, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 });
    const geometry = new THREE.CylinderGeometry(radiusMm, radiusMm, displayAxialLengthMm, 128, 1, false);
    const material = new THREE.MeshStandardMaterial({ color: 0xd7dde6, roughness: 0.72,
      metalness: 0.08, side: THREE.DoubleSide });
    const cylinder = new THREE.Mesh(geometry, material);
    cylinder.rotation.y = Math.PI;
    rocket.add(cylinder);

    const upperBody = new THREE.Mesh(new THREE.CylinderGeometry(radiusMm, radiusMm,
      upperBodyHeight, 96), shellMaterial);
    upperBody.position.y = displayAxialLengthMm / 2 + upperBodyHeight / 2;
    rocket.add(upperBody);
    const fairing = new THREE.Mesh(new THREE.SphereGeometry(radiusMm, 96, 48,
      0, Math.PI * 2, 0, Math.PI / 2), shellMaterial);
    fairing.scale.y = fairingHeight / radiusMm;
    fairing.position.y = displayAxialLengthMm / 2 + upperBodyHeight;
    rocket.add(fairing);

    const lowerBody = new THREE.Mesh(new THREE.CylinderGeometry(radiusMm, radiusMm,
      lowerBodyHeight, 96), lowerMaterial);
    lowerBody.position.y = -displayAxialLengthMm / 2 - lowerBodyHeight / 2;
    rocket.add(lowerBody);
    const nozzleHeight = Math.max(radiusMm * 0.9, axialLengthMm * 0.22);
    const nozzle = new THREE.Mesh(new THREE.CylinderGeometry(radiusMm * 0.42,
      radiusMm * 0.62, nozzleHeight, 64), engineMaterial);
    nozzle.position.y = -displayAxialLengthMm / 2 - lowerBodyHeight - nozzleHeight / 2;
    rocket.add(nozzle);
    addStrapOnBoosters(rocket, radiusMm, displayAxialLengthMm, lowerBodyHeight,
      shellMaterial, navyMaterial, redMaterial, engineMaterial);

    for (const y of [-displayAxialLengthMm / 2, displayAxialLengthMm / 2]) {
      addBodyBand(rocket, radiusMm, y, radiusMm * 0.07, navyMaterial);
    }
    addBodyBand(rocket, radiusMm, displayAxialLengthMm / 2 + upperBodyHeight * 0.28,
      radiusMm * 0.11, navyMaterial);
    addBodyBand(rocket, radiusMm, displayAxialLengthMm / 2 + upperBodyHeight * 0.78,
      radiusMm * 0.07, navyMaterial);
    addBodyBand(rocket, radiusMm, -displayAxialLengthMm / 2 - lowerBodyHeight * 0.66,
      radiusMm * 0.09, navyMaterial);

    const flagTexture = createFlagTexture();
    const flagMaterial = new THREE.MeshBasicMaterial({ map: flagTexture, transparent: true,
      side: THREE.DoubleSide, depthWrite: false, polygonOffset: true,
      polygonOffsetFactor: -3, polygonOffsetUnits: -3 });
    // Size the visible flag in physical space, rather than stretching a small rectangle
    // across a texture whose height changes with the rocket's upper-body dimensions.
    const flagHeight = Math.min(upperBodyHeight * 0.34, radiusMm * 0.9);
    const flagWidth = flagHeight * 1.5;
    const flagRadius = radiusMm + 4;
    const halfFlagAngle = Math.asin(flagWidth / (2 * flagRadius));
    const flagDecal = new THREE.Mesh(new THREE.CylinderGeometry(flagRadius, flagRadius,
      flagHeight, 48, 1, true, -halfFlagAngle, halfFlagAngle * 2), flagMaterial);
    flagDecal.position.y = displayAxialLengthMm / 2 + upperBodyHeight / 2;
    rocket.add(flagDecal);
    scene.add(new THREE.HemisphereLight(0xffffff, 0x263548, 2.3));
    const key = new THREE.DirectionalLight(0xffffff, 2.5);
    key.position.set(radiusMm * 2, axialLengthMm, radiusMm * 2);
    scene.add(key);
    const seamMaterial = new THREE.LineBasicMaterial({ color: 0xff5964 });
    const seamPoints = [new THREE.Vector3(0, -displayAxialLengthMm / 2, -radiusMm - 1),
      new THREE.Vector3(0, displayAxialLengthMm / 2, -radiusMm - 1)];
    if (showEngineeringGuides) {
      rocket.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(seamPoints), seamMaterial));
    }

    let disposed = false;
    for (const [photoId, url] of photoUrls) {
      void loadImage(url).then((image) => {
        if (disposed) return;
        const slots = manifest.slots.filter((slot) => slot.photoId === photoId);
        if (!slots.length) return;
        const geometry = photoSurfaceGeometry(manifest, slots);
        const texture = new THREE.Texture(image);
        texture.colorSpace = THREE.SRGBColorSpace;
        texture.anisotropy = renderer.capabilities.getMaxAnisotropy();
        texture.minFilter = THREE.LinearMipmapLinearFilter;
        texture.magFilter = THREE.LinearFilter;
        texture.needsUpdate = true;
        const photoMaterial = new THREE.MeshBasicMaterial({ map: texture, side: THREE.DoubleSide,
          polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 });
        photoLayer.add(new THREE.Mesh(geometry, photoMaterial));
      }).catch(() => undefined);
    }
    if (textureUrl && !useSimulationTexture) {
      void createSurfaceTexture(manifest, textureUrl, showEngineeringGuides).then((texture) => {
        if (disposed) { texture.dispose(); return; }
        configureTexture(texture, renderer);
        material.map = texture;
        material.color.set(0xffffff);
        material.needsUpdate = true;
      }).catch(() => undefined);
    } else {
      const texture = createPlaceholderTexture(simulationText, simulationDirection);
      configureTexture(texture, renderer);
      material.map = texture;
      material.color.set(0xffffff);
      material.needsUpdate = true;
    }
    const resize = () => {
      const width = Math.max(1, host.clientWidth); const height = Math.max(1, host.clientHeight);
      renderer.setSize(width, height, false); camera.aspect = width / height; camera.updateProjectionMatrix();
    };
    const observer = new ResizeObserver(resize); observer.observe(host); resize();
    let frame = 0;
    const render = () => { controls.update(); renderer.render(scene, camera); frame = requestAnimationFrame(render); };
    render();
    return () => {
      disposed = true; cancelAnimationFrame(frame); observer.disconnect(); controls.dispose();
      rocket.traverse((object) => {
        if (object instanceof THREE.Mesh || object instanceof THREE.Line) {
          object.geometry.dispose();
        }
      });
      photoLayer.traverse((object) => {
        if (object instanceof THREE.Mesh && object.material instanceof THREE.MeshBasicMaterial) {
          object.material.map?.dispose(); object.material.dispose();
        }
      });
      material.map?.dispose(); material.dispose(); shellMaterial.dispose(); lowerMaterial.dispose();
      engineMaterial.dispose(); navyMaterial.dispose(); redMaterial.dispose(); seamMaterial.dispose();
      flagTexture.dispose(); flagMaterial.dispose();
      renderer.dispose(); renderer.domElement.remove();
    };
  }, [focusMode, manifest, photoUrls, showEngineeringGuides, simulationDirection, simulationText, textureUrl, useSimulationTexture]);

  return <div className="surface-canvas" ref={hostRef} />;
}

function photoSurfaceGeometry(manifest: SurfaceManifest, slots: SurfaceManifest['slots']): THREE.BufferGeometry {
  const positions: number[] = [];
  const uv: number[] = [];
  const { radiusMm, circumferenceMm, axialLengthMm, seamXmm } = manifest.surface;
  const radius = radiusMm + 6;
  const add = (xMm: number, yMm: number, imageU: number, imageV: number) => {
    const angle = Math.PI + 2 * Math.PI * ((xMm - seamXmm) / circumferenceMm);
    positions.push(radius * Math.sin(angle), axialLengthMm / 2 - yMm, radius * Math.cos(angle));
    uv.push(imageU, imageV);
  };
  for (const slot of slots) {
    const { physical, crop } = slot;
    const segments = Math.max(1, Math.min(6, Math.ceil(physical.width / (circumferenceMm / 256))));
    for (let index = 0; index < segments; index += 1) {
      const left = index / segments;
      const right = (index + 1) / segments;
      const leftX = physical.x + physical.width * left;
      const rightX = physical.x + physical.width * right;
      const topV = 1 - crop.y;
      const bottomV = 1 - crop.y - crop.height;
      const leftU = crop.x + crop.width * left;
      const rightU = crop.x + crop.width * right;
      add(leftX, physical.y, leftU, topV);
      add(leftX, physical.y + physical.height, leftU, bottomV);
      add(rightX, physical.y, rightU, topV);
      add(rightX, physical.y, rightU, topV);
      add(leftX, physical.y + physical.height, leftU, bottomV);
      add(rightX, physical.y + physical.height, rightU, bottomV);
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  geometry.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  geometry.computeVertexNormals();
  return geometry;
}

function addBodyBand(rocket: THREE.Group, radiusMm: number, y: number, thickness: number,
    material: THREE.Material) {
  const bandRadius = radiusMm + Math.max(10, radiusMm * 0.04);
  const band = new THREE.Mesh(new THREE.CylinderGeometry(bandRadius, bandRadius,
    thickness, 96, 1, true), material);
  band.position.y = y;
  rocket.add(band);
}

function addStrapOnBoosters(rocket: THREE.Group, radiusMm: number, axialLengthMm: number,
    lowerBodyHeight: number, shellMaterial: THREE.Material, navyMaterial: THREE.Material,
    redMaterial: THREE.Material, engineMaterial: THREE.Material) {
  const boosterRadius = radiusMm * 0.34;
  const boosterHeight = lowerBodyHeight * 0.84;
  const noseHeight = radiusMm * 0.72;
  const lowerBodyBottom = -axialLengthMm / 2 - lowerBodyHeight;
  const boosterY = lowerBodyBottom + boosterHeight / 2 + radiusMm * 0.08;
  const radialDistance = radiusMm + boosterRadius * 0.72;
  for (let index = 0; index < 4; index += 1) {
    const angle = index * Math.PI / 2;
    const x = Math.sin(angle) * radialDistance;
    const z = Math.cos(angle) * radialDistance;
    const booster = new THREE.Mesh(new THREE.CylinderGeometry(boosterRadius, boosterRadius,
      boosterHeight, 48), shellMaterial);
    booster.position.set(x, boosterY, z);
    rocket.add(booster);
    const nose = new THREE.Mesh(new THREE.ConeGeometry(boosterRadius, noseHeight, 48), shellMaterial);
    nose.position.set(x, boosterY + boosterHeight / 2 + noseHeight / 2, z);
    rocket.add(nose);
    const nozzle = new THREE.Mesh(new THREE.CylinderGeometry(boosterRadius * 0.5,
      boosterRadius * 0.72, boosterRadius * 0.72, 40), engineMaterial);
    nozzle.position.set(x, boosterY - boosterHeight / 2 - boosterRadius * 0.36, z);
    rocket.add(nozzle);
    const boosterBandRadius = boosterRadius + Math.max(6, boosterRadius * 0.055);
    const ring = new THREE.Mesh(new THREE.CylinderGeometry(boosterBandRadius,
      boosterBandRadius, boosterRadius * 0.22, 48, 1, true), navyMaterial);
    ring.position.set(x, boosterY + boosterHeight * 0.28, z);
    rocket.add(ring);
    for (let stripe = 0; stripe < 4; stripe += 1) {
      const marker = new THREE.Mesh(new THREE.BoxGeometry(boosterRadius * 0.34,
        boosterHeight * 0.055, Math.max(3, boosterRadius * 0.05)), redMaterial);
      const markerDistance = radialDistance + boosterRadius + 2;
      marker.position.set(Math.sin(angle) * markerDistance,
        boosterY - boosterHeight * (0.1 + stripe * 0.14), Math.cos(angle) * markerDistance);
      marker.rotation.y = angle;
      rocket.add(marker);
    }
  }
}

function createFlagTexture(): THREE.Texture {
  const canvas = document.createElement('canvas');
  canvas.width = 900; canvas.height = 600;
  const context = canvas.getContext('2d')!;
  context.scale(canvas.width / 300, canvas.height / 200);
  context.fillStyle = '#d82936'; context.fillRect(0, 0, 300, 200);
  context.fillStyle = '#ffdc54';
  const largeStar = { x: 50, y: 50 };
  drawFivePointStar(context, largeStar.x, largeStar.y, 30, -Math.PI / 2);
  for (const [x, y] of [[100, 20], [120, 40], [120, 70], [100, 90]]) {
    const pointsTowardLargeStar = Math.atan2(largeStar.y - y, largeStar.x - x);
    drawFivePointStar(context, x, y, 10, pointsTowardLargeStar);
  }
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

function drawFivePointStar(context: CanvasRenderingContext2D, centerX: number, centerY: number,
    outerRadius: number, rotation: number) {
  const innerRadius = outerRadius * 0.382;
  context.beginPath();
  for (let point = 0; point < 10; point += 1) {
    const radius = point % 2 === 0 ? outerRadius : innerRadius;
    const angle = rotation + point * Math.PI / 5;
    const x = centerX + Math.cos(angle) * radius;
    const y = centerY + Math.sin(angle) * radius;
    if (point === 0) context.moveTo(x, y); else context.lineTo(x, y);
  }
  context.closePath();
  context.fill();
}

function createPlaceholderTexture(text: string, direction: 'HORIZONTAL' | 'VERTICAL'): THREE.Texture {
  const width = 2048;
  const height = 2048;
  const canvas = document.createElement('canvas');
  canvas.width = width; canvas.height = height;
  const context = canvas.getContext('2d')!;
  context.fillStyle = '#e8edf3'; context.fillRect(0, 0, width, height);
  const mask = document.createElement('canvas');
  mask.width = width; mask.height = height;
  const maskContext = mask.getContext('2d')!;
  maskContext.fillStyle = '#fff';
  const characters = Array.from(text);
  const vertical = direction === 'VERTICAL';
  const fontSize = vertical ? Math.min(height * 0.155, height * 0.74 / Math.max(1, characters.length))
    : Math.min(height * 0.17, width * 0.82 / Math.max(1, characters.length));
  maskContext.font = `900 ${Math.round(fontSize)}px "PingFang SC", "Microsoft YaHei", sans-serif`;
  maskContext.textAlign = 'center'; maskContext.textBaseline = 'middle';
  const spacing = fontSize * 1.12;
  const start = (vertical ? height : width) / 2 - spacing * (characters.length - 1) / 2;
  const position = (index: number): [number, number] => vertical
    ? [width / 2, start + spacing * index] : [start + spacing * index, height / 2];
  characters.forEach((character, index) => maskContext.fillText(character, ...position(index)));
  const pixels = maskContext.getImageData(0, 0, width, height).data;
  const tile = Math.max(4, Math.round(height / 140));
  const palette = ['#386fa4', '#d84a4a', '#d89b2b', '#4d8f65', '#815ca8', '#cf6f9b'];
  for (let y = 0; y < height; y += tile) {
    for (let x = 0; x < width; x += tile) {
      const sampleX = Math.min(width - 1, x + Math.floor(tile / 2));
      const sampleY = Math.min(height - 1, y + Math.floor(tile / 2));
      if (pixels[(sampleY * width + sampleX) * 4 + 3] < 40) continue;
      const paletteIndex = (Math.floor(x / tile) * 7 + Math.floor(y / tile) * 13) % palette.length;
      context.fillStyle = palette[paletteIndex];
      context.fillRect(x + 1, y + 1, tile - 2, tile - 2);
      context.fillStyle = 'rgba(18,28,40,.48)';
      context.beginPath(); context.arc(x + tile / 2, y + tile * 0.38,
        tile * 0.14, 0, Math.PI * 2); context.fill();
    }
  }
  context.strokeStyle = 'rgba(30,42,56,.88)';
  context.lineWidth = Math.max(2, height * 0.006);
  context.lineJoin = 'round';
  context.font = maskContext.font;
  context.textAlign = 'center'; context.textBaseline = 'middle';
  characters.forEach((character, index) => context.strokeText(character, ...position(index)));
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.wrapS = THREE.RepeatWrapping;
  return texture;
}

function configureTexture(texture: THREE.Texture, renderer: THREE.WebGLRenderer) {
  texture.anisotropy = renderer.capabilities.getMaxAnisotropy();
  texture.minFilter = THREE.LinearFilter;
  texture.magFilter = THREE.LinearFilter;
  texture.generateMipmaps = false;
  texture.needsUpdate = true;
}

async function createSurfaceTexture(manifest: SurfaceManifest, url: string,
    showEngineeringGuides: boolean): Promise<THREE.Texture> {
  const image = await loadImage(url);
  const width = 2048;
  const height = Math.max(512, Math.min(2048,
    Math.round(width * manifest.surface.axialLengthMm / manifest.surface.circumferenceMm)));
  const canvas = document.createElement('canvas'); canvas.width = width; canvas.height = height;
  const context = canvas.getContext('2d')!;
  context.fillStyle = '#f3f6fa'; context.fillRect(0, 0, width, height);
  const toU = (xMm: number) => normalize((xMm - manifest.surface.seamXmm)
    / manifest.surface.circumferenceMm);
  context.save();
  context.beginPath();
  for (const slot of manifest.slots) {
    const physical = slot.physical;
    const x = toU(physical.x) * width;
    const y = physical.y / manifest.surface.axialLengthMm * height;
    const slotWidth = physical.width / manifest.surface.circumferenceMm * width;
    const slotHeight = physical.height / manifest.surface.axialLengthMm * height;
    if (x + slotWidth <= width) context.rect(x, y, slotWidth, slotHeight);
    else {
      context.rect(x, y, width - x, slotHeight);
      context.rect(0, y, x + slotWidth - width, slotHeight);
    }
  }
  context.clip();
  drawWrappedImage(context, image, toU(manifest.mapping.originXmm) * width,
    manifest.mapping.originYmm / manifest.surface.axialLengthMm * height,
    manifest.mapping.printWidthMm / manifest.surface.circumferenceMm * width,
    manifest.mapping.printHeightMm / manifest.surface.axialLengthMm * height, width);
  context.restore();
  if (showEngineeringGuides) {
    for (const region of manifest.regions) {
      const color = region.type === 'NO_PRINT' ? 'rgba(255,50,70,.45)'
        : region.type === 'COMPARTMENT' ? 'rgba(70,170,255,.45)' : 'rgba(255,205,80,.45)';
      context.strokeStyle = color; context.lineWidth = Math.max(2, width / 800);
      drawWrappedRect(context, toU(region.xMm) * width,
        region.yMm / manifest.surface.axialLengthMm * height,
        region.widthMm / manifest.surface.circumferenceMm * width,
        region.heightMm / manifest.surface.axialLengthMm * height, width);
    }
    context.fillStyle = 'rgba(255,55,70,.8)';
    const seamPixels = manifest.surface.seamSafetyMm / manifest.surface.circumferenceMm * width;
    context.fillRect(0, 0, Math.max(2, seamPixels), height);
    context.fillRect(width - Math.max(2, seamPixels), 0, Math.max(2, seamPixels), height);
  }
  const texture = new THREE.CanvasTexture(canvas); texture.colorSpace = THREE.SRGBColorSpace;
  texture.wrapS = THREE.RepeatWrapping; texture.needsUpdate = true;
  return texture;
}

function drawWrappedImage(context: CanvasRenderingContext2D, image: HTMLImageElement,
    x: number, y: number, width: number, height: number, canvasWidth: number) {
  if (x + width <= canvasWidth) { context.drawImage(image, x, y, width, height); return; }
  const firstWidth = canvasWidth - x; const firstRatio = firstWidth / width;
  context.drawImage(image, 0, 0, image.width * firstRatio, image.height, x, y, firstWidth, height);
  context.drawImage(image, image.width * firstRatio, 0, image.width * (1 - firstRatio), image.height,
    0, y, width - firstWidth, height);
}

function drawWrappedRect(context: CanvasRenderingContext2D, x: number, y: number,
    width: number, height: number, canvasWidth: number) {
  if (x + width <= canvasWidth) { context.strokeRect(x, y, width, height); return; }
  context.strokeRect(x, y, canvasWidth - x, height); context.strokeRect(0, y, width - (canvasWidth - x), height);
}

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image(); image.crossOrigin = 'anonymous'; image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('曲面纹理加载失败')); image.src = url;
  });
}
function normalize(value: number) { return value - Math.floor(value); }
