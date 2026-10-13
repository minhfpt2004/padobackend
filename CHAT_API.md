# Chat API

Base URL: `http://localhost:8080/api`

All endpoints require `Authorization: Bearer <accessToken>`.

## Conversation model

A conversation is unique for one product and one buyer. The product owner is always the seller. Only that buyer, the seller, and an administrator can read or send its messages.

### List conversations

`GET /chats/conversations`

Returns the signed-in user's conversations, newest activity first. Each item includes the product snapshot, both participants, `currentUserRole`, the latest message, and `unreadCount`.

### Open a conversation as buyer

`POST /chats/conversations`

```json
{ "productId": 1 }
```

Calling this endpoint repeatedly returns the same conversation. A user cannot open a buyer conversation for their own product.

### Open an order conversation as seller

```json
{ "productId": 1, "buyerId": 3 }
```

The signed-in user must own the product, and the buyer must have an order for that product. This form is used by the seller's sales-history screen.

### Read messages

`GET /chats/conversations/{conversationId}/messages`

Messages are returned oldest first. `mine` is calculated for the signed-in user.

### Send a message

`POST /chats/conversations/{conversationId}/messages`

```json
{ "content": "Món này còn không bạn?" }
```

Content is trimmed, required, and limited to 2,000 characters. The response has HTTP status `201`.

### Mark incoming messages as read

`PATCH /chats/conversations/{conversationId}/read`

Response:

```json
{ "updatedCount": 2 }
```

## Error behavior

- `400`: invalid product/buyer/message input
- `401`: missing or invalid token
- `403`: user is not a participant, or seller tries to contact someone who did not order the product
- `404`: product, buyer, or conversation does not exist
- `409`: product owner tries to open a buyer chat without a buyer
