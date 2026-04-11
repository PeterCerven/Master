import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'app-city-import-dialog',
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, TranslocoDirective],
  templateUrl: './city-import-dialog.html',
})
export class CityImportDialog {
  private readonly dialogRef = inject(MatDialogRef<CityImportDialog>);

  cityName = '';

  onConfirm(): void {
    const trimmed = this.cityName.trim();
    if (trimmed) this.dialogRef.close(trimmed);
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
