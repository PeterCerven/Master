import {Component, inject, OnInit, signal} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {MatToolbar} from '@angular/material/toolbar';
import {MatIconButton} from '@angular/material/button';
import {MatIcon} from '@angular/material/icon';
import {MatDialog, MatDialogModule} from '@angular/material/dialog';
import {TranslocoService} from '@jsverse/transloco';
import {ThemeService} from '@services/theme.service';
import {AuthService} from '@services/auth.service';
import {RegisterUserDialogComponent} from '@components/register-user-dialog/register-user-dialog';
import {AdminUsersDialog} from '@components/admin-users-dialog/admin-users-dialog';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    MatToolbar,
    MatIconButton,
    MatIcon,
    MatDialogModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  private translocoService = inject(TranslocoService);
  private themeService = inject(ThemeService);
  private dialog = inject(MatDialog);
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

  openRegisterDialog(): void {
    this.dialog.open(RegisterUserDialogComponent, {width: 'min(420px, calc(100vw - 32px))'});
  }

  openAdminUsersDialog(): void {
    this.dialog.open(AdminUsersDialog, {minWidth: 'min(800px, calc(100vw - 16px))'});
  }
}
