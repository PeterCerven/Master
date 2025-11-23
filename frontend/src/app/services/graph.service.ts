import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {environment} from '@env/environment.production';
import {catchError, Observable, throwError} from 'rxjs';
import {MyGraph} from '@models/my-graph.model';

@Injectable({
  providedIn: 'root'
})
export class GraphService {
  private readonly apiUrl = `${environment.apiUrl}/graph`;
  private readonly http = inject(HttpClient);

  public getGraph(event: Event): Observable<MyGraph> {
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

  private parseGpxFile(file: File): Observable<MyGraph> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<MyGraph>(`${this.apiUrl}/import`, formData).pipe(
      catchError(this.handleError)
    );
  }

  private handleError(error: any): Observable<never> {
    console.error('Error fetching graph:', error);
    return throwError(() => new Error(error.message || 'Server Error'));
  }
}
