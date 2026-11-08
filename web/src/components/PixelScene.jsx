import { useMemo } from 'react';

function buildRects(sprite) {
  const { palette, rows } = sprite;
  const cols = rows[0].length;
  const rects = [];

  rows.forEach((row, y) => {
    let x = 0;
    while (x < cols) {
      const ch = row[x];
      const color = palette[ch];
      if (!color) {
        x += 1;
        continue;
      }
      let run = 1;
      while (x + run < cols && row[x + run] === ch) run += 1;
      rects.push({ x, y, w: run, color });
      x += run;
    }
  });

  return rects;
}

export default function PixelScene({ sprite, width = 220, className = '' }) {
  const rects = useMemo(() => buildRects(sprite), [sprite]);
  const cols = sprite.rows[0].length;
  const rows = sprite.rows.length;
  const height = Math.round((width * rows) / cols);

  return (
    <svg
      viewBox={'0 0 ' + cols + ' ' + rows}
      width={width}
      height={height}
      shapeRendering="crispEdges"
      className={className}
      aria-hidden="true"
    >
      {rects.map((r, i) => (
        <rect key={i} x={r.x} y={r.y} width={r.w} height="1" fill={r.color} />
      ))}
    </svg>
  );
}
