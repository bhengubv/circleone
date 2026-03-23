# isiBheqe soHlamvu Reference

## Official Reference PDF

The authoritative design specification for isiBheqe soHlamvu glyph shapes:

**Download:** https://isibheqe.org.za/wp-content/uploads/2020/03/ingcazo.pdf

Source: https://isibheqe.org.za

This PDF contains:
- Complete vowel shape chart (directional triangles/chevrons)
- Consonant stroke patterns for all phoneme groups
- CV syllable composition examples
- Syllabic nasal (amaQanda) forms

## Rendering Pages Locally

If you need PNG renders of the PDF pages:

```bash
pip install pymupdf
python -c "
import fitz
doc = fitz.open('Isibheqe Sohlamvu.pdf')
for i in range(len(doc)):
    page = doc[i]
    pix = page.get_pixmap(dpi=200)
    pix.save(f'page_{i+1}.png')
"
```
