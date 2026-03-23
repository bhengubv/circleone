# Contributing to CircleOne

Welcome, and thank you for your interest in contributing to the CircleOne project.

CircleOne is the open-source keyboard and input method for isiBheqe soHlamvu, a
featural script for siNtu (Bantu) languages. The project goal is to bring full
digital support for isiBheqe soHlamvu across every major platform -- mobile,
desktop, and web -- and to pursue formal recognition through the Unicode
Consortium.

Whether you are a linguist, a type designer, a software developer, or simply
someone passionate about indigenous African writing systems, there is a place for
you here.

Learn more about the script at https://isibheqe.org.za

---

## How to Contribute

### 1. Fork and Clone

```bash
git clone https://github.com/<your-username>/circleone.git
cd circleone
```

### 2. Create a Branch

Use a descriptive branch name that reflects the work you are doing:

```bash
git checkout -b feature/rime-zulu-schema
git checkout -b fix/android-key-mapping
git checkout -b docs/add-xhosa-phonology-notes
```

### 3. Make Your Changes

Follow the code style guidelines below. Keep commits atomic and well-described.

### 4. Push and Open a Pull Request

```bash
git push origin feature/rime-zulu-schema
```

Then open a Pull Request against the `main` branch on GitHub. In your PR
description:

- Summarize what you changed and why.
- Reference any related issues (e.g., "Closes #12").
- Note which platform or component is affected.

### 5. Review

A maintainer will review your PR. You may be asked to make changes. Once
approved, your work will be merged.

---

## Contributor Roles Needed

We welcome contributions across many disciplines. Here are the roles where help
is most needed:

| Role | Focus Area | Key Skills |
|------|-----------|------------|
| **Linguist** | siNtu phonology, script accuracy, language-specific mappings | Knowledge of Nguni, Sotho-Tswana, or other siNtu language families |
| **Type Designer** | Font creation and refinement for isiBheqe soHlamvu glyphs | FontForge, OpenType feature development, glyph design |
| **Android Developer** | Native keyboard (IME) for Android | Kotlin, Android InputMethodService, custom key layouts |
| **iOS Developer** | Native keyboard extension for iOS | Swift, UIKit/SwiftUI keyboard extensions |
| **Keyman Developer** | Keyman keyboard packages for desktop and web | Keyman .kmn format, keyboard layout design |
| **Rime Schema Developer** | Rime input method schemas for Linux/macOS | YAML configuration, Rime schema authoring |
| **Web Developer** | Browser-based virtual keyboard, documentation site | HTML, CSS, JavaScript |
| **Unicode Specialist** | Unicode proposal preparation and liaison | Unicode Technical Standard processes, ISO 15924 |
| **Community Manager** | Outreach, translation, user support | Communication, community building, siNtu language fluency |

---

## Code Style Guidelines

### Python (scripts/)

- Follow PEP 8.
- Use Python 3.10 or later.
- Include docstrings for all public functions.
- Use `black` for formatting and `ruff` for linting.

### YAML (rime/)

- Use 2-space indentation.
- Comment all non-obvious configuration values.
- Follow existing Rime schema conventions for key bindings and translators.

### Keyman (.kmn files, keyman/)

- Follow the Keyman keyboard grammar specification.
- Include a `LAYOUT` section with clear comments mapping each key to its
  isiBheqe soHlamvu character.
- Provide both desktop and touch layout definitions where applicable.

### Kotlin / Swift (android/, ios/)

- Follow the official Kotlin and Swift style guides respectively.
- Keep platform-specific code in its own directory.
- Write unit tests for all input transformation logic.

### General

- Use UTF-8 encoding for all files.
- End all files with a single newline.
- Do not include trailing whitespace.

---

## Reporting Bugs

Open an issue on GitHub at https://github.com/bhengubv/circleone/issues with the
following information:

- **Platform**: Which platform and version (e.g., Android 14, macOS Sonoma, Keyman 17).
- **Steps to reproduce**: A clear sequence of actions that triggers the bug.
- **Expected behavior**: What you expected to happen.
- **Actual behavior**: What actually happened.
- **Screenshots or logs**: If applicable.

---

## Requesting Features

Open a feature request issue on GitHub. Please include:

- A clear description of the feature.
- The use case: who benefits and how.
- Which platform or component it relates to.
- Any reference material (e.g., linguistic sources, Unicode charts).

---

## Adding Support for a New Language

isiBheqe soHlamvu is designed to represent multiple siNtu languages. To add
support for a new language:

1. **Research**: Document the phoneme inventory of the target language and how
   each phoneme maps to isiBheqe soHlamvu characters. Cite linguistic sources.

2. **Propose**: Open an issue titled "Language support: [Language Name]" with
   your phoneme-to-character mapping table.

3. **Implement**: Once the mapping is agreed upon, create or update the relevant
   input method files:
   - `rime/` -- Add or update the Rime schema YAML for the language.
   - `keyman/` -- Add or update the Keyman .kmn layout.
   - `android/` and `ios/` -- Update the language selection and key mappings.

4. **Test**: Verify that all phonemes can be correctly input and that the output
   renders properly with the project font.

5. **Document**: Update `docs/` with the new language mapping and any notes on
   usage.

---

## Communication

- **GitHub Issues**: Primary channel for bug reports, feature requests, and
  discussion. https://github.com/bhengubv/circleone/issues
- **Pull Request comments**: For code review and technical discussion.
- **Project website**: https://isibheqe.org.za

---

## License

By contributing to CircleOne, you agree that your contributions will be licensed
under the **MIT License**. See the `LICENSE` file in the repository root for the
full text. Note: HeliBoard-derived components in android/ are subject to GPL-3.0.

---

Thank you for helping bring isiBheqe soHlamvu to the digital world.
