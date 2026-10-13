# Cloudinary image upload API

The backend performs authenticated Cloudinary uploads. Cloudinary credentials must only be configured in the backend environment:

```properties
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret
CLOUDINARY_FOLDER=pado/products
```

## Upload product images

```http
POST /api/uploads/images
Authorization: Bearer <accessToken>
Content-Type: multipart/form-data
```

Send one to eight parts named `files`. Each file may be JPG, PNG or WebP and must not exceed 8 MB.

Example response:

```json
[
  {
    "url": "https://res.cloudinary.com/.../image/upload/...jpg",
    "publicId": "pado/products/example",
    "format": "jpg",
    "width": 1200,
    "height": 1500,
    "bytes": 245000
  }
]
```

Use the returned `url` values as the `images` array in `POST /api/products` or `PUT /api/products/{id}`.
