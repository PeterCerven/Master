import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from './auth.service';
import { TokenResponse, User } from '@models/auth.model';
import { environment } from '@env/environment.production';

const API = `${environment.apiUrl}/auth`;

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let routerNavigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    routerNavigate = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate: routerNavigate } },
      ],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  describe('login()', () => {
    it('posts credentials, stores token, and loads current user', () => {
      const tokenResponse: TokenResponse = { accessToken: 'tok-1', tokenType: 'Bearer' };
      const me: User = {
        id: 1,
        name: 'Peter',
        email: 'peter@master.sk',
        role: 'USER',
        enabled: true,
      };

      let emitted: TokenResponse | undefined;
      service.login({ email: me.email, password: 'pw' }).subscribe((r) => (emitted = r));

      const loginReq = http.expectOne(`${API}/login`);
      expect(loginReq.request.method).toBe('POST');
      expect(loginReq.request.body).toEqual({ email: me.email, password: 'pw' });
      expect(loginReq.request.withCredentials).toBe(true);
      loginReq.flush(tokenResponse);

      const meReq = http.expectOne(`${API}/me`);
      expect(meReq.request.method).toBe('GET');
      meReq.flush(me);

      expect(emitted).toEqual(tokenResponse);
      expect(service.accessToken).toBe('tok-1');
      expect(service.isAuthenticated()).toBe(true);
      expect(service.currentUser()).toEqual(me);
      expect(service.isAdmin()).toBe(false);
    });

    it('sets isAdmin true when /me returns role ADMIN', () => {
      service.login({ email: 'a@b.c', password: 'pw' }).subscribe();
      http.expectOne(`${API}/login`).flush({ accessToken: 'x', tokenType: 'Bearer' });
      http.expectOne(`${API}/me`).flush({
        id: 2,
        name: 'Admin',
        email: 'a@b.c',
        role: 'ADMIN',
        enabled: true,
      } satisfies User);

      expect(service.isAdmin()).toBe(true);
    });
  });

  describe('refreshToken()', () => {
    it('deduplicates concurrent calls into a single HTTP request', () => {
      const responses: string[] = [];
      service.refreshToken().subscribe((r) => responses.push(r.accessToken));
      service.refreshToken().subscribe((r) => responses.push(r.accessToken));

      // Only ONE actual HTTP request should be in flight.
      const req = http.expectOne(`${API}/refresh`);
      req.flush({ accessToken: 'shared', tokenType: 'Bearer' });

      expect(responses).toEqual(['shared', 'shared']);
      expect(service.accessToken).toBe('shared');
    });

    it('clears auth state on refresh failure and allows a fresh retry afterwards', () => {
      // First refresh fails.
      let firstError: unknown;
      service.refreshToken().subscribe({
        next: () => {},
        error: (e) => (firstError = e),
      });
      http.expectOne(`${API}/refresh`).flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(firstError).toBeTruthy();
      expect(service.accessToken).toBeNull();
      expect(service.isAuthenticated()).toBe(false);

      // Second call must issue a NEW request (refreshInProgress$ reset).
      service.refreshToken().subscribe();
      const retry = http.expectOne(`${API}/refresh`);
      retry.flush({ accessToken: 'fresh', tokenType: 'Bearer' });
      expect(service.accessToken).toBe('fresh');
    });
  });
});
