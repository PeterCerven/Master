import {Component, Input} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {TranslocoDirective} from '@jsverse/transloco';
import {MatCard} from '@angular/material/card';
import {MatIcon} from '@angular/material/icon';
import {MatDivider} from '@angular/material/divider';
import {PlacementResultInfo} from '@models/my-graph.model';

@Component({
  selector: 'app-placement-result-panel',
  imports: [TranslocoDirective, DecimalPipe, MatCard, MatIcon, MatDivider],
  templateUrl: './placement-result-panel.html',
  styleUrl: './placement-result-panel.scss',
})
export class PlacementResultPanel {
  @Input() info!: PlacementResultInfo;
}
