#!/usr/bin/env fontforge -script
"""
isiBheqe Font Generator (CircleOne)
====================================
Generates the CircleOne TrueType font for the isiBheqe SoNtu writing system.

Each glyph encodes a complete CV syllable, pure vowel, or syllabic nasal
using Unicode Private Use Area (PUA) codepoints starting at U+E000.

The geometric design follows isiBheqe SoNtu conventions:
  - Vowels define the outer shape (circle, half-circle, triangle)
  - Consonants define the inner mark or stroke pattern
  - Syllabic nasals (amaQanda) are standalone circle forms

Usage:
    fontforge -script generate_glyphs.py

Output:
    one.ttf           -- TrueType font file
    one-pua-map.csv   -- PUA codepoint mapping

Requirements:
    FontForge with Python scripting support (20230101+)

Author: The Other Bhengu (Pty) Ltd t/a The Geek.
License: See repository LICENSE
"""

import fontforge
import csv
import math
import os

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

PUA_START = 0xE000

FONT_NAME = "CircleOne"
FONT_FAMILY = "CircleOne"
FONT_FULLNAME = "CircleOne isiBheqe"
FONT_VERSION = "1.0.0"
FONT_COPYRIGHT = "Copyright (c) 2026 The Other Bhengu (Pty) Ltd t/a The Geek. isiBheqe SoNtu script."

EM_SIZE = 1000          # Units per em
ASCENT = 800
DESCENT = 200
GLYPH_WIDTH = 1000      # Monospaced advance width

# Geometric parameters for glyph drawing
CENTER_X = 500
CENTER_Y = 400          # Optical center (slightly above baseline midpoint)
RADIUS = 350            # Outer shape radius
INNER_RADIUS = 200      # Inner consonant mark radius
STROKE_WIDTH = 80

# ---------------------------------------------------------------------------
# Vowel Shapes -- the 8 isiBheqe vowel forms
# ---------------------------------------------------------------------------
# Each vowel has:
#   name      -- isiBheqe name
#   latin     -- Latin input key
#   ipa       -- IPA representation
#   codepoint -- PUA codepoint (for standalone pure vowel)

VOWELS = [
    {"name": "isoka",           "latin": "a",  "ipa": "a",  "codepoint": 0xE000},
    {"name": "iphambili",       "latin": "e",  "ipa": "e",  "codepoint": 0xE001},
    {"name": "intombi",         "latin": "i",  "ipa": "i",  "codepoint": 0xE002},
    {"name": "imuva",           "latin": "o",  "ipa": "o",  "codepoint": 0xE003},
    {"name": "umkhonto",        "latin": "u",  "ipa": "u",  "codepoint": 0xE004},
    {"name": "isoka_long",      "latin": "aa", "ipa": "a:", "codepoint": 0xE005},
    {"name": "iphambili_long",  "latin": "ee", "ipa": "e:", "codepoint": 0xE006},
    {"name": "intombi_long",    "latin": "ii", "ipa": "i:", "codepoint": 0xE007},
]

# Short vowels used in CV combinations (first 5 only)
SHORT_VOWELS = VOWELS[:5]

# ---------------------------------------------------------------------------
# Consonant Graphemes
# ---------------------------------------------------------------------------
# Each consonant has:
#   latin      -- input key(s)
#   ipa        -- IPA transcription
#   base_cp    -- base codepoint for the _a variant (other vowels follow at +1..+4)
#   group      -- phonetic grouping

