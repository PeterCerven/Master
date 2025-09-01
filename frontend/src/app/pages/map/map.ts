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
    center: {lat: 40, lng: -20},
    zoom: 4
  };

}
