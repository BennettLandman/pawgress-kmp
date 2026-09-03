"""
Generates the interface illustrations used by docs/manual.html.

These are hand-built SVG recreations of the real screens -- not device
captures. Every colour below is lifted from ui/theme/Theme.kt and every label
from the actual composables, so the pictures stay honest about what the app
looks like. Re-run this script after a UI change: python3 _gen_mockups.py
"""
import os

W, H = 390, 844
FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif"

C = dict(
    bg="#FCFAF6", on="#2A322E", muted="#4C574F", outline="#7C8880",
    outlineV="#D3DDD6", primary="#B96756", onPrimary="#FFFFFF",
    primaryC="#F1D5D0", onPrimaryC="#46180F", secondary="#978DAE",
    secondaryC="#E7E1ED", tertiary="#6E7F76", tertiaryC="#E8EEE9",
    surfaceV="#E8EEE9", chip="#FAF7F0", ink="#34403B", white="#FFFFFF",
)
GROUP = dict(upper="#B96756", core="#6E7F76", lower="#978DAE", other="#8A968E")
DIFF = dict(very_easy="#6E9E78", easy="#A3C4A0", about="#C9BE8E",
            hard="#E0954F", very_hard="#C1543A")


def esc(s):
    return (str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def t(x, y, s, size=12, fill=None, anchor="start", weight="400", op=1):
    fill = fill or C["on"]
    return (f'<text x="{x}" y="{y}" font-family="{FONT}" font-size="{size}" '
            f'fill="{fill}" text-anchor="{anchor}" font-weight="{weight}" '
            f'opacity="{op}">{esc(s)}</text>')


def rr(x, y, w, h, r=12, fill="none", stroke="none", sw=1):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>')


def circ(cx, cy, r, fill, stroke="none", sw=1):
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="{sw}"/>')


def ell(cx, cy, rx, ry, fill, rot=0):
    tr = f' transform="rotate({rot} {cx} {cy})"' if rot else ''
    return f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{fill}"{tr}/>'


def ln(x1, y1, x2, y2, stroke, sw=1, cap="round"):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{stroke}" '
            f'stroke-width="{sw}" stroke-linecap="{cap}"/>')


def pth(d, fill="none", stroke="none", sw=1, cap="round", join="round"):
    return (f'<path d="{d}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" '
            f'stroke-linecap="{cap}" stroke-linejoin="{join}"/>')


def paw(cx, cy, s=1.0, fill=None):
    """The paw mark from the app icon. Four fat toes and a pad rather than the
    icon's finer shape -- at 11px wide in a top-bar chip, anything more
    detailed collapses into a blob."""
    f = fill or C["muted"]
    return "".join([
        ell(cx - 4.6 * s, cy - 2.2 * s, 1.9 * s, 2.4 * s, f, -14),
        ell(cx - 1.6 * s, cy - 4.3 * s, 1.9 * s, 2.5 * s, f),
        ell(cx + 1.6 * s, cy - 4.3 * s, 1.9 * s, 2.5 * s, f),
        ell(cx + 4.6 * s, cy - 2.2 * s, 1.9 * s, 2.4 * s, f, 14),
        ell(cx, cy + 2.4 * s, 4.2 * s, 3.4 * s, f),
    ])


def cat(cx, cy, r, body="#C9BEA8", ink="#3A3630"):
    """A simple cat portrait standing in for the mascot artwork."""
    return "".join([
        pth(f"M{cx-r*0.72},{cy-r*0.34} L{cx-r*0.86},{cy-r*1.06} "
            f"L{cx-r*0.18},{cy-r*0.72} Z", fill=body),
        pth(f"M{cx+r*0.72},{cy-r*0.34} L{cx+r*0.86},{cy-r*1.06} "
            f"L{cx+r*0.18},{cy-r*0.72} Z", fill=body),
        ell(cx, cy, r * 0.92, r * 0.82, body),
        ell(cx - r * 0.32, cy - r * 0.08, r * 0.1, r * 0.14, ink),
        ell(cx + r * 0.32, cy - r * 0.08, r * 0.1, r * 0.14, ink),
        ell(cx, cy + r * 0.22, r * 0.09, r * 0.07, ink),
        ln(cx, cy + r * 0.3, cx - r * 0.18, cy + r * 0.44, ink, 1.4),
        ln(cx, cy + r * 0.3, cx + r * 0.18, cy + r * 0.44, ink, 1.4),
    ])