CONSONANTS = [
    # Plosives
    {"latin": "b",    "ipa": "b",    "base_cp": 0xE010, "group": "plosive"},
    {"latin": "p",    "ipa": "p",    "base_cp": 0xE018, "group": "plosive"},
    {"latin": "ph",   "ipa": "ph",   "base_cp": 0xE020, "group": "plosive"},
    {"latin": "d",    "ipa": "d",    "base_cp": 0xE028, "group": "plosive"},
    {"latin": "t",    "ipa": "t",    "base_cp": 0xE030, "group": "plosive"},
    {"latin": "th",   "ipa": "th",   "base_cp": 0xE038, "group": "plosive"},
    {"latin": "g",    "ipa": "g",    "base_cp": 0xE040, "group": "plosive"},
    {"latin": "k",    "ipa": "k",    "base_cp": 0xE048, "group": "plosive"},
    {"latin": "kh",   "ipa": "kh",   "base_cp": 0xE050, "group": "plosive"},
    # Fricatives
    {"latin": "f",    "ipa": "f",    "base_cp": 0xE060, "group": "fricative"},
    {"latin": "v",    "ipa": "v",    "base_cp": 0xE068, "group": "fricative"},
    {"latin": "s",    "ipa": "s",    "base_cp": 0xE070, "group": "fricative"},
    {"latin": "z",    "ipa": "z",    "base_cp": 0xE078, "group": "fricative"},
    {"latin": "sh",   "ipa": "sh",   "base_cp": 0xE080, "group": "fricative"},
    {"latin": "h",    "ipa": "h",    "base_cp": 0xE088, "group": "fricative"},
    {"latin": "hl",   "ipa": "hl",   "base_cp": 0xE090, "group": "fricative"},
    {"latin": "dl",   "ipa": "dl",   "base_cp": 0xE098, "group": "fricative"},
    # Nasals
    {"latin": "m",    "ipa": "m",    "base_cp": 0xE0A0, "group": "nasal"},
    {"latin": "n",    "ipa": "n",    "base_cp": 0xE0A8, "group": "nasal"},
    {"latin": "ng",   "ipa": "ng",   "base_cp": 0xE0B0, "group": "nasal"},
    {"latin": "ny",   "ipa": "ny",   "base_cp": 0xE0B8, "group": "nasal"},
    # Liquids
    {"latin": "l",    "ipa": "l",    "base_cp": 0xE0C0, "group": "liquid"},
    {"latin": "r",    "ipa": "r",    "base_cp": 0xE0C8, "group": "liquid"},
    # Approximants
    {"latin": "w",    "ipa": "w",    "base_cp": 0xE0D0, "group": "approximant"},
    {"latin": "y",    "ipa": "j",    "base_cp": 0xE0D8, "group": "approximant"},
    # Implosive
    {"latin": "bh",   "ipa": "bh",   "base_cp": 0xE0E0, "group": "implosive"},
    # Prenasalized
    {"latin": "mb",   "ipa": "mb",   "base_cp": 0xE0E8, "group": "prenasalized"},
    {"latin": "nd",   "ipa": "nd",   "base_cp": 0xE0F0, "group": "prenasalized"},
    {"latin": "nt",   "ipa": "nt",   "base_cp": 0xE0F8, "group": "prenasalized"},
    {"latin": "nk",   "ipa": "nk",   "base_cp": 0xE100, "group": "prenasalized"},
    {"latin": "nj",   "ipa": "ndz",  "base_cp": 0xE108, "group": "prenasalized"},
    {"latin": "ntsh", "ipa": "ntsh", "base_cp": 0xE110, "group": "prenasalized"},
    # Affricates
    {"latin": "j",    "ipa": "dz",   "base_cp": 0xE120, "group": "affricate"},
    {"latin": "tsh",  "ipa": "tsh",  "base_cp": 0xE128, "group": "affricate"},
    # Clicks -- dental (c)
    {"latin": "c",    "ipa": "!",    "base_cp": 0xE140, "group": "click_dental"},
    {"latin": "ch",   "ipa": "!h",   "base_cp": 0xE148, "group": "click_dental"},
    {"latin": "gc",   "ipa": "g!",   "base_cp": 0xE150, "group": "click_dental"},
    {"latin": "nc",   "ipa": "n!",   "base_cp": 0xE158, "group": "click_dental"},
    # Clicks -- palatal (q)
    {"latin": "q",    "ipa": "||",   "base_cp": 0xE160, "group": "click_palatal"},
    {"latin": "qh",   "ipa": "||h",  "base_cp": 0xE168, "group": "click_palatal"},
    {"latin": "gq",   "ipa": "g||",  "base_cp": 0xE170, "group": "click_palatal"},
    {"latin": "nq",   "ipa": "n||",  "base_cp": 0xE178, "group": "click_palatal"},
    # Clicks -- lateral (x)
    {"latin": "x",    "ipa": "||x",  "base_cp": 0xE180, "group": "click_lateral"},
    {"latin": "xh",   "ipa": "||xh", "base_cp": 0xE188, "group": "click_lateral"},
    {"latin": "gx",   "ipa": "g||x", "base_cp": 0xE190, "group": "click_lateral"},
    {"latin": "nx",   "ipa": "n||x", "base_cp": 0xE198, "group": "click_lateral"},
    # --- Additional prenasalized ---
    {"latin": "nz",   "ipa": "nz",   "base_cp": 0xE1C0, "group": "prenasalized"},
    {"latin": "mf",   "ipa": "mf",   "base_cp": 0xE1C8, "group": "prenasalized"},
    {"latin": "mv",   "ipa": "mv",   "base_cp": 0xE1D0, "group": "prenasalized"},
    {"latin": "mph",  "ipa": "mph",  "base_cp": 0xE1D8, "group": "prenasalized"},
    {"latin": "ndl",  "ipa": "ndl",  "base_cp": 0xE1E0, "group": "prenasalized"},
    {"latin": "nhl",  "ipa": "nhl",  "base_cp": 0xE1E8, "group": "prenasalized"},
    {"latin": "ntl",  "ipa": "ntl",  "base_cp": 0xE1F0, "group": "prenasalized"},
    {"latin": "ns",   "ipa": "ns",   "base_cp": 0xE1F8, "group": "prenasalized"},
    # --- Labialized plosives ---
    {"latin": "bw",   "ipa": "bw",   "base_cp": 0xE200, "group": "labialized"},
    {"latin": "pw",   "ipa": "pw",   "base_cp": 0xE208, "group": "labialized"},
    {"latin": "phw",  "ipa": "phw",  "base_cp": 0xE210, "group": "labialized"},
    {"latin": "dw",   "ipa": "dw",   "base_cp": 0xE218, "group": "labialized"},
    {"latin": "tw",   "ipa": "tw",   "base_cp": 0xE220, "group": "labialized"},
    {"latin": "thw",  "ipa": "thw",  "base_cp": 0xE228, "group": "labialized"},
    {"latin": "gw",   "ipa": "gw",   "base_cp": 0xE230, "group": "labialized"},
    {"latin": "kw",   "ipa": "kw",   "base_cp": 0xE238, "group": "labialized"},
    {"latin": "khw",  "ipa": "khw",  "base_cp": 0xE240, "group": "labialized"},
    # --- Labialized fricatives ---
    {"latin": "fw",   "ipa": "fw",   "base_cp": 0xE248, "group": "labialized"},
    {"latin": "vw",   "ipa": "vw",   "base_cp": 0xE250, "group": "labialized"},
    {"latin": "sw",   "ipa": "sw",   "base_cp": 0xE258, "group": "labialized"},
    {"latin": "zw",   "ipa": "zw",   "base_cp": 0xE260, "group": "labialized"},
    {"latin": "shw",  "ipa": "shw",  "base_cp": 0xE268, "group": "labialized"},
    {"latin": "hw",   "ipa": "hw",   "base_cp": 0xE270, "group": "labialized"},
    {"latin": "hlw",  "ipa": "hlw",  "base_cp": 0xE278, "group": "labialized"},
    {"latin": "dlw",  "ipa": "dlw",  "base_cp": 0xE280, "group": "labialized"},
    # --- Labialized nasals/liquids ---
    {"latin": "mw",   "ipa": "mw",   "base_cp": 0xE288, "group": "labialized"},
    {"latin": "nw",   "ipa": "nw",   "base_cp": 0xE290, "group": "labialized"},
    {"latin": "ngw",  "ipa": "ngw",  "base_cp": 0xE298, "group": "labialized"},
    {"latin": "nyw",  "ipa": "nyw",  "base_cp": 0xE2A0, "group": "labialized"},
    {"latin": "lw",   "ipa": "lw",   "base_cp": 0xE2A8, "group": "labialized"},
    {"latin": "rw",   "ipa": "rw",   "base_cp": 0xE2B0, "group": "labialized"},
    # --- Labialized prenasalized ---
    {"latin": "mbw",  "ipa": "mbw",  "base_cp": 0xE2B8, "group": "labialized"},
    {"latin": "ndw",  "ipa": "ndw",  "base_cp": 0xE2C0, "group": "labialized"},
    {"latin": "ntw",  "ipa": "ntw",  "base_cp": 0xE2C8, "group": "labialized"},
    {"latin": "nkw",  "ipa": "nkw",  "base_cp": 0xE2D0, "group": "labialized"},
    {"latin": "njw",  "ipa": "njw",  "base_cp": 0xE2D8, "group": "labialized"},
    {"latin": "nzw",  "ipa": "nzw",  "base_cp": 0xE2E0, "group": "labialized"},
    # --- Labialized clicks ---
    {"latin": "cw",   "ipa": "!w",   "base_cp": 0xE300, "group": "click_labialized"},
    {"latin": "qw",   "ipa": "||w",  "base_cp": 0xE308, "group": "click_labialized"},
    {"latin": "xw",   "ipa": "||xw", "base_cp": 0xE310, "group": "click_labialized"},
    {"latin": "gcw",  "ipa": "g!w",  "base_cp": 0xE318, "group": "click_labialized"},
    {"latin": "gqw",  "ipa": "g||w", "base_cp": 0xE320, "group": "click_labialized"},
    {"latin": "gxw",  "ipa": "g||xw","base_cp": 0xE328, "group": "click_labialized"},
    {"latin": "ncw",  "ipa": "n!w",  "base_cp": 0xE330, "group": "click_labialized"},
    {"latin": "nqw",  "ipa": "n||w", "base_cp": 0xE338, "group": "click_labialized"},
    {"latin": "nxw",  "ipa": "n||xw","base_cp": 0xE340, "group": "click_labialized"},
]

