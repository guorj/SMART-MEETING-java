/**
 * 主持页 Rail 侧滑抽屉：桌面右侧悬停展开，移动端图标切换。
 * 供 host-meeting.html（生产）与 host-avatar-preview.html（预览）共用。
 */
(function (global) {
  var drawerOpen = false;
  var drawerPinned = false;
  var drawerHoverTimer = null;
  var wired = false;
  var lastMeetingMode = null;
  var lastPreviewScene = null;
  var MOBILE_RAIL_MQ = typeof window.matchMedia === 'function'
    ? window.matchMedia('(max-width: 900px)')
    : null;

  function $(id) {
    return document.getElementById(id);
  }

  function isRailLayout() {
    return document.body.classList.contains('host-rail-layout');
  }

  function isMobileRail() {
    return MOBILE_RAIL_MQ && MOBILE_RAIL_MQ.matches;
  }

  function syncDrawerUi() {
    var drawer = $('hostRailDrawer');
    var toggle = $('hostRailMobileToggle');
    var backdrop = $('hostRailDrawerBackdrop');
    var mobileOpen = drawerOpen && isMobileRail();
    if (drawer) {
      drawer.classList.toggle('is-open', drawerOpen);
      drawer.setAttribute('aria-hidden', drawerOpen ? 'false' : 'true');
      if (isMobileRail()) {
        if (drawerOpen) drawer.removeAttribute('inert');
        else drawer.setAttribute('inert', '');
      } else {
        drawer.removeAttribute('inert');
      }
    }
    if (toggle) {
      toggle.classList.toggle('is-active', drawerOpen);
      toggle.setAttribute('aria-expanded', drawerOpen ? 'true' : 'false');
      toggle.setAttribute('aria-label', drawerOpen ? '关闭主持面板' : '打开主持面板');
      toggle.title = drawerOpen ? '关闭主持面板' : '主持面板';
    }
    document.body.classList.toggle('host-rail-drawer-open', mobileOpen);
    if (backdrop) backdrop.hidden = !mobileOpen;
  }

  function openDrawer(options) {
    if (!isRailLayout()) return;
    options = options || {};
    drawerOpen = true;
    if (options.pinned != null) drawerPinned = !!options.pinned;
    syncDrawerUi();
    var scrollEl = $('hostRailDrawerScroll');
    if (scrollEl) scrollEl.scrollTop = 0;
  }

  function closeDrawer(force) {
    if (!isRailLayout()) return;
    if (drawerPinned && !force) return;
    drawerOpen = false;
    syncDrawerUi();
  }

  function toggleDrawer() {
    if (drawerOpen) {
      drawerPinned = false;
      closeDrawer(true);
    } else {
      openDrawer();
    }
  }

  function scrollDrawerTo(section) {
    var scrollEl = $('hostRailDrawerScroll');
    var target = section === 'avatar' ? $('hostRailSectionAvatar')
      : section === 'agenda' ? $('hostRailSectionAgenda')
      : $('hostRailSectionControl');
    if (!scrollEl || !target) return;
    scrollEl.scrollTo({ top: Math.max(0, target.offsetTop - 8), behavior: 'smooth' });
  }

  function clearDrawerHoverTimer() {
    if (drawerHoverTimer) {
      clearTimeout(drawerHoverTimer);
      drawerHoverTimer = null;
    }
  }

  function isPointerInRailZone() {
    var drawer = $('hostRailDrawer');
    var edge = $('hostRailEdge');
    return (drawer && drawer.matches(':hover')) || (edge && edge.matches(':hover'));
  }

  function scheduleDrawerClose() {
    if (drawerPinned || isMobileRail()) return;
    clearDrawerHoverTimer();
    drawerHoverTimer = setTimeout(function () {
      if (isPointerInRailZone()) return;
      closeDrawer();
    }, 280);
  }

  function syncIdleHint(show) {
    var hint = $('hostRailIdleHint');
    if (hint) hint.hidden = !show;
  }

  function meetingModeFromState(state) {
    state = state || {};
    if (state.readOnly) return 'readonly';
    if (!state.meetingActive) return 'idle';
    if (state.transcriptOpen) return 'transcript';
    return 'live';
  }

  function applyMeetingMode(mode) {
    if (mode === 'readonly') {
      syncIdleHint(false);
      drawerPinned = false;
      closeDrawer(true);
      return;
    }
    if (mode === 'idle') {
      syncIdleHint(true);
      drawerPinned = true;
      openDrawer({ pinned: true, scrollTo: 'control' });
      return;
    }
    syncIdleHint(false);
    if (mode === 'transcript') {
      drawerPinned = true;
      openDrawer({ pinned: true, scrollTo: 'control' });
      return;
    }
    drawerPinned = false;
    if (drawerOpen && !isPointerInRailZone()) closeDrawer(true);
  }

  /** 预览页场景驱动 */
  function syncForScene(scene) {
    if (!isRailLayout()) return;
    if (scene === lastPreviewScene) return;
    lastPreviewScene = scene;
    if (scene === 'idle') {
      syncIdleHint(true);
      drawerPinned = true;
      openDrawer({ pinned: true, scrollTo: 'control' });
      return;
    }
    syncIdleHint(false);
    if (scene === 'transcript') {
      drawerPinned = true;
      openDrawer({ pinned: true, scrollTo: 'control' });
      return;
    }
    drawerPinned = false;
    if (drawerOpen && !isPointerInRailZone()) closeDrawer(true);
  }

  /** 生产页状态驱动：仅在模式切换时改抽屉，避免 host_state 轮询打断悬停 */
  function syncMeetingState(state) {
    if (!isRailLayout()) return;
    var mode = meetingModeFromState(state);
    if (mode === lastMeetingMode) return;
    lastMeetingMode = mode;
    applyMeetingMode(mode);
  }

  function syncViewerMode(viewerOn) {
    if (!isRailLayout()) return;
    if (viewerOn) {
      drawerPinned = false;
      closeDrawer(true);
    }
  }

  function wireInteraction() {
    if (!isRailLayout() || wired) return;
    wired = true;

    var edge = $('hostRailEdge');
    var drawer = $('hostRailDrawer');
    var toggle = $('hostRailMobileToggle');

    function onHoverZoneEnter() {
      if (isMobileRail() || drawerPinned) return;
      clearDrawerHoverTimer();
      if (edge) edge.classList.add('is-hot');
      openDrawer();
    }

    function onHoverZoneLeave() {
      if (edge) edge.classList.remove('is-hot');
      if (drawerOpen && isPointerInRailZone()) return;
      scheduleDrawerClose();
    }

    if (edge) {
      edge.addEventListener('mouseenter', onHoverZoneEnter);
      edge.addEventListener('mouseleave', onHoverZoneLeave);
    }
    if (drawer) {
      drawer.addEventListener('mouseenter', function () {
        if (isMobileRail()) return;
        clearDrawerHoverTimer();
      });
      drawer.addEventListener('mouseleave', onHoverZoneLeave);
    }
    if (toggle) {
      toggle.addEventListener('click', function () {
        if (toggle.disabled) return;
        toggleDrawer();
      });
    }
    var shell = document.querySelector('.host-rail-shell');
    var drawerEl = $('hostRailDrawer');
    if (shell && drawerEl && !$('hostRailDrawerBackdrop')) {
      var backdrop = document.createElement('div');
      backdrop.id = 'hostRailDrawerBackdrop';
      backdrop.className = 'host-rail-drawer-backdrop';
      backdrop.hidden = true;
      backdrop.setAttribute('aria-hidden', 'true');
      shell.insertBefore(backdrop, drawerEl);
      backdrop.addEventListener('click', function () {
        drawerPinned = false;
        closeDrawer(true);
      });
    }
    if (MOBILE_RAIL_MQ && MOBILE_RAIL_MQ.addEventListener) {
      MOBILE_RAIL_MQ.addEventListener('change', function () {
        if (!isMobileRail() && !drawerPinned) closeDrawer(true);
        syncDrawerUi();
      });
    }
  }

  function init() {
    if (!isRailLayout()) return;
    wireInteraction();
    syncDrawerUi();
  }

  function resetMeetingMode() {
    lastMeetingMode = null;
  }

  function onFullscreenEnter() {
    closeDrawer(true);
  }

  /** 从后台切回时重置移动端抽屉命中区域（iOS fixed+transform 触摸失效） */
  function recoverFromBackground() {
    if (!isRailLayout()) return;
    syncDrawerUi();
    if (!isMobileRail()) return;
    var drawer = $('hostRailDrawer');
    if (!drawer || drawerOpen) return;
    drawer.style.display = 'none';
    void drawer.offsetHeight;
    drawer.style.display = '';
  }

  function handleEscapeKey(agendaDocFullscreen) {
    if (agendaDocFullscreen) return false;
    if (isRailLayout() && drawerOpen) {
      drawerPinned = false;
      closeDrawer(true);
      return true;
    }
    return false;
  }

  global.HostMeetingRail = {
    init: init,
    openDrawer: openDrawer,
    closeDrawer: closeDrawer,
    toggleDrawer: toggleDrawer,
    scrollDrawerTo: scrollDrawerTo,
    syncForScene: syncForScene,
    syncMeetingState: syncMeetingState,
    resetMeetingMode: resetMeetingMode,
    syncViewerMode: syncViewerMode,
    syncIdleHint: syncIdleHint,
    onFullscreenEnter: onFullscreenEnter,
    recoverFromBackground: recoverFromBackground,
    handleEscapeKey: handleEscapeKey,
    isMobile: isMobileRail
  };
})(typeof window !== 'undefined' ? window : this);
