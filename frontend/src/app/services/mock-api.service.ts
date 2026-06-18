import { Injectable } from '@angular/core';
import { Observable, of, delay } from 'rxjs';
import { LoginResponse, ChatResponse, StatusResponse } from './api.service';

// Canned responses matching the real API shapes.
// Loerti + Urim build against this until the backend is live (CP2).
@Injectable({ providedIn: 'root' })
export class MockApiService {

  private loggedInUser: LoginResponse | null = null;

  login(username: string): Observable<LoginResponse> {
    const officeMap: Record<string, string> = {
      urim_albania: 'albania',
      agathi_albania: 'albania',
      srb_user: 'serbia',
    };
    const office = officeMap[username] ?? 'albania';
    this.loggedInUser = { upn: `${username}@eng.it`, displayName: username, office };
    return of(this.loggedInUser).pipe(delay(300));
  }

  logout(): Observable<void> {
    this.loggedInUser = null;
    return of(undefined).pipe(delay(100));
  }

  chat(question: string): Observable<ChatResponse> {
    const office = this.loggedInUser?.office ?? 'unknown';
    const response: ChatResponse = {
      text: `[MOCK] This is a canned answer for office "${office}". Your question was: "${question}". ` +
            `In production, Claude Opus 4.8 will answer from the ${office} HR documents.`,
      language: 'en',
      citations: [
        { sourceName: 'Office Attendance Rules.pdf', lastModified: '2026-02-11', url: null, page: 3 },
        { sourceName: 'Internal Policies ESL.pdf', lastModified: '2025-12-01', url: null, page: 7 },
      ],
      refused: false,
      reason: null,
    };
    return of(response).pipe(delay(800));
  }

  chatRefused(): Observable<ChatResponse> {
    const office = this.loggedInUser?.office ?? 'unknown';
    return of({
      text: '',
      language: 'en',
      citations: [],
      refused: true,
      reason: `I can only help with HR questions for the ${office} office, based on our HR documents.`,
    }).pipe(delay(400));
  }

  status(): Observable<StatusResponse> {
    const office = this.loggedInUser?.office ?? 'unknown';
    return of({ office, docCount: 24, lastIndexed: '2026-06-18T10:00:00Z' }).pipe(delay(200));
  }

  listUsers(): Observable<string[]> {
    return of(['urim_albania', 'agathi_albania', 'srb_user']).pipe(delay(100));
  }
}
