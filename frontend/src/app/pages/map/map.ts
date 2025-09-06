import {GoogleMapsModule, GoogleMap} from '@angular/google-maps';
import {AfterViewInit, Component, viewChild} from '@angular/core';
import {environment} from '../../../environments/environment.production';


@Component({
  selector: 'app-map',
  imports: [GoogleMapsModule, GoogleMap],
  templateUrl: './map.html',
  styleUrl: './map.scss'
})
export class Map implements AfterViewInit {
  protected readonly google = google;
  map = viewChild.required<GoogleMap>('googleMap');

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
    center: {lat: 40, lng: -20},
    zoom: 4,
    disableDefaultUI: true,
    zoomControl: false,
    mapTypeControl: false,
    streetViewControl: false,
    fullscreenControl: false,
    mapId: environment.googleMapId,
  };

  markerData = [
    {position: {lat: 40.7128, lng: -74.0060}, title: 'New York City'},
    {position: {lat: 34.0522, lng: -118.2437}, title: 'Los Angeles'},
    {position: {lat: 41.8781, lng: -87.6298}, title: 'Chicago'},
    {position: {lat: 29.7604, lng: -95.3698}, title: 'Houston'}
  ];


  addAdvancedMarkers(nativeMap: google.maps.Map): void {
    const {AdvancedMarkerElement} = google.maps.marker;

    this.markerData.forEach(data => {

      const dot = document.createElement('div');

      dot.style.width = '14px';
      dot.style.height = '14px';
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

}
