import SwiftUI
import Lottie
import Multipaz

struct ProximityPresentmentScreen: View {
    @Environment(ViewModel.self) private var viewModel
    let documentId: String

    private var matchingDocInfos: [DocumentInfo] {
        let selectedIds = viewModel.proximityPresentmentModel.selectedDocuments.map { $0.identifier }
        let ids = selectedIds.isEmpty ? [documentId] : selectedIds
        return ids.compactMap { id in
            viewModel.documentModel.documentInfos.first(where: { $0.document.identifier == id })
        }
    }

    private func cardDimensions(geometry: GeometryProxy) -> (width: CGFloat, height: CGFloat) {
        guard geometry.size.width > 32, geometry.size.height > 0 else {
            return (0, 0)
        }
        let availableWidth = geometry.size.width - 32
        let maxCardHeight = geometry.size.height / 3
        var cardWidth = availableWidth
        var cardHeight = cardWidth / 1.586
        if cardHeight > maxCardHeight {
            cardHeight = maxCardHeight
            cardWidth = cardHeight * 1.586
        }
        return (cardWidth, cardHeight)
    }

    var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 0) {
                cardsView(geometry: geometry)
                    .padding(.top, 24)

                statusContentView
                    .padding(.top, 24)

                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color(uiColor: .systemBackground).ignoresSafeArea())
        .task(id: viewModel.proximityPresentmentModel.state) {
            if case .completed = viewModel.proximityPresentmentModel.state {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                viewModel.dismissProximityPresentment()
            }
        }
        .onDisappear {
            if case .completed = viewModel.proximityPresentmentModel.state {
                viewModel.proximityPresentmentModel.reset()
            } else {
                viewModel.proximityPresentmentModel.cancel()
            }
        }
    }

    @ViewBuilder
    private func cardsView(geometry: GeometryProxy) -> some View {
        let (cardWidth, cardHeight) = cardDimensions(geometry: geometry)
        let docInfos = matchingDocInfos

        if docInfos.isEmpty || cardWidth <= 0 || cardHeight <= 0 {
            EmptyView()
        } else if docInfos.count == 1 {
            let docInfo = docInfos[0]
            ZStack(alignment: .topTrailing) {
                Image(uiImage: docInfo.cardArt)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: cardWidth, height: cardHeight)
                    .clipShape(RoundedRectangle(cornerRadius: 24))

                CardBadgesView(badges: docInfo.badges)
            }
            .frame(width: cardWidth, height: cardHeight)
            .shadow(color: Color.black.opacity(0.18), radius: 12, x: 0, y: 6)
        } else {
            let scale: CGFloat = 0.85
            let cardW = cardWidth * scale
            let cardH = cardHeight * scale
            let maxXOffset = cardWidth * (1.0 - scale)
            let maxYOffset = cardHeight * (1.0 - scale)
            let count = docInfos.count

            ZStack(alignment: .topLeading) {
                ForEach(Array(docInfos.enumerated()), id: \.element.document.identifier) { index, docInfo in
                    let offsetX = maxXOffset * (CGFloat(index) / CGFloat(count - 1))
                    let offsetY = maxYOffset * (CGFloat(index) / CGFloat(count - 1))

                    ZStack(alignment: .topTrailing) {
                        Image(uiImage: docInfo.cardArt)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: cardW, height: cardH)
                            .clipShape(RoundedRectangle(cornerRadius: 24 * scale))

                        CardBadgesView(badges: docInfo.badges)
                    }
                    .frame(width: cardW, height: cardH)
                    .shadow(color: Color.black.opacity(0.18), radius: 12, x: 0, y: 6)
                    .offset(x: offsetX, y: offsetY)
                    .zIndex(Double(index))
                }
            }
            .frame(width: cardWidth, height: cardHeight, alignment: .topLeading)
        }
    }

    @ViewBuilder
    private var statusContentView: some View {
        switch viewModel.proximityPresentmentModel.state {
        case .idle, .generatingQrCode, .readyToShowQrCode, .connecting, .waitingForRequest, .sendingResponse:
            ProgressView()
                .controlSize(.large)

        case .waitingForUserInput:
            EmptyView()

        case .completed(let errorMessage, let isCannotSatisfy):
            VStack(spacing: 24) {
                if errorMessage != nil {
                    LottieView(animation: .named("error_animation"))
                        .playing(loopMode: .playOnce)
                        .resizable()
                        .frame(width: 120, height: 120)

                    if isCannotSatisfy {
                        Text("Cannot satisfy request")
                            .font(.headline)
                            .foregroundColor(.primary)
                    } else {
                        Text("Something went wrong")
                            .font(.headline)
                            .foregroundColor(.primary)
                    }
                } else {
                    LottieView(animation: .named("success_animation"))
                        .playing(loopMode: .playOnce)
                        .resizable()
                        .frame(width: 120, height: 120)

                    Text("The info was shared")
                        .font(.headline)
                        .foregroundColor(.primary)
                }
            }
        }
    }
}
