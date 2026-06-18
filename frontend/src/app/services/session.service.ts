import { Injectable, signal } from '@angular/core';

// Contract E — frozen at P0. office is DISPLAY-ONLY; never sent on /api/chat.
export interface User {
  upn: string;
  displayName: string;
  office: string;
}

export interface ChatMsg {
  role: 'user' | 'assistant';
  text: string;
  language?: string;
  citations?: Citation[];
  refused?: boolean;
  reason?: string;
}

export interface Citation {
  sourceName: string;
  lastModified: string;
  url: string | null;
  page: number | null;
}

@Injectable({ providedIn: 'root' })
export class SessionService {
  readonly user = signal<User | null>(null);
  readonly office = signal<string | null>(null); // display-only
  readonly messages = signal<ChatMsg[]>([]);
  readonly lang = signal<string>('en');

  setUser(u: User): void {
    this.user.set(u);
    this.office.set(u.office);
  }

  clearUser(): void {
    this.user.set(null);
    this.office.set(null);
    this.messages.set([]);
  }

  addMessage(msg: ChatMsg): void {
    this.messages.update(msgs => [...msgs, msg]);
  }

  setLang(lang: string): void {
    this.lang.set(lang);
  }
}
