# Dictionary attribution

CircleOne's dictionaries for isiZulu, isiXhosa, isiNdebele, siSwati, Sesotho,
Setswana, Sepedi, Tshivenda, Xitsonga and Afrikaans are built from the
**NCHLT Text Corpora**.

- Attribute work to: South African Department of Arts and Culture & Centre for
  Text Technology (CTexT, North-West University, South Africa) -
  http://www.nwu.ac.za/ctext
- Licence: Creative Commons Attribution 2.5 South Africa -
  http://creativecommons.org/licenses/by/2.5/za/
- Source: repo.sadilar.org, NCHLT Text Corpora records 20.500.12185/293 (af),
  308 (nr), 314 (xh), 321 (zu), 330 (nso), 336 (st), 343 (tn), 348 (ss),
  357 (ve), 364 (ts). Downloaded 2026-10-17, each archive checked against the
  repository's MD5.

What was done to them: each corpus's frequency lexicon was filtered (words seen
once dropped), case variants were merged, word frequencies were log-scaled, and
next-word pairs were counted from the clean corpus. See `build_dicts.py`.

The same credit is shown in CircleOne, on the Learn screen's About line.
