import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PipelineConfigService } from '@services/pipeline-config.service';
import { PipelineConfig } from '@models/pipeline-config.model';
import { PlacementRequestDto } from '@models/my-graph.model';
import {TranslocoDirective} from '@jsverse/transloco';
import {MatTooltip} from '@angular/material/tooltip';

export interface PlacementConfigResult {
  strategy: PlacementRequestDto['algorithm'];
  k: number;
  maxRadiusMeters: number;
  iterations: number;
}

@Component({
  selector: 'app-placement-config-dialog',
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatSelectModule, TranslocoDirective, MatTooltip],
  templateUrl: './placement-config-dialog.html',
  styleUrl: './placement-config-dialog.scss',
})
export class PlacementConfigDialog implements OnInit {
  private readonly configService = inject(PipelineConfigService);
  private readonly dialogRef = inject(MatDialogRef<PlacementConfigDialog>);
  private readonly destroyRef = inject(DestroyRef);

  loadedConfig: PipelineConfig | null = null;
  strategy: PlacementRequestDto['algorithm'] = 'RANDOM_STRATEGY';
  k = 10;
  maxRadiusMeters = 5000;
  iterations = 10;
  loading = signal(true);
  saving = signal(false);

  readonly strategies: { value: PlacementRequestDto['algorithm']; label: string }[] = [
    { value: 'RANDOM_STRATEGY', label: 'Random' },
    { value: 'GREEDY_STRATEGY', label: 'Greedy' },
    { value: 'CUSTOM_STRATEGY', label: 'GRASP' },
  ];

  ngOnInit(): void {
    this.configService
      .getActiveConfig()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (config) => {
          this.loadedConfig = config;
          this.k = config.kDominatingSet;
          this.maxRadiusMeters = config.maxRadiusMeters;
          this.iterations = config.iterations;
          this.strategy = config.lastAlgorithm;
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  onOk(): void {
    if (!this.loadedConfig) return;
    this.saving.set(true);
    this.configService
      .updateConfig({ ...this.loadedConfig, kDominatingSet: this.k, maxRadiusMeters: this.maxRadiusMeters, iterations: this.iterations, lastAlgorithm: this.strategy })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.dialogRef.close({ strategy: this.strategy, k: this.k, maxRadiusMeters: this.maxRadiusMeters, iterations: this.iterations } as PlacementConfigResult);
        },
        error: () => this.saving.set(false),
      });
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
