import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  let service: ThemeService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ThemeService);
  });

  afterEach(() => {
    document.body.classList.remove('dark-mode');
  });

  it('starts with dark mode disabled', () => {
    expect(service.isDarkMode()).toBe(false);
    expect(document.body.classList.contains('dark-mode')).toBe(false);
  });

  it('toggles the dark mode signal on and off', () => {
    service.toggleDarkMode();
    expect(service.isDarkMode()).toBe(true);

    service.toggleDarkMode();
    expect(service.isDarkMode()).toBe(false);
  });

  it('adds the dark-mode class to body when enabled and removes it when disabled', () => {
    service.toggleDarkMode();
    expect(document.body.classList.contains('dark-mode')).toBe(true);

    service.toggleDarkMode();
    expect(document.body.classList.contains('dark-mode')).toBe(false);
  });
});
