import Foundation

/// Result of processing a key through the transliteration engine.
struct TransliterationResult {
    /// Number of characters to delete from the text proxy (buffered Latin chars).
    let deleteCount: Int
    /// The string to insert (isiBheqe PUA character).
    let output: String
}

/// Converts Latin keystroke sequences into isiBheqe PUA codepoints.
///
/// The engine buffers consonant keystrokes and emits a PUA glyph when a vowel
/// completes a CV syllable. The mapping is loaded from the bundled CSV file.
class TransliterationEngine {

    /// Maps Latin input strings (e.g. "ba", "ngu") to PUA codepoints.
    private var cvMap: [String: UInt32] = [:]

    /// Current consonant buffer (e.g. "n" → "ng" → "ngw").
    private var buffer: String = ""

    /// Set of valid consonant prefixes for buffering.
    private var validPrefixes: Set<String> = []

    private let vowels: Set<Character> = ["a", "e", "i", "o", "u"]

    init() {
        loadMap()
    }

    /// Load the PUA mapping from the bundled CSV.
    private func loadMap() {
        // Try keyboard extension bundle first, then main bundle
        let bundles = [Bundle(for: TransliterationEngine.self), Bundle.main]

        for bundle in bundles {
            guard let url = bundle.url(forResource: "one-pua-map", withExtension: "csv"),
                  let content = try? String(contentsOf: url, encoding: .utf8) else {
                continue
            }

            let lines = content.components(separatedBy: .newlines)
            for line in lines.dropFirst() { // skip header
                let cols = line.components(separatedBy: ",")
                guard cols.count >= 3 else { continue }
                let hexStr = cols[0].trimmingCharacters(in: .whitespaces)
                    .replacingOccurrences(of: "0x", with: "")
                let input = cols[1].trimmingCharacters(in: .whitespaces)
                guard let codepoint = UInt32(hexStr, radix: 16) else { continue }
                cvMap[input] = codepoint
            }
            break
        }

        // Build valid prefix set for consonant buffering
        for key in cvMap.keys {
            // Extract consonant part (everything before the vowel)
            if let lastChar = key.last, vowels.contains(lastChar) {
                let consonant = String(key.dropLast())
                if !consonant.isEmpty {
                    // Add all prefixes of this consonant
                    for i in 1...consonant.count {
                        validPrefixes.insert(String(consonant.prefix(i)))
                    }
                }
            }
        }
    }

    /// Process a single key press. Returns a TransliterationResult if a glyph
    /// should be emitted, or nil if the key was buffered.
    func processKey(_ key: String) -> TransliterationResult? {
        let char = Character(key.lowercased())

        // If it's a vowel, try to complete a syllable
        if vowels.contains(char) {
            let syllable = buffer + String(char)

            if let codepoint = cvMap[syllable] {
                let deleteCount = buffer.count // delete the buffered consonant chars
                buffer = ""
                guard let scalar = Unicode.Scalar(codepoint) else { return nil }
                return TransliterationResult(
                    deleteCount: deleteCount,
                    output: String(scalar)
                )
            }

            // Pure vowel (no consonant buffer)
            if buffer.isEmpty, let codepoint = cvMap[String(char)] {
                guard let scalar = Unicode.Scalar(codepoint) else { return nil }
                return TransliterationResult(deleteCount: 0, output: String(scalar))
            }

            // No match — flush buffer as literal + insert vowel literal
            buffer = ""
            return nil
        }

        // It's a consonant — try to extend the buffer
        let extended = buffer + String(char)
        if validPrefixes.contains(extended) {
            buffer = extended
            return nil // buffered, no output yet
        }

        // Extended buffer is not a valid prefix — flush old buffer and start new
        buffer = validPrefixes.contains(String(char)) ? String(char) : ""
        return nil
    }

    /// Reset the engine state (e.g. on space, backspace, or return).
    func reset() {
        buffer = ""
    }
}
