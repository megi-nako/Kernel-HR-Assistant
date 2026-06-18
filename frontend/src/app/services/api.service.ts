import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface LoginResponse {
  upn: string;
  displayName: string;
  office: string;
}

export interface ChatResponse {
  text: string;
  language: string;
  citations: Citation[];
  refused: boolean;
  reason: string | null;
}

export interface Citation {
  sourceName: string;
  lastModified: string;
  url: string | null;
  page: number | null;
}

export interface StatusResponse {
  office: string;
  docCount: number;
  lastIndexed: string | null;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private base = environment.apiBase;

  constructor(private http: HttpClient) {}

  login(username: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.base}/api/login`, { username }, { withCredentials: true });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.base}/api/logout`, {}, { withCredentials: true });
  }

  chat(question: string): Observable<ChatResponse> {
    // office is NOT sent — the backend reads it from the server session.
    return this.http.post<ChatResponse>(`${this.base}/api/chat`, { question }, { withCredentials: true });
  }

  status(): Observable<StatusResponse> {
    return this.http.get<StatusResponse>(`${this.base}/api/status`, { withCredentials: true });
  }

  listUsers(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/api/users`, { withCredentials: true });
  }
}
