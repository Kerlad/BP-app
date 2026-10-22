// Oracle for stats_golden.json: prototype logic from design_handoff_bp_diary/BPApp.dc.html
// (makeData() lines 344-358 copied literally; stats lines 480-489 reproduced below).
// Run: TZ=Europe/Moscow node stats_oracle.js > stats_golden.json
// Only intended difference from the prototype: Max/Min ties pick the LATEST reading (decision D-21);
// the prototype keeps the first (earliest) one with a strict comparison.
class P {
  state = { th: { hs: 135, hd: 85, ls: 100, ld: 60, pl: 50, ph: 90 } };
  cls(sys, dia) {
    const t = this.state.th;
    if (sys >= t.hs || dia >= t.hd) return 'hi';
    if (sys <= t.ls || dia <= t.ld) return 'lo';
    return 'ok';
  }
  now() { return new Date(2026, 9, 20, 9, 12); }
  makeData() {
    let s = 11; const rnd = () => (s = (s * 16807) % 2147483647) / 2147483647;
    const N = this.now(), out = [];
    for (let d = 29; d >= 0; d--) for (const h of [7, 21]) {
      const t = new Date(N.getFullYear(), N.getMonth(), N.getDate() - d, h, 5 + Math.floor(rnd() * 40)).getTime();
      if (t > N.getTime()) continue;
      const ev = h > 12 ? 5 : 0, tr = -(29 - d) * 0.3, sp = rnd() < 0.12 ? 14 : 0, dp = rnd() < 0.06 ? -24 : 0;
      out.push({ id: out.length + 1, t, arm: 'Левая', tags: [],
        sys: Math.round(134 + ev + tr + sp + dp + (rnd() - .5) * 16),
        dia: Math.round(86 + ev * .6 + tr * .5 + sp * .5 + dp * .6 + (rnd() - .5) * 10),
        pulse: Math.round(71 + (rnd() - .5) * 18) });
    }
    return out.reverse();
  }
  stats(period) {
    const N = this.now(), R = this.makeData();
    const from = new Date(N.getFullYear(), N.getMonth(), N.getDate() - (period - 1)).getTime();
    const pts = R.filter(r => r.t >= from).sort((a, b) => a.t - b.t), n = pts.length;
    const avg = k => Math.round(pts.reduce((a, p) => a + p[k], 0) / n);
    const cnt = { hi: 0, ok: 0, lo: 0 }; pts.forEach(p => cnt[this.cls(p.sys, p.dia)]++);
    // D-21: >= and <= instead of the prototype's > and <, so the later reading wins a tie.
    const mx = pts.reduce((a, p) => p.sys >= a.sys ? p : a, pts[0]), mn = pts.reduce((a, p) => p.sys <= a.sys ? p : a, pts[0]);
    const am = pts.filter(p => new Date(p.t).getHours() < 12), pm = pts.filter(p => new Date(p.t).getHours() >= 12);
    const av2 = a => a.length ? { sys: Math.round(a.reduce((s, p) => s + p.sys, 0) / a.length), dia: Math.round(a.reduce((s, p) => s + p.dia, 0) / a.length), count: a.length } : null;
    return { count: n, avgSys: avg('sys'), avgDia: avg('dia'), avgPulse: avg('pulse'),
      morning: av2(am), evening: av2(pm),
      max: { at: new Date(mx.t).toISOString(), sys: mx.sys, dia: mx.dia },
      min: { at: new Date(mn.t).toISOString(), sys: mn.sys, dia: mn.dia },
      countHigh: cnt.hi, countNormal: cnt.ok, countLow: cnt.lo, inRangePercent: n ? Math.round(cnt.ok / n * 100) : 0 };
  }
}
const p = new P();
console.log(JSON.stringify({ p7: p.stats(7), p30: p.stats(30) }, null, 2));
