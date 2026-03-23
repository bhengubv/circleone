import UIKit

class KeyboardViewController: UIInputViewController {

    private var keyboardView: KeyboardView!
    private let engine = TransliterationEngine()

    override func viewDidLoad() {
        super.viewDidLoad()
        keyboardView = KeyboardView(frame: .zero)
        keyboardView.translatesAutoresizingMaskIntoConstraints = false
        keyboardView.delegate = self
        view.addSubview(keyboardView)

        NSLayoutConstraint.activate([
            keyboardView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            keyboardView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            keyboardView.topAnchor.constraint(equalTo: view.topAnchor),
            keyboardView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            keyboardView.heightAnchor.constraint(equalToConstant: 260)
        ])
    }

    override func textWillChange(_ textInput: UITextInput?) {
        // Called when the text is about to change
    }

    override func textDidChange(_ textInput: UITextInput?) {
        // Called when the text has changed
    }
}

// MARK: - KeyboardViewDelegate

extension KeyboardViewController: KeyboardViewDelegate {

    func didTapKey(_ key: String) {
        let proxy = textDocumentProxy

        // Feed the key to the transliteration engine
        if let result = engine.processKey(key) {
            // Delete the buffered Latin characters
            for _ in 0..<result.deleteCount {
                proxy.deleteBackward()
            }
            // Insert the isiBheqe glyph (PUA character)
            proxy.insertText(result.output)
        } else {
            // No transliteration match — insert literal
            proxy.insertText(key)
        }
    }

    func didTapBackspace() {
        textDocumentProxy.deleteBackward()
        engine.reset()
    }

    func didTapSpace() {
        textDocumentProxy.insertText(" ")
        engine.reset()
    }

    func didTapReturn() {
        textDocumentProxy.insertText("\n")
        engine.reset()
    }

    func didTapNextKeyboard() {
        // MANDATORY — Apple will reject without this
        advanceToNextInputMode()
    }
}
