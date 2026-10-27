# Spec section 7.3. Run: python3 -I handoff/tools/contrast.py
# WCAG 2.x contrast for every text/background pair the redesign uses.
# Run: python3 -I contrast.py   (no dependencies)
T = {  # proposed :root values (spec section 7)
 'bg':'#0b0f14','bg-2':'#0e141b','surface':'#131a22','surface-2':'#18212b',
 'border':'#222c38','border-strong':'#2f3b4a','text':'#e7ecf2','soft':'#c4ccd6',
 'muted':'#8695a7','dim':'#7b8a9c','faint':'#5a6878','amber':'#ffb454','amber-hi':'#ffc97f',
 'teal':'#4fd6c4','red':'#ef6a5f','good':'#3ccf6e','good-text':'#9fe3b7','on-amber':'#1a1206',
 'term':'#0e141b','explain':'#b9c2cd','amber-hover-nn':'#ffc370',
}
def rgb(h): h=h.lstrip('#'); return [int(h[i:i+2],16) for i in (0,2,4)]
def hexs(c): return '#%02x%02x%02x' % tuple(round(v) for v in c)
def mix(fg, bg, a):  # fg at alpha a over opaque bg
    f, b = rgb(fg if fg.startswith('#') else T[fg]), rgb(bg if bg.startswith('#') else T[bg])
    return hexs([a*x+(1-a)*y for x, y in zip(f, b)])
def lum(h):
    def ch(c): c/=255; return c/12.92 if c<=0.04045 else ((c+0.055)/1.055)**2.4
    r,g,b = rgb(h); return 0.2126*ch(r)+0.7152*ch(g)+0.0722*ch(b)
def ratio(a, b):
    la, lb = lum(a), lum(b); return (max(la,lb)+0.05)/(min(la,lb)+0.05)
C = {}  # composited backgrounds (alpha fills flattened over what sits under them)
C['header'] = mix('#0b0f14', 'text', 0.92)               # header rgba(11,15,20,.92) (section 8.1) over --text scrolling under it: worst case, blur ignored
C['header-0.82'] = mix('#0b0f14', 'text', 0.82)          # today's alpha (site.css:101), same worst case
C['hero-teal'] = mix('teal', 'bg', 0.07)                   # peak of the teal radial wash
C['hero-amber'] = mix('amber', 'bg', 0.06)                 # peak of the amber radial wash
C['status'] = mix('good', C['hero-amber'], 0.07)           # status pill fill over the worst hero point
C['badge'] = mix('good', 'surface', 0.07)                  # live badge fill on a card
C['resume-hover'] = mix('amber', C['header'], 0.10)        # nav Résumé hover fill
C['contact'] = mix('amber', 'bg', 0.06)                    # contact section wash peak
C['seg-on'] = mix('amber', 'surface', 0.12)                # mcp-surface selected example
C['rule-hit'] = mix('amber', 'surface', 0.06)              # mcp-surface rule with findings
C['verdict-pass'] = mix('good', 'surface', 0.07)
C['verdict-fail'] = mix('red', 'surface', 0.07)
C['rule-0.6'] = mix('surface-2', 'surface', 0.6)           # .rule.call-only {opacity:.6}: card fill
C['rule-0.45'] = mix('surface-2', 'surface', 0.45)         # .rule[aria-pressed=true] {opacity:.45}
C['hud'] = mix('#0b0f14', 'bg', 0.78)                      # neuronet .hud over the arena's --bg
def col(x): return C.get(x) or (T[x] if x in T else x)
def fgop(fg, bgname, a, under='surface'):  # text inside an element with opacity a
    return mix(col(fg), under, a)
