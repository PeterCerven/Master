import {Component, computed, signal, CUSTOM_ELEMENTS_SCHEMA} from '@angular/core';
import {MatSidenav, MatSidenavContainer, MatSidenavContent} from '@angular/material/sidenav';
import {RouterOutlet} from '@angular/router';
import {MatToolbar} from '@angular/material/toolbar';
import {MatIconButton} from '@angular/material/button';
import {MatIcon} from '@angular/material/icon';
import {CustomSidenav} from './components/custom-sidenav/custom-sidenav';

@Component({
  selector: 'app-root',
  imports: [
    MatSidenav,
    MatSidenavContent,
    RouterOutlet,
    MatToolbar,
    MatIconButton,
    MatSidenavContainer,
    MatIcon,
    CustomSidenav,
  ],
  schemas: [
    CUSTOM_ELEMENTS_SCHEMA
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly title = signal('frontend');

  collapsed = signal(false);
  sideNavWidth = computed(() => this.collapsed() ? '60px' : '250px');
}
