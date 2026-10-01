import { useEffect, useRef } from 'react';
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import type { SurfaceManifest } from './types';

interface Props { manifest: SurfaceManifest; textureUrl: string | null }

export function ThreeSurfaceCanvas({ manifest, textureUrl }: Props) {
  const hostRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    host.appendChild(renderer.domElement);
    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0d1117);
    const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 100_000);
    const { radiusMm, axialLengthMm } = manifest.surface;
    const distance = Math.max(radiusMm * 3.2, axialLengthMm * 1.15);
    camera.position.set(distance, axialLengthMm * 0.15, distance);
    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.target.set(0, 0, 0);
    const geometry = new THREE.CylinderGeometry(radiusMm, radiusMm, axialLengthMm, 128, 1, false);
    const material = new THREE.MeshStandardMaterial({ color: 0xd7dde6, roughness: 0.72,
      metalness: 0.08, side: THREE.DoubleSide });
    const cylinder = new THREE.Mesh(geometry, material);
    cylinder.rotation.y = Math.PI / 2;
    scene.add(cylinder);
    scene.add(new THREE.HemisphereLight(0xffffff, 0x263548, 2.3));
    const key = new THREE.DirectionalLight(0xffffff, 2.5);
    key.position.set(radiusMm * 2, axialLengthMm, radiusMm * 2);
    scene.add(key);
    const seamMaterial = new THREE.LineBasicMaterial({ color: 0xff5964 });
    const seamPoints = [new THREE.Vector3(radiusMm + 1, -axialLengthMm / 2, 0),
      new THREE.Vector3(radiusMm + 1, axialLengthMm / 2, 0)];
    scene.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(seamPoints), seamMaterial));

    let disposed = false;
    if (textureUrl) {
      void createSurfaceTexture(manifest, textureUrl).then((texture) => {
        if (disposed) { texture.dispose(); return; }
        material.map = texture;
        material.color.set(0xffffff);
        material.needsUpdate = true;
      }).catch(() => undefined);
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
      geometry.dispose(); material.map?.dispose(); material.dispose(); seamMaterial.dispose();
      renderer.dispose(); renderer.domElement.remove();
    };
  }, [manifest, textureUrl]);

  return <div className="surface-canvas" ref={hostRef} />;
}

async function createSurfaceTexture(manifest: SurfaceManifest, url: string): Promise<THREE.Texture> {
  const image = await loadImage(url);
  const width = 2048;
  const height = Math.max(512, Math.min(2048,
    Math.round(width * manifest.surface.axialLengthMm / manifest.surface.circumferenceMm)));
  const canvas = document.createElement('canvas'); canvas.width = width; canvas.height = height;
  const context = canvas.getContext('2d')!;
  context.fillStyle = '#20252d'; context.fillRect(0, 0, width, height);
  const toU = (xMm: number) => normalize((xMm - manifest.surface.seamXmm)
    / manifest.surface.circumferenceMm);
  drawWrappedImage(context, image, toU(manifest.mapping.originXmm) * width,
    manifest.mapping.originYmm / manifest.surface.axialLengthMm * height,
    manifest.mapping.printWidthMm / manifest.surface.circumferenceMm * width,
    manifest.mapping.printHeightMm / manifest.surface.axialLengthMm * height, width);
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
