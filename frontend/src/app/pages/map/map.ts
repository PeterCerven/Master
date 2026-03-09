import {GoogleMap, GoogleMapsModule} from '@angular/google-maps';
import {Component, computed, DestroyRef, effect, inject, signal, viewChild} from '@angular/core';
import {GraphService} from '@services/graph.service';
import {ThemeService} from '@services/theme.service';
import {GraphEdgeDto, GraphNodeDto, GraphResponseDto, PlacementResponseDto, SavedGraphResponseDto, StationNodeDto} from '@models/my-graph.model';
import {takeUntilDestroyed, toObservable} from '@angular/core/rxjs-interop';
import {filter, skip} from 'rxjs';
import {MatFabButton} from '@angular/material/button';
import {environment} from '@env/environment.production';
import {PlacementService} from '@services/placement.service';
import {MatDialog} from '@angular/material/dialog';
import {GraphConfigDialog} from '@components/graph-config-dialog/graph-config-dialog';
import {PlacementConfigDialog, PlacementConfigResult} from '@components/placement-config-dialog/placement-config-dialog';
import {SaveGraphDialog} from '@components/save-graph-dialog/save-graph-dialog';
import {LoadGraphDialog} from '@components/load-graph-dialog/load-graph-dialog';
import {TranslocoDirective} from '@jsverse/transloco';

