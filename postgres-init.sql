-- Runs once, the first time the Postgres container creates its data volume.
--
-- Each service owns its own database rather than sharing one. The Order Service
-- must not be able to read or corrupt the orchestrator's saga tables, and vice
-- versa; separate databases make that a hard boundary instead of a convention.
CREATE DATABASE swifttrack_orders;
CREATE DATABASE swifttrack_saga;
CREATE DATABASE swifttrack_auth;
