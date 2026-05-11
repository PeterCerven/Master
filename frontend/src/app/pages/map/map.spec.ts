import { describe, it } from 'vitest';

// The Map page integrates Google Maps and deck.gl which require a real browser
// environment (WebGL, google.maps global). Skip in the jsdom-based unit test
// runner; cover via end-to-end tests instead.
describe.skip('Map (requires browser-mode runner)', () => {
  it('placeholder', () => {});
});
