/* ArtistikCity landing pages: the checkout runs inside the page (a dialog), so a visitor never leaves it.
   Steps: 1. quick sign-in (Google one-tap / button, a six-digit code by e-mail or SMS, or a passkey) - skipped when
   already signed in; 2. details and payment (the page's own payment options; Razorpay opens its own window on top of
   the page); 3. the result. The server decides prices, coupons and which payment options exist
   (GET /landing/checkout/{page}/state); this file only collects input and shows answers. No framework, no build step. */
(function () {
  'use strict';
  var d = document;
  var body = d.body;
  var pageId = body.getAttribute('data-page');
  var slug = body.getAttribute('data-slug');
  var dlg = d.getElementById('lp-co');
  if (!dlg || !pageId || !slug) return;
  var preview = body.hasAttribute('data-preview');
  var main = d.getElementById('lp-co-body');
  var title = d.getElementById('lp-co-h');
  var state = null;           // last /state answer
  var orderId = null;         // the open order
  var coupon = '';
  var method = null;
  var form = null;            // details typed so far (kept when going back and forth)
  var timers = [];
  var opener = null;
  var signinOnly = false;

  // ------------------------------------------------------------------ helpers

  function token() {
    var m = d.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    if (m) return decodeURIComponent(m[1]);
    var meta = d.querySelector('meta[name="csrf-token"]');
    return meta ? meta.getAttribute('content') : '';
  }

  function api(method_, url, data) {
    return fetch(url, {
      method: method_, credentials: 'same-origin', cache: 'no-store',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest', 'X-CSRF-TOKEN': token() },
      body: data === undefined ? undefined : JSON.stringify(data)
    }).then(function (r) {
      return r.text().then(function (t) {
        var j = null;
        try { j = t ? JSON.parse(t) : {}; } catch (e) { j = {}; }
        if (r.ok) return j;
        var err = new Error((j && j.message) || (r.status === 419 ? 'Your session expired. Please refresh the page and try again.' : 'Something went wrong. Please try again.'));
        err.status = r.status;
        err.data = j || {};
        var fe = {};
        var es = (j && j.errors) || {};
        for (var k in es) fe[k] = Array.isArray(es[k]) ? es[k][0] : es[k];
        err.fields = fe;
        throw err;
      });
    });
  }
  var post = function (url, data) { return api('POST', url, data || {}); };

  function el(tag, attrs, kids) {
    var n = d.createElement(tag);
    for (var k in attrs || {}) {
      var v = attrs[k];
      if (v === null || v === undefined || v === false) continue;
      if (k === 'text') n.textContent = v;
      else if (k === 'html') n.innerHTML = v;
      else if (k.slice(0, 2) === 'on') n.addEventListener(k.slice(2), v);
      else if (k === 'class') n.className = v;
      else n.setAttribute(k, v === true ? '' : v);
    }
    (kids || []).forEach(function (c) { if (c !== null && c !== undefined && c !== false) n.appendChild(typeof c === 'string' ? d.createTextNode(c) : c); });
    return n;
  }
  function icon(id) {
    var s = d.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('aria-hidden', 'true');
    var u = d.createElementNS('http://www.w3.org/2000/svg', 'use');
    u.setAttribute('href', '#' + id);
    s.appendChild(u);
    return s;
  }
  function clearTimers() { timers.forEach(function (t) { clearTimeout(t); clearInterval(t); }); timers = []; }
  function render(nodes, heading) {
    clearTimers();
    title.textContent = heading;
    main.innerHTML = '';
    nodes.forEach(function (n) { if (n) main.appendChild(n); });
    main.scrollTop = 0;
  }
  function note(tone, text) {
    return el('p', { class: 'lp-co__note is-' + tone, role: tone === 'error' ? 'alert' : 'status', text: text });
  }
  function field(id, label, input, error, hint) {
    input.id = id;
    if (error) { input.setAttribute('aria-invalid', 'true'); input.setAttribute('aria-describedby', id + '-e'); }
    return el('label', { class: 'lp-co__field', for: id }, [
      el('span', { text: label }), input,
      error ? el('small', { class: 'lp-co__err', id: id + '-e', text: error }) : null,
      hint && !error ? el('small', { class: 'lp-co__hint', text: hint }) : null
    ]);
  }
  function button(text, attrs) {
    var b = el('button', Object.assign({ type: 'button', class: 'lp-btn lp-btn--block' }, attrs || {}));
    b.textContent = text;
    return b;
  }
  function busy(btn, on, label) {
    if (!btn) return;
    btn.disabled = on;
    if (on) { btn.setAttribute('data-label', btn.textContent); btn.textContent = label || 'Please wait…'; btn.setAttribute('aria-busy', 'true'); }
    else { if (btn.getAttribute('data-label')) btn.textContent = btn.getAttribute('data-label'); btn.removeAttribute('aria-busy'); }
  }
  function track(event, extra) {
    if (preview) return;
    var data = { page: Number(pageId), event: event };
    for (var k in extra || {}) data[k] = extra[k];
    fetch('/landing/e', { method: 'POST', keepalive: true, credentials: 'same-origin', headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token() },
      body: JSON.stringify(data) }).catch(function () {});
  }
  function script(src) {
    return new Promise(function (resolve, reject) {
      var existing = d.querySelector('script[src="' + src + '"]');
      if (existing && existing.getAttribute('data-loaded')) return resolve();
      var s = existing || d.createElement('script');
      s.addEventListener('load', function () { s.setAttribute('data-loaded', '1'); resolve(); });
      s.addEventListener('error', function () { reject(new Error('Could not load ' + src)); });
      if (!existing) { s.src = src; s.async = true; d.head.appendChild(s); }
    });
  }

  // ------------------------------------------------------------------ order summary (shown on every step)

  function summary(compact) {
    var c = state.course, q = state.quote;
    var lines = el('dl', { class: 'lp-co__lines' });
    function line(dt, dd, cls) { lines.appendChild(el('div', { class: cls || '' }, [el('dt', { text: dt }), el('dd', { text: dd })])); }
    if (!compact) {
      if (q.listLabel) line('Course price', q.listLabel, 'is-strike');
      if (q.offerLabel) line(q.offerLabel, q.saving ? '−' + q.saving : '', 'is-good');
      if (q.coupon && Number(q.discount) > 0) line('Coupon ' + q.coupon, '−₹' + Number(q.discount).toLocaleString('en-IN'), 'is-good');
      if (q.taxMode === 'ADDED' && Number(q.tax) > 0) line(q.taxLabel, '₹' + Number(q.tax).toLocaleString('en-IN'));
    }
    line('Total', q.totalLabel, 'is-total');
    return el('div', { class: 'lp-co__sum' }, [
      el('div', { class: 'lp-co__course' }, [
        c.thumb ? el('img', { src: c.thumb, alt: '', width: 64, height: 64 }) : null,
        el('div', {}, [
          el('p', { class: 'lp-co__ctitle', text: c.title }),
          el('p', { class: 'lp-co__cby', text: [c.kind, c.artist ? 'with ' + c.artist : null].filter(Boolean).join(' · ') }),
          c.starts ? el('p', { class: 'lp-co__cby', text: 'Starts ' + c.starts + (c.duration ? ' · ' + c.duration : '') }) : null
        ])
      ]),
      lines,
      !compact && q.offerEndsLabel ? el('p', { class: 'lp-co__fine', text: 'Offer price until ' + q.offerEndsLabel + ' (IST).' }) : null
    ]);
  }

  // ------------------------------------------------------------------ step 1: quick sign-in

  var signin = { mode: 'email', email: '', name: '', phone: '', sent: null, profile: false, msg: null };

  function signedIn(r) {
    var u = r && r.user;
    var link = d.querySelector('.lp-top__end [data-signin]');
    if (link) { link.textContent = 'My studio'; link.setAttribute('href', '/dashboard'); link.removeAttribute('data-signin'); }
    if (signinOnly) {
      render([note('ok', 'You\'re signed in' + (u && u.name ? ' as ' + u.name : '') + '.'), button('Continue', { onclick: close })], 'Signed in');
      return;
    }
    load();
  }

  function stepSignin(message) {
    var o = state.signIn || {};
    var kids = [];
    if (!signinOnly) kids.push(summary(true));
    var pwOnly = o.password && !o.google && !o.email && !o.phone;
    kids.push(el('p', { class: 'lp-co__lead', text: pwOnly
      ? (signinOnly ? 'Log in, or create your account in a few seconds.' : 'First, tell us who is enrolling: log in, or create your account in a few seconds.')
      : (signinOnly ? 'Sign in with one tap or a code. No password needed.' : 'First, tell us who is enrolling. It takes a few seconds, no password needed.') }));
    if (message || signin.msg) kids.push(note((message || signin.msg).tone, (message || signin.msg).text));
    signin.msg = null;

    if (o.google) {
      var gbox = el('div', { class: 'lp-co__google', id: 'lp-co-google', 'data-testid': 'lp-co-google' });
      kids.push(gbox);
      google(o.google, gbox);
    }
    var codeChannels = [];
    if (o.email) codeChannels.push('email');
    if (o.phone) codeChannels.push('phone');
    if (o.password) codeChannels.push('password');
    if (codeChannels.length && codeChannels.indexOf(signin.mode) < 0) signin.mode = codeChannels[0];

    if (signin.sent) {
      kids.push(codeForm());
    } else if (signin.profile) {
      kids.push(profileForm());
    } else if (codeChannels.length) {
      if (o.google) kids.push(el('p', { class: 'lp-co__or' }, [el('span', { text: 'or' })]));
      kids.push(signin.mode === 'password' ? passwordForm(codeChannels) : startForm(codeChannels));
    }
    if (o.passkey && window.PublicKeyCredential && navigator.credentials && !signin.sent && !signin.profile) {
      kids.push(el('button', { type: 'button', class: 'lp-co__link', onclick: passkey, 'data-testid': 'lp-co-passkey' }, [icon('i-lock'), ' Sign in with a passkey']));
    }
    if (!o.google && !codeChannels.length && !o.passkey) {
      kids.push(note('info', 'Quick sign-in isn\'t set up on this server yet.'));
    }
    kids.push(el('p', { class: 'lp-co__fine' }, [
      'By continuing you agree to our ', el('a', { href: '/terms-conditions', target: '_blank', rel: 'noopener', text: 'terms' }), ' and ',
      el('a', { href: '/privacy-policy', target: '_blank', rel: 'noopener', text: 'privacy policy' }), '. ',
      el('a', { href: '/login?return_to=' + encodeURIComponent('/landing/' + slug + '/enroll'), text: 'Other ways to sign in' })
    ]));
    render(kids, signinOnly ? 'Sign in' : 'Enrol · step 1 of 2');
    var first = main.querySelector('input:not([type=hidden])');
    if (first && !o.google) first.focus();
  }

  function startForm(channels) {
    var isEmail = signin.mode === 'email';
    var input = isEmail
      ? el('input', { type: 'email', name: 'email', autocomplete: 'email', inputmode: 'email', required: true, placeholder: 'you@example.com', value: signin.email })
      : el('input', { type: 'tel', name: 'phone', autocomplete: 'tel', inputmode: 'tel', required: true, placeholder: '+91 98765 43210', value: signin.phone });
    var err = signin.err || {};
    signin.err = null;
    var btn = el('button', { type: 'submit', class: 'lp-btn lp-btn--block', 'data-testid': 'lp-co-send' }, [isEmail ? 'Email me a code' : 'Text me a code']);
    var f = el('form', { class: 'lp-co__form', novalidate: true, onsubmit: function (e) {
      e.preventDefault();
      if (isEmail) signin.email = input.value.trim(); else signin.phone = input.value.trim();
      busy(btn, true, 'Sending…');
      var req = isEmail ? post('/auth/email-code/send', { email: signin.email, name: signin.name }) : post('/auth/otp/send', { phone: signin.phone });
      req.then(function (r) {
        signin.sent = { channel: signin.mode, to: isEmail ? r.email : r.phone, masked: isEmail ? r.email : r.masked, resendIn: r.resendIn || 30, devCode: r.devCode };
        stepSignin();
      }).catch(function (e2) {
        busy(btn, false);
        signin.err = e2.fields;
        stepSignin({ tone: 'error', text: e2.message });
      });
    } }, [
      field(isEmail ? 'lp-co-email' : 'lp-co-phone', isEmail ? 'E-mail' : 'Mobile number', input, err.email || err.phone,
        isEmail ? 'We\'ll e-mail you a 6-digit code.' : 'We\'ll text you a 6-digit code.'),
      btn
    ]);
    switchLinks(f, channels);
    return f;
  }

  var MODE_LINK = { email: 'Use an e-mail code instead', phone: 'Use my mobile number instead', password: 'Use a password instead' };
  function switchLinks(f, channels) {
    channels.filter(function (c) { return c !== signin.mode; }).forEach(function (c) {
      f.appendChild(el('button', { type: 'button', class: 'lp-co__link', 'data-testid': 'lp-co-switch-' + c, onclick: function () {
        signin.mode = c;
        stepSignin();
      } }, [MODE_LINK[c]]));
    });
  }

  // e-mail and password: the site's own /login and /register, answered here (no setup needed on the server)
  function authForm(url, data) {
    return fetch(url, { method: 'POST', credentials: 'same-origin', redirect: 'manual',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest', 'X-CSRF-TOKEN': token() },
      body: JSON.stringify(data) }).then(function (r) {
      if (r.type === 'opaqueredirect' || r.ok || r.status === 409) return {};
      return r.json().catch(function () { return {}; }).then(function (j) {
        var err = new Error((j && j.message) || (r.status === 419 ? 'Your session expired. Please refresh the page and try again.' : 'Something went wrong. Please try again.'));
        var fe = {}, es = (j && j.errors) || {};
        for (var k in es) fe[k] = Array.isArray(es[k]) ? es[k][0] : es[k];
        err.fields = fe;
        throw err;
      });
    });
  }

  function passwordForm(channels) {
    var up = !!signin.signup;
    var err = signin.err || {};
    signin.err = null;
    var name = el('input', { type: 'text', autocomplete: 'name', required: true, value: signin.name, 'data-testid': 'lp-co-pname' });
    var email = el('input', { type: 'email', autocomplete: 'email', inputmode: 'email', required: true, placeholder: 'you@example.com', value: signin.email, 'data-testid': 'lp-co-pemail' });
    var pass = el('input', { type: 'password', autocomplete: up ? 'new-password' : 'current-password', required: true, minlength: up ? 8 : null, 'data-testid': 'lp-co-password' });
    var btn = el('button', { type: 'submit', class: 'lp-btn lp-btn--block', 'data-testid': 'lp-co-login' }, [up ? 'Create account & continue' : 'Log in & continue']);
    var f = el('form', { class: 'lp-co__form', novalidate: true, onsubmit: function (e) {
      e.preventDefault();
      signin.email = email.value.trim();
      if (up) signin.name = name.value.trim();
      busy(btn, true, up ? 'Creating your account…' : 'Logging in…');
      var req = up
        ? authForm('/register', { name: signin.name, email: signin.email, password: pass.value, password_confirmation: pass.value, remember: true })
        : authForm('/login', { email: signin.email, password: pass.value, remember: true });
      req.then(function () { signedIn({}); }).catch(function (e2) {
        signin.err = e2.fields;
        var msg = (e2.fields && (e2.fields.email || e2.fields.password || e2.fields.name)) || e2.message;
        stepSignin({ tone: 'error', text: String(msg).replace(/,? or (sign in with an e-mail link|use "Email me a sign-in link")/i, '') });
      });
    } }, [
      up ? field('lp-co-pwname', 'Your name', name, err.name) : null,
      field('lp-co-pwemail', 'E-mail', email, err.email),
      field('lp-co-pwpass', up ? 'Choose a password' : 'Password', pass, err.password, up ? 'At least 8 characters.' : null),
      btn,
      el('button', { type: 'button', class: 'lp-co__link', 'data-testid': 'lp-co-toggle-signup', onclick: function () { signin.signup = !up; stepSignin(); } },
        [up ? 'Already have an account? Log in' : 'New here? Create an account']),
      up ? null : el('a', { class: 'lp-co__link', href: '/forgot-password', target: '_blank', rel: 'noopener', text: 'Forgot your password?' })
    ]);
    switchLinks(f, channels);
    return f;
  }

  function codeForm() {
    var s = signin.sent;
    var input = el('input', { type: 'text', name: 'code', autocomplete: 'one-time-code', inputmode: 'numeric', pattern: '[0-9]*', maxlength: 6,
      class: 'lp-co__code', placeholder: '••••••', required: true, 'aria-label': '6-digit code', 'data-testid': 'lp-co-code' });
    var btn = el('button', { type: 'submit', class: 'lp-btn lp-btn--block', 'data-testid': 'lp-co-verify' }, ['Continue']);
    var err = el('small', { class: 'lp-co__err', role: 'alert' });
    var resend = el('button', { type: 'button', class: 'lp-co__link', disabled: true });
    var left = s.resendIn;
    function tick() {
      if (left > 0) { resend.textContent = 'Send a new code in ' + left + 's'; resend.disabled = true; left--; }
      else { resend.textContent = 'Send a new code'; resend.disabled = false; }
    }
    resend.addEventListener('click', function () { signin.sent = null; stepSignin(); main.querySelector('form').requestSubmit(); });
    function submit() {
      var code = input.value.replace(/\D/g, '');
      if (code.length !== 6) { err.textContent = 'Enter the 6 digits from the ' + (s.channel === 'email' ? 'e-mail' : 'text message') + '.'; return; }
      busy(btn, true, 'Checking…');
      var req = s.channel === 'email'
        ? post('/auth/email-code/verify', { email: s.to, code: code, name: signin.name, remember: true })
        : post('/auth/otp/verify', { phone: s.to, code: code, remember: true });
      req.then(function (r) {
        if (r.next === 'profile') { signin.sent = null; signin.profile = true; stepSignin(); return; }
        signin.sent = null;
        signedIn(r);
      }).catch(function (e) {
        busy(btn, false);
        err.textContent = (e.fields && e.fields.code) || e.message;
        input.select();
      });
    }
    input.addEventListener('input', function () {
      input.value = input.value.replace(/\D/g, '').slice(0, 6);
      err.textContent = '';
      if (input.value.length === 6) submit();
    });
    var f = el('form', { class: 'lp-co__form', novalidate: true, onsubmit: function (e) { e.preventDefault(); submit(); } }, [
      el('p', { class: 'lp-co__lead' }, ['We sent a 6-digit code to ', el('b', { text: s.masked }), '.']),
      s.devCode ? note('info', 'Development server: your code is ' + s.devCode + ' (shown only on this computer).') : null,
      input, err, btn,
      el('div', { class: 'lp-co__row' }, [
        el('button', { type: 'button', class: 'lp-co__link', onclick: function () { signin.sent = null; stepSignin(); } }, [s.channel === 'email' ? 'Change e-mail' : 'Change number']),
        resend
      ])
    ]);
    tick();
    timers.push(setInterval(tick, 1000));
    setTimeout(function () { input.focus(); }, 30);
    // Android Chrome: read the code from the SMS (WebOTP; the message ends with "@host #code")
    if (s.channel === 'phone' && 'OTPCredential' in window) {
      var ac = new AbortController();
      timers.push(setTimeout(function () { ac.abort(); }, 120000));
      navigator.credentials.get({ otp: { transport: ['sms'] }, signal: ac.signal }).then(function (otp) {
        if (otp && otp.code && d.body.contains(input)) { input.value = otp.code; submit(); }
      }).catch(function () {});
    }
    return f;
  }

  function profileForm() {
    var name = el('input', { type: 'text', autocomplete: 'name', required: true, value: signin.name });
    var email = el('input', { type: 'email', autocomplete: 'email', inputmode: 'email', placeholder: 'For receipts and course updates' });
    var btn = el('button', { type: 'submit', class: 'lp-btn lp-btn--block' }, ['Create my account']);
    var errs = signin.err || {};
    signin.err = null;
    return el('form', { class: 'lp-co__form', novalidate: true, onsubmit: function (e) {
      e.preventDefault();
      busy(btn, true);
      signin.name = name.value.trim();
      post('/auth/otp/complete', { name: signin.name, email: email.value.trim() }).then(function (r) {
        signin.profile = false;
        signedIn(r);
      }).catch(function (e2) {
        busy(btn, false);
        signin.err = e2.fields;
        if (e2.status === 410) signin.profile = false;
        stepSignin({ tone: 'error', text: e2.message });
      });
    } }, [
      el('p', { class: 'lp-co__lead', text: 'Your number is confirmed. Last step: your name.' }),
      field('lp-co-pname', 'Your name', name, errs.name),
      field('lp-co-pemail', 'E-mail (optional)', email, errs.email),
      btn
    ]);
  }

  var gisReady = null;
  function google(cfg, box) {
    box.appendChild(el('div', { class: 'lp-co__gwait', text: 'Loading Google sign-in…' }));
    gisReady = gisReady || script('https://accounts.google.com/gsi/client');
    gisReady.then(function () {
      if (!window.google || !window.google.accounts) throw new Error('no gis');
      window.google.accounts.id.initialize({
        client_id: cfg.clientId, nonce: cfg.nonce, auto_select: false, cancel_on_tap_outside: true, context: 'signin',
        itp_support: true, use_fedcm_for_prompt: true,
        callback: function (resp) {
          box.innerHTML = '';
          box.appendChild(el('div', { class: 'lp-co__gwait', text: 'Signing you in…' }));
          post('/auth/google/credential', { credential: resp.credential }).then(signedIn).catch(function (e) {
            reload({ tone: 'error', text: e.message });
          });
        }
      });
      box.innerHTML = '';
      window.google.accounts.id.renderButton(box, { type: 'standard', theme: 'outline', size: 'large', text: 'continue_with', shape: 'pill',
        logo_alignment: 'center', width: Math.min(400, Math.max(240, box.offsetWidth || 320)) });
      window.google.accounts.id.prompt();     // one-tap, when Google has a signed-in account in this browser
    }).catch(function () {
      box.innerHTML = '';
      box.hidden = true;                       // Google unreachable (blocked, offline): the code sign-in stays
      var or = box.nextElementSibling;
      if (or && or.classList.contains('lp-co__or')) or.hidden = true;
    });
  }

  // re-read the options (a Google nonce is single use), keeping a message
  function reload(message) {
    api('GET', '/landing/checkout/' + pageId + '/state').then(function (s) { state = s; stepSignin(message); }).catch(function () { stepSignin(message); });
  }

  function b64(buf) {
    var bytes = new Uint8Array(buf), bin = '';
    for (var i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
    return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }
  function unb64(s) {
    var b = atob(s.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((s.length + 3) % 4)), out = new Uint8Array(b.length);
    for (var i = 0; i < b.length; i++) out[i] = b.charCodeAt(i);
    return out.buffer;
  }
  function passkey() {
    post('/auth/passkey/options').then(function (o) {
      return navigator.credentials.get({ publicKey: { challenge: unb64(o.challenge), rpId: o.rpId, timeout: o.timeout, userVerification: o.userVerification, allowCredentials: [] } });
    }).then(function (cred) {
      var r = cred.response;
      return post('/auth/passkey/verify', { remember: true, credential: { id: cred.id, rawId: b64(cred.rawId), type: cred.type, response: {
        clientDataJSON: b64(r.clientDataJSON), authenticatorData: b64(r.authenticatorData), signature: b64(r.signature), userHandle: r.userHandle ? b64(r.userHandle) : null } } });
    }).then(signedIn).catch(function (e) {
      if (e && e.name === 'NotAllowedError') return;     // cancelled
      stepSignin({ tone: 'error', text: (e && e.message) || 'The passkey didn\'t work. Please use a code instead.' });
    });
  }

  // ------------------------------------------------------------------ step 2: details and payment

  function stepPay(message, errors) {
    var q = state.quote, ck = state.checkout || {}, u = state.user || {}, a = state.address || {};
    errors = errors || {};
    if (!form) {
      form = { name: u.name || '', phone: u.phone || '', address: a.address || '', city: a.city || '', state: a.state || '', zipcode: a.zipcode || '',
        country: a.country || 'India', terms: false, privacy: false };
    }
    if (!method && state.choices.length) method = state.choices[0].key;
    function input(key, attrs) {
      var i = el('input', Object.assign({ value: form[key] || '' }, attrs || {}));
      i.addEventListener('input', function () { form[key] = i.value; });
      return i;
    }
    var kids = [summary(false)];
    if (message) kids.push(note(message.tone, message.text));

    // coupon
    if (ck.showCoupon && q.couponsEnabled && !q.free) {
      var ci = el('input', { value: coupon, placeholder: 'Coupon code', autocomplete: 'off', maxlength: 40, 'aria-label': 'Coupon code', 'data-testid': 'lp-co-coupon' });
      var cmsg = el('small', { class: 'lp-co__hint', role: 'status' });
      var cb = el('button', { type: 'submit', class: 'lp-btn lp-btn--ghost lp-btn--sm' }, ['Apply']);
      kids.push(el('form', { class: 'lp-co__coupon', onsubmit: function (e) {
        e.preventDefault();
        coupon = ci.value.toUpperCase().replace(/\s/g, '');
        if (!coupon) return;
        busy(cb, true, '…');
        post('/landing/checkout/' + pageId + '/quote', { coupon: coupon }).then(function (r) {
          state.quote = r.quote;
          if (r.couponError) { coupon = ''; stepPay({ tone: 'error', text: r.couponError }); }
          else stepPay({ tone: 'ok', text: 'Code ' + r.quote.coupon + ' applied.' });
        }).catch(function (e2) { busy(cb, false); cmsg.textContent = e2.message; });
      } }, [ci, cb, cmsg]));
    }

    var who = el('div', { class: 'lp-co__who' }, [
      el('span', {}, ['Enrolling as ', el('b', { text: u.email || u.phone || u.name })]),
      el('button', { type: 'button', class: 'lp-co__link', onclick: switchAccount }, ['Not you?'])
    ]);
    var fields = el('div', { class: 'lp-co__fields' }, [
      field('lp-co-fname', 'Full name', input('name', { autocomplete: 'name', required: true }), errors.name),
      field('lp-co-fphone', 'Phone' + (ck.requirePhone ? '' : ' (optional)'), input('phone', { type: 'tel', autocomplete: 'tel', inputmode: 'tel', placeholder: '+91 98765 43210' }), errors.phone)
    ]);
    if (ck.requireAddress) {
      fields.appendChild(field('lp-co-faddress', 'Address', input('address', { autocomplete: 'street-address' }), errors.address));
      fields.appendChild(field('lp-co-fcity', 'City', input('city', { autocomplete: 'address-level2' }), errors.city));
      fields.appendChild(field('lp-co-fzip', 'PIN / postal code', input('zipcode', { autocomplete: 'postal-code' }), errors.zipcode));
      fields.appendChild(field('lp-co-fstate', 'State (optional)', input('state', { autocomplete: 'address-level1' })));
      fields.appendChild(field('lp-co-fcountry', 'Country', input('country', { autocomplete: 'country-name' })));
    }
    var payForm = el('form', { class: 'lp-co__form', novalidate: true }, [who, fields]);

    if (!q.free) {
      var methods = el('div', { class: 'lp-co__methods', role: 'radiogroup', 'aria-label': 'Payment method' });
      state.choices.forEach(function (c) {
        var r = el('input', { type: 'radio', name: 'lp-co-method', value: c.key, checked: method === c.key });
        r.addEventListener('change', function () { method = c.key; track('METHOD', { method: c.key }); refreshPay(); });
        methods.appendChild(el('label', { class: 'lp-co__method', 'data-method': c.key }, [
          r, el('span', { class: 'lp-co__mtext' }, [el('b', { text: c.label }), el('small', { text: c.hint || '' })]), el('b', { class: 'lp-co__mamt', text: c.amount })
        ]));
      });
      payForm.appendChild(el('fieldset', { class: 'lp-co__set' }, [el('legend', { text: 'How would you like to pay?' }), methods]));
      if (!state.choices.length && !state.simulate) payForm.appendChild(note('error', 'Online payment isn\'t available for this course right now. Please contact us and we\'ll help you enrol.'));
      if (state.paymentsClosed) payForm.appendChild(note('info', 'Online payment for this course opens soon. Please check back, or contact us and we\'ll help you enrol.'));
      else if (state.razorpaySimulated) payForm.appendChild(el('p', { class: 'lp-co__fine', 'data-testid': 'lp-co-simnote', text: 'Test mode: Razorpay is simulated on this site. You are a test buyer, so no real payment is made.' }));
      if (state.testMode) payForm.appendChild(el('p', { class: 'lp-co__fine', text: 'Test mode: no real payment is taken on this server.' }));
    }
    function check(key, id, label) {
      var c = el('input', { type: 'checkbox', id: id, checked: form[key], 'data-testid': id });
      c.addEventListener('change', function () { form[key] = c.checked; });
      return el('label', { class: 'lp-co__check', for: id }, [c, el('span', { html: label })]);
    }
    if (ck.requireTerms) payForm.appendChild(check('terms', 'lp-co-terms', 'I accept the <a href="/terms-conditions" target="_blank" rel="noopener">terms of use</a>' +
      (ck.requirePrivacy ? '' : ' and the <a href="/privacy-policy" target="_blank" rel="noopener">privacy policy</a>') + '.'));
    if (errors.terms) payForm.appendChild(el('small', { class: 'lp-co__err', text: errors.terms }));
    if (ck.requirePrivacy) payForm.appendChild(check('privacy', 'lp-co-privacy', 'I agree that ArtistikCity uses my details to run this course, as set out in the <a href="/privacy-policy" target="_blank" rel="noopener">privacy policy</a>.'));
    if (errors.privacy) payForm.appendChild(el('small', { class: 'lp-co__err', text: errors.privacy }));

    var payBtn = el('button', { type: 'submit', class: 'lp-btn lp-btn--lg lp-btn--block', 'data-testid': 'lp-co-pay' });
    function refreshPay() {
      var c = state.choices.filter(function (x) { return x.key === method; })[0];
      payBtn.innerHTML = '';
      payBtn.appendChild(icon('i-lock'));
      payBtn.appendChild(d.createTextNode(q.free ? ' Enrol for free' : ' Pay ' + (c ? c.amount : q.totalLabel)));
      payBtn.disabled = !(q.free || c) || (!!state.paymentsClosed && !q.free);
    }
    refreshPay();
    payForm.appendChild(payBtn);
    if (state.simulate && !q.free && !state.razorpaySimulated) {     // with simulated Razorpay, Pay already opens the simulated window
      payForm.appendChild(button('Simulate payment (test buyer)', { class: 'lp-btn lp-btn--ghost lp-btn--block', 'data-testid': 'lp-co-simulate', onclick: function () { pay('test', this); } }));
    }
    payForm.appendChild(el('p', { class: 'lp-co__fine lp-co__center' }, [icon('i-lock'), ' Encrypted payment by our payment partners. We never see your card or UPI PIN.']));
    payForm.addEventListener('submit', function (e) { e.preventDefault(); pay(q.free ? 'free' : method, payBtn); });
    kids.push(payForm);
    render(kids, 'Enrol · step 2 of 2');
    var firstErr = main.querySelector('[aria-invalid=true]');
    if (firstErr) firstErr.focus();
  }

  function switchAccount() {
    post('/logout').catch(function () {}).then(function () {
      form = null; orderId = null;
      var link = d.querySelector('.lp-top__end a[href="/dashboard"]');
      if (link) { link.textContent = 'Log in'; link.setAttribute('href', '/login'); link.setAttribute('data-signin', ''); }
      load();
    });
  }

  function pay(choice, btn) {
    busy(btn, true, 'Opening payment…');
    var data = { name: form.name, phone: form.phone, address: form.address, city: form.city, state: form.state, zipcode: form.zipcode,
      country: form.country, terms: form.terms, privacy: form.privacy, coupon: state.quote.coupon || '' };
    post('/landing/checkout/' + pageId + '/order', data).then(function (o) {
      orderId = o.orderId;
      state.quote = o.quote;
      return post('/landing/checkout/' + pageId + '/pay', { orderId: orderId, method: choice });
    }).then(function (r) {
      if (r.gateway === 'done') return showResult(orderId);
      if (r.gateway === 'paypal') { window.location.assign(r.approveUrl); return null; }    // PayPal's page, then back here
      if (r.gateway === 'test') return stepTest(r);
      if (r.gateway === 'razorpay-sim') { busy(btn, false); return razorpaySim(r); }
      return razorpay(r, btn);
    }).catch(function (e) {
      busy(btn, false);
      if (e.data && e.data.enrolled) return load();
      if (e.data && e.data.repriced && e.data.quote) state.quote = e.data.quote;
      if (e.data && e.data.code === 'SIGN_IN' || e.status === 401) { state.user = null; return load(); }
      stepPay({ tone: 'error', text: e.message }, e.fields || {});
    });
  }

  function razorpay(r, btn) {
    return script('https://checkout.razorpay.com/v1/checkout.js').then(function () {
      var rzp = new window.Razorpay({
        key: r.key, amount: r.amount, currency: r.currency, order_id: r.razorpayOrderId, name: r.name, description: r.description,
        prefill: r.prefill || {}, theme: { color: getComputedStyle(body).getPropertyValue('--accent').trim() || '#9c0d48' }, config: r.config,
        handler: function (resp) {
          render([note('info', 'Confirming your payment…')], 'Payment');
          var v = { orderId: orderId };
          for (var k in resp) v[k] = resp[k];
          post('/checkout/razorpay/verify', v).then(function () { showResult(orderId); }).catch(function (e) { stepPay({ tone: 'error', text: e.message }); });
        },
        modal: { ondismiss: function () {
          post('/landing/checkout/' + pageId + '/failed', { orderId: orderId, reason: 'closed' }).catch(function () {});
          busy(btn, false);
          stepPay({ tone: 'info', text: 'Payment window closed. No money was taken; you can try again any time.' });
        } }
      });
      rzp.on('payment.failed', function (resp) {
        post('/landing/checkout/' + pageId + '/failed', { orderId: orderId, reason: 'declined' }).catch(function () {});
        stepPay({ tone: 'error', text: (resp && resp.error && resp.error.description) || 'The payment failed. No money was taken. Please try again or choose another method.' });
      });
      rzp.open();
    }).catch(function () {
      busy(btn, false);
      stepPay({ tone: 'error', text: 'Could not load the payment window. Check your connection and try again.' });
    });
  }

  // Simulated Razorpay (Admin › Landing › Payments & checkout › Simulate Razorpay; no keys on the server): a window that
  // works like Razorpay's test mode - the chosen method, then the bank's Success / Failure page - but nothing leaves the site.
  function razorpaySim(r) {
    var old = d.getElementById('lp-rzs');
    if (old) old.remove();
    var box = el('div', { class: 'lp-rzs', id: 'lp-rzs', role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': 'lp-rzs-h', 'data-testid': 'lp-rzs' });
    var panel = el('div', { class: 'lp-rzs__panel' });
    function closeSim(msg) {
      box.remove();
      if (msg) stepPay(msg);
    }
    var head = el('div', { class: 'lp-rzs__head' }, [
      el('div', { class: 'lp-rzs__brand' }, [el('b', { id: 'lp-rzs-h', text: r.name }), el('small', { text: r.description })]),
      el('div', { class: 'lp-rzs__amt' }, [el('small', { text: 'Amount' }), el('b', { text: r.amountLabel })]),
      el('button', { type: 'button', class: 'lp-rzs__x', 'aria-label': 'Close the payment window', onclick: function () {
        post('/landing/checkout/' + pageId + '/failed', { orderId: orderId, reason: 'closed' }).catch(function () {});
        closeSim({ tone: 'info', text: 'Payment window closed. No money was taken; you can try again any time.' });
      } }, [icon('i-close')])
    ]);
    var ribbon = el('p', { class: 'lp-rzs__ribbon', text: 'Test mode · Razorpay is simulated. No real payment is made.' });
    var body2 = el('div', { class: 'lp-rzs__body' });
    function methodForm() {
      body2.innerHTML = '';
      var m = r.method, inputs;
      if (m === 'upi' || m === 'upi_qr') {
        inputs = [el('label', { class: 'lp-co__field' }, [el('span', { text: 'UPI ID' }), el('input', { value: 'success@razorpay', 'aria-label': 'UPI ID' })])];
      } else if (m === 'card' || m === 'emi') {
        inputs = [
          el('label', { class: 'lp-co__field' }, [el('span', { text: 'Card number' }), el('input', { value: '4111 1111 1111 1111', inputmode: 'numeric', 'aria-label': 'Card number' })]),
          el('div', { class: 'lp-rzs__row' }, [
            el('label', { class: 'lp-co__field' }, [el('span', { text: 'Expiry' }), el('input', { value: '12 / 30', 'aria-label': 'Expiry' })]),
            el('label', { class: 'lp-co__field' }, [el('span', { text: 'CVV' }), el('input', { value: '123', inputmode: 'numeric', 'aria-label': 'CVV' })])
          ])
        ];
      } else if (m === 'netbanking') {
        var sel = el('select', { 'aria-label': 'Bank' });
        ['State Bank of India', 'HDFC Bank', 'ICICI Bank', 'Axis Bank', 'Kotak Mahindra Bank'].forEach(function (b) { sel.appendChild(el('option', { text: b })); });
        inputs = [el('label', { class: 'lp-co__field' }, [el('span', { text: 'Bank' }), sel])];
      } else {
        inputs = [el('p', { class: 'lp-co__lead', text: r.methodLabel })];
      }
      var pay = button('Pay ' + r.amountLabel, { 'data-testid': 'lp-rzs-pay' });
      pay.addEventListener('click', bankPage);
      [el('p', { class: 'lp-rzs__method', text: r.methodLabel }), ribbon].concat(inputs).concat([pay]).forEach(function (n) { body2.appendChild(n); });
      setTimeout(function () { pay.focus(); }, 30);
    }
    function bankPage() {
      body2.innerHTML = '';
      var ok = button('Success', { class: 'lp-btn lp-btn--block lp-rzs__ok', 'data-testid': 'lp-rzs-success' });
      var no = button('Failure', { class: 'lp-btn lp-btn--ghost lp-btn--block', 'data-testid': 'lp-rzs-failure' });
      function finish(outcome, b) {
        busy(b, true, outcome === 'success' ? 'Confirming…' : 'Declining…');
        post('/landing/checkout/' + pageId + '/simulate', { orderId: orderId, outcome: outcome }).then(function () {
          closeSim();
          showResult(orderId);
        }).catch(function (e) { closeSim({ tone: 'error', text: e.message }); });
      }
      ok.addEventListener('click', function () { finish('success', ok); });
      no.addEventListener('click', function () { finish('failed', no); });
      [el('p', { class: 'lp-rzs__method', text: 'Bank page (simulated)' }),
        el('p', { class: 'lp-co__lead', text: 'In Razorpay\'s test mode the bank asks you to choose what happens. Choose the outcome to test.' }),
        el('p', { class: 'lp-co__fine', text: 'Order ' + r.razorpayOrderId + ' · ' + r.amountLabel }), ok, no].forEach(function (n) { body2.appendChild(n); });
      setTimeout(function () { ok.focus(); }, 30);
    }
    panel.appendChild(head);
    panel.appendChild(body2);
    panel.appendChild(el('p', { class: 'lp-rzs__foot' }, [icon('i-lock'), ' Secured by Razorpay · simulated']));
    box.appendChild(panel);
    box.addEventListener('keydown', function (e) { if (e.key === 'Escape') { e.stopPropagation(); e.preventDefault(); head.querySelector('.lp-rzs__x').click(); } });
    dlg.appendChild(box);
    methodForm();
  }

  function stepTest(r) {
    var done = button('Complete test payment', { 'data-testid': 'lp-co-test-complete' });
    var fail = button('Make it fail', { class: 'lp-btn lp-btn--ghost lp-btn--block', 'data-testid': 'lp-co-test-fail' });
    done.addEventListener('click', function () {
      busy(done, true);
      post('/checkout/test-pay', { orderId: orderId }).then(function () { showResult(orderId); }).catch(function (e) { stepPay({ tone: 'error', text: e.message }); });
    });
    fail.addEventListener('click', function () {
      post('/landing/checkout/' + pageId + '/failed', { orderId: orderId, reason: 'declined' }).catch(function () {}).then(function () {
        showResult(orderId);
      });
    });
    render([summary(true), el('div', { class: 'lp-co__test', 'data-testid': 'lp-co-test' }, [
      el('p', {}, [el('b', { text: 'Test payment' }), ' · ' + (r.simulated ? 'You are a test buyer: this payment is simulated and the order is marked as a test.'
        : 'No gateway keys on this server: complete a simulated payment.')]),
      done, fail
    ])], 'Test payment');
  }

  // ------------------------------------------------------------------ step 3: the result

  function showResult(oid) {
    render([note('info', 'Confirming…')], 'Your enrolment');
    return api('GET', '/landing/checkout/' + pageId + '/state?order=' + oid).then(function (s) {
      state = s;
      stepResult(s.result, 0);
    });
  }

  function stepResult(r, tries) {
    if (!r) { stepPay(); return; }
    var ck = state.checkout || {};
    var kids = [];
    var ok = r.state === 'success';
    var next = ck.successRedirect || r.classroom;
    var box = el('div', { class: 'lp-co__result is-' + r.state, 'data-testid': 'lp-co-result', 'data-state': r.state, role: 'status' });
    function add(h, p) { box.appendChild(el('h3', { text: h })); if (p) box.appendChild(el('p', { text: p })); }
    if (ok && !r.duplicate && !r.soldout) {
      add('You\'re in!', ck.successMessage || 'Your payment is confirmed and your seat is booked. We have e-mailed your receipt and joining details.');
      box.appendChild(el('p', { class: 'lp-co__fine', text: 'Order ' + r.number + ' · ' + r.amount }));
      if (next) box.appendChild(el('a', { class: 'lp-btn lp-btn--block', href: next, 'data-testid': 'lp-co-next', text: ck.successRedirect ? 'Continue' : 'Start learning' }));
      box.appendChild(el('a', { class: 'lp-btn lp-btn--ghost lp-btn--block', href: '/dashboard', text: 'Open my studio' }));
    } else if (ok && r.duplicate) {
      add('You were already enrolled', 'This payment will be refunded in full. Nothing else to do.');
    } else if (ok && r.soldout) {
      add('The course filled up', 'Someone took the last seat while you were paying. Your payment will be refunded in full.');
    } else if (r.state === 'refund') {
      add('A refund is on its way', 'You\'ll get an e-mail when it\'s done.');
    } else if (r.state === 'pending' || r.state === 'open') {
      add('Confirming your payment…', ck.pendingMessage || 'Some banks take a minute to confirm. You can keep this open; we will also e-mail you as soon as it is confirmed.');
      if (tries < 30) {
        timers.push(setTimeout(function () {
          api('GET', '/landing/checkout/order/' + r.orderId + '/status').then(function (x) { stepResult(x, tries + 1); }).catch(function () { stepResult(r, tries + 1); });
        }, 4000));
      } else {
        box.appendChild(el('p', { class: 'lp-co__fine', text: 'Still waiting. If money left your account, it will either be confirmed or refunded automatically. Order ' + r.number + '.' }));
      }
    } else if (r.state === 'failed') {
      add('The payment didn\'t go through', ck.failureMessage || 'No money was taken. You can try again, perhaps with another payment method.');
      box.appendChild(button('Try again', { 'data-testid': 'lp-co-retry', onclick: function () { load(); } }));
    } else if (r.state === 'refunded') {
      add('This order was refunded', 'Order ' + r.number + '.');
    }
    kids.push(box);
    render(kids, ok ? 'Enrolled' : 'Your enrolment');
    var r1 = d.querySelector('[data-testid=lp-co-next]');
    if (r1) r1.focus();
  }

  // ------------------------------------------------------------------ open / close

  function load(message) {
    render([el('div', { class: 'lp-co__wait', 'aria-busy': 'true' }, [el('span', { class: 'lp-co__spin' }), 'Loading…'])], signinOnly ? 'Sign in' : 'Enrol');
    var params = new URLSearchParams(location.search);
    var order = params.get('order');
    return api('GET', '/landing/checkout/' + pageId + '/state' + (order ? '?order=' + encodeURIComponent(order) : '')).then(function (s) {
      state = s;
      if (order) {
        params.delete('order');
        history.replaceState(history.state, '', location.pathname + (params.toString() ? '?' + params : '') + location.hash);
        if (s.result) { stepResult(s.result, 0); return; }
      }
      var notice = params.get('payment');
      if (notice) {
        params.delete('payment');
        history.replaceState(history.state, '', location.pathname + (params.toString() ? '?' + params : '') + location.hash);
        message = message || (notice === 'cancelled' ? { tone: 'info', text: 'Payment cancelled. No money was taken.' } : { tone: 'error', text: 'The payment did not complete. No money was taken; please try again.' });
      }
      if (!s.user) { stepSignin(message); return; }
      if (signinOnly) { signedIn({ user: s.user }); return; }
      if (s.enrolled) {
        render([summary(true), el('div', { class: 'lp-co__result is-success', 'data-testid': 'lp-co-result', 'data-state': 'enrolled' }, [
          el('h3', { text: 'You\'re already enrolled' }),
          el('p', { text: 'Pick up where you left off.' }),
          el('a', { class: 'lp-btn lp-btn--block', href: s.enrolled.classroom, text: 'Go to your course' })
        ])], 'Enrolled');
        return;
      }
      stepPay(message);
    }).catch(function (e) {
      render([note('error', e.status === 410 ? 'This page is no longer taking enrolments.' : e.message), button('Try again', { onclick: function () { load(); } })], 'Enrol');
    });
  }

  function open(e, onlySignin) {
    if (e) e.preventDefault();
    opener = d.activeElement;
    signinOnly = !!onlySignin;
    if (preview) {
      render([note('info', 'This is a preview. The checkout opens here on the published page.')], 'Enrol');
    } else {
      load();
    }
    if (!dlg.open) { if (dlg.showModal) dlg.showModal(); else dlg.setAttribute('open', ''); }
    body.classList.add('lp-co-open');
    if (!signinOnly && location.hash !== '#enroll') history.replaceState(history.state, '', location.pathname + location.search + '#enroll');
  }

  function close() {
    clearTimers();
    if (dlg.open) { if (dlg.close) dlg.close(); else dlg.removeAttribute('open'); }
  }
  dlg.addEventListener('close', function () {
    clearTimers();
    body.classList.remove('lp-co-open');
    if (location.hash === '#enroll') history.replaceState(history.state, '', location.pathname + location.search);
    if (window.google && window.google.accounts) { try { window.google.accounts.id.cancel(); } catch (x) { /* ignore */ } }
    if (opener && opener.focus) opener.focus();
  });
  dlg.querySelector('[data-close]').addEventListener('click', close);
  dlg.addEventListener('click', function (e) { if (e.target === dlg) close(); });

  // every Enrol button of this page opens the checkout here (its href still works without JavaScript)
  d.addEventListener('click', function (e) {
    var a = e.target.closest && e.target.closest('a[href]');
    if (!a || e.defaultPrevented || e.button || e.metaKey || e.ctrlKey || e.shiftKey) return;
    var href = a.getAttribute('href') || '';
    if (/^\/landing\/[^/]+\/enroll(\?|$)/.test(href) && href.indexOf('/landing/' + slug + '/') === 0) open(e, false);
    else if (a.hasAttribute('data-signin')) open(e, true);
  });
  if (location.hash === '#enroll' || new URLSearchParams(location.search).get('order')) open(null, false);
  window.addEventListener('hashchange', function () { if (location.hash === '#enroll' && !dlg.open) open(null, false); });
})();
