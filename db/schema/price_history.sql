DROP TABLE IF EXISTS price_history CASCADE;

CREATE TABLE IF NOT EXISTS price_history (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol           VARCHAR(20) NOT NULL REFERENCES instruments(symbol),      
    price_date       DATE NOT NULL,
    open_price       NUMERIC(18,2) NOT NULL CHECK (open_price > 0),
    high_price       NUMERIC(18,2) NOT NULL CHECK (high_price > 0),
    low_price        NUMERIC(18,2) NOT NULL CHECK (low_price > 0),
    close_price      NUMERIC(18,2) NOT NULL CHECK (close_price > 0),
    volume           BIGINT NOT NULL CHECK (volume > 0),
    UNIQUE(symbol, price_date)
);