(function () {
  'use strict';
  if (window.__hdaoTvSpatialNavigation) return;
  window.__hdaoTvSpatialNavigation = true;

  var STYLE_TEXT =
    '@keyframes __hdao_tv_pulse {' +
    '0%,100%{box-shadow:0 0 9px 3px rgba(255,112,67,.55),0 0 0 2px rgba(255,255,255,.95)}' +
    '50%{box-shadow:0 0 18px 6px rgba(255,112,67,.82),0 0 0 2px #fff}' +
    '}' +
    '.__hdao_tv_focus{' +
    'outline:3px solid #ff7043!important;' +
    'outline-offset:3px!important;' +
    'border-radius:8px!important;' +
    'animation:__hdao_tv_pulse 1.35s ease-in-out infinite!important;' +
    'position:relative!important;' +
    'z-index:2147483646!important;' +
    '}';

  var SELECTOR = [
    'a[href]', 'button', 'input', 'select', 'textarea', 'video',
    '[role="button"]', '[role="link"]', '[role="tab"]',
    '[role="menuitem"]', '[role="option"]', '[role="checkbox"]',
    '[role="radio"]', '[role="switch"]', '[aria-label]',
    '[tabindex]:not([tabindex="-1"])', '[onclick]',
    '.art-control', '.art-control-item', '.art-settings-item'
  ].join(',');

  var current = null;
  var observer = null;

  function ensureStyle(root) {
    if (!root || root.__hdaoTvStyled) return;
    root.__hdaoTvStyled = true;
    var style = document.createElement('style');
    style.textContent = STYLE_TEXT;
    var host = root === document ? (document.head || document.documentElement) : root;
    host.appendChild(style);
  }

  function isVisible(element) {
    if (!element || !element.isConnected || element.disabled) return false;
    var rect = element.getBoundingClientRect();
    if (rect.width < 4 || rect.height < 4) return false;
    var style = window.getComputedStyle(element);
    if (style.display === 'none' || style.visibility === 'hidden') return false;
    return parseFloat(style.opacity || '1') >= 0.05;
  }

  function wrapsCandidate(element) {
    if (element.querySelector && element.querySelector(SELECTOR)) return true;
    return !!(element.shadowRoot && element.shadowRoot.querySelector(SELECTOR));
  }

  function observe(root) {
    if (!observer || !root || root.__hdaoTvObserved) return;
    root.__hdaoTvObserved = true;
    observer.observe(root, { childList: true, subtree: true });
  }

  function collectDeep(root, output) {
    var elements = root.querySelectorAll('*');
    for (var index = 0; index < elements.length; index += 1) {
      var element = elements[index];
      if (element.shadowRoot) {
        ensureStyle(element.shadowRoot);
        observe(element.shadowRoot);
        collectDeep(element.shadowRoot, output);
      }
      if (element === current || !element.matches || !element.matches(SELECTOR)) continue;
      if (wrapsCandidate(element) || !isVisible(element)) continue;
      output.push(element);
    }
  }

  function candidates() {
    var output = [];
    collectDeep(document, output);
    return output;
  }

  function rect(element) {
    return element.getBoundingClientRect();
  }

  function rangesOverlap(aStart, aEnd, bStart, bEnd) {
    return Math.min(aEnd, bEnd) - Math.max(aStart, bStart) > 0;
  }

  function pickInitial() {
    var list = candidates();
    var centerX = window.innerWidth / 2;
    var centerY = window.innerHeight / 2;
    var best = null;
    var bestDistance = Infinity;

    for (var index = 0; index < list.length; index += 1) {
      var itemRect = rect(list[index]);
      var distance = Math.abs(itemRect.left + itemRect.width / 2 - centerX) +
        Math.abs(itemRect.top + itemRect.height / 2 - centerY);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = list[index];
      }
    }
    return best;
  }

  function findNext(direction) {
    if (!current || !isVisible(current)) {
      current = null;
      return pickInitial();
    }

    var currentRect = rect(current);
    var currentX = currentRect.left + currentRect.width / 2;
    var currentY = currentRect.top + currentRect.height / 2;
    var list = candidates();
    var vertical = direction === 'up' || direction === 'down';

    // Strict same-row/column, then a 45-degree cone, then the half-plane.
    for (var pass = 0; pass < 3; pass += 1) {
      var best = null;
      var bestScore = Infinity;

      for (var index = 0; index < list.length; index += 1) {
        var element = list[index];
        var itemRect = rect(element);
        var itemX = itemRect.left + itemRect.width / 2;
        var itemY = itemRect.top + itemRect.height / 2;
        var mainDistance;
        var crossDistance;

        if (direction === 'up') {
          if (itemY >= currentY - 2) continue;
          mainDistance = currentRect.top - itemRect.bottom;
          crossDistance = Math.abs(itemX - currentX);
        } else if (direction === 'down') {
          if (itemY <= currentY + 2) continue;
          mainDistance = itemRect.top - currentRect.bottom;
          crossDistance = Math.abs(itemX - currentX);
        } else if (direction === 'left') {
          if (itemX >= currentX - 2) continue;
          mainDistance = currentRect.left - itemRect.right;
          crossDistance = Math.abs(itemY - currentY);
        } else {
          if (itemX <= currentX + 2) continue;
          mainDistance = itemRect.left - currentRect.right;
          crossDistance = Math.abs(itemY - currentY);
        }

        mainDistance = Math.max(0, mainDistance);
        var overlap = vertical
          ? rangesOverlap(currentRect.left, currentRect.right, itemRect.left, itemRect.right)
          : rangesOverlap(currentRect.top, currentRect.bottom, itemRect.top, itemRect.bottom);

        if (pass === 0 && !overlap) continue;
        if (pass === 1 && crossDistance > mainDistance + 4) continue;

        var score = mainDistance + crossDistance * (pass === 2 ? 5 : 1.5);
        if (score < bestScore) {
          bestScore = score;
          best = element;
        }
      }
      if (best) return best;
    }
    return null;
  }

  function setCurrent(element) {
    if (current) current.classList.remove('__hdao_tv_focus');
    current = element;
    if (!current) return;

    current.classList.add('__hdao_tv_focus');
    try {
      current.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
    } catch (error) {
      current.scrollIntoView();
    }
  }

  function scrollContainerFor(element) {
    var node = element ? element.parentElement : null;
    while (node && node !== document.body) {
      var style = window.getComputedStyle(node);
      var scrollable = style.overflowY === 'auto' || style.overflowY === 'scroll';
      if (scrollable && node.scrollHeight > node.clientHeight + 8) return node;
      node = node.parentElement;
    }
    return document.scrollingElement || document.documentElement;
  }

  function scrollFallback(direction) {
    if (direction !== 'up' && direction !== 'down') return;
    var scroller = scrollContainerFor(current);
    var delta = Math.round(window.innerHeight * 0.55) * (direction === 'up' ? -1 : 1);
    try {
      scroller.scrollBy({ top: delta, behavior: 'smooth' });
    } catch (error) {
      scroller.scrollTop += delta;
    }

    window.setTimeout(function () {
      var next = findNext(direction);
      if (next) setCurrent(next);
    }, 360);
  }

  function deepestActiveElement() {
    var element = document.activeElement;
    while (element && element.shadowRoot && element.shadowRoot.activeElement) {
      element = element.shadowRoot.activeElement;
    }
    return element;
  }

  function isTextInput(element) {
    if (!element) return false;
    if (element.tagName === 'TEXTAREA' || element.isContentEditable) return true;
    if (element.tagName !== 'INPUT') return false;
    var type = (element.type || 'text').toLowerCase();
    return ['text', 'password', 'email', 'search', 'number', 'tel', 'url'].indexOf(type) >= 0;
  }

  function activate(element) {
    if (!element) return;
    if (isTextInput(element)) {
      element.focus();
      return;
    }
    try {
      element.focus({ preventScroll: true });
    } catch (error) {
      element.focus();
    }
    element.click();
  }

  var directions = {
    ArrowLeft: 'left',
    ArrowRight: 'right',
    ArrowUp: 'up',
    ArrowDown: 'down'
  };

  window.addEventListener('keydown', function (event) {
    var direction = directions[event.key];
    var active = deepestActiveElement();

    if (isTextInput(active)) {
      if (direction === 'left' || direction === 'right' || event.key === 'Enter') return;
      if (direction === 'up' || direction === 'down') active.blur();
    }

    if (direction) {
      event.preventDefault();
      event.stopPropagation();
      var next = findNext(direction);
      if (next) setCurrent(next);
      else if (!current) setCurrent(pickInitial());
      else scrollFallback(direction);
      return;
    }

    if (event.key === 'Enter') {
      event.preventDefault();
      event.stopPropagation();
      if (!current) setCurrent(pickInitial());
      else activate(current);
    }
  }, true);

  ensureStyle(document);
  observer = new MutationObserver(function () {
    if (current && !isVisible(current)) setCurrent(pickInitial());
  });
  observe(document.documentElement);

  window.setTimeout(function () {
    if (!current) setCurrent(pickInitial());
  }, 850);
})();
