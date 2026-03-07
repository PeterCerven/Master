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

  public generateGraphFromFile(event: Event): Observable<GraphResponseDto> {
    const files = (event.target as HTMLInputElement).files;
    if (!files || files.length === 0 ) {
      return throwError(() => new Error('No file selected'));
    }

    const file = files[0];
    const fileExtension = file.name.split('.').pop()?.toLowerCase();

    switch (fileExtension) {
      case 'gpx':
      case 'json':
      case 'geojson':
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

  private handleError(error: any): Observable<never> {
    console.error('Graph error:', error);
    return throwError(() => new Error(error.message || 'Server Error'));
  }
}
