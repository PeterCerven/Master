import {GoogleMap, GoogleMapsModule} from '@angular/google-maps';
import {DecimalPipe} from '@angular/common';
import {Component, computed, DestroyRef, effect, inject, signal, viewChild} from '@angular/core';
import {GraphService} from '@services/graph.service';
import {ThemeService} from '@services/theme.service';
import {GraphEdgeDto, GraphMetrics, GraphNodeDto, GraphResponseDto, PlacementResponseDto, PlacementResultInfo, SavedGraphResponseDto, StationNodeDto} from '@models/my-graph.model';
import {takeUntilDestroyed, toObservable} from '@angular/core/rxjs-interop';
import {filter, skip, Subject, takeUntil} from 'rxjs';
import {MatFabButton} from '@angular/material/button';
import {environment} from '@env/environment.production';
import {PlacementService} from '@services/placement.service';
import {MatDialog} from '@angular/material/dialog';
import {GraphConfigDialog} from '@components/graph-config-dialog/graph-config-dialog';
import {PlacementConfigDialog, PlacementConfigResult} from '@components/placement-config-dialog/placement-config-dialog';
import {PlacementResultPanel} from '@components/placement-result-panel/placement-result-panel';
import {SaveGraphDialog} from '@components/save-graph-dialog/save-graph-dialog';
import {LoadGraphDialog} from '@components/load-graph-dialog/load-graph-dialog';
import {CityImportDialog} from '@components/city-import-dialog/city-import-dialog';
import {TranslocoDirective} from '@jsverse/transloco';
import {MatTooltip} from '@angular/material/tooltip';
import {GoogleMapsOverlay} from '@deck.gl/google-maps';
import {LineLayer, ScatterplotLayer} from '@deck.gl/layers';

interface NodeDatum extends GraphNodeDto {
  position: [number, number];
}

interface EdgeDatum extends GraphEdgeDto {
  sourcePosition: [number, number];
  targetPosition: [number, number];
  color: [number, number, number, number];
}

const COMPONENT_COLORS: [number, number, number, number][] = [
  [231,  76,  60, 255],
  [ 52, 152, 219, 255],
  [155,  89, 182, 255],
  [241, 196,  15, 255],
  [230, 126,  34, 255],
  [236,  72, 153, 255],
  [149, 165, 166, 255],
  [ 52,  73,  94, 255],
  [  0, 172, 193, 255],
  [255, 112,  67, 255],
];

function getComponentColor(componentId: number): [number, number, number, number] {
  return COMPONENT_COLORS[componentId % COMPONENT_COLORS.length];
}

@Component({
  selector: 'app-map',
  imports: [GoogleMapsModule, GoogleMap, MatFabButton, TranslocoDirective, DecimalPipe, MatTooltip, PlacementResultPanel],
  templateUrl: './map.html',
  styleUrl: './map.scss'
})
export class Map {
  protected readonly google = google;
  map = viewChild<GoogleMap>('googleMap');
  private readonly graphService = inject(GraphService);
  private readonly themeService = inject(ThemeService);
  private readonly placementService = inject(PlacementService);
  private readonly dialog = inject(MatDialog);
  private destroyRef = inject(DestroyRef);

  private static readonly SK_GRAPH = 'map_graphData';
  private static readonly SK_PLACEMENT = 'map_placementData';
  private static readonly SK_PLACEMENT_INFO = 'map_placementResultInfo';
  private static readonly SK_METRICS = 'map_graphMetrics';

  loading = false;
  saving = false;
  computingPlacement = false;
  computingMetrics = false;
  loadingElapsed = 0;
  placementElapsed = 0;
  metricsElapsed = 0;
  private loadingTimer: ReturnType<typeof setInterval> | null = null;
  private placementTimer: ReturnType<typeof setInterval> | null = null;
  private metricsTimer: ReturnType<typeof setInterval> | null = null;
  private readonly cancelLoading$ = new Subject<void>();
  private readonly cancelPlacement$ = new Subject<void>();
  private readonly cancelMetrics$ = new Subject<void>();
  get isBusy(): boolean {
    return this.loading || this.saving || this.computingPlacement || this.computingMetrics;
  }

  graphData: GraphResponseDto | null = null;
  placementData: PlacementResponseDto | null = null;
  placementResultInfo: PlacementResultInfo | null = null;
  graphMetrics: GraphMetrics | null = null;
  mapVisible = signal(true);
  private deckOverlay: GoogleMapsOverlay | null = null;
  private stationMarkers: google.maps.marker.AdvancedMarkerElement[] = [];
  private rightClickListenerSetup = false;

