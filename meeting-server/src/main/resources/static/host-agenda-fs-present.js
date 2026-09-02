/**
 * AgendaFsPresent — fullscreen agenda doc presentation layer (backdrop, chrome, enter/exit motion).
 */
(function (global) {
  'use strict';

  var BACKDROP_ID = 'agendaDocFsBackdrop';
  var CHROME_ID = 'agendaDocFsChrome';
  var STAGGER_SELECTOR = '.feishu-part-block, .local-material-block, .weekly-report-block, .md-body-host > *';
  var STAGGER_LIMIT = 14;
  var activeCard = null;
  var isPresenting = false;
  var exitInFlight = false;
  var activeTweens = [];

  function prefersReducedMotion() {
    return global.matchMedia && global.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  function plainText(el) {
    if (!el) return '';
    return (el.textContent || '').replace(/\s+/g, ' ').trim();
  }

  function killActiveTweens() {
    activeTweens.forEach(function (t) {
      if (t && t.kill) t.kill();
    });
    activeTweens = [];
  }

  function trackTween(tween) {
    if (tween) activeTweens.push(tween);
    return tween;
  }

  function getBackdrop() {
    return document.getElementById(BACKDROP_ID);
  }

  function getChrome() {
    return document.getElementById(CHROME_ID);
  }

  function ensureBackdrop() {
    var el = getBackdrop();
    if (el) return el;
    el = document.createElement('div');
    el.id = BACKDROP_ID;
    el.className = 'agenda-doc-fs-backdrop';
    el.setAttribute('aria-hidden', 'true');
    document.body.appendChild(el);
    return el;
  }

  function ensureChrome() {
    var el = getChrome();
    if (el) return el;
    el = document.createElement('div');
    el.id = CHROME_ID;
    el.className = 'agenda-doc-fs-chrome';
    el.setAttribute('role', 'status');
    el.innerHTML =
      '<div class="agenda-doc-fs-chrome-inner">' +
        '<div class="agenda-doc-fs-chrome-topic" id="agendaDocFsChromeTopic"></div>' +
        '<div class="agenda-doc-fs-chrome-meta">' +
          '<span class="agenda-doc-fs-chrome-meeting" id="agendaDocFsChromeMeeting"></span>' +
          '<span class="agenda-doc-fs-chrome-hint">Esc 退出</span>' +
        '</div>' +
      '</div>';
    document.body.appendChild(el);
    return el;
  }

  function updateChromeText() {
    var chrome = getChrome();
    if (!chrome) return;
    var topicEl = document.getElementById('agendaDocFsChromeTopic');
    var meetingEl = document.getElementById('agendaDocFsChromeMeeting');
    var panelTitle = document.getElementById('agendaDocPanelTitle');
    var meetingTitle = document.getElementById('meetingTitleH1');
    if (topicEl) topicEl.textContent = plainText(panelTitle) || '当前议题资料';
    if (meetingEl) meetingEl.textContent = plainText(meetingTitle) || '';
  }

  function getStaggerTargets(card) {
    if (!card) return [];
    var reportBody = card.querySelector('#progressReportBody');
    if (!reportBody) return [];
    var nodes = Array.prototype.slice.call(reportBody.querySelectorAll(STAGGER_SELECTOR));
    if (!nodes.length) {
      var fallback = reportBody.querySelector('.feishu-doc-panel, .md-body-host, .feishu-doc-body');
      if (fallback) nodes = [fallback];
      else if (reportBody.children.length) nodes = [reportBody.children[0]];
    }
    return nodes.slice(0, STAGGER_LIMIT);
  }

  function resetStaggerTargets(targets) {
    targets.forEach(function (node) {
      node.style.opacity = '';
      node.style.transform = '';
    });
  }

  function cleanup() {
    killActiveTweens();
    var backdrop = getBackdrop();
    var chrome = getChrome();
    if (backdrop && backdrop.parentNode) backdrop.parentNode.removeChild(backdrop);
    if (chrome && chrome.parentNode) chrome.parentNode.removeChild(chrome);
    if (activeCard) {
      activeCard.classList.remove('agenda-doc-fs-ready');
      activeCard.style.opacity = '';
      activeCard.style.transform = '';
      var targets = getStaggerTargets(activeCard);
      resetStaggerTargets(targets);
    }
    activeCard = null;
    isPresenting = false;
    exitInFlight = false;
  }

  function playEnter(card) {
    if (!card) return;
    if (isPresenting && activeCard === card) {
      updateChromeText();
      return;
    }
    if (isPresenting && activeCard !== card) cleanup();

    activeCard = card;
    isPresenting = true;
    exitInFlight = false;

    var backdrop = ensureBackdrop();
    var chrome = ensureChrome();
    updateChromeText();

    card.classList.add('agenda-doc-fs-ready');

    if (prefersReducedMotion() || !global.gsap) {
      backdrop.style.opacity = '1';
      chrome.style.opacity = '1';
      chrome.style.transform = '';
      card.style.opacity = '1';
      card.style.transform = '';
      return;
    }

    killActiveTweens();

    global.gsap.set(backdrop, { opacity: 0 });
    global.gsap.set(chrome, { opacity: 0, y: -10 });
    global.gsap.set(card, { opacity: 0.94, scale: 0.985, transformOrigin: '50% 50%' });

    var targets = getStaggerTargets(card);
    global.gsap.set(targets, { opacity: 0, y: 10 });

    trackTween(global.gsap.to(backdrop, { opacity: 1, duration: 0.38, ease: 'power2.out' }));
    trackTween(global.gsap.to(card, { opacity: 1, scale: 1, duration: 0.38, ease: 'power2.out' }));
    trackTween(global.gsap.to(chrome, { opacity: 1, y: 0, duration: 0.38, ease: 'power2.out', delay: 0.06 }));
    if (targets.length) {
      trackTween(global.gsap.to(targets, {
        opacity: 1,
        y: 0,
        duration: 0.32,
        stagger: 0.035,
        ease: 'power2.out',
        delay: 0.12
      }));
    }
  }

  function playExit(card, onDone) {
    var done = typeof onDone === 'function' ? onDone : function () {};
    var finished = false;
    var exitFallbackTimer = null;
    function finish() {
      if (finished) return;
      finished = true;
      if (exitFallbackTimer) {
        clearTimeout(exitFallbackTimer);
        exitFallbackTimer = null;
      }
      cleanup();
      done();
    }

    if (!card || !isPresenting) {
      finish();
      return;
    }
    if (exitInFlight) return;
    exitInFlight = true;

    var backdrop = getBackdrop();
    var chrome = getChrome();

    if (prefersReducedMotion() || !global.gsap) {
      finish();
      return;
    }

    killActiveTweens();

    exitFallbackTimer = setTimeout(finish, 600);

    var tl = global.gsap.timeline({
      onComplete: finish
    });
    activeTweens.push(tl);

    if (chrome) tl.to(chrome, { opacity: 0, y: -6, duration: 0.18, ease: 'power2.in' }, 0);
    tl.to(card, { opacity: 0.92, scale: 0.992, duration: 0.22, ease: 'power2.in' }, 0);
    if (backdrop) tl.to(backdrop, { opacity: 0, duration: 0.22, ease: 'power2.in' }, 0.04);
  }

  /**
   * 后台挂起时 GSAP 可能中断。退出动画中 → cleanup 交由 host 还原 DOM；
   * 仍在全屏展示 → 重建 chrome/backdrop，避免误拆 presentation 层。
   */
  function recoverFromBackground() {
    killActiveTweens();
    var card = activeCard || document.getElementById('agendaDocCard');
    var wasExitInFlight = exitInFlight;
    exitInFlight = false;

    if (wasExitInFlight) {
      cleanup();
      return;
    }
    if (card && card.classList.contains('agenda-doc-fullscreen')) {
      playEnter(card);
      return;
    }
    if (isPresenting) cleanup();
  }

  global.AgendaFsPresent = {
    playEnter: playEnter,
    playExit: playExit,
    cleanup: cleanup,
    recoverFromBackground: recoverFromBackground,
    updateChromeText: updateChromeText
  };
})(window);
