import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { signal } from '@angular/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { authGuard } from './auth.guard';
import { AuthService } from '@services/auth.service';

class AuthServiceStub {
  private readonly _authed = signal(false);
  readonly isAuthenticated = this._authed.asReadonly();
  setAuthenticated(v: boolean) {
    this._authed.set(v);
  }
}

function runGuard() {
  return TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
}

describe('authGuard', () => {
  let auth: AuthServiceStub;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useClass: AuthServiceStub }],
    });
    auth = TestBed.inject(AuthService) as unknown as AuthServiceStub;
    router = TestBed.inject(Router);
  });

  it('returns true when the user is authenticated', () => {
    auth.setAuthenticated(true);
    expect(runGuard()).toBe(true);
  });

  it('returns a UrlTree pointing to /login when the user is not authenticated', () => {
    auth.setAuthenticated(false);
    const result = runGuard();
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });
});
