import { Routes } from '@angular/router';
import { Map } from '@pages/map/map';
import { Settings } from '@pages/settings/settings';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'map'
  },
  {
    path: 'map',
    component: Map,
  },
  {
    path: 'settings',
    component: Settings,
  }
];