def gear(cx, cy, r, fill):
    out = [circ(cx, cy, r * 0.62, "none", fill, r * 0.34)]
    import math
    for i in range(8):
        a = math.radians(i * 45)
        x1, y1 = cx + math.cos(a) * r * 0.78, cy + math.sin(a) * r * 0.78
        x2, y2 = cx + math.cos(a) * r * 1.15, cy + math.sin(a) * r * 1.15
        out.append(ln(x1, y1, x2, y2, fill, r * 0.34))
    return "".join(out)


def back_arrow(x, y, col=None):
    col = col or C["on"]
    return pth(f"M{x+7},{y-6} L{x},{y} L{x+7},{y+6}", stroke=col, sw=1.8)


def check(cx, cy, s, col):
    return pth(f"M{cx-s*0.5},{cy} L{cx-s*0.1},{cy+s*0.42} L{cx+s*0.55},{cy-s*0.42}",
               stroke=col, sw=s * 0.34)


def status_bar():
    return "".join([
        t(24, 34, "9:41", 12.5, C["on"], weight="600"),
        rr(330, 25, 22, 11, 3, "none", C["on"], 1.1),
        rr(332, 27, 15, 7, 1.5, C["on"]),
        rr(353, 28, 2, 5, 1, C["on"]),
        pth("M300,33 L306,26 L312,33", stroke=C["on"], sw=1.6),
        pth("M283,33 L288,27 L293,33", stroke=C["on"], sw=1.6),
    ])


def frame(body, label=""):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
            f'viewBox="0 0 {W} {H}" role="img" aria-label="{esc(label)}">'
            f'<rect width="{W}" height="{H}" rx="30" fill="{C["bg"]}"/>'
            f'{status_bar()}{body}'
            f'<rect x="0.6" y="0.6" width="{W-1.2}" height="{H-1.2}" rx="30" '
            f'fill="none" stroke="{C["outlineV"]}" stroke-width="1.2"/>'
            f'<rect x="{W/2-58}" y="{H-14}" width="116" height="4.5" rx="2.3" '
            f'fill="{C["outline"]}" opacity="0.5"/></svg>')


def chips(x, y, items, selected, gap=7, ph=11, fs=11.5):
    """A row of filter chips; returns (svg, total_width)."""
    out, cx = [], x
    for lab in items:
        w = max(30, len(lab) * 6.4 + ph * 2)
        sel = lab == selected
        out.append(rr(cx, y, w, 27, 13.5,
                      C["secondaryC"] if sel else "none",
                      "none" if sel else C["outlineV"], 1))
        out.append(t(cx + w / 2, y + 17.6, lab, fs,
                     C["on"] if sel else C["muted"], "middle",
                     "600" if sel else "400"))
        cx += w + gap
    return "".join(out), cx - x - gap


def card(x, y, w, h, fill=None, r=14):
    return rr(x, y, w, h, r, fill or C["white"], C["outlineV"], 1)


OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "img")
os.makedirs(OUT, exist_ok=True)


def write(name, svg):
    p = os.path.join(OUT, name)
    with open(p, "w") as f:
        f.write(svg)
    print(f"  wrote img/{name}  ({len(svg)} bytes)")


# ------------------------------------------------------------------ 1. grid
def machine_glyph(cx, cy, kind):
    """Small abstract equipment marks, in the spirit of MachineArt."""
    i = C["ink"]
    if kind == 0:      # bench press
        return (ln(cx - 15, cy - 6, cx + 15, cy - 6, i, 2.6) +
                circ(cx - 15, cy - 6, 3.4, i) + circ(cx + 15, cy - 6, 3.4, i) +
                rr(cx - 9, cy + 3, 18, 5, 2.5, i))
    if kind == 1:      # lat pulldown
        return (ln(cx, cy - 13, cx, cy - 2, i, 2.2) +
                ln(cx - 11, cy - 2, cx + 11, cy - 2, i, 2.6) +
                rr(cx - 7, cy + 6, 14, 6, 2.5, i))
    if kind == 2:      # leg press
        return (rr(cx - 13, cy - 10, 11, 20, 3, i) +
                ln(cx - 1, cy + 6, cx + 13, cy - 6, i, 2.6) +
                circ(cx + 13, cy - 6, 3.2, i))
    if kind == 3:      # dumbbell
        return (ln(cx - 8, cy, cx + 8, cy, i, 2.4) +
                rr(cx - 14, cy - 6, 6, 12, 2, i) + rr(cx + 8, cy - 6, 6, 12, 2, i))
    if kind == 4:      # cable row
        return (circ(cx - 10, cy - 8, 3.2, i) +
                pth(f"M{cx-10},{cy-5} Q{cx},{cy+2} {cx+11},{cy-3}", stroke=i, sw=2) +
                rr(cx - 13, cy + 6, 26, 5, 2.5, i))
    return (circ(cx, cy - 5, 5, i) + rr(cx - 10, cy + 3, 20, 6, 3, i))


