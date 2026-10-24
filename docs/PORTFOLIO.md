# BattleTrust Engine — portfolio and resume copy

**Project summary:** Designed and implemented a full-stack esports tournament platform using Java 25, Spring Boot 4.1.2, Next.js, React, TypeScript, and MySQL, with tournament-scoped escrow, squad invoicing, secure onboarding, and administrator-controlled settlement.

- Architected tournament-scoped escrow and wallet services with transactional settlement, configurable fee snapshots, organizer security stakes, and deterministic per-member prize allocation, preserving financial audit history across payments, refunds, and payouts.
- Implemented concurrency-safe squad payment and slot assignment using pessimistic database locks, post-lock state validation, unique constraints, and idempotent references; exercised concurrent payment retries and 20 independently funded tournament rooms in local integration scenarios.
- Engineered secure authentication with mandatory SMTP email verification, hashed single-use verification tokens, JWT access control, rotating HttpOnly refresh cookies, Google identity validation, and role/ownership enforcement across player, organizer, and administrator APIs.
- Built capability-based guest-to-profile wallet linking and private tournament SSE chat, protecting lobby credentials, member claims, and historical funds from unauthorized access; integrated server-authenticated Cloudinary uploads with decoded-image validation.
- Delivered responsive Next.js dashboards, searchable mobile tournament discovery, and an auditable dispute-to-settlement workflow; validated the implementation with 67 backend tests, 26 general integration checks, email/media smoke tests, and a successful production frontend build.

## Interview positioning

Describe the system as a locally validated implementation with production deployment controls documented. Explain that the 1Bill flow simulates receipts and does not execute bank transfers. The 20-room scenario demonstrates isolation and correctness under its test conditions, not a claim of production throughput, user adoption, or revenue.

Useful discussion topics include lock ordering and stale ORM state, replay prevention versus transport retries, cent rounding, financial policy snapshots, guest capability ownership, verification-token lifecycle, and the shared messaging required to scale process-local SSE.
