/* Home page: "the same trip, two drivers" (scroll-craft, split stage grammar).
   Renders from window.SITE (content.js) and window.KIT_UI (ui.js); the drive
   comes from window.EcoDrive (drive.js). The scroll-craft engine
   (scrollcraft.js, copied unchanged, MIT) pins the act and publishes its
   progress; everything specific to this page is here.

   The act: one pinned stage, the commute strip on top, the usual driver on
   the left (top on phones), the EcoDrive driver on the right (bottom). Scroll
   moves both along the same 25 km. The divider carries the kilometre and the
   fuel gap, which grows as they drive (signature). On arrival the divider
   slides to the edge and the EcoDrive side takes the screen with the result
   and the download. No framework, no build. */
(function () {
  'use strict';
  var S = window.SITE, D = window.EcoDrive, $ = function (s, r) { return (r || document).querySelector(s); };
  var LANGS = Object.keys(S.i18n);
  var saved = null; try { saved = localStorage.getItem('lang'); } catch (e) {}
  var q = new URLSearchParams(location.search).get('lang');
  var nav = (navigator.language || 'en').slice(0, 2);
  var lang = [q, saved, nav].find(function (l) { return LANGS.indexOf(l) > -1; }) || 'en';
  var reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;

  var usual = D.run('usual'), eco = D.run('eco');
  var A = S.assumptions;

  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  function num(n, d) { return n.toLocaleString(lang === 'fr' ? 'fr-FR' : 'en-GB', { minimumFractionDigits: d, maximumFractionDigits: d }); }
  function clock(s) { var m = Math.floor(s / 60), r = Math.round(s % 60); if (r === 60) { m++; r = 0; } return m + ':' + (r < 10 ? '0' : '') + r; }
  function duration(s) { var m = Math.floor(s / 60), r = Math.round(s % 60); return lang === 'fr' ? (m ? m + ' min ' : '') + (r < 10 && m ? '0' : '') + r + ' s' : (m ? m + ' min ' : '') + r + ' s'; }
  function fill(str, vals) { return str.replace(/\{(\w+)\}/g, function (_, k) { return vals[k]; }); }

  // The app's messages are French; the English page translates them.
  function hintText(fr) {
    if (lang === 'fr') return fr;
    var m;
    if ((m = /^Levez le pied, limitation à (\d+) dans (\d+) m$/.exec(fr))) return 'Lift off, ' + m[1] + ' km/h limit in ' + m[2] + ' m';
    if ((m = /^Levez le pied, (feu|stop|rond-point) dans (\d+) m$/.exec(fr))) return 'Lift off, ' + { feu: 'traffic light', stop: 'stop sign', 'rond-point': 'roundabout' }[m[1]] + ' in ' + m[2] + ' m';
    if ((m = /^Limitée à (\d+) : /.exec(fr))) return 'Limit ' + m[1] + ': lift off, every extra km/h costs';
    return {
      'À l’arrêt': 'Stopped', 'Freinage brusque : anticipez': 'Harsh braking: anticipate',
      'Descente : levez le pied pour couper l’injection': 'Downhill: lift off to cut injection',
      'Accélération trop vive': 'Acceleration too hard', 'Accélération souple : normal de consommer plus': 'Smooth acceleration: higher consumption is normal',
      'Montée : surconsommation normale': 'Climbing: higher consumption is normal', 'Conduite souple, continuez': 'Smooth driving, keep going'
    }[fr] || fr;
  }
  function tone(fr) { return /Conduite souple|Montée|À l’arrêt|souple :/.test(fr) ? 'ok' : 'warn'; }

  // Scroll to distance: town and country road get more room than the ring road.
  var HOLD = 0.05, TRAVEL_END = 0.8, COLLAPSE_END = 0.92;
  var WEIGHTS = [[0, 4000, 1.3], [4000, 16000, 1.1], [16000, 24000, 1.6], [24000, 25000, 0.7]];
  var TOTAL_W = WEIGHTS.reduce(function (s, w) { return s + w[2]; }, 0);
  function metresAt(p) {
    var t = Math.min(Math.max((p - HOLD) / (TRAVEL_END - HOLD), 0), 1) * TOTAL_W, acc = 0;
    for (var i = 0; i < WEIGHTS.length; i++) {
      var w = WEIGHTS[i];
      if (t <= acc + w[2]) return w[0] + (t - acc) / w[2] * (w[1] - w[0]);
      acc += w[2];
    }
    return D.LENGTH;
  }
  function collapseAt(p) { return Math.min(Math.max((p - TRAVEL_END) / (COLLAPSE_END - TRAVEL_END), 0), 1); }

  function strip(t) {
    var zones = D.ZONES.map(function (z) {
      var w = (z[1] - z[0]) / D.LENGTH * 100;
      return '<span class="z z-' + z[4] + '" style="width:' + w + '%"><span>' + esc(w > 9.5 ? t.zones[z[4]] : '') + '</span></span>';
    }).join('');
    var marks = D.EVENTS.slice(0, -1).map(function (e) { return '<i class="ev" style="left:' + (e[0] / D.LENGTH * 100) + '%" title="' + esc(e[1]) + '"></i>'; }).join('');
    return '<div class="route" aria-hidden="true"><span class="end">A · ' + esc(t.from) + '</span><div class="track">' + zones + marks + '<i class="cursor" data-cursor></i></div><span class="end">B · ' + esc(t.to) + '</span></div>';
  }

  function side(which, t) {
    var label = which === 'usual' ? t.usualLabel : t.ecoLabel, title = which === 'usual' ? t.usualTitle : t.ecoTitle;
    return '<div class="side ' + which + '"><div class="inner">' +
      '<p class="label">' + esc(label) + '</p><h2>' + esc(title) + '</h2>' +
      '<dl class="gauges">' +
      '<div><dt>' + esc(t.used) + '</dt><dd class="big"><span data-' + which + '-l>0,00</span> L</dd></div>' +
      '<div><dt>' + esc(t.speed) + '</dt><dd><span data-' + which + '-v>0</span> km/h</dd></div>' +
      '<div><dt>' + esc(t.time) + '</dt><dd><span data-' + which + '-t>0:00</span></dd></div></dl>' +
      '<div class="hint"><p class="hint-l">' + esc(which === 'usual' ? t.usualHint : t.ecoHint) + '</p><p class="hint-t" data-' + which + '-h></p></div>' +
      '</div></div>';
  }

  function result(t) {
    var saved = usual.litres - eco.litres, pct = Math.round(saved / usual.litres * 100);
    var yearL = saved * 2 * A.workDays;
    return '<div class="win"><h2>' + esc(fill(t.resultTitle, { pct: pct })) + '</h2>' +
      '<p class="win-text">' + esc(fill(t.resultText, { eco: num(eco.litres, 2), usual: num(usual.litres, 2), more: duration(eco.time - usual.time) })) + '</p>' +
      '<p class="win-year">' + esc(fill(t.yearText, { days: A.workDays, liters: num(Math.round(yearL), 0), euros: num(Math.round(yearL * A.pricePerLiter), 0), co2: num(Math.round(yearL * A.co2PerLiter), 0) })) + '</p>' +
      '<div class="cta"><a class="btn primary" href="#download">' + esc(window.KIT_UI[lang].apkBtn) + '</a><a class="btn ghost" href="' + S.repo + '">' + esc(window.KIT_UI[lang].source) + '</a></div></div>';
  }

  function downloadBlock(t, ui) {
    var d = S.download || {}, blocks = '';
    if (d.apk) {
      var ver = '<p class="note">' + esc(ui.apkVersion.replace('{v}', d.apk.version).replace('{c}', d.apk.versionCode)
        .replace('{d}', new Intl.DateTimeFormat(lang === 'fr' ? 'fr-FR' : 'en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(d.apk.builtOn + 'T00:00:00Z')))) + '</p>';
      blocks += '<div class="card"><span class="badge">APK</span><h3>' + esc(ui.apkTitle) + '</h3><p>' + esc(ui.apkText) + '</p><a class="btn primary" href="' + d.apk.url + '">' + ui.apkBtn + '</a>' + ver +
        '<p class="note">SHA-256</p><code class="sha">' + esc(d.apk.sha256) + '</code></div>';
    }
    var h = '<section id="download"><div class="wrap"><h2>' + esc(t.downloadTitle) + '</h2><p class="intro">' + esc(t.downloadIntro) + '</p><div class="dl">' + blocks + '</div>';
    if (d.apk && d.apk.sha256) {
      var cmd = 'curl -LO ' + d.apk.url + '\necho "' + d.apk.sha256 + '  ' + d.apk.url.split('/').pop() + '" | sha256sum -c -';
      h += '<div class="term"><div class="bar"><span>' + esc(ui.verify) + '</span><button class="copy" type="button" data-copy>' + ui.copy + '</button></div><pre><code id="cmd">' + esc(cmd) + '</code></pre></div>';
    }
    return h + '<p class="note">' + esc(t.downloadNote) + '</p></div></section>';
  }

  var engine = null, raf = 0;

  function render() {
    var t = S.i18n[lang], ui = window.KIT_UI[lang] || window.KIT_UI.en, c = S.common;
    document.documentElement.lang = lang;
    document.title = S.name + ' · ' + t.tagline;
    $('meta[name=description]').setAttribute('content', t.description);
    var h = '<a class="skip" href="#main">' + ui.skip + '</a>';
    h += '<nav class="top"><a class="brand" href="#main"><img src="' + S.logo + '" alt="">' + esc(S.name) + '</a>' +
      '<ul><li><a href="#tips">' + esc(t.nav.app) + '</a></li><li><a href="#download">' + esc(t.nav.download) + '</a></li><li><a href="' + S.repo + '">GitHub</a></li></ul>' +
      '<div class="lang" role="group" aria-label="' + ui.language + '">' + LANGS.map(function (l) { return '<button type="button" data-lang="' + l + '" aria-pressed="' + (l === lang) + '" lang="' + l + '">' + l.toUpperCase() + '</button>'; }).join('') + '</div></nav>';
    h += '<main id="main"><div class="act-root"><section class="act" data-sc-act="pin" data-sc-span="6"><div class="stage" data-sc-stage>' +
      '<header class="head"><h1>' + t.h1 + '</h1><p class="lede">' + esc(t.lede) + '</p>' + strip(t) + '</header>' +
      '<div class="area" data-area>' + side('eco', t) + side('usual', t) +
      '<div class="divider" data-divider aria-hidden="true"><span class="chip"><span><span data-km>0,0</span> ' + esc(t.km) + '</span><b>' + esc(t.gap) + ' <span data-gap>0,00</span> L</b></span></div>' +
      result(t) + '</div>' +
      '<p class="sim">' + esc(t.simulation) + '</p></div></section></div>';
    // The app.
    h += '<section id="tips" class="app"><div class="wrap"><h2>' + esc(t.appTitle) + '</h2><p class="intro">' + esc(t.appIntro) + '</p><div class="tips">' +
      t.tips.map(function (tip) { return '<div class="tip"><h3>' + esc(tip.title) + '</h3><p>' + esc(tip.text) + '</p><blockquote>« ' + esc(lang === 'fr' ? tip.quote : tip.quoteEn) + ' »</blockquote></div>'; }).join('') +
      '</div><p class="fair">' + esc(t.fair) + '</p><div class="shots" role="list">' +
      S.shots.map(function (s, i) { var cap = t.shotCaptions[i] || ''; return '<figure class="shot" role="listitem"><img src="' + s.src + '" alt="' + esc(cap) + '" loading="lazy"><figcaption>' + esc(cap) + '</figcaption></figure>'; }).join('') +
      '</div><p class="caption">' + esc(t.shotNote) + '</p></div></section>';
    // Method.
    h += '<section id="method" class="method"><div class="wrap"><h2>' + esc(t.howTitle) + '</h2><ul>' +
      t.how.map(function (line) { return '<li>' + esc(fill(line, { days: A.workDays, price: num(A.pricePerLiter, 2), co2: num(A.co2PerLiter, 1) })) + '</li>'; }).join('') +
      '</ul><p><a href="' + S.repo + '/blob/main/app/src/main/java/tech/dm/conso/eco/VehicleModel.kt">' + esc(t.howLink) + '</a></p></div></section>';
    h += downloadBlock(t, ui) + '</main>';
    h += '<footer><div class="wrap"><span>' + esc(S.name) + ' · ' + esc(ui.licensed) + ' <a href="' + c.licenseUrl + '">' + c.license + '</a></span>' +
      '<span><a href="' + t.privacyUrl + '">' + esc(t.privacy) + '</a> · <a href="' + S.dmcPage[lang] + '">' + esc(ui.dmc) + '</a> · <a href="' + S.repo + '">GitHub</a> · <a href="' + S.repo + '/issues">' + esc(ui.issues) + '</a></span></div></footer>';
    $('#app').innerHTML = h;
    mount();
  }

  function mount() {
    if (engine) { engine.destroy(); engine = null; }
    cancelAnimationFrame(raf);
    var act = $('.act'), area = $('[data-area]');
    var els = {};
    ['usual-l', 'usual-v', 'usual-t', 'usual-h', 'eco-l', 'eco-v', 'eco-t', 'eco-h'].forEach(function (k) { els[k] = $('[data-' + k + ']'); });
    var km = $('[data-km]'), gap = $('[data-gap]'), cursor = $('[data-cursor]'), divider = $('[data-divider]');
    var last = { x: -1, c: -1 };
    function draw(p) {
      var x = metresAt(p), col = collapseAt(p);
      if (Math.abs(x - last.x) >= 1) {
        last.x = x;
        var u = D.at(usual, x), e = D.at(eco, x);
        els['usual-l'].textContent = num(u.l, 2); els['eco-l'].textContent = num(e.l, 2);
        els['usual-v'].textContent = num(Math.round(u.v), 0); els['eco-v'].textContent = num(Math.round(e.v), 0);
        els['usual-t'].textContent = clock(u.t); els['eco-t'].textContent = clock(e.t);
        var hu = D.hint(u, 'usual'), he = D.hint(e, 'eco');
        els['usual-h'].textContent = hintText(hu); els['usual-h'].dataset.tone = tone(hu);
        els['eco-h'].textContent = hintText(he); els['eco-h'].dataset.tone = tone(he);
        km.textContent = num(x / 1000, 1);
        gap.textContent = (u.l - e.l > 0.005 ? '−' : '') + num(Math.max(0, u.l - e.l), 2);
        cursor.style.left = (x / D.LENGTH * 100) + '%';
      }
      if (col !== last.c) {
        last.c = col;
        area.style.setProperty('--collapse', col.toFixed(3));
        area.classList.toggle('collapsed', col > 0.6);
      }
    }
    draw(0);
    function start() {
      if (!window.ScrollCraft) return;
      engine = window.ScrollCraft.mount($('.act-root'));
      document.documentElement.classList.add('live');
      (function frame() {
        var a = engine && engine.acts.find(function (x) { return x.el === act; });
        if (a) draw(a.p);
        raf = requestAnimationFrame(frame);
      })();
    }
    start();
  }

  document.addEventListener('click', function (e) {
    var b = e.target.closest('[data-lang]');
    if (b) {
      lang = b.dataset.lang; try { localStorage.setItem('lang', lang); } catch (x) {}
      var u = new URL(location.href); u.searchParams.set('lang', lang); history.replaceState(null, '', u);
      render(); var nb = $('[data-lang="' + lang + '"]'); nb && nb.focus();
    }
    var c = e.target.closest('[data-copy]');
    if (c) { navigator.clipboard.writeText($('#cmd').textContent).then(function () { var o = c.textContent; c.textContent = '✓'; setTimeout(function () { c.textContent = o; }, 1500); }); }
  });
  if (reduced) document.documentElement.classList.add('reduced');
  render();
})();
