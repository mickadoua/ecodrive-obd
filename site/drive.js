/* Simulation for the home page: the same commute driven twice, by a usual
   driver and by a driver following EcoDrive's hints.

   The vehicle model is the app's own (app/src/main/java/tech/dm/conso/eco/
   VehicleModel.kt, default petrol car): traction power from rolling
   resistance, slope, air drag and acceleration, then expected fuel from engine
   efficiency, plus idle. Nothing here is measured on a real car: the page says
   so and lists the assumptions.

   What makes the two drivers differ is only what the model can compute:
   - speed: the usual driver is over the limit on the ring road and the
     country road (drag grows with the square of speed);
   - anticipation: the usual driver brakes hard at the last moment, the eco
     driver lifts off early and coasts (no propulsion while slowing down);
   - downhill: the usual driver keeps the accelerator pressed, the eco driver
     lifts off so injection is cut.
   Hard acceleration is shown as a hint but costs nothing extra here: the
   model has a constant engine efficiency, so it is not counted as a saving.

   Hints are the app's own French messages (EcoScorer.kt, RoadAhead.kt).
   No dependency; usable from the page (window.EcoDrive) and from Node. */
(function (root) {
  'use strict';

  var CAR = { mass: 1300, rolling: 0.012, dragArea: 0.65, idleLh: 0.6, efficiency: 0.25, energyPerLiter: 32e6 };
  var G = 9.81, AIR = 1.2;

  function powerW(v, grade, accel) {
    var force = CAR.mass * G * (CAR.rolling + grade) + 0.5 * AIR * CAR.dragArea * v * v + CAR.mass * accel;
    return force * v;
  }
  function fuelLh(power) {
    return CAR.idleLh + Math.max(0, power) / (CAR.efficiency * CAR.energyPerLiter) * 3600;
  }

  // A 25 km commute: town, ring road, country road over a hill, town.
  // zones: [from m, to m, limit km/h, grade, kind]
  var ZONES = [
    [0, 4000, 50, 0, 'town'],
    [4000, 16000, 110, 0, 'ring'],
    [16000, 18000, 80, 0, 'road'],
    [18000, 19500, 80, 0.05, 'climb'],
    [19500, 21000, 80, -0.05, 'descent'],
    [21000, 24000, 80, 0, 'road'],
    [24000, 25000, 50, 0, 'town2']
  ];
  // events: [position m, kind (app label), target km/h, wait s]
  var EVENTS = [
    [700, 'feu', 0, 25], [1500, 'stop', 0, 4], [2300, 'feu', 0, 25], [3200, 'rond-point', 30, 0],
    [17000, 'rond-point', 30, 0], [22500, 'rond-point', 30, 0], [24400, 'feu', 0, 25], [25000, 'stop', 0, 0]
  ];
  var LENGTH = 25000;

  var DRIVERS = {
    usual: { speed: { town: 1, ring: 125 / 110, road: 1.1, climb: 1.1, descent: 1.1, town2: 1 }, accel: 2.0, decel: 3.0, coast: false, downhillLh: 3 },
    eco: { speed: { town: 1, ring: 1, road: 1, climb: 1, descent: 1, town2: 1 }, accel: 1.0, decel: 0.6, coast: true, downhillLh: 0 }
  };

  function zoneAt(x) {
    for (var i = 0; i < ZONES.length; i++) if (x < ZONES[i][1]) return ZONES[i];
    return ZONES[ZONES.length - 1];
  }
  function cruiseMs(driver, zone) { return zone[2] * driver.speed[zone[4]] / 3.6; }

  // Speed constraints ahead: events, and zones where the cruise speed drops.
  function constraints(driver) {
    var list = EVENTS.map(function (e) { return { x: e[0], v: e[2] / 3.6, kind: e[1], kmh: e[2], wait: e[3] }; });
    for (var i = 1; i < ZONES.length; i++) {
      var before = cruiseMs(driver, ZONES[i - 1]), after = cruiseMs(driver, ZONES[i]);
      if (after < before) list.push({ x: ZONES[i][0], v: after, kind: 'limitation', kmh: ZONES[i][2], wait: 0 });
    }
    return list.sort(function (a, b) { return a.x - b.x; });
  }

  function run(name) {
    var d = DRIVERS[name], cons = constraints(d), dt = 0.25;
    var x = 0, v = 0, t = 0, litres = 0, next = 0;
    var samples = [], lastSample = -Infinity;
    function sample(state) {
      samples.push({ x: x, t: t, v: v * 3.6, l: litres, state: state.kind, target: state.target, dist: state.dist, zone: zoneAt(Math.min(x, LENGTH - 1))[4] });
      lastSample = x;
    }
    while (x < LENGTH - 0.01) {
      var zone = zoneAt(x), cruise = cruiseMs(d, zone);
      while (next < cons.length && cons[next].x <= x + 0.01) next++;
      // Slow down when the next constraint needs it at this driver's rate.
      var slowFor = null;
      for (var k = next; k < cons.length && cons[k].x - x < 2500; k++) {
        var c = cons[k], dist = cons[k].x - x;
        if (v > c.v + 0.05 && v * v - c.v * c.v >= 2 * d.decel * dist - 0.5) { slowFor = { c: c, dist: dist }; break; }
      }
      var a, kind;
      if (slowFor) { a = -d.decel; kind = d.coast ? 'lift' : 'brake'; }
      else if (v < cruise - 0.05) { a = Math.min(d.accel, (cruise - v) / dt); kind = 'accel'; }
      else if (v > cruise + 0.3) { a = -d.decel; kind = d.coast ? 'lift' : 'brake'; }
      else { a = 0; kind = 'cruise'; }
      var nv = Math.max(0, v + a * dt), dx = (v + nv) / 2 * dt;
      var mean = (v + nv) / 2, grade = zone[3];
      var lh = fuelLh(powerW(mean, grade, a));
      if (d.downhillLh && grade < -0.03 && a >= 0) lh = Math.max(lh, d.downhillLh);
      // Hitting a stop: land on the line, then wait at idle.
      var c0 = cons[next];
      if (c0 && x + dx >= c0.x && c0.v === 0) {
        dx = c0.x - x; nv = 0;
      }
      litres += lh * dt / 3600; t += dt; x += dx; v = nv;
      if (c0 && x >= c0.x - 0.01 && c0.v === 0 && v === 0) {
        x = c0.x; next++;
        if (c0.wait) { sample({ kind: 'idle' }); litres += CAR.idleLh * c0.wait / 3600; t += c0.wait; }
        sample({ kind: 'idle' });
        continue;
      }
      if (x - lastSample >= 10) {
        var state = { kind: kind };
        if (kind === 'lift' || kind === 'brake') { state.target = slowFor ? slowFor.c : null; state.dist = slowFor ? slowFor.dist : 0; }
        if (grade < -0.03 && kind !== 'lift' && kind !== 'brake') state.kind = d.downhillLh ? 'downhillPedal' : 'downhillLift';
        sample(state);
      }
    }
    sample({ kind: 'idle' });
    return { name: name, samples: samples, litres: litres, time: t, length: LENGTH };
  }

  // Reading at a given distance, interpolated between samples.
  function at(result, x) {
    var s = result.samples, lo = 0, hi = s.length - 1;
    // At the start line both cars are still standing.
    if (x <= 0) return { x: 0, t: 0, l: 0, v: 0, state: 'idle', zone: 'town' };
    if (x <= s[0].x) return s[0];
    if (x >= s[hi].x) return s[hi];
    while (hi - lo > 1) { var mid = (lo + hi) >> 1; if (s[mid].x <= x) lo = mid; else hi = mid; }
    var a = s[lo], b = s[hi], k = b.x === a.x ? 0 : (x - a.x) / (b.x - a.x);
    return { x: x, t: a.t + (b.t - a.t) * k, l: a.l + (b.l - a.l) * k, v: a.v + (b.v - a.v) * k, state: a.state, target: a.target, dist: a.dist, zone: a.zone };
  }

  // The app's hint for a reading (EcoScorer.kt texts). For the usual driver,
  // this is what the app would have told them.
  function hint(r, name) {
    var limit = { town: 50, ring: 110, road: 80, climb: 80, descent: 80, town2: 50 }[r.zone];
    if (r.state === 'idle') return 'À l’arrêt';
    if (r.state === 'lift' && r.target) {
      var m = Math.max(10, Math.round(r.dist / 10) * 10);
      return r.target.kind === 'limitation' ? 'Levez le pied, limitation à ' + r.target.kmh + ' dans ' + m + ' m' : 'Levez le pied, ' + r.target.kind + ' dans ' + m + ' m';
    }
    if (r.state === 'brake') return 'Freinage brusque : anticipez';
    if (r.state === 'downhillPedal' || r.state === 'downhillLift') return 'Descente : levez le pied pour couper l’injection';
    if (r.v > limit * 1.1 + 2) return 'Limitée à ' + limit + ' : levez le pied, chaque km/h en trop coûte';
    if (r.state === 'accel') return name === 'usual' ? 'Accélération trop vive' : 'Accélération souple : normal de consommer plus';
    if (r.zone === 'climb') return 'Montée : surconsommation normale';
    return 'Conduite souple, continuez';
  }

  var api = { CAR: CAR, ZONES: ZONES, EVENTS: EVENTS, LENGTH: LENGTH, run: run, at: at, hint: hint, powerW: powerW, fuelLh: fuelLh };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.EcoDrive = api;
})(this);
