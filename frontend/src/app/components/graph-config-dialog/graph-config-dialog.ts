import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PipelineConfigService } from '@services/pipeline-config.service';
import { PipelineConfig } from '@models/pipeline-config.model';
import {TranslocoDirective} from '@jsverse/transloco';
import {MatTooltip} from '@angular/material/tooltip';

@Component({
  selector: 'app-graph-config-dialog',
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, TranslocoDirective, MatTooltip],
  templateUrl: './graph-config-dialog.html',
  styleUrl: './graph-config-dialog.scss',
})
export class GraphConfigDialog implements OnInit {
  private readonly configService = inject(PipelineConfigService);
  private readonly dialogRef = inject(MatDialogRef<GraphConfigDialog>);
  private readonly destroyRef = inject(DestroyRef);

  loadedConfig: PipelineConfig | null = null;
  maxSpeedKmh = 200;
  h3DedupResolution = 12;
  loading = signal(true);
  saving = signal(false);
  selectedFile: File | null = null;

  ngOnInit(): void {
    this.configService
      .getActiveConfig()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (config) => {
          this.loadedConfig = config;
          this.maxSpeedKmh = config.maxSpeedKmh;
          this.h3DedupResolution = config.h3DedupResolution;
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  onFileChange(event: Event): void {
    this.selectedFile = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  onOk(): void {
    if (!this.loadedConfig) return;
    this.saving.set(true);
    this.configService
      .updateConfig({ ...this.loadedConfig, maxSpeedKmh: this.maxSpeedKmh, h3DedupResolution: this.h3DedupResolution })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.dialogRef.close(this.selectedFile);
        },
        error: () => this.saving.set(false),
      });
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
