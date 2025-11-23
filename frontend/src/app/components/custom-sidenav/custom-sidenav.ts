import {Component, input, signal} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatListModule} from '@angular/material/list';
import {RouterModule} from '@angular/router';
import {TranslocoDirective} from '@jsverse/transloco';


export type MenuItem = {
  icon: string;
  label: string;
  route?: string;
}

@Component({
  selector: 'custom-sidenav',
  imports: [
    MatListModule, MatIconModule, RouterModule, TranslocoDirective
  ],
  templateUrl: './custom-sidenav.html',
  styleUrl: './custom-sidenav.scss'
})
export class CustomSidenav {

  collapsed = input<boolean>(false);

  menuItems = signal<MenuItem[]>([
    {
      icon: 'map',
      label: 'map',
      route: 'map'
    },
    {
      icon: 'settings',
      label: 'settings',
      route: 'settings'
    },
  ]);
}
