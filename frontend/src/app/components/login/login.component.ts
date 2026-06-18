import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MockApiService } from '../../services/mock-api.service';
import { SessionService } from '../../services/session.service';
import { FormsModule } from '@angular/forms';

// Urim owns this component (feat/shell-urim).
// Stub: profile picker from MockApiService, switches to real ApiService at CP2.
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="login-container">
      <h1>Kernel HR Assistant</h1>
      <h2>Sign In</h2>
      <div *ngIf="users.length > 0; else loading">
        <label for="user-select">Select profile:</label>
        <select id="user-select" [(ngModel)]="selectedUser">
          <option value="">-- choose --</option>
          <option *ngFor="let u of users" [value]="u">{{ u }}</option>
        </select>
        <button (click)="login()" [disabled]="!selectedUser || loading">
          {{ loading ? 'Signing in...' : 'Sign In' }}
        </button>
      </div>
      <ng-template #loading><p>Loading profiles…</p></ng-template>
      <p *ngIf="error" class="error">{{ error }}</p>
    </div>
  `,
  styles: [`
    .login-container { max-width: 400px; margin: 80px auto; text-align: center; }
    select, button { display: block; width: 100%; margin: 8px 0; padding: 8px; }
    .error { color: red; }
  `]
})
export class LoginComponent implements OnInit {
  users: string[] = [];
  selectedUser = '';
  loading = false;
  error = '';

  constructor(
    private mockApi: MockApiService,
    private session: SessionService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.mockApi.listUsers().subscribe(u => this.users = u);
  }

  login(): void {
    if (!this.selectedUser) return;
    this.loading = true;
    this.error = '';
    this.mockApi.login(this.selectedUser).subscribe({
      next: user => {
        this.session.setUser(user);
        this.router.navigate(['/chat']);
      },
      error: err => {
        this.error = err.message ?? 'Login failed';
        this.loading = false;
      }
    });
  }
}
