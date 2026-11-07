/* ArtistikCity landing pages: progressive enhancement only (the page works without it).
   Lite video embeds, gallery lightbox, before/after slider, sticky enrol bar, scroll reveal, CTA click events, and the
   page's marketing tags (GA4 / GTM / Meta Pixel), which load only after the visitor accepts cookies (ac_consent=all,
   the same cookie as the rest of the site). Nothing is tracked in admin previews. */
(function () {
  'use strict';
  var d = document;
  var body = d.body;
  var pageId = body.getAttribute('data-page');
  var preview = body.hasAttribute('data-preview');
  d.documentElement.classList.add('lp-js');

  function csrf() {
    var m = d.querySelector('meta[name="csrf-token"]');
    return m ? m.getAttribute('content') : '';
  }

  function send(event, extra) {
    if (preview || !pageId) return;
    try {
      var data = { page: Number(pageId), event: event };
      for (var k in extra || {}) data[k] = extra[k];
      fetch('/landing/e', { method: 'POST', keepalive: true, credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf(), 'X-Requested-With': 'XMLHttpRequest' },
        body: JSON.stringify(data) }).catch(function () {});
    } catch (e) { /* analytics never breaks the page */ }
    if (window.gtag) window.gtag('event', event === 'CTA' ? 'select_content' : event.toLowerCase(), { content_type: 'cta', item_id: (extra && extra.label) || '' });
    if (window.fbq && event === 'CTA') window.fbq('track', 'Lead');
  }

  // CTA clicks; utm_* on the address bar is carried to the enrol link
  var params = new URLSearchParams(location.search);
  var utm = [];
  ['utm_source', 'utm_medium', 'utm_campaign'].forEach(function (k) { if (params.get(k)) utm.push(k + '=' + encodeURIComponent(params.get(k))); });
  d.querySelectorAll('[data-cta]').forEach(function (a) {
    if (utm.length && /\/enroll$/.test(a.getAttribute('href') || '')) a.setAttribute('href', a.getAttribute('href') + '?' + utm.join('&'));
    a.addEventListener('click', function () { send('CTA', { label: a.getAttribute('data-cta') }); });
  });

  // lite video: the player (and its cookies / scripts) loads only when asked for
  d.querySelectorAll('.lp-video[data-video] .lp-video__btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var box = btn.parentNode;
      var f = d.createElement('iframe');
      f.src = box.getAttribute('data-video');
      f.title = btn.getAttribute('aria-label') || 'Video';
      f.allow = 'autoplay; fullscreen; picture-in-picture; encrypted-media';
      f.allowFullscreen = true;
      box.replaceChild(f, btn);
      f.focus();
      send('CTA', { label: 'video' });
    });
  });

  // gallery lightbox
  var box = d.getElementById('lp-lightbox');
  if (box && box.showModal) {
    var img = box.querySelector('img');
    var cap = box.querySelector('p');
    var opener = null;
    d.querySelectorAll('.lp-gallery__item').forEach(function (b) {
      b.addEventListener('click', function () {
        opener = b;
        img.src = b.getAttribute('data-full');
        img.alt = (b.querySelector('img') || {}).alt || '';
        cap.textContent = b.getAttribute('data-caption') || '';
        box.showModal();
      });
    });
    box.querySelector('.lp-lightbox__x').addEventListener('click', function () { box.close(); });
    box.addEventListener('click', function (e) { if (e.target === box) box.close(); });
    box.addEventListener('close', function () { if (opener) opener.focus(); });
  }

  // before / after
  d.querySelectorAll('.lp-compare input').forEach(function (r) {
    r.addEventListener('input', function () { r.parentNode.style.setProperty('--pos', r.value + '%'); });
  });

  // sticky enrol bar: shown once the hero's buttons scroll away, hidden while the price card is on screen
  var sticky = d.getElementById('lp-sticky');
  var heroCtas = d.querySelector('.lp-hero .lp-ctas');
  var priceCard = d.querySelector('.lp-pricecard');
  if (sticky && heroCtas && 'IntersectionObserver' in window) {
    sticky.hidden = false;
    var heroGone = false;
    var priceOn = false;
    var update = function () {
      var on = heroGone && !priceOn;
      sticky.classList.toggle('is-on', on);
      body.classList.toggle('has-sticky', on);
      sticky.setAttribute('aria-hidden', on ? 'false' : 'true');
      sticky.querySelector('a').tabIndex = on ? 0 : -1;
    };
    new IntersectionObserver(function (es) { heroGone = !es[0].isIntersecting && es[0].boundingClientRect.top < 0; update(); }).observe(heroCtas);
    if (priceCard) new IntersectionObserver(function (es) { priceOn = es[0].isIntersecting; update(); }, { threshold: 0.2 }).observe(priceCard);
    update();
  }

  // optional lead capture: the site newsletter, with explicit consent, tagged with this page
  var lead = d.getElementById('lp-lead');
  if (lead) {
    lead.addEventListener('submit', function (e) {
      e.preventDefault();
      var msg = lead.querySelector('.lp-lead__msg');
      var email = lead.email.value.trim();
      var say = function (t, bad) { msg.textContent = t; msg.classList.toggle('is-bad', !!bad); };
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { say('Please enter a valid e-mail address.', true); lead.email.focus(); return; }
      if (!lead.consent.checked) { say('Please tick the box to agree to the newsletter.', true); return; }
      if (preview) { say('Preview: nothing was sent.'); return; }
      var btn = lead.querySelector('button'); btn.disabled = true;
      fetch('/api/newsletter', { method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf(), 'X-Requested-With': 'XMLHttpRequest', Accept: 'application/json' },
        body: JSON.stringify({ email: email, website: lead.website.value, source: 'landing:' + pageId }) })
        .then(function (r) { return r.json().then(function (j) { return { ok: r.ok, j: j }; }); })
        .then(function (x) {
          if (x.ok && x.j.message === 'success') { say('Thank you, you are subscribed.'); lead.reset(); send('LEAD'); }
          else say((x.j.error && x.j.error.email && x.j.error.email[0]) || x.j.message || 'That did not work. Please try again.', true);
        })
        .catch(function () { say('That did not work. Please check your connection and try again.', true); })
        .then(function () { btn.disabled = false; });
    });
  }

  // marketing tags, only with consent
  var ga4 = body.getAttribute('data-ga4');
  var gtm = body.getAttribute('data-gtm');
  var pixel = body.getAttribute('data-pixel');
  function consent() { var m = d.cookie.match(/(?:^|; )ac_consent=([^;]*)/); return m ? decodeURIComponent(m[1]) : null; }
  function script(src) { var s = d.createElement('script'); s.async = true; s.src = src; d.head.appendChild(s); }
  function loadTags() {
    if (ga4) {
      window.dataLayer = window.dataLayer || [];
      window.gtag = function () { window.dataLayer.push(arguments); };
      window.gtag('js', new Date());
      window.gtag('config', ga4, { anonymize_ip: true });
      script('https://www.googletagmanager.com/gtag/js?id=' + encodeURIComponent(ga4));
    }
    if (gtm) {
      window.dataLayer = window.dataLayer || [];
      window.dataLayer.push({ 'gtm.start': Date.now(), event: 'gtm.js' });
      script('https://www.googletagmanager.com/gtm.js?id=' + encodeURIComponent(gtm));
    }
    if (pixel) {
      var n = window.fbq = function () { n.callMethod ? n.callMethod.apply(n, arguments) : n.queue.push(arguments); };
      n.push = n; n.loaded = true; n.version = '2.0'; n.queue = [];
      script('https://connect.facebook.net/en_US/fbevents.js');
      window.fbq('init', pixel);
      window.fbq('track', 'PageView');
    }
  }
  if (!preview && (ga4 || gtm || pixel)) {
    var c = consent();
    if (c === 'all') loadTags();
    else if (!c) {
      var bar = d.createElement('div');
      bar.className = 'lp-consent';
      bar.setAttribute('role', 'region');
      bar.setAttribute('aria-label', 'Cookies');
      bar.innerHTML = '<p>We use cookies to understand which pages help people find our courses. <a href="/privacy-policy">Privacy policy</a></p>'
        + '<div><button type="button" data-v="essential">Essential only</button><button type="button" data-v="all">Accept all</button></div>';
      body.appendChild(bar);
      bar.addEventListener('click', function (e) {
        var v = e.target.getAttribute && e.target.getAttribute('data-v');
        if (!v) return;
        d.cookie = 'ac_consent=' + v + '; path=/; max-age=' + (180 * 86400) + '; SameSite=Lax' + (location.protocol === 'https:' ? '; Secure' : '');
        bar.remove();
        if (v === 'all') loadTags();
      });
    }
  }
})();
