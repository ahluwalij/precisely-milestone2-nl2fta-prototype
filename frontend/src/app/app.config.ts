import { ApplicationConfig, provideZoneChangeDetection, APP_INITIALIZER } from '@angular/core';
import { ConfigService } from './core/services/config.service';
import { LoggerService } from './core/services/logger.service';
import { provideClientHydration } from '@angular/platform-browser';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import { MessageService } from 'primeng/api';
import Lara from '@primeng/themes/lara';
import { definePreset } from '@primeng/themes';
import { routes } from './app.routes';
import { userHeaderInterceptor } from './core/interceptors/user-header.interceptor';

const NL2FTATheme = definePreset(Lara, {
  semantic: {
    primary: {
      50: '#f3e5f5',
      100: '#e1bee7',
      200: '#ce93d8',
      300: '#ba68c8',
      400: '#ab47bc',
      500: '#9c27b0',
      600: '#6d2c91',
      700: '#5a2478',
      800: '#4a148c',
      900: '#38006b',
      950: '#2e004d',
    },
    colorScheme: {
      light: {
        primary: {
          color: '#6d2c91',
          inverseColor: '#ffffff',
          hoverColor: '#5a2478',
          activeColor: '#4a148c',
        },
        highlight: {
          background: '#6d2c91',
          focusBackground: '#5a2478',
          color: '#ffffff',
          focusColor: '#ffffff',
        },
      },
    },
  },
});

export function initializeApp(
  configService: ConfigService,
  logger: LoggerService
): () => Promise<void> {
  return () =>
    configService
      .loadConfig()
      .then(() => {})
      .catch(err => {
        logger.warn('Failed to initialize configuration', err, 'AppInitializer');
      });
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideClientHydration(),
    provideHttpClient(withFetch(), withInterceptors([userHeaderInterceptor])),
    provideAnimationsAsync(),
    providePrimeNG({
      theme: {
        preset: NL2FTATheme,
        options: {
          darkModeSelector: false,
          cssLayer: {
            name: 'primeng',
            order: 'tailwind-base, primeng, tailwind-utilities',
          },
        },
      },
    }),
    MessageService,
    {
      provide: APP_INITIALIZER,
      useFactory: initializeApp,
      deps: [ConfigService, LoggerService],
      multi: true,
    },
  ],
};