def screen_main():
    b = [
        t(20, 78, "Pawgress", 21, C["on"], weight="600"),
        # trends icon
        pth("M126,80 L132,72 L137,76 L144,66", stroke=C["muted"], sw=1.8),
        pth("M140,66 L145,66 L145,71", stroke=C["muted"], sw=1.8),
        t(20, 96, "Tue, Sep 3  ·  4 of 22 done", 11.5, C["muted"]),
        # paw balance chip
        rr(288, 62, 48, 26, 13, C["surfaceV"]),
        paw(302, 75, 0.92, C["muted"]),
        t(319, 79, "12", 12.5, C["muted"], "middle", "600"),
        gear(354, 75, 9, C["on"]),
    ]
    names = ["Chest Press", "Lat Pulldown", "Leg Press", "Shoulder Press",
             "Seated Row", "Leg Curl", "Pec Fly", "Triceps", "Ab Crunch",
             "Leg Extension", "Biceps Curl", "Calf Raise", "Hip Abduction",
             "Rear Delt", "Torso Rotation"]
    groups = ["upper", "upper", "lower", "upper", "upper", "lower",
              "upper", "upper", "core", "lower", "upper", "lower",
              "lower", "upper", "core"]
    weights = [130, 95, 210, 65, 110, 85, 70, 55, 90, 120, 40, 160, 75, 45, 100]
    done = [True, True, False, True, False, False, False, False, False,
            False, False, False, False, False, False]
    diffs = ["about", "hard", None, "easy", None, None, None, None, None,
             None, None, None, None, None, None]

    x0, y0, tw, th = 13, 112, 115, 136
    for idx in range(15):
        col, row = idx % 3, idx // 3
        x = x0 + col * (tw + 10)
        y = y0 + row * (th + 10)
        d = done[idx]
        b.append(rr(x, y, tw, th, 15,
                    C["primaryC"] if d else C["white"],
                    "none" if d else C["outlineV"], 1))
        # body-area dot
        b.append(circ(x + tw - 11, y + 11, 3.6, GROUP[groups[idx]]))
        # artwork chip
        b.append(rr(x + 11, y + 10, tw - 22, 58, 11, C["chip"]))
        b.append(machine_glyph(x + tw / 2, y + 39, idx % 6))
        # weight
        wy = y + 96
        b.append(t(x + 11, wy, str(weights[idx]), 25,
                   C["onPrimaryC"] if d else C["on"], weight="600"))
        b.append(t(x + 11 + len(str(weights[idx])) * 14.6, wy, "lb", 11,
                   C["muted"]))
        if diffs[idx]:
            b.append(circ(x + tw - 15, wy - 8, 4, DIFF[diffs[idx]]))
        b.append(t(x + 11, y + 116, names[idx], 10.5, C["muted"]))
        if d:
            b.append(circ(x + tw - 20, y + 122, 8.5, C["primary"]))
            b.append(check(x + tw - 20, y + 122, 9, C["onPrimary"]))
    return frame("".join(b), "Pawgress main grid")