# ---------------------------------------------------------------------------
# Syllabic Nasals -- amaQanda (standalone circle forms)
# ---------------------------------------------------------------------------

SYLLABIC_NASALS = [
    {"latin": "m_syl",  "ipa": "m",  "codepoint": 0xE1B0, "glyph_name": "nasal_m_syllabic"},
    {"latin": "n_syl",  "ipa": "n",  "codepoint": 0xE1B1, "glyph_name": "nasal_n_syllabic"},
    {"latin": "ng_syl", "ipa": "ng", "codepoint": 0xE1B2, "glyph_name": "nasal_ng_syllabic"},
]


# ===========================================================================
# Drawing Helpers
# ===========================================================================

OUTLINE_WIDTH = 70   # Thickness of the hollow outline stroke

def _circle_cw(pen, cx, cy, r):
    """Draw a circle clockwise (outer contour)."""
    k = r * 0.5522847498
    pen.moveTo((cx, cy + r))
    pen.curveTo((cx + k, cy + r), (cx + r, cy + k), (cx + r, cy))
    pen.curveTo((cx + r, cy - k), (cx + k, cy - r), (cx, cy - r))
    pen.curveTo((cx - k, cy - r), (cx - r, cy - k), (cx - r, cy))
    pen.curveTo((cx - r, cy + k), (cx - k, cy + r), (cx, cy + r))
    pen.closePath()

