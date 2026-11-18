-- One PostgreSQL instance, one database per microservice (no shared tables).
-- Used by docker-compose (as the postgres superuser) and for native local setup.
SELECT 'CREATE DATABASE aeroflow_auth' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aeroflow_auth')\gexec
SELECT 'CREATE DATABASE aeroflow_flight' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aeroflow_flight')\gexec
SELECT 'CREATE DATABASE aeroflow_passenger' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aeroflow_passenger')\gexec
SELECT 'CREATE DATABASE aeroflow_booking' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aeroflow_booking')\gexec
SELECT 'CREATE DATABASE aeroflow_checkin' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aeroflow_checkin')\gexec
SELECT 'CREATE DATABASE aeroflow_notification' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aeroflow_notification')\gexec
SELECT 'CREATE DATABASE aeroflow_reporting' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aeroflow_reporting')\gexec
SELECT 'CREATE DATABASE aeroflow_events' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aeroflow_events')\gexec
