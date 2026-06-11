/**
 * React Bits Strands (vanilla ogl port).
 * Mount inside #avatarStrands; lip-sync bar stays a separate DOM overlay.
 */
import { Renderer, Program, Mesh, Color, Triangle, RenderTarget } from 'https://esm.sh/ogl@1.0.11';

const MAX_STRANDS = 12;
const MAX_COLORS = 8;

/** 对齐 React Bits Strands；主持条 80px 高时用 AVATAR_BAR 覆盖 scale/亮度 */
const DEFAULTS = {
  colors: ['#F97316', '#7C3AED', '#06B6D4', '#22D3EE', '#EAB308'],
  count: 3,
  speed: 0.5,
  amplitude: 1,
  waviness: 1,
  thickness: 0.78,
  glow: 3.0,
  taper: 3,
  spread: 1.1,
  hueShift: 0,
  intensity: 0.68,
  saturation: 1.6,
  opacity: 1,
  scale: 1.15,
  glass: false,
  refraction: 1,
  dispersion: 1,
  glassSize: 1
};

/** 主持页横向条（#avatarStrands）专用；避免方形 fallback 分辨率拉扁成细线 */
const AVATAR_BAR = {
  scale: 1.05,
  amplitude: 1.12,
  thickness: 0.88,
  glow: 3.55,
  intensity: 0.82,
  spread: 1.15
};

const SPEAKING = {
  intensity: 0.82,
  glow: 3.2,
  speed: 0.72,
  amplitude: 1.12,
  waviness: 1.08
};

const VERT = `#version 300 es
in vec2 position;
void main() {
  gl_Position = vec4(position, 0.0, 1.0);
}
`;

const FRAG = `#version 300 es
precision highp float;

uniform float uTime;
uniform vec2 uResolution;
uniform vec3 uColors[${MAX_COLORS}];
uniform int uColorCount;
uniform int uStrandCount;
uniform float uSpeed;
uniform float uAmplitude;
uniform float uWaviness;
uniform float uThickness;
uniform float uGlow;
uniform float uTaper;
uniform float uSpread;
uniform float uHueShift;
uniform float uIntensity;
uniform float uOpacity;
uniform float uScale;
uniform float uSaturation;

out vec4 fragColor;

const float PI = 3.14159265;

vec3 spectrum(float t) {
  return 0.5 + 0.5 * cos(2.0 * PI * (t + vec3(0.00, 0.33, 0.67)));
}

vec3 samplePalette(float t) {
  t = fract(t);
  float scaled = t * float(uColorCount);
  int idx = int(floor(scaled));
  float blend = fract(scaled);
  int nextIdx = idx + 1;
  if (nextIdx >= uColorCount) nextIdx = 0;
  return mix(uColors[idx], uColors[nextIdx], blend);
}

vec3 strandColor(float t) {
  if (uColorCount > 0) return samplePalette(t);
  return spectrum(t);
}

void main() {
  vec2 uv = (gl_FragCoord.xy - 0.5 * uResolution) / uResolution.y;
  float aspect = uResolution.x / max(uResolution.y, 1.0);
  uv.x /= max(aspect, 1.0);
  uv /= max(uScale, 0.0001);

  float e = 0.06 + uIntensity * 0.94;
  float env = pow(max(cos(uv.x * PI * 1.3), 0.0), uTaper);

  vec3 col = vec3(0.0);

  for (int i = 0; i < ${MAX_STRANDS}; i++) {
    if (i >= uStrandCount) break;

    float fi = float(i);
    float ph = fi * 1.7 * uSpread;
    float freq = (2.0 + fi * 0.35) * uWaviness;
    float spd = 1.4 + fi * 1.2;

    float tt = uTime * uSpeed;
    float w = sin(uv.x * freq + tt * spd + ph) * 0.60
            + sin(uv.x * freq * 1.1 - tt * spd * 0.7 + ph * 1.7) * 0.40;

    float amp = (0.1 + 0.02 * e) * env * uAmplitude;
    float y = w * amp;

    float d = abs(uv.y - y);
    float thick = (0.001 + 0.05 * e) * (0.35 + env) * uThickness;
    float g = thick / (d + thick * 0.45);
    g = g * g;

    float h = fi / float(uStrandCount) + uv.x * 0.30 + uTime * 0.04 + uHueShift;
    col += strandColor(h) * g * env;
  }

  col *= 0.45 + 0.7 * e;
  col = 1.0 - exp(-col * uGlow);

  float gray = dot(col, vec3(0.2126, 0.7152, 0.0722));
  col = max(mix(vec3(gray), col, uSaturation), 0.0);

  float lum = max(max(col.r, col.g), col.b);
  float alpha = clamp(lum, 0.0, 1.0) * uOpacity;

  fragColor = vec4(col * uOpacity, alpha);
}
`;