def _circle_ccw(pen, cx, cy, r):
    """Draw a circle counter-clockwise (inner cutout)."""
    k = r * 0.5522847498
    pen.moveTo((cx, cy + r))
    pen.curveTo((cx - k, cy + r), (cx - r, cy + k), (cx - r, cy))
    pen.curveTo((cx - r, cy - k), (cx - k, cy - r), (cx, cy - r))
    pen.curveTo((cx + k, cy - r), (cx + r, cy - k), (cx + r, cy))
    pen.curveTo((cx + r, cy + k), (cx + k, cy + r), (cx, cy + r))
    pen.closePath()

def draw_circle(pen, cx, cy, r, num_points=64):
    """Draw a hollow circle outline."""
    _circle_cw(pen, cx, cy, r)
    _circle_ccw(pen, cx, cy, r - OUTLINE_WIDTH)


def draw_right_half_circle(pen, cx, cy, r):
    """Draw a hollow right-facing half-circle (iphambili / e vowel)."""
    k = r * 0.5522847498
    ri = r - OUTLINE_WIDTH
    ki = ri * 0.5522847498
    # Outer path (clockwise)
    pen.moveTo((cx, cy + r))
    pen.curveTo((cx + k, cy + r), (cx + r, cy + k), (cx + r, cy))
    pen.curveTo((cx + r, cy - k), (cx + k, cy - r), (cx, cy - r))
    pen.lineTo((cx, cy - ri))
    # Inner path (back up, counter-clockwise)
    pen.curveTo((cx + ki, cy - ri), (cx + ri, cy - ki), (cx + ri, cy))
    pen.curveTo((cx + ri, cy + ki), (cx + ki, cy + ri), (cx, cy + ri))
    pen.closePath()
    # Left edge (vertical bar closing the flat side)
    pen.moveTo((cx, cy + r))
    pen.lineTo((cx, cy + ri))
    pen.lineTo((cx - OUTLINE_WIDTH, cy + ri))
    pen.lineTo((cx - OUTLINE_WIDTH, cy - ri))
    pen.lineTo((cx, cy - ri))
    pen.lineTo((cx, cy - r))
    pen.lineTo((cx - OUTLINE_WIDTH, cy - r))
    pen.lineTo((cx - OUTLINE_WIDTH, cy + r))
    pen.closePath()


def draw_left_half_circle(pen, cx, cy, r):
    """Draw a hollow left-facing half-circle (imuva / o vowel)."""
    k = r * 0.5522847498
    ri = r - OUTLINE_WIDTH
    ki = ri * 0.5522847498
    # Outer path (clockwise)
    pen.moveTo((cx, cy - r))
    pen.curveTo((cx - k, cy - r), (cx - r, cy - k), (cx - r, cy))
    pen.curveTo((cx - r, cy + k), (cx - k, cy + r), (cx, cy + r))
    pen.lineTo((cx, cy + ri))
    # Inner path (back down, counter-clockwise)
    pen.curveTo((cx - ki, cy + ri), (cx - ri, cy + ki), (cx - ri, cy))
    pen.curveTo((cx - ri, cy - ki), (cx - ki, cy - ri), (cx, cy - ri))
    pen.closePath()
    # Right edge (vertical bar closing the flat side)
    pen.moveTo((cx, cy + r))
    pen.lineTo((cx, cy + ri))
    pen.lineTo((cx + OUTLINE_WIDTH, cy + ri))
    pen.lineTo((cx + OUTLINE_WIDTH, cy - ri))
    pen.lineTo((cx, cy - ri))
    pen.lineTo((cx, cy - r))
    pen.lineTo((cx + OUTLINE_WIDTH, cy - r))
    pen.lineTo((cx + OUTLINE_WIDTH, cy + r))
    pen.closePath()


def _triangle_outline(pen, p1, p2, p3, w):
    """Draw a hollow triangle outline given 3 vertices and stroke width w."""
    import math as _m
    pts = [p1, p2, p3]
    # Compute inset triangle vertices
    inner = []
    for i in range(3):
        # Get the two edges meeting at this vertex
        prev = pts[(i - 1) % 3]
        curr = pts[i]
        nxt = pts[(i + 1) % 3]
        # Edge vectors pointing inward
        e1x, e1y = prev[0] - curr[0], prev[1] - curr[1]
        e2x, e2y = nxt[0] - curr[0], nxt[1] - curr[1]
        l1 = _m.sqrt(e1x*e1x + e1y*e1y)
        l2 = _m.sqrt(e2x*e2x + e2y*e2y)
        if l1 == 0 or l2 == 0:
            inner.append(curr)
            continue
        e1x, e1y = e1x/l1, e1y/l1
        e2x, e2y = e2x/l2, e2y/l2
        # Bisector
        bx, by = e1x + e2x, e1y + e2y
        bl = _m.sqrt(bx*bx + by*by)
        if bl == 0:
            inner.append(curr)
            continue
        bx, by = bx/bl, by/bl
        # Distance along bisector to achieve offset w from edge
        sin_half = abs(e1x * by - e1y * bx)
        if sin_half < 0.01:
            inner.append(curr)
            continue
        d = w / sin_half
        inner.append((curr[0] + bx * d, curr[1] + by * d))
    # Outer triangle (clockwise)
    pen.moveTo(pts[0])
    pen.lineTo(pts[1])
    pen.lineTo(pts[2])
    pen.closePath()
    # Inner triangle (counter-clockwise cutout)
    pen.moveTo(inner[0])
    pen.lineTo(inner[2])
    pen.lineTo(inner[1])
    pen.closePath()


