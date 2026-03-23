#!/usr/bin/env python3
"""
Validate the Rime schema YAML file (rime/one.schema.yaml).

Checks:
- File exists and is valid YAML
- Required top-level keys are present
- Schema metadata fields are populated
- Engine has required processor/segmentor/translator/filter sections
- Speller has alphabet and delimiter defined
- Translator references the correct dictionary

Usage:
    python scripts/validate_schema.py [path_to_schema.yaml]

If no path is given, defaults to rime/one.schema.yaml relative to the repo root.
"""

import os
import sys

try:
    import yaml
except ImportError:
    print(
        "Error: PyYAML is required. Install with: pip install pyyaml", file=sys.stderr
    )
    sys.exit(1)


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


def validate_schema(schema_path):
    """Validate the Rime schema file. Returns a list of error strings."""
    errors = []
    warnings = []

    # Check file exists
    if not os.path.isfile(schema_path):
        return [f"Schema file not found: {schema_path}"], []

    # Parse YAML
    try:
        with open(schema_path, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f)
    except yaml.YAMLError as e:
        return [f"YAML parse error: {e}"], []

    if not isinstance(data, dict):
        return ["Schema file does not contain a YAML mapping at the top level."], []

    # Check required top-level keys
    required_keys = ["schema", "engine", "speller", "translator"]
    for key in required_keys:
        if key not in data:
            errors.append(f"Missing required top-level key: '{key}'")

    # Validate schema metadata
    if "schema" in data:
        schema = data["schema"]
        if not isinstance(schema, dict):
            errors.append("'schema' must be a mapping.")
        else:
            for field in ["schema_id", "name", "version", "author", "description"]:
                if field not in schema:
                    errors.append(f"Missing schema metadata field: 'schema.{field}'")
                elif not schema[field]:
                    warnings.append(f"Empty schema metadata field: 'schema.{field}'")

            if "schema_id" in schema and schema["schema_id"] != "one":
                warnings.append(
                    f"schema_id is '{schema['schema_id']}', expected 'one'"
                )

    # Validate engine
    if "engine" in data:
        engine = data["engine"]
        if not isinstance(engine, dict):
            errors.append("'engine' must be a mapping.")
        else:
            engine_sections = ["processors", "segmentors", "translators", "filters"]
            for section in engine_sections:
                if section not in engine:
                    errors.append(f"Missing engine section: 'engine.{section}'")
                elif not isinstance(engine[section], list):
                    errors.append(f"'engine.{section}' must be a list.")
                elif len(engine[section]) == 0:
                    warnings.append(f"'engine.{section}' is empty.")

            # Check for essential processors
            if "processors" in engine and isinstance(engine["processors"], list):
                essential_processors = ["speller", "punctuator", "selector"]
                for proc in essential_processors:
                    if proc not in engine["processors"]:
                        warnings.append(
                            f"Missing recommended processor: '{proc}'"
                        )

            # Check for table_translator
            if "translators" in engine and isinstance(engine["translators"], list):
                if "table_translator" not in engine["translators"]:
                    errors.append(
                        "engine.translators must include 'table_translator' "
                        "for dictionary-based input."
                    )

    # Validate speller
    if "speller" in data:
        speller = data["speller"]
        if not isinstance(speller, dict):
            errors.append("'speller' must be a mapping.")
        else:
            if "alphabet" not in speller:
                errors.append("Missing 'speller.alphabet'.")
            if "delimiter" not in speller:
                warnings.append("Missing 'speller.delimiter' (recommended).")

    # Validate translator
    if "translator" in data:
        translator = data["translator"]
        if not isinstance(translator, dict):
            errors.append("'translator' must be a mapping.")
        else:
            if "dictionary" not in translator:
                errors.append("Missing 'translator.dictionary'.")
            elif translator["dictionary"] != "one":
                warnings.append(
                    f"translator.dictionary is '{translator['dictionary']}', "
                    "expected 'one'"
                )

    return errors, warnings


def main():
    if len(sys.argv) > 1:
        schema_path = sys.argv[1]
    else:
        repo_root = find_repo_root()
        schema_path = os.path.join(repo_root, "rime", "one.schema.yaml")

    print(f"Validating schema: {schema_path}")
    print()

    errors, warnings = validate_schema(schema_path)

    if warnings:
        print(f"Warnings ({len(warnings)}):")
        for w in warnings:
            print(f"  [WARN] {w}")
        print()

    if errors:
        print(f"Errors ({len(errors)}):")
        for e in errors:
            print(f"  [ERROR] {e}")
        print()
        print("VALIDATION FAILED")
        sys.exit(1)
    else:
        print("VALIDATION PASSED")
        if warnings:
            print(f"  ({len(warnings)} warning(s))")
        sys.exit(0)


if __name__ == "__main__":
    main()
