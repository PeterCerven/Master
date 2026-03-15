import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoDirective } from '@jsverse/transloco';
import { GraphService } from '@services/graph.service';
import { GraphSummaryDto } from '@models/my-graph.model';

@Component({
  selector: 'app-load-graph-dialog',
  imports: [DatePipe, FormsModule, MatDialogModule, MatTableModule, MatButtonModule, MatIconModule, MatInputModule, MatProgressSpinnerModule, TranslocoDirective],
  templateUrl: './load-graph-dialog.html',
  styleUrl: './load-graph-dialog.scss',
})
export class LoadGraphDialog implements OnInit {
  private readonly graphService = inject(GraphService);
  private readonly dialogRef = inject(MatDialogRef<LoadGraphDialog>);
  private readonly destroyRef = inject(DestroyRef);

  graphs = signal<GraphSummaryDto[]>([]);
  loading = signal(true);
  deleting = signal<number | null>(null);
  editingId = signal<number | null>(null);
  editingName = signal('');
  selectedId: number | null = null;

  readonly displayedColumns = ['name', 'date', 'nodes', 'edges', 'stations', 'actions'];

  ngOnInit(): void {
    this.graphService.listGraphs()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (graphs) => {
          this.graphs.set(graphs);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  selectRow(id: number): void {
    this.selectedId = id;
  }

  onLoad(): void {
    if (this.selectedId == null) return;
    this.dialogRef.close(this.selectedId);
  }

  deleteGraph(id: number, event: MouseEvent): void {
    event.stopPropagation();
    this.deleting.set(id);
    this.graphService.deleteGraph(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.graphs.update(graphs => graphs.filter(g => g.id !== id));
          if (this.selectedId === id) this.selectedId = null;
          this.deleting.set(null);
        },
        error: () => this.deleting.set(null),
      });
  }

  startRename(id: number, currentName: string, event: MouseEvent): void {
    event.stopPropagation();
    this.editingId.set(id);
    this.editingName.set(currentName);
  }

  confirmRename(id: number): void {
    const name = this.editingName().trim();
    if (!name) return;
    this.graphService.renameGraph(id, name)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.graphs.update(graphs => graphs.map(g => g.id === id ? { ...g, name: updated.name } : g));
          this.editingId.set(null);
        },
        error: () => this.editingId.set(null),
      });
  }

  cancelRename(event: MouseEvent): void {
    event.stopPropagation();
    this.editingId.set(null);
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
