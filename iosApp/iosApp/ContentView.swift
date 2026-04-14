//
//  ContentView.swift
//  iosApp
//
//  Created by David Zeuthen on 3/22/26.
//

import SwiftUI
import Multipaz

struct ContentView: View {
    @State private var viewModel = ViewModel()
    
    var body: some View {
        NavigationStack(path: $viewModel.path) {
            VStack {
                if (viewModel.isLoading) {
                    VStack {
                        ProgressView()
                    }
                } else {
                    StartScreen()
                }
            }
            .navigationDestination(for: Destination.self) { destination in
                PromptDialogs(promptModel: viewModel.promptModel)
                switch destination {
                case .startScreen: StartScreen()
                case .settingsScreen: SettingsScreen()
                }
            }
        }
        .environment(viewModel)
        .onAppear {
            Task {
                await viewModel.load()
                do {
                    try await viewModel.walletClient.refresh()
                } catch {
                    print("refresh() on start-up failed: \(error)")
                }

            }
        }
    }
}

#Preview {
    ContentView()
}
