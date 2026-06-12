/**
 * ChromaGrid — Vanilla JS port of React Bits ChromaGrid.
 * Grayscale grid with cursor-following color spotlight (GSAP).
 */
(function () {
  'use strict';

  var DEFAULT_PALETTE = [
    { borderColor: '#002FA7', gradient: 'linear-gradient(145deg, #D0DAE8 0%, #EEF2F7 72%)' },
    { borderColor: '#1565C0', gradient: 'linear-gradient(160deg, #C8DDF5 0%, #EEF2F7 70%)' },
    { borderColor: '#1B7F4B', gradient: 'linear-gradient(150deg, #CFE8DA 0%, #EEF2F7 70%)' },
    { borderColor: '#5C6BC0', gradient: 'linear-gradient(170deg, #D6DAEC 0%, #EEF2F7 72%)' },
    { borderColor: '#00838F', gradient: 'linear-gradient(140deg, #C5E5E8 0%, #EEF2F7 70%)' },
    { borderColor: '#6A1B9A', gradient: 'linear-gradient(155deg, #DDD0EA 0%, #EEF2F7 72%)' },
    { borderColor: '#B8860B', gradient: 'linear-gradient(165deg, #E8DFC8 0%, #EEF2F7 70%)' },
    { borderColor: '#37474F', gradient: 'linear-gradient(145deg, #D5DBDE 0%, #EEF2F7 72%)' }
  ];

  function parsePresetMeta(displayName) {
    var raw = displayName == null ? '' : String(displayName);
    var match = raw.match(/[（(]([^）)]+)[）)]/);
    if (!match) {
      return { title: raw.trim(), subtitle: '点击创建会议' };
    }
    return {
      title: raw.replace(/[（(][^）)]+[）)]/g, '').trim(),
      subtitle: match[1]
    };
  }

  function renderVideoIcon() {
    if (window.Iconsax && typeof Iconsax.render === 'function') {
      return Iconsax.render('video');
    }
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polygon points="6 3 20 12 6 21 6 3"/></svg>';
  }

  function escAttr(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/"/g, '&quot;')
      .replace(/</g, '&lt;');
  }

  function escText(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;');
  }

  function presetsToItems(presets, palette) {
    var colors = palette || DEFAULT_PALETTE;
    return (presets || []).map(function (preset, index) {
      var meta = parsePresetMeta(preset.displayName);
      var tone = colors[index % colors.length];
      return {
        code: preset.code,
        title: meta.title || preset.displayName,
        subtitle: meta.subtitle,
        handle: '模板 #' + preset.code,
        borderColor: tone.borderColor,
        gradient: tone.gradient
      };
    });
  }

  function ChromaGridInstance(container, options) {
    this.container = container;
    this.options = options || {};
    this.items = this.options.items || [];
    this.radius = this.options.radius != null ? this.options.radius : 260;
    this.damping = this.options.damping != null ? this.options.damping : 0.45;
    this.fadeOut = this.options.fadeOut != null ? this.options.fadeOut : 0.6;
    this.ease = this.options.ease || 'power3.out';
    this.columns = this.options.columns != null ? this.options.columns : 2;
    this.onItemClick = this.options.onItemClick;
    this.className = this.options.className || '';
    this.root = null;
    this.fadeEl = null;
    this.setX = null;
    this.setY = null;
    this.pos = { x: 0, y: 0 };
    this.listeners = [];
    this._mounted = false;
  }

  ChromaGridInstance.prototype.mount = function () {
    if (!this.container) return;
    this.unmount();

    var root = document.createElement('div');
    root.className = 'chroma-grid chroma-grid--light ' + this.className;
    root.style.setProperty('--r', this.radius + 'px');
    root.style.setProperty('--cols', String(this.columns));

    var self = this;
    this.items.forEach(function (item, index) {
      var card = document.createElement('button');
      card.type = 'button';
      card.className = 'chroma-card';
      card.dataset.code = item.code != null ? String(item.code) : String(index);
      card.style.setProperty('--card-border', item.borderColor || '#002FA7');
      card.style.setProperty('--card-gradient', item.gradient || DEFAULT_PALETTE[0].gradient);

      card.innerHTML =
        '<div class="chroma-visual">' +
          '<div class="chroma-visual-icon">' + renderVideoIcon() + '</div>' +
        '</div>' +
        '<footer class="chroma-info">' +
          '<h3 class="name">' + escText(item.title) + '</h3>' +
          (item.handle ? '<span class="handle">' + escText(item.handle) + '</span>' : '') +
          '<p class="role">' + escText(item.subtitle || '') + '</p>' +
        '</footer>';

      card.addEventListener('click', function () {
        if (card.classList.contains('is-busy')) return;
        if (typeof self.onItemClick === 'function') {
          self.onItemClick(item, card);
        } else if (item.url) {
          window.open(item.url, '_blank', 'noopener,noreferrer');
        }
      });

      card.addEventListener('mousemove', function (e) {
        var rect = card.getBoundingClientRect();
        card.style.setProperty('--mouse-x', (e.clientX - rect.left) + 'px');
        card.style.setProperty('--mouse-y', (e.clientY - rect.top) + 'px');
      });

      root.appendChild(card);
    });

    var overlay = document.createElement('div');
    overlay.className = 'chroma-overlay';
    overlay.setAttribute('aria-hidden', 'true');

    var fade = document.createElement('div');
    fade.className = 'chroma-fade';
    fade.setAttribute('aria-hidden', 'true');

    root.appendChild(overlay);
    root.appendChild(fade);

    this.container.innerHTML = '';
    this.container.classList.add('chroma-grid-root');
    this.container.appendChild(root);

    this.root = root;
    this.fadeEl = fade;
    this._mounted = true;

    if (window.gsap && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      this._initSpotlight();
    }
  };

  ChromaGridInstance.prototype._initSpotlight = function () {
    var self = this;
    var el = this.root;
    if (!el || !window.gsap) return;

    this.setX = window.gsap.quickSetter(el, '--x', 'px');
    this.setY = window.gsap.quickSetter(el, '--y', 'px');

    var rect = el.getBoundingClientRect();
    this.pos = { x: rect.width / 2, y: rect.height / 2 };
    this.setX(this.pos.x);
    this.setY(this.pos.y);

    var moveTo = function (x, y) {
      window.gsap.to(self.pos, {
        x: x,
        y: y,
        duration: self.damping,
        ease: self.ease,
        onUpdate: function () {
          if (self.setX) self.setX(self.pos.x);
          if (self.setY) self.setY(self.pos.y);
        },
        overwrite: true
      });
    };

    var onMove = function (e) {
      var bounds = el.getBoundingClientRect();
      moveTo(e.clientX - bounds.left, e.clientY - bounds.top);
      window.gsap.to(self.fadeEl, { opacity: 0, duration: 0.25, overwrite: true });
    };

    var onLeave = function () {
      window.gsap.to(self.fadeEl, {
        opacity: 1,
        duration: self.fadeOut,
        overwrite: true
      });
    };

    el.addEventListener('pointermove', onMove);
    el.addEventListener('pointerleave', onLeave);
    this.listeners.push({ el: el, type: 'pointermove', fn: onMove });
    this.listeners.push({ el: el, type: 'pointerleave', fn: onLeave });
  };

  ChromaGridInstance.prototype.setBusy = function (code, busy) {
    if (!this.root) return;
    var selector = code == null ? '.chroma-card' : '.chroma-card[data-code="' + escAttr(code) + '"]';
    var cards = this.root.querySelectorAll(selector);
    for (var i = 0; i < cards.length; i++) {
      cards[i].classList.toggle('is-busy', !!busy);
    }
  };

  ChromaGridInstance.prototype.unmount = function () {
    for (var i = 0; i < this.listeners.length; i++) {
      var entry = this.listeners[i];
      entry.el.removeEventListener(entry.type, entry.fn);
    }
    this.listeners = [];
    if (this.container) {
      this.container.innerHTML = '';
      this.container.classList.remove('chroma-grid-root');
    }
    this.root = null;
    this.fadeEl = null;
    this.setX = null;
    this.setY = null;
    this._mounted = false;
  };

  window.ChromaGrid = {
    DEFAULT_PALETTE: DEFAULT_PALETTE,
    presetsToItems: presetsToItems,
    parsePresetMeta: parsePresetMeta,
    mount: function (container, options) {
      var target = typeof container === 'string'
        ? document.querySelector(container)
        : container;
      if (!target) return null;
      var instance = new ChromaGridInstance(target, options || {});
      instance.mount();
      return instance;
    }
  };
})();
