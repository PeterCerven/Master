import {GoogleMapsModule, GoogleMap} from '@angular/google-maps';
import {AfterViewInit, Component, viewChild} from '@angular/core';
import {environment} from '@env/environment.production';


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

}
