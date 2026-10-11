# Future TV pairing proposal

No pairing routes exist in the audited backend. Current TV login uses the actual username/password API. This proposal is not implemented and is not an API compatibility promise.

A future TV device generates a high-entropy secret and asks the server for a short-lived pending request. The display code is only a lookup handle, never the polling credential. An already authenticated device reviews the exact server, account, requested device name and expiry, then explicitly approves a single-use request. The server issues a new revocable device token for that account to the TV's secret-authenticated poll, never to an unauthenticated observer of the code.

Requirements: five-minute maximum expiry, one-time consumption, per-address/per-account throttling, bounded polling with server-directed intervals, cryptographic randomness, no password/token in URL or log, cancellation, generic lookup failures, a small bounded pending store, approval authorization checks, and tests for races/replay/cross-account approval. Do not add universal owner-token pairing or auto-approval.

Design endpoints and schemas only in a separately reviewed Go spec/implementation PR, preserving current local login and deployment.
