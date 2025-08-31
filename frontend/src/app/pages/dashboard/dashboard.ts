import {Component, ElementRef, inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {forkJoin, Subject, takeUntil} from 'rxjs';

import {MapLoaderService} from '../../services/map-loader.service';

@Component({
  selector: 'app-dashboard',
  imports: [],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class Dashboard implements OnInit, OnDestroy {
  @ViewChild('mapContainer', { static: true }) mapContainer!: ElementRef;

  private mapLoader = inject(MapLoaderService);
  private destroy$ = new Subject<void>();

  ngOnInit() {
    this.initMap();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initMap(): void {
    // Load both Maps and Marker libraries in parallel
    forkJoin({
      maps: this.mapLoader.loadMapsLibrary(),
      marker: this.mapLoader.loadMarkerLibrary()
    }).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: ({ maps, marker }) => {
        this.createMap(maps, marker);
      },
      error: (error) => {
        console.error('Error loading Google Maps libraries:', error);
      }
    });
  }

  private createMap(mapsLibrary: any, markerLibrary: any): void {
    const { Map } = mapsLibrary;
    const { AdvancedMarkerElement } = markerLibrary;

    const map = new Map(this.mapContainer.nativeElement, {
      center: { lat: 40.12150192260742, lng: -100.45039367675781 },
      zoom: 4,
      mapId: "DEMO_MAP_ID"
    });

    // Add marker
    const marker = new AdvancedMarkerElement({
      map: map,
      position: { lat: 40.12150192260742, lng: -100.45039367675781 },
      title: "My location"
    });

    console.log('Map initialized successfully');
  }
}
