import {loadFont as loadAnton} from '@remotion/google-fonts/Anton';
import {loadFont as loadInterTight} from '@remotion/google-fonts/InterTight';

// Same palette and type as the deck, which was sampled from iqoo.reskilll.com.
export const C = {
  dark: '#0E0E0E',
  light: '#FAFAF7',
  amber: '#F0B31C',
  deep: '#8A6205',
  greyL: '#5A5A5A',
  greyD: '#A8A8A2',
  border: '#E8E5DB',
  cardD: '#1A1A18',
  borderD: '#2E2E2A',
  white: '#FFFFFF',
  muted: '#9A9A92',
};

export const {fontFamily: display} = loadAnton('normal', {subsets: ['latin']});
export const {fontFamily: body} = loadInterTight('normal', {weights: ['400', '600', '700'], subsets: ['latin']});
