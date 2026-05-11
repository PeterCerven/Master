import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideTransloco, Translation, TranslocoLoader } from '@jsverse/transloco';
import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Login } from './login';
import { AuthService } from '@services/auth.service';
import { environment } from '@env/environment.production';

const API = `${environment.apiUrl}/auth`;

@Injectable()
class NoopTranslocoLoader implements TranslocoLoader {
  getTranslation(_lang: string): Observable<Translation> {
    return of({});
  }
}

describe('Login page (integration)', () => {
  let fixture: ComponentFixture<Login>;
  let component: Login;
  let httpMock: HttpTestingController;
  let auth: AuthService;
  let routerNavigate: ReturnType<typeof vi.fn>;
  let snackBarOpen: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    routerNavigate = vi.fn();

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimationsAsync('noop'),
        provideTransloco({
          config: { availableLangs: ['en', 'sk'], defaultLang: 'sk' },
          loader: NoopTranslocoLoader,
        }),
        { provide: Router, useValue: { navigate: routerNavigate } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);

    // Spy on whatever MatSnackBar Angular actually injected into the component.
    const realSnackBar = (component as unknown as { snackBar: MatSnackBar }).snackBar;
    snackBarOpen = vi.spyOn(realSnackBar, 'open').mockReturnValue({} as never);

    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('navigates to /map after a successful login', () => {
    component.loginForm.setValue({ email: 'peter@master.sk', password: 'pw12345' });
    component.onSubmit();

    const loginReq = httpMock.expectOne(`${API}/login`);
    expect(loginReq.request.body).toEqual({ email: 'peter@master.sk', password: 'pw12345' });
    loginReq.flush({ accessToken: 'tok', tokenType: 'Bearer' });

    // /me is fetched immediately after login.
    httpMock.expectOne(`${API}/me`).flush({
      id: 1,
      name: 'Peter',
      email: 'peter@master.sk',
      role: 'USER',
      enabled: true,
    });

    expect(routerNavigate).toHaveBeenCalledWith(['/map']);
    expect(component.loading()).toBe(false);
    expect(auth.isAuthenticated()).toBe(true);
    expect(snackBarOpen).not.toHaveBeenCalled();
  });

  it('shows an error and does not navigate when the server returns 401', () => {
    component.loginForm.setValue({ email: 'peter@master.sk', password: 'wrong' });
    component.onSubmit();

    httpMock
      .expectOne(`${API}/login`)
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(routerNavigate).not.toHaveBeenCalled();
    expect(auth.isAuthenticated()).toBe(false);
    expect(component.loading()).toBe(false);
    expect(snackBarOpen).toHaveBeenCalledTimes(1);
    // First arg is the translated string; with the noop loader it's the raw key.
    expect(snackBarOpen.mock.calls[0][0]).toBe('login.errorInvalidCredentials');
  });

  it('does not call the backend when the form is invalid', () => {
    component.loginForm.setValue({ email: 'not-an-email', password: '' });
    component.onSubmit();
    httpMock.expectNone(`${API}/login`);
    expect(routerNavigate).not.toHaveBeenCalled();
  });
});