# (where, fg, bg, kind) kind: text 4.5 | large 3.0 | ui 3.0 | deco (reported, no bar)
P = [
 # --- site (assets/css/site.css, _layouts/default.html, index.html, mock) ---
 ('body, h1, job and card titles', 'text','bg','text'), ('cards, buttons, glance card', 'text','surface','text'),
 ('button hover', 'text','surface-2','text'), ('brand under sticky header', 'text','header','text'),
 ('h1 on hero wash', 'text','hero-teal','large'), ('contact email on wash', 'text','contact','large'),
 ('lede, bullets', 'soft','bg','text'), ('lede on hero wash', 'soft','hero-teal','text'),
 ('card one-liners, edu', 'soft','surface','text'), ('chips', 'soft','bg-2','text'),
 ('section titles, dates, job-org, footer', 'muted','bg','text'), ('nav links in header', 'muted','header','text'),
 ('TODAY nav links, header at .82', 'muted','header-0.82','text'),
 ('glance labels (dt)', 'muted','surface','text'), ('glance values (dd)', 'soft','surface','text'),
 ('phone section row (8.1: --soft)', 'soft','bg','text'),
 ('kicker prefix "// " (span, 8.0)', 'dim','bg','text'), ('kicker text', 'teal','bg','text'),
 ('kicker on hero wash', 'teal','hero-teal','text'), ('case-facts', 'teal','bg','text'),
 ('links, "## " "> " "@ ", eyebrow', 'amber','bg','text'), ('card links', 'amber','surface','text'),
 ('"Projects and live demos" link', 'amber','hero-amber','text'), ('nav Résumé', 'amber','header','text'),
 ('nav Résumé hover', 'amber-hi','resume-hover','text'), ('link hover', 'amber-hi','bg','text'),
 ('card link hover', 'amber-hi','surface','text'),
 ('status pill', 'good-text','status','text'), ('live badge', 'good-text','badge','text'),
 ('primary button', 'on-amber','amber','text'), ('primary button hover', 'on-amber','amber-hi','text'),
 ('skip link', 'bg','amber','text'),
 ('focus ring vs page', 'amber','bg','ui'), ('focus ring vs card', 'amber','surface','ui'),
 ('focus ring vs button hover', 'amber','surface-2','ui'),
 ('list markers (::marker)', 'faint','bg','deco'), ('list markers in cards', 'faint','surface','deco'),
 # --- mcp-surface (this repo: mcp-surface/styles.css) ---
 ('back link, top links, foot', 'muted','bg','text'), ('.meta, card text', 'muted','surface','text'),
 ('editor text, .cmd', 'text','term','text'), ('.finding .msg', 'soft','term','text'),
 ('.dim .parse .summary', 'muted','term','text'), ('gutter (was --faint)', 'dim','term','text'),
 ('.rule-tag (was --faint)', 'dim','term','text'), ('gutter TODAY --faint', 'faint','term','text'),
 ('.tool-name', 'teal','term','text'), ('.prompt .ok', 'good','term','text'), ('.parse.bad, error', 'red','term','text'),
 ('warn level', 'amber','term','text'), ('verdict pass', 'good','verdict-pass','text'),
 ('verdict fail', 'red','verdict-fail','text'), ('selected example', 'amber','seg-on','text'),
 ('.rule-id, select', 'text','surface-2','text'), ('.rule-what, .rule-level', 'muted','surface-2','text'),
 ('.rule-level error', 'red','surface-2','text'), ('.rule-level warn', 'amber','surface-2','text'),
 ('.rule.hit text', 'text','rule-hit','text'), ('.rule.hit .rule-what', 'muted','rule-hit','text'),
 ('.rule-hits count', 'text','border-strong','text'),
 ('TODAY call-only .rule-id (opacity .6)', fgop('text','',0.6), 'rule-0.6', 'text'),
 ('TODAY call-only .rule-what (opacity .6)', fgop('muted','',0.6), 'rule-0.6', 'text'),
 ('TODAY skipped .rule-id (opacity .45)', fgop('text','',0.45), 'rule-0.45', 'text'),
 ('TODAY skipped .rule-what (opacity .45)', fgop('muted','',0.45), 'rule-0.45', 'text'),
 ('FIX call-only/skipped .rule-id', 'muted','surface-2','text'), ('FIX call-only/skipped .rule-what', 'dim','surface-2','text'),
 # --- neuronet-arena (upstream only: neuronet-arena/styles.css) ---
 ('.meta .legend .readout', 'muted','surface','text'), ('.tiles dt', 'muted','surface-2','text'),
 ('.hud text', 'muted','hud','text'), ('.parity p (#c4ccd6)', 'soft','surface','text'),
 ('.explain p (#b9c2cd)', 'explain','bg','text'), ('td.pending TODAY --faint', 'faint','surface','text'),
 ('td.pending FIX --dim', 'dim','surface','text'), ('btn-primary', 'bg','amber','text'),
 ('btn-primary hover (#ffc370)', 'bg','amber-hover-nn','text'),
 ('chart line --series-1 #c98500', '#c98500','surface','ui'), ('chart line --series-2 #22a392', '#22a392','surface','ui'),
 ('--blue #63a9f0 marks', '#63a9f0','surface','ui'),
 # --- non-text boundaries (SC 1.4.11 applies only where the boundary is the sole cue) ---
 ('--border vs page', 'border','bg','deco'), ('--border-strong vs page', 'border-strong','bg','deco'),
 ('--border-strong vs card', 'border-strong','surface','deco'), ('--faint as control edge vs card', 'faint','surface','ui'),
]
BAR = {'text':4.5,'large':3.0,'ui':3.0,'deco':None}
print('| # | Where | FG | BG | Ratio | Needs | Result |'); print('|---|---|---|---|---|---|---|')
fails = 0
for i, (w, fg, bg, k) in enumerate(P, 1):
    f, b = col(fg), col(bg); r = ratio(f, b); bar = BAR[k]
    res = 'n/a (decorative)' if bar is None else ('pass' if r >= bar else 'FAIL')
    fails += res == 'FAIL'
    fgl = f'`--{fg}` {f}' if fg in T else f'{f}'; bgl = f'`--{bg}` {b}' if bg in T else f'{bg} {b}'
    print(f'| {i} | {w} | {fgl} | {bgl} | {r:.2f} | {bar or "—"} | {res} |')
print(f'\n{fails} pairs below their bar. Composites: ' + ', '.join(f'{k} {v}' for k, v in C.items()))
