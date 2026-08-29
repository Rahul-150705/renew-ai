// FaceExtractor.js
// Loads the portrait image onto an offscreen canvas, samples pixels,
// rejects the background (sky-blue ~#4AABDF), and returns a particle array
// with per-particle { x, y, z, r, g, b, size, brightness } data.

export async function extractFaceParticles(imageSrc, targetCount = 80000) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      const canvas = document.createElement('canvas');
      const W = 512;
      const H = Math.round(W * (img.height / img.width));
      canvas.width = W;
      canvas.height = H;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(img, 0, 0, W, H);
      const imageData = ctx.getImageData(0, 0, W, H);
      const data = imageData.data;

      // --- Background keying ---
      // The background is a flat sky-blue ~(74, 171, 223)
      const BG_R = 74, BG_G = 171, BG_B = 223;
      const BG_THRESHOLD = 38; // colour distance for rejection

      const colourDist = (r, g, b) =>
        Math.sqrt((r - BG_R) ** 2 + (g - BG_G) ** 2 + (b - BG_B) ** 2);

      // Collect candidate pixels
      const candidates = [];
      for (let y = 0; y < H; y++) {
        for (let x = 0; x < W; x++) {
          const i = (y * W + x) * 4;
          const r = data[i], g = data[i + 1], b = data[i + 2], a = data[i + 3];
          if (a < 128) continue;
          if (colourDist(r, g, b) < BG_THRESHOLD) continue;
          candidates.push({ x, y, r, g, b });
        }
      }

      // Sub-sample if more than targetCount
      const step = Math.max(1, Math.floor(candidates.length / targetCount));
      const sampled = candidates.filter((_, i) => i % step === 0).slice(0, targetCount);

      // Build particle data
      const particles = sampled.map(({ x, y, r, g, b }) => {
        const nx = (x / W - 0.5) * 2;          // [-1, +1]  left→right
        const ny = -(y / H - 0.5) * 2;          // [-1, +1]  bottom→top
        const lum = (r * 0.299 + g * 0.587 + b * 0.114) / 255;

        // Pseudo-depth: brighter central pixels are nearer
        // Nose/forehead area → high lum → z > 0
        // Jaw/shadow → lower lum → z < 0
        const depthBase = lum;
        // Add radial depth: centre of face closer
        const radial = 1 - Math.sqrt(nx * nx + ny * ny);
        const z = (depthBase * 0.6 + radial * 0.4) * 0.5; // [-0.5, +0.5] → mostly positive

        // Feature-region sizing
        // Eyes, lips, nose → higher detail → smaller particles
        const eyeRegion =
          (Math.abs(ny - 0.2) < 0.12 && Math.abs(nx) < 0.35) ||
          (Math.abs(ny - 0.35) < 0.09 && Math.abs(nx) < 0.15); // nose bridge
        const lipRegion = Math.abs(ny + 0.1) < 0.1 && Math.abs(nx) < 0.22;
        const hairRegion = ny > 0.55;
        let size = 1.0;
        if (eyeRegion) size = 0.55;
        else if (lipRegion) size = 0.65;
        else if (hairRegion) size = 1.3;

        return { x: nx, y: ny, z, r: r / 255, g: g / 255, b: b / 255, lum, size };
      });

      resolve({ particles, aspectRatio: W / H });
    };
    img.onerror = reject;
    img.src = imageSrc;
  });
}
