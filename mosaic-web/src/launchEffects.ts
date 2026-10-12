import * as THREE from 'three';

export function launchMotion(elapsed: number, height: number) {
  const progress = THREE.MathUtils.clamp(elapsed / 10, 0, 1);
  const lift = Math.max(0, progress - 0.2) / 0.8;
  return { progress, ascent: height * 1.25 * lift * lift, complete: progress === 1 };
}

/** Local-space turbulent exhaust; its origin stays attached to each nozzle. */
export function createExhaustMaterial() {
  return new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, side: THREE.DoubleSide,
    blending: THREE.AdditiveBlending,
    uniforms: { time: { value: 0 }, power: { value: 1 } },
    vertexShader: `varying vec2 vUv;
      void main() { vUv = uv;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
    fragmentShader: `varying vec2 vUv; uniform float time; uniform float power;
      void main() {
        float along = 1.0 - vUv.y;
        float waves = sin(vUv.x*68.0 + along*28.0 - time*29.0)
          * sin(vUv.x*39.0 - along*53.0 + time*18.0);
        float diamonds = pow(0.5 + 0.5*cos(along*52.0-time*2.0), 5.0);
        float fade = pow(1.0-along, 0.7) * (0.58 + waves*0.23);
        vec3 hot = mix(vec3(0.42,0.56,1.0), vec3(1.0,0.85,0.52), smoothstep(0.0,0.2,along));
        hot = mix(hot, vec3(1.0,0.19,0.025), smoothstep(0.15,1.0,along));
        gl_FragColor = vec4(hot*(1.2+diamonds*0.7), fade*power);
      }`,
  });
}

export function createLaunchSmoke(scene: THREE.Scene, r: number, groundY: number) {
  const canvas = document.createElement('canvas'); canvas.width = canvas.height = 128;
  const context = canvas.getContext('2d')!;
  const gradient = context.createRadialGradient(64, 64, 4, 64, 64, 62);
  gradient.addColorStop(0, 'rgba(225,224,217,1)');
  gradient.addColorStop(0.48, 'rgba(205,207,201,0.85)');
  gradient.addColorStop(0.75, 'rgba(183,190,185,0.4)');
  gradient.addColorStop(1, 'rgba(166,170,163,0)');
  context.fillStyle = gradient; context.fillRect(0, 0, 128, 128);
  const texture = new THREE.CanvasTexture(canvas); texture.colorSpace = THREE.SRGBColorSpace;
  const particles = Array.from({ length: 260 }, (_, index) => {
    const material = new THREE.SpriteMaterial({ map: texture, transparent: true,
      depthWrite: false, opacity: 0, rotation: index * 2.39996 });
    const sprite = new THREE.Sprite(material); sprite.visible = false; scene.add(sprite);
    return sprite;
  });
  return {
    update(elapsed: number) {
      particles.forEach((sprite, index) => {
        // Dense initial burst, followed by a longer sustained emission.
        const born = index < 70 ? index * 0.007 : 0.5 + (index - 70) * 0.03;
        const age = elapsed - born;
        sprite.visible = age >= 0 && age < 13;
        if (!sprite.visible) return;
        const angle = index * 2.39996;
        const spread = r * (0.3 + age * 1.8);
        const side = index % 2 === 0 ? 1 : -1;
        sprite.position.set(Math.cos(angle) * spread * 0.8,
          groundY + r * (0.75 + age * 0.6 + Math.sin(age * 1.4 + angle) * 0.15),
          side * spread * 1.3 + Math.sin(angle) * r);
        const size = r * (2.5 + age * 2.2);
        sprite.scale.set(size * 1.2, size, 1);
        sprite.material.rotation = angle + side * age * 0.12;
        sprite.material.opacity = Math.min(1, age * 8) * (1 - THREE.MathUtils.smoothstep(age, 5, 13)) * 0.78;
      });
    },
    reset() { particles.forEach((sprite) => { sprite.visible = false; }); },
    dispose() {
      particles.forEach((sprite) => { scene.remove(sprite); sprite.material.dispose(); });
      texture.dispose();
    },
  };
}
