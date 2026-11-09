# Instructor course authoring

Phase 2 course-authoring screens are registered through `routes.js` and
`navigation.js`. All server state is fetched and mutated through TanStack Query
and the shared API client. The UI mode only controls navigation and a banner;
the backend authorizes every authoring request.
