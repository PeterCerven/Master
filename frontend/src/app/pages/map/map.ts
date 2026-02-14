import {GoogleMapsModule, GoogleMap} from '@angular/google-maps';
import {AfterViewInit, Component, DestroyRef, effect, inject, signal, viewChild} from '@angular/core';
import {GraphService} from '@services/graph.service';
import {ThemeService} from '@services/theme.service';
import {GraphResponseDto, GraphNodeDto, GraphEdgeDto} from '@models/my-graph.model';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatFabButton} from '@angular/material/button';
import {environment} from '@env/environment.production';

const DARK_MAP_STYLES: google.maps.MapTypeStyle[] = [
  {elementType: 'geometry', stylers: [{color: '#242f3e'}]},
  {elementType: 'labels.text.stroke', stylers: [{color: '#242f3e'}]},
  {elementType: 'labels.text.fill', stylers: [{color: '#746855'}]},
  {featureType: 'administrative.locality', elementType: 'labels.text.fill', stylers: [{color: '#d59563'}]},
  {featureType: 'poi', elementType: 'labels.text.fill', stylers: [{color: '#d59563'}]},
  {featureType: 'poi.park', elementType: 'geometry', stylers: [{color: '#263c3f'}]},
  {featureType: 'poi.park', elementType: 'labels.text.fill', stylers: [{color: '#6b9a76'}]},
  {featureType: 'road', elementType: 'geometry', stylers: [{color: '#38414e'}]},
  {featureType: 'road', elementType: 'geometry.stroke', stylers: [{color: '#212a37'}]},
  {featureType: 'road', elementType: 'labels.text.fill', stylers: [{color: '#9ca5b3'}]},
  {featureType: 'road.highway', elementType: 'geometry', stylers: [{color: '#746855'}]},
  {featureType: 'road.highway', elementType: 'geometry.stroke', stylers: [{color: '#1f2835'}]},
  {featureType: 'road.highway', elementType: 'labels.text.fill', stylers: [{color: '#f3d19c'}]},
  {featureType: 'transit', elementType: 'geometry', stylers: [{color: '#2f3948'}]},
  {featureType: 'transit.station', elementType: 'labels.text.fill', stylers: [{color: '#d59563'}]},
  {featureType: 'water', elementType: 'geometry', stylers: [{color: '#17263c'}]},
  {featureType: 'water', elementType: 'labels.text.fill', stylers: [{color: '#515c6d'}]},
  {featureType: 'water', elementType: 'labels.text.stroke', stylers: [{color: '#17263c'}]},
];


@Component({
  selector: 'app-map',
  imports: [GoogleMapsModule, GoogleMap, MatFabButton],
  templateUrl: './map.html',
  styleUrl: './map.scss'
})
export class Map implements AfterViewInit {
  protected readonly google = google;
  map = viewChild.required<GoogleMap>('googleMap');
  private readonly graphService = inject(GraphService);
  private readonly themeService = inject(ThemeService);
  private destroyRef = inject(DestroyRef);

  loading = false;
  saving = false;
  processing = false;
  graphData: GraphResponseDto | null = null;
  pendingPoints = signal<Array<{ lat: number, lon: number }>>([]);
  private graphMarkers: google.maps.marker.AdvancedMarkerElement[] = [];
  private graphPolylines: google.maps.Polyline[] = [];
  private pendingMarkers: google.maps.marker.AdvancedMarkerElement[] = [];
  private rightClickListenerSetup = false;

  ngAfterViewInit(): void {
    const mapInstance = this.map();

    const subscription = mapInstance.tilesloaded.subscribe(async () => {
      if (mapInstance.googleMap && !this.rightClickListenerSetup) {
        const {AdvancedMarkerElement: _AdvancedMarkerElement} = await google.maps.importLibrary("marker") as google.maps.MarkerLibrary;
        if (!_AdvancedMarkerElement) {
          console.error('AdvancedMarkerElement not available in the Google Maps library');
          subscription.unsubscribe();
          return;
        }
        this.setupRightClickListener(mapInstance.googleMap);
        this.rightClickListenerSetup = true;
        subscription.unsubscribe();
      }
    });
  }

  options: google.maps.MapOptions = {
    center: {lat: 48.1478, lng: 17.1072},
    zoom: 13,
    mapId: environment.googleMapId,
    disableDefaultUI: true,
    zoomControl: false,
    mapTypeControl: false,
    streetViewControl: false,
    fullscreenControl: false,
  };

  private darkModeEffect = effect(() => {
    const isDark = this.themeService.isDarkMode();
    const mapInstance = this.map()?.googleMap;
    if (mapInstance) {
      mapInstance.setOptions({
        styles: isDark ? DARK_MAP_STYLES : [],
        backgroundColor: isDark ? '#242f3e' : undefined,
      });
    }
  });

  importGraphFromFile(event: Event): void {
    this.loading = true;
    this.graphService.generateGraphFromFile(event)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (graph: GraphResponseDto) => {
          this.graphData = graph;
          this.displayGraphOnMap(graph);
          this.loading = false;
          console.log('Graph loaded successfully:', graph);
        },
        error: (error) => {
          console.error('Error loading graph:', error);
          this.loading = false;
          alert('Failed to load graph from backend. Please check the console for details.');
        }
      });
  }

  private displayGraphOnMap(graph: GraphResponseDto): void {
    const mapInstance = this.map().googleMap;
    if (!mapInstance) {
      console.error('Map instance not available');
      return;
    }

    // Clear existing graph visualization
    this.clearGraph();

    // Create a lookup object for quick node access
    const nodeMap: Record<string, GraphNodeDto> = {};
    graph.nodes.forEach(node => nodeMap[node.id] = node);

    // Display nodes as markers
    this.displayGraphNodes(mapInstance, graph.nodes);

    // Display edges as polylines
    this.displayGraphEdges(mapInstance, graph.edges, nodeMap);

    // Adjust map bounds to fit the graph
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

      // Add click listener to show edge info
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

    nativeMap.fitBounds(bounds);
    // Add some padding
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

  saveCurrentGraph(): void {
    if (!this.graphData) {
      alert('No graph data to save');
      return;
    }

    const name = window.prompt('Enter a name for this graph:');
    if (!name || name.trim() === '') {
      alert('Graph name is required');
      return;
    }

    this.saving = true;
    this.graphService.saveGraph(this.graphData, name.trim())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.saving = false;
          alert(`Graph "${name}" saved successfully!`);
          console.log('Graph saved:', response);
        },
        error: (error) => {
          console.error('Error saving graph:', error);
          this.saving = false;
          alert('Failed to save graph. Please check the console for details.');
        }
      });
  }

  private setupRightClickListener(nativeMap: google.maps.Map): void {
    nativeMap.addListener('rightclick', (event: google.maps.MapMouseEvent) => {
      if (event.latLng) {
        const lat = event.latLng.lat();
        const lon = event.latLng.lng();
        this.addPendingPoint(lat, lon);
      }
    });
  }

  private addPendingPoint(lat: number, lon: number): void {
    const mapInstance = this.map().googleMap;
    if (!mapInstance) {
      console.error('Map instance not available');
      return;
    }

    this.pendingPoints.update(points => [...points, {lat, lon}]);

    // Pending yellow marker
    const {AdvancedMarkerElement} = google.maps.marker;

    const dot = document.createElement('div');
    dot.style.width = '10px';
    dot.style.height = '10px';
    dot.style.backgroundColor = '#f39c12';
    dot.style.border = '2px solid #ffffff';
    dot.style.borderRadius = '50%';
    dot.style.boxShadow = '0 0 3px rgba(0,0,0,0.6)';

    const marker = new AdvancedMarkerElement({
      map: mapInstance,
      position: {lat, lng: lon},
      title: 'Pending Point',
      content: dot,
    });

    this.pendingMarkers.push(marker);
    console.log(`Added pending point at (${lat.toFixed(5)}, ${lon.toFixed(5)}). Total: ${this.pendingPoints().length}`);
  }

  processGraph(): void {
    if (this.pendingPoints().length === 0) {
      alert('Please add some points by right-clicking on the map first');
      return;
    }

    this.processing = true;
    const points = this.pendingPoints().map(p => ({lat: p.lat, lon: p.lon}));

    this.graphService.updateGraphWithPoints(this.graphData, points)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (graph: GraphResponseDto) => {
          this.graphData = graph;
          this.clearPendingPoints();
          this.displayGraphOnMap(graph);

          this.processing = false;
          console.log('Graph processed successfully:', graph);
          alert(`Graph created with ${graph.nodes.length} nodes and ${graph.edges.length} edges`);
        },
        error: (error) => {
          console.error('Error processing graph:', error);
          this.processing = false;
          alert('Failed to process graph. Please check the console for details.');
        }
      });
  }

  private clearPendingPoints(): void {
    this.pendingMarkers.forEach(marker => {
      marker.map = null;
    });
    this.pendingMarkers = [];
    this.pendingPoints.set([]);
  }

  importGraphFromDatabase() {
    this.loading = true;
    this.graphService.loadGraphFromDatabase(1)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (graph: GraphResponseDto) => {
          this.graphData = graph;
          this.displayGraphOnMap(graph);
          this.loading = false;
          console.log('Graph loaded successfully:', graph);
        },
        error: (error) => {
          console.error('Error loading graph from database:', error);
          this.loading = false;
          alert('Failed to load graph from database. Please check the console for details.');
        }
      });
  }

  clearAll(): void {
    this.graphData = null;
    this.clearGraph();
    this.clearPendingPoints();
    console.log('All graph data and pending points cleared');
  }

}
