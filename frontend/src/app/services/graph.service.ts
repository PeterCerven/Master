import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {environment} from '@env/environment.production';
import {catchError, Observable, throwError} from 'rxjs';
import {GraphPoint, MyGraph} from '@models/my-graph.model';

@Injectable({
  providedIn: 'root'
})
export class GraphService {
  private readonly apiUrl = `${environment.apiUrl}/graph`;
  private readonly http = inject(HttpClient);

  public generateGraphFromFile(event: Event): Observable<MyGraph> {
    const files = (event.target as HTMLInputElement).files;
    if (!files || files.length === 0 ) {
      return throwError(() => new Error('No file selected'));
    }

    const file = files[0];
    const fileExtension = file.name.split('.').pop()?.toLowerCase();

    switch (fileExtension) {
      case 'gpx':
        return this.parseGpxFile(file);
      default:
        return throwError(() => new Error('Unsupported file format'));
    }
  }

  public loadGraphFromDatabase(id: Number): Observable<MyGraph> {
    return this.http.get<MyGraph>(`${this.apiUrl}/graph/${id}`).pipe(catchError(this.handleError));
  }

  private parseGpxFile(file: File): Observable<MyGraph> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<MyGraph>(`${this.apiUrl}/file-import`, formData).pipe(
      catchError(this.handleError)
    );
  }

  public saveGraph(graph: MyGraph, name: string): Observable<any> {
    const request = { name, graph };
    return this.http.post(`${this.apiUrl}/save`, request).pipe(catchError(this.handleError));
  }

  public updateGraphWithPoints(points: Array<GraphPoint>): Observable<MyGraph> {
    const request = { points: points };
    return this.http.post<MyGraph>(`${this.apiUrl}/add-points`, request).pipe(
      catchError(this.handleError)
    );
  }

  private handleError(error: any): Observable<never> {
    console.error('Graph error:', error);
    return throwError(() => new Error(error.message || 'Server Error'));
  }
}
