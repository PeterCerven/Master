import {Component, inject, OnInit, signal} from '@angular/core';
import {RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {MatToolbar} from '@angular/material/toolbar';
import {MatIconButton} from '@angular/material/button';
import {MatIcon} from '@angular/material/icon';
import { TranslocoService } from '@jsverse/transloco';
import { ThemeService } from '@services/theme.service';
import { AuthService } from '@services/auth.service';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    MatToolbar,
    MatIconButton,
    MatIcon,
    RouterLink,
    RouterLinkActive,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  private translocoService = inject(TranslocoService);
  private themeService = inject(ThemeService);
  authService = inject(AuthService);
  isDarkMode = this.themeService.isDarkMode;
  currentLanguage = signal('SK')

  ngOnInit(): void {
    this.authService.tryAutoLogin().subscribe();
  }

  toggleDarkMode() {
    this.themeService.toggleDarkMode();
  }

  switchLanguage() {
    this.translocoService.setActiveLang(this.currentLanguage() === 'SK' ? 'en' : 'sk');
    this.currentLanguage.set(this.currentLanguage() === 'SK' ? 'EN' : 'SK');
  }
}
