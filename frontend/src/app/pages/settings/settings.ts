import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslocoDirective } from '@jsverse/transloco';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PipelineConfigService } from '@services/pipeline-config.service';
import { PipelineConfig } from '@models/pipeline-config.model';

@Component({
  selector: 'app-settings',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    TranslocoDirective,
  ],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings implements OnInit {
  private readonly configService = inject(PipelineConfigService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  loading = signal(false);
  saving = signal(false);
  configForm!: FormGroup;

  ngOnInit(): void {
    this.initForm();
    this.loadConfig();
  }

  private initForm(): void {
    this.configForm = this.fb.group({
      id: [null],
      name: ['', Validators.required],
      // Preprocessing
      minLat: [47.5, [Validators.required, Validators.min(-90), Validators.max(90)]],
      maxLat: [49.7, [Validators.required, Validators.min(-90), Validators.max(90)]],
      minLon: [16.8, [Validators.required, Validators.min(-180), Validators.max(180)]],
      maxLon: [22.6, [Validators.required, Validators.min(-180), Validators.max(180)]],
      nearDuplicateThresholdM: [5, [Validators.required, Validators.min(0.1)]],
      outlierMinNeighbors: [1, [Validators.required, Validators.min(1)]],
      outlierRadiusM: [200, [Validators.required, Validators.min(1)]],
      maxSpeedKmh: [200, [Validators.required, Validators.min(1)]],
      tripGapMinutes: [30, [Validators.required, Validators.min(1)]],
      // H3
      h3DedupResolution: [12, [Validators.required, Validators.min(0), Validators.max(15)]],
      h3ClusterResolution: [9, [Validators.required, Validators.min(0), Validators.max(15)]],
      // DBSCAN + Graph
      dbscanEpsMeters: [50, [Validators.required, Validators.min(1)]],
      dbscanMinPts: [2, [Validators.required, Validators.min(1)]],
      maxEdgeLengthM: [300, [Validators.required, Validators.min(1)]],
      mergeThresholdM: [10, [Validators.required, Validators.min(0.1)]],
      knnK: [5, [Validators.required, Validators.min(1)]],
      // Matching
      maxSnapDistanceM: [100, [Validators.required, Validators.min(1)]],
    });
  }

  private loadConfig(): void {
    this.loading.set(true);
    this.configService
      .getActiveConfig()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (config: PipelineConfig) => {
          this.configForm.patchValue(config);
          this.loading.set(false);
        },
        error: (error) => {
          console.error('Error loading config:', error);
          this.loading.set(false);
          this.snackBar.open('Failed to load configuration', 'OK', { duration: 5000 });
        },
      });
  }

  saveConfig(): void {
    if (this.configForm.invalid) {
      this.configForm.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    const config: PipelineConfig = this.configForm.value;

    this.configService
      .updateConfig(config)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated: PipelineConfig) => {
          this.configForm.patchValue(updated);
          this.saving.set(false);
          this.snackBar.open('Configuration saved successfully', 'OK', { duration: 3000 });
        },
        error: (error) => {
          console.error('Error saving config:', error);
          this.saving.set(false);
          this.snackBar.open('Failed to save configuration', 'OK', { duration: 5000 });
        },
      });
  }

  resetToDefaults(): void {
    this.saving.set(true);
    this.configService
      .resetToDefaults()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (config: PipelineConfig) => {
          this.configForm.patchValue(config);
          this.saving.set(false);
          this.snackBar.open('Configuration reset to defaults', 'OK', { duration: 3000 });
        },
        error: (error) => {
          console.error('Error resetting config:', error);
          this.saving.set(false);
          this.snackBar.open('Failed to reset configuration', 'OK', { duration: 5000 });
        },
      });
  }
}
