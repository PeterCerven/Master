import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
  isDevMode
} from '@angular/core';
import {provideRouter} from '@angular/router';

import {routes} from './app.routes';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {TranslocoHttpLoader} from '@services/transloco-loader.service';
import {provideTransloco} from '@jsverse/transloco';
import {authInterceptor} from './interceptors/auth.interceptor';
import {MAT_DIALOG_DEFAULT_OPTIONS} from '@angular/material/dialog';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({eventCoalescing: true}),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    {provide: MAT_DIALOG_DEFAULT_OPTIONS, useValue: {maxWidth: '100vw'}},
    provideTransloco({
      config: {
        availableLangs: ['en', 'sk'],
        defaultLang: 'sk',
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TranslocoHttpLoader
    }),
  ]
};