def draw_triangle_down(pen, cx, cy, r):
    """Draw a hollow downward-pointing triangle (intombi / i vowel)."""
    h = r * math.sqrt(3) / 2
    p1 = (cx - r, cy + h * 0.67)
    p2 = (cx + r, cy + h * 0.67)
    p3 = (cx, cy - h * 1.33)
    _triangle_outline(pen, p1, p2, p3, OUTLINE_WIDTH)


def draw_triangle_up(pen, cx, cy, r):
    """Draw a hollow upward-pointing triangle (umkhonto / u vowel)."""
    h = r * math.sqrt(3) / 2
    p1 = (cx - r, cy - h * 0.67)
    p2 = (cx + r, cy - h * 0.67)
    p3 = (cx, cy + h * 1.33)
    _triangle_outline(pen, p1, p2, p3, OUTLINE_WIDTH)


def draw_vowel_shape(pen, vowel_name, cx, cy, r):
    """Draw the outer vowel shape based on isiBheqe name."""
    if vowel_name in ("isoka", "isoka_long"):
        # Full circle
        draw_circle(pen, cx, cy, r)
        if vowel_name == "isoka_long":
            # Double circle for long vowel
            draw_circle(pen, cx, cy, r * 0.85)
    elif vowel_name in ("iphambili", "iphambili_long"):
        draw_right_half_circle(pen, cx, cy, r)
        if vowel_name == "iphambili_long":
            draw_right_half_circle(pen, cx, cy, r * 0.85)
    elif vowel_name in ("intombi", "intombi_long"):
        draw_triangle_down(pen, cx, cy, r)
        if vowel_name == "intombi_long":
            draw_triangle_down(pen, cx, cy, r * 0.85)
    elif vowel_name == "imuva":
        draw_left_half_circle(pen, cx, cy, r)
    elif vowel_name == "umkhonto":
        draw_triangle_up(pen, cx, cy, r)
    else:
        # Fallback: circle
        draw_circle(pen, cx, cy, r)


