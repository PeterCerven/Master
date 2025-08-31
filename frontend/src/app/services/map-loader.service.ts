import { Injectable } from '@angular/core';
import { Observable, from, of } from 'rxjs';
import { shareReplay, switchMap } from 'rxjs/operators';
import { environment } from '../../environments/environment.production';

declare global {
  interface Window {
    google: any;
  }

  const google: any;
}

@Injectable({
  providedIn: 'root'
})
export class MapLoaderService {
  private scriptLoading$?: Observable<void>;

  public loadScript(): Observable<void> {
    if (!this.scriptLoading$) {
      this.scriptLoading$ = this.loadGoogleMapsScript().pipe(
        shareReplay(1)
      );
    }
    return this.scriptLoading$;
  }

  public loadLibrary(library: string): Observable<any> {
    return this.loadScript().pipe(
      switchMap(() => from(google.maps.importLibrary(library)))
    );
  }

  public loadMapsLibrary(): Observable<any> {
    return this.loadLibrary("maps");
  }

  public loadMarkerLibrary(): Observable<any> {
    return this.loadLibrary("marker");
  }

  private loadGoogleMapsScript(): Observable<void> {
    return new Observable(observer => {
      // Check if already loaded
      if (window.google?.maps?.importLibrary) {
        observer.next();
        observer.complete();
        return;
      }

      const script = document.createElement('script');
      script.src = `https://maps.googleapis.com/maps/api/js?key=${environment.googleApiUrl}&v=weekly&loading=async`;
      script.async = true;
      script.defer = true;

      script.onload = () => {
        console.log('Google Maps script loaded successfully');
        observer.next();
        observer.complete();
      };

      script.onerror = (error) => {
        console.error('Failed to load Google Maps script', error);
        observer.error(error);
      };

      document.head.appendChild(script);
    });
  }
}