# -------------------------------------------------------------- 2. log sheet
def screen_log():
    b = [
        # dimmed grid peeking above the sheet
        rr(0, 0, W, 150, 30, "#EDE9E1"),
        t(20, 78, "Pawgress", 21, "#B9B4AA", weight="600"),
        t(20, 96, "Tue, Sep 3  ·  4 of 22 done", 11.5, "#C4BFB5"),
        rr(0, 132, W, H - 132, 26, C["bg"]),
        rr(W / 2 - 20, 144, 40, 4.5, 2.3, C["outlineV"]),
        # hero artwork
        rr(W / 2 - 88, 164, 176, 150, 22, C["chip"]),
        ln(W / 2 - 46, 232, W / 2 + 46, 232, C["ink"], 7),
        circ(W / 2 - 52, 232, 10, C["ink"]), circ(W / 2 + 52, 232, 10, C["ink"]),
        rr(W / 2 - 30, 250, 60, 15, 7, C["ink"]),
        rr(W / 2 - 9, 205, 18, 30, 6, C["ink"]),
        t(W / 2, 336, "Chest Press", 17, C["on"], "middle", "600"),
        # step buttons + weight
        circ(58, 386, 27, "none", C["outline"], 1.4),
        t(58, 393, "−5", 19, C["on"], "middle", "500"),
        t(W / 2 - 12, 400, "130", 45, C["on"], "middle", "600"),
        t(W / 2 + 46, 400, "lb", 17, C["muted"], "middle"),
        circ(332, 386, 27, "none", C["outline"], 1.4),
        t(332, 393, "+5", 19, C["on"], "middle", "500"),
        # slider
        ln(28, 434, 362, 434, C["outlineV"], 4),
        ln(28, 434, 168, 434, C["primary"], 4),
        circ(168, 434, 10, C["primary"]),
        t(28, 456, "10", 10.5, C["muted"]),
        t(362, 456, "300", 10.5, C["muted"], "end"),
        t(28, 490, "How did it feel? (optional)", 12.5, C["muted"], weight="500"),
    ]
    labels = [("Very Easy", "very_easy"), ("Easy", "easy"),
              ("About Right", "about"), ("Hard", "hard"), ("Very Hard", "very_hard")]
    cx = 28
    for lab, key in labels:
        w = len(lab) * 5.5 + 16
        sel = key == "about"
        b.append(rr(cx, 502, w, 26, 13, DIFF[key] if sel else "none",
                    "none" if sel else C["outlineV"], 1))
        b.append(t(cx + w / 2, 519, lab, 10, "#2F2A22" if sel else C["muted"],
                   "middle", "600" if sel else "400"))
        cx += w + 6
    b += [
        rr(28, 556, 334, 54, 27, C["primary"]),
        t(W / 2, 590, "Confirm 130 lb", 17, C["onPrimary"], "middle", "600"),
        t(W / 2, 634, "Undo today's entry", 13.5, C["primary"], "middle"),
    ]
    return frame("".join(b), "Logging a lift")


# -------------------------------------------------------------- 3. fun facts
def screen_funfacts():
    b = [back_arrow(24, 76), t(46, 82, "Fun Facts", 19, C["on"], weight="600")]
    ch, _ = chips(20, 102, ["Today", "This Week", "This Month", "∞"], "This Week")
    b.append(ch)
    # mascot card
    b += [card(16, 146, 358, 176, C["tertiaryC"]),
          cat(84, 226, 46),
          t(84, 296, "Coach Moose", 12, C["muted"], "middle", "600")]
    bx, by, bw, bh = 146, 176, 212, 104
    b += [rr(bx, by, bw, bh, 15, C["white"], C["outlineV"], 1),
          pth(f"M{bx},{by+44} L{bx-11},{by+54} L{bx},{by+64} Z",
              fill=C["white"], stroke=C["outlineV"], sw=1),
          t(bx + 15, by + 30, "Four days running now.", 13, C["on"], weight="500"),
          t(bx + 15, by + 50, "That is what steady", 13, C["on"], weight="500"),
          t(bx + 15, by + 70, "looks like.", 13, C["on"], weight="500")]
    b.append(t(20, 340, "Just for fun — your coach's line is picked at", 10.5, C["muted"]))
    b.append(t(20, 354, "random and isn't training advice.", 10.5, C["muted"]))
    # streak
    b += [card(16, 370, 358, 78, C["secondaryC"]),
          t(32, 402, "4 day streak", 19, C["on"], weight="600"),
          t(32, 424, "Consecutive gym days with at least one lift logged.",
            10.5, C["muted"])]
    facts = [("12 lifts", "That's 12 confirmed lifts this week."),
             ("Chest Press", "Logged 3 times — your favorite this week."),
             ("4,180 lb", "Total weight moved this week.")]
    y = 462
    for head, sub in facts:
        b += [card(16, y, 358, 70),
              t(32, y + 30, head, 17, C["on"], weight="600"),
              t(32, y + 51, sub, 11, C["muted"])]
        y += 80
    return frame("".join(b), "Fun Facts screen")


