import SwiftUI

struct ContentView: View {
    @State private var keyboardEnabled = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("isiBheqe soHlamvu")
                            .font(.largeTitle)
                            .fontWeight(.bold)
                        Text("The keyboard that writes every language.")
                            .font(.title3)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 8)
                }

                Section("Setup") {
                    NavigationLink {
                        SetupInstructionsView()
                    } label: {
                        Label("Enable Keyboard", systemImage: "keyboard")
                    }
                }

                Section("How It Works") {
                    NavigationLink {
                        HowItWorksView()
                    } label: {
                        Label("Vowel Shapes", systemImage: "triangle")
                    }
                    NavigationLink {
                        ConsonantGuideView()
                    } label: {
                        Label("Consonant Marks", systemImage: "pencil.and.outline")
                    }
                }

                Section("About") {
                    LabeledContent("Version", value: "0.1.0")
                    LabeledContent("Script Creator", value: "Mqondisi Bhebhe")
                    Link(destination: URL(string: "https://isibheqe.org.za")!) {
                        Label("isibheqe.org.za", systemImage: "globe")
                    }
                    Link(destination: URL(string: "https://github.com/bhengubv/circleone")!) {
                        Label("Source Code", systemImage: "chevron.left.forwardslash.chevron.right")
                    }
                }
            }
            .navigationTitle("CircleOne")
        }
    }
}

struct SetupInstructionsView: View {
    var body: some View {
        List {
            Section {
                Text("Follow these steps to enable CircleOne:")
                    .font(.headline)
            }
            Section {
                Label("Open Settings", systemImage: "1.circle.fill")
                Label("Go to General → Keyboard → Keyboards", systemImage: "2.circle.fill")
                Label("Tap Add New Keyboard...", systemImage: "3.circle.fill")
                Label("Select CircleOne", systemImage: "4.circle.fill")
                Label("Switch to CircleOne using the globe key", systemImage: "5.circle.fill")
            }
            Section {
                Text("CircleOne does not request Full Access. Your keystrokes never leave your device.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Enable Keyboard")
    }
}

struct HowItWorksView: View {
    var body: some View {
        List {
            Section("Vowel Shapes") {
                Text("Each vowel has a unique directional shape:")
                VowelRow(vowel: "a", shape: "△", description: "Upward triangle — open central")
                VowelRow(vowel: "e", shape: "◁", description: "Left triangle — front vowel")
                VowelRow(vowel: "i", shape: "△", description: "Narrow upward triangle — close front")
                VowelRow(vowel: "o", shape: "▷", description: "Right triangle — back vowel")
                VowelRow(vowel: "u", shape: "▽", description: "Downward triangle — close back")
            }
            Section {
                Text("The shape tells you how the sound is produced. Direction encodes front/back, orientation encodes open/close.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Vowel Shapes")
    }
}

struct VowelRow: View {
    let vowel: String
    let shape: String
    let description: String

    var body: some View {
        HStack {
            Text(shape)
                .font(.title)
                .frame(width: 44)
            VStack(alignment: .leading) {
                Text(vowel)
                    .font(.headline)
                Text(description)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

struct ConsonantGuideView: View {
    var body: some View {
        List {
            Section {
                Text("Type Latin transliteration and see isiBheqe glyphs. Consonant strokes cross through the vowel shape.")
            }
            Section("Examples") {
                LabeledContent("ba", value: "△ with horizontal line")
                LabeledContent("sa", value: "△ with diagonal")
                LabeledContent("kwa", value: "△ with cross + circle")
                LabeledContent("ngu", value: "▽ with circle-cross")
            }
        }
        .navigationTitle("Consonant Marks")
    }
}

#Preview {
    ContentView()
}
