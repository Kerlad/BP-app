// Oracle for DemoData: makeData() copied literally from design_handoff_bp_diary/BPApp.dc.html (lines 344-358).
// Run: TZ=Europe/Moscow node demo_data_oracle.js > demo_data_golden.csv
class P {
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
}
for (const r of new P().makeData()) console.log([new Date(r.t).toISOString(), r.sys, r.dia, r.pulse].join(','));
