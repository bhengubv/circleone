#!/usr/bin/env fontforge -script
"""
isiBheqe Font Generator (CircleOne)
====================================
Generates the CircleOne TrueType font for the isiBheqe SoNtu writing system.

Each glyph encodes a complete CV syllable, pure vowel, or syllabic nasal
using Unicode Private Use Area (PUA) codepoints starting at U+E000.

The geometric design follows isiBheqe SoNtu conventions:
  - Vowels are directional triangles/chevrons encoding articulation
  - Consonant strokes overlay and cross through the vowel shape
  - Syllabic nasals (amaQanda) are standalone triangle forms with nasal marks

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
CENTER_Y = 350          # Optical center
SIZE = 320              # Half-height/width of vowel shapes
STROKE = 45             # Universal stroke thickness (thin, clean lines)
CONSONANT_EXTENT = 280  # How far consonant lines extend from center

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

# ===========================================================================
# Stroke Drawing Primitives
# ===========================================================================

def _draw_line(pen, x1, y1, x2, y2, w=None):
    """Draw a line as a thin rectangle with width w (defaults to STROKE)."""
    if w is None:
        w = STROKE
    dx = x2 - x1
    dy = y2 - y1
    length = math.sqrt(dx * dx + dy * dy)
    if length == 0:
        return
    nx = -dy / length * w / 2
    ny = dx / length * w / 2
    pen.moveTo((x1 + nx, y1 + ny))
    pen.lineTo((x2 + nx, y2 + ny))
    pen.lineTo((x2 - nx, y2 - ny))
    pen.lineTo((x1 - nx, y1 - ny))
    pen.closePath()


def _draw_triangle_outline(pen, p1, p2, p3, w=None):
    """Draw a triangle as three stroked lines."""
    if w is None:
        w = STROKE
    _draw_line(pen, p1[0], p1[1], p2[0], p2[1], w)
    _draw_line(pen, p2[0], p2[1], p3[0], p3[1], w)
    _draw_line(pen, p3[0], p3[1], p1[0], p1[1], w)


def _draw_circle_outline(pen, cx, cy, r, w=None):
    """Draw a circle as an outlined ring (outer CW, inner CCW)."""
    if w is None:
        w = STROKE
    k = r * 0.5522847498
    ri = r - w
    ki = ri * 0.5522847498
    # Outer clockwise
    pen.moveTo((cx, cy + r))
    pen.curveTo((cx + k, cy + r), (cx + r, cy + k), (cx + r, cy))
    pen.curveTo((cx + r, cy - k), (cx + k, cy - r), (cx, cy - r))
    pen.curveTo((cx - k, cy - r), (cx - r, cy - k), (cx - r, cy))
    pen.curveTo((cx - r, cy + k), (cx - k, cy + r), (cx, cy + r))
    pen.closePath()
    # Inner counter-clockwise (cutout)
    pen.moveTo((cx, cy + ri))
    pen.curveTo((cx - ki, cy + ri), (cx - ri, cy + ki), (cx - ri, cy))
    pen.curveTo((cx - ri, cy - ki), (cx - ki, cy - ri), (cx, cy - ri))
    pen.curveTo((cx + ki, cy - ri), (cx + ri, cy - ki), (cx + ri, cy))
    pen.curveTo((cx + ri, cy + ki), (cx + ki, cy + ri), (cx, cy + ri))
    pen.closePath()


def _draw_dot(pen, cx, cy, r):
    """Draw a small filled circle (dot)."""
    k = r * 0.5522847498
    pen.moveTo((cx, cy + r))
    pen.curveTo((cx + k, cy + r), (cx + r, cy + k), (cx + r, cy))
    pen.curveTo((cx + r, cy - k), (cx + k, cy - r), (cx, cy - r))
    pen.curveTo((cx - k, cy - r), (cx - r, cy - k), (cx - r, cy))
    pen.curveTo((cx - r, cy + k), (cx - k, cy + r), (cx, cy + r))
    pen.closePath()


# ===========================================================================
# Vowel Shape Drawing -- isiBheqe directional triangles/chevrons
# ===========================================================================
# From the isiBheqe reference:
#   a (isoka)     = upward triangle △ (open central -- mouth open, points up)
#   e (iphambili) = left triangle ◁ (front vowel -- points to front/left)
#   i (intombi)   = upward triangle △ (close front -- narrower/taller than a)
#   o (imuva)     = right triangle ▷ (back vowel -- points to back/right)
#   u (umkhonto)  = downward triangle ▽ (close back -- spear, points down)

def draw_vowel_a(pen, cx, cy, s):
    """Upward triangle △ -- wide base, open central vowel."""
    p1 = (cx - s, cy - s * 0.7)       # bottom-left
    p2 = (cx + s, cy - s * 0.7)       # bottom-right
    p3 = (cx, cy + s)                  # apex
    _draw_triangle_outline(pen, p1, p2, p3)

def draw_vowel_e(pen, cx, cy, s):
    """Left-pointing triangle ◁ -- front vowel."""
    p1 = (cx + s * 0.7, cy + s)       # top-right
    p2 = (cx + s * 0.7, cy - s)       # bottom-right
    p3 = (cx - s, cy)                  # left apex
    _draw_triangle_outline(pen, p1, p2, p3)

def draw_vowel_i(pen, cx, cy, s):
    """Upward triangle △ -- narrower/taller than a, close front."""
    p1 = (cx - s * 0.6, cy - s * 0.8) # bottom-left
    p2 = (cx + s * 0.6, cy - s * 0.8) # bottom-right
    p3 = (cx, cy + s)                  # apex
    _draw_triangle_outline(pen, p1, p2, p3)

def draw_vowel_o(pen, cx, cy, s):
    """Right-pointing triangle ▷ -- back vowel."""
    p1 = (cx - s * 0.7, cy + s)       # top-left
    p2 = (cx - s * 0.7, cy - s)       # bottom-left
    p3 = (cx + s, cy)                  # right apex
    _draw_triangle_outline(pen, p1, p2, p3)

def draw_vowel_u(pen, cx, cy, s):
    """Downward triangle ▽ -- close back, spear pointing down."""
    p1 = (cx - s, cy + s * 0.7)       # top-left
    p2 = (cx + s, cy + s * 0.7)       # top-right
    p3 = (cx, cy - s)                  # bottom apex
    _draw_triangle_outline(pen, p1, p2, p3)


def draw_vowel_shape(pen, vowel_name, cx, cy, s):
    """Draw the vowel shape based on isiBheqe name."""
    if vowel_name in ("isoka", "isoka_long"):
        draw_vowel_a(pen, cx, cy, s)
        if vowel_name == "isoka_long":
            draw_vowel_a(pen, cx, cy, s * 0.7)
    elif vowel_name in ("iphambili", "iphambili_long"):
        draw_vowel_e(pen, cx, cy, s)
        if vowel_name == "iphambili_long":
            draw_vowel_e(pen, cx, cy, s * 0.7)
    elif vowel_name in ("intombi", "intombi_long"):
        draw_vowel_i(pen, cx, cy, s)
        if vowel_name == "intombi_long":
            draw_vowel_i(pen, cx, cy, s * 0.7)
    elif vowel_name == "imuva":
        draw_vowel_o(pen, cx, cy, s)
    elif vowel_name == "umkhonto":
        draw_vowel_u(pen, cx, cy, s)
    else:
        draw_vowel_a(pen, cx, cy, s)


def draw_consonant_mark(pen, consonant_latin, cx, cy, ext):
    """
    Draw the consonant stroke(s) overlaid on the vowel shape.

    Based on the isiBheqe reference: consonant marks are simple line strokes
    that cross through the vowel shape. Each consonant has a unique pattern
    of lines, crosses, dots, or circles from the reference chart (page 3).
    """
    h = ext       # full extent
    hh = ext * 0.5  # half extent
    q = ext * 0.35  # quarter extent

    # Consonant stroke patterns from the isiBheqe reference chart
    # Each entry is a list of (x1,y1,x2,y2) line segments
    marks = {
        # Plosives -- from reference chart row 1
        "b":    [(cx - h, cy, cx + h, cy)],                          # horizontal line
        "p":    [(cx, cy - h, cx, cy + h)],                          # vertical line
        "ph":   [(cx, cy - h, cx, cy + h),                           # vertical + horizontal
                 (cx - hh, cy, cx + hh, cy)],
        "d":    [(cx - h, cy - h, cx + h, cy + h)],                  # diagonal /
        "t":    [(cx - h, cy + h, cx + h, cy - h)],                  # diagonal \
        "th":   [(cx - h, cy + h, cx + h, cy - h),                   # X cross
                 (cx - h, cy - h, cx + h, cy + h)],
        "g":    [(cx - h, cy, cx + h, cy),                           # + cross
                 (cx, cy - h, cx, cy + h)],
        "k":    [(cx - h, cy, cx + h, cy),                           # horizontal + diagonal
                 (cx - hh, cy + hh, cx + hh, cy - hh)],
        "kh":   [(cx - h, cy, cx + h, cy),                           # horizontal + X
                 (cx - hh, cy + hh, cx + hh, cy - hh),
                 (cx - hh, cy - hh, cx + hh, cy + hh)],
        # Fricatives -- reference chart row 2
        "f":    [(cx - h, cy + h, cx, cy - h),                       # V shape (inverted)
                 (cx, cy - h, cx + h, cy + h)],
        "v":    [(cx - h, cy - h, cx, cy + h),                       # V shape
                 (cx, cy + h, cx + h, cy - h)],
        "s":    [(cx - h, cy + q, cx + h, cy - q)],                  # gentle diagonal
        "z":    [(cx - h, cy - q, cx + h, cy + q)],                  # gentle diagonal other way
        "sh":   [(cx - h, cy + q, cx, cy - q),                       # zigzag
                 (cx, cy - q, cx + h, cy + q)],
        "h":    [(cx - q, cy - h, cx - q, cy + h),                   # two verticals
                 (cx + q, cy - h, cx + q, cy + h)],
        "hl":   [(cx - h, cy + q, cx + h, cy + q),                   # two horizontals
                 (cx - h, cy - q, cx + h, cy - q)],
        "dl":   [(cx - h, cy, cx + h, cy),                           # horizontal + two short verticals
                 (cx - q, cy - hh, cx - q, cy + hh),
                 (cx + q, cy - hh, cx + q, cy + hh)],
        # Nasals -- reference chart: circles and dots
        "m":    "circle",                                             # small circle
        "n":    "dot",                                                # filled dot
        "ng":   "circle_cross",                                       # circle with cross
        "ny":   "circle_dot",                                         # circle with dot inside
        # Liquids
        "l":    [(cx, cy - h, cx, cy + h)],                          # single vertical
        "r":    [(cx - h, cy, cx + h, cy),                           # horizontal + hook
                 (cx + hh, cy, cx + h, cy + q)],
        # Approximants
        "w":    [(cx - hh, cy + hh, cx, cy - hh),                    # W shape
                 (cx, cy - hh, cx + hh, cy + hh)],
        "y":    [(cx - hh, cy + h, cx, cy),                          # Y shape
                 (cx + hh, cy + h, cx, cy),
                 (cx, cy, cx, cy - h)],
        # Implosive
        "bh":   [(cx - h, cy, cx + h, cy),                           # horizontal + hook down
                 (cx - hh, cy, cx - hh, cy - q)],
        # Prenasalized -- dot prefix + base consonant pattern
        "mb":   [(cx - h, cy, cx + h, cy),                           # horizontal + dot below
                 (cx - q, cy - hh, cx + q, cy - hh)],
        "nd":   [(cx - h, cy - h, cx + h, cy + h),                   # diagonal + short horiz
                 (cx - q, cy, cx + q, cy)],
        "nt":   [(cx - h, cy + h, cx + h, cy - h),                   # \ + short horiz
                 (cx - q, cy, cx + q, cy)],
        "nk":   [(cx - h, cy, cx + h, cy),                           # + cross + short diag
                 (cx, cy - h, cx, cy + h),
                 (cx - q, cy + q, cx + q, cy - q)],
        "nj":   [(cx - h, cy, cx + h, cy),                           # horizontal + V below
                 (cx - q, cy - q, cx, cy - hh),
                 (cx, cy - hh, cx + q, cy - q)],
        "ntsh": [(cx - h, cy + h, cx + h, cy - h),                   # X + horizontal
                 (cx - h, cy - h, cx + h, cy + h),
                 (cx - hh, cy, cx + hh, cy)],
        "nz":   [(cx - h, cy - q, cx + h, cy + q),                   # gentle diag + short horiz
                 (cx - q, cy, cx + q, cy)],
        "mf":   [(cx - h, cy + h, cx, cy - h),                       # V + dot
                 (cx, cy - h, cx + h, cy + h)],
        "mv":   [(cx - h, cy - h, cx, cy + h),                       # inverted V + dot
                 (cx, cy + h, cx + h, cy - h)],
        "mph":  [(cx, cy - h, cx, cy + h),                           # vertical + V
                 (cx - hh, cy + hh, cx, cy),
                 (cx, cy, cx + hh, cy + hh)],
        "ndl":  [(cx - h, cy - h, cx + h, cy + h),                   # diag + two horiz
                 (cx - hh, cy + q, cx + hh, cy + q),
                 (cx - hh, cy - q, cx + hh, cy - q)],
        "nhl":  [(cx - q, cy - h, cx - q, cy + h),                   # two verticals + horiz
                 (cx + q, cy - h, cx + q, cy + h),
                 (cx - hh, cy, cx + hh, cy)],
        "ntl":  [(cx - h, cy + h, cx + h, cy - h),                   # \ + vertical
                 (cx, cy - hh, cx, cy + hh)],
        "ns":   [(cx - h, cy + q, cx + h, cy - q),                   # gentle diag + dot
                 (cx - q, cy - q, cx + q, cy - q)],
        # Affricates
        "j":    [(cx - hh, cy, cx + hh, cy),                         # T shape
                 (cx, cy, cx, cy - h)],
        "tsh":  [(cx - h, cy + h, cx + h, cy - h),                   # \ + T below
                 (cx - q, cy - hh, cx + q, cy - hh),
                 (cx, cy - hh, cx, cy - h)],
        # Clicks -- dental: vertical line + modifiers
        "c":    [(cx, cy - h, cx, cy + h),                           # vertical + short horiz
                 (cx - q, cy, cx + q, cy)],
        "ch":   [(cx, cy - h, cx, cy + h),                           # vertical + tick right
                 (cx, cy + q, cx + q, cy + q)],
        "gc":   [(cx, cy - h, cx, cy + h),                           # vertical + tick left-down
                 (cx - q, cy - q, cx, cy - hh)],
        "nc":   [(cx, cy - h, cx, cy + h),                           # vertical + two horiz
                 (cx - q, cy + q, cx + q, cy + q),
                 (cx - q, cy - q, cx + q, cy - q)],
        # Clicks -- palatal: two verticals + modifiers
        "q":    [(cx - q, cy - h, cx - q, cy + h),                   # two verticals
                 (cx + q, cy - h, cx + q, cy + h)],
        "qh":   [(cx - q, cy - h, cx - q, cy + h),                   # two verticals + tick
                 (cx + q, cy - h, cx + q, cy + h),
                 (cx + q, cy + q, cx + hh, cy + q)],
        "gq":   [(cx - q, cy - h, cx - q, cy + h),                   # two verticals + tick down
                 (cx + q, cy - h, cx + q, cy + h),
                 (cx - q, cy - q, cx - hh, cy - hh)],
        "nq":   [(cx - q, cy - h, cx - q, cy + h),                   # two verticals + horiz
                 (cx + q, cy - h, cx + q, cy + h),
                 (cx - q, cy, cx + q, cy)],
        # Clicks -- lateral: X cross + modifiers
        "x":    [(cx - h, cy + h, cx + h, cy - h),                   # X
                 (cx - h, cy - h, cx + h, cy + h)],
        "xh":   [(cx - h, cy + h, cx + h, cy - h),                   # X + tick
                 (cx - h, cy - h, cx + h, cy + h),
                 (cx, cy + q, cx + q, cy + q)],
        "gx":   [(cx - h, cy + h, cx + h, cy - h),                   # X + tick down
                 (cx - h, cy - h, cx + h, cy + h),
                 (cx - q, cy - q, cx - hh, cy - hh)],
        "nx":   [(cx - h, cy + h, cx + h, cy - h),                   # X + horiz
                 (cx - h, cy - h, cx + h, cy + h),
                 (cx - q, cy, cx + q, cy)],
    }

    # Handle labialized consonants: base mark + small circle
    base = consonant_latin
    is_labialized = False
    if base.endswith("w") and base != "w" and len(base) > 1:
        base = base[:-1]
        is_labialized = True
    if base not in marks and consonant_latin in marks:
        base = consonant_latin
        is_labialized = False

    mark = marks.get(base, marks.get(consonant_latin, [(cx - h, cy, cx + h, cy)]))

    # Special nasal marks (circles/dots)
    if mark == "circle":
        _draw_circle_outline(pen, cx, cy, q)
    elif mark == "dot":
        _draw_dot(pen, cx, cy, STROKE * 0.8)
    elif mark == "circle_cross":
        _draw_circle_outline(pen, cx, cy, q)
        _draw_line(pen, cx - q, cy, cx + q, cy)
        _draw_line(pen, cx, cy - q, cx, cy + q)
    elif mark == "circle_dot":
        _draw_circle_outline(pen, cx, cy, q)
        _draw_dot(pen, cx, cy, STROKE * 0.5)
    else:
        # Draw line segments
        for seg in mark:
            x1, y1, x2, y2 = seg
            _draw_line(pen, x1, y1, x2, y2)

    # Labialized: add small open circle at bottom-right
    if is_labialized:
        _draw_circle_outline(pen, cx + hh, cy - hh, STROKE * 1.2)


def draw_syllabic_nasal(pen, nasal_latin, cx, cy, s):
    """Draw a syllabic nasal glyph (amaQanda) -- triangle with nasal marker."""
    # Draw a small upward triangle as the base shape
    r = s * 0.6
    p1 = (cx - r, cy - r * 0.7)
    p2 = (cx + r, cy - r * 0.7)
    p3 = (cx, cy + r)
    _draw_triangle_outline(pen, p1, p2, p3)
    # Nasal mark inside
    q = r * 0.4
    if nasal_latin == "m_syl":
        _draw_circle_outline(pen, cx, cy, q)
    elif nasal_latin == "n_syl":
        _draw_dot(pen, cx, cy, STROKE * 0.8)
    elif nasal_latin == "ng_syl":
        _draw_circle_outline(pen, cx, cy, q)
        _draw_line(pen, cx - q, cy, cx + q, cy)
        _draw_line(pen, cx, cy - q, cx, cy + q)


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
        draw_vowel_shape(pen, v["name"], CENTER_X, CENTER_Y, SIZE)
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

            # Draw vowel shape (triangle/chevron)
            draw_vowel_shape(pen, v["name"], CENTER_X, CENTER_Y, SIZE)

            # Draw consonant strokes crossing through the shape
            draw_consonant_mark(pen, c["latin"], CENTER_X, CENTER_Y, CONSONANT_EXTENT)

            pen = None

    # -----------------------------------------------------------------------
    # Syllabic nasals (amaQanda)
    # -----------------------------------------------------------------------
    for sn in SYLLABIC_NASALS:
        glyph = font.createChar(sn["codepoint"], sn["glyph_name"])
        glyph.width = GLYPH_WIDTH
        pen = glyph.glyphPen()
        draw_syllabic_nasal(pen, sn["latin"], CENTER_X, CENTER_Y, SIZE)
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
