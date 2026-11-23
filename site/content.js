/* Home page copy (French and English). Facts come from the README and the app
   code; the commute figures are computed live by drive.js and labelled as a
   simulation. The download block keeps the release data used by every
   project site. */
window.SITE = {
  name: 'EcoDrive OBD', logo: 'logo.png',
  repo: 'https://github.com/mickadoua/ecodrive-obd',
  dmcPage: { en: 'https://www.dm-consulting.tech/en/open-source/ecodrive-obd', fr: 'https://www.dm-consulting.tech/fr/open-source/ecodrive-obd' },
  common: { license: 'Apache-2.0', licenseUrl: 'https://github.com/mickadoua/ecodrive-obd/blob/main/LICENSE' },
  download: {
    // play: 'https://play.google.com/store/apps/details?id=tech.dmconsulting.ecodrive', playLive: false,   // enable when the Play listing is public (404 on 15/11/2026)
    // APK of the latest release; version and versionCode are read in the APK (aapt2 dump badging), builtOn is the day the CI attached it; the SHA-256 is the one in ecodrive-obd.apk.sha256 of that release.
    apk: { url: 'https://www.dm-consulting.tech/downloads/ecodrive-obd.apk', sha256: '414931f2f471e44e87e442f594696f516a01bf77350c6ad4ddde9de99cfd9147', size: '', version: '0.1.0', versionCode: 67, builtOn: '2026-11-16' }
  },
  shots: [{ src: 'direct.jpg' }, { src: 'trajet.jpg' }, { src: 'carte-exemple.jpg' }, { src: 'vehicule.jpg' }],
  // Assumptions shown on the page, used for the yearly figures.
  assumptions: { pricePerLiter: 1.8, co2PerLiter: 2.3, workDays: 220 },
  i18n: {
    fr: {
      tagline: 'Le même trajet, deux conducteurs',
      description: 'EcoDrive OBD lit la consommation réelle de votre voiture par un module OBD-II Bluetooth et vous dit, en roulant, quoi changer. Comparaison simulée sur un trajet domicile-travail.',
      nav: { trip: 'Le trajet', app: 'L’appli', download: 'Télécharger' },
      h1: 'Le même trajet. <em>Deux conducteurs.</em>',
      lede: '25 km entre la maison et le travail : ville, rocade, départementale. À gauche, une conduite habituelle. À droite, la même voiture qui suit les conseils d’EcoDrive.',
      zones: { town: 'Ville', ring: 'Rocade', road: 'Départementale', climb: 'Côte', descent: 'Descente', town2: 'Ville' },
      from: 'Maison', to: 'Travail',
      usualLabel: 'Conduite habituelle', ecoLabel: 'Avec EcoDrive',
      usualTitle: 'Il roule comme d’habitude.', ecoTitle: 'Il suit les conseils de l’appli.',
      speed: 'Vitesse', used: 'Consommé', time: 'Temps',
      usualHint: 'Ce que l’appli lui aurait dit', ecoHint: 'Ce que l’appli lui dit',
      gap: 'Écart', km: 'km',
      resultTitle: 'Arrivé avec {pct} % d’essence en moins.',
      resultText: '{eco} L au lieu de {usual} L pour ces 25 km, en {more} de plus.',
      yearText: 'Sur un an d’allers-retours ({days} jours) : environ {liters} L, {euros} € et {co2} kg de CO₂ en moins.',
      simulation: 'Simulation, pas une mesure : calculée avec le modèle physique de l’appli. Hypothèses en bas de page.',
      appTitle: 'L’appli vous le dit pendant que vous roulez.',
      appIntro: 'Branchez un module OBD-II Bluetooth (ELM327) : EcoDrive lit la consommation réelle de la voiture et la compare à ce qu’elle devrait être pour votre vitesse et la pente du moment.',
      tips: [
        { title: 'Anticiper', text: 'Un feu, un stop ou un rond-point arrive : l’appli vous dit de lever le pied assez tôt pour ne pas freiner.', quote: 'Levez le pied, feu dans 150 m' },
        { title: 'Ne pas dépasser', text: 'Au-dessus de la limitation, chaque km/h coûte, surtout sur voie rapide : la résistance de l’air croît avec le carré de la vitesse.', quote: 'Limitée à 110 : levez le pied, chaque km/h en trop coûte' },
        { title: 'Couper l’injection', text: 'En descente, pied levé, le moteur ne consomme plus. Garder le pied sur l’accélérateur, c’est payer pour freiner ensuite.', quote: 'Descente : levez le pied pour couper l’injection' }
      ],
      fair: 'Monter une côte n’est pas pénalisé : la note tient compte de la pente.',
      shotCaptions: ['Tableau de bord en direct', 'Courbe du trajet', 'Carte des surconsommations (trajet simulé)', 'Profil du véhicule'],
      shotNote: 'L’interface de l’appli est en français.',
      howTitle: 'Comment la simulation est faite',
      how: [
        'Même voiture pour les deux : essence, 1 300 kg, valeurs par défaut de l’appli (roulement 0,012, SCx 0,65 m², rendement 0,25, ralenti 0,6 L/h).',
        'Même trajet : 4 km de ville avec deux feux, un stop et un rond-point ; 12 km de rocade à 110 ; 8 km de départementale à 80 avec une côte et une descente de 5 % et deux ronds-points ; 1 km de ville.',
        'Conduite habituelle : 125 km/h sur la rocade, 88 sur la départementale, freinage tardif (3 m/s²), pied sur l’accélérateur en descente.',
        'Avec EcoDrive : vitesses limites respectées, pied levé tôt avant chaque arrêt ou ralentissement (décélération de 0,6 m/s², sans propulsion), injection coupée en descente.',
        'Les accélérations vives sont signalées mais ne coûtent rien de plus dans ce modèle : elles ne comptent pas dans l’écart.',
        'Sur un an : 2 trajets par jour, {days} jours, essence à {price} €/L, {co2} kg de CO₂ par litre. Ce sont des hypothèses.'
      ],
      howLink: 'Lire le modèle dans le code',
      downloadTitle: 'Obtenir EcoDrive OBD', downloadIntro: 'Installez l’APK signé sur un téléphone Android, après avoir vérifié sa somme SHA-256.', downloadNote: 'Un module OBD-II (ELM327) Bluetooth est nécessaire pour rouler.',
      privacy: 'Confidentialité', privacyUrl: 'privacy/'
    },
    en: {
      tagline: 'The same trip, two drivers',
      description: 'EcoDrive OBD reads your car’s real fuel consumption through a Bluetooth OBD-II module and tells you, while you drive, what to change. Simulated comparison on a commute.',
      nav: { trip: 'The trip', app: 'The app', download: 'Download' },
      h1: 'The same trip. <em>Two drivers.</em>',
      lede: '25 km between home and work: town, ring road, country road. On the left, everyday driving. On the right, the same car following EcoDrive’s advice.',
      zones: { town: 'Town', ring: 'Ring road', road: 'Country road', climb: 'Climb', descent: 'Descent', town2: 'Town' },
      from: 'Home', to: 'Work',
      usualLabel: 'Everyday driving', ecoLabel: 'With EcoDrive',
      usualTitle: 'Driving as usual.', ecoTitle: 'Following the app’s advice.',
      speed: 'Speed', used: 'Fuel used', time: 'Time',
      usualHint: 'What the app would have said', ecoHint: 'What the app says',
      gap: 'Gap', km: 'km',
      resultTitle: 'Arrived using {pct}% less fuel.',
      resultText: '{eco} L instead of {usual} L for these 25 km, taking {more} longer.',
      yearText: 'Over a year of round trips ({days} days): about {liters} L, €{euros} and {co2} kg of CO₂ saved.',
      simulation: 'A simulation, not a measurement: computed with the app’s physical model. Assumptions at the bottom of the page.',
      appTitle: 'The app tells you while you drive.',
      appIntro: 'Plug in a Bluetooth OBD-II module (ELM327): EcoDrive reads the car’s real consumption and compares it with what it should be for your speed and the current slope.',
      tips: [
        { title: 'Anticipate', text: 'A traffic light, a stop sign or a roundabout ahead: the app tells you to lift off early enough not to brake.', quote: 'Levez le pied, feu dans 150 m', quoteEn: 'Lift off, traffic light in 150 m' },
        { title: 'Stay at the limit', text: 'Above the speed limit, every km/h costs, above all on fast roads: air drag grows with the square of speed.', quote: 'Limitée à 110 : levez le pied, chaque km/h en trop coûte', quoteEn: 'Limit 110: lift off, every extra km/h costs' },
        { title: 'Cut the injection', text: 'Downhill with your foot off, the engine uses no fuel. Keeping the accelerator pressed means paying to brake later.', quote: 'Descente : levez le pied pour couper l’injection', quoteEn: 'Downhill: lift off to cut injection' }
      ],
      fair: 'Climbing a hill is not penalised: the score accounts for the slope.',
      shotCaptions: ['Live dashboard', 'Trip chart', 'Over-consumption map (simulated trip)', 'Vehicle profile'],
      shotNote: 'The app interface is in French; the messages above are the app’s own, translated.',
      howTitle: 'How the simulation works',
      how: [
        'Same car for both: petrol, 1,300 kg, the app’s default values (rolling 0.012, drag area 0.65 m², efficiency 0.25, idle 0.6 L/h).',
        'Same trip: 4 km of town with two lights, a stop sign and a roundabout; 12 km of ring road at 110; 8 km of country road at 80 with a 5% climb and descent and two roundabouts; 1 km of town.',
        'Everyday driving: 125 km/h on the ring road, 88 on the country road, late braking (3 m/s²), accelerator pressed downhill.',
        'With EcoDrive: speed limits kept, foot lifted early before every stop or slowdown (0.6 m/s² deceleration, no propulsion), injection cut downhill.',
        'Hard accelerations are flagged but cost nothing extra in this model: they are not counted in the gap.',
        'Over a year: 2 trips a day, {days} days, petrol at €{price}/L, {co2} kg of CO₂ per litre. These are assumptions.'
      ],
      howLink: 'Read the model in the code',
      downloadTitle: 'Get EcoDrive OBD', downloadIntro: 'Install the signed APK on an Android phone, after checking its SHA-256.', downloadNote: 'Requires a Bluetooth OBD-II (ELM327) module for real driving.',
      privacy: 'Privacy', privacyUrl: 'en/privacy/'
    }
  }
};
