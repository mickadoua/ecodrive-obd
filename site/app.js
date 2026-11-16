/* Renders the page from window.SITE (content.js). No framework, no build. */
(function () {
  var S = window.SITE, $ = function (s, r) { return (r || document).querySelector(s); };
  var LANGS = Object.keys(S.i18n);                       // add a language = add a key in content.js
  var saved = null; try { saved = localStorage.getItem('lang'); } catch (e) {}
  var q = new URLSearchParams(location.search).get('lang');
  var nav = (navigator.language || 'en').slice(0, 2);
  var lang = [q, saved, nav].find(function (l) { return LANGS.indexOf(l) > -1; }) || 'en';

  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  var ICONS = {
    mic: '<path d="M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/>',
    bolt: '<path d="M13 3 5 14h6l-1 7 8-11h-6z"/>', lock: '<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
    chart: '<path d="M4 20V4M4 20h16M8 16v-5M12 16V8M16 16v-3"/>', map: '<path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2z"/><path d="M9 4v14M15 6v14"/>',
    gauge: '<path d="M4 17a8 8 0 1 1 16 0"/><path d="m12 17 4-6"/>', text: '<path d="M4 7h16M4 12h10M4 17h7"/>',
    keys: '<rect x="2.5" y="6" width="19" height="12" rx="2.5"/><path d="M6.5 10h.01M10 10h.01M13.5 10h.01M17 10h.01M8 14h8"/>', shield: '<path d="M12 3 5 6v6c0 4 3 7 7 9 4-2 7-5 7-9V6z"/>'
  };
  function icon(n) { return '<span class="icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (ICONS[n] || ICONS.bolt) + '</svg></span>'; }

  function render() {
    var t = S.i18n[lang], c = S.common || {}, ui = (window.KIT_UI[lang] || window.KIT_UI.en);
    document.documentElement.lang = lang;
    document.title = S.name + ' — ' + t.tagline;
    $('meta[name=description]').setAttribute('content', t.lede);
    var h = '';
    h += '<a class="skip" href="#main">' + ui.skip + '</a>';
    h += '<nav><div class="wrap"><a class="brand" href="#">' + (S.logo ? '<img src="' + S.logo + '" alt="">' : '') + esc(S.name) + '</a><ul>' +
      '<li><a href="#features">' + ui.features + '</a></li>' + (t.highlights ? '<li><a href="#details">' + ui.details + '</a></li>' : '') +
      '<li><a href="#download">' + ui.download + '</a></li><li><a href="' + S.repo + '">GitHub</a></li></ul>' +
      '<div class="lang" role="group" aria-label="' + ui.language + '">' + LANGS.map(function (l) { return '<button type="button" data-lang="' + l + '" aria-pressed="' + (l === lang) + '" lang="' + l + '">' + l.toUpperCase() + '</button>'; }).join('') + '</div></div></nav>';
    h += '<main id="main"><header class="hero"><div class="wrap">' + (S.logo ? '<img class="logo" src="' + S.logo + '" alt="">' : '') +
      '<span class="eyebrow">' + esc(t.eyebrow) + '</span><h1>' + t.h1 + '</h1><p class="lede">' + esc(t.lede) + '</p><div class="cta">' +
      '<a class="btn primary" href="#download">' + ui.download + '</a><a class="btn ghost" href="' + S.repo + '">' + ui.source + '</a></div></div>';
    if (S.shots && S.shots.length) {
      h += '<div class="shots" role="list">' + S.shots.map(function (s, i) {
        var cap = (t.shotCaptions || [])[i] || '';
        var img = s.src ? '<img src="' + s.src.replace('{lang}', lang) + '" alt="' + esc(cap) + '" loading="lazy">' : '<div class="ph">' + esc(ui.placeholder) + '</div>';
        if (s.srcDark) img = '<picture><source srcset="' + s.srcDark + '" media="(prefers-color-scheme: dark)">' + img + '</picture>';
        return '<figure class="shot' + (s.wide ? ' wide' : '') + '" role="listitem">' + img + '<figcaption>' + esc(cap) + '</figcaption></figure>';
      }).join('') + '</div>';
      if (t.shotNote) h += '<p class="caption wrap">' + esc(t.shotNote) + '</p>';
    }
    h += '</header>';
    h += '<section id="features" class="alt"><div class="wrap"><p class="kicker">' + esc(t.featuresKicker) + '</p><h2>' + esc(t.featuresTitle) + '</h2><p class="intro">' + esc(t.featuresIntro) + '</p><div class="grid">' +
      t.features.map(function (f) { return '<div class="card">' + icon(f.icon) + '<h3>' + esc(f.title) + '</h3><p>' + esc(f.text) + '</p></div>'; }).join('') + '</div></div></section>';
    if (t.highlights) {
      var hl = t.highlights;
      h += '<section id="details"><div class="wrap split"><div><p class="kicker">' + esc(hl.kicker) + '</p><h2>' + esc(hl.title) + '</h2><p class="intro">' + esc(hl.intro) + '</p>' +
        (hl.chips ? '<ul class="chips">' + hl.chips.map(function (x) { return '<li>' + esc(x) + '</li>'; }).join('') + '</ul>' : '') +
        '<ul class="checks">' + hl.checks.map(function (x) { return '<li><strong>' + esc(x[0]) + '</strong> ' + esc(x[1]) + '</li>'; }).join('') + '</ul></div>' +
        '<div class="aside">' + hl.facts.map(function (x) { return '<div class="row"><span class="k">' + esc(x[0]) + '</span><span>' + esc(x[1]) + '</span></div>'; }).join('') + '</div></div></section>';
    }
    // Download: blocks are optional, driven by S.download = { play, apk:{url,sha256,size}, source }
    var d = S.download || {}, blocks = '';
    if (d.play) blocks += '<div class="card"><span class="badge">Google Play</span><h3>' + esc(ui.playTitle) + '</h3><p>' + esc(d.playLive ? ui.playText : ui.playSoon) + '</p><a class="btn primary" href="' + d.play + '">' + ui.playBtn + '</a></div>';
    if (d.apk) {
      var sha = d.apk.sha256 ? '<code class="sha" id="sha">' + esc(d.apk.sha256) + '</code>' : '<p class="todo">' + esc(ui.shaTodo) + '</p>';
      blocks += '<div class="card"><span class="badge">APK</span><h3>' + esc(ui.apkTitle) + '</h3><p>' + esc(ui.apkText) + '</p><a class="btn ' + (d.play ? 'ghost' : 'primary') + '" href="' + d.apk.url + '">' + ui.apkBtn + (d.apk.size ? ' (' + esc(d.apk.size) + ')' : '') + '</a>' +
        '<p class="note" style="margin-bottom:0">SHA-256</p>' + sha + '</div>';
    }
    if (d.build) blocks += '<div class="card"><span class="badge">' + esc(ui.buildBadge) + '</span><h3>' + esc(ui.buildTitle) + '</h3><p>' + esc(t.buildText || '') + '</p><a class="btn ghost" href="' + d.build + '">' + ui.buildBtn + '</a></div>';
    h += '<section id="download" class="alt"><div class="wrap"><p class="kicker">' + esc(ui.download) + '</p><h2>' + esc(t.downloadTitle) + '</h2><p class="intro">' + esc(t.downloadIntro) + '</p><div class="dl">' + blocks + '</div>';
    if (d.apk && d.apk.sha256) {
      var cmd = 'curl -LO ' + d.apk.url + '\necho "' + d.apk.sha256 + '  ' + d.apk.url.split('/').pop() + '" | sha256sum -c -';
      h += '<div class="term"><div class="bar"><span>' + esc(ui.verify) + '</span><button class="copy" type="button" data-copy>' + ui.copy + '</button></div><pre><code id="cmd">' + esc(cmd) + '</code></pre></div>';
    }
    h += '<p class="note">' + esc(t.downloadNote || '') + '</p></div></section></main>';
    h += '<footer><div class="wrap"><span>' + esc(S.name) + ' · ' + esc(ui.licensed) + ' <a href="' + c.licenseUrl + '">' + c.license + '</a></span>' +
      '<span><a href="' + (typeof S.dmcPage === "object" ? S.dmcPage[lang] : S.dmcPage) + '">' + esc(ui.dmc) + '</a> · <a href="' + S.repo + '">GitHub</a> · <a href="' + S.repo + '/issues">' + esc(ui.issues) + '</a></span></div></footer>';
    $('#app').innerHTML = h;
  }
  document.addEventListener('click', function (e) {
    var b = e.target.closest('[data-lang]');
    if (b) { lang = b.dataset.lang; try { localStorage.setItem('lang', lang); } catch (x) {} render(); var nb = $('[data-lang="' + lang + '"]'); nb && nb.focus(); }
    var c = e.target.closest('[data-copy]');
    if (c) { navigator.clipboard.writeText($('#cmd').textContent).then(function () { var o = c.textContent; c.textContent = '✓'; setTimeout(function () { c.textContent = o; }, 1500); }); }
  });
  render();
})();
