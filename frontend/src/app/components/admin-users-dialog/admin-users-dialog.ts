import {Component, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {MatDialogModule} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTableModule} from '@angular/material/table';
import {TranslocoDirective} from '@jsverse/transloco';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AuthService} from '@services/auth.service';
import {UserSummary} from '@models/auth.model';

@Component({
  selector: 'app-admin-users-dialog',
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    TranslocoDirective,
  ],
  templateUrl: './admin-users-dialog.html',
  styleUrl: './admin-users-dialog.scss',
})
export class AdminUsersDialog implements OnInit {
  protected readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly users = signal<UserSummary[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly pendingDeleteId = signal<number | null>(null);
  readonly currentUserId = signal<number | null>(null);

  readonly displayedColumns = ['name', 'email', 'role', 'status', 'actions'];

  ngOnInit(): void {
    this.currentUserId.set(this.authService.currentUser()?.id ?? null);
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.authService
      .getUsers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (users) => {
          this.users.set(users);
          this.loading.set(false);
        },
        error: () => {
          this.errorMessage.set('error');
          this.loading.set(false);
        },
      });
  }

  toggleEnabled(userId: number): void {
    this.authService
      .setUserEnabled(userId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.loadUsers(),
        error: () => this.errorMessage.set('error'),
      });
  }

  confirmDelete(userId: number): void {
    this.pendingDeleteId.set(userId);
  }

  cancelDelete(): void {
    this.pendingDeleteId.set(null);
  }

  executeDelete(userId: number): void {
    this.authService
      .deleteUser(userId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.pendingDeleteId.set(null);
          this.loadUsers();
        },
        error: () => this.errorMessage.set('error'),
      });
  }
}