def draw_consonant_mark(pen, consonant_latin, cx, cy, r):
    """
    Draw the inner consonant grapheme mark.

    Each consonant group has a distinctive stroke pattern placed inside the
    vowel shape. This is a placeholder system -- real glyph art would be
    designed by a typographer. The generator creates distinguishable marks
    for each consonant so that the font is structurally complete and testable.
    """
    half = r * 0.4
    quarter = r * 0.2

    # Simple distinguishing marks per consonant group
    # These are structural placeholders; final art is a design task.
    group_marks = {
        # Plosives: horizontal lines at various heights
        "b":    [(cx - half, cy, cx + half, cy)],
        "p":    [(cx - half, cy + quarter, cx + half, cy + quarter)],
        "ph":   [(cx - half, cy + quarter, cx + half, cy + quarter),
                 (cx, cy + quarter, cx, cy - quarter)],
        "d":    [(cx - half, cy - quarter, cx + half, cy - quarter)],
        "t":    [(cx - half, cy, cx + half, cy),
                 (cx, cy, cx, cy + half)],
        "th":   [(cx - half, cy, cx + half, cy),
                 (cx, cy, cx, cy - half)],
        "g":    [(cx - half, cy - half, cx + half, cy + half)],
        "k":    [(cx - half, cy + half, cx + half, cy - half)],
        "kh":   [(cx - half, cy + half, cx + half, cy - half),
                 (cx - half, cy - half, cx + half, cy + half)],
        # Fricatives: curved or angled marks
        "f":    [(cx - half, cy + half, cx, cy), (cx, cy, cx + half, cy + half)],
        "v":    [(cx - half, cy - half, cx, cy), (cx, cy, cx + half, cy - half)],
        "s":    [(cx - half, cy + quarter, cx + half, cy - quarter)],
        "z":    [(cx - half, cy - quarter, cx + half, cy + quarter)],
        "sh":   [(cx - half, cy + quarter, cx, cy - quarter),
                 (cx, cy - quarter, cx + half, cy + quarter)],
        "h":    [(cx - quarter, cy + half, cx - quarter, cy - half),
                 (cx + quarter, cy + half, cx + quarter, cy - half)],
        "hl":   [(cx - half, cy, cx + half, cy),
                 (cx - half, cy + quarter, cx + half, cy + quarter)],
        "dl":   [(cx - half, cy, cx + half, cy),
                 (cx - half, cy - quarter, cx + half, cy - quarter)],
        # Nasals: dots or short strokes
        "m":    [(cx - quarter, cy, cx + quarter, cy)],
        "n":    [(cx, cy - quarter, cx, cy + quarter)],
        "ng":   [(cx - quarter, cy, cx + quarter, cy),
                 (cx, cy - quarter, cx, cy + quarter)],
        "ny":   [(cx - quarter, cy + quarter, cx + quarter, cy - quarter)],
        # Liquids
        "l":    [(cx, cy + half, cx, cy - half)],
        "r":    [(cx - half, cy, cx, cy + quarter), (cx, cy + quarter, cx + half, cy)],
        # Approximants
        "w":    [(cx - half, cy + half, cx - quarter, cy - half),
                 (cx - quarter, cy - half, cx + quarter, cy + half),
                 (cx + quarter, cy + half, cx + half, cy - half)],
        "y":    [(cx - half, cy + half, cx, cy),
                 (cx + half, cy + half, cx, cy),
                 (cx, cy, cx, cy - half)],
        # Implosive
        "bh":   [(cx - half, cy, cx + half, cy),
                 (cx - half, cy, cx - half, cy + quarter)],
        # Prenasalized
        "mb":   [(cx - quarter, cy, cx + quarter, cy),
                 (cx - half, cy - quarter, cx + half, cy - quarter)],
        "nd":   [(cx, cy - quarter, cx, cy + quarter),
                 (cx - half, cy - quarter, cx + half, cy - quarter)],
        "nt":   [(cx - half, cy, cx + half, cy),
                 (cx, cy, cx, cy + half),
                 (cx - quarter, cy + half, cx + quarter, cy + half)],
        "nk":   [(cx - half, cy + half, cx + half, cy - half),
                 (cx - quarter, cy, cx + quarter, cy)],
        "nj":   [(cx - half, cy, cx + half, cy),
                 (cx - quarter, cy - quarter, cx + quarter, cy + quarter)],
        "ntsh": [(cx - half, cy, cx + half, cy),
                 (cx, cy, cx, cy - half),
                 (cx - quarter, cy - half, cx + quarter, cy - half)],
        # Affricates
        "j":    [(cx - half, cy, cx + half, cy),
                 (cx + quarter, cy + quarter, cx + quarter, cy - quarter)],
        "tsh":  [(cx - half, cy, cx + half, cy),
                 (cx, cy, cx, cy + half),
                 (cx - quarter, cy, cx + quarter, cy + half)],
        # Clicks -- dental
        "c":    [(cx, cy + half, cx, cy - half),
                 (cx - quarter, cy, cx + quarter, cy)],
        "ch":   [(cx, cy + half, cx, cy - half),
                 (cx - quarter, cy + quarter, cx + quarter, cy + quarter)],
        "gc":   [(cx, cy + half, cx, cy - half),
                 (cx - half, cy - quarter, cx, cy - half)],
        "nc":   [(cx, cy + half, cx, cy - half),
                 (cx - quarter, cy, cx + quarter, cy),
                 (cx - quarter, cy + quarter, cx + quarter, cy + quarter)],
        # Clicks -- palatal
        "q":    [(cx - quarter, cy + half, cx - quarter, cy - half),
                 (cx + quarter, cy + half, cx + quarter, cy - half)],
        "qh":   [(cx - quarter, cy + half, cx - quarter, cy - half),
                 (cx + quarter, cy + half, cx + quarter, cy - half),
                 (cx - quarter, cy + quarter, cx + quarter, cy + quarter)],
        "gq":   [(cx - quarter, cy + half, cx - quarter, cy - half),
                 (cx + quarter, cy + half, cx + quarter, cy - half),
                 (cx - half, cy - quarter, cx - quarter, cy - half)],
        "nq":   [(cx - quarter, cy + half, cx - quarter, cy - half),
                 (cx + quarter, cy + half, cx + quarter, cy - half),
                 (cx - quarter, cy, cx + quarter, cy)],
        # Clicks -- lateral
        "x":    [(cx - half, cy + quarter, cx + half, cy - quarter),
                 (cx - half, cy - quarter, cx + half, cy + quarter)],
        "xh":   [(cx - half, cy + quarter, cx + half, cy - quarter),
                 (cx - half, cy - quarter, cx + half, cy + quarter),
                 (cx, cy + half, cx, cy + quarter)],
        "gx":   [(cx - half, cy + quarter, cx + half, cy - quarter),
                 (cx - half, cy - quarter, cx + half, cy + quarter),
                 (cx - half, cy, cx - half, cy - quarter)],
        "nx":   [(cx - half, cy + quarter, cx + half, cy - quarter),
                 (cx - half, cy - quarter, cx + half, cy + quarter),
                 (cx - quarter, cy, cx + quarter, cy)],
        # Additional prenasalized
        "nz":   [(cx - half, cy - quarter, cx + half, cy + quarter),
                 (cx - quarter, cy, cx + quarter, cy)],
        "mf":   [(cx - half, cy + half, cx, cy), (cx, cy, cx + half, cy + half),
                 (cx - quarter, cy, cx + quarter, cy)],
        "mv":   [(cx - half, cy - half, cx, cy), (cx, cy, cx + half, cy - half),
                 (cx - quarter, cy, cx + quarter, cy)],
        "mph":  [(cx - half, cy + half, cx, cy), (cx, cy, cx + half, cy + half),
                 (cx, cy + quarter, cx, cy - quarter)],
        "ndl":  [(cx - half, cy, cx + half, cy),
                 (cx - half, cy - quarter, cx + half, cy - quarter),
                 (cx, cy - quarter, cx, cy + quarter)],
        "nhl":  [(cx - half, cy, cx + half, cy),
                 (cx - half, cy + quarter, cx + half, cy + quarter),
                 (cx, cy - quarter, cx, cy + quarter)],
        "ntl":  [(cx - half, cy, cx + half, cy),
                 (cx, cy, cx, cy + half),
                 (cx - quarter, cy + half, cx + quarter, cy + half)],
        "ns":   [(cx - half, cy + quarter, cx + half, cy - quarter),
                 (cx - quarter, cy, cx + quarter, cy)],
    }

    # Labialized consonants: reuse base mark and add a small "w" tail
    # If the consonant ends with 'w', draw the base consonant mark then add a w-tail
    base = consonant_latin
    is_labialized = False
    if base.endswith("w") and base != "w" and len(base) > 1:
        base = base[:-1]
        is_labialized = True

    if base not in group_marks and consonant_latin in group_marks:
        base = consonant_latin
        is_labialized = False

    lines = group_marks.get(base, group_marks.get(consonant_latin, [(cx - half, cy, cx + half, cy)]))

    for line in lines:
        x1, y1, x2, y2 = line
        # Draw a thin rectangle along the line to simulate a stroke
        dx = x2 - x1
        dy = y2 - y1
        length = math.sqrt(dx * dx + dy * dy)
        if length == 0:
            continue
        # Perpendicular offset for stroke width
        sw = STROKE_WIDTH * 0.4
        nx_val = -dy / length * sw
        ny_val = dx / length * sw

        pen.moveTo((x1 + nx_val, y1 + ny_val))
        pen.lineTo((x2 + nx_val, y2 + ny_val))
        pen.lineTo((x2 - nx_val, y2 - ny_val))
        pen.lineTo((x1 - nx_val, y1 - ny_val))
        pen.closePath()

    # Add a small circle dot at bottom-right for labialized consonants
    if is_labialized:
        dot_r = STROKE_WIDTH * 0.35
        dot_cx = cx + half * 0.7
        dot_cy = cy - half * 0.7
        _circle_cw(pen, dot_cx, dot_cy, dot_r)


