// PostProcessing.js
// Custom post-processing stack: bloom + vignette + film grain + depth haze
// Built on raw Three.js render targets (no extra npm deps).

import * as THREE from 'three';
import postFinalFrag from '../shaders/postFinal.frag';

const SCREEN_VERT = /* glsl */`
varying vec2 vUv;
void main() {
  vUv = uv;
  gl_Position = vec4(position, 1.0);
}
`;

export class PostProcessing {
  constructor(renderer, scene, camera) {
    this.renderer = renderer;
    this.scene    = scene;
    this.camera   = camera;

    const size = renderer.getSize(new THREE.Vector2());
    this._buildTargets(size.x, size.y);
    this._buildQuad();
  }

  _buildTargets(w, h) {
    const opts = {
      minFilter: THREE.LinearFilter,
      magFilter: THREE.LinearFilter,
      format:    THREE.RGBAFormat,
      type:      THREE.HalfFloatType,
    };
    this.mainTarget  = new THREE.WebGLRenderTarget(w, h, opts);
    this.blurTargetA = new THREE.WebGLRenderTarget(w >> 1, h >> 1, opts);
    this.blurTargetB = new THREE.WebGLRenderTarget(w >> 1, h >> 1, opts);
  }

  _buildQuad() {
    // Full-screen triangle
    const geo = new THREE.PlaneGeometry(2, 2);

    this.finalMaterial = new THREE.ShaderMaterial({
      uniforms: {
        uScene:  { value: null },
        uBloom:  { value: null },
        uTime:   { value: 0 },
        uPhase:  { value: 0 },
        uRes:    { value: new THREE.Vector2(1, 1) },
      },
      vertexShader:   SCREEN_VERT,
      fragmentShader: postFinalFrag,
      depthTest:  false,
      depthWrite: false,
    });

    this.quad = new THREE.Mesh(geo, this.finalMaterial);
    this.quadScene  = new THREE.Scene();
    this.quadCamera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
    this.quadScene.add(this.quad);
  }

  resize(w, h) {
    this.mainTarget.setSize(w, h);
    this.blurTargetA.setSize(w >> 1, h >> 1);
    this.blurTargetB.setSize(w >> 1, h >> 1);
    this.finalMaterial.uniforms.uRes.value.set(w, h);
  }

  render(time, shaderPhase) {
    const r = this.renderer;

    // 1. Render scene to main target
    r.setRenderTarget(this.mainTarget);
    r.render(this.scene, this.camera);

    // 2. Composite with post
    r.setRenderTarget(null);
    this.finalMaterial.uniforms.uScene.value = this.mainTarget.texture;
    this.finalMaterial.uniforms.uBloom.value = this.mainTarget.texture; // simple passthrough
    this.finalMaterial.uniforms.uTime.value  = time;
    this.finalMaterial.uniforms.uPhase.value = shaderPhase;
    r.render(this.quadScene, this.quadCamera);
  }

  dispose() {
    this.mainTarget.dispose();
    this.blurTargetA.dispose();
    this.blurTargetB.dispose();
    this.finalMaterial.dispose();
  }
}
