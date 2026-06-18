# Kernel HR Assistant — Future Implementation Roadmap

This document describes three planned feature extensions beyond the current MVP. Each section covers the problem being solved, the full technical approach, the architecture changes required across frontend and backend, and the open questions that must be answered before implementation begins.

---

## 1. Voice Commands (Frontend)

### Problem

HR questions are often asked in conversational, spoken language — especially by employees who are on the move, have limited keyboard access, or feel more comfortable speaking in their native language (Serbian or Albanian) than typing. The current text-only composer is the sole input method.

### What this enables

- Hands-free HR lookups (e.g., "How many sick days do I have left?" while commuting)
- Lower friction for employees less comfortable typing in EN/SR/SQ
- Natural language spoken questions that can be richer and more specific than typed queries
- Optional voice readout of answers for accessibility

---

### Technical Approach

#### Input: Speech-to-Text

Use the **Web Speech API** (`SpeechRecognition` / `webkitSpeechRecognition`), available natively in Chromium-based browsers and Safari. No external API or server round-trip is needed for transcription.

```js
// frontend/src/hooks/useSpeechInput.js
export function useSpeechInput({ onResult, onError, language = 'en-US' }) {
  const recognitionRef = useRef(null)
  const [listening, setListening] = useState(false)

  const start = useCallback(() => {
    const SR = window.SpeechRecognition ?? window.webkitSpeechRecognition
    if (!SR) { onError('Voice input is not supported in this browser.'); return }

    const rec = new SR()
    rec.lang = language          // 'en-US' | 'sr-RS' | 'sq-AL'
    rec.interimResults = true    // show partial transcript while speaking
    rec.maxAlternatives = 1

    rec.onresult = (e) => {
      const transcript = Array.from(e.results)
        .map(r => r[0].transcript)
        .join('')
      onResult(transcript, e.results[e.results.length - 1].isFinal)
    }
    rec.onerror = (e) => onError(e.error)
    rec.onend   = () => setListening(false)

    rec.start()
    recognitionRef.current = rec
    setListening(true)
  }, [language, onResult, onError])

  const stop = useCallback(() => {
    recognitionRef.current?.stop()
    setListening(false)
  }, [])

  return { start, stop, listening }
}
```

