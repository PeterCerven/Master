import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {environment} from '@env/environment.production';
import {catchError, Observable, throwError} from 'rxjs';
import {GraphResponseDto, GraphSummaryDto, SavedGraphResponseDto, StationNodeDto} from '@models/my-graph.model';

@Injectable({
  providedIn: 'root'
})
export class GraphService {
  private readonly apiUrl = `${environment.apiUrl}/graph`;
  private readonly http = inject(HttpClient);

  public generateGraphFromFile(file: File): Observable<GraphResponseDto> {
    const fileExtension = file.name.split('.').pop()?.toLowerCase();
    switch (fileExtension) {
      case 'gpx':
      case 'json':
      case 'geojson':
      case 'csv':
        return this.parseGpxFile(file);
      default:
        return throwError(() => new Error('Unsupported file format'));
    }
  }

  public listGraphs(): Observable<GraphSummaryDto[]> {
    return this.http.get<GraphSummaryDto[]>(`${this.apiUrl}/list`).pipe(catchError(this.handleError));
  }

  public loadGraphFromDatabase(id: number): Observable<SavedGraphResponseDto> {
    return this.http.get<SavedGraphResponseDto>(`${this.apiUrl}/load`, { params: { graphId: id.toString() } }).pipe(
      catchError(this.handleError)
    );
  }

  private parseGpxFile(file: File): Observable<GraphResponseDto> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<GraphResponseDto>(`${this.apiUrl}/file-import`, formData).pipe(
      catchError(this.handleError)
    );
  }

  public saveGraph(graph: GraphResponseDto, name: string, stations: StationNodeDto[] | null): Observable<GraphSummaryDto> {
    const request = { name, graph, stations: stations ?? [] };
    return this.http.post<GraphSummaryDto>(`${this.apiUrl}/save`, request).pipe(catchError(this.handleError));
  }

  public listSamples(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrl}/samples`).pipe(catchError(this.handleError));
  }

  public importSampleFile(filename: string): Observable<GraphResponseDto> {
    return this.http.post<GraphResponseDto>(`${this.apiUrl}/sample-import/${encodeURIComponent(filename)}`, null).pipe(
      catchError(this.handleError)
    );
  }

  public importCityGraph(city: string): Observable<GraphResponseDto> {
    return this.http.get<GraphResponseDto>(`${this.apiUrl}/city-import`, { params: { city } }).pipe(
      catchError(this.handleError)
    );
  }

  public deleteGraph(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(catchError(this.handleError));
  }

  public renameGraph(id: number, name: string): Observable<GraphSummaryDto> {
    return this.http.patch<GraphSummaryDto>(`${this.apiUrl}/${id}/rename`, { name }).pipe(
      catchError(this.handleError)
    );
  }

  private handleError(error: any): Observable<never> {
    console.error('Graph error:', error);
    return throwError(() => new Error(error.message || 'Server Error'));
  }
}
