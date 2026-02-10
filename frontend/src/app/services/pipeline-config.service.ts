import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '@env/environment.production';
import { catchError, Observable, throwError } from 'rxjs';
import { PipelineConfig } from '@models/pipeline-config.model';

@Injectable({
  providedIn: 'root',
})
export class PipelineConfigService {
  private readonly apiUrl = `${environment.apiUrl}/pipeline-config`;
  private readonly http = inject(HttpClient);

  getActiveConfig(): Observable<PipelineConfig> {
    return this.http.get<PipelineConfig>(this.apiUrl).pipe(catchError(this.handleError));
  }

  updateConfig(config: PipelineConfig): Observable<PipelineConfig> {
    return this.http.put<PipelineConfig>(this.apiUrl, config).pipe(catchError(this.handleError));
  }

  resetToDefaults(): Observable<PipelineConfig> {
    return this.http
      .post<PipelineConfig>(`${this.apiUrl}/reset`, {})
      .pipe(catchError(this.handleError));
  }

  private handleError(error: any): Observable<never> {
    console.error('Pipeline config error:', error);
    return throwError(() => new Error(error.message || 'Server Error'));
  }
}
