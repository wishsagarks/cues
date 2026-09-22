import {Config} from '@remotion/cli/config';

// JPEG frames keep 4K renders fast; quality 95 is visually lossless for flat UI art.
Config.setVideoImageFormat('jpeg');
Config.setJpegQuality(95);
Config.setOverwriteOutput(true);
