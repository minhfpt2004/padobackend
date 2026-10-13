# Location API

The backend is the only component that contacts the configured geocoding provider. The frontend never receives a provider credential or URL.

## Public lookup endpoints

- `GET /api/locations/reverse?latitude=10.776&longitude=106.701`
- `GET /api/locations/search?query=Quan%207%2C%20Ho%20Chi%20Minh&limit=5`
- `GET /api/products/nearby?latitude=10.776&longitude=106.701&radiusKm=20&limit=20`

Address search is explicitly submitted by the user; it is not an autocomplete endpoint. Search is restricted to Vietnam for the MVP. Responses contain a compact administrative label and coordinates.

## Signed-in user location

- `GET /api/locations/me`
- `PUT /api/locations/me`
- `DELETE /api/locations/me`

Example GPS request:

```json
{
  "label": "Ben Nghe, District 1, Ho Chi Minh City",
  "source": "GPS",
  "latitude": 10.776,
  "longitude": 106.701,
  "accuracy": 25
}
```

Example address selected from search:

```json
{
  "label": "District 7, Ho Chi Minh City",
  "source": "MANUAL",
  "latitude": 10.735,
  "longitude": 106.721
}
```

## Provider configuration

```properties
GEOCODING_BASE_URL=https://nominatim.openstreetmap.org
GEOCODING_USER_AGENT=PADO-MVP/1.0
GEOCODING_MINIMUM_INTERVAL_MS=1100
```

The service serializes provider traffic, keeps a bounded in-memory cache and exposes only administrative address fields. The public Nominatim endpoint is suitable only for low-volume MVP use; replace `GEOCODING_BASE_URL` with a production provider or self-hosted instance before scaling.
