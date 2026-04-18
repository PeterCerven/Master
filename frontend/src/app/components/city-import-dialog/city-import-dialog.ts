import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltip } from '@angular/material/tooltip';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoDirective } from '@jsverse/transloco';
import { PipelineConfigService } from '@services/pipeline-config.service';
import { PipelineConfig } from '@models/pipeline-config.model';

@Component({
  selector: 'app-city-import-dialog',
  imports: [
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
    MatTooltip,
    TranslocoDirective,
  ],
  templateUrl: './city-import-dialog.html',
  styleUrl: './city-import-dialog.scss'
})
export class CityImportDialog implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<CityImportDialog>);
  private readonly configService = inject(PipelineConfigService);
  private readonly destroyRef = inject(DestroyRef);

  cityName = '';
  loadedConfig: PipelineConfig | null = null;
  cityCountry: 'sk' | 'cz' | 'at' | null = null;
  retainLargestComponentPercent = 0.1;
  cityBoundaryBufferMeters = 100;
  loading = signal(true);
  saving = signal(false);

  readonly countryOptions: { value: 'sk' | 'cz' | 'at' | null; labelKey: string }[] = [
    { value: null, labelKey: 'city.countryAny' },
    { value: 'sk', labelKey: 'city.countrySk' },
    { value: 'cz', labelKey: 'city.countryCz' },
    { value: 'at', labelKey: 'city.countryAt' },
  ];

  ngOnInit(): void {
    this.configService
      .getActiveConfig()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (config) => {
          this.loadedConfig = config;
          this.cityCountry = config.cityCountry;
          this.retainLargestComponentPercent = config.retainLargestComponentPercent;
          this.cityBoundaryBufferMeters = config.cityBoundaryBufferMeters;
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  onConfirm(): void {
    if (!this.loadedConfig) return;
    const trimmed = this.cityName.trim();
    if (!trimmed) return;
    this.saving.set(true);
    this.configService
      .updateConfig({
        ...this.loadedConfig,
        cityCountry: this.cityCountry,
        retainLargestComponentPercent: this.retainLargestComponentPercent,
        cityBoundaryBufferMeters: this.cityBoundaryBufferMeters,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.dialogRef.close(trimmed);
        },
        error: () => this.saving.set(false),
      });
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
