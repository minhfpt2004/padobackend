# Location API

Base URL: `http://localhost:8080/api`. All endpoints require a bearer token.

The API stores only the user's currently selected location. It does not store a location history or continuously track the device.

## Read current location

`GET /locations/me`

An account without a saved location returns `configured: false` and nullable location fields.

## Save GPS location

`PUT /locations/me`

```json
{
  "label": "Bến Nghé, Quận 1, TP.HCM",
  "source": "GPS",
  "latitude": 10.776,
  "longitude": 106.701,
  "accuracy": 25
}
```

GPS updates require valid latitude, longitude, and non-negative accuracy.

## Save a manually selected area

```json
{
  "label": "Quận 7, TP.HCM",
  "source": "MANUAL"
}
```

Manual locations intentionally have no coordinates, so they cannot be used to calculate distance.

## Clear location

`DELETE /locations/me`

This removes the saved current location from the account.
