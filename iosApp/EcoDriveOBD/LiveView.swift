import SwiftUI
import Shared

struct LiveView: View {
    @StateObject private var model = LiveModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    if let r = model.reading {
                        consumption(r)
                        ScoreGauge(score: r.ecoScore, instant: r.instantScore)
                        Text(r.hint).font(.headline).multilineTextAlignment(.center)
                        HStack {
                            stat("Vitesse", String(format: "%.0f km/h", r.speedKmh))
                            stat("Pente", String(format: "%+.1f %%", r.grade * 100))
                            stat("Trajet", String(format: "%.1f km", r.tripKm))
                        }
                        Text("Mesure : \(r.method)").font(.footnote).foregroundStyle(.secondary)
                    } else {
                        Text("Non connecté").foregroundStyle(.secondary).padding(.top, 40)
                    }
                    if let error = model.error {
                        Text(error).foregroundStyle(.red)
                    }
                    controls
                }
                .padding()
            }
            .navigationTitle("Direct")
        }
    }

    private func consumption(_ r: LiveReading) -> some View {
        VStack {
            if let l100 = r.litersPer100?.doubleValue {
                Text(String(format: "%.1f", l100)).font(.system(size: 64, weight: .bold, design: .rounded))
                Text("L/100 km").foregroundStyle(.secondary)
            } else if let lh = r.litersPerHour?.doubleValue {
                Text(String(format: "%.2f", lh)).font(.system(size: 64, weight: .bold, design: .rounded))
                Text("L/h").foregroundStyle(.secondary)
            } else {
                Text("—").font(.system(size: 64, weight: .bold))
            }
        }
    }

    private func stat(_ label: String, _ value: String) -> some View {
        VStack {
            Text(value).font(.title3.monospacedDigit())
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }

    private var controls: some View {
        VStack(spacing: 12) {
            Picker("Source", selection: $model.source) {
                ForEach(ObdSource.allCases) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)
            .disabled(model.running)
            if model.source == .wifi {
                TextField("Adresse du boîtier", text: $model.wifiHost)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.decimalPad)
            }
            Button(model.running ? "Déconnecter" : "Connecter") {
                model.running ? model.disconnect() : model.connect()
            }
            .buttonStyle(.borderedProminent)
        }
    }
}

struct ScoreGauge: View {
    let score: Double
    let instant: Double

    var body: some View {
        ZStack {
            Circle().stroke(Color.secondary.opacity(0.2), lineWidth: 14)
            Circle()
                .trim(from: 0, to: score / 100)
                .stroke(color, style: StrokeStyle(lineWidth: 14, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack {
                Text(String(format: "%.0f", score)).font(.system(size: 44, weight: .bold, design: .rounded))
                Text("éco-score · instant \(Int(instant))").font(.caption).foregroundStyle(.secondary)
            }
        }
        .frame(width: 180, height: 180)
    }

    private var color: Color {
        switch score {
        case 75...: return .green
        case 50..<75: return .orange
        default: return .red
        }
    }
}
