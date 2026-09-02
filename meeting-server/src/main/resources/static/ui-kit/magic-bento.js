/**
 * MagicBento — Vanilla JS card effects (border glow, spotlight, particles, tilt, ripple).
 * Depends on GSAP (window.gsap). Silently no-ops when GSAP is unavailable.
 */
(function () {
  'use strict';

  var DEFAULT_PARTICLE_COUNT = 6;
  var DEFAULT_SPOTLIGHT_RADIUS = 300;
  var DEFAULT_SPOTLIGHT_MAX_OPACITY = 0.22;
  var DEFAULT_GLOW_COLOR = '0, 47, 167';
  var MOBILE_BREAKPOINT = 900;
  var MOBILE_MQ = typeof window.matchMedia === 'function'
    ? window.matchMedia('(max-width: 900px), (pointer: coarse)')
    : null;

  function isMobile() {
    return (MOBILE_MQ && MOBILE_MQ.matches) || window.innerWidth <= MOBILE_BREAKPOINT;
  }

  var INTERACTIVE_SELECTOR = 'button, a, input, select, textarea, .agenda-doc-panel-actions';

  var defaultConfig = {
    textAutoHide: true,
    enableStars: false,
    enableSpotlight: true,
    enableBorderGlow: true,
    enableTilt: false,
    enableMagnetism: false,
    clickEffect: true,
    spotlightRadius: DEFAULT_SPOTLIGHT_RADIUS,
    particleCount: DEFAULT_PARTICLE_COUNT,
    glowColor: DEFAULT_GLOW_COLOR,
    spotlightEl: null,
    scopeEl: null
  };

  var cardStates = new Map();
  var spotlightInitialized = false;
  var spotlightState = null;

  function resolveElements(selectorOrElements) {
    if (!selectorOrElements) return [];
    if (typeof selectorOrElements === 'string') {
      return Array.prototype.slice.call(document.querySelectorAll(selectorOrElements));
    }
    if (selectorOrElements.length !== undefined) {
      return Array.prototype.slice.call(selectorOrElements);
    }
    return [selectorOrElements];
  }

  function calculateSpotlightValues(radius) {
    return {
      proximity: radius * 0.5,
      fadeDistance: radius * 0.75
    };
  }

  function updateCardGlowProperties(card, mouseX, mouseY, glow, radius) {
    var rect = card.getBoundingClientRect();
    var relativeX = ((mouseX - rect.left) / rect.width) * 100;
    var relativeY = ((mouseY - rect.top) / rect.height) * 100;
    card.style.setProperty('--glow-x', relativeX + '%');
    card.style.setProperty('--glow-y', relativeY + '%');
    card.style.setProperty('--glow-intensity', glow.toString());
    card.style.setProperty('--glow-radius', radius + 'px');
  }

  function createParticle(x, y, glowColor) {
    var el = document.createElement('div');
    el.className = 'magic-bento-particle';
    el.style.left = x + 'px';
    el.style.top = y + 'px';
    el.style.background = 'rgba(' + glowColor + ', 0.6)';
    el.style.boxShadow = '0 0 6px rgba(' + glowColor + ', 0.3)';
    return el;
  }

  function generateMemoizedParticles(card, count) {
    var particles = [];
    var rect = card.getBoundingClientRect();
    var width = rect.width || 100;
    var height = rect.height || 100;
    for (var i = 0; i < count; i++) {
      particles.push({
        x: Math.random() * width,
        y: Math.random() * height
      });
    }
    return particles;
  }

  function animateParticles(card, memoizedParticles, glowColor) {
    var state = cardStates.get(card);
    if (!state || !state.isHovered) return;

    memoizedParticles.forEach(function (pos, index) {
      setTimeout(function () {
        var currentState = cardStates.get(card);
        if (!currentState || !currentState.isHovered) return;

        var particle = createParticle(pos.x, pos.y, glowColor);
        card.appendChild(particle);
        currentState.particles.push(particle);

        window.gsap.fromTo(particle,
          { scale: 0, opacity: 0 },
          { scale: 1, opacity: 1, duration: 0.3, ease: 'back.out(1.7)' }
        );

        window.gsap.to(particle, {
          x: (Math.random() - 0.5) * 100,
          y: (Math.random() - 0.5) * 100,
          rotation: Math.random() * 360,
          duration: 2 + Math.random() * 2,
          ease: 'none',
          repeat: -1,
          yoyo: true
        });

        window.gsap.to(particle, {
          opacity: 0.3,
          duration: 1.5,
          ease: 'power2.inOut',
          repeat: -1,
          yoyo: true
        });
      }, index * 100);
    });
  }

  function clearParticles(card) {
    var state = cardStates.get(card);
    if (!state) return;

    state.particles.forEach(function (particle) {
      if (particle.parentNode) {
        particle.parentNode.removeChild(particle);
      }
    });
    state.particles = [];
  }

  function handleCardClick(card, glowColor, e) {
    var rect = card.getBoundingClientRect();
    var x = e.clientX - rect.left;
    var y = e.clientY - rect.top;

    var maxDistance = Math.max(
      Math.hypot(x, y),
      Math.hypot(x - rect.width, y),
      Math.hypot(x, y - rect.height),
      Math.hypot(x - rect.width, y - rect.height)
    );

    var ripple = document.createElement('div');
    ripple.className = 'magic-bento-ripple';
    ripple.style.width = (maxDistance * 2) + 'px';
    ripple.style.height = (maxDistance * 2) + 'px';
    ripple.style.left = (x - maxDistance) + 'px';
    ripple.style.top = (y - maxDistance) + 'px';
    ripple.style.background = 'radial-gradient(circle, rgba(' + glowColor + ', 0.15) 0%, rgba(' + glowColor + ', 0.08) 30%, transparent 70%)';
    card.appendChild(ripple);

    window.gsap.fromTo(ripple,
      { scale: 0, opacity: 1 },
      {
        scale: 1,
        opacity: 0,
        duration: 0.8,
        ease: 'power2.out',
        onComplete: function () { ripple.remove(); }
      }
    );
  }

  function isFullscreenLocked(card) {
    return card.id === 'agendaDocCard' && card.classList.contains('agenda-doc-fullscreen');
  }

  function isInteractiveTarget(target) {
    return target && typeof target.closest === 'function' && target.closest(INTERACTIVE_SELECTOR);
  }

  function setupCard(card, options) {
    var glowColor = options.glowColor;
    var enableTilt = options.enableTilt;
    var enableMagnetism = options.enableMagnetism;
    var clickEffect = options.clickEffect;
    var particleCount = options.particleCount;
    var enableStars = options.enableStars;

    var memoizedParticles = generateMemoizedParticles(card, particleCount);

    var state = {
      isHovered: false,
      particles: [],
      memoizedParticles: memoizedParticles
    };
    cardStates.set(card, state);

    card.addEventListener('mouseenter', function () {
      state.isHovered = true;
      if (enableStars) {
        animateParticles(card, memoizedParticles, glowColor);
      }
    });

    card.addEventListener('mouseleave', function () {
      state.isHovered = false;
      clearParticles(card);

      if (enableTilt) {
        window.gsap.to(card, { rotateX: 0, rotateY: 0, duration: 0.3, ease: 'power2.out' });
      }
      if (enableMagnetism) {
        window.gsap.to(card, { x: 0, y: 0, duration: 0.3, ease: 'power2.out' });
      }
    });

    card.addEventListener('mousemove', function (e) {
      if (isFullscreenLocked(card)) return;

      var rect = card.getBoundingClientRect();
      var x = e.clientX - rect.left;
      var y = e.clientY - rect.top;
      var centerX = rect.width / 2;
      var centerY = rect.height / 2;

      if (enableTilt) {
        var rotateX = ((y - centerY) / centerY) * -10;
        var rotateY = ((x - centerX) / centerX) * 10;
        window.gsap.to(card, {
          rotateX: rotateX,
          rotateY: rotateY,
          duration: 0.1,
          ease: 'power2.out',
          transformPerspective: 1000
        });
      }

      if (enableMagnetism) {
        var magnetX = (x - centerX) * 0.05;
        var magnetY = (y - centerY) * 0.05;
        window.gsap.to(card, { x: magnetX, y: magnetY, duration: 0.3, ease: 'power2.out' });
      }
    });

    if (clickEffect) {
      card.addEventListener('click', function (e) {
        if (isInteractiveTarget(e.target)) return;
        handleCardClick(card, glowColor, e);
      });
    }
  }

  function getEnhancedCards(scopeEl) {
    if (!scopeEl) {
      return Array.prototype.slice.call(document.querySelectorAll('.magic-bento-enhanced'));
    }
    return Array.prototype.slice.call(scopeEl.querySelectorAll('.magic-bento-enhanced'));
  }

  function setupScopedSpotlight(options) {
    if (spotlightInitialized || !options.enableSpotlight) return;

    var scopeSelector = options.scopeEl || '.host-workspace';
    var spotlightSelector = options.spotlightEl || '#hostMagicSpotlight';
    var scopeEl = document.querySelector(scopeSelector);
    var spotlightEl = document.querySelector(spotlightSelector);

    if (!scopeEl || !spotlightEl || !window.gsap) return;

    spotlightInitialized = true;
    spotlightState = {
      scopeEl: scopeEl,
      spotlightEl: spotlightEl,
      spotlightRadius: options.spotlightRadius || DEFAULT_SPOTLIGHT_RADIUS,
      spotlightMaxOpacity: options.spotlightMaxOpacity != null ?
        options.spotlightMaxOpacity : DEFAULT_SPOTLIGHT_MAX_OPACITY
    };

    document.addEventListener('mousemove', function (e) {
      var rect = scopeEl.getBoundingClientRect();
      var isInside = e.clientX >= rect.left && e.clientX <= rect.right &&
        e.clientY >= rect.top && e.clientY <= rect.bottom;

      var cards = getEnhancedCards(scopeEl);

      if (!isInside) {
        window.gsap.to(spotlightEl, { opacity: 0, duration: 0.3, ease: 'power2.out' });
        cards.forEach(function (card) {
          card.style.setProperty('--glow-intensity', '0');
        });
        return;
      }

      var values = calculateSpotlightValues(spotlightState.spotlightRadius);
      var proximity = values.proximity;
      var fadeDistance = values.fadeDistance;
      var minDistance = Infinity;

      cards.forEach(function (card) {
        var cardRect = card.getBoundingClientRect();
        var centerX = cardRect.left + cardRect.width / 2;
        var centerY = cardRect.top + cardRect.height / 2;
        var distance = Math.hypot(e.clientX - centerX, e.clientY - centerY) -
          Math.max(cardRect.width, cardRect.height) / 2;
        var effectiveDistance = Math.max(0, distance);

        minDistance = Math.min(minDistance, effectiveDistance);

        var glowIntensity = 0;
        if (effectiveDistance <= proximity) {
          glowIntensity = 1;
        } else if (effectiveDistance <= fadeDistance) {
          glowIntensity = (fadeDistance - effectiveDistance) / (fadeDistance - proximity);
        }

        updateCardGlowProperties(card, e.clientX, e.clientY, glowIntensity, spotlightState.spotlightRadius);
      });

      window.gsap.to(spotlightEl, {
        left: e.clientX,
        top: e.clientY,
        duration: 0.1,
        ease: 'power2.out'
      });

      var maxOpacity = spotlightState.spotlightMaxOpacity;
      var targetOpacity = minDistance <= proximity ? maxOpacity :
        minDistance <= fadeDistance ?
          ((fadeDistance - minDistance) / (fadeDistance - proximity)) * maxOpacity : 0;

      window.gsap.to(spotlightEl, {
        opacity: targetOpacity,
        duration: targetOpacity > 0 ? 0.2 : 0.5,
        ease: 'power2.out'
      });
    });

    document.addEventListener('mouseleave', function () {
      getEnhancedCards(scopeEl).forEach(function (card) {
        card.style.setProperty('--glow-intensity', '0');
      });
      window.gsap.to(spotlightEl, { opacity: 0, duration: 0.3, ease: 'power2.out' });
    });
  }

  function applyEnhanceClasses(card, options) {
    card.classList.add('magic-bento-enhanced');
    if (options.enableBorderGlow !== false) {
      card.classList.add('magic-bento-card--border-glow');
    }
    card.classList.add('magic-bento-card--light');
    card.style.setProperty('--glow-color', options.glowColor || DEFAULT_GLOW_COLOR);
    card.style.setProperty('--glow-x', '50%');
    card.style.setProperty('--glow-y', '50%');
    card.style.setProperty('--glow-intensity', '0');
    card.style.setProperty('--glow-radius', (options.spotlightRadius || DEFAULT_SPOTLIGHT_RADIUS) + 'px');
  }

  function enhance(selectorOrElements, options) {
    if (!window.gsap) return [];

    var merged = Object.assign({}, defaultConfig, options || {});
    var mobile = isMobile();

    if (mobile) {
      merged.enableTilt = false;
      merged.enableMagnetism = false;
      merged.enableStars = false;
    }

    var elements = resolveElements(selectorOrElements);
    var enhanced = [];

    elements.forEach(function (el) {
      if (!el || el.classList.contains('magic-bento-enhanced')) return;

      applyEnhanceClasses(el, merged);
      setupCard(el, merged);
      enhanced.push(el);
    });

    setupScopedSpotlight(merged);
    return enhanced;
  }

  function init(selector, options) {
    if (!window.gsap) return;

    var gridEl = document.querySelector(selector);
    if (!gridEl) return;

    var merged = Object.assign({}, defaultConfig, options || {});
    var cards = options && options.cards;

    if (!cards || !cards.length) {
      console.error('MagicBento: cards option is required');
      return;
    }

    var mobile = isMobile();
    if (mobile) {
      merged.enableTilt = false;
      merged.enableMagnetism = false;
      merged.enableStars = false;
    }

    gridEl.innerHTML = '';

    cards.forEach(function (cardData, index) {
      var card = document.createElement('div');
      var className = 'magic-bento-card magic-bento-enhanced';
      if (merged.textAutoHide) className += ' magic-bento-card--text-autohide';
      if (merged.enableBorderGlow !== false) className += ' magic-bento-card--border-glow';
      if (merged.lightTheme) className += ' magic-bento-card--light';
      card.className = className;
      card.style.setProperty('--glow-color', merged.glowColor);

      card.innerHTML =
        '<div class="magic-bento-card__header">' +
          '<div class="card-icon">' + (cardData.icon || '📄') + '</div>' +
          '<div class="magic-bento-card__label">' + cardData.label + '</div>' +
        '</div>' +
        '<div class="magic-bento-card__content">' +
          '<h2 class="magic-bento-card__title">' + cardData.title + '</h2>' +
          '<p class="magic-bento-card__description">' + cardData.description + '</p>' +
        '</div>';

      gridEl.appendChild(card);
      setupCard(card, merged);
    });

    if (merged.enableSpotlight) {
      var section = gridEl.closest('.magic-bento-section') || gridEl.parentElement;
      setupScopedSpotlight(Object.assign({}, merged, {
        scopeEl: section,
        spotlightEl: merged.spotlightEl || '#globalSpotlight'
      }));
    }
  }

  window.MagicBento = { enhance: enhance, init: init };
})();
