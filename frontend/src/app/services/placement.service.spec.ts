import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { PlacementService } from './placement.service';
import {
  GraphResponseDto,
  PlacementRequestDto,
  PlacementResponseDto,
} from '@models/my-graph.model';
import { environment } from '@env/environment.production';

const API = `${environment.apiUrl}/placement`;

function makeGraph(): GraphResponseDto {
  return {
    nodes: [
      {
        id: 'n1',
        lat: 48.1,
        lon: 17.1,
        roadName: 'Hlavná',
        roadClass: 'primary',
        componentId: 0,
      },
      {
        id: 'n2',
        lat: 48.2,
        lon: 17.2,
        roadName: null,
        roadClass: null,
        componentId: 0,
      },
    ],
    edges: [
      { sourceId: 'n1', targetId: 'n2', distanceMeters: 1234.5, roadName: 'Hlavná' },
    ],
    metrics: null,
  };
}

describe('PlacementService', () => {
  let service: PlacementService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PlacementService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('trims node/edge DTOs to only the fields the backend needs', () => {
    service
      .computePlacement(makeGraph(), 3, 500, 10, 'GRASP_STRATEGY', 0.3, 500)
      .subscribe();

    const req = http.expectOne(`${API}/compute`);
    const body = req.request.body as PlacementRequestDto;

    expect(body.graph.nodes).toHaveLength(2);
    for (const node of body.graph.nodes) {
      expect(Object.keys(node).sort()).toEqual(['id', 'lat', 'lon']);
    }
    expect(body.graph.edges).toHaveLength(1);
    for (const edge of body.graph.edges) {
      expect(Object.keys(edge).sort()).toEqual(['distanceMeters', 'sourceId', 'targetId']);
    }

    req.flush({
      stations: [],
      objectiveValue: 0,
      totalNodes: 2,
      coverageDistances: {},
      computationTimeMs: 1,
    } satisfies PlacementResponseDto);
  });

  it('forwards algorithm and tuning parameters verbatim', () => {
    service
      .computePlacement(makeGraph(), 7, 2500, 42, 'GRASP_STRATEGY', 0.4, 750)
      .subscribe();

    const req = http.expectOne(`${API}/compute`);
    const body = req.request.body as PlacementRequestDto;
    expect(body.algorithm).toBe('GRASP_STRATEGY');
    expect(body.k).toBe(7);
    expect(body.maxRadiusMeters).toBe(2500);
    expect(body.iterations).toBe(42);
    expect(body.graspAlpha).toBe(0.4);
    expect(body.graspEvalBudget).toBe(750);

    req.flush({
      stations: [],
      objectiveValue: 0,
      totalNodes: 0,
      coverageDistances: {},
      computationTimeMs: 0,
    } satisfies PlacementResponseDto);
  });

  it('maps HTTP errors to an Error via handleError', () => {
    let captured: Error | undefined;
    service
      .computePlacement(makeGraph(), 1, 100, 1, 'RANDOM_STRATEGY', 0, 0)
      .subscribe({
        next: () => {},
        error: (e: Error) => (captured = e),
      });

    http
      .expectOne(`${API}/compute`)
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(captured).toBeInstanceOf(Error);
    expect(captured!.message.length).toBeGreaterThan(0);
  });
});
