/* DRAFT copy: Léa replaces the texts. Facts taken from the project README (no figures invented). */
window.SITE = {
  name: 'EcoDrive OBD', logo: 'logo.png',
  repo: 'https://github.com/mickadoua/ecodrive-obd',
  dmcPage: { en: 'https://www.dm-consulting.tech/en/open-source/ecodrive-obd', fr: 'https://www.dm-consulting.tech/fr/open-source/ecodrive-obd' },
  common: { license: 'Apache-2.0', licenseUrl: 'https://github.com/mickadoua/ecodrive-obd/blob/main/LICENSE' },
  download: {
    // play: 'https://play.google.com/store/apps/details?id=tech.dmconsulting.ecodrive', playLive: false,   // enable when the Play listing is public (404 on 15/11/2026)
    // APK of the latest release; the SHA-256 is the one in ecodrive-obd.apk.sha256 of that release.
    apk: { url: 'https://www.dm-consulting.tech/downloads/ecodrive-obd.apk', sha256: '414931f2f471e44e87e442f594696f516a01bf77350c6ad4ddde9de99cfd9147', size: '' }
  },
  shots: [{ src: 'direct.jpg' }, { src: 'trajet.jpg' }, { src: 'carte-exemple.jpg' }, { src: 'vehicule.jpg' }],
  i18n: {
    en: { tagline: 'Real-time fuel consumption over OBD-II', eyebrow: 'Android app · Apache-2.0',
      h1: 'Eco-driving that <em>understands hills.</em>',
      lede: 'EcoDrive OBD measures your car’s fuel consumption in real time through a Bluetooth OBD-II module, and scores your driving while accounting for road gradient.',
      shotCaptions: ['Live dashboard', 'Trip chart', 'Over-consumption map (simulated trip)', 'Vehicle profile'],
      shotNote: 'The app interface is in French.',
      featuresKicker: 'Features', featuresTitle: 'Fair to the driver.', featuresIntro: 'Climbing is not penalised. Accelerating downhill is.',
      features: [
        { icon: 'gauge', title: 'Live consumption', text: 'Instant and average L/100 km, read over OBD-II from a Bluetooth ELM327 module.' },
        { icon: 'chart', title: 'Gradient-aware score', text: 'Actual consumption is compared with a physical model for the current speed and slope.' },
        { icon: 'map', title: 'Over-consumption map', text: 'OpenStreetMap map aggregated per road, with a per-day detail view.' }],
      highlights: { kicker: 'Details', title: 'Try it without a car.', intro: 'A built-in ELM327 simulator answers with real OBD frames.',
        chips: ['ELM327', 'OBD-II', 'OpenStreetMap', 'Barometer elevation'],
        checks: [['Trip kept.', 'The trip survives an app restart while the engine runs.'], ['Auto-start.', 'Starts when the phone connects to the car over Bluetooth.'], ['No background location.', 'The app never asks for it.']],
        facts: [['Platform', 'Android'], ['Adapter', 'Bluetooth ELM327'], ['License', 'Apache-2.0']] },
      downloadTitle: 'Get EcoDrive OBD', downloadIntro: 'Install the signed APK on an Android phone, after checking its SHA-256.', downloadNote: 'Requires a Bluetooth OBD-II (ELM327) module for real driving.' },
    fr: { tagline: 'Consommation en temps réel via OBD-II', eyebrow: 'Appli Android · Apache-2.0',
      h1: 'Une éco-conduite qui <em>comprend les côtes.</em>',
      lede: 'EcoDrive OBD mesure la consommation de votre voiture en temps réel via un module OBD-II Bluetooth, et note votre conduite en tenant compte de la pente.',
      shotCaptions: ['Tableau de bord en direct', 'Courbe du trajet', 'Carte des surconsommations (trajet simulé)', 'Profil du véhicule'],
      shotNote: 'L’interface de l’appli est en français.',
      featuresKicker: 'Fonctions', featuresTitle: 'Juste envers le conducteur.', featuresIntro: 'Monter une côte n’est pas pénalisé. Accélérer en descente, si.',
      features: [
        { icon: 'gauge', title: 'Consommation en direct', text: 'L/100 km instantanés et moyens, lus en OBD-II depuis un module Bluetooth ELM327.' },
        { icon: 'chart', title: 'Note tenant compte de la pente', text: 'La consommation réelle est comparée à un modèle physique pour la vitesse et la pente du moment.' },
        { icon: 'map', title: 'Carte des surconsommations', text: 'Carte OpenStreetMap agrégée par route, avec le détail jour par jour.' }],
      highlights: { kicker: 'Détails', title: 'À essayer sans voiture.', intro: 'Un simulateur ELM327 intégré répond avec de vraies trames OBD.',
        chips: ['ELM327', 'OBD-II', 'OpenStreetMap', 'Altitude barométrique'],
        checks: [['Trajet conservé.', 'Le trajet survit à un redémarrage de l’appli, moteur en marche.'], ['Démarrage auto.', 'Démarre quand le téléphone se connecte à la voiture en Bluetooth.'], ['Pas de position en arrière-plan.', 'L’appli ne la demande jamais.']],
        facts: [['Plateforme', 'Android'], ['Adaptateur', 'ELM327 Bluetooth'], ['Licence', 'Apache-2.0']] },
      downloadTitle: 'Obtenir EcoDrive OBD', downloadIntro: 'Installez l’APK signé sur un téléphone Android, après avoir vérifié sa somme SHA-256.', downloadNote: 'Un module OBD-II (ELM327) Bluetooth est nécessaire pour rouler.' }
  }
};
