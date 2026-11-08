import { useMemo } from 'react';
import sprite from './sheepSprite.json';

const PALETTE = sprite.palette;
const ROWS = sprite.rows;

// 横向合并同色像素，减少 SVG 节点
function buildRects() {
  const cols = ROWS[0].length;
  const rects = [];

  ROWS.forEach((row, y) => {
    let x = 0;
    while (x < cols) {
      const ch = row[x];
      const color = PALETTE[ch];
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

export default function PixelSheep({ width = 96, className = '' }) {
  const rects = useMemo(buildRects, []);
  const cols = ROWS[0].length;
  const rows = ROWS.length;
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