  options = computed<google.maps.MapOptions>(() => ({
    center: {lat: 48.1478, lng: 17.1072},
    zoom: 13,
    mapId: environment.googleMapId,
    renderingType: 'VECTOR' as google.maps.RenderingType,
    colorScheme: (this.themeService.isDarkMode() ? 'DARK' : 'LIGHT') as google.maps.ColorScheme,
    disableDefaultUI: true,
    zoomControl: false,
    mapTypeControl: false,
    streetViewControl: false,
    fullscreenControl: false,
  }));

  constructor() {
    this.restoreFromSession();

    // colorScheme is init-only — destroy and recreate the map component on theme change
    toObservable(this.themeService.isDarkMode)
      .pipe(skip(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.mapVisible.set(false);
        setTimeout(() => this.mapVisible.set(true));
      });

    this.destroyRef.onDestroy(() => {
      if (this.loadingTimer) clearInterval(this.loadingTimer);
      if (this.placementTimer) clearInterval(this.placementTimer);
      if (this.metricsTimer) clearInterval(this.metricsTimer);
      this.cancelLoading$.complete();
      this.cancelPlacement$.complete();
      this.cancelMetrics$.complete();
    });

    // Setup map each time it becomes available (initial load and after recreation)
    effect((onCleanup) => {
      const mapEl = this.map();
      if (!mapEl) return;

      this.rightClickListenerSetup = false;

      const subscription = mapEl.tilesloaded.subscribe(async () => {
        if (mapEl.googleMap && !this.rightClickListenerSetup) {
          this.rightClickListenerSetup = true;
          subscription.unsubscribe();

          // Pre-load marker library for station markers
          await google.maps.importLibrary('marker');

          // After recreation, drop orphaned references and re-render onto new map instance
          this.deckOverlay = null;
          this.stationMarkers = [];
          if (this.graphData) {
            this.displayGraphOnMap(this.graphData);
          }
          if (this.placementData) {
            this.displayStationsOnMap(this.placementData.stations);
          }
        }
      });

      onCleanup(() => subscription.unsubscribe());
    });
  }

  openGraphConfigDialog(): void {
    this.dialog.open(GraphConfigDialog, {minWidth: 'min(420px, calc(100vw - 32px))'}).afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(result => {
        if (result instanceof File) this.importGraphFromFile(result);
        else if (typeof result === 'string') this.importSampleGraph(result);
      });
  }

  openCityImportDialog(): void {
    this.dialog.open(CityImportDialog, { minWidth: 'min(380px, calc(100vw - 32px))' }).afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(city => { if (city) this.importCityGraph(city); });
  }

