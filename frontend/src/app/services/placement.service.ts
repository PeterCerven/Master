import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '@env/environment.production';
import { catchError, Observable, throwError } from 'rxjs';
import { GraphResponseDto } from '@models/my-graph.model';
import { PlacementRequestDto, PlacementResponseDto } from '@models/my-graph.model';

@Injectable({
  providedIn: 'root',
})
export class PlacementService {
  private readonly apiUrl = `${environment.apiUrl}/placement`;
  private readonly http = inject(HttpClient);

  computePlacement(graph: GraphResponseDto, k: number): Observable<PlacementResponseDto> {
    const request: PlacementRequestDto = {
      graph: {
        nodes: graph.nodes.map(n => ({ id: n.id, lat: n.lat, lon: n.lon })),
        edges: graph.edges.map(e => ({ sourceId: e.sourceId, targetId: e.targetId, distanceMeters: e.distanceMeters })),
      },
      algorithm: 'RANDOM_STRATEGY',
      k,
      maxRadiusMeters: 50000,
    };

    return this.http.post<PlacementResponseDto>(`${this.apiUrl}/compute`, request).pipe(
      catchError(this.handleError)
    );
  }

  private handleError(error: any): Observable<never> {
    console.error('Placement error:', error);
    return throwError(() => new Error(error.message || 'Server Error'));
  }
}
