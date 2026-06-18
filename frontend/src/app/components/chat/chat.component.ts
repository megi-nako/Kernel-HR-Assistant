import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MockApiService } from '../../services/mock-api.service';
import { SessionService, ChatMsg } from '../../services/session.service';
import { CitationComponent } from '../citation/citation.component';

// Loerti owns this component (feat/chat-loerti).
// Stub: full chat UI wired to MockApiService; flips to ApiService at CP2.
@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule, CitationComponent],
  template: `
    <div class="chat-wrapper">
      <div class="messages" #msgList>
        <div *ngFor="let msg of session.messages()"
             [class]="'msg msg-' + msg.role">
          <div class="bubble">
            <span *ngIf="msg.language" class="lang-badge">{{ msg.language | uppercase }}</span>
            <ng-container *ngIf="!msg.refused; else refused">
              <p>{{ msg.text }}</p>
              <app-citation *ngIf="msg.citations?.length" [citations]="msg.citations!"></app-citation>
            </ng-container>
            <ng-template #refused>
              <p class="refused-msg">{{ msg.reason }}</p>
            </ng-template>
          </div>
        </div>
        <div *ngIf="thinking" class="msg msg-assistant">
          <div class="bubble thinking">Thinking…</div>
        </div>
      </div>
      <div class="input-row">
        <input [(ngModel)]="question"
               (keyup.enter)="send()"
               placeholder="Ask an HR question…"
               [disabled]="thinking" />
        <button (click)="send()" [disabled]="!question.trim() || thinking">Send</button>
      </div>
    </div>
  `,
  styles: [`
    .chat-wrapper { display: flex; flex-direction: column; height: 100%; }
    .messages { flex: 1; overflow-y: auto; padding: 16px; }
    .msg { margin-bottom: 12px; }
    .msg-user .bubble { background: #e0f0ff; margin-left: 20%; border-radius: 8px; padding: 10px; }
    .msg-assistant .bubble { background: #f5f5f5; margin-right: 20%; border-radius: 8px; padding: 10px; }
    .lang-badge { font-size: 11px; background: #555; color: #fff; border-radius: 4px; padding: 2px 6px; margin-right: 6px; }
    .refused-msg { color: #b00; font-style: italic; }
    .thinking { color: #888; font-style: italic; }
    .input-row { display: flex; padding: 12px; gap: 8px; border-top: 1px solid #ddd; }
    .input-row input { flex: 1; padding: 8px; border: 1px solid #ccc; border-radius: 4px; }
    .input-row button { padding: 8px 16px; }
  `]
})
export class ChatComponent {
  question = '';
  thinking = false;

  constructor(public session: SessionService, private mockApi: MockApiService) {}

  send(): void {
    const q = this.question.trim();
    if (!q) return;
    this.session.addMessage({ role: 'user', text: q });
    this.question = '';
    this.thinking = true;

    this.mockApi.chat(q).subscribe({
      next: res => {
        const msg: ChatMsg = {
          role: 'assistant',
          text: res.text,
          language: res.language,
          citations: res.citations,
          refused: res.refused,
          reason: res.reason ?? undefined,
        };
        this.session.addMessage(msg);
        this.session.setLang(res.language);
        this.thinking = false;
      },
      error: () => {
        this.session.addMessage({ role: 'assistant', text: 'Error contacting the assistant. Please try again.' });
        this.thinking = false;
      }
    });
  }
}
