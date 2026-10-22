// Oracle for geometry_golden.txt: prototype logic from design_handoff_bp_diary/BPApp.dc.html
// (makeData, dayDiff, y, ym copied literally; the week-bar loop (lines 433-442) and the line chart
// (lines 480-486) are reproduced below, printing numbers instead of SVG paths).
// Run: TZ=Europe/Moscow node geometry_oracle.js > geometry_golden.txt
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
  dayDiff(t) { const a = new Date(t), b = this.now(); return Math.round((new Date(b.getFullYear(), b.getMonth(), b.getDate()) - new Date(a.getFullYear(), a.getMonth(), a.getDate())) / 864e5); }
  y(v) { return 190 - (Math.min(180, Math.max(50, v)) - 50) / 130 * 180; }
  ym(v) { return 112 - (Math.min(180, Math.max(50, v)) - 50) / 130 * 104; }
  print() {
    const N = this.now(), R = this.makeData(), th = this.state.th, out = [];
    let wS = 0, wD = 0, wn = 0;
    for (let i = 6; i >= 0; i--) {
      const dr = R.filter(r => this.dayDiff(r.t) === i), x = ((6 - i) + .5) * 300 / 7;
      if (!dr.length) continue;
      const as = dr.reduce((a, r) => a + r.sys, 0) / dr.length, ad = dr.reduce((a, r) => a + r.dia, 0) / dr.length;
      dr.forEach(r => { wS += r.sys; wD += r.dia; wn++; });
      const mx = Math.max(...dr.map(r => r.sys)), mn = Math.min(...dr.map(r => r.dia));
      out.push(['bar', i, x.toFixed(3), this.ym(mx).toFixed(3), this.ym(mn).toFixed(3), this.cls(as, ad)].join(','));
    }
    out.push(['week', Math.round(wS / wn), Math.round(wD / wn), this.ym(th.hs).toFixed(3)].join(','));
    for (const period of [7, 30]) {
      const from = new Date(N.getFullYear(), N.getMonth(), N.getDate() - (period - 1)).getTime();
      const pts = R.filter(r => r.t >= from).sort((a, b) => a.t - b.t), n = pts.length;
      const X = i => n > 1 ? 34 + i * 280 / (n - 1) : 174;
      pts.forEach((p, i) => out.push(['line' + period, i, X(i).toFixed(3), this.y(p.sys).toFixed(3), this.y(p.dia).toFixed(3),
        this.cls(p.sys, p.dia) === 'hi'].join(',')));
      const clipped = pts.some(p => [p.sys, p.dia].some(v => v < 50 || v > 180));
      out.push(['meta' + period, n, clipped, this.y(th.hs).toFixed(3), this.y(th.hd).toFixed(3)].join(','));
    }
    return out.join('\n');
  }
}
console.log(new P().print());
