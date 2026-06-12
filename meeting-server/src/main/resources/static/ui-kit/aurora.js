/**
 * Aurora — Vanilla JS port of React Bits Aurora (ogl WebGL).
 * Animated gradient aurora background; silently no-ops when WebGL unavailable.
 */
(function () {
  'use strict';

  var OGL_CDN = 'https://esm.sh/ogl@1.0.11';

  var VERT = '#version 300 es\nin vec2 position;\nvoid main() {\n  gl_Position = vec4(position, 0.0, 1.0);\n}\n';

  var FRAG = '#version 300 es\nprecision highp float;\n\nuniform float uTime;\nuniform float uAmplitude;\nuniform vec3 uColorStops[3];\nuniform vec2 uResolution;\nuniform float uBlend;\n\nout vec4 fragColor;\n\nvec3 permute(vec3 x) {\n  return mod(((x * 34.0) + 1.0) * x, 289.0);\n}\n\nfloat snoise(vec2 v){\n  const vec4 C = vec4(\n      0.211324865405187, 0.366025403784439,\n      -0.577350269189626, 0.024390243902439\n  );\n  vec2 i  = floor(v + dot(v, C.yy));\n  vec2 x0 = v - i + dot(i, C.xx);\n  vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);\n  vec4 x12 = x0.xyxy + C.xxzz;\n  x12.xy -= i1;\n  i = mod(i, 289.0);\n\n  vec3 p = permute(\n      permute(i.y + vec3(0.0, i1.y, 1.0))\n    + i.x + vec3(0.0, i1.x, 1.0)\n  );\n\n  vec3 m = max(\n      0.5 - vec3(\n          dot(x0, x0),\n          dot(x12.xy, x12.xy),\n          dot(x12.zw, x12.zw)\n      ),\n      0.0\n  );\n  m = m * m;\n  m = m * m;\n\n  vec3 x = 2.0 * fract(p * C.www) - 1.0;\n  vec3 h = abs(x) - 0.5;\n  vec3 ox = floor(x + 0.5);\n  vec3 a0 = x - ox;\n  m *= 1.79284291400159 - 0.85373472095314 * (a0*a0 + h*h);\n\n  vec3 g;\n  g.x  = a0.x  * x0.x  + h.x  * x0.y;\n  g.yz = a0.yz * x12.xz + h.yz * x12.yw;\n  return 130.0 * dot(m, g);\n}\n\nstruct ColorStop {\n  vec3 color;\n  float position;\n};\n\n#define COLOR_RAMP(colors, factor, finalColor) {              \\\n  int index = 0;                                            \\\n  for (int i = 0; i < 2; i++) {                               \\\n     ColorStop currentColor = colors[i];                    \\\n     bool isInBetween = currentColor.position <= factor;    \\\n     index = int(mix(float(index), float(i), float(isInBetween))); \\\n  }                                                         \\\n  ColorStop currentColor = colors[index];                   \\\n  ColorStop nextColor = colors[index + 1];                  \\\n  float range = nextColor.position - currentColor.position; \\\n  float lerpFactor = (factor - currentColor.position) / range; \\\n  finalColor = mix(currentColor.color, nextColor.color, lerpFactor); \\\n}\n\nvoid main() {\n  vec2 uv = gl_FragCoord.xy / uResolution;\n\n  ColorStop colors[3];\n  colors[0] = ColorStop(uColorStops[0], 0.0);\n  colors[1] = ColorStop(uColorStops[1], 0.5);\n  colors[2] = ColorStop(uColorStops[2], 1.0);\n\n  vec3 rampColor;\n  COLOR_RAMP(colors, uv.x, rampColor);\n\n  float height = snoise(vec2(uv.x * 2.0 + uTime * 0.1, uTime * 0.25)) * 0.5 * uAmplitude;\n  height = exp(height);\n  height = (uv.y * 2.0 - height + 0.2);\n  float intensity = 0.6 * height;\n\n  float midPoint = 0.20;\n  float auroraAlpha = smoothstep(midPoint - uBlend * 0.5, midPoint + uBlend * 0.5, intensity);\n\n  vec3 auroraColor = intensity * rampColor;\n\n  fragColor = vec4(auroraColor * auroraAlpha, auroraAlpha);\n}\n';

  function prefersReducedMotion() {
    return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  function resolveEl(container) {
    if (!container) return null;
    if (typeof container === 'string') return document.querySelector(container);
    return container;
  }

  function AuroraInstance(container, options) {
    this.container = container;
    this.options = options || {};
    this.colorStops = this.options.colorStops || ['#7cff67', '#B497CF', '#5227FF'];
    this.amplitude = this.options.amplitude != null ? this.options.amplitude : 1.0;
    this.blend = this.options.blend != null ? this.options.blend : 0.5;
    this.speed = this.options.speed != null ? this.options.speed : 0.5;
    this.className = this.options.className || '';
    this.renderer = null;
    this.program = null;
    this.mesh = null;
    this.animateId = 0;
    this.resizeObserver = null;
    this.onResize = null;
    this.root = null;
    this._mounted = false;
    this._destroyed = false;
  }

  AuroraInstance.prototype.colorStopsToUniform = function (stops, ColorCtor) {
    return stops.map(function (hex) {
      var c = new ColorCtor(hex);
      return [c.r, c.g, c.b];
    });
  };

  AuroraInstance.prototype.resize = function () {
    if (!this.container || !this.renderer || !this.program) return;
    var width = this.container.offsetWidth;
    var height = this.container.offsetHeight;
    if (width <= 0 || height <= 0) return;
    this.renderer.setSize(width, height);
    this.program.uniforms.uResolution.value = [width, height];
  };

  AuroraInstance.prototype.unmount = function () {
    this._destroyed = true;
    if (this.animateId) {
      cancelAnimationFrame(this.animateId);
      this.animateId = 0;
    }
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
      this.resizeObserver = null;
    }
    if (this.onResize) {
      window.removeEventListener('resize', this.onResize);
      this.onResize = null;
    }
    if (this.renderer && this.renderer.gl) {
      var gl = this.renderer.gl;
      var canvas = gl.canvas;
      if (canvas && canvas.parentNode) {
        canvas.parentNode.removeChild(canvas);
      }
      gl.getExtension('WEBGL_lose_context') && gl.getExtension('WEBGL_lose_context').loseContext();
    }
    if (this.root && this.root.parentNode) {
      this.root.parentNode.removeChild(this.root);
    }
    this.renderer = null;
    this.program = null;
    this.mesh = null;
    this.root = null;
    this._mounted = false;
  };

  AuroraInstance.prototype.mount = async function () {
    var self = this;
    this.unmount();
    this._destroyed = false;

    if (!this.container || prefersReducedMotion()) return false;

    this.root = document.createElement('div');
    this.root.className = 'aurora-container ' + this.className;
    this.container.innerHTML = '';
    this.container.appendChild(this.root);

    try {
      var ogl = await import(OGL_CDN);
      if (self._destroyed) return false;

      var Renderer = ogl.Renderer;
      var Program = ogl.Program;
      var Mesh = ogl.Mesh;
      var Color = ogl.Color;
      var Triangle = ogl.Triangle;

      var renderer = new Renderer({
        alpha: true,
        premultipliedAlpha: true,
        antialias: true
      });
      var gl = renderer.gl;
      gl.clearColor(0, 0, 0, 0);
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
      gl.canvas.style.backgroundColor = 'transparent';

      var geometry = new Triangle(gl);
      if (geometry.attributes.uv) {
        delete geometry.attributes.uv;
      }

      var program = new Program(gl, {
        vertex: VERT,
        fragment: FRAG,
        uniforms: {
          uTime: { value: 0 },
          uAmplitude: { value: self.amplitude },
          uColorStops: { value: self.colorStopsToUniform(self.colorStops, Color) },
          uResolution: { value: [self.root.offsetWidth || 1, self.root.offsetHeight || 1] },
          uBlend: { value: self.blend }
        }
      });

      var mesh = new Mesh(gl, { geometry: geometry, program: program });
      self.root.appendChild(gl.canvas);

      self.renderer = renderer;
      self.program = program;
      self.mesh = mesh;
      self._mounted = true;

      self.onResize = function () { self.resize(); };
      window.addEventListener('resize', self.onResize);

      if (window.ResizeObserver) {
        self.resizeObserver = new ResizeObserver(function () { self.resize(); });
        self.resizeObserver.observe(self.container);
      }

      self.resize();

      var update = function (t) {
        if (self._destroyed || !self.program || !self.renderer) return;
        self.animateId = requestAnimationFrame(update);
        self.program.uniforms.uTime.value = t * 0.001 * self.speed * 0.1;
        self.program.uniforms.uAmplitude.value = self.amplitude;
        self.program.uniforms.uBlend.value = self.blend;
        self.program.uniforms.uColorStops.value = self.colorStopsToUniform(self.colorStops, Color);
        self.renderer.render({ scene: self.mesh });
      };
      self.animateId = requestAnimationFrame(update);
      return true;
    } catch (err) {
      console.warn('Aurora: failed to initialize', err);
      if (self.root && self.root.parentNode) {
        self.root.parentNode.removeChild(self.root);
      }
      self.root = null;
      return false;
    }
  };

  window.Aurora = {
    mount: function (container, options) {
      var target = resolveEl(container);
      if (!target) return Promise.resolve(null);
      var instance = new AuroraInstance(target, options || {});
      return instance.mount().then(function (ok) {
        return ok ? instance : null;
      });
    }
  };
})();
