import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoDirective } from '@jsverse/transloco';
import { GraphService } from '@services/graph.service';
import { GraphSummaryDto } from '@models/my-graph.model';

@Component({
  selector: 'app-load-graph-dialog',
  imports: [DatePipe, MatDialogModule, MatTableModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, TranslocoDirective],
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

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