def draw_syllabic_nasal(pen, nasal_latin, cx, cy, r):
    """Draw a syllabic nasal glyph (amaQanda) -- a hollow circle with nasal marker."""
    # Hollow outer circle
    _circle_cw(pen, cx, cy, r * 0.6)
    _circle_ccw(pen, cx, cy, r * 0.6 - OUTLINE_WIDTH)
    # Inner nasal mark
    if nasal_latin == "m_syl":
        # Horizontal bar through center
        sw = STROKE_WIDTH * 0.5
        pen.moveTo((cx - r * 0.3, cy + sw))
        pen.lineTo((cx + r * 0.3, cy + sw))
        pen.lineTo((cx + r * 0.3, cy - sw))
        pen.lineTo((cx - r * 0.3, cy - sw))
        pen.closePath()
    elif nasal_latin == "n_syl":
        # Vertical bar through center
        sw = STROKE_WIDTH * 0.5
        pen.moveTo((cx - sw, cy + r * 0.3))
        pen.lineTo((cx + sw, cy + r * 0.3))
        pen.lineTo((cx + sw, cy - r * 0.3))
        pen.lineTo((cx - sw, cy - r * 0.3))
        pen.closePath()
    elif nasal_latin == "ng_syl":
        # Cross through center
        sw = STROKE_WIDTH * 0.35
        pen.moveTo((cx - r * 0.3, cy + sw))
        pen.lineTo((cx + r * 0.3, cy + sw))
        pen.lineTo((cx + r * 0.3, cy - sw))
        pen.lineTo((cx - r * 0.3, cy - sw))
        pen.closePath()
        pen.moveTo((cx - sw, cy + r * 0.3))
        pen.lineTo((cx + sw, cy + r * 0.3))
        pen.lineTo((cx + sw, cy - r * 0.3))
        pen.lineTo((cx - sw, cy - r * 0.3))
        pen.closePath()


# ===========================================================================
# CSV Map Builder
# ===========================================================================

def build_pua_map():
    """
    Build the complete PUA mapping as a list of dicts.
    Returns list of: {codepoint, input, ipa, glyph_name}
    """
    entries = []

    # Pure vowels
    for v in VOWELS:
        entries.append({
            "codepoint": "0x%04x" % v["codepoint"],
            "input": v["latin"],
            "ipa": v["ipa"],
            "glyph_name": "vowel_%s" % v["name"],
        })

    # CV combinations
    for c in CONSONANTS:
        for vi, v in enumerate(SHORT_VOWELS):
            cp = c["base_cp"] + vi
            cv_input = c["latin"] + v["latin"]
            cv_ipa = c["ipa"] + v["ipa"]
            glyph_name = "cv_%s_%s" % (c["latin"], v["latin"])
            entries.append({
                "codepoint": "0x%04x" % cp,
                "input": cv_input,
                "ipa": cv_ipa,
                "glyph_name": glyph_name,
            })

    # Syllabic nasals
    for sn in SYLLABIC_NASALS:
        entries.append({
            "codepoint": "0x%04x" % sn["codepoint"],
            "input": sn["latin"],
            "ipa": sn["ipa"],
            "glyph_name": sn["glyph_name"],
        })

    return entries


