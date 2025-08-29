import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TrajectoryDataModel } from '../models/trajectory-data.model';
import {firstValueFrom} from 'rxjs';
import { environment } from '../../environments/environment.production'

@Injectable({ providedIn: 'root' })
export class ParseService {

  constructor(private http: HttpClient) {
  }

  parseFile(file: File): Promise<TrajectoryDataModel[]> {
    const fileExtension = file.name.split('.').pop()?.toLowerCase();
    switch (fileExtension) {
      case 'gpx':
        return this.parseGpxFile(file);
      default:
        return Promise.reject(new Error('Unsupported file type'));
    }
  }


  private parseGpxFile(file: File): Promise<TrajectoryDataModel[]> {
    const formData = new FormData();
    formData.append('file', file);

    return firstValueFrom(this.http.post<TrajectoryDataModel[]>(`${environment.apiUrl}/data/parse-gpx`, formData));
  }


}
