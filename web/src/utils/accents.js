export const ACCENTS = [
  '#2fe6c8', // teal
  '#22d3ee', // cyan
  '#38bdf8', // sky
  '#4f8cff', // blue
  '#818cf8', // indigo
  '#a78bfa', // violet
  '#c084fc', // purple
  '#e879f9', // fuchsia
  '#f472b6', // pink
  '#fb7185', // rose
  '#f87171', // red
  '#fb923c', // orange
  '#fbbf24', // amber
  '#a3e635', // lime
  '#4ade80', // green
];

/** 按列表位置分配颜色：频道数 ≤ 15 时保证不重复 */
export function accentAt(index) {
  const n = Math.max(0, Number(index) || 0);
  return ACCENTS[n % ACCENTS.length];
}

/** 单个频道的稳定颜色（无列表上下文时兜底） */
export function accentFor(id) {
  const n = Math.abs(Number(id) || 0);
  return ACCENTS[n % ACCENTS.length];
}

/** 为整组频道生成 id → 颜色映射（按列表位置，过滤时颜色也保持稳定） */
export function accentMap(channels) {
  const map = {};
  (channels || []).forEach((c, i) => {
    map[c.id] = accentAt(i);
  });
  return map;
}
