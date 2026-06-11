(function () {
  'use strict';

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/"/g, '&quot;');
  }

  function hint(text) {
    const t = text == null ? '' : String(text);
    if (!t) return '';
    return '<p class="form-hint">' + esc(t) + '</p>';
  }

  function field(label, controlHtml, hintText) {
    const lbl = esc(label);
    const hintBlock = hint(hintText);
    const html = String(controlHtml || '');
    if (!lbl) {
      return '<div class="form-field">' + html + hintBlock + '</div>';
    }
    if (/type=["']checkbox|type=["']radio|check-label|form-check-row/.test(html)) {
      return '<div class="form-field"><span class="form-field-caption">' + lbl + '</span>' + html + hintBlock + '</div>';
    }
    if (/^\s*<label[\s>]/i.test(html)) {
      return '<div class="form-field">' + html + hintBlock + '</div>';
    }
    return '<div class="form-field"><label>' + lbl + '</label>' + html + hintBlock + '</div>';
  }

  function bindSelectHint(selectId, hintElId, hintsMap) {
    const sel = document.getElementById(selectId);
    const el = document.getElementById(hintElId);
    if (!sel || !el || !hintsMap) return;
    const update = function () {
      const v = sel.value;
      const text = hintsMap[v] != null ? String(hintsMap[v]) : '';
      el.textContent = text;
      el.className = text ? 'form-hint' : 'form-hint muted';
    };
    sel.addEventListener('change', update);
    update();
  }

  window.AdminForm = {
    esc: esc,
    hint: hint,
    field: field,
    bindSelectHint: bindSelectHint
  };
})();
