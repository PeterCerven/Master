import { Routes } from '@angular/router';
import { Map } from '@pages/map/map';
import { Login } from '@pages/login/login';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'map'
  },
  {
    path: 'login',
    component: Login,
  },
  {
    path: 'map',
    component: Map,
    canActivate: [authGuard],
  }
];
