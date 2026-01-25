import {Component, inject, signal} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {MatToolbar} from '@angular/material/toolbar';
import {MatIconButton} from '@angular/material/button';
import {MatIcon} from '@angular/material/icon';
import { TranslocoService } from '@jsverse/transloco';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    MatToolbar,
    MatIconButton,
    MatIcon,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  private translocoService = inject(TranslocoService);
  isDarkMode = signal(false);
  currentLanguage = signal('SK')

  toggleDarkMode() {
    this.isDarkMode.update(v => !v);

    if (this.isDarkMode()) {
      document.body.classList.add('dark-mode');
    } else {
      document.body.classList.remove('dark-mode');
    }
  }

  switchLanguage() {
    this.translocoService.setActiveLang(this.currentLanguage() === 'SK' ? 'en' : 'sk');
    this.currentLanguage.set(this.currentLanguage() === 'SK' ? 'EN' : 'SK');
  }
}
