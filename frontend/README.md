# Kernel HR Assistant — Frontend

React + Vite single-page app for the Kernel HR Assistant, built on the
**Harmony design system** (tokens in `src/index.css`, primitives in
`src/components/ds/`).

## Develop

```bash
npm install
npm run dev      # http://localhost:4200
```

The dev server proxies `/api/*` to the Spring Boot backend on
`http://localhost:8080` (see `vite.config.js`), so no CORS setup is needed.

## Build

```bash
npm run build    # outputs to dist/
npm run preview  # serve the production build
```

## Backend wiring

All components talk to the backend through the unified facade in
`src/services/index.js`. Which implementation it uses is controlled by the
`VITE_USE_MOCK` environment variable (copy `.env.example` to `.env`):

| `VITE_USE_MOCK` | Behaviour                                                                 |
|-----------------|---------------------------------------------------------------------------|
| _unset_ / `auto`| Use the real backend; **fall back to the in-memory mock if it's unreachable** (default). |
| `false`         | Always use the real backend; surface errors as-is.                        |
| `true`          | Always use the in-memory mock (`src/services/mockApi.js`) — no backend needed. |

The REST contract (`/api/login`, `/api/chat`, `/api/status`, `/api/users`,
`/api/logout`) matches the backend's frozen Contract D DTOs.

## Structure

- `src/App.jsx` — routes (`/login`, `/chat`) + auth guard.
- `src/components/` — `LoginScreen`, `Sidebar`, `ChatLayout`, `ChatView`.
- `src/components/ds/` — Harmony primitives (`Button`, `IconButton`, `Avatar`,
  `Badge`, `ChatBubble`, `TypingIndicator`).
- `src/contexts/SessionContext.jsx` — in-memory auth session.
- `src/services/` — `api.js` (real), `mockApi.js` (mock), `index.js` (facade).
