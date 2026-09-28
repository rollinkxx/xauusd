# XAU/USD provider source audit — 2026-09-29

## Gold API public quote

The rendered [Gold API homepage](https://gold-api.com/) visibly showed symbol `XAU`, a USD-per-ounce quote, and a relative update label. Browser resource inspection showed that the page itself requests `https://api.gold-api.com/price/XAU`. The explicit-USD endpoint `https://api.gold-api.com/price/XAU/USD` returned HTTP 200 with `symbol=XAU`, `currency=USD`, `price`, and `updatedAt` during a direct sandbox probe.

Scraping the rendered HTML would add page rendering, JavaScript, and DOM selectors around the same public JSON response. It would not make the data fresher. The app therefore calls the no-key JSON endpoint directly, at a five-minute foreground cadence, and does not scrape the page. This source provides a current quote only; documented historical and OHLC endpoints require a key. No candles are synthesized from the quote, and signals/new paper entries remain disabled in this mode. Gold API docs advise caching for at least 30 seconds; the app's polling cadence respects that.

This probe verifies endpoint reachability and response shape, **not** independent quote accuracy or a service-level guarantee. Gold API's [terms](https://gold-api.com/terms) disclaim accuracy, completeness, reliability, and timeliness and prohibit spam/abuse.

## Sources excluded from the XAU/USD feed

API Ninjas Gold Price provides gold futures data, not XAU/USD spot; an API Ninjas token cannot authenticate to Twelve Data. Indodax XAUT/IDR and PAXG/IDR are gold-backed crypto markets quoted in IDR, not spot XAU/USD. These sources are not selectable and do not enter the app's signal or paper-trading pipeline.

## Historical candle option

Twelve Data documents `XAU/USD` (Gold Spot US Dollar) and a 5-minute `time_series` endpoint. Access requires the user's own key and a plan entitled to commodities and intraday data. Sandbox demo-key requests returned HTTP 401, so a user's entitled history feed remains unverified until configured in the app.
