import {AfterViewInit, Component, inject, OnDestroy, ViewChild} from '@angular/core';
import {
  MatCell,
  MatCellDef,
  MatColumnDef,
  MatHeaderCell,
  MatHeaderCellDef,
  MatHeaderRow,
  MatHeaderRowDef,
  MatRow,
  MatRowDef,
  MatTable,
  MatTableDataSource,
} from '@angular/material/table';
import {MatPaginator} from '@angular/material/paginator';
import {MatSort, MatSortHeader, MatSortModule} from '@angular/material/sort';
import {MatFormField, MatInput, MatLabel} from '@angular/material/input';
import {MatFabButton} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {TrajectoryDataModel} from '../../models/trajectory-data.model';
import {DataService} from '../../services/data.service';
import {Subject, takeUntil} from 'rxjs';

@Component({
  selector: 'app-tables',
  imports: [
    MatTable, MatPaginator, MatCell, MatHeaderCell, MatHeaderRow, MatRow, MatCellDef,
    MatHeaderCellDef, MatHeaderRowDef, MatRowDef, MatColumnDef, MatSortHeader, MatSortModule,
    MatFormField, MatLabel, MatInput, MatLabel, MatFormField, MatFabButton, MatIconModule,
  ],
  templateUrl: './tables.html',
  styleUrl: './tables.scss',
})
export class Tables implements AfterViewInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private dataService = inject(DataService);

  displayedColumns: string[] = [
    'latitude',
    'longitude',
    'timestamp',
  ];
  dataSource = new MatTableDataSource<TrajectoryDataModel>();

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  applyFilter(event: Event) {
    const filterValue = (event.target as HTMLInputElement).value;
    this.dataSource.filter = filterValue.trim().toLowerCase();
  }

  parseData(event: Event) {
    this.dataService.parseFile(event)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
          next: (data) => {
            this.dataSource.data = data;
            if (this.dataSource.paginator) {
              this.dataSource.paginator.firstPage();
            }
            console.log('GPX file parsed successfully.');
          },
          error: (error) => {
            console.error('Error while parsing the GPX file:', error);
          }
        }
      )
  }


  saveData() {
    this.dataService.saveData(this.dataSource.data)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          console.log('Data saved successfully:', data);
        },
        error: (error) => {
          console.error('Error saving data:', error);
        }
      });
  }

  showData() {
    this.dataService.getData()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          this.dataSource.data = data;
          if (this.dataSource.paginator) {
            this.dataSource.paginator.firstPage();
          }
          console.log('Data fetched successfully:', data);
        },
        error: (error) => {
          console.error('Error fetching data:', error);
        }
      })
  }

  onRowClicked(row: TrajectoryDataModel) {
    console.log('Row clicked: ', row);
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
