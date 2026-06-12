/**
 * 会议前台 UI 预览：静态 mock 数据驱动，无需 token / API。
 */
(function () {
  var MOCK_TOPICS = [
    {
      title: '开场与议程说明',
      minutes: 5,
      status: 'COMPLETED',
      detail: '欢迎参会，说明本次会议目标与纪律。'
    },
    {
      title: '上季度指标回顾',
      minutes: 20,
      status: 'RUNNING',
      detail: '## 核心指标\n\n- DAU **+12%**（环比）\n- 7 日留存 **68%**\n- NPS **42**\n\n### 待讨论\n\n1. 增长瓶颈与获客成本\n2. 下季度 OKR 对齐'
    },
    {
      title: '会议检点',
      minutes: 8,
      status: 'PENDING'
    },
    {
      title: '产品路线讨论',
      minutes: 35,
      status: 'PENDING',
      detail: '讨论 Q3 优先级、资源分配与里程碑。'
    },
    {
      title: '总结与待办',
      minutes: 12,
      status: 'PENDING'
    }
  ];

  var MOCK_ROLL_CALL = [
    { name: '张明', status: 'ANSWERED', attendanceMode: 'OFFLINE' },
    { name: '李华', status: 'ANSWERED', attendanceMode: 'ONLINE' },
    { name: '王芳', status: 'PENDING', attendanceMode: 'OFFLINE' },
    { name: '赵强', status: 'MISSED', attendanceMode: 'OFFLINE' }
  ];

  var MOCK_TRANSCRIPT = [
    { speaker: '张明', text: '大家好，我们开始今天的复盘会。', final: true },
    { speaker: '李华', text: '上季度 DAU 增长主要来自…', final: true },
    { speaker: '王芳', text: '关于留存这块我觉得…', final: false }
  ];

  var AGENDA_DOC_MD = [
    '# 上季度指标回顾',
    '',
    '| 指标 | 本季 | 环比 |',
    '| --- | --- | --- |',
    '| DAU | 128 万 | +12% |',
    '| 留存 | 68% | +2pt |',
    '| NPS | 42 | +5 |',
    '',
    '## 增长拆解',
    '',
    '获客渠道中，自然流量占比提升至 41%，付费投放 ROI 环比改善 0.3。',
    '留存方面，新用户第 7 日留存受版本迭代影响小幅回升。',
    '',
    '## 风险与对策',
    '',
    '1. 竞品促销挤压中端市场\n2. 客服峰值响应延迟\n3. 核心功能学习成本偏高',
    '',
    '## 讨论纪要（预览占位）',
    '',
    '请各负责人准备下季度目标与资源诉求。全屏模式下右下角可常驻 **下一议题** 按钮。',
    '',
    '---',
    '',
    '附录：完整飞书文档在正式环境由会序资料 API 注入。'
  ].join('\n');

  var AGENDA_DOC_PRODUCT_MD = [
    '# 产品路线讨论',
    '',
    '## Q3 优先级',
    '',
    '1. 主持页体验统一\n2. 会序资料多源聚合\n3. 检点与 TTS 衔接优化',
    '',
    '## 里程碑',
    '',
    '| 阶段 | 时间 | 交付 |\n| --- | --- | --- |\n| M1 | 7 月 | 前台 UI 预览 |\n| M2 | 8 月 | 资料全屏 FAB |\n| M3 | 9 月 | 旁观链路 |',
    '',
    '全屏模式下右下角可常驻下一议题按钮。'
  ].join('\n');

  var lipTimer = null;
  var lipPhase = 0;
  var recTimerInterval = null;
  var recSeconds = 0;
  var currentScene = 'live';
  var viewerMode = false;
  var previewCurrentIdx = 1;
  var agendaDocFullscreen = false;
  var agendaDocNextFabRevealed = false;
  var agendaDocScrollFabRoots = [];

  var PREVIEW_SCENE_BTNS = ['btnSceneIdle', 'btnSceneLive', 'btnScenePaused', 'btnSceneRollcall', 'btnSceneTranscript'];
  var PREVIEW_EFFECT_BTNS = ['btnSceneSpeak', 'btnSceneLip'];

  function $(id) {
    return document.getElementById(id);
  }

  function fmtMs(ms) {
    ms = Math.max(0, ms | 0);
    var s = Math.floor(ms / 1000);
    var m = Math.floor(s / 60);
    var h = Math.floor(m / 60);
    function pad(n) { return (n < 10 ? '0' : '') + n; }
    return pad(h) + ':' + pad(m % 60) + ':' + pad(s % 60);
  }

  function fmtRecTimer(sec) {
    var h = Math.floor(sec / 3600);
    var m = Math.floor((sec % 3600) / 60);
    var s = sec % 60;
    function pad(n) { return (n < 10 ? '0' : '') + n; }
    return pad(h) + ':' + pad(m) + ':' + pad(s);
  }

  function rollCallStatusLabel(st, mode) {
    if (st === 'ANSWERED') return mode === 'ONLINE' ? '已到(线上)' : '已到';
    if (st === 'MISSED') return '未到';
    if (st === 'SKIPPED') return '已跳过';
    if (mode === 'ONLINE') return '待打开链接';
    return '待答到';
  }

  function showToast(text) {
    var stack = $('hostToastStack');
    if (!stack || !text) return;
    var el = document.createElement('div');
    el.className = 'host-toast';
    el.setAttribute('role', 'status');
    el.textContent = text;
    stack.appendChild(el);
    setTimeout(function () {
      el.classList.add('host-toast-fade');
      setTimeout(function () {
        if (el.parentNode) el.parentNode.removeChild(el);
      }, 400);
    }, 2200);
  }

  function renderMarkdown(el, md, baseClass) {
    if (!el) return;
    var base = baseClass || 'current-agenda-detail-body';
    var text = String(md || '').trim();
    if (!text) {
      el.className = base + ' muted';
      el.textContent = '本项暂无补充说明。';
      return;
    }
    if (typeof marked !== 'undefined' && typeof DOMPurify !== 'undefined') {
      el.className = base + ' md-body-host';
      var rawHtml = marked.parse(text, { breaks: true, gfm: true, mangle: false, headerIds: false });
      el.innerHTML = DOMPurify.sanitize(rawHtml, { USE_PROFILES: { html: true } });
      return;
    }
    el.className = base + ' current-agenda-detail-plain';
    el.textContent = text;
  }

  function setMeetingBadge(label, tone) {
    var badge = $('meetingStatusBadge');
    if (!badge) return;
    badge.textContent = label;
    badge.className = 'ui-badge ui-badge-' + (tone || 'neutral');
  }

  function setAvatarState(state, caption) {
    var stage = $('avatarStage');
    if (stage) stage.setAttribute('data-state', state);
    var badge = $('avatarBadge');
    if (badge) {
      badge.setAttribute('data-badge', state === 'speaking' ? 'speaking' : 'idle');
      badge.textContent = state === 'speaking' ? '播报中' : '等待中';
    }
    var cap = $('avatarCaption');
    if (cap) cap.textContent = caption != null ? caption : (state === 'speaking' ? '正在播报…' : '等待中');
    if (window.hostAvatarStrands && typeof window.hostAvatarStrands.setState === 'function') {
      window.hostAvatarStrands.setState(state);
    }
  }

  function stopLipDemo() {
    if (lipTimer) {
      clearInterval(lipTimer);
      lipTimer = null;
    }
    var mouth = $('avatarMouth');
    if (mouth) mouth.style.transform = 'scaleY(0.28)';
  }

  function startLipDemo() {
    stopLipDemo();
    setAvatarState('speaking', '口型演示中…');
    lipTimer = setInterval(function () {
      lipPhase += 0.35;
      var open = 0.22 + (0.5 + 0.5 * Math.sin(lipPhase)) * 0.75;
      var mouth = $('avatarMouth');
      if (mouth) mouth.style.transform = 'scaleY(' + open.toFixed(3) + ')';
    }, 50);
  }

  function stopRecTimer() {
    if (recTimerInterval) {
      clearInterval(recTimerInterval);
      recTimerInterval = null;
    }
  }

  function startRecTimer(fromSec) {
    stopRecTimer();
    recSeconds = fromSec || 0;
    var el = $('recStatusTimer');
    if (el) el.textContent = fmtRecTimer(recSeconds);
    recTimerInterval = setInterval(function () {
      recSeconds += 1;
      if (el) el.textContent = fmtRecTimer(recSeconds);
    }, 1000);
  }

  function setRecordingUi(mode) {
    var statusPanel = $('recordingStatusPanel');
    var transcriptPanel = $('transcriptPanel');
    var chip = statusPanel ? statusPanel.closest('.host-recording-chip') : null;
    var dot = $('recStatusDot');
    var main = $('recStatusMain');
    if (mode === 'realtime') {
      if (chip) chip.style.display = 'none';
      if (transcriptPanel) transcriptPanel.classList.remove('transcript-panel-hidden');
      if (dot) dot.className = 'rec-status-dot recording';
      if (main) main.textContent = '实时转写';
    } else if (mode === 'recording') {
      if (chip) chip.style.display = '';
      if (transcriptPanel) transcriptPanel.classList.add('transcript-panel-hidden');
      if (dot) dot.className = 'rec-status-dot recording';
      if (main) main.textContent = '正在录音';
    } else {
      if (chip) chip.style.display = '';
      if (transcriptPanel) transcriptPanel.classList.add('transcript-panel-hidden');
      if (dot) dot.className = 'rec-status-dot idle';
      if (main) main.textContent = '未开始';
      stopRecTimer();
      var t = $('recStatusTimer');
      if (t) t.textContent = '00:00:00';
    }
  }

  function renderTranscript() {
    var list = $('transcriptListHost');
    if (!list) return;
    list.innerHTML = MOCK_TRANSCRIPT.map(function (row) {
      var cls = row.final ? 'transcript-line' : 'transcript-line interim';
      var bodyCls = row.final ? '' : ' class="transcript-body"';
      return '<p class="' + cls + '"><span class="transcript-speaker">' + row.speaker + '：</span><span' + bodyCls + '>' + row.text + '</span></p>';
    }).join('');
  }

  function renderTopics(topics, currentIdx, preview) {
    var el = $('topics');
    if (!el) return;
    if (!topics.length) {
      el.innerHTML = '<p class="muted">暂无议程项</p>';
      return;
    }
    if (preview) {
      var totalMin = topics.reduce(function (a, t) {
        return a + (t.minutes != null && t.minutes > 0 ? t.minutes : 10);
      }, 0);
      var head = '<p class="muted" style="margin-bottom:10px">共 ' + topics.length + ' 项 · 合计约 ' + totalMin + ' 分钟（开始会议后将显示实时进度）</p>';
      el.innerHTML = head + topics.map(function (t, i) {
        return '<div class="topic PENDING">' + (i + 1) + '. ' + (t.title || '') + '（' + (t.minutes != null ? t.minutes : 10) + ' 分钟）</div>';
      }).join('');
      return;
    }
    el.innerHTML = topics.map(function (t, i) {
      var cur = i === currentIdx ? ' ▶' : '';
      return '<div class="topic ' + (t.status || 'PENDING') + '">' + (i + 1) + '. ' + t.title + '（' + t.minutes + ' 分钟）' + cur + '</div>';
    }).join('');
  }

  function renderCurrentAgenda(topics, currentIdx) {
    var panel = $('currentAgendaPanel');
    if (!panel) return;
    if (!topics.length || currentIdx < 0 || currentIdx >= topics.length) {
      panel.style.display = 'none';
      return;
    }
    panel.style.display = 'block';
    var cur = topics[currentIdx];
    var titleEl = $('currentAgendaTitle');
    var metaEl = $('currentAgendaMeta');
    var bodyEl = $('currentAgendaDetailBody');
    if (titleEl) titleEl.textContent = (currentIdx + 1) + '. ' + (cur.title || '');
    if (metaEl) {
      var st = cur.status || 'PENDING';
      var stZh = st === 'RUNNING' ? '进行中' : st === 'COMPLETED' ? '已完成' : st === 'SKIPPED' ? '已跳过' : '待进行';
      metaEl.textContent = '本项约 ' + (cur.minutes != null ? cur.minutes : 10) + ' 分钟 · ' + stZh;
    }
    renderMarkdown(bodyEl, cur.detail);
  }

  function meetingLivePreview() {
    return currentScene !== 'idle' && !viewerMode;
  }

  function topicHasAgendaDoc(topic) {
    if (!topic) return false;
    if (String(topic.title || '').indexOf('检点') >= 0) return false;
    return true;
  }

  function agendaDocMarkdownForIndex(idx) {
    if (idx === 3) return AGENDA_DOC_PRODUCT_MD;
    if (idx === 1) return AGENDA_DOC_MD;
    var t = MOCK_TOPICS[idx];
    return (t && t.detail) ? t.detail : AGENDA_DOC_MD;
  }

  function clearAgendaDocFullscreenLayout() {
    var card = $('agendaDocCard');
    if (!card) return;
    card.querySelectorAll('.agenda-doc-fs-scroll').forEach(function (node) {
      node.classList.remove('agenda-doc-fs-scroll');
    });
    var reportBody = $('progressReportBody');
    if (reportBody) reportBody.classList.remove('agenda-doc-fs-stack-scroll');
    var sentinel = $('agendaDocScrollEndSentinel');
    if (sentinel) sentinel.remove();
  }

  function ensureAgendaDocScrollSentinel(container) {
    if (!container) return;
    var old = $('agendaDocScrollEndSentinel');
    if (old) old.remove();
    var sentinel = document.createElement('div');
    sentinel.id = 'agendaDocScrollEndSentinel';
    sentinel.className = 'agenda-doc-scroll-end-sentinel';
    sentinel.setAttribute('aria-hidden', 'true');
    container.appendChild(sentinel);
  }

  function collectAgendaDocScrollRoots() {
    var reportBody = $('progressReportBody');
    if (!reportBody) return [];
    if (reportBody.classList.contains('agenda-doc-fs-scroll')) return [reportBody];
    var roots = [];
    reportBody.querySelectorAll('.agenda-doc-fs-scroll').forEach(function (el) {
      roots.push(el);
    });
    return roots.length ? roots : [reportBody];
  }

  function agendaDocIsScrolledToBottom() {
    var roots = collectAgendaDocScrollRoots();
    for (var i = 0; i < roots.length; i++) {
      var root = roots[i];
      if (root.scrollHeight > root.clientHeight + 6) {
        if (root.scrollTop + root.clientHeight < root.scrollHeight - 10) return false;
      }
    }
    return true;
  }

  function revealAgendaDocNextFab() {
    agendaDocNextFabRevealed = true;
    setAgendaDocNextFabVisible(true);
  }

  function resetAgendaDocNextFabRevealed() {
    agendaDocNextFabRevealed = false;
    var btn = $('btnAgendaDocNextTopicFab');
    if (btn) btn.classList.remove('is-visible');
  }

  function setAgendaDocNextFabVisible(show) {
    if (show) agendaDocNextFabRevealed = true;
    var btn = $('btnAgendaDocNextTopicFab');
    if (btn) btn.classList.toggle('is-visible', agendaDocNextFabRevealed);
  }

  function onAgendaDocScrollFabCheck() {
    if (agendaDocIsScrolledToBottom()) agendaDocNextFabRevealed = true;
    setAgendaDocNextFabVisible(agendaDocNextFabRevealed);
  }

  function unbindAgendaDocScrollFab() {
    agendaDocScrollFabRoots.forEach(function (pair) {
      if (pair && pair.root && pair.handler) pair.root.removeEventListener('scroll', pair.handler);
    });
    agendaDocScrollFabRoots = [];
  }

  function bindAgendaDocScrollFab() {
    unbindAgendaDocScrollFab();
    if (!agendaDocFullscreen || !meetingLivePreview()) {
      resetAgendaDocNextFabRevealed();
      return;
    }
    revealAgendaDocNextFab();
  }

  function syncAgendaDocFullscreenLayout() {
    var card = $('agendaDocCard');
    if (!card || !card.classList.contains('agenda-doc-fullscreen')) return;
    clearAgendaDocFullscreenLayout();
    var reportBody = $('progressReportBody');
    if (reportBody) {
      reportBody.classList.add('agenda-doc-fs-scroll');
      reportBody.style.minHeight = '0';
      reportBody.style.flex = '1';
      reportBody.style.maxHeight = 'none';
      reportBody.style.overflowY = 'auto';
      ensureAgendaDocScrollSentinel(reportBody);
    }
    bindAgendaDocScrollFab();
  }

  function syncAgendaDocFullscreenControls() {
    var showFab = agendaDocFullscreen && meetingLivePreview();
    var fab = $('agendaDocFsFab');
    var headFs = $('btnAgendaDocFullscreen');
    if (fab) {
      fab.style.display = showFab ? 'flex' : 'none';
      fab.setAttribute('aria-hidden', showFab ? 'false' : 'true');
    }
    if (headFs) headFs.style.display = agendaDocFullscreen ? 'none' : '';
    if (!showFab) resetAgendaDocNextFabRevealed();
    else revealAgendaDocNextFab();
  }

  function syncRailForScene(scene) {
    if (window.HostMeetingRail) HostMeetingRail.syncForScene(scene);
  }

  function syncRailViewerMode() {
    if (window.HostMeetingRail) HostMeetingRail.syncViewerMode(viewerMode);
  }

  function finishAgendaDocFullscreenRestore(card, btn) {
    if (card.__fsAnchor && card.__fsAnchor.parentNode) {
      card.__fsAnchor.parentNode.insertBefore(card, card.__fsAnchor);
      card.__fsAnchor.remove();
      card.__fsAnchor = null;
    }
    card.classList.remove('agenda-doc-fullscreen');
    card.classList.remove('agenda-doc-fs-ready');
    document.body.classList.remove('agenda-doc-fullscreen-lock');
    var reportBody = $('progressReportBody');
    if (reportBody) {
      reportBody.style.overflowY = reportBody.dataset.fsPrevOverflow || 'auto';
      reportBody.style.minHeight = '';
      reportBody.style.maxHeight = '';
      reportBody.style.flex = '';
      delete reportBody.dataset.fsPrevOverflow;
      clearAgendaDocFullscreenLayout();
      unbindAgendaDocScrollFab();
    }
    syncAgendaDocFullscreenControls();
    if (btn) {
      btn.setAttribute('aria-pressed', 'false');
      btn.title = '进入全屏';
      btn.setAttribute('aria-label', btn.title);
    }
  }

  function setAgendaDocFullscreen(on) {
    var card = $('agendaDocCard');
    var btn = $('btnAgendaDocFullscreen');
    if (!card) return;
    var wantOn = !!on;
    if (wantOn && card.style.display === 'none') return;
    if (!wantOn && agendaDocFullscreen) {
      agendaDocFullscreen = false;
      if (window.AgendaFsPresent) {
        AgendaFsPresent.playExit(card, function () {
          finishAgendaDocFullscreenRestore(card, btn);
        });
      } else {
        finishAgendaDocFullscreenRestore(card, btn);
      }
      return;
    }
    if (!wantOn) return;
    agendaDocFullscreen = true;
    if (window.HostMeetingRail) HostMeetingRail.onFullscreenEnter();
    if (!card.__fsAnchor) {
      var anchor = document.createComment('agendaDocCard-fs-anchor');
      card.parentNode.insertBefore(anchor, card);
      card.__fsAnchor = anchor;
    }
    document.body.appendChild(card);
    card.classList.add('agenda-doc-fullscreen');
    document.body.classList.add('agenda-doc-fullscreen-lock');
    var reportBody = $('progressReportBody');
    if (reportBody) {
      reportBody.dataset.fsPrevOverflow = reportBody.style.overflowY || '';
      reportBody.style.overflowY = 'hidden';
      reportBody.style.minHeight = '0';
      reportBody.style.maxHeight = 'none';
      reportBody.style.flex = '1';
      syncAgendaDocFullscreenLayout();
    }
    syncAgendaDocFullscreenControls();
    if (btn) {
      btn.setAttribute('aria-pressed', 'true');
      btn.title = '退出全屏';
      btn.setAttribute('aria-label', btn.title);
    }
    if (window.AgendaFsPresent) AgendaFsPresent.playEnter(card);
  }

  function renderAgendaDoc(show, topicIdx) {
    var card = $('agendaDocCard');
    if (!card) return;
    if (!show) {
      card.style.display = 'none';
      setAgendaDocFullscreen(false);
      return;
    }
    var topic = MOCK_TOPICS[topicIdx];
    if (!topicHasAgendaDoc(topic)) {
      card.style.display = 'none';
      setAgendaDocFullscreen(false);
      return;
    }
    card.style.display = 'block';
    var titleEl = $('agendaDocPanelTitle');
    if (titleEl) {
      titleEl.innerHTML = '<svg class="icon-sm" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 20a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8l6 6z"/><path d="M12 10v6"/><path d="M9 13h6"/></svg> 当前议题资料 · ' + (topic.title || '');
    }
    if (agendaDocFullscreen && window.AgendaFsPresent) AgendaFsPresent.updateChromeText();
    var body = $('progressReportBody');
    if (body) renderMarkdown(body, agendaDocMarkdownForIndex(topicIdx), 'transcript-box host-main-doc-body');
    var link = $('agendaDocExternalLink');
    if (link) {
      link.style.display = agendaDocFullscreen ? 'none' : 'block';
      link.innerHTML = '飞书文档：<a href="#" onclick="return false">示例 Wiki 链接（预览占位）</a>';
    }
    if (agendaDocFullscreen) syncAgendaDocFullscreenLayout();
    syncAgendaDocFullscreenControls();
  }

  function getTopicsSnapshot() {
    return MOCK_TOPICS.map(function (t, i) {
      var status = 'PENDING';
      if (i < previewCurrentIdx) status = 'COMPLETED';
      else if (i === previewCurrentIdx) status = 'RUNNING';
      return {
        title: t.title,
        minutes: t.minutes,
        status: status,
        detail: t.detail || ''
      };
    });
  }

  function previewGoNextTopic() {
    if (!meetingLivePreview()) return;
    if (previewCurrentIdx >= MOCK_TOPICS.length - 1) {
      showToast('已是最后一项议程');
      return;
    }
    previewCurrentIdx += 1;
    refreshLiveUi();
    showToast('已切换至：' + MOCK_TOPICS[previewCurrentIdx].title);
    if (agendaDocFullscreen) {
      var reportBody = $('progressReportBody');
      if (reportBody) reportBody.scrollTop = 0;
      syncAgendaDocFullscreenLayout();
      revealAgendaDocNextFab();
    }
  }

  function renderRollCall(show, active) {
    var wrap = $('rollCallWrap');
    if (!wrap) return;
    wrap.style.display = show ? 'block' : 'none';
    if (!show) return;
    var list = $('rollCallList');
    var cd = $('rollCallCountdown');
    if (list) {
      list.innerHTML = MOCK_ROLL_CALL.map(function (p, i) {
        var cur = active && i === 2;
        var st = p.status || 'PENDING';
        var mode = (p.attendanceMode || 'OFFLINE').toUpperCase();
        var tag = mode === 'ONLINE' ? ' <span class="muted">[线上]</span>' : ' <span class="muted">[线下]</span>';
        return '<div class="rc-row ' + st + (cur ? ' current' : '') + '"><span class="rc-name">' + (i + 1) + '. ' + p.name + tag + '</span><span class="rc-st">' + rollCallStatusLabel(st, mode) + '</span></div>';
      }).join('');
    }
    if (cd) cd.textContent = active ? '线下点名中 · 答到窗口剩余 18 秒' : '已配置检点会序；具备应到名单时开场播完将自动检点。';
    var btnRs = $('btnRollCallStart');
    var btnRk = $('btnRollCallSkip');
    if (btnRs) btnRs.disabled = active;
    if (btnRk) btnRk.disabled = !active;
  }

  function applyViewerMode(on) {
    viewerMode = !!on;
    var notice = $('viewerNotice');
    if (notice) notice.style.display = viewerMode ? 'block' : 'none';
    [
      'btnStartMeeting', 'btnPauseMeeting', 'btnResumeMeeting', 'btnEnd',
      'btnNext', 'btnSkip', 'btnExtendTopic', 'btnRollCallStart', 'btnRollCallSkip',
      'btnCopyViewerLink'
    ].forEach(function (id) {
      var el = $(id);
      if (!el) return;
      if (viewerMode) {
        el.style.display = 'none';
        el.disabled = true;
      } else {
        el.style.display = '';
      }
    });
    var rec = $('hostRecordingWidget');
    if (rec) rec.style.display = viewerMode ? 'none' : '';
    if (viewerMode) {
      setMeetingBadge('旁观', 'primary');
      setAgendaDocFullscreen(false);
    }
    syncAgendaDocFullscreenControls();
    syncPreviewDockButtons();
    syncRailViewerMode();
  }

  function sceneToMeetingDockActive(scene) {
    if (scene === 'idle') return 'btnSceneIdle';
    if (scene === 'paused') return 'btnScenePaused';
    if (scene === 'rollcall') return 'btnSceneRollcall';
    if (scene === 'transcript') return 'btnSceneTranscript';
    return 'btnSceneLive';
  }

  function setDockBtnState(el, active) {
    if (!el) return;
    el.classList.remove('ui-btn-primary', 'ui-btn-frosted', 'ui-btn-secondary', 'ui-btn-soft');
    el.classList.add(active ? 'ui-btn-primary' : 'ui-btn-frosted');
  }

  function syncPreviewDockButtons() {
    var activeSceneBtn = sceneToMeetingDockActive(currentScene);
    PREVIEW_SCENE_BTNS.forEach(function (id) {
      setDockBtnState($(id), id === activeSceneBtn);
    });
    setDockBtnState($('btnSceneViewer'), viewerMode);
    PREVIEW_EFFECT_BTNS.forEach(function (id) {
      var sceneKey = id === 'btnSceneSpeak' ? 'speaking' : 'lip';
      setDockBtnState($(id), currentScene === sceneKey);
    });
    document.body.classList.toggle('scene-idle', currentScene === 'idle');
  }

  function setControlButtons(scene) {
    var btnStart = $('btnStartMeeting');
    var btnPause = $('btnPauseMeeting');
    var btnResume = $('btnResumeMeeting');
    var btnCopy = $('btnCopyViewerLink');
    var btnNext = $('btnNext');
    var btnSkip = $('btnSkip');
    var btnEnd = $('btnEnd');
    var extSel = $('extendMinutesSelect');
    var extBtn = $('btnExtendTopic');

    if (viewerMode) return;

    var idle = scene === 'idle';
    var paused = scene === 'paused';
    var live = !idle && !paused;

    if (btnStart) {
      btnStart.disabled = !idle;
      btnStart.style.display = idle ? '' : 'none';
    }
    if (btnPause) {
      btnPause.disabled = !live;
      btnPause.style.display = live ? '' : 'none';
    }
    if (btnResume) {
      btnResume.style.display = paused ? '' : 'none';
      btnResume.disabled = !paused;
    }
    if (btnCopy) btnCopy.style.display = live || paused ? 'inline-block' : 'none';
    if (btnNext) btnNext.disabled = !live && !paused;
    if (btnSkip) btnSkip.disabled = !live && !paused;
    if (btnEnd) btnEnd.disabled = idle;
    if (extSel) extSel.disabled = !(live || paused);
    if (extBtn) extBtn.disabled = !(live || paused);
  }

  function refreshLiveUi() {
    var topics = getTopicsSnapshot();
    var topicLeft = 12 * 60 * 1000 + 34000;
    var meetLeft = 68 * 60 * 1000;
    if (currentScene === 'paused') {
      setMeetingBadge('已暂停', 'warning');
      topicLeft = 8 * 60 * 1000;
      meetLeft = 52 * 60 * 1000;
    } else {
      setMeetingBadge('进行中', 'success');
    }
    var topicTimer = $('topicTimer');
    var meetTimer = $('meetTimer');
    if (topicTimer) topicTimer.textContent = '议题剩余 ' + fmtMs(topicLeft);
    if (meetTimer) meetTimer.textContent = '会议剩余 ' + fmtMs(meetLeft);
    renderTopics(topics, previewCurrentIdx, false);
    renderCurrentAgenda(topics, previewCurrentIdx);
    var onRollCall = String(MOCK_TOPICS[previewCurrentIdx].title || '').indexOf('检点') >= 0;
    renderAgendaDoc(!onRollCall, previewCurrentIdx);
    renderRollCall(onRollCall, currentScene === 'rollcall');
    setControlButtons(currentScene === 'paused' ? 'paused' : 'live');
    if (currentScene === 'transcript') {
      setRecordingUi('realtime');
      renderTranscript();
    } else {
      setRecordingUi('recording');
    }
    if (currentScene === 'speaking' || currentScene === 'lip') {
      setAvatarState('speaking', currentScene === 'lip' ? '口型演示中…' : '正在播报当前议题…');
      var mouth = $('avatarMouth');
      if (mouth && currentScene === 'speaking') mouth.style.transform = 'scaleY(0.65)';
      if (currentScene === 'lip') startLipDemo();
    } else {
      setAvatarState('idle', onRollCall ? '检点播报中…' : '等待中');
    }
    syncAgendaDocFullscreenControls();
  }

  function applyScene(scene) {
    currentScene = scene;
    stopLipDemo();

    if (scene === 'idle') {
      setAgendaDocFullscreen(false);
      previewCurrentIdx = 1;
      setMeetingBadge('未开始', 'neutral');
      var topicTimerIdle = $('topicTimer');
      var meetTimerIdle = $('meetTimer');
      if (topicTimerIdle) topicTimerIdle.textContent = '议题剩余 —:—';
      if (meetTimerIdle) meetTimerIdle.textContent = '会议剩余 —:—';
      $('currentAgendaPanel').style.display = 'none';
      renderTopics(MOCK_TOPICS, -1, true);
      renderAgendaDoc(false);
      renderRollCall(false, false);
      setRecordingUi('idle');
      setAvatarState('idle', '等待开始会议…');
      setControlButtons('idle');
      syncPreviewDockButtons();
      syncRailForScene('idle');
      return;
    }

    if (scene === 'rollcall') previewCurrentIdx = 2;
    else if (scene !== 'live' && scene !== 'paused' && scene !== 'transcript' && scene !== 'speaking' && scene !== 'lip') {
      /* 保持当前议题下标 */
    } else if (previewCurrentIdx < 1 || previewCurrentIdx >= MOCK_TOPICS.length) {
      previewCurrentIdx = 1;
    }

    if (scene === 'transcript') startRecTimer(754);
    else startRecTimer(754);

    refreshLiveUi();
    syncPreviewDockButtons();
    syncRailForScene(scene);
  }

  function wireDemoButtons() {
    var demoIds = [
      'btnStartMeeting', 'btnPauseMeeting', 'btnResumeMeeting', 'btnSkip',
      'btnExtendTopic', 'btnEnd', 'btnRollCallStart', 'btnRollCallSkip', 'btnCopyViewerLink'
    ];
    demoIds.forEach(function (id) {
      var el = $(id);
      if (!el) return;
      el.addEventListener('click', function () {
        if (viewerMode) return;
        showToast('预览模式：「' + (el.textContent || id).trim() + '」仅 UI 演示');
      });
    });
    var btnNext = $('btnNext');
    if (btnNext) btnNext.addEventListener('click', function () {
      if (viewerMode) return;
      previewGoNextTopic();
    });
    var btnFs = $('btnAgendaDocFullscreen');
    if (btnFs) btnFs.addEventListener('click', function () {
      if (viewerMode) return;
      setAgendaDocFullscreen(!agendaDocFullscreen);
    });
    var btnFsFab = $('btnAgendaDocFullscreenFab');
    if (btnFsFab) btnFsFab.addEventListener('click', function () {
      setAgendaDocFullscreen(false);
    });
    var btnNextFab = $('btnAgendaDocNextTopicFab');
    if (btnNextFab) btnNextFab.addEventListener('click', function () {
      if (viewerMode) return;
      previewGoNextTopic();
    });
    document.addEventListener('keydown', function (e) {
      if (e.key !== 'Escape') return;
      if (agendaDocFullscreen) {
        setAgendaDocFullscreen(false);
        return;
      }
      if (window.HostMeetingRail && HostMeetingRail.handleEscapeKey(false)) {
        e.preventDefault();
      }
    });
  }

  function wireSceneButtons() {
    var map = {
      btnSceneIdle: 'idle',
      btnSceneLive: 'live',
      btnScenePaused: 'paused',
      btnSceneSpeak: 'speaking',
      btnSceneLip: 'lip',
      btnSceneRollcall: 'rollcall',
      btnSceneTranscript: 'transcript',
      btnSceneViewer: 'viewer'
    };
    Object.keys(map).forEach(function (btnId) {
      var el = $(btnId);
      if (!el) return;
      el.addEventListener('click', function () {
        if (btnId === 'btnSceneViewer') {
          var next = !viewerMode;
          applyViewerMode(next);
          if (next) {
            applyScene('live');
          } else {
            applyScene(currentScene === 'viewer' ? 'live' : currentScene);
          }
          el.setAttribute('aria-pressed', next ? 'true' : 'false');
          return;
        }
        if (btnId === 'btnSceneLip' && lipTimer) {
          stopLipDemo();
          applyScene('live');
          return;
        }
        applyViewerMode(false);
        var viewerBtn = $('btnSceneViewer');
        if (viewerBtn) viewerBtn.setAttribute('aria-pressed', 'false');
        applyScene(map[btnId]);
      });
    });
  }

  function waitForStrandsThen(fn, attempt) {
    attempt = attempt || 0;
    if (window.hostAvatarStrands && window.hostAvatarStrands.active) {
      fn();
      return;
    }
    if (attempt < 80) requestAnimationFrame(function () { waitForStrandsThen(fn, attempt + 1); });
  }

  function init() {
    $('meetingTitleH1').textContent = '2026 Q2 产品复盘会';
    $('titleLine').textContent = '主持人：张明 · 预计 90 分钟 · 预览数据';
    wireDemoButtons();
    wireSceneButtons();
    if (window.HostMeetingRail) HostMeetingRail.init();
    renderTranscript();
    applyScene('live');
    waitForStrandsThen(function () {
      if (currentScene === 'speaking' || currentScene === 'lip') return;
      setAvatarState('idle', currentScene === 'rollcall' ? '检点播报中…' : '等待中');
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
