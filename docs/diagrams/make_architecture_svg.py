"""Draws docs/diagrams/aws-architecture.svg (the ArtistikCity AWS architecture). Run: python3 make_architecture_svg.py"""
from pathlib import Path

W, H = 1720, 1100
C = {  # AWS-style category colours: (accent, fill, text)
    'people':   ('#FF9900', '#FFF4E5', '#232F3E'),
    'external': ('#879196', '#F2F3F3', '#232F3E'),
    'network':  ('#8C4FFF', '#EDE4FF', '#2B1A5A'),
    'compute':  ('#ED7100', '#FFE8D1', '#5A2A00'),
    'database': ('#3B48CC', '#E3ECFF', '#14205E'),
    'storage':  ('#7AA116', '#E8F5D6', '#2C4405'),
    'security': ('#DD344C', '#FFE3E6', '#5C0F1A'),
    'mgmt':     ('#E7157B', '#FDE4F1', '#5C0A30'),
    'devtools': ('#01A88D', '#E0F4F4', '#06433A'),
}
REQ, DEP, AUX = '#232F3E', '#01A88D', '#B0577E'
out = []
add = out.append


def esc(t):
    return t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def group(x, y, w, h, title, stroke, fill, dash='', tcolor=None, badge=None):
    add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="{fill}" stroke="{stroke}" stroke-width="2" {f"stroke-dasharray={dash!r}" if dash else ""}/>')
    tx = x + 16
    if badge:
        add(f'<rect x="{x + 12}" y="{y + 10}" width="26" height="26" rx="6" fill="{stroke}"/>'
            f'<text x="{x + 25}" y="{y + 28}" font-size="13" font-weight="700" fill="#fff" text-anchor="middle">{esc(badge)}</text>')
        tx = x + 46
    add(f'<text x="{tx}" y="{y + 29}" font-size="16" font-weight="700" fill="{tcolor or stroke}">{esc(title)}</text>')


def card(x, y, w, h, cat, abbr, title, lines=(), cyl=False):
    accent, fill, text = C[cat]
    if cyl:
        e = 12
        add(f'<path d="M{x},{y + e} A{w / 2},{e} 0 0,1 {x + w},{y + e} L{x + w},{y + h - e} A{w / 2},{e} 0 0,1 {x},{y + h - e} Z" fill="{fill}" stroke="{accent}" stroke-width="2.5" filter="url(#sh)"/>')
        add(f'<ellipse cx="{x + w / 2}" cy="{y + e}" rx="{w / 2}" ry="{e}" fill="{fill}" stroke="{accent}" stroke-width="2.5"/>')
        top = y + 2 * e
    else:
        add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="{fill}" stroke="{accent}" stroke-width="2.5" filter="url(#sh)"/>')
        top = y
    ix, iy = x + 12, top + 12
    add(f'<rect x="{ix}" y="{iy}" width="44" height="44" rx="9" fill="{accent}"/>'
        f'<text x="{ix + 22}" y="{iy + 28}" font-size="{14 if len(abbr) <= 3 else 11.5}" font-weight="800" fill="#fff" text-anchor="middle">{esc(abbr)}</text>')
    add(f'<text x="{ix + 56}" y="{iy + 18}" font-size="15.5" font-weight="700" fill="{text}">{esc(title)}</text>')
    for i, ln in enumerate(lines):
        add(f'<text x="{ix + 56}" y="{iy + 38 + i * 18}" font-size="13" fill="{text}">{esc(ln)}</text>')


