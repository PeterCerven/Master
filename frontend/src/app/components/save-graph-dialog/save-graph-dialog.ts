import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoDirective } from '@jsverse/transloco';
import { GraphResponseDto, PlacementResponseDto } from '@models/my-graph.model';

export interface SaveGraphDialogData {
  graphData: GraphResponseDto;
  placementData: PlacementResponseDto | null;
}

@Component({
  selector: 'app-save-graph-dialog',
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, TranslocoDirective],
  templateUrl: './save-graph-dialog.html',
  styleUrl: './save-graph-dialog.scss',
})
export class SaveGraphDialog {
  private readonly dialogRef = inject(MatDialogRef<SaveGraphDialog>);
  readonly data = inject<SaveGraphDialogData>(MAT_DIALOG_DATA);

  graphName = '';

  onOk(): void {
    const trimmed = this.graphName.trim();
    if (!trimmed) return;
    this.dialogRef.close({ name: trimmed });
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
