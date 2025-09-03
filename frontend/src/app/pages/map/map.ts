import {GoogleMapsModule, GoogleMap} from '@angular/google-maps';
import {Component} from '@angular/core';
import {environment} from '../../../environments/environment.production';


@Component({
  selector: 'app-map',
  imports: [GoogleMapsModule, GoogleMap],
  templateUrl: './map.html',
  styleUrl: './map.scss'
})
export class Map {
  protected readonly google = google;

  options: google.maps.MapOptions = {
    center: { lat: 40, lng: -20 },
    zoom: 4,
    disableDefaultUI: true,
    zoomControl: false,
    mapTypeControl: false,
    streetViewControl: false,
    fullscreenControl: false,
    mapId: environment.googleMapId,
  };

  marketOptions = {
    draggable: false,
    clickable: true,
    icon: {
      url: 'https://maps.gstatic.com/mapfiles/api-3/images/spotlight-poi2_hdpi.png'
    }
  }

  markers = [
    { position: { lat: 40.7128, lng: -74.0060 }},
    { position: { lat: 34.0522, lng: -118.2437 }},
    { position: { lat: 41.8781, lng: -87.6298 }}
  ];

}
