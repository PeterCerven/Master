import {GoogleMapsModule, GoogleMap} from '@angular/google-maps';
import {Component} from '@angular/core';


@Component({
  selector: 'app-dashboard',
  imports: [GoogleMapsModule, GoogleMap],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class Dashboard {

  options: google.maps.MapOptions = {
    center: {lat: 40, lng: -20},
    zoom: 4
  };

}
