# XAU/USD market-data providers

The app intentionally accepts only feeds that identify the instrument as XAU priced in USD. Gold futures, gold-backed crypto tokens, and IDR-denominated exchange pairs are not interchangeable with XAU/USD spot and are excluded from the signal pipeline.

| Provider | Instrument | Key required | Public historical candles | App behavior |
|---|---|---:|---:|---|
| Twelve Data | Gold Spot US Dollar (`XAU/USD`) | Yes | 5-minute time series, subject to plan entitlement | Full candle-based chart, candidate signal, and local paper simulation when data is valid/fresh |
| Gold API | `XAU` in `USD` | No for current quote | No-key endpoint is quote-only; documented history/OHLC endpoints require a key | Shows the live quote and source status only; signal stays `WAIT`, candle chart remains empty, no new paper position is opened |

## Twelve Data

Adapter: `TwelveDataProvider`, through `MarketDataProvider`.

The app requests `GET https://api.twelvedata.com/time_series?symbol=XAU%2FUSD&interval=5min&outputsize=5000&order=desc&timezone=UTC`. The user's own key is sent in the documented `Authorization: apikey …` header over HTTPS. It is encrypted locally with Android Keystore AES-GCM, kept in a provider-specific storage slot, and never bundled, logged, or placed in the URL.

A key alone does not guarantee access: the account must be entitled to XAU/USD commodities and 5-minute intraday time series. The integration strictly checks the XAU/USD symbol, UTC timestamps, OHLC validity, duplicate timestamps, and response errors. Missing, denied, stale, or malformed data never produces a mock candle or trade. Attribution shown: `Source: Twelve Data`.

Official sources: [API documentation](https://twelvedata.com/docs), [pricing](https://twelvedata.com/pricing), [terms](https://twelvedata.com/terms), and [attribution guidance](https://support.twelvedata.com/en/articles/12647398-attribution-guidelines-for-using-twelve-data).

## Gold API (current quote only, no key)

Adapter: `GoldApiProvider`. The app requests `GET https://api.gold-api.com/price/XAU/USD`, with no key or credentials. The parser accepts only JSON whose symbol is `XAU`, currency is `USD`, price is finite/positive, and `updatedAt` is a valid timestamp. It returns **no candles**; that absence is intentional. The app does not derive fabricated OHLC from a single quote, so candle-based signals and automatic paper entries stay disabled in this mode.

The public [Gold API webpage](https://gold-api.com/) was inspected in the rendered browser. It itself fetches `https://api.gold-api.com/price/XAU`; scraping its HTML would add a brittle page/rendering dependency without adding fresher data. The no-key [Get Price](https://gold-api.com/docs) endpoint warns to cache for at least 30 seconds. The documented [history](https://gold-api.com/docs) endpoint requires an `x-api-key`; free history is limited to 10 requests/hour and minute/hour grouping is premium-only. The documented [OHLC](https://gold-api.com/docs) endpoint also requires a key. The app polls only while foregrounded and no more often than every five minutes.

Gold API [terms](https://gold-api.com/terms) (effective 2026-09-10) describe the service as free and allow commercial API use, but disclaim accuracy, completeness, reliability, timeliness, and uninterrupted availability; they prohibit spam/abuse and multiple requests per second. The app does not rely on the endpoint for financial execution and labels the source.

## Sources intentionally excluded

- API Ninjas' Gold Price API returns gold **futures**, not XAU/USD spot. Its historical 5-minute OHLCV access is plan-dependent; a key for API Ninjas cannot be used with Twelve Data.
- Indodax offers XAUT/IDR and PAXG/IDR token markets. Those are gold-backed crypto assets quoted in IDR, not XAU/USD spot.
- Yahoo Finance's public chart probe was rate-limited (HTTP 429); no reliable authorized XAU/USD historical feed was verified there.

## Verification status

On 2026-09-29, the no-key Gold API URL returned HTTP 200 with `symbol=XAU`, `currency=USD`, `price`, and `updatedAt`; this is an endpoint-shape/reachability check, not independent verification of the source price. Twelve Data demo probes previously returned HTTP 401, and no entitled user key was available, so its live 5-minute data remains unverified until the user configures an eligible key.
