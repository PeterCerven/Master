import {GoogleMapsModule, GoogleMap} from '@angular/google-maps';
import {AfterViewInit, Component, inject, viewChild} from '@angular/core';
import {environment} from '@env/environment.production';
import {GraphService} from '@services/graph.service';
import {MyGraph, GraphNode, GraphEdge} from '@models/my-graph.model';


@Component({
  selector: 'app-map',
  imports: [GoogleMapsModule, GoogleMap],
  templateUrl: './map.html',
  styleUrl: './map.scss'
})
export class Map implements AfterViewInit {
  protected readonly google = google;
  map = viewChild.required<GoogleMap>('googleMap');
  private readonly graphService = inject(GraphService);

  loading = false;
  graphData: MyGraph | null = null;
  private graphMarkers: google.maps.marker.AdvancedMarkerElement[] = [];
  private graphPolylines: google.maps.Polyline[] = [];

  ngAfterViewInit(): void {
    const mapInstance = this.map();

    mapInstance.tilesloaded.subscribe(async () => {
      if (mapInstance.googleMap) {
        const {AdvancedMarkerElement: _AdvancedMarkerElement} = await google.maps.importLibrary("marker") as google.maps.MarkerLibrary;
        if (!_AdvancedMarkerElement) {
          console.error('AdvancedMarkerElement not available in the Google Maps library');
          return;
        }
        this.addAdvancedMarkers(mapInstance.googleMap);
      } else {
        console.error('Google Map object not available');
      }
    });
  }

  options: google.maps.MapOptions = {
    center: {lat: 48.1478, lng: 17.1072},
    zoom: 13,
    disableDefaultUI: true,
    zoomControl: false,
    mapTypeControl: false,
    streetViewControl: false,
    fullscreenControl: false,
    mapId: environment.googleMapId,
  };

  markerData = [
    {position: {lat: 48.1472, lng: 17.1070}, title: 'Location1'},
    {position: {lat: 48.1448, lng: 17.1062}, title: 'Location2'},
    {position: {lat: 48.1428, lng: 17.1272}, title: 'Location3'},
    {position: {lat: 48.1458, lng: 17.1062}, title: 'Location4'}
  ];


  addAdvancedMarkers(nativeMap: google.maps.Map): void {
    const {AdvancedMarkerElement} = google.maps.marker;

    this.markerData.forEach(data => {

      const dot = document.createElement('div');

      dot.style.width = '20px';
      dot.style.height = '20px';
      dot.style.backgroundColor = '#1a73e8';
      dot.style.border = '2px solid #ffffff';
      dot.style.borderRadius = '50%';
      dot.style.boxShadow = '0 0 2px rgba(0,0,0,0.5)';

      const marker = new AdvancedMarkerElement({
        map: nativeMap,
        position: data.position,
        title: data.title,
        content: dot,
      });


      marker.addListener('click', () => {
        const infoWindow = new google.maps.InfoWindow({
          content: `<h3 style="color: #333;">${data.title}</h3>`
        });
        infoWindow.open(nativeMap, marker);
      });
    });
  }

  loadGraph(): void {
    this.loading = true;
    this.graphService.getGraph().subscribe({
      next: (graph: MyGraph) => {
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

  private displayGraphOnMap(graph: MyGraph): void {
    const mapInstance = this.map().googleMap;
    if (!mapInstance) {
      console.error('Map instance not available');
      return;
    }

    // Clear existing graph visualization
    this.clearGraph();

    // Create a lookup object for quick node access
    const nodeMap: Record<number, GraphNode> = {};
    graph.nodes.forEach(node => nodeMap[node.id] = node);

    // Display nodes as markers
    this.displayGraphNodes(mapInstance, graph.nodes);

    // Display edges as polylines
    this.displayGraphEdges(mapInstance, graph.edges, nodeMap);

    // Adjust map bounds to fit the graph
    this.fitGraphBounds(mapInstance, graph.nodes);
  }

  private async displayGraphNodes(nativeMap: google.maps.Map, nodes: GraphNode[]): Promise<void> {
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

      marker.addListener('click', () => {
        const infoWindow = new google.maps.InfoWindow({
          content: `<div style="color: #333;">
                      <h4>Node ${node.id}</h4>
                      <p>Lat: ${node.lat.toFixed(5)}</p>
                      <p>Lon: ${node.lon.toFixed(5)}</p>
                    </div>`
        });
        infoWindow.open(nativeMap, marker);
      });

      this.graphMarkers.push(marker);
    });
  }

  private displayGraphEdges(
    nativeMap: google.maps.Map,
    edges: GraphEdge[],
    nodeMap: Record<number, GraphNode>
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
                      <p>From Node: ${edge.sourceId}</p>
                      <p>To Node: ${edge.targetId}</p>
                      <p>Weight: ${edge.weight.toFixed(2)}m</p>
                    </div>`,
          position: {lat: sourceNode.lat, lng: sourceNode.lon}
        });
        infoWindow.open(nativeMap);
      });

      this.graphPolylines.push(polyline);
    });
  }

  private fitGraphBounds(nativeMap: google.maps.Map, nodes: GraphNode[]): void {
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
    // Remove all graph markers
    this.graphMarkers.forEach(marker => {
      marker.map = null;
    });
    this.graphMarkers = [];

    // Remove all graph polylines
    this.graphPolylines.forEach(polyline => {
      polyline.setMap(null);
    });
    this.graphPolylines = [];
  }

}
