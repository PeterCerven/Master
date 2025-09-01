import { Routes } from '@angular/router';
import { Map } from './pages/map/map';
import { Settings } from './pages/settings/settings';
import { Tables } from './pages/tables/tables';

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
    path: 'tables',
    component: Tables,
  },
  {
    path: 'settings',
    component: Settings,
  }
];
