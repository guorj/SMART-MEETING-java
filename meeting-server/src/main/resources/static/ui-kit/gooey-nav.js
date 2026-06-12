/**
 * GooeyNav — Vanilla JS port of React Bits GooeyNav.
 * Morphing pill indicator with gooey bubble particles on tab change.
 */
(function () {
  'use strict';

  function prefersReducedMotion() {
    return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  function GooeyNavInstance(container, options) {
    this.container = container;
    this.options = options || {};
    this.items = this.options.items || [];
    this.animationTime = this.options.animationTime != null ? this.options.animationTime : 600;
    this.particleCount = this.options.particleCount != null ? this.options.particleCount : 15;
    this.particleDistances = this.options.particleDistances || [90, 10];
    this.particleR = this.options.particleR != null ? this.options.particleR : 100;
    this.timeVariance = this.options.timeVariance != null ? this.options.timeVariance : 300;
    this.colors = this.options.colors || [1, 2, 3, 1, 2, 3, 1, 4];
    this.initialActiveIndex = this.options.initialActiveIndex != null ? this.options.initialActiveIndex : 0;
    this.onSelect = this.options.onSelect;
    this.className = this.options.className || 'gooey-nav--light';
    this.activeIndex = this.initialActiveIndex;
    this.root = null;
    this.navEl = null;
    this.filterEl = null;
    this.textEl = null;
    this.resizeObserver = null;
    this._mounted = false;
  }

  GooeyNavInstance.prototype.noise = function (n) {
    n = n == null ? 1 : n;
    return n / 2 - Math.random() * n;
  };

  GooeyNavInstance.prototype.getXY = function (distance, pointIndex, totalPoints) {
    var angle = ((360 + this.noise(8)) / totalPoints) * pointIndex * (Math.PI / 180);
    return [distance * Math.cos(angle), distance * Math.sin(angle)];
  };

  GooeyNavInstance.prototype.createParticle = function (i, t, d, r) {
    var rotate = this.noise(r / 10);
    return {
      start: this.getXY(d[0], this.particleCount - i, this.particleCount),
      end: this.getXY(d[1] + this.noise(7), this.particleCount - i, this.particleCount),
      time: t,
      scale: 1 + this.noise(0.2),
      color: this.colors[Math.floor(Math.random() * this.colors.length)],
      rotate: rotate > 0 ? (rotate + r / 20) * 10 : (rotate - r / 20) * 10
    };
  };

  GooeyNavInstance.prototype.makeParticles = function (element) {
    if (prefersReducedMotion()) return;

    var self = this;
    var d = this.particleDistances;
    var r = this.particleR;
    var bubbleTime = this.animationTime * 2 + this.timeVariance;
    element.style.setProperty('--time', bubbleTime + 'ms');

    for (var i = 0; i < this.particleCount; i++) {
      (function (idx) {
        var t = self.animationTime * 2 + self.noise(self.timeVariance * 2);
        var p = self.createParticle(idx, t, d, r);
        element.classList.remove('active');

        setTimeout(function () {
          if (!self.filterEl || !element.parentNode) return;

          var particle = document.createElement('span');
          var point = document.createElement('span');
          particle.classList.add('particle');
          particle.style.setProperty('--start-x', p.start[0] + 'px');
          particle.style.setProperty('--start-y', p.start[1] + 'px');
          particle.style.setProperty('--end-x', p.end[0] + 'px');
          particle.style.setProperty('--end-y', p.end[1] + 'px');
          particle.style.setProperty('--time', p.time + 'ms');
          particle.style.setProperty('--scale', String(p.scale));
          particle.style.setProperty('--color', 'var(--gooey-color-' + p.color + ', white)');
          particle.style.setProperty('--rotate', p.rotate + 'deg');

          point.classList.add('point');
          particle.appendChild(point);
          element.appendChild(particle);

          requestAnimationFrame(function () {
            element.classList.add('active');
          });

          setTimeout(function () {
            try {
              if (particle.parentNode === element) {
                element.removeChild(particle);
              }
            } catch (e) { /* ignore */ }
          }, t);
        }, 30);
      })(i);
    }
  };

  GooeyNavInstance.prototype.updateEffectPosition = function (liEl) {
    if (!this.root || !this.filterEl || !this.textEl || !liEl) return;

    var containerRect = this.root.getBoundingClientRect();
    var pos = liEl.getBoundingClientRect();
    var styles = {
      left: (pos.x - containerRect.x) + 'px',
      top: (pos.y - containerRect.y) + 'px',
      width: pos.width + 'px',
      height: pos.height + 'px'
    };

    Object.assign(this.filterEl.style, styles);
    Object.assign(this.textEl.style, styles);
    this.textEl.textContent = liEl.innerText;
  };

  GooeyNavInstance.prototype.setActiveIndex = function (index, animate) {
    if (!this.navEl) return;
    var items = this.navEl.querySelectorAll('li');
    if (index < 0 || index >= items.length) return;
    if (this.activeIndex === index && animate !== true) return;

    var liEl = items[index];
    var prevIndex = this.activeIndex;
    this.activeIndex = index;

    for (var i = 0; i < items.length; i++) {
      items[i].classList.toggle('active', i === index);
    }

    this.updateEffectPosition(liEl);

    if (animate === false) {
      if (this.textEl) this.textEl.classList.add('active');
      return;
    }

    if (this.filterEl) {
      var particles = this.filterEl.querySelectorAll('.particle');
      for (var j = 0; j < particles.length; j++) {
        this.filterEl.removeChild(particles[j]);
      }
    }

    if (this.textEl) {
      this.textEl.classList.remove('active');
      void this.textEl.offsetWidth;
      this.textEl.classList.add('active');
    }

    if (this.filterEl && prevIndex !== index) {
      this.makeParticles(this.filterEl);
    }

    if (typeof this.onSelect === 'function') {
      this.onSelect(index, this.items[index]);
    }
  };

  GooeyNavInstance.prototype.handleClick = function (index, liEl) {
    if (this.activeIndex === index) return;
    this.setActiveIndex(index, true);
  };

  GooeyNavInstance.prototype.handleKeyDown = function (e, index) {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      var liEl = e.currentTarget.parentElement;
      if (liEl) this.handleClick(index, liEl);
    }
  };

  GooeyNavInstance.prototype.mount = function () {
    var self = this;
    this.unmount();

    var root = document.createElement('div');
    root.className = 'gooey-nav-container ' + this.className;

    var nav = document.createElement('nav');
    var ul = document.createElement('ul');
    ul.setAttribute('role', 'tablist');

    this.items.forEach(function (item, index) {
      var li = document.createElement('li');
      li.setAttribute('role', 'presentation');
      if (index === self.activeIndex) li.classList.add('active');

      var btn = document.createElement('button');
      btn.type = 'button';
      btn.setAttribute('role', 'tab');
      btn.setAttribute('aria-selected', index === self.activeIndex ? 'true' : 'false');
      btn.textContent = item.label || '';

      btn.addEventListener('click', function (e) {
        e.preventDefault();
        btn.setAttribute('aria-selected', 'true');
        var siblings = ul.querySelectorAll('button[role="tab"]');
        for (var s = 0; s < siblings.length; s++) {
          if (siblings[s] !== btn) siblings[s].setAttribute('aria-selected', 'false');
        }
        self.handleClick(index, li);
      });

      btn.addEventListener('keydown', function (e) {
        self.handleKeyDown(e, index);
      });

      li.appendChild(btn);
      ul.appendChild(li);
    });

    nav.appendChild(ul);

    var filter = document.createElement('span');
    filter.className = 'effect filter';
    filter.setAttribute('aria-hidden', 'true');

    var text = document.createElement('span');
    text.className = 'effect text';
    text.setAttribute('aria-hidden', 'true');

    root.appendChild(nav);
    root.appendChild(filter);
    root.appendChild(text);

    this.container.innerHTML = '';
    this.container.appendChild(root);

    this.root = root;
    this.navEl = ul;
    this.filterEl = filter;
    this.textEl = text;
    this._mounted = true;

    var activeLi = ul.querySelectorAll('li')[this.activeIndex];
    if (activeLi) {
      this.updateEffectPosition(activeLi);
      if (this.textEl) this.textEl.classList.add('active');
    }

    if (window.ResizeObserver) {
      this.resizeObserver = new ResizeObserver(function () {
        var current = self.navEl && self.navEl.querySelectorAll('li')[self.activeIndex];
        if (current) self.updateEffectPosition(current);
      });
      this.resizeObserver.observe(root);
    }
  };

  GooeyNavInstance.prototype.unmount = function () {
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
      this.resizeObserver = null;
    }
    if (this.container) this.container.innerHTML = '';
    this.root = null;
    this.navEl = null;
    this.filterEl = null;
    this.textEl = null;
    this._mounted = false;
  };

  window.GooeyNav = {
    mount: function (container, options) {
      var target = typeof container === 'string'
        ? document.querySelector(container)
        : container;
      if (!target) return null;
      var instance = new GooeyNavInstance(target, options || {});
      instance.mount();
      return instance;
    }
  };
})();
