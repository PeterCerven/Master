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

  public getGraph(): Observable<MyGraph> {
    return this.http.get<MyGraph>(`${this.apiUrl}/`).pipe(
      catchError(this.handleError)
    );
  }

  private handleError(error: any): Observable<never> {
    console.error('Error fetching graph:', error);
    return throwError(() => new Error(error.message || 'Server Error'));
  }
}