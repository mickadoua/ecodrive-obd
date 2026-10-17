import Foundation
import Shared

enum ObdSource: String, CaseIterable, Identifiable {
    case simulator = "Simulateur"
    case bluetoothLe = "Bluetooth LE"
    case wifi = "Wi-Fi"

    var id: String { rawValue }
}

@MainActor
final class LiveModel: ObservableObject {
    @Published var source: ObdSource = .simulator
    @Published var wifiHost = "192.168.0.10"
    @Published private(set) var reading: LiveReading?
    @Published private(set) var error: String?
    @Published private(set) var running = false

    private var session: LiveSession?

    func connect() {
        session?.close()
        error = nil
        let session = IosObd.shared.session(transport: transport())
        self.session = session
        running = true
        session.start(onReading: { [weak self] in self?.reading = $0 }, onError: { [weak self] in
            self?.error = $0
            self?.running = false
        })
    }

    func disconnect() {
        session?.close()
        session = nil
        running = false
    }

    private func transport() -> ObdTransport {
        switch source {
        case .simulator: return IosObd.shared.simulator()
        case .bluetoothLe: return IosObd.shared.bluetoothLe()
        case .wifi: return IosObd.shared.wifi(host: wifiHost, port: 35000)
        }
    }
}
