import {inject, Injectable} from '@angular/core';
import {TrajectoryDataModel} from '../models/trajectory-data.model';
import {HttpClient} from '@angular/common/http';
import {environment} from '../../environments/environment.production'
import {catchError, Observable, throwError} from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class DataService {

  private readonly apiUrl = `${environment.apiUrl}/data`;
  private readonly http = inject(HttpClient);


  public saveData(data: TrajectoryDataModel[]): Observable<TrajectoryDataModel[]> {
    return this.http.post<TrajectoryDataModel[]>(`${this.apiUrl}/save`, data).pipe(
      catchError(this.handleError)
    );
  }

  public getData(): Observable<TrajectoryDataModel[]> {
    return this.http.get<TrajectoryDataModel[]>(`${this.apiUrl}/`).pipe(
      catchError(this.handleError)
    );
  }

  public parseFile(event: Event): Observable<TrajectoryDataModel[]> {
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


  private parseGpxFile(file: File): Observable<TrajectoryDataModel[]> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<TrajectoryDataModel[]>(`${this.apiUrl}/parse`, formData).pipe(
      catchError(this.handleError)
    );
  }

  private handleError(error: any): Observable<never> {
    return throwError(() => new Error(error.message || 'Server Error'));
  }
}