# ----------------------------------------------------------------- 4. trends
def screen_trends():
    b = [back_arrow(24, 76), t(46, 82, "Trends", 19, C["on"], weight="600")]
    ch, _ = chips(20, 102, ["This Week", "This Month", "This Year", "∞"], "This Month")
    b.append(ch)
    # bar chart
    b += [card(16, 146, 358, 176),
          t(32, 176, "Weight lifted per week", 13, C["on"], weight="600")]
    bars = [0.42, 0.61, 0.55, 0.78, 0.68, 0.92]
    labs = ["Aug 4", "Aug 11", "Aug 18", "Aug 25", "Sep 1", "Sep 8"]
    bw, gap, base, top = 40, 15, 288, 196
    for i, v in enumerate(bars):
        x = 34 + i * (bw + gap)
        h = (base - top) * v
        b.append(rr(x, base - h, bw, h, 6,
                    C["primary"] if i == len(bars) - 1 else C["secondary"]))
        b.append(t(x + bw / 2, base + 16, labs[i], 8.5, C["muted"], "middle"))
    # biggest gains
    b += [card(16, 336, 358, 148),
          t(32, 366, "Biggest gains this range", 13, C["on"], weight="600")]
    gains = [("Leg Press", "+25 lb", "lower"), ("Chest Press", "+15 lb", "upper"),
             ("Seated Row", "+10 lb", "upper")]
    y = 396
    for name, delta, grp in gains:
        b += [circ(36, y - 4, 4, GROUP[grp]),
              t(50, y, name, 12.5, C["on"]),
              t(358, y, delta, 12.5, C["tertiary"], "end", "600")]
        y += 30
    # overlay line chart
    b += [card(16, 498, 358, 216),
          t(32, 528, "Relative progress, this range", 13, C["on"], weight="600")]
    dr, _ = chips(32, 542, ["All", "Upper", "Lower"], "All", gap=6, ph=9, fs=10)
    b.append(dr)
    gx, gy, gw, gh = 40, 590, 316, 96
    for i in range(4):
        yy = gy + gh * i / 3
        b.append(ln(gx, yy, gx + gw, yy, C["outlineV"], 1))
    series = [([0, .18, .30, .28, .46, .60], C["primary"]),
              ([0, .10, .22, .34, .40, .52], C["secondary"]),
              ([0, .06, .12, .16, .26, .32], C["tertiary"])]
    for pts, col in series:
        d = " ".join(
            f"{'M' if i == 0 else 'L'}{gx + gw * i / (len(pts) - 1):.1f},"
            f"{gy + gh - gh * v:.1f}" for i, v in enumerate(pts))
        b.append(pth(d, stroke=col, sw=2.4))
        b.append(circ(gx + gw, gy + gh - gh * pts[-1], 3.4, col))
    b.append(t(40, 706, "Each line starts at its own first weight in range.",
               10, C["muted"]))
    return frame("".join(b), "Trends screen")


# ---------------------------------------------------------------- 5. coaches
def screen_coaches():
    b = [t(20, 82, "Coaches", 19, C["on"], weight="600"),
         rr(288, 66, 48, 26, 13, C["surfaceV"]),
         paw(302, 79, 0.92, C["muted"]),
         t(319, 83, "12", 12.5, C["muted"], "middle", "600")]
    # banner of coaches
    b.append(rr(16, 104, 358, 84, 16, C["tertiaryC"]))
    for i in range(6):
        b.append(cat(48 + i * 60, 146, 24,
                     ["#C9BEA8", "#A8B6AE", "#C6B0A0", "#B3A9C2", "#D0C2A6", "#9FB3A8"][i]))
    b += [t(20, 210, "Earn one pawprint per machine, per gym day, and", 11.5, C["muted"]),
          t(20, 226, "spend them here on new coaches and outfits.", 11.5, C["muted"]),
          t(20, 250, "Coaches are cosmetic and just for fun — cartoon cats", 10.5, C["muted"]),
          t(20, 264, "whose encouragement is picked at random, not real", 10.5, C["muted"]),
          t(20, 278, "trainers, and not advice about what to lift.", 10.5, C["muted"])]
    cards_ = [("Coach Moose", "Maine Coon", "Gentle giant — patient, steady hype",
               "selected", "#C9BEA8"),
              ("Coach Noodle", "Ragdoll", "Zen, floppy-chill — consistency first",
               "unlocked", "#A8B6AE"),
              ("Duchess Marmalade", "Persian", "Glamorous diva — red-carpet energy",
               "locked", "#C6B0A0")]
    y = 298
    for name, breed, personality, state, col in cards_:
        b += [card(16, y, 358, 132), cat(56, y + 42, 26, col),
              t(94, y + 34, name, 14, C["on"], weight="600"),
              t(94, y + 52, breed, 10.5, C["muted"]),
              t(32, y + 78, personality, 11, C["on"])]
        if state == "selected":
            b.append(check(38, y + 104, 11, C["primary"]))
            b.append(t(50, y + 108, "Selected", 12.5, C["primary"], weight="600"))
        elif state == "unlocked":
            b += [rr(32, y + 92, 190, 30, 15, "none", C["outline"], 1.2),
                  t(127, y + 112, "Select Coach Noodle", 11.5, C["on"], "middle", "500")]
        else:
            b += [rr(32, y + 92, 150, 30, 15, C["primary"]),
                  t(97, y + 112, "Unlock for 15", 11.5, C["onPrimary"], "middle", "600"),
                  paw(160, y + 107, 0.8, C["onPrimary"])]
        b += [t(358, y + 108, "Outfits", 11.5, C["secondary"], "end", "500")]
        y += 142
    return frame("".join(b), "Coaches screen")