def arrow(d, color, width=2.5, dash='', marker='a', label=None, lx=0, ly=0, lcolor=None, num=None, nx=0, ny=0):
    add(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{width}" {f"stroke-dasharray={dash!r}" if dash else ""} marker-end="url(#{marker})"/>')
    if label:
        lines = label.split('\n')
        wmax = max(len(s) for s in lines) * 7.1 + 14
        hh = 18 * len(lines) + 6
        add(f'<rect x="{lx - wmax / 2}" y="{ly - 15}" width="{wmax}" height="{hh}" rx="6" fill="#ffffff" stroke="{lcolor or color}" stroke-width="1"/>')
        for i, s in enumerate(lines):
            add(f'<text x="{lx}" y="{ly + i * 18}" font-size="12.5" fill="{lcolor or color}" text-anchor="middle" font-weight="600">{esc(s)}</text>')
    if num:
        add(f'<circle cx="{nx}" cy="{ny}" r="13" fill="{color}"/><text x="{nx}" y="{ny + 5}" font-size="13" font-weight="800" fill="#fff" text-anchor="middle">{esc(num)}</text>')


add(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="Arial, Helvetica, sans-serif">')
add('<defs>'
    '<filter id="sh" x="-5%" y="-5%" width="110%" height="120%"><feDropShadow dx="0" dy="2" stdDeviation="2" flood-color="#0b1f33" flood-opacity="0.16"/></filter>'
    f'<marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="{REQ}"/></marker>'
    f'<marker id="d" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="{DEP}"/></marker>'
    f'<marker id="x" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="{AUX}"/></marker>'
    '</defs>')
add(f'<rect width="{W}" height="{H}" fill="#ffffff"/>')
add('<text x="30" y="44" font-size="26" font-weight="800" fill="#232F3E">ArtistikCity on AWS</text>'
    '<text x="30" y="70" font-size="15" fill="#545B64">Region ap-south-1 (Mumbai) · CloudFront + EC2 (Docker) + RDS MySQL + S3 · deployed by GitHub Actions</text>')

# ------------------------------------------------------------------ containers
group(520, 95, 1180, 905, 'AWS Cloud · your account', '#232F3E', '#FBFCFD', badge='aws')
group(545, 145, 255, 340, 'Global edge', C['network'][0], '#F7F2FF', dash='7 5')
group(825, 145, 855, 830, 'Region ap-south-1 · Mumbai', '#147EBA', '#F4F9FD', dash='7 5', tcolor='#0F5E8C')
group(850, 330, 500, 455, 'VPC (default)', '#248814', '#F1F9EE', tcolor='#1B5E10')
group(868, 372, 464, 190, 'Public subnet', '#248814', '#FFFFFF', dash='4 3', tcolor='#1B5E10')
group(868, 578, 464, 195, 'DB subnets', '#3B48CC', '#FFFFFF', dash='4 3', tcolor='#14205E')
group(850, 800, 805, 160, '', '#545B64', '#FFFFFF', dash='4 3', tcolor='#545B64')
add('<text x="1640" y="829" font-size="16" font-weight="700" fill="#545B64" text-anchor="end">Deployment, access &amp; cost</text>')
group(25, 640, 465, 345, 'GitHub · NewArtistikcityWebsite', C['devtools'][0], '#F2FBFA', dash='7 5')

# ------------------------------------------------------------------ nodes
card(40, 170, 215, 76, 'people', 'USR', 'Students & visitors', ['browser · phone'])
card(40, 268, 215, 76, 'people', 'ADM', 'Admins & teachers', ['/admin console'])
card(290, 215, 200, 84, 'external', 'DNS', 'Domain DNS', ['www.artistikcity.com', '→ CloudFront'])
card(565, 190, 215, 84, 'security', 'ACM', 'ACM certificate', ['us-east-1 · free', 'auto-renews'])
card(565, 335, 215, 120, 'network', 'CF', 'CloudFront CDN', ['HTTPS · HTTP/2+3', 'cache · gzip/brotli', 'edges across India'])
card(870, 180, 300, 130, 'storage', 'S3', 'S3 bucket (private)', ['storage/uploads · certificates', 'photos · videos · artworks', 'versioned · encrypted'], cyl=True)
card(888, 418, 70, 70, 'network', 'EIP', '', [])
card(1010, 410, 305, 128, 'compute', 'EC2', 'EC2 t3.small', ['Amazon Linux 2023 · Docker', 'Spring Boot + React (port 8080)', 'IAM role · IMDSv2 · 2 GB swap'])
card(1010, 612, 305, 128, 'database', 'RDS', 'RDS MySQL 8.4', ['db.t4g.micro · private', 'encrypted · TLS required', 'daily backups (7 days)'], cyl=True)
card(1380, 330, 280, 80, 'security', 'SM', 'Secrets Manager', ['DB password · origin secret · OTP'])
card(1380, 425, 280, 80, 'mgmt', 'SSM', 'Parameter Store', ['Razorpay · mail · OAuth keys'])
card(1380, 520, 280, 80, 'mgmt', 'CW', 'CloudWatch Logs', ['/artistikcity/app · 30 days'])
card(1380, 615, 280, 80, 'security', 'IAM', 'IAM roles', ['server role · GitHub deploy role'])
card(870, 845, 240, 96, 'mgmt', 'RUN', 'SSM Run Command', ['runs the deploy script', '+ Session Manager shell'])
card(1135, 845, 240, 96, 'devtools', 'ECR', 'ECR registry', ['Docker images', 'tag = git commit'])
card(1400, 845, 240, 96, 'mgmt', 'BUD', 'Budget alert', ['email when > $60 / month'])
card(60, 685, 395, 66, 'people', 'DEV', 'Developer', ['git push to main'])
card(60, 785, 395, 66, 'devtools', 'GIT', 'Repository · main branch', ['code + infra/ + deploy/'])
card(60, 885, 395, 82, 'devtools', 'GHA', 'GitHub Actions', ['Test → Build image → Deploy', 'signs in to AWS with OIDC (no keys)'])

# ------------------------------------------------------------------ request flow (dark, numbered)
arrow('M255,208 C272,208 272,245 288,245', REQ)
arrow('M255,306 C272,306 272,270 288,270', REQ, num='1', nx=270, ny=257)
arrow('M490,257 C525,257 525,395 563,395', REQ, num='2', nx=525, ny=330)
arrow('M672,276 L672,333', '#8C4FFF', width=2, dash='5 4', label='TLS cert', lx=716, ly=309, lcolor='#6B3FD1')
arrow('M780,360 C820,360 820,240 868,240', REQ, width=3.2, label='/storage/*', lx=826, ly=330, num='3a', nx=820, ny=300)
arrow('M780,425 C830,425 835,453 886,453', REQ, width=3.2, label='pages · API · admin\n+ X-Origin-Verify', lx=825, ly=532, num='3b', nx=840, ny=440)
arrow('M958,453 L1008,453', REQ, width=3.2)
arrow('M1285,538 L1285,610', REQ, width=3.2, label='SQL over TLS', lx=1215, ly=596, num='4', nx=1285, ny=574)
arrow('M1060,410 L1060,312', REQ, width=2.2, label='uploads', lx=1060, ly=352)
# app → supporting services (dashed pink)
arrow('M1315,440 C1350,440 1345,370 1378,370', AUX, width=2, dash='5 4', marker='x')
arrow('M1315,470 C1350,470 1345,465 1378,465', AUX, width=2, dash='5 4', marker='x')
arrow('M1315,500 C1350,500 1345,560 1378,560', AUX, width=2, dash='5 4', marker='x')
arrow('M1378,655 C1340,655 1345,525 1317,525', AUX, width=2, dash='5 4', marker='x')

# ------------------------------------------------------------------ deploy flow (teal, D1..D4)
arrow('M257,751 L257,783', DEP, width=2.2, marker='d')
arrow('M257,851 L257,883', DEP, width=2.2, marker='d')
arrow('M455,945 C700,945 1000,985 1255,985 L1255,943', DEP, width=2.6, dash='8 5', marker='d', label='push image', lx=760, ly=975, num='D1', nx=640, ny=962)
arrow('M455,905 C620,905 700,893 868,893', DEP, width=2.6, dash='8 5', marker='d', label='run deploy', lx=640, ly=885, num='D2', nx=540, ny=905)
arrow('M990,843 L990,532 C990,520 995,515 1008,515', DEP, width=2.6, dash='8 5', marker='d', label='pull · migrate DB\nswap · health check', lx=925, ly=668, num='D3', nx=990, ny=815)
arrow('M455,925 C490,925 505,915 505,880 L505,560 C505,500 600,500 640,459', DEP, width=2.2, dash='8 5', marker='d', label='refresh cache\n+ health check', lx=505, ly=600, num='D4', nx=505, ny=700)

# security group notes and captions
add('<text x="1022" y="555" font-size="12.5" fill="#1B5E10" font-weight="600">🔒 SG: port 80 from CloudFront only</text>')
add('<text x="1022" y="764" font-size="12.5" fill="#14205E" font-weight="600">🔒 SG: MySQL 3306 from the app only</text>')
add('<text x="923" y="506" font-size="12" fill="#2B1A5A" text-anchor="middle" font-weight="600">Elastic IP</text>')

# ------------------------------------------------------------------ legend
lx, ly = 30, 1030
add(f'<rect x="20" y="1010" width="{W - 40}" height="70" rx="10" fill="#F7F8F9" stroke="#D5DBDB"/>')
items = [('people', 'People'), ('network', 'Networking / CDN'), ('compute', 'Compute'), ('database', 'Database'),
         ('storage', 'Storage'), ('security', 'Security & identity'), ('mgmt', 'Management'), ('devtools', 'Developer tools')]
x = 40
for cat, name in items:
    accent, fill, _ = C[cat]
    add(f'<rect x="{x}" y="1026" width="18" height="18" rx="4" fill="{accent}"/><text x="{x + 25}" y="1040" font-size="13" fill="#232F3E">{esc(name)}</text>')
    x += 38 + len(name) * 7.2
add(f'<line x1="40" y1="1063" x2="80" y2="1063" stroke="{REQ}" stroke-width="3" marker-end="url(#a)"/><text x="90" y="1068" font-size="13" fill="#232F3E">Visitor request (1 → 4)</text>')
add(f'<line x1="290" y1="1063" x2="330" y2="1063" stroke="{DEP}" stroke-width="3" stroke-dasharray="8 5" marker-end="url(#d)"/><text x="340" y="1068" font-size="13" fill="#232F3E">Deployment (D1 → D4)</text>')
add(f'<line x1="530" y1="1063" x2="570" y2="1063" stroke="{AUX}" stroke-width="2" stroke-dasharray="5 4" marker-end="url(#x)"/><text x="580" y="1068" font-size="13" fill="#232F3E">Settings, secrets, logs, permissions</text>')
add('<text x="1690" y="1068" font-size="12.5" fill="#545B64" text-anchor="end">SG = security group · EIP = Elastic IP · OIDC = GitHub signs in without stored AWS keys</text>')
add('</svg>')
Path(__file__).with_name('aws-architecture.svg').write_text('\n'.join(out), encoding='utf-8')
print('written', len(out), 'elements')
