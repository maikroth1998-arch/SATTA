CREATE TABLE IF NOT EXISTS worklogs (
  id BIGSERIAL PRIMARY KEY,
  order_number TEXT NOT NULL,
  customer TEXT NOT NULL DEFAULT '',
  ticket_number TEXT NOT NULL,
  booked_at TIMESTAMPTZ NOT NULL,
  person TEXT NOT NULL,
  hours NUMERIC(8, 2) NOT NULL,
  comment TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_worklogs_order_ticket
  ON worklogs (order_number, ticket_number);

CREATE INDEX IF NOT EXISTS idx_worklogs_order_booked_at
  ON worklogs (order_number, booked_at);