@Component({
  selector: 'app-map',
  imports: [GoogleMapsModule, GoogleMap, MatFabButton, TranslocoDirective],
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

  loading = false;
  saving = false;
  computingPlacement = false;
  graphData: GraphResponseDto | null = null;
  placementData: PlacementResponseDto | null = null;
  mapVisible = signal(true);
  private graphMarkers: google.maps.marker.AdvancedMarkerElement[] = [];
  private graphPolylines: google.maps.Polyline[] = [];
  private stationMarkers: google.maps.marker.AdvancedMarkerElement[] = [];
  private rightClickListenerSetup = false;

  options = computed<google.maps.MapOptions>(() => ({
    center: {lat: 48.1478, lng: 17.1072},
    zoom: 13,
    mapId: environment.googleMapId,
    colorScheme: (this.themeService.isDarkMode() ? 'DARK' : 'LIGHT') as google.maps.ColorScheme,
    disableDefaultUI: true,
    zoomControl: false,
    mapTypeControl: false,
    streetViewControl: false,
    fullscreenControl: false,
  }));

  constructor() {
    // colorScheme is init-only — destroy and recreate the map component on theme change
    toObservable(this.themeService.isDarkMode)
      .pipe(skip(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.mapVisible.set(false);
        setTimeout(() => this.mapVisible.set(true));
      });

    // Setup map each time it becomes available (initial load and after recreation)
    effect((onCleanup) => {
      const mapEl = this.map();
      if (!mapEl) return;

      this.rightClickListenerSetup = false;

      const subscription = mapEl.tilesloaded.subscribe(async () => {
        if (mapEl.googleMap && !this.rightClickListenerSetup) {
          const {AdvancedMarkerElement: _AdvancedMarkerElement} = await google.maps.importLibrary('marker') as google.maps.MarkerLibrary;
          if (!_AdvancedMarkerElement) {
            console.error('AdvancedMarkerElement not available in the Google Maps library');
            subscription.unsubscribe();
            return;
          }
          subscription.unsubscribe();

          // After recreation, drop orphaned references and re-render onto new map instance
          this.graphMarkers = [];
          this.graphPolylines = [];
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
      });
  }

  importGraphFromFile(file: File): void {
    this.loading = true;
    this.graphService.generateGraphFromFile(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (graph: GraphResponseDto) => {
          this.graphData = graph;
          this.displayGraphOnMap(graph);
          this.loading = false;
        },
        error: (error) => {
          console.error('Error loading graph:', error);
          this.loading = false;
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
    graph.nodes.forEach(node => nodeMap[node.id] = node);

    this.displayGraphNodes(mapInstance, graph.nodes);
    this.displayGraphEdges(mapInstance, graph.edges, nodeMap);
    this.fitGraphBounds(mapInstance, graph.nodes);
  }

  private async displayGraphNodes(nativeMap: google.maps.Map, nodes: GraphNodeDto[]): Promise<void> {
    const {AdvancedMarkerElement} = google.maps.marker;

    nodes.forEach(node => {
      const dot = document.createElement('div');
      dot.style.width = '12px';
      dot.style.height = '12px';
      dot.style.backgroundColor = '#e74c3c';
      dot.style.border = '2px solid #ffffff';
      dot.style.borderRadius = '50%';
      dot.style.boxShadow = '0 0 3px rgba(0,0,0,0.6)';

      const marker = new AdvancedMarkerElement({
        map: nativeMap,
        position: {lat: node.lat, lng: node.lon},
        title: `Node ${node.id}`,
        content: dot,
      });

      marker.addListener('gmp-click', () => {
        const infoWindow = new google.maps.InfoWindow({
          content: `<div style="color: #333;">
                      <h4>Node ${node.id}</h4>
                      <p>Lat: ${node.lat.toFixed(5)}</p>
                      <p>Lon: ${node.lon.toFixed(5)}</p>
                      ${node.roadName ? `<p>Road: ${node.roadName}</p>` : ''}
                      ${node.roadClass ? `<p>Class: ${node.roadClass}</p>` : ''}
                    </div>`
        });
        infoWindow.open({anchor: marker, map: nativeMap});
      });

      this.graphMarkers.push(marker);
    });
  }

  private displayGraphEdges(
    nativeMap: google.maps.Map,
    edges: GraphEdgeDto[],
    nodeMap: Record<string, GraphNodeDto>
  ): void {
    edges.forEach(edge => {
      const sourceNode = nodeMap[edge.sourceId];
      const targetNode = nodeMap[edge.targetId];

      if (!sourceNode || !targetNode) {
        console.warn(`Missing node for edge: ${edge.sourceId} -> ${edge.targetId}`);
        return;
      }

      const polyline = new google.maps.Polyline({
        path: [
          {lat: sourceNode.lat, lng: sourceNode.lon},
          {lat: targetNode.lat, lng: targetNode.lon}
        ],
        geodesic: true,
        strokeColor: '#3498db',
        strokeOpacity: 0.7,
        strokeWeight: 3,
        map: nativeMap
      });

      polyline.addListener('click', () => {
        const infoWindow = new google.maps.InfoWindow({
          content: `<div style="color: #333;">
                      <h4>Edge</h4>
                      <p>From: ${edge.sourceId}</p>
                      <p>To: ${edge.targetId}</p>
                      <p>Distance: ${edge.distanceMeters.toFixed(2)}m</p>
                      ${edge.roadName ? `<p>Road: ${edge.roadName}</p>` : ''}
                    </div>`,
          position: {lat: sourceNode.lat, lng: sourceNode.lon}
        });
        infoWindow.open(nativeMap);
      });

      this.graphPolylines.push(polyline);
    });
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
    this.graphMarkers.forEach(marker => {
      marker.map = null;
    });
    this.graphMarkers = [];

    this.graphPolylines.forEach(polyline => {
      polyline.setMap(null);
    });
    this.graphPolylines = [];
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
      .subscribe(({strategy, k, maxRadiusMeters, iterations}) => {
        this.computingPlacement = true;
        this.placementService.computePlacement(this.graphData!, k, maxRadiusMeters, iterations, strategy)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: (result: PlacementResponseDto) => {
              this.placementData = result;
              this.displayStationsOnMap(result.stations);
              this.computingPlacement = false;
            },
            error: (error) => {
              console.error('Error computing placement:', error);
              this.computingPlacement = false;
              alert('Failed to compute placement. Please check the console for details.');
            }
          });
      });
  }

  private displayStationsOnMap(stations: StationNodeDto[]): void {
    const mapInstance = this.map()?.googleMap;
    if (!mapInstance) return;

    this.stationMarkers.forEach(marker => marker.map = null);
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
        this.graphService.loadGraphFromDatabase(id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: (saved: SavedGraphResponseDto) => {
              this.graphData = { nodes: saved.nodes, edges: saved.edges };
              this.displayGraphOnMap(this.graphData);
              if (saved.stations.length > 0) {
                this.placementData = { stations: saved.stations, objectiveValue: 0,
                  totalNodes: saved.nodes.length, coverageDistances: {} };
                this.displayStationsOnMap(saved.stations);
              } else {
                this.placementData = null;
                this.stationMarkers.forEach(m => m.map = null);
                this.stationMarkers = [];
              }
              this.loading = false;
            },
            error: () => { this.loading = false; }
          });
      });
  }

  clearAll(): void {
    this.graphData = null;
    this.placementData = null;
    this.clearGraph();
    this.stationMarkers.forEach(marker => marker.map = null);
    this.stationMarkers = [];
    console.log('All graph data cleared');
  }
}
