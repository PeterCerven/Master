import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '@services/auth.service';
import { environment } from '@env/environment.production';

const API = environment.apiUrl;

function seedToken(auth: AuthService, token: string): void {
  (auth as unknown as { _accessToken: { set: (v: string) => void } })._accessToken.set(
    token,
  );
}

describe('authInterceptor (integration with AuthService)', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let auth: AuthService;
  let routerNavigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    routerNavigate = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate: routerNavigate } },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => httpMock.verify());

  it('attaches Authorization header when a token is present', () => {
    seedToken(auth, 'tok-abc');

    http.get(`${API}/graph/list`).subscribe();

    const req = httpMock.expectOne(`${API}/graph/list`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer tok-abc');
    req.flush([]);
  });

  it('skips Authorization header for /auth/login and /auth/refresh even when a token is present', () => {
    seedToken(auth, 'tok-abc');

    http.post(`${API}/auth/login`, { email: 'a', password: 'b' }).subscribe();
    http.post(`${API}/auth/refresh`, {}).subscribe();

    const loginReq = httpMock.expectOne(`${API}/auth/login`);
    const refreshReq = httpMock.expectOne(`${API}/auth/refresh`);
    expect(loginReq.request.headers.has('Authorization')).toBe(false);
    expect(refreshReq.request.headers.has('Authorization')).toBe(false);
    loginReq.flush({ accessToken: 'x', tokenType: 'Bearer' });
    refreshReq.flush({ accessToken: 'x', tokenType: 'Bearer' });
  });

  it('on 401 it refreshes the token and retries the original request with the new token', () => {
    seedToken(auth, 'old');

    let result: unknown;
    http.get<{ ok: boolean }>(`${API}/graph/list`).subscribe((r) => (result = r));

    // 1. Original request goes out with old token, server says 401.
    const original = httpMock.expectOne(`${API}/graph/list`);
    expect(original.request.headers.get('Authorization')).toBe('Bearer old');
    original.flush(null, { status: 401, statusText: 'Unauthorized' });

    // 2. Interceptor triggers a refresh.
    const refresh = httpMock.expectOne(`${API}/auth/refresh`);
    refresh.flush({ accessToken: 'new', tokenType: 'Bearer' });

    // 3. Interceptor retries the original with the new token.
    const retried = httpMock.expectOne(`${API}/graph/list`);
    expect(retried.request.headers.get('Authorization')).toBe('Bearer new');
    retried.flush({ ok: true });

    expect(result).toEqual({ ok: true });
    expect(auth.accessToken).toBe('new');
  });

  it('on 401 with refresh failure it logs the user out and propagates the error', () => {
    seedToken(auth, 'old');

    let errored: unknown;
    http.get(`${API}/graph/list`).subscribe({
      next: () => {},
      error: (e) => (errored = e),
    });

    httpMock
      .expectOne(`${API}/graph/list`)
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    httpMock
      .expectOne(`${API}/auth/refresh`)
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    // AuthService.logout() fires a POST and, on completion, clears state + navigates.
    httpMock.expectOne(`${API}/auth/logout`).flush({});

    expect(errored).toBeTruthy();
    expect(auth.isAuthenticated()).toBe(false);
    expect(routerNavigate).toHaveBeenCalledWith(['/login']);
  });
});
