import { Injectable } from '@angular/core';
import { TrajectoryDataModel } from '../models/trajectory-data.model';

@Injectable({ providedIn: 'root' })
export class ParseService {

  constructor() {
  }

  parseFile(file: File): Promise<TrajectoryDataModel[]> {
    const fileExtension = file.name.split('.').pop()?.toLowerCase();
    switch (fileExtension) {
      case 'xlsx':
      case 'xls':
        return this.parseExcelFile(file);
      case 'csv':
        return this.parseCsvFile(file);
      default:
        return Promise.reject(new Error('Unsupported file type'));
    }
  }

  parseExcelFile(file: File): Promise<TrajectoryDataModel[]> {
    return Promise.resolve([]);
  }

  private parseCsvFile(file: File) {
    return Promise.resolve([]);
  }


}
