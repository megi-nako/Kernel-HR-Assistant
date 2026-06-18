import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SessionService } from '../../services/session.service';
import { MockApiService } from '../../services/mock-api.service';
import { StatusResponse } from '../../services/api.service';

// Urim owns this component (feat/shell-urim).
// Office badge is READ-ONLY — no selector (removes isolation loophole #1).
@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule],
  template: `
    <nav class="sidebar">
      <div class="logo">Kernel HR</div>
      <div *ngIf="session.user() as user" class="user-info">
        <div class="display-name">{{ user.displayName }}</div>
        <div class="office-badge office-{{ user.office }}">
          {{ user.office | uppercase }}
        </div>
      </div>
      <div *ngIf="status" class="status-panel">
        <div class="status-label">Index status</div>
        <div>{{ status.docCount }} documents</div>
        <div *ngIf="status.lastIndexed" class="last-indexed">
          Last indexed: {{ status.lastIndexed | date:'short' }}
        </div>
        <div *ngIf="!status.lastIndexed" class="last-indexed">Not yet indexed</div>
      </div>
    </nav>
  `,
  styles: [`
    .sidebar { width: 220px; background: #1a1a2e; color: #fff; padding: 16px; display: flex; flex-direction: column; gap: 16px; }
    .logo { font-size: 18px; font-weight: bold; color: #e0c56b; }
    .display-name { font-weight: 500; }
    .office-badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; margin-top: 4px; }
    .office-albania { background: #c0392b; }
    .office-serbia { background: #2980b9; }
    .status-panel { font-size: 12px; color: #aaa; border-top: 1px solid #444; padding-top: 12px; }
    .status-label { font-weight: bold; color: #ccc; margin-bottom: 4px; }
    .last-indexed { margin-top: 4px; }
  `]
})
export class SidebarComponent implements OnInit {
  status: StatusResponse | null = null;

  constructor(public session: SessionService, private mockApi: MockApiService) {}

  ngOnInit(): void {
    this.mockApi.status().subscribe(s => this.status = s);
  }
}
