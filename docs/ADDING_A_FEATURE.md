# Adding a feature in a later phase

Use this checklist to add a module without coupling it to existing domains or
editing the Phase 0 frontend shell/registries.

1. **Backend module:** implement the feature inside its owned
   `com.peergeek.<module>` package. Depend on `common` contracts rather than
   another domain's persistence internals. Keep transport controllers under the
   module and all application routes under `/api/v1`.
   Keep authorization decisions in services using `CurrentUserProvider`,
   `@PreAuthorize`, and `OwnershipGuard`; frontend visibility is never security.
   Record security-sensitive changes with `AuditService` and secret-free event
   details.
2. **Migration:** add an additive Flyway migration named
   `V<phase>_<seq>__description.sql` in `backend/src/main/resources/db/migration`.
   Do not edit an already-applied migration or use Hibernate schema generation.
3. **Frontend route:** create `frontend/src/features/<module>/routes.js` and
   export a default array of `{ path, element, visibility }` objects. Use
   `visibility: 'protected'` when the route will require Phase 1 protection.
4. **Navigation:** create `navigation.js` in that feature folder and export
   `{ id, label, route, order, visibility }` items. Choose `always`,
   `anonymous`, or `authenticated` visibility.
5. **Slot UI:** when needed, create `slots.js` and export contributions with
   `id`, `name`, `order`, and `element`. Use only declared slot names:
   `course-page.enroll-area`, `player.side-panel`, and `header.actions`.
6. **Tests and docs:** add backend and frontend tests for the feature behavior,
   keep the frozen error/pagination/request-ID contracts, and update feature
   documentation where its public contract changes.

The Vite registries discover each feature's registration files automatically.
Do not add feature routes or UI directly to the app shell.
