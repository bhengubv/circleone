import UIKit

protocol KeyboardViewDelegate: AnyObject {
    func didTapKey(_ key: String)
    func didTapBackspace()
    func didTapSpace()
    func didTapReturn()
    func didTapNextKeyboard()
}

class KeyboardView: UIView {

    weak var delegate: KeyboardViewDelegate?

    // QWERTY rows
    private let row1 = ["q","w","e","r","t","y","u","i","o","p"]
    private let row2 = ["a","s","d","f","g","h","j","k","l"]
    private let row3 = ["z","x","c","v","b","n","m"]

    private var stackView: UIStackView!

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupKeyboard()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupKeyboard()
    }

    private func setupKeyboard() {
        backgroundColor = UIColor(red: 0.82, green: 0.84, blue: 0.86, alpha: 1.0)

        stackView = UIStackView()
        stackView.axis = .vertical
        stackView.distribution = .fillEqually
        stackView.spacing = 6
        stackView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stackView)

        NSLayoutConstraint.activate([
            stackView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 4),
            stackView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -4),
            stackView.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            stackView.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8)
        ])

        stackView.addArrangedSubview(createRow(keys: row1))
        stackView.addArrangedSubview(createRow(keys: row2))
        stackView.addArrangedSubview(createRow(keys: row3))
        stackView.addArrangedSubview(createBottomRow())
    }

    private func createRow(keys: [String]) -> UIStackView {
        let row = UIStackView()
        row.axis = .horizontal
        row.distribution = .fillEqually
        row.spacing = 4

        for key in keys {
            let button = createKeyButton(title: key)
            button.addTarget(self, action: #selector(keyTapped(_:)), for: .touchUpInside)
            row.addArrangedSubview(button)
        }

        return row
    }

    private func createBottomRow() -> UIStackView {
        let row = UIStackView()
        row.axis = .horizontal
        row.distribution = .fill
        row.spacing = 4

        // Globe key (Next Keyboard) — MANDATORY for App Store approval
        let globeButton = createKeyButton(title: "🌐")
        globeButton.addTarget(self, action: #selector(nextKeyboardTapped), for: .touchUpInside)
        globeButton.widthAnchor.constraint(equalToConstant: 44).isActive = true
        row.addArrangedSubview(globeButton)

        // Space bar
        let spaceButton = createKeyButton(title: "space")
        spaceButton.addTarget(self, action: #selector(spaceTapped), for: .touchUpInside)
        row.addArrangedSubview(spaceButton)

        // Backspace
        let backspaceButton = createKeyButton(title: "⌫")
        backspaceButton.addTarget(self, action: #selector(backspaceTapped), for: .touchUpInside)
        backspaceButton.widthAnchor.constraint(equalToConstant: 44).isActive = true
        row.addArrangedSubview(backspaceButton)

        // Return
        let returnButton = createKeyButton(title: "↵")
        returnButton.addTarget(self, action: #selector(returnTapped), for: .touchUpInside)
        returnButton.widthAnchor.constraint(equalToConstant: 60).isActive = true
        row.addArrangedSubview(returnButton)

        return row
    }

    private func createKeyButton(title: String) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 22)
        button.setTitleColor(.black, for: .normal)
        button.backgroundColor = .white
        button.layer.cornerRadius = 5
        button.layer.shadowColor = UIColor.black.cgColor
        button.layer.shadowOffset = CGSize(width: 0, height: 1)
        button.layer.shadowOpacity = 0.2
        button.layer.shadowRadius = 0.5
        return button
    }

    // MARK: - Actions

    @objc private func keyTapped(_ sender: UIButton) {
        guard let key = sender.title(for: .normal) else { return }
        delegate?.didTapKey(key)
    }

    @objc private func nextKeyboardTapped() {
        delegate?.didTapNextKeyboard()
    }

    @objc private func spaceTapped() {
        delegate?.didTapSpace()
    }

    @objc private func backspaceTapped() {
        delegate?.didTapBackspace()
    }

    @objc private func returnTapped() {
        delegate?.didTapReturn()
    }
}
