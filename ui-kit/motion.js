(function (global) {
  'use strict';

  function initBlurFade(root) {
    var scope = root || document;
    var items = scope.querySelectorAll('.blur-fade');
    if (!items.length) return;
    if ('IntersectionObserver' in global) {
      var obs = new IntersectionObserver(function (entries) {
        entries.forEach(function (e) {
          if (e.isIntersecting) {
            e.target.classList.add('visible');
            obs.unobserve(e.target);
          }
        });
      }, { threshold: 0.1 });
      items.forEach(function (el, i) {
        el.style.transitionDelay = (i * 80) + 'ms';
        obs.observe(el);
      });
    } else {
      items.forEach(function (el) { el.classList.add('visible'); });
    }
  }

  function showModuleSkeleton(root) {
    var el = root || document.getElementById('module-root');
    if (!el) return;
    el.innerHTML = '<div class="panel module-skeleton blur-fade">'
      + '<div class="ui-skeleton ui-skeleton-line"></div>'
      + '<div class="ui-skeleton ui-skeleton-line"></div>'
      + '<div class="ui-skeleton ui-skeleton-line short"></div>'
      + '</div>';
    initBlurFade(el);
    var card = el.querySelector('.blur-fade');
    if (card) card.classList.add('visible');
  }

  global.SmMotion = { initBlurFade: initBlurFade, showModuleSkeleton: showModuleSkeleton };
})(typeof window !== 'undefined' ? window : global);
