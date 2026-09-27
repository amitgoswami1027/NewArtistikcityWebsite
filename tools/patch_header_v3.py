"""Updates the static header in the classic Mustache pages to the site's menu order:
How it works · Courses · Commission art · Marketplace · Studio Stories · search · Log in / Join for free."""
import os, re, sys
root = sys.argv[1] if len(sys.argv) > 1 else 'src/main/resources/templates'
HEADER = '''<!-- ac:header -->
<div class="ac">
<header class="dm-header">
    <div class="ac-wide dm-header__row">
        <a href="/" class="ac-logo" aria-label="ArtistikCity home"><img src="/assets/images/logo.png" alt="ArtistikCity" /></a>
        <nav class="dm-links dm-links--main" aria-label="Main">
            <a href="/how-it-works">How it works</a>
            <div class="dm-explore"><a href="/courses?type=all" style="display:inline-flex;align-items:center;gap:8px;padding:8px 12px;font-weight:600;font-size:15px;border-radius:999px;color:#111">Courses <i class="fa fa-angle-down"></i></a></div>
            <a href="/commission/step-1">Commission art</a>
            <a href="/marketplace">Marketplace</a>
            <a href="/studio-stories" class="is-accent">Studio Stories</a>
        </nav>
        <form class="dm-search" action="/courses" method="get" role="search">
            <i class="fa fa-search"></i>
            <input type="hidden" name="type" value="all">
            <input type="search" name="q" placeholder="Search courses…" aria-label="Search courses">
        </form>
        <nav class="dm-links dm-links--account" aria-label="Account">
            {{#auth.check}}
                <a href="/dashboard">My studio</a>
                <a href="javascript:void(0)" class="dm-btn dm-btn--line" onclick="document.getElementById('logout-form').submit();">Log out</a>
                <form id="logout-form" action="/logout" method="POST" style="display: none;"><input type="hidden" name="_token" value="{{csrf}}"></form>
            {{/auth.check}}
            {{#auth.guest}}
                <a href="/login">Log in</a>
                <a href="/join" class="dm-btn dm-btn--brand" style="margin-left:6px">Join for free</a>
            {{/auth.guest}}
        </nav>
        <button type="button" class="ac-burger" aria-label="Menu" onclick="var d=document.getElementById('ac-drawer');d.style.display=d.style.display==='block'?'none':'block';"><i class="fa fa-bars"></i></button>
    </div>
</header>
<div class="ac-drawer" id="ac-drawer" style="display:none;top:68px">
    <a href="/how-it-works">How it works</a>
    <a href="/courses?type=all">All courses</a>
    <a href="/courses?type=workshop">Workshops</a>
    <a href="/commission/step-1">Commission art</a>
    <a href="/marketplace">Marketplace</a>
    <a href="/studio-stories">Studio Stories</a>
    {{#auth.check}}<a href="/dashboard" class="dm-btn dm-btn--line dm-btn--block">My studio</a>{{/auth.check}}
    {{#auth.guest}}<a href="/join" class="dm-btn dm-btn--brand dm-btn--block">Join for free</a><a href="/login" class="dm-btn dm-btn--line dm-btn--block" style="margin-top:10px">Log in</a>{{/auth.guest}}
</div>
</div>
<!-- /ac:header -->'''
n = 0
for dirpath, _, files in os.walk(root):
    for f in files:
        if not f.endswith('.mustache'):
            continue
        p = os.path.join(dirpath, f)
        s = open(p, encoding='utf-8').read()
        if '<!-- ac:header -->' not in s:
            continue
        s2 = re.sub(r'<!-- ac:header -->.*?<!-- /ac:header -->', lambda m: HEADER, s, flags=re.S)
        s2 = s2.replace('href="/student-feedback">Gallery<', 'href="/marketplace">Art marketplace<')
        s2 = s2.replace('href="/student-feedback">Student gallery<', 'href="/marketplace">Art marketplace<')
        s2 = s2.replace('href="/student-shop">Student shop<', 'href="/marketplace?source=student">Student originals<')
        if s2 != s:
            open(p, 'w', encoding='utf-8').write(s2)
            n += 1
            print('patched', p)
print(n, 'files')
