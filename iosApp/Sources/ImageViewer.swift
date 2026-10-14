import SwiftUI

struct ImageViewerPresentation: Identifiable {
    let id = UUID()
    let urls: [URL]
    let initialIndex: Int
}

struct ImageViewer: View {
    let presentation: ImageViewerPresentation

    @Environment(\.dismiss) private var dismiss
    @State private var selectedIndex: Int

    init(presentation: ImageViewerPresentation) {
        self.presentation = presentation
        _selectedIndex = State(initialValue: presentation.initialIndex)
    }

    var body: some View {
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()

            GeometryReader { geometry in
                TabView(selection: $selectedIndex) {
                    ForEach(presentation.urls.indices, id: \.self) { index in
                        RemoteImage(url: presentation.urls[index], fallback: "photo", contentMode: .fit)
                            .frame(width: geometry.size.width, height: geometry.size.height)
                            .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }

            HStack {
                Button { dismiss() } label: {
                    Image(systemName: "xmark")
                        .font(.headline.weight(.semibold))
                        .frame(width: 44, height: 44)
                        .background(.black.opacity(0.55), in: Circle())
                }
                .accessibilityLabel("关闭图片")

                Spacer()

                Text("\(selectedIndex + 1) / \(presentation.urls.count)")
                    .font(.subheadline.monospacedDigit().weight(.semibold))
                    .padding(.horizontal, 14)
                    .frame(height: 36)
                    .background(.black.opacity(0.55), in: Capsule())
                    .accessibilityLabel("第 \(selectedIndex + 1) 张，共 \(presentation.urls.count) 张")
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .padding(.top, 12)
        }
        .tint(CoolapkStyle.darkGreen)
        .preferredColorScheme(.dark)
    }
}
