import {Component, inject, signal} from '@angular/core';
import {RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {MatToolbar} from '@angular/material/toolbar';
import {MatIconButton} from '@angular/material/button';
import {MatIcon} from '@angular/material/icon';
import { TranslocoService } from '@jsverse/transloco';
import { ThemeService } from '@services/theme.service';

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
export class App {
  private translocoService = inject(TranslocoService);
  private themeService = inject(ThemeService);
  isDarkMode = this.themeService.isDarkMode;
  currentLanguage = signal('SK')

  toggleDarkMode() {
    this.themeService.toggleDarkMode();
  }

  switchLanguage() {
    this.translocoService.setActiveLang(this.currentLanguage() === 'SK' ? 'en' : 'sk');
    this.currentLanguage.set(this.currentLanguage() === 'SK' ? 'EN' : 'SK');
  }
}