const GLASS_FRAG = `#version 300 es
precision highp float;

uniform sampler2D uScene;
uniform vec2 uResolution;
uniform float uRadius;
uniform float uRefraction;
uniform float uDispersion;

out vec4 fragColor;

vec2 toUv(vec2 p) {
  return p * (uResolution.y / uResolution) + 0.5;
}

void main() {
  vec2 p = (gl_FragCoord.xy - 0.5 * uResolution) / uResolution.y;
  float d = length(p);
  float r = uRadius;

  float edge = fwidth(d) * 1.5;
  float mask = 1.0 - smoothstep(r - edge, r + edge, d);
  if (mask <= 0.0) {
    fragColor = vec4(0.0);
    return;
  }

  float z = sqrt(max(r * r - d * d, 0.0)) / r;
  float nd = d / r;

  vec2 dir = d > 0.0 ? p / d : vec2(0.0);
  float lens = smoothstep(0.85, 1.0, nd) * pow(nd, 6.0);
  vec2 offset = -dir * lens * uRefraction * 0.15;
  vec2 disp = -dir * lens * uDispersion * 0.012;

  vec3 light;
  light.r = texture(uScene, toUv(p + offset - disp)).r;
  light.g = texture(uScene, toUv(p + offset)).g;
  light.b = texture(uScene, toUv(p + offset + disp)).b;

  float fres = pow(1.0 - z, 3.0);
  vec3 rim = vec3(1.0) * fres * 0.18;

  vec2 lightDir = normalize(vec2(-0.55, 0.6));
  float spec = pow(max(dot(p / max(r, 1e-4), lightDir), 0.0), 6.0);
  spec *= smoothstep(r, r * 0.55, d);

  vec3 emissive = light + rim + vec3(spec) * 0.4;
  float emissiveA = clamp(max(max(emissive.r, emissive.g), emissive.b), 0.0, 1.0);

  float bodyA = 0.05 + fres * 0.05;

  float outA = emissiveA + bodyA * (1.0 - emissiveA);
  vec3 outRGB = emissive;

  outRGB *= mask;
  outA *= mask;

  fragColor = vec4(outRGB, outA);
}
`;

function buildPalette(colors) {
  const filled = colors && colors.length ? colors : ['#ffffff'];
  const padded = [];
  for (let i = 0; i < MAX_COLORS; i++) {
    const hex = filled[i] ?? filled[filled.length - 1];
    const c = new Color(hex);
    padded.push([c.r, c.g, c.b]);
  }
  return padded;
}

