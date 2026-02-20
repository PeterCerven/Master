import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, Observable, of, shareReplay, tap, throwError } from 'rxjs';
import { environment } from '@env/environment.production';
import { LoginRequest, TokenResponse, User } from '@models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly apiUrl = `${environment.apiUrl}/auth`;

  private readonly _accessToken = signal<string | null>(null);
  private readonly _currentUser = signal<User | null>(null);

  readonly isAuthenticated = computed(() => !!this._accessToken());
  readonly currentUser = this._currentUser.asReadonly();
  readonly isAdmin = computed(() => this._currentUser()?.role === 'ADMIN');

  private refreshInProgress$: Observable<TokenResponse> | null = null;

  get accessToken(): string | null {
    return this._accessToken();
  }

  login(request: LoginRequest): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${this.apiUrl}/login`, request, { withCredentials: true })
      .pipe(
        tap((response) => {
          this._accessToken.set(response.accessToken);
          this.loadCurrentUser();
        }),
      );
  }

  refreshToken(): Observable<TokenResponse> {
    if (this.refreshInProgress$) {
      return this.refreshInProgress$;
    }

    this.refreshInProgress$ = this.http
      .post<TokenResponse>(`${this.apiUrl}/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((response) => {
          this._accessToken.set(response.accessToken);
          this.refreshInProgress$ = null;
        }),
        catchError((error) => {
          this.refreshInProgress$ = null;
          this.clearAuthState();
          return throwError(() => error);
        }),
        shareReplay(1),
      );

    return this.refreshInProgress$;
  }

  logout(): void {
    this.http.post(`${this.apiUrl}/logout`, {}, { withCredentials: true }).subscribe({
      complete: () => {
        this.clearAuthState();
        this.router.navigate(['/login']);
      },
      error: () => {
        this.clearAuthState();
        this.router.navigate(['/login']);
      },
    });
  }

  tryAutoLogin(): Observable<TokenResponse | null> {
    return this.http
      .post<TokenResponse>(`${this.apiUrl}/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((response) => {
          this._accessToken.set(response.accessToken);
          this.loadCurrentUser();
        }),
        catchError(() => of(null)),
      );
  }

  private loadCurrentUser(): void {
    this.http.get<User>(`${this.apiUrl}/me`).subscribe({
      next: (user) => this._currentUser.set(user),
      error: () => {},
    });
  }

  private clearAuthState(): void {
    this._accessToken.set(null);
    this._currentUser.set(null);
  }
}
