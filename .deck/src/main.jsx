import React, { useEffect, useRef } from 'react';
import { createRoot } from 'react-dom/client';
import { mountDeck } from '../deck-engine.js';

function HyperDeckApp() {
  const host = useRef(null);
  useEffect(() => mountDeck(host.current), []);
  return <main id="app" ref={host} />;
}

createRoot(document.querySelector('#root')).render(<HyperDeckApp />);