  importCityGraph(city: string): void {
    this.loading = true;
    this.startTimer('loading');
    this.graphService.importCityGraph(city)
      .pipe(takeUntil(this.cancelLoading$), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (graph: GraphResponseDto) => {
          this.graphData = graph;
          this.graphMetrics = null;
          this.displayGraphOnMap(graph);
          this.loading = false;
          this.stopTimer('loading');
          this.saveToSession();
        },
        error: (err: Error) => {
          this.loading = false;
          this.stopTimer('loading');
          alert(err.message || 'Failed to import city graph.');
        }
      });
  }

  importSampleGraph(filename: string): void {
    this.loading = true;
    this.startTimer('loading');
    this.graphService.importSampleFile(filename)
      .pipe(takeUntil(this.cancelLoading$), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (graph: GraphResponseDto) => {
          this.graphData = graph;
          this.graphMetrics = null;
          this.displayGraphOnMap(graph);
          this.loading = false;
          this.stopTimer('loading');
          this.saveToSession();
        },
        error: () => {
          this.loading = false;
          this.stopTimer('loading');
          alert('Failed to import sample file.');
        }
      });
  }

  importGraphFromFile(file: File): void {
    this.loading = true;
    this.startTimer('loading');
    this.graphService.generateGraphFromFile(file)
      .pipe(takeUntil(this.cancelLoading$), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (graph: GraphResponseDto) => {
          this.graphData = graph;
          this.graphMetrics = null;
          this.displayGraphOnMap(graph);
          this.loading = false;
          this.stopTimer('loading');
          this.saveToSession();
        },
        error: (error) => {
          console.error('Error loading graph:', error);
          this.loading = false;
          this.stopTimer('loading');
          alert('Failed to load graph from backend. Please check the console for details.');
        }
      });
  }

  private displayGraphOnMap(graph: GraphResponseDto): void {
    const mapInstance = this.map()?.googleMap;
    if (!mapInstance) {
      console.error('Map instance not available');
      return;
    }

    this.clearGraph();

    const nodeMap: Record<string, GraphNodeDto> = {};
    graph.nodes.forEach(node => (nodeMap[node.id] = node));

    this.renderGraphWithDeckGl(mapInstance, graph.nodes, graph.edges, nodeMap);
    this.fitGraphBounds(mapInstance, graph.nodes);
  }

  private renderGraphWithDeckGl(
    nativeMap: google.maps.Map,
    nodes: GraphNodeDto[],
    edges: GraphEdgeDto[],
    nodeMap: Record<string, GraphNodeDto>
  ): void {
    const nodeData: NodeDatum[] = nodes.map(n => ({...n, position: [n.lon, n.lat]}));

    const nodeComponentMap = new Map<string, number>(nodes.map(n => [n.id, n.componentId]));

    const edgeData: EdgeDatum[] = edges
      .filter(e => nodeMap[e.sourceId] && nodeMap[e.targetId])
      .map(e => ({
        ...e,
        sourcePosition: [nodeMap[e.sourceId].lon, nodeMap[e.sourceId].lat],
        targetPosition: [nodeMap[e.targetId].lon, nodeMap[e.targetId].lat],
        color: getComponentColor(nodeComponentMap.get(e.sourceId) ?? 0),
      }));

    const edgeLayer = new LineLayer<EdgeDatum>({
      id: 'graph-edges',
      data: edgeData,
      getSourcePosition: d => d.sourcePosition,
      getTargetPosition: d => d.targetPosition,
      getColor: (d: EdgeDatum) => [d.color[0], d.color[1], d.color[2], 178],
      getWidth: 3,
      widthUnits: 'pixels',
      pickable: true,
      onClick: ({object, coordinate}) => {
        if (!object || !coordinate) return;
        const infoWindow = new google.maps.InfoWindow({
          content: `<div style="color: #333;">
                      <h4>Edge</h4>
                      <p>From: ${object.sourceId}</p>
                      <p>To: ${object.targetId}</p>
                      <p>Distance: ${object.distanceMeters.toFixed(2)}m</p>
                      ${object.roadName ? `<p>Road: ${object.roadName}</p>` : ''}
                    </div>`,
          position: {lat: coordinate[1], lng: coordinate[0]},
        });
        infoWindow.open(nativeMap);
      },
    });

    const nodeLayer = new ScatterplotLayer<NodeDatum>({
      id: 'graph-nodes',
      data: nodeData,
      getPosition: d => d.position,
      getFillColor: (d: NodeDatum) => getComponentColor(d.componentId),
      getLineColor: [255, 255, 255, 255],
      getRadius: 6,
      radiusUnits: 'pixels',
      stroked: true,
      lineWidthMinPixels: 2,
      pickable: true,
      onClick: ({object, coordinate}) => {
        if (!object || !coordinate) return;
        const infoWindow = new google.maps.InfoWindow({
          content: `<div style="color: #333;">
                      <h4>Node ${object.id}</h4>
                      <p>Lat: ${object.lat.toFixed(5)}</p>
                      <p>Lon: ${object.lon.toFixed(5)}</p>
                      ${object.roadName ? `<p>Road: ${object.roadName}</p>` : ''}
                      ${object.roadClass ? `<p>Class: ${object.roadClass}</p>` : ''}
                    </div>`,
          position: {lat: coordinate[1], lng: coordinate[0]},
        });
        infoWindow.open(nativeMap);
      },
    });

    if (!this.deckOverlay) {
      this.deckOverlay = new GoogleMapsOverlay({
        layers: [edgeLayer, nodeLayer],
        interleaved: false
      });
      this.deckOverlay.setMap(nativeMap);
    } else {
      this.deckOverlay.setProps({layers: [edgeLayer, nodeLayer]});
    }
  }

  private fitGraphBounds(nativeMap: google.maps.Map, nodes: GraphNodeDto[]): void {
    if (nodes.length === 0) return;

    const bounds = new google.maps.LatLngBounds();
    nodes.forEach(node => {
      bounds.extend({lat: node.lat, lng: node.lon});
    });

    const padding = {top: 50, right: 50, bottom: 50, left: 50};
    nativeMap.fitBounds(bounds, padding);
  }

  private clearGraph(): void {
    if (this.deckOverlay) {
      this.deckOverlay.setMap(null);
      this.deckOverlay = null;
    }
  }

  openSaveGraphDialog(): void {
    if (!this.graphData) return;
    this.dialog.open(SaveGraphDialog, {
      data: { graphData: this.graphData, placementData: this.placementData },
      minWidth: 'min(360px, calc(100vw - 32px))'
    })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef), filter(r => !!r))
      .subscribe(({ name }) => {
        this.saving = true;
        this.graphService.saveGraph(this.graphData!, name, this.placementData?.stations ?? null)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => { this.saving = false; },
            error: () => { this.saving = false; }
          });
      });
  }

  computePlacement(): void {
    if (!this.graphData) return;

    this.dialog.open(PlacementConfigDialog, {minWidth: 'min(380px, calc(100vw - 32px))'}).afterClosed()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        filter((result): result is PlacementConfigResult => !!result),
      )
      .subscribe(({strategy, k, maxRadiusMeters, iterations, graspAlpha, graspEvalBudget}) => {
        this.computingPlacement = true;
        this.startTimer('placement');
        this.placementService.computePlacement(this.graphData!, k, maxRadiusMeters, iterations, strategy, graspAlpha, graspEvalBudget)
          .pipe(takeUntil(this.cancelPlacement$), takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: (result: PlacementResponseDto) => {
              this.placementData = result;
              this.placementResultInfo = {
                strategy,
                k,
                maxRadiusMeters,
                iterations: strategy !== 'GREEDY_STRATEGY' ? iterations : null,
                graspAlpha: strategy === 'GRASP_STRATEGY' ? graspAlpha : null,
                graspEvalBudget: strategy === 'GRASP_STRATEGY' ? graspEvalBudget : null,
                computationTimeMs: result.computationTimeMs,
                stationsPlaced: result.stations.length,
              };
              this.displayStationsOnMap(result.stations);
              this.computingPlacement = false;
              this.stopTimer('placement');
              this.saveToSession();
            },
            error: (error) => {
              console.error('Error computing placement:', error);
              this.computingPlacement = false;
              this.stopTimer('placement');
              alert('Failed to compute placement. Please check the console for details.');
            }
          });
      });
  }

  computeGraphMetrics(): void {
    this.computingMetrics = true;
    this.startTimer('metrics');
    this.graphService.computeGraphMetrics()
      .pipe(takeUntil(this.cancelMetrics$), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (metrics: GraphMetrics) => {
          this.graphMetrics = metrics;
          this.computingMetrics = false;
          this.stopTimer('metrics');
          this.saveToSession();
        },
        error: () => { this.computingMetrics = false; this.stopTimer('metrics'); }
      });
  }

  cancelLoading(): void {
    this.cancelLoading$.next();
    this.loading = false;
    this.stopTimer('loading');
  }

  cancelPlacement(): void {
    this.cancelPlacement$.next();
    this.computingPlacement = false;
    this.stopTimer('placement');
  }

  cancelMetrics(): void {
    this.cancelMetrics$.next();
    this.computingMetrics = false;
    this.stopTimer('metrics');
  }

  private startTimer(which: 'loading' | 'placement' | 'metrics'): void {
    this.stopTimer(which);
    if (which === 'loading') {
      this.loadingElapsed = 0;
      this.loadingTimer = setInterval(() => this.loadingElapsed++, 1000);
    } else if (which === 'placement') {
      this.placementElapsed = 0;
      this.placementTimer = setInterval(() => this.placementElapsed++, 1000);
    } else {
      this.metricsElapsed = 0;
      this.metricsTimer = setInterval(() => this.metricsElapsed++, 1000);
    }
  }

  private stopTimer(which: 'loading' | 'placement' | 'metrics'): void {
    if (which === 'loading' && this.loadingTimer) {
      clearInterval(this.loadingTimer);
      this.loadingTimer = null;
    } else if (which === 'placement' && this.placementTimer) {
      clearInterval(this.placementTimer);
      this.placementTimer = null;
    } else if (which === 'metrics' && this.metricsTimer) {
      clearInterval(this.metricsTimer);
      this.metricsTimer = null;
    }
  }

  private displayStationsOnMap(stations: StationNodeDto[]): void {
    const mapInstance = this.map()?.googleMap;
    if (!mapInstance) return;

    this.stationMarkers.forEach(marker => (marker.map = null));
    this.stationMarkers = [];

    const {AdvancedMarkerElement} = google.maps.marker;

    stations.forEach(station => {
      const square = document.createElement('div');
      square.style.width = '18px';
      square.style.height = '18px';
      square.style.backgroundColor = '#2ecc71';
      square.style.border = '2px solid #ffffff';
      square.style.borderRadius = '3px';
      square.style.boxShadow = '0 0 4px rgba(0,0,0,0.6)';
      square.style.display = 'flex';
      square.style.alignItems = 'center';
      square.style.justifyContent = 'center';
      square.style.color = '#ffffff';
      square.style.fontSize = '9px';
      square.style.fontWeight = '700';
      square.textContent = String(station.rank);

      const marker = new AdvancedMarkerElement({
        map: mapInstance,
        position: {lat: station.lat, lng: station.lon},
        title: `Station #${station.rank}`,
        content: square,
      });

      marker.addListener('gmp-click', () => {
        const infoWindow = new google.maps.InfoWindow({
          content: `<div style="color: #333;">
                      <h4>Charging Station #${station.rank}</h4>
                      <p>ID: ${station.id}</p>
                      <p>Lat: ${station.lat.toFixed(5)}</p>
                      <p>Lon: ${station.lon.toFixed(5)}</p>
                    </div>`
        });
        infoWindow.open({anchor: marker, map: mapInstance});
      });

      this.stationMarkers.push(marker);
    });
  }

  openLoadGraphDialog(): void {
    this.dialog.open(LoadGraphDialog, {minWidth: 'min(620px, calc(100vw - 32px))'})
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef), filter(id => id != null))
      .subscribe(id => {
        this.loading = true;
        this.startTimer('loading');
        this.graphService.loadGraphFromDatabase(id)
          .pipe(takeUntil(this.cancelLoading$), takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: (saved: SavedGraphResponseDto) => {
              this.graphData = { nodes: saved.nodes, edges: saved.edges, metrics: saved.metrics };
              this.graphMetrics = null;
              this.displayGraphOnMap(this.graphData);
              if (saved.stations.length > 0) {
                this.placementData = { stations: saved.stations, objectiveValue: 0,
                  totalNodes: saved.nodes.length, coverageDistances: {}, computationTimeMs: 0 };
                this.displayStationsOnMap(saved.stations);
              } else {
                this.placementData = null;
                this.stationMarkers.forEach(m => (m.map = null));
                this.stationMarkers = [];
              }
              this.loading = false;
              this.stopTimer('loading');
              this.saveToSession();
            },
            error: () => { this.loading = false; this.stopTimer('loading'); }
          });
      });
  }

  clearAll(): void {
    this.graphData = null;
    this.placementData = null;
    this.placementResultInfo = null;
    this.graphMetrics = null;
    this.clearGraph();
    this.stationMarkers.forEach(marker => (marker.map = null));
    this.stationMarkers = [];
    this.clearSession();
  }

  private saveToSession(): void {
    const graphToStore: GraphResponseDto | null = this.graphData
      ? { ...this.graphData, metrics: null }
      : null;

    const placementToStore: PlacementResponseDto | null = this.placementData
      ? { ...this.placementData, coverageDistances: {} }
      : null;

    try {
      sessionStorage.setItem(Map.SK_PLACEMENT, JSON.stringify(placementToStore));
    } catch (e) {
      console.warn('Could not persist placement data to sessionStorage', e);
    }
    try {
      sessionStorage.setItem(Map.SK_PLACEMENT_INFO, JSON.stringify(this.placementResultInfo));
    } catch (e) {
      console.warn('Could not persist placement result info to sessionStorage', e);
    }
    try {
      sessionStorage.setItem(Map.SK_METRICS, JSON.stringify(this.graphMetrics));
    } catch (e) {
      console.warn('Could not persist graph metrics to sessionStorage', e);
    }

    try {
      sessionStorage.setItem(Map.SK_GRAPH, JSON.stringify(graphToStore));
    } catch (e) {
      console.warn('Graph data too large for sessionStorage; graph will not be restored on refresh', e);
      sessionStorage.removeItem(Map.SK_GRAPH);
    }
  }

  private restoreFromSession(): void {
    try {
      const graph = sessionStorage.getItem(Map.SK_GRAPH);
      const placement = sessionStorage.getItem(Map.SK_PLACEMENT);
      const placementInfo = sessionStorage.getItem(Map.SK_PLACEMENT_INFO);
      const metrics = sessionStorage.getItem(Map.SK_METRICS);
      if (graph) this.graphData = JSON.parse(graph);
      if (placement) this.placementData = JSON.parse(placement);
      if (placementInfo) this.placementResultInfo = JSON.parse(placementInfo);
      if (metrics) this.graphMetrics = JSON.parse(metrics);
    } catch (e) {
      console.warn('Could not restore state from sessionStorage', e);
    }
  }

  private clearSession(): void {
    [Map.SK_GRAPH, Map.SK_PLACEMENT, Map.SK_PLACEMENT_INFO, Map.SK_METRICS]
      .forEach(k => sessionStorage.removeItem(k));
  }
}
