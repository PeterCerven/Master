import {inject, Injectable} from '@angular/core';
import {TrajectoryDataModel} from '../models/trajectory-data.model';
import {HttpClient} from '@angular/common/http';
import {environment} from '../../environments/environment.production'
import {catchError, Observable, tap, throwError} from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class DataService {

  private readonly apiUrl = `${environment.apiUrl}/data`;
  private readonly http = inject(HttpClient);


  public saveData(data: TrajectoryDataModel[]): Observable<TrajectoryDataModel[]> {
    return this.http.post<TrajectoryDataModel[]>(this.apiUrl, data).pipe(
      tap((response) => console.log('Successfully added trajectory data', response)),
      catchError(this.handleError)
    );
  }

  public showData(): Observable<TrajectoryDataModel[]> {
    return this.http.get<TrajectoryDataModel[]>(`${this.apiUrl}/show`).pipe(
      tap((response) => console.log('Successfully fetched trajectory data', response)),
      catchError(this.handleError)
    );
  }

  public parseFile(file: File): Observable<TrajectoryDataModel[]> {
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

    return this.http.post<TrajectoryDataModel[]>(`${this.apiUrl}/parse-gpx`, formData).pipe(
      tap((response) => console.log('GPX file parsed successfully', response)),
      catchError(this.handleError)
    );
  }

  private handleError(error: any): Observable<never> {
    console.error('An error occurred', error);
    return throwError(() => new Error(error.message || 'Server Error'));
  }
}