function prefersReducedMotion() {
  return typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function measureMountSize(mountEl) {
  const figure = mountEl && mountEl.closest ? mountEl.closest('.avatar-figure') : null;
  const rect = mountEl.getBoundingClientRect();
  let width = Math.round(rect.width) || mountEl.offsetWidth;
  let height = Math.round(rect.height) || mountEl.offsetHeight;
  if ((!width || !height) && figure) {
    const fr = figure.getBoundingClientRect();
    width = width || Math.round(fr.width) || figure.offsetWidth;
    height = height || Math.round(fr.height) || figure.offsetHeight;
  }
  return {
    width: Math.max(width || 280, 24),
    height: Math.max(height || 80, 24)
  };
}

/**
 * @param {HTMLElement} mountEl
 * @param {object} [options]
 * @returns {{ setState: Function, dispose: Function, active: boolean }}
 */
export function initHostAvatarStrands(mountEl, options) {
  const figure = mountEl && mountEl.closest ? mountEl.closest('.avatar-figure') : null;
  const props = Object.assign({}, DEFAULTS, options || {});
  let runtime = Object.assign({}, props);
  let animateId = 0;
  let resizeObserver = null;
  let windowResizeHandler = null;
  let disposed = false;
  let active = false;
  let gl = null;

  function useCssFallback() {
    if (figure) {
      figure.classList.add('avatar-figure--css-fallback');
      figure.classList.remove('avatar-strands-active');
    }
    if (mountEl) mountEl.innerHTML = '';
    active = false;
  }

  function cleanup() {
    disposed = true;
    cancelAnimationFrame(animateId);
    if (resizeObserver) {
      resizeObserver.disconnect();
      resizeObserver = null;
    }
    if (windowResizeHandler) {
      window.removeEventListener('resize', windowResizeHandler);
      windowResizeHandler = null;
    }
    if (mountEl) mountEl.innerHTML = '';
    if (figure) figure.classList.remove('avatar-strands-active');
    if (gl) gl.getExtension('WEBGL_lose_context')?.loseContext();
    active = false;
  }

  function setState(state) {
    if (!active) return;
    if (state === 'speaking') {
      runtime.intensity = SPEAKING.intensity;
      runtime.glow = SPEAKING.glow;
      runtime.speed = SPEAKING.speed;
      runtime.amplitude = SPEAKING.amplitude;
      runtime.waviness = SPEAKING.waviness;
    } else {
      runtime.intensity = props.intensity;
      runtime.glow = props.glow;
      runtime.speed = props.speed;
      runtime.amplitude = props.amplitude;
      runtime.waviness = props.waviness;
    }
  }

  if (!mountEl || prefersReducedMotion()) {
    useCssFallback();
    return { setState, dispose: cleanup, active: false };
  }

  try {
    const renderer = new Renderer({
      alpha: true,
      premultipliedAlpha: true,
      antialias: true
    });
    gl = renderer.gl;
    gl.clearColor(0, 0, 0, 0);
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
    gl.canvas.style.backgroundColor = 'transparent';

    const geometry = new Triangle(gl);
    if (geometry.attributes.uv) {
      delete geometry.attributes.uv;
    }

    const program = new Program(gl, {
      vertex: VERT,
      fragment: FRAG,
      uniforms: {
        uTime: { value: 0 },
        uResolution: { value: (() => { const s = measureMountSize(mountEl); return [s.width, s.height]; })() },
        uColors: { value: buildPalette(runtime.colors) },
        uColorCount: { value: Math.min(runtime.colors.length, MAX_COLORS) },
        uStrandCount: { value: Math.min(runtime.count, MAX_STRANDS) },
        uSpeed: { value: runtime.speed },
        uAmplitude: { value: runtime.amplitude },
        uWaviness: { value: runtime.waviness },
        uThickness: { value: runtime.thickness },
        uGlow: { value: runtime.glow },
        uTaper: { value: runtime.taper },
        uSpread: { value: runtime.spread },
        uHueShift: { value: runtime.hueShift },
        uIntensity: { value: runtime.intensity },
        uOpacity: { value: runtime.opacity },
        uScale: { value: runtime.scale },
        uSaturation: { value: runtime.saturation }
      }
    });

    const mesh = new Mesh(gl, { geometry, program });

    const initSize = measureMountSize(mountEl);
    const w = initSize.width;
    const h = initSize.height;
    const renderTarget = new RenderTarget(gl, { width: w, height: h });

    const glassProgram = new Program(gl, {
      vertex: VERT,
      fragment: GLASS_FRAG,
      uniforms: {
        uScene: { value: renderTarget.texture },
        uResolution: { value: [w, h] },
        uRadius: { value: 0.46 * runtime.glassSize },
        uRefraction: { value: runtime.refraction },
        uDispersion: { value: runtime.dispersion }
      }
    });
    const glassMesh = new Mesh(gl, { geometry, program: glassProgram });

    mountEl.appendChild(gl.canvas);

    function resize() {
      if (!mountEl || disposed) return;
      const size = measureMountSize(mountEl);
      const width = size.width;
      const height = size.height;
      renderer.setSize(width, height);
      program.uniforms.uResolution.value = [width, height];
      renderTarget.setSize(width, height);
      glassProgram.uniforms.uResolution.value = [width, height];
    }

    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(resize);
      resizeObserver.observe(mountEl);
    } else {
      windowResizeHandler = resize;
      window.addEventListener('resize', windowResizeHandler);
    }
    resize();

    if (figure) {
      figure.classList.add('avatar-strands-active');
      figure.classList.remove('avatar-figure--css-fallback');
    }

    const update = (t) => {
      if (disposed) return;
      animateId = requestAnimationFrame(update);
      program.uniforms.uTime.value = t * 0.001;
      program.uniforms.uColors.value = buildPalette(runtime.colors);
      program.uniforms.uColorCount.value = Math.min(runtime.colors.length, MAX_COLORS);
      program.uniforms.uStrandCount.value = Math.min(Math.max(Math.round(runtime.count), 1), MAX_STRANDS);
      program.uniforms.uSpeed.value = runtime.speed;
      program.uniforms.uAmplitude.value = runtime.amplitude;
      program.uniforms.uWaviness.value = runtime.waviness;
      program.uniforms.uThickness.value = runtime.thickness;
      program.uniforms.uGlow.value = runtime.glow;
      program.uniforms.uTaper.value = runtime.taper;
      program.uniforms.uSpread.value = runtime.spread;
      program.uniforms.uHueShift.value = runtime.hueShift;
      program.uniforms.uIntensity.value = runtime.intensity;
      program.uniforms.uOpacity.value = runtime.opacity;
      program.uniforms.uScale.value = runtime.scale;
      program.uniforms.uSaturation.value = runtime.saturation;

      if (runtime.glass) {
        renderer.render({ scene: mesh, target: renderTarget });
        glassProgram.uniforms.uScene.value = renderTarget.texture;
        glassProgram.uniforms.uRefraction.value = runtime.refraction;
        glassProgram.uniforms.uDispersion.value = runtime.dispersion;
        glassProgram.uniforms.uRadius.value = 0.46 * runtime.glassSize;
        renderer.render({ scene: glassMesh });
      } else {
        renderer.render({ scene: mesh });
      }
    };
    animateId = requestAnimationFrame(update);
    active = true;

    return { setState, dispose: cleanup, active: true };
  } catch (err) {
    console.warn('host-strands-avatar: WebGL init failed, using CSS fallback', err);
    useCssFallback();
    return { setState, dispose: cleanup, active: false };
  }
}

function boot() {
  const mount = document.getElementById('avatarStrands');
  if (!mount) return;
  let attempts = 0;
  function tryBoot() {
    const size = measureMountSize(mount);
    if (size.width < 48 && attempts < 80) {
      attempts += 1;
      requestAnimationFrame(tryBoot);
      return;
    }
    window.hostAvatarStrands = initHostAvatarStrands(mount, AVATAR_BAR);
  }
  tryBoot();
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', boot);
} else {
  boot();
}
