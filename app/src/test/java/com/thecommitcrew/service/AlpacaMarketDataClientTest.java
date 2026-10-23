package com.thecommitcrew.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Instant;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thecommitcrew.config.AlpacaProperties;
import com.thecommitcrew.domain.dto.IntradayPriceResponse;

class AlpacaMarketDataClientTest {

    private static final String BASE_URL = "http://alpaca.test";
    private static final String API_KEY = "test-key";
    private static final String API_SECRET = "test-secret";

    private MockRestServiceServer server;
    private AlpacaMarketDataClient client;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter();
        stringConverter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));
        HttpMessageConverter<Object> jacksonConverter = new Jackson2TestMessageConverter(objectMapper);
        RestClient.Builder restClientBuilder = RestClient.builder()
            .configureMessageConverters(converters -> {
                converters.disableDefaults()
                    .withStringConverter(stringConverter)
                    .withJsonConverter(jacksonConverter);
            });
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        client = new AlpacaMarketDataClient(
            new AlpacaProperties(true, API_KEY, API_SECRET, BASE_URL, "iex"),
            restClientBuilder,
            objectMapper
        );
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    private static final class Jackson2TestMessageConverter
        extends AbstractHttpMessageConverter<Object> {

        private final ObjectMapper objectMapper;

        private Jackson2TestMessageConverter(ObjectMapper objectMapper) {
            super(MediaType.APPLICATION_JSON);
            this.objectMapper = objectMapper;
        }

        @Override
        protected boolean supports(Class<?> clazz) {
            return true;
        }

        @Override
        protected Object readInternal(Class<? extends Object> clazz, HttpInputMessage inputMessage)
            throws IOException {
            return objectMapper.readValue(inputMessage.getBody(), objectMapper.constructType(clazz));
        }

        @Override
        protected void writeInternal(Object object, HttpOutputMessage outputMessage)
            throws IOException {
            objectMapper.writeValue(outputMessage.getBody(), object);
        }
    }

    @Test
    void exposesConfiguration() {
        assertTrue(client.isConfigured());
        assertEquals("iex", client.feed());
    }

    @Test
    void getLatestTradeRequestsAndCachesLatestTrade() {
        server.expect(requestTo(BASE_URL + "/v2/stocks/AAPL/trades/latest?feed=iex"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("APCA-API-KEY-ID", API_KEY))
            .andExpect(header("APCA-API-SECRET-KEY", API_SECRET))
            .andRespond(withSuccess("""
                {"trade":{"p":195.50,"t":"2026-10-19T14:30:00Z"}}
                """, MediaType.APPLICATION_JSON));

        AlpacaMarketDataClient.LatestTrade first = client.getLatestTrade("AAPL");
        AlpacaMarketDataClient.LatestTrade second = client.getLatestTrade("AAPL");

        assertEquals(new BigDecimal("195.50"), first.price());
        assertEquals(Instant.parse("2026-10-19T14:30:00Z"), first.timestamp());
        assertEquals(first, second);
    }

    @Test
    void getLatestTradeRejectsMissingTrade() {
        server.expect(requestTo(BASE_URL + "/v2/stocks/AAPL/trades/latest?feed=iex"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getLatestTrade("AAPL")
        );

        assertEquals("Alpaca returned no latest trade for symbol: AAPL", exception.getMessage());
    }

    @Test
    void getLatestTradeRejectsNullResponse() {
        server.expect(requestTo(BASE_URL + "/v2/stocks/AAPL/trades/latest?feed=iex"))
            .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getLatestTrade("AAPL")
        );

        assertEquals("Alpaca returned no latest trade for symbol: AAPL", exception.getMessage());
    }

    @Test
    void getLatestTradeRejectsTradeWithoutPrice() {
        server.expect(requestTo(BASE_URL + "/v2/stocks/AAPL/trades/latest?feed=iex"))
            .andRespond(withSuccess("{\"trade\":{\"t\":\"2026-10-19T14:30:00Z\"}}", MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getLatestTrade("AAPL")
        );

        assertEquals("Alpaca returned no latest trade for symbol: AAPL", exception.getMessage());
    }

    @Test
    void getIntradayPricesKeepsLatestRegularSession() {
        server.expect(request -> {
            assertEquals(HttpMethod.GET, request.getMethod());
            assertEquals("/v2/stocks/AAPL/bars", request.getURI().getPath());
            assertEquals("1Min", request.getURI().getQuery().split("&")[0].split("=")[1]);
        }).andRespond(withSuccess("""
            {"bars": [
              {"t":"2026-10-19T13:00:00Z","o":190,"h":191,"l":189,"c":190.5,"v":10},
              {"t":"2026-10-19T13:30:00Z","o":191,"h":192,"l":190,"c":191.5,"v":20},
              {"t":"2026-10-19T20:00:00Z","o":192,"h":193,"l":191,"c":192.5,"v":30},
              {"t":"2026-10-20T13:30:00Z","o":193,"h":194,"l":192,"c":193.5,"v":40}
            ]}
            """, MediaType.APPLICATION_JSON));

        IntradayPriceResponse response = client.getIntradayPrices("AAPL");

        assertEquals("AAPL", response.symbol());
        assertEquals("ALPACA_IEX", response.source());
        assertTrue(response.live());
        assertEquals(1, response.bars().size());
        assertEquals(Instant.parse("2026-10-20T13:30:00Z"), response.updatedAt());
        assertEquals(new BigDecimal("193.5"), response.bars().get(0).close());
    }

    @Test
    void getIntradayPricesReadsBarsFromExactSymbolKey() {
        server.expect(request -> assertEquals("/v2/stocks/AAPL/bars", request.getURI().getPath()))
            .andRespond(withSuccess("""
                {"bars":{"AAPL":[
                  {"t":"2026-10-20T13:30:00Z","o":191,"h":192,"l":190,"c":191.5,"v":20}
                ]}}
                """, MediaType.APPLICATION_JSON));

        IntradayPriceResponse response = client.getIntradayPrices("AAPL");

        assertEquals(1, response.bars().size());
        assertEquals(new BigDecimal("191.5"), response.bars().get(0).close());
    }

    @Test
    void getIntradayPricesMatchesSymbolKeysWithoutCaseSensitivity() {
        server.expect(request -> assertEquals("/v2/stocks/AAPL/bars", request.getURI().getPath()))
            .andRespond(withSuccess("""
                {"bars":{"aapl":[
                  {"t":"2026-10-20T13:30:00Z","o":191,"h":192,"l":190,"c":191.5,"v":20}
                ]}}
                """, MediaType.APPLICATION_JSON));

        IntradayPriceResponse response = client.getIntradayPrices("AAPL");

        assertEquals(1, response.bars().size());
        assertEquals(new BigDecimal("191.5"), response.bars().get(0).close());
    }

    @Test
    void getIntradayPricesRejectsMissingBars() {
        server.expect(request -> assertEquals("/v2/stocks/AAPL/bars", request.getURI().getPath()))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> client.getIntradayPrices("AAPL"));
    }

    @Test
    void getIntradayPricesRejectsNonArrayBars() {
        server.expect(request -> assertEquals("/v2/stocks/AAPL/bars", request.getURI().getPath()))
            .andRespond(withSuccess("{\"bars\":{}}", MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getIntradayPrices("AAPL")
        );

        assertEquals("Alpaca returned no intraday bars for symbol: AAPL", exception.getMessage());
    }

    @Test
    void getIntradayPricesRejectsWhenEveryBarIsMalformed() {
        server.expect(request -> assertEquals("/v2/stocks/AAPL/bars", request.getURI().getPath()))
            .andRespond(withSuccess("""
                {"bars":[{"t":"2026-10-20T13:30:00Z","o":-1,"h":192,"l":190,"c":191.5,"v":20}]}
                """, MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getIntradayPrices("AAPL")
        );

        assertEquals("Alpaca returned no valid intraday bars for symbol: AAPL", exception.getMessage());
    }

    @Test
    void getIntradayPricesUsesCurrentDateWhenNoRegularSessionBarsExist() {
        server.expect(request -> assertEquals("/v2/stocks/AAPL/bars", request.getURI().getPath()))
            .andRespond(withSuccess("""
                {"bars":[
                  {"t":"2026-10-20T12:00:00Z","o":190,"h":191,"l":189,"c":190.5,"v":10},
                  {"t":"2026-10-20T20:00:00Z","o":191,"h":192,"l":190,"c":191.5,"v":20}
                ]}
                """, MediaType.APPLICATION_JSON));

        IntradayPriceResponse response = client.getIntradayPrices("AAPL");

        assertEquals(java.time.LocalDate.now(java.time.ZoneId.of("America/New_York")), response.sessionDate());
        assertTrue(response.bars().isEmpty());
        assertNull(response.updatedAt());
    }

    @Test
    void getIntradayPricesRejectsInvalidJson() {
        server.expect(request -> assertEquals("/v2/stocks/AAPL/bars", request.getURI().getPath()))
            .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getIntradayPrices("AAPL")
        );

        assertEquals("Alpaca returned invalid intraday JSON for symbol: AAPL", exception.getMessage());
    }

    @Test
    void getIntradayPricesSkipsMalformedBarsWhenValidBarsRemain() {
        server.expect(request -> assertEquals("/v2/stocks/AAPL/bars", request.getURI().getPath()))
            .andRespond(withSuccess("""
                {"bars":[
                  {"t":"not-an-instant","o":190,"h":191,"l":189,"c":190.5,"v":10},
                  {"t":"2026-10-20T13:30:00Z","o":191,"h":192,"l":190,"c":191.5,"v":20}
                ]}
                """, MediaType.APPLICATION_JSON));

        IntradayPriceResponse response = client.getIntradayPrices("AAPL");

        assertEquals(1, response.bars().size());
        assertEquals(Instant.parse("2026-10-20T13:30:00Z"), response.updatedAt());
        assertEquals(new BigDecimal("191.5"), response.bars().get(0).close());
    }

    @Test
    void getLatestCryptoSnapshotParsesAndCachesSnapshot() {
        server.expect(requestTo(BASE_URL + "/v1beta3/crypto/us/snapshots?symbols=BTC/USD"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {"snapshots":{"BTC/USD":{
                  "latestTrade":{"p":68123.45,"t":"2026-10-19T14:30:00Z"},
                  "prevDailyBar":{"c":67500.00}
                }}}
                """, MediaType.APPLICATION_JSON));

        AlpacaMarketDataClient.CryptoSnapshot first = client.getLatestCryptoSnapshot("BTC/USD");
        AlpacaMarketDataClient.CryptoSnapshot second = client.getLatestCryptoSnapshot("BTC/USD");

        assertEquals(new BigDecimal("68123.45"), first.price());
        assertEquals(0, new BigDecimal("67500.00").compareTo(first.previousClose()));
        assertEquals(Instant.parse("2026-10-19T14:30:00Z"), first.timestamp());
        assertEquals(first, second);
    }

    @Test
    void getLatestCryptoSnapshotRejectsInvalidJson() {
        server.expect(requestTo(BASE_URL + "/v1beta3/crypto/us/snapshots?symbols=BTC/USD"))
            .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getLatestCryptoSnapshot("BTC/USD")
        );

        assertEquals("Alpaca returned invalid JSON for symbol: BTC/USD", exception.getMessage());
    }

    @Test
    void getLatestCryptoSnapshotRejectsNegativePrice() {
        server.expect(requestTo(BASE_URL + "/v1beta3/crypto/us/snapshots?symbols=BTC/USD"))
            .andRespond(withSuccess("""
                {"BTC/USD":{"latestTrade":{"p":-1}}}
                """, MediaType.APPLICATION_JSON));

        assertThrows(
            IllegalStateException.class,
            () -> client.getLatestCryptoSnapshot("BTC/USD")
        );
    }

    @Test
    void getLatestCryptoSnapshotFindsNormalizedSymbolAndAllowsOptionalFieldsToBeMissing() {
        server.expect(requestTo(BASE_URL + "/v1beta3/crypto/us/snapshots?symbols=BTC/USD"))
            .andRespond(withSuccess("""
                                {"snapshots":{"btcusd":{
                                    "latestTrade":{"p":68123.45},
                                    "prevDailyBar":{"o":67000}
                                }}}
                """, MediaType.APPLICATION_JSON));

        AlpacaMarketDataClient.CryptoSnapshot snapshot = client.getLatestCryptoSnapshot("BTC/USD");

        assertEquals(new BigDecimal("68123.45"), snapshot.price());
        assertNull(snapshot.previousClose());
        assertNull(snapshot.timestamp());
    }

    @Test
    void getLatestCryptoSnapshotRejectsSnapshotWithoutLatestTrade() {
        server.expect(requestTo(BASE_URL + "/v1beta3/crypto/us/snapshots?symbols=BTC/USD"))
            .andRespond(withSuccess("{\"BTC/USD\":{}}", MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getLatestCryptoSnapshot("BTC/USD")
        );

        assertEquals("Alpaca returned no crypto snapshot for symbol: BTC/USD", exception.getMessage());
    }

    @Test
    void getLatestCryptoSnapshotRejectsMissingPrice() {
        server.expect(requestTo(BASE_URL + "/v1beta3/crypto/us/snapshots?symbols=BTC/USD"))
            .andRespond(withSuccess("{\"BTC/USD\":{\"latestTrade\":{}}}", MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getLatestCryptoSnapshot("BTC/USD")
        );

        assertEquals("Alpaca returned an invalid crypto price for symbol: BTC/USD", exception.getMessage());
    }

    @Test
    void getLatestCryptoSnapshotRejectsNonNumericPrice() {
        server.expect(requestTo(BASE_URL + "/v1beta3/crypto/us/snapshots?symbols=BTC/USD"))
            .andRespond(withSuccess("{\"BTC/USD\":{\"latestTrade\":{\"p\":\"unknown\"}}}", MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getLatestCryptoSnapshot("BTC/USD")
        );

        assertEquals("Alpaca returned an invalid crypto price for symbol: BTC/USD", exception.getMessage());
    }

    @Test
    void getLatestCryptoSnapshotRejectsMissingSnapshot() {
        server.expect(requestTo(BASE_URL + "/v1beta3/crypto/us/snapshots?symbols=BTC/USD"))
            .andRespond(withSuccess("{\"snapshots\":{}}", MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getLatestCryptoSnapshot("BTC/USD")
        );

        assertEquals("Alpaca returned no crypto snapshot for symbol: BTC/USD", exception.getMessage());
    }
}