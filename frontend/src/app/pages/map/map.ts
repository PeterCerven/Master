import {GoogleMapsModule, GoogleMap} from '@angular/google-maps';
import {Component} from '@angular/core';


@Component({
  selector: 'app-map',
  imports: [GoogleMapsModule, GoogleMap],
  templateUrl: './map.html',
  styleUrl: './map.scss'
})
export class Map {

  options: google.maps.MapOptions = {
    center: { lat: 40, lng: -20 },
    zoom: 4,
    disableDefaultUI: true,
    zoomControl: false,
    mapTypeControl: false,
    streetViewControl: false,
    fullscreenControl: false
  };

  markers = [
    { position: { lat: 40.7128, lng: -74.0060 }, title: 'New York' },
    { position: { lat: 34.0522, lng: -118.2437 }, title: 'Los Angeles' },
    { position: { lat: 41.8781, lng: -87.6298 }, title: 'Chicago' }
  ];

}
