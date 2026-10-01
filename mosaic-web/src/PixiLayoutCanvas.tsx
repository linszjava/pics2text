import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';
import { Application, Assets, Container, Graphics, Sprite, Texture } from 'pixi.js';
import type { Assignment } from './types';
import { fitTransform, visibleBounds, zoomAt, type Bounds, type ViewTransform } from './viewport';

export interface CanvasHandle {
  zoom(factor: number): void;
  fit(): void;
  locate(assignment: Assignment): void;
}

interface Props {
  canvasWidth: number;
  canvasHeight: number;
  assignments: Assignment[];
  photoUrls: Map<string, string>;
  selectedSlot: number | null;
  onSelect(assignment: Assignment): void;
  onViewport(bounds: Bounds): void;
}

export const PixiLayoutCanvas = forwardRef<CanvasHandle, Props>(function PixiLayoutCanvas(props, ref) {
  const hostRef = useRef<HTMLDivElement>(null);
  const appRef = useRef<Application | null>(null);
  const worldRef = useRef<Container | null>(null);
  const transformRef = useRef<ViewTransform>({ scale: 1, x: 0, y: 0 });
  const propsRef = useRef(props);
  propsRef.current = props;

  const applyTransform = () => {
    const world = worldRef.current;
    if (!world) return;
    const value = transformRef.current;
    world.scale.set(value.scale);
    world.position.set(value.x, value.y);
    const app = appRef.current;
    if (app) propsRef.current.onViewport(visibleBounds(value, app.screen.width, app.screen.height,
      propsRef.current.canvasWidth, propsRef.current.canvasHeight));
  };

  useImperativeHandle(ref, () => ({
    zoom(factor) {
      const app = appRef.current;
      if (!app) return;
      transformRef.current = zoomAt(transformRef.current, app.screen.width / 2, app.screen.height / 2, factor);
      applyTransform();
    },
    fit() {
      const app = appRef.current;
      if (!app) return;
      transformRef.current = fitTransform(app.screen.width, app.screen.height,
        propsRef.current.canvasWidth, propsRef.current.canvasHeight);
      applyTransform();
    },
    locate(assignment) {
      const app = appRef.current;
      if (!app) return;
      const scale = Math.max(transformRef.current.scale, 1.2);
      transformRef.current = {
        scale,
        x: app.screen.width / 2 - (assignment.x + assignment.width / 2) * scale,
        y: app.screen.height / 2 - (assignment.y + assignment.height / 2) * scale,
      };
      applyTransform();
    },
  }));

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const app = new Application();
    let disposed = false;
    let dragging = false;
    let lastX = 0;
    let lastY = 0;
    void app.init({ resizeTo: host, background: '#12161d', antialias: true }).then(() => {
      if (disposed) { app.destroy(true); return; }
      appRef.current = app;
      const world = new Container();
      worldRef.current = world;
      app.stage.addChild(world);
      host.appendChild(app.canvas);
      transformRef.current = fitTransform(app.screen.width, app.screen.height,
        propsRef.current.canvasWidth, propsRef.current.canvasHeight);
      applyTransform();
      app.canvas.addEventListener('wheel', onWheel, { passive: false });
      app.canvas.addEventListener('pointerdown', onDown);
      window.addEventListener('pointermove', onMove);
      window.addEventListener('pointerup', onUp);
    });
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      const rect = app.canvas.getBoundingClientRect();
      transformRef.current = zoomAt(transformRef.current, event.clientX - rect.left,
        event.clientY - rect.top, event.deltaY < 0 ? 1.15 : 1 / 1.15);
      applyTransform();
    };
    const onDown = (event: PointerEvent) => {
      if (event.button !== 0 || (event.target as HTMLElement).dataset.slot === 'true') return;
      dragging = true; lastX = event.clientX; lastY = event.clientY;
      app.canvas.setPointerCapture?.(event.pointerId);
    };
    const onMove = (event: PointerEvent) => {
      if (!dragging) return;
      transformRef.current = { ...transformRef.current,
        x: transformRef.current.x + event.clientX - lastX,
        y: transformRef.current.y + event.clientY - lastY };
      lastX = event.clientX; lastY = event.clientY; applyTransform();
    };
    const onUp = () => { dragging = false; };
    return () => {
      disposed = true;
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      if (appRef.current) {
        app.canvas.removeEventListener('wheel', onWheel);
        app.canvas.removeEventListener('pointerdown', onDown);
        app.destroy(true, { children: true });
      }
      appRef.current = null; worldRef.current = null;
    };
  }, []);

  useEffect(() => {
    const world = worldRef.current;
    if (!world) return;
    const old = world.removeChildren();
    for (const child of old) child.destroy({ children: true });
    const border = new Graphics().rect(0, 0, props.canvasWidth, props.canvasHeight)
      .stroke({ color: 0x526173, width: 2 / transformRef.current.scale });
    world.addChild(border);
    let active = true;
    for (const assignment of props.assignments) {
      const cell = new Container();
      cell.position.set(assignment.x, assignment.y);
      cell.eventMode = 'static';
      cell.cursor = 'pointer';
      cell.hitArea = { contains: (x: number, y: number) => x >= 0 && y >= 0
        && x <= assignment.width && y <= assignment.height };
      cell.on('pointertap', () => propsRef.current.onSelect(assignment));
      const placeholder = new Graphics().rect(0, 0, assignment.width, assignment.height)
        .fill(colorFromId(assignment.photoId));
      cell.addChild(placeholder);
      if (assignment.slotIndex === props.selectedSlot) {
        cell.addChild(new Graphics().rect(0, 0, assignment.width, assignment.height)
          .stroke({ color: 0xffc857, width: Math.max(1, 2 / transformRef.current.scale) }));
      }
      world.addChild(cell);
      const url = props.photoUrls.get(assignment.photoId);
      if (url) void Assets.load<Texture>(url).then((texture) => {
        if (!active || cell.destroyed) return;
        const sprite = new Sprite(texture);
        const fullWidth = assignment.width / assignment.cropWidth;
        const fullHeight = assignment.height / assignment.cropHeight;
        sprite.position.set(-assignment.cropX * fullWidth, -assignment.cropY * fullHeight);
        sprite.width = fullWidth; sprite.height = fullHeight;
        const mask = new Graphics().rect(0, 0, assignment.width, assignment.height).fill(0xffffff);
        cell.addChildAt(sprite, 0); cell.addChild(mask); sprite.mask = mask;
      }).catch(() => undefined);
    }
    return () => { active = false; };
  }, [props.assignments, props.photoUrls, props.selectedSlot, props.canvasWidth, props.canvasHeight]);

  return <div className="canvas-host" ref={hostRef} />;
});

function colorFromId(id: string): number {
  let hash = 0;
  for (let index = 0; index < id.length; index++) hash = (hash * 31 + id.charCodeAt(index)) >>> 0;
  return ((hash & 0x7f7f7f) | 0x404040) >>> 0;
}
