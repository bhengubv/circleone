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

Author: The Geek Network
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
FONT_COPYRIGHT = "Copyright (c) The Geek Network. isiBheqe SoNtu script."

EM_SIZE = 1000          # Units per em
ASCENT = 800
DESCENT = 200
GLYPH_WIDTH = 1000      # Monospaced advance width

# Geometric parameters for glyph drawing
CENTER_X = 500
CENTER_Y = 400          # Optical center (slightly above baseline midpoint)
RADIUS = 350            # Outer shape radius
INNER_RADIUS = 200      # Inner consonant mark radius
STROKE_WIDTH = 60

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

def draw_circle(pen, cx, cy, r, num_points=64):
    """Draw a circle using cubic bezier approximation."""
    # Use 4-segment cubic bezier approximation for a circle
    k = r * 0.5522847498     # magic number for cubic bezier circle approx
    pen.moveTo((cx, cy + r))
    pen.curveTo((cx + k, cy + r), (cx + r, cy + k), (cx + r, cy))
    pen.curveTo((cx + r, cy - k), (cx + k, cy - r), (cx, cy - r))
    pen.curveTo((cx - k, cy - r), (cx - r, cy - k), (cx - r, cy))
    pen.curveTo((cx - r, cy + k), (cx - k, cy + r), (cx, cy + r))
    pen.closePath()


def draw_right_half_circle(pen, cx, cy, r):
    """Draw a right-facing half-circle (iphambili / e vowel)."""
    k = r * 0.5522847498
    pen.moveTo((cx, cy + r))
    pen.curveTo((cx + k, cy + r), (cx + r, cy + k), (cx + r, cy))
    pen.curveTo((cx + r, cy - k), (cx + k, cy - r), (cx, cy - r))
    pen.lineTo((cx, cy + r))
    pen.closePath()


def draw_left_half_circle(pen, cx, cy, r):
    """Draw a left-facing half-circle (imuva / o vowel)."""
    k = r * 0.5522847498
    pen.moveTo((cx, cy - r))
    pen.curveTo((cx - k, cy - r), (cx - r, cy - k), (cx - r, cy))
    pen.curveTo((cx - r, cy + k), (cx - k, cy + r), (cx, cy + r))
    pen.lineTo((cx, cy - r))
    pen.closePath()


def draw_triangle_down(pen, cx, cy, r):
    """Draw a downward-pointing triangle (intombi / i vowel)."""
    # Equilateral triangle pointing down
    h = r * math.sqrt(3) / 2
    pen.moveTo((cx - r, cy + h * 0.67))
    pen.lineTo((cx + r, cy + h * 0.67))
    pen.lineTo((cx, cy - h * 1.33))
    pen.closePath()


def draw_triangle_up(pen, cx, cy, r):
    """Draw an upward-pointing triangle (umkhonto / u vowel)."""
    h = r * math.sqrt(3) / 2
    pen.moveTo((cx - r, cy - h * 0.67))
    pen.lineTo((cx + r, cy - h * 0.67))
    pen.lineTo((cx, cy + h * 1.33))
    pen.closePath()


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
    }

    lines = group_marks.get(consonant_latin, [(cx - half, cy, cx + half, cy)])

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


def draw_syllabic_nasal(pen, nasal_latin, cx, cy, r):
    """Draw a syllabic nasal glyph (amaQanda) -- a circle with nasal marker."""
    # Outer circle
    draw_circle(pen, cx, cy, r * 0.6)
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
