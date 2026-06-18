import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Citation } from '../../services/session.service';

// Loerti owns this component (feat/chat-loerti).
@Component({
  selector: 'app-citation',
  standalone: true,
  imports: [CommonModule],
  template: `
    <details class="citations">
      <summary>Sources ({{ citations.length }})</summary>
      <ul>
        <li *ngFor="let c of citations">
          <strong>{{ c.sourceName }}</strong>
          <span *ngIf="c.page"> — {{ isSlide(c.sourceName) ? 'Slide' : 'Page' }} {{ c.page }}</span>
          <span *ngIf="c.lastModified"> — {{ c.lastModified }}</span>
          <a *ngIf="c.url" [href]="c.url" target="_blank" rel="noopener"> [open]</a>
        </li>
      </ul>
    </details>
  `,
  styles: [`
    .citations { margin-top: 8px; font-size: 13px; color: #555; }
    ul { margin: 4px 0; padding-left: 16px; }
    li { margin: 2px 0; }
  `]
})
export class CitationComponent {
  @Input() citations: Citation[] = [];

  isSlide(name: string): boolean {
    return name.toLowerCase().endsWith('.pptx') || name.toLowerCase().endsWith('.ppt');
  }
}
