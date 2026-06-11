/**
 * 主持页资料区统一图片 Lightbox：点击放大、滚轮缩放、ESC 关闭。
 * 绑定：ImageLightbox.bind('.feishu-doc-panel');
 */
(function (global) {
  'use strict';

  var overlay = null;
  var imgEl = null;
  var scale = 1;
  var translateX = 0;
  var translateY = 0;
  var dragging = false;
  var dragStartX = 0;
  var dragStartY = 0;
  var dragOriginX = 0;
  var dragOriginY = 0;

  function ensureOverlay() {
    if (overlay) return;
    overlay = document.createElement('div');
    overlay.className = 'sm-image-lightbox';
    overlay.setAttribute('role', 'dialog');
    overlay.setAttribute('aria-modal', 'true');
    overlay.hidden = true;
    overlay.innerHTML = '<button type="button" class="sm-image-lightbox-close" aria-label="关闭">×</button>'
        + '<div class="sm-image-lightbox-stage"><img class="sm-image-lightbox-img" alt="" /></div>';
    document.body.appendChild(overlay);
    imgEl = overlay.querySelector('.sm-image-lightbox-img');
    overlay.querySelector('.sm-image-lightbox-close').addEventListener('click', close);
    overlay.addEventListener('click', function (e) {
      if (e.target === overlay || e.target.classList.contains('sm-image-lightbox-stage')) {
        close();
      }
    });
    overlay.addEventListener('wheel', function (e) {
      if (overlay.hidden) return;
      e.preventDefault();
      var delta = e.deltaY < 0 ? 0.12 : -0.12;
      setScale(scale + delta);
    }, { passive: false });
    imgEl.addEventListener('dblclick', function () {
      setScale(scale >= 1.8 ? 1 : 2);
    });
    imgEl.addEventListener('mousedown', function (e) {
      if (scale <= 1) return;
      dragging = true;
      dragStartX = e.clientX;
      dragStartY = e.clientY;
      dragOriginX = translateX;
      dragOriginY = translateY;
      e.preventDefault();
    });
    document.addEventListener('mousemove', function (e) {
      if (!dragging) return;
      translateX = dragOriginX + (e.clientX - dragStartX);
      translateY = dragOriginY + (e.clientY - dragStartY);
      applyTransform();
    });
    document.addEventListener('mouseup', function () {
      dragging = false;
    });
    document.addEventListener('keydown', function (e) {
      if (overlay.hidden) return;
      if (e.key === 'Escape') close();
    });
  }

  function applyTransform() {
    if (!imgEl) return;
    imgEl.style.transform = 'translate(' + translateX + 'px,' + translateY + 'px) scale(' + scale + ')';
  }

  function setScale(next) {
    scale = Math.max(0.5, Math.min(4, next));
    if (scale <= 1) {
      translateX = 0;
      translateY = 0;
    }
    applyTransform();
  }

  function open(src, alt) {
    if (!src) return;
    ensureOverlay();
    scale = 1;
    translateX = 0;
    translateY = 0;
    imgEl.src = src;
    imgEl.alt = alt || '放大图片';
    applyTransform();
    overlay.hidden = false;
    document.body.classList.add('sm-lightbox-open');
  }

  function close() {
    if (!overlay) return;
    overlay.hidden = true;
    imgEl.removeAttribute('src');
    document.body.classList.remove('sm-lightbox-open');
  }

  function isZoomable(img) {
    if (!img || img.tagName !== 'IMG') return false;
    var src = img.getAttribute('src');
    if (!src || src.length < 4) return false;
    if (img.naturalWidth === 0 && img.complete && !img.classList.contains('local-material-loaded')) {
      return false;
    }
    return true;
  }

  function bind(rootSelector) {
    document.addEventListener('click', function (e) {
      var root = document.querySelector(rootSelector);
      if (!root || !root.contains(e.target)) return;
      var target = e.target;
      if (target.tagName !== 'IMG') return;
      if (!target.classList.contains('sm-zoomable-img')
          && !target.classList.contains('structured-doc-image')
          && !target.classList.contains('local-material-img')
          && !target.classList.contains('structured-slide-image')
          && !target.closest('.feishu-doc-body')
          && !target.closest('.local-material-gallery')
          && !target.closest('.local-material-preview')
          && !target.closest('.structured-slide-deck')) {
        return;
      }
      if (!isZoomable(target)) return;
      e.preventDefault();
      e.stopPropagation();
      open(target.currentSrc || target.src, target.alt);
    });
    document.addEventListener('error', function (e) {
      if (e.target && e.target.tagName === 'IMG' && e.target.classList.contains('structured-doc-image')) {
        e.target.alt = '图片加载失败';
        e.target.classList.add('structured-doc-image-error');
      }
    }, true);
  }

  global.ImageLightbox = { bind: bind, open: open, close: close };
})(typeof window !== 'undefined' ? window : this);
