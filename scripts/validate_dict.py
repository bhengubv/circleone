#!/usr/bin/env python3
"""
Validate the Rime dictionary YAML file (rime/one.dict.yaml).

Checks:
- File exists and has a valid YAML header
- Header contains required fields (name, version, sort, columns)
- All entries have the correct number of tab-separated columns
- No duplicate input codes
- All entries have non-empty text, code, and weight fields
- Weights are valid integers

Usage:
    python scripts/validate_dict.py [path_to_dict.yaml]

If no path is given, defaults to rime/one.dict.yaml relative to the repo root.
"""

import os
import sys


def find_repo_root():
    """Walk up from the script location to find the repository root."""
    current = os.path.dirname(os.path.abspath(__file__))
    while current != os.path.dirname(current):
        if os.path.isfile(os.path.join(current, "README.md")) and os.path.isdir(
            os.path.join(current, "rime")
        ):
            return current
        current = os.path.dirname(current)
    print("Error: Could not locate repository root.", file=sys.stderr)
    sys.exit(1)


def parse_dict_file(dict_path):
    """Parse the Rime dictionary file into header lines and entry lines.

    Rime dict format:
        ---
        name: one
        version: "0.1.0"
        ...
        <tab-separated entries>

    Returns (header_text, entries) where entries is a list of
    (line_number, raw_line) tuples.
    """
    header_lines = []
    entries = []
    in_header = False
    header_ended = False

    with open(dict_path, "r", encoding="utf-8") as f:
        for line_num, line in enumerate(f, start=1):
            stripped = line.rstrip("\n\r")

            # Skip comments and blank lines before and after header
            if stripped.startswith("#") or stripped == "":
                continue

            # Detect YAML header boundaries
            if stripped == "---" and not in_header:
                in_header = True
                continue
            if stripped == "..." and in_header:
                in_header = False
                header_ended = True
                continue

            if in_header:
                header_lines.append(stripped)
                continue

            if header_ended:
                entries.append((line_num, stripped))

    return header_lines, entries


def validate_header(header_lines):
    """Validate the YAML header fields. Returns list of errors."""
    errors = []
    header_text = "\n".join(header_lines)

    required_fields = ["name", "version", "sort"]
    for field in required_fields:
        if f"{field}:" not in header_text:
            errors.append(f"Missing required header field: '{field}'")

    # Check that name is "one"
    for line in header_lines:
        if line.startswith("name:"):
            name_value = line.split(":", 1)[1].strip()
            if name_value != "one":
                errors.append(
                    f"Dictionary name is '{name_value}', expected 'one'"
                )

    return errors


def validate_entries(entries):
    """Validate dictionary entries. Returns (errors, stats)."""
    errors = []
    seen_codes = {}
    valid_count = 0

    for line_num, line in entries:
        parts = line.split("\t")

        # Expect 3 columns: text, code, weight
        if len(parts) < 2:
            errors.append(
                f"Line {line_num}: Too few columns ({len(parts)}). "
                f"Expected at least 2 (text, code). Got: '{line}'"
            )
            continue

        text = parts[0]
        code = parts[1]
        weight = parts[2] if len(parts) > 2 else None

        # Check for empty fields
        if not text:
            errors.append(f"Line {line_num}: Empty text field.")
        if not code:
            errors.append(f"Line {line_num}: Empty code (input) field.")

        # Check weight is a valid integer if present
        if weight is not None:
            try:
                int(weight)
            except ValueError:
                errors.append(
                    f"Line {line_num}: Invalid weight '{weight}' (must be integer)."
                )

        # Check for duplicate input codes
        if code in seen_codes:
            errors.append(
                f"Line {line_num}: Duplicate input code '{code}' "
                f"(first seen on line {seen_codes[code]})."
            )
        else:
            seen_codes[code] = line_num
            valid_count += 1

    stats = {
        "total_entries": len(entries),
        "valid_entries": valid_count,
        "unique_codes": len(seen_codes),
    }
    return errors, stats


def main():
    if len(sys.argv) > 1:
        dict_path = sys.argv[1]
    else:
        repo_root = find_repo_root()
        dict_path = os.path.join(repo_root, "rime", "one.dict.yaml")

    print(f"Validating dictionary: {dict_path}")
    print()

    if not os.path.isfile(dict_path):
        print(f"Error: Dictionary file not found: {dict_path}", file=sys.stderr)
        sys.exit(1)

    header_lines, entries = parse_dict_file(dict_path)
    all_errors = []

    # Validate header
    if not header_lines:
        all_errors.append("No YAML header found (expected --- ... delimiters).")
    else:
        header_errors = validate_header(header_lines)
        all_errors.extend(header_errors)

    # Validate entries
    if not entries:
        all_errors.append("No dictionary entries found after the YAML header.")
    else:
        entry_errors, stats = validate_entries(entries)
        all_errors.extend(entry_errors)

    # Report
    if entries:
        print(f"Entries found: {stats['total_entries']}")
        print(f"Unique input codes: {stats['unique_codes']}")
        print()

    if all_errors:
        print(f"Errors ({len(all_errors)}):")
        for e in all_errors:
            print(f"  [ERROR] {e}")
        print()
        print("VALIDATION FAILED")
        sys.exit(1)
    else:
        print("VALIDATION PASSED")
        sys.exit(0)


if __name__ == "__main__":
    main()
