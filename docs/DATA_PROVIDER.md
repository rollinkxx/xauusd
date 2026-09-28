# Market-data provider

## Selected adapter: Twelve Data

Provider adapter: `MarketDataProvider`; concrete adapter: `TwelveDataProvider`.

- Instrument: `XAU/USD` (Gold Spot US Dollar), a provider-specific spot-gold series. It is not a universal OTC XAUUSD price.
- Endpoint: `GET https://api.twelvedata.com/time_series?symbol=XAU%2FUSD&interval=5min&outputsize=5000&order=desc&timezone=UTC`.
- Authentication: user's own key in `Authorization: apikey …` header (the header form is documented by Twelve Data). HTTPS only; no key in URL, logs, repo, or APK as an application secret. Local ciphertext is AES-GCM encrypted with an Android Keystore key.
- Data: OHLC series; price uses the latest returned 5-minute close. Bid/ask are not provided by this integration and stay `Not supplied` in the UI. Candle timestamp is parsed as UTC; receive time and request latency are separately retained.
- Refresh: one API request every five minutes only while the app is foregrounded; `outputsize` is bounded by the documented 5,000 values. Automatic retries use bounded exponential backoff; no aggressive background service or fallback feed.
- HTTP/API error, malformed JSON, invalid OHLC, stale timestamps or missing key never create a synthetic price or allow a new virtual position.
- Provider's current individual pricing page lists commodity access under a paid/higher plan; XAU/USD is shown in the commodity catalog. A key with access to commodities and the requested intraday interval is required. Plan names/entitlements can change; users must confirm entitlement in their account.
- Individual/free-tier access is for personal/internal/non-commercial use as permitted by plan. Public/external display or redistribution can require explicit rights. This app is not licensed for commercial redistribution of provider data.
- Attribution shown: `Source: Twelve Data`.

## Verification performed (2026-09-28)

Official documentation reviewed:

- [Twelve Data API docs](https://twelvedata.com/docs): `time_series`, 5min interval, `outputsize` 1–5000, UTC intraday timezone; its commodity catalog lists XAU/USD Gold Spot. Docs recommend the Authorization header, warn to secure keys, and describe 401/403/429 conditions.
- [Twelve Data individual pricing](https://twelvedata.com/pricing): lists commodity market data under a higher individual tier; free/basic allowance is limited and does not imply commodity entitlement.
- [Twelve Data Terms](https://twelvedata.com/terms): license/redistribution boundaries; external display rights depend on plan/add-on or separate agreement.
- [Attribution guidance](https://support.twelvedata.com/en/articles/12647398-attribution-guidelines-for-using-twelve-data): recommends a visible source attribution and a link for public display.
- [Alpha Vantage docs](https://www.alphavantage.co/documentation/): `GOLD_SILVER_SPOT` accepts XAU, but gold history is daily/weekly/monthly; its intraday endpoint documented here is for equities and premium. It does not meet this app's multi-timeframe intraday needs as a sole free fallback.
- [Finnhub API docs](https://finnhub.io/docs/api): forex candle symbols/exchanges vary; documented stock quote is US-equity oriented, and access to streaming/candles is plan-dependent. No verified supported XAUUSD live source was established for this app.

## Live test status

Twelve Data's public `demo` key returned HTTP 401 for both the documented `price` and `time_series` probes. No user API credential was available, so an entitled XAU/USD feed, its current freshness, rate limit, subscription licensing and actual data delivery were **not verified**. The app must not report `LIVE` until a user key returns valid recent candles.