# ===========================================================================
# Font Generation
# ===========================================================================

def create_font():
    """Create and populate the CircleOne font."""
    font = fontforge.font()

    # Font metadata
    font.fontname = FONT_NAME
    font.familyname = FONT_FAMILY
    font.fullname = FONT_FULLNAME
    font.version = FONT_VERSION
    font.copyright = FONT_COPYRIGHT
    font.encoding = "UnicodeFull"
    font.em = EM_SIZE
    font.ascent = ASCENT
    font.descent = DESCENT

    # -----------------------------------------------------------------------
    # Create .notdef glyph
    # -----------------------------------------------------------------------
    notdef = font.createChar(-1, ".notdef")
    notdef.width = GLYPH_WIDTH
    pen = notdef.glyphPen()
    # Simple box for .notdef
    pen.moveTo((100, 0))
    pen.lineTo((100, 700))
    pen.lineTo((900, 700))
    pen.lineTo((900, 0))
    pen.closePath()
    pen.moveTo((150, 50))
    pen.lineTo((850, 50))
    pen.lineTo((850, 650))
    pen.lineTo((150, 650))
    pen.closePath()
    pen = None

    # -----------------------------------------------------------------------
    # Create space glyph
    # -----------------------------------------------------------------------
    space = font.createChar(0x0020, "space")
    space.width = GLYPH_WIDTH

    # -----------------------------------------------------------------------
    # Pure vowels
    # -----------------------------------------------------------------------
    for v in VOWELS:
        glyph_name = "vowel_%s" % v["name"]
        glyph = font.createChar(v["codepoint"], glyph_name)
        glyph.width = GLYPH_WIDTH
        pen = glyph.glyphPen()
        draw_vowel_shape(pen, v["name"], CENTER_X, CENTER_Y, RADIUS)
        pen = None

    # -----------------------------------------------------------------------
    # CV composite glyphs
    # -----------------------------------------------------------------------
    for c in CONSONANTS:
        for vi, v in enumerate(SHORT_VOWELS):
            cp = c["base_cp"] + vi
            glyph_name = "cv_%s_%s" % (c["latin"], v["latin"])
            glyph = font.createChar(cp, glyph_name)
            glyph.width = GLYPH_WIDTH
            pen = glyph.glyphPen()

            # Draw outer vowel shape
            draw_vowel_shape(pen, v["name"], CENTER_X, CENTER_Y, RADIUS)

            # Draw inner consonant mark
            draw_consonant_mark(pen, c["latin"], CENTER_X, CENTER_Y, INNER_RADIUS)

            pen = None

    # -----------------------------------------------------------------------
    # Syllabic nasals (amaQanda)
    # -----------------------------------------------------------------------
    for sn in SYLLABIC_NASALS:
        glyph = font.createChar(sn["codepoint"], sn["glyph_name"])
        glyph.width = GLYPH_WIDTH
        pen = glyph.glyphPen()
        draw_syllabic_nasal(pen, sn["latin"], CENTER_X, CENTER_Y, RADIUS)
        pen = None

    return font


def export_csv(entries, output_path):
    """Write the PUA map CSV file."""
    with open(output_path, "w") as f:
        writer = csv.writer(f, lineterminator="\n")
        writer.writerow(["codepoint", "input", "ipa", "glyph_name"])
        for e in entries:
            writer.writerow([e["codepoint"], e["input"], e["ipa"], e["glyph_name"]])


def main():
    """Main entry point -- generate font and CSV map."""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    ttf_path = os.path.join(script_dir, "one.ttf")
    csv_path = os.path.join(script_dir, "one-pua-map.csv")

    print("=" * 60)
    print("  CircleOne -- isiBheqe SoNtu Font Generator")
    print("=" * 60)

    # Build PUA map
    print("\n[1/3] Building PUA codepoint map...")
    entries = build_pua_map()
    print("      %d glyphs mapped" % len(entries))

    # Create font
    print("[2/3] Generating font glyphs...")
    font = create_font()

    # Count glyphs (excluding .notdef and space)
    glyph_count = sum(1 for _ in font.glyphs() if _.glyphname not in (".notdef", "space"))
    print("      %d glyphs created in font" % glyph_count)

    # Export
    print("[3/3] Exporting...")

    font.generate(ttf_path)
    print("      Font:  %s" % ttf_path)

    export_csv(entries, csv_path)
    print("      CSV:   %s" % csv_path)

    print("\nDone. Total PUA entries: %d" % len(entries))
    print("Codepoint range: U+%04X - U+%04X" % (PUA_START, SYLLABIC_NASALS[-1]["codepoint"]))
    print("=" * 60)


if __name__ == "__main__":
    main()
