# Authentication feature

Phase 1 owns account registration, login, password reset, in-memory session
state, refresh recovery, and its public route/navigation contributions. Access
tokens exist only in the Zustand store; refresh credentials remain in the
server-managed HttpOnly cookie.
