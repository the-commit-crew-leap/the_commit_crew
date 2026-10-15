COPY price_history (symbol, price_date, open_price, high_price, low_price, close_price, volume) 
FROM '/docker-entrypoint-initdb.d/data/price_history.csv' 
WITH (FORMAT csv, HEADER true, DELIMITER ',');