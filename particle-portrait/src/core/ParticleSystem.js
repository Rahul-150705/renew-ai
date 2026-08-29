// ParticleSystem.js
// Builds the Three.js BufferGeometry + ShaderMaterial for the particle cloud.
// All visual state (face ↔ sculpture morph) is driven purely by GPU uniforms.

import * as THREE from 'three';
import particleVert from '../shaders/particle.vert';
import particleFrag from '../shaders/particle.frag';

export class ParticleSystem {
  constructor(particleData, renderer) {
    this.count = particleData.length;
    this.renderer = renderer;
    this._buildGeometry(particleData);
    this._buildMaterial();
    this.mesh = new THREE.Points(this.geometry, this.material);
  }

  _buildGeometry(particleData) {
    const count = this.count;
    const positions   = new Float32Array(count * 3);
    const origins     = new Float32Array(count * 3);
    const targets     = new Float32Array(count * 3);
    const colors      = new Float32Array(count * 3);
    const sizes       = new Float32Array(count);
    const phases      = new Float32Array(count);   // random per-particle offset
    const luminances  = new Float32Array(count);

    // Sculpture target: abstract flowing torus-like field
    // We use a parametric surface so particles form an elegant form
    const totalTwoPi = Math.PI * 2;

    for (let i = 0; i < count; i++) {
      const p = particleData[i];

      // Face positions (origin)
      const fx = p.x * 1.0;
      const fy = p.y * 1.1;
      const fz = p.z * 0.4;

      positions[i * 3]     = fx;
      positions[i * 3 + 1] = fy;
      positions[i * 3 + 2] = fz;

      origins[i * 3]     = fx;
      origins[i * 3 + 1] = fy;
      origins[i * 3 + 2] = fz;

      // Sculpture target: flowing toroidal sculpture
      const theta = (i / count) * totalTwoPi * 7.3;
      const phi   = (i / count) * totalTwoPi * 3.1;
      const R = 0.6 + Math.sin(phi * 2.4) * 0.25;
      const tx = (R + 0.2 * Math.cos(phi)) * Math.cos(theta);
      const ty = (R + 0.2 * Math.cos(phi)) * Math.sin(theta) * 0.6;
      const tz = 0.3 * Math.sin(phi);

      targets[i * 3]     = tx;
      targets[i * 3 + 1] = ty;
      targets[i * 3 + 2] = tz;

      // Color: keep actual sampled color for face phase
      colors[i * 3]     = p.r;
      colors[i * 3 + 1] = p.g;
      colors[i * 3 + 2] = p.b;

      sizes[i]      = p.size;
      phases[i]     = Math.random();
      luminances[i] = p.lum;
    }

    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geo.setAttribute('aOrigin',  new THREE.BufferAttribute(origins,   3));
    geo.setAttribute('aTarget',  new THREE.BufferAttribute(targets,   3));
    geo.setAttribute('aColor',   new THREE.BufferAttribute(colors,    3));
    geo.setAttribute('aSize',    new THREE.BufferAttribute(sizes,     1));
    geo.setAttribute('aPhase',   new THREE.BufferAttribute(phases,    1));
    geo.setAttribute('aLum',     new THREE.BufferAttribute(luminances,1));
    this.geometry = geo;
  }

  _buildMaterial() {
    this.uniforms = {
      uTime:       { value: 0 },
      uPhase:      { value: 0 },       // 0→6 continuous shader phase
      uPixelRatio: { value: Math.min(window.devicePixelRatio, 2) },
      uCorePos:    { value: new THREE.Vector3(0, 0, 0) },
      uMouse:      { value: new THREE.Vector2(0, 0) },
    };

    this.material = new THREE.ShaderMaterial({
      uniforms:       this.uniforms,
      vertexShader:   particleVert,
      fragmentShader: particleFrag,
      transparent:    true,
      depthWrite:     false,
      blending:       THREE.AdditiveBlending,
      vertexColors:   false,
    });
  }

  update(time, shaderPhase, mouse) {
    this.uniforms.uTime.value  = time;
    this.uniforms.uPhase.value = shaderPhase;
    this.uniforms.uMouse.value.set(mouse.x, mouse.y);
  }

  dispose() {
    this.geometry.dispose();
    this.material.dispose();
  }
}