# --------------------------------------------------------------- 6. settings
def screen_settings():
    b = [back_arrow(24, 76), t(46, 82, "Settings", 19, C["on"], weight="600")]
    # google card
    b += [card(16, 104, 358, 150),
          t(32, 134, "Google account", 13, C["on"], weight="600"),
          circ(40, 164, 11, C["secondaryC"]),
          t(40, 168, "B", 12, C["secondary"], "middle", "600"),
          t(60, 162, "balandman@gmail.com", 12, C["on"]),
          t(60, 178, "Last synced 2 minutes ago", 10.5, C["muted"])]
    bx = 32
    for lab, w in [("Sync now", 82), ("Open sheet", 92), ("Switch account", 108)]:
        b += [rr(bx, 202, w, 30, 15, "none", C["outline"], 1.2),
              t(bx + w / 2, 222, lab, 10.5, C["on"], "middle")]
        bx += w + 8
    # restore
    b += [card(16, 268, 358, 56),
          t(32, 302, "Restore from Google Sheet", 13, C["on"], weight="500"),
          pth("M348,296 L354,302 L348,308", stroke=C["muted"], sw=1.6)]
    # machines
    b += [t(20, 356, "Machines", 13, C["on"], weight="600"),
          t(300, 356, "Show all", 11.5, C["primary"], "end"),
          t(358, 356, "Hide all", 11.5, C["primary"], "end")]
    rows = [("Chest Press", "upper", True), ("Lat Pulldown", "upper", True),
            ("Leg Press", "lower", True), ("Ab Crunch", "core", False)]
    y = 372
    for name, grp, shown in rows:
        b += [card(16, y, 358, 52), circ(38, y + 26, 4.4, GROUP[grp]),
              t(54, y + 31, name, 12.5, C["on"] if shown else C["muted"])]
        b += [rr(320, y + 16, 36, 20, 10,
                 C["primary"] if shown else C["outlineV"]),
              circ(346 if shown else 330, y + 26, 8, C["white"])]
        y += 60
    b += [rr(16, y + 4, 358, 46, 14, "none", C["outline"], 1.3),
          t(W / 2, y + 33, "+  Add a machine", 13, C["on"], "middle", "500")]
    y += 66
    b += [rr(16, y + 4, 172, 44, 14, "none", C["outline"], 1.2),
          t(102, y + 32, "Reset today", 12.5, C["on"], "middle"),
          rr(202, y + 4, 172, 44, 14, "none", "#A03A2C", 1.2),
          t(288, y + 32, "Full reset", 12.5, "#A03A2C", "middle")]
    return frame("".join(b), "Settings screen")


if __name__ == "__main__":
    print("Generating interface illustrations:")
    write("screen-main.svg", screen_main())
    write("screen-log.svg", screen_log())
    write("screen-funfacts.svg", screen_funfacts())
    write("screen-trends.svg", screen_trends())
    write("screen-coaches.svg", screen_coaches())
    write("screen-settings.svg", screen_settings())
    print("Done.")
