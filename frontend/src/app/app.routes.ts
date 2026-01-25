import { Routes } from '@angular/router';
import { Map } from '@pages/map/map';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'map'
  },
  {
    path: 'map',
    component: Map,
  }
];