**Language mapping** (Web Speech API uses BCP-47 tags, not the app's en/sr/sq codes):

| App language | BCP-47 tag | Notes |
|---|---|---|
| `en` | `en-US` | Default |
| `sr` | `sr-RS` | Serbian (Latin and Cyrillic both work) |
| `sq` | `sq-AL` | Albanian |

The detected language from `SessionContext` (or the last response's `language` field) is used as the initial `lang`. If the user's microphone input is in a different language, the backend's `LanguageDetector` will correct the reply language automatically.

#### Output: Text-to-Speech (Optional)

Use the **Web Speech Synthesis API** to read out the assistant's response. No external API needed.

```js
export function useSpeechOutput() {
  const speak = useCallback((text, language = 'en') => {
    if (!window.speechSynthesis) return
    window.speechSynthesis.cancel()  // stop any ongoing speech
    const utter = new SpeechSynthesisUtterance(text)
    utter.lang = { en: 'en-US', sr: 'sr-RS', sq: 'sq-AL' }[language] ?? 'en-US'
    utter.rate = 0.95
    window.speechSynthesis.speak(utter)
  }, [])

  const stop = useCallback(() => window.speechSynthesis?.cancel(), [])

  return { speak, stop }
}
```

Text-to-speech should be **opt-in** (a toggle in the UI, defaulting to off) since auto-reading every response would be disruptive in an office setting.

---

### UI Changes

#### Mic button in Composer

Replace the current decorative `<Mic>` icon button with an active recording button:

```
States:
  idle      → mic icon, ghost variant, no color
  listening → animated red pulse, "Listening…" label, tap to stop
  processing → spinner (transcription finished, waiting for send)
```

```jsx
// In ChatView.jsx Composer component:
const { start, stop, listening } = useSpeechInput({
  language: detectedLang,
  onResult: (transcript, isFinal) => {
    setValue(transcript)                       // show partial in textarea
    if (isFinal) { stop(); send(transcript) } // auto-send on sentence end
  },
  onError: (err) => setVoiceError(err),
})
```

#### Voice toggle in header

Add a speaker icon next to the Beta badge in the chat header. When active, AI responses are read aloud using `useSpeechOutput`. State persists in `localStorage`.

#### Browser compatibility notice

If `window.SpeechRecognition` is undefined, show a non-blocking notice:

```jsx
{!('SpeechRecognition' in window || 'webkitSpeechRecognition' in window) && (
  <p style={{ color: 'var(--text-muted)', fontSize: 12 }}>
    Voice input requires Chrome or Edge.
  </p>
)}
```

---

### Backend Changes

No backend changes are required for the basic voice input path. The transcribed text is sent to `POST /api/chat` as a normal question string — the pipeline is identical to typed input.

**Possible enhancement:** If voice quality is poor and the SpeechRecognition API produces noisy transcripts (e.g., "šta je R A S" instead of "šta je RAS"), a pre-processing step before the API call could normalize common spoken artifacts. This would be a frontend utility function, not a backend change.

---

### Open Questions

1. **Auto-send vs. manual confirm?** Auto-sending on `isFinal` is faster but can misfire. A "tap to confirm" pattern (show transcript → user taps send) is safer for HR questions where accuracy matters.
2. **Push-to-talk vs. continuous?** Push-to-talk (hold mic button) reduces false activations in noisy environments.
3. **Serbian Cyrillic vs. Latin?** `sr-RS` may produce Cyrillic output from some engines. The backend handles both, but the text box should display correctly with a Unicode-safe font.
4. **Safari on iOS?** `SpeechRecognition` requires user gesture and only works in Safari 14.5+. Test on iPhone as that's a common device for mobile HR lookups.

---

## 2. Integration with the ENG Engage Platform

### Problem

The Kernel HR Assistant currently operates as a standalone chatbot. The **Engage** platform is Engineering's employee engagement and HR management hub — where employees manage leave requests, view payslips, access their personal HR profile, and read company announcements. Connecting the assistant to Engage creates a seamless experience: employees can ask a question and act on the answer in the same workflow, without switching systems.

---

### Integration Points

There are three distinct integration layers, each with different technical complexity.

---

#### 2A. Single Sign-On via Engage (Identity)

**What:** Replace the current mock profile picker (or the planned Entra SSO) with an OAuth 2.0 / SAML 2.0 flow that authenticates against Engage's identity provider. The Engage session becomes the source of truth for the user's identity and office.

**Why:** Employees are already logged into Engage during their workday. A second login prompt for the HR assistant creates friction and a separate session to manage.

**How:**

If Engage uses **Microsoft Entra ID** (most likely for an Engineering company), the existing `AUTH_MODE=entra` path in `IdentityService` already handles this:

```yaml
# application.yml
kernel:
  auth:
    mode: entra
  graph:
    tenant-id: ${GRAPH_TENANT_ID}
    client-id: ${GRAPH_CLIENT_ID}
    client-secret: ${GRAPH_CLIENT_SECRET}
```

`IdentityService.loadUser()` calls `GET https://graph.microsoft.com/v1.0/me/profile` using the user's token, which already contains the company name (Albania/Serbia) for office resolution.

If Engage uses a **proprietary identity provider**, the implementation is:

```
User clicks "Open HR Assistant" in Engage
  → Engage issues a short-lived signed JWT (HMAC-SHA256, shared secret)
      payload: { upn, displayName, office, exp (5 min) }
  → Frontend receives JWT as a URL param: /chat?token=eyJ...
  → POST /api/login {"token": "eyJ..."}
  → Backend verifies signature + expiry → creates session
  → No separate login screen needed
```

```java
// auth/EngageTokenValidator.java
@Component
public class EngageTokenValidator {
    @Value("${kernel.engage.shared-secret}")
    private String sharedSecret;

    public IdentityService.User validate(String jwt) {
        // 1. Decode header + payload (Base64)
        // 2. Verify HMAC-SHA256(header.payload, sharedSecret) == signature
        // 3. Check exp claim > now
        // 4. Return User{upn, displayName, office, internal=true}
        // 5. Any failure → throw 401
    }
}
```

**New config:**
```yaml
kernel:
  engage:
    shared-secret: ${ENGAGE_SHARED_SECRET}
    sso-enabled: ${ENGAGE_SSO:false}
```

---

#### 2B. Engage as Document Source (HR Policy Sync)

**What:** In addition to SharePoint (Albania) and the Serbia ZIP, ingest HR policies published and maintained in Engage. When an HR administrator updates a policy in Engage, the vector store is automatically updated without manual re-ingestion.

**Why:** Engage is the system of record for HR policies at Engineering. Having to manually export documents and re-run `--ingest` when policies change is error-prone and creates a lag between the official policy and what the assistant knows.

**How:**

Engage exposes a REST API for document management. A new `EngageSource` implements the same `SourceDoc` interface as `ZipSource` and `SharePointClient`:

```java
// ingestion/EngageSource.java
@Component
public class EngageSource {

    // GET /api/v1/hr-documents?office={office}&modified_since={lastRun}
    // Returns: [{id, name, url, lastModified, fileType, downloadUrl}]

    public List<SourceDoc> loadAll(String office) {
        String token = authenticateEngage();  // client-credentials or API key
        List<EngageDocMeta> docs = listDocuments(token, office, lastRunTimestamp);
        return docs.stream().map(meta -> {
            byte[] bytes = downloadDocument(meta.downloadUrl(), token);
            return new SourceDoc(bytes, meta.name(), meta.url(),
                                 meta.lastModified(), office, meta.fileType());
        }).toList();
    }
}
```

**Webhook for real-time updates:**

Instead of polling on a schedule, Engage pushes a webhook notification when a document is updated:

```
POST /api/engage/webhook
  Authorization: Bearer {ENGAGE_WEBHOOK_SECRET}
  Body: {
    "event": "document.updated",
    "office": "serbia",
    "documentId": "doc-123",
    "documentName": "Updated Leave Policy 2026.pdf",
    "downloadUrl": "https://engage.eng.it/documents/doc-123/content"
  }
```

```java
// web/EngageWebhookController.java
@PostMapping("/api/engage/webhook")
public ResponseEntity<Void> onDocumentEvent(
        @RequestHeader("Authorization") String auth,
        @RequestBody EngageWebhookEvent event) {

    if (!webhookAuth.verify(auth)) return ResponseEntity.status(401).build();

    // Async: download + re-index only the changed document
    taskExecutor.execute(() -> {
        SourceDoc doc = engageSource.downloadOne(event.documentId(), event.office());
        indexer.index(List.of(doc));
        log.info("Re-indexed '{}' for office={} via Engage webhook", event.documentName(), event.office());
    });
    return ResponseEntity.accepted().build();
}
```

This ensures the vector store is always in sync with Engage within minutes of a policy update, not days.

---

#### 2C. Embedded Widget in Engage (UX)

**What:** Embed the HR Assistant as a floating chat widget inside Engage, so employees never leave the platform to ask an HR question.

**Why:** Context switching between Engage and a separate browser tab breaks the workflow. An embedded widget means an employee can be looking at their leave balance and ask "wait, how many days can I carry over?" without leaving the page.

**How:**

Build a lightweight embeddable build of the chat interface:

```js
// vite.config.embed.js — separate build target
export default defineConfig({
  build: {
    lib: {
      entry: 'src/embed.jsx',
      name: 'KernelHRAssistant',
      fileName: 'kernel-hr-widget',
      formats: ['iife'],  // single self-contained JS file
    },
    rollupOptions: {
      external: [],  // bundle everything, including React
    }
  }
})
```

```jsx
// src/embed.jsx — minimal widget shell
function Widget() {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState([])

  return (
    <>
      {/* Floating button */}
      <button onClick={() => setOpen(o => !o)} style={fabStyle}>
        {open ? '✕' : '💬 HR Help'}
      </button>

      {/* Chat panel */}
      {open && (
        <div style={panelStyle}>
          <ChatView messages={messages} onSend={handleSend} embedded />
        </div>
      )}
    </>
  )
}

// Engage team adds one script tag to their HTML:
// <script src="https://hr-assistant.eng.it/widget/kernel-hr-widget.iife.js"></script>
// <script>KernelHRAssistant.mount({ container: '#engage-app', ssoToken: engage.currentUserToken })</script>
```

The widget receives the `ssoToken` from Engage (2A) so no separate login is needed.

---

### New Config for Engage Integration

```yaml
kernel:
  engage:
    base-url:       ${ENGAGE_BASE_URL:}         # https://engage.eng.it
    api-key:        ${ENGAGE_API_KEY:}
    shared-secret:  ${ENGAGE_SHARED_SECRET:}    # for JWT verification
    webhook-secret: ${ENGAGE_WEBHOOK_SECRET:}   # for inbound webhooks
    sso-enabled:    ${ENGAGE_SSO:false}
```

---

### Open Questions

1. **What identity provider does Engage use?** If it is Entra ID, the existing `AUTH_MODE=entra` path requires zero new code. If it is a custom IdP, the JWT approach described above is needed.
2. **Does Engage have a REST API for documents?** The webhook approach requires Engage to support outbound webhooks on document updates. If not, a polling job (`@Scheduled`) is the fallback.
3. **CORS for the embedded widget:** The widget makes cross-origin requests to `hr-assistant.eng.it`. `CorsConfig.java` must be updated to add the Engage origin to the allowed list.
4. **Who controls Engage document permissions?** HR administrators who can publish documents in Engage should also be the ones who control what is indexed. A permission mapping between Engage roles and the `office` field must be agreed.
5. **Data residency:** If Engage's Albania and Serbia instances are separate tenants, the document sync must target the correct tenant for each office.

---

## 3. File Submission

### Problem

The assistant can tell an employee *what* the leave policy is, but cannot currently help them *act on it*. Employees must leave the chatbot, navigate to a separate HR system, fill in a form, and attach a document. This creates friction and abandonment — especially for less tech-savvy employees.

File submission brings the action into the conversation: the assistant understands the request, presents the right form, guides the employee through filling it in, and submits it with any supporting document directly from the chat interface.

---

### Use Cases

| Request | File involved | Action |
|---|---|---|
| "I want to request annual leave" | None initially (form only) | Submit leave request form |
| "I need to submit a medical certificate for sick leave" | Scanned PDF/photo of certificate | Upload to HR system, linked to active sick leave |
| "I want to submit a training expense" | Expense receipt (PDF/image) | Attach to expense claim |
| "Here's my signed contract amendment" | PDF | Route to HR inbox for the correct office |

---

### Technical Approach

#### 3A. File Upload Endpoint

A new backend endpoint accepts multipart file uploads from the chat, validates the file, and stores it (SharePoint for Albania, configurable storage for Serbia):

```java
// web/FileSubmissionController.java
@PostMapping(value = "/api/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<SubmissionResponse> submit(
        @RequestPart("file") MultipartFile file,
        @RequestPart("metadata") SubmissionMetadata metadata,
        HttpSession session) {

    String office = (String) session.getAttribute("office");
    String upn    = (String) session.getAttribute("upn");
    if (office == null) return ResponseEntity.status(401).build();

    // 1. Validate file type (PDF, JPG, PNG only — no executable formats)
    validateFileType(file);

    // 2. Validate file size (max 10 MB)
    if (file.getSize() > 10 * 1024 * 1024) {
        return ResponseEntity.badRequest().body(new SubmissionResponse(false, "File exceeds 10 MB limit.", null));
    }

    // 3. Virus scan (ClamAV or vendor cloud scan — required before SharePoint upload)
    scanFile(file);

    // 4. Upload to office-specific storage
    String uploadUrl = storageService.upload(file, metadata, office, upn);

    // 5. Trigger HR workflow (optional: Engage webhook, email to HR inbox)
    workflowService.notify(metadata.type(), upn, office, uploadUrl);

    // 6. Audit log
    auditService.log(upn, office, "[FILE_SUBMIT] " + metadata.type(), true, uploadUrl);

    return ResponseEntity.ok(new SubmissionResponse(true, "Submitted successfully.", uploadUrl));
}
```

```java
public record SubmissionMetadata(
    String type,         // "sick_leave_certificate" | "expense_receipt" | "leave_request"
    String description,  // employee-provided note
    String relatedDate   // ISO-8601 — date of leave/expense
) {}

public record SubmissionResponse(boolean success, String message, String referenceUrl) {}
```

---

#### 3B. Storage Backend

| Office | Storage | Mechanism |
|---|---|---|
| Albania | SharePoint `HR Submissions` drive | Microsoft Graph `PUT /drives/{id}/items/{path}:/content` |
| Serbia | Configurable: SharePoint, S3, or local filesystem | `StorageService` interface with office-specific implementations |

```java
// ingestion/StorageService.java (interface)
public interface StorageService {
    String upload(MultipartFile file, SubmissionMetadata meta, String office, String upn);
}

// AlbaniaSharePointStorageService: uploads to ALB SharePoint Submissions folder
// SerbiaLocalStorageService: writes to configurable local path + returns file:// URL
// SerbiaSharePointStorageService: uploads to SRB SharePoint (future)
```

Config:
```yaml
kernel:
  submission:
    serbia:
      storage: ${SRB_SUBMISSION_STORAGE:local}    # local | sharepoint | s3
      local-path: ${SRB_SUBMISSION_PATH:./data/submissions/}
    albania:
      storage: ${ALB_SUBMISSION_STORAGE:sharepoint}
      sharepoint-folder: ${ALB_SUBMISSION_FOLDER:HR Submissions}
    allowed-types: pdf,jpg,jpeg,png
    max-size-mb: 10
```

---

#### 3C. Conversation-Guided Submission

The HR Agent gains a new tool: `submitDocument`. The agent guides the employee through the submission without leaving the chat:

```json
{
  "name": "submitDocument",
  "description": "Submit an HR document or form on behalf of the authenticated employee. Use this when the user wants to submit a leave request, upload a medical certificate, attach an expense receipt, or send a signed document to HR. The office and upn are bound server-side. Ask the user for all required metadata before calling this tool.",
  "input_schema": {
    "type": "object",
    "properties": {
      "type": {
        "type": "string",
        "enum": ["sick_leave_certificate", "expense_receipt", "leave_request", "contract_amendment", "other"],
        "description": "The type of document being submitted."
      },
      "description": {
        "type": "string",
        "description": "A short description of the submission, provided by the employee."
      },
      "relatedDate": {
        "type": "string",
        "description": "The date this document relates to (leave date, expense date, etc.) in YYYY-MM-DD format."
      }
    },
    "required": ["type", "description"]
  }
}
```

**Conversation flow example:**

```
User:   "I need to submit my medical certificate for sick leave"
Claude: "I can help you submit your medical certificate. A few quick questions:
         1. What date range does the certificate cover? (start date – end date)
         2. Is there anything else I should note about this submission?"
User:   "January 8th to 10th. It's from City Hospital."
Claude: "Thank you. Please attach your certificate using the paperclip button below,
         then I'll submit it to HR for the Serbia office."
User:   [attaches certificate.pdf]
Claude: [calls submitDocument({type:"sick_leave_certificate", description:"City Hospital certificate Jan 8-10", relatedDate:"2026-01-08"})]
Claude: "Done! Your medical certificate has been submitted to HR. Reference: SRB-2026-0042.
         HR will process it within 1-2 business days.
         Based on the Leave Policy (Pravilnik o godišnjem odmoru, §8), sick days from January 8–10
         do not count against your annual leave balance."
```

---

#### 3D. Frontend: File Attachment in Composer

The currently decorative paperclip button becomes functional:

```jsx
// In Composer component
const [pendingFile, setPendingFile] = useState(null)
const fileRef = useRef(null)

const onFileSelect = (e) => {
  const file = e.target.files[0]
  if (!file) return
  // Validate type client-side before upload
  const allowed = ['application/pdf', 'image/jpeg', 'image/png']
  if (!allowed.includes(file.type)) {
    setFileError('Only PDF, JPG, and PNG files are supported.')
    return
  }
  setPendingFile(file)
}

const send = async () => {
  if (pendingFile) {
    // Upload file first, get a reference ID, then send the question
    const form = new FormData()
    form.append('file', pendingFile)
    form.append('metadata', JSON.stringify({ type: 'pending', description: value }))
    const result = await fetch('/api/submit', {
      method: 'POST', credentials: 'include', body: form
    }).then(r => r.json())
    onSend(value, result.referenceUrl)  // include file reference in the message
    setPendingFile(null)
  } else {
    onSend(value)
  }
}
```

**File attachment preview in composer:**
```
┌─────────────────────────────────────────────────────┐
│  📎 certificate.pdf (142 KB)  ✕                      │
│  ──────────────────────────────────────────────────  │
│  Ask about HR policies, leave, benefits, payroll…   │
│                                    [Mic]  [→ Send]  │
└─────────────────────────────────────────────────────┘
```

---

#### 3E. Security Considerations

File submission is a higher-risk feature than chat. These controls are required before production deployment:

| Risk | Control |
|---|---|
| Malicious file upload (malware) | Server-side virus scan before any storage write (ClamAV via `clamd` or cloud AV API) |
| Path traversal | Use `UUID.randomUUID()` as the stored filename; never use the client-provided filename as-is |
| Unauthorized submission | Session office check on every upload — an Albania user cannot submit to Serbia's HR folder |
| File type spoofing | Verify MIME type using Apache Tika's MIME detection, not the `Content-Type` header or file extension alone |
| Large file DoS | 10 MB server-side limit enforced by Spring's `spring.servlet.multipart.max-file-size` |
| Audit trail | Every upload logged to `audit.log` with upn, office, file hash (SHA-256), and storage URL |

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 11MB
```

---

### Open Questions

1. **Which HR workflow system receives the submitted files?** Email to an HR inbox? A SharePoint folder that HR monitors? A webhook into Engage? The backend `workflowService.notify()` is a stub until this is decided.
2. **Does submission require HR approval tracking?** A simple file drop is easy; a full approval workflow (submit → HR reviews → employee notified) requires a ticketing integration.
3. **What is the file retention policy?** HR documents may be subject to GDPR. The storage layer must support deletion and expiry.
4. **Can employees see the status of their submissions?** A `GET /api/submissions` endpoint listing the employee's prior submissions and their status would complement the upload capability.
5. **Virus scanning infrastructure:** ClamAV can be run as a Docker sidecar alongside the application. Cloud-based AV (e.g., VirusTotal Enterprise API) is an alternative with no infrastructure overhead but involves sending employee documents to a third party.

---

## Summary Table

| Feature | Frontend effort | Backend effort | External dependencies | Blocking decisions |
|---|---|---|---|---|
| Voice commands | Medium — Web Speech API hooks + mic button UI | None (transcribed text hits existing `/api/chat`) | Browser support (Chrome/Edge/Safari only) | Auto-send vs. confirm? Push-to-talk vs. continuous? |
| Engage SSO | Low (token param on URL) | Low if Entra; Medium if custom JWT | Engage IdP type, shared secret | What IdP does Engage use? |
| Engage document source | None | Medium — EngageSource + webhook endpoint | Engage REST API contract, webhook support | Does Engage expose a document API? |
| Engage embedded widget | Medium — separate widget build | None | Engage team must add script tag + pass SSO token | Agreed on embed API contract? |
| File upload UI | Medium — file picker, preview, upload fetch | High — storage, virus scan, workflow notify, audit | ClamAV or cloud AV; storage backend (SharePoint/S3) | HR workflow target, retention policy, approval flow |
