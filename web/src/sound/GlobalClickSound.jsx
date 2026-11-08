import { useEffect } from 'react';
import { playClick } from './click';

export default function GlobalClickSound() {
  useEffect(() => {
    function onPointerDown(e) {
      const target = e.target;
      if (!target || !target.closest) return;
      const el = target.closest('button, a, input[type="range"]');
      if (!el || el.disabled) return;
      playClick();
    }

    document.addEventListener('pointerdown', onPointerDown, true);
    return () => document.removeEventListener('pointerdown', onPointerDown, true);
  }, []);

  return null;
}
