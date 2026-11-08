import PixelScene from './PixelScene';
import cabin from './pixel/cabinSprite.json';
import signpost from './pixel/signpostSprite.json';
import campfire from './pixel/campfireSprite.json';
import tavern from './pixel/tavernSprite.json';
import cracked from './pixel/crackedSprite.json';

export function CabinScene(props) {
  return <PixelScene sprite={cabin} {...props} />;
}

export function SignpostScene(props) {
  return <PixelScene sprite={signpost} {...props} />;
}

export function CampfireScene(props) {
  return <PixelScene sprite={campfire} {...props} />;
}

export function TavernScene(props) {
  return <PixelScene sprite={tavern} {...props} />;
}

export function CrackedScene(props) {
  return <PixelScene sprite={cracked} {...props} />;
}
