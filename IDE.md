# IDE Integration Guide

Use the Android app's **Local API Endpoint** feature as an **OpenAI-compatible** endpoint for editors, agent tools, and SDKs.

## 1. Start the endpoint in the app

1. Open the app on your Android device.
2. Download a supported LLM model if you have not already.
3. Open **Local API Endpoint** from the home screen.
4. Select the serving model.
5. Tap **Start server**.
6. Copy the **base URL** and **bearer token** from the dashboard.

## 2. Important connection notes

- The app now **listens on all available interfaces**.
- `/v1/*` routes require the bearer token shown in the dashboard.
- The app may display a base URL using `0.0.0.0`. That is the **bind address**, not the client address to use.
- From another device on the same network, replace `0.0.0.0` with the **phone's LAN IP**, for example:

```text
http://192.168.1.42:8080
```

- The endpoint is **HTTP only**, not HTTPS.
- Current API surface:
  - `GET /healthz`
  - `GET /v1/models`
  - `POST /v1/chat/completions`

## 3. Quick connectivity check

Replace the host, port, token, and model name with your values:

```bash
curl http://192.168.1.42:8080/healthz
```

```bash
curl http://192.168.1.42:8080/v1/models \
  -H "Authorization: Bearer YOUR_TOKEN"
```

```bash
curl http://192.168.1.42:8080/v1/chat/completions \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "YOUR_MODEL_NAME",
    "stream": false,
    "messages": [
      {"role": "user", "content": "Write a hello world in Kotlin."}
    ]
  }'
```

## 4. VS Code

VS Code itself does not provide a generic custom OpenAI endpoint for all AI features, but popular extensions do.

### Continue

Add or update your Continue config to point at the app:

```json
{
  "models": [
    {
      "title": "Android Local Endpoint",
      "provider": "openai",
      "model": "YOUR_MODEL_NAME",
      "apiBase": "http://192.168.1.42:8080/v1",
      "apiKey": "YOUR_TOKEN"
    }
  ]
}
```

Use the model name returned by `GET /v1/models`.

### Cline / Roo Code / similar OpenAI-compatible extensions

If the extension supports a **custom OpenAI-compatible provider**, use:

- **Base URL:** `http://192.168.1.42:8080/v1`
- **API key / token:** `YOUR_TOKEN`
- **Model:** `YOUR_MODEL_NAME`

If the extension has a provider type selector, choose **OpenAI-compatible** or **OpenAI** with a custom base URL.

## 5. Codex CLI

If you are using Codex CLI against an OpenAI-compatible endpoint, set:

```bash
export OPENAI_BASE_URL=http://192.168.1.42:8080/v1
export OPENAI_API_KEY=YOUR_TOKEN
```

Then run Codex with the endpoint model name:

```bash
codex --model YOUR_MODEL_NAME
```

If your local Codex setup uses a config file instead of environment variables, use the same values there:

- **base URL:** `http://192.168.1.42:8080/v1`
- **API key:** `YOUR_TOKEN`
- **model:** `YOUR_MODEL_NAME`

## 6. OpenAI-compatible SDKs and tools

Anything that supports a custom OpenAI-compatible base URL can usually be pointed at the app.

### Python

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://192.168.1.42:8080/v1",
    api_key="YOUR_TOKEN",
)

response = client.chat.completions.create(
    model="YOUR_MODEL_NAME",
    messages=[{"role": "user", "content": "Summarize this repository."}],
)

print(response.choices[0].message.content)
```

### JavaScript / TypeScript

```ts
import OpenAI from "openai";

const client = new OpenAI({
  baseURL: "http://192.168.1.42:8080/v1",
  apiKey: "YOUR_TOKEN",
});

const response = await client.chat.completions.create({
  model: "YOUR_MODEL_NAME",
  messages: [{ role: "user", content: "Explain this code." }],
});

console.log(response.choices[0].message?.content);
```

## 7. Troubleshooting

### Connection refused

- Make sure the endpoint is running in the app.
- Make sure you are using the **device IP**, not `0.0.0.0`.
- Confirm the client machine can reach the phone on the same network.

### 401 Unauthorized

- Check that you set:

```text
Authorization: Bearer YOUR_TOKEN
```

- If needed, rotate the token in the dashboard and update the client.

### Model not found

- Call `GET /v1/models` and use the exact returned model ID.
- Make sure that same model is selected in the dashboard.

### Requests fail while the server is starting

- Wait until the dashboard shows the selected model as **Ready**.
- The dashboard now shows a progress indicator while the model is initializing.

### Streaming issues

- Use `stream: true` only with clients that support SSE/OpenAI-style streaming.
- If a client does not handle streaming well, switch to non-streaming requests first.

## 8. Security note

This endpoint is intended for **trusted local/network use**. It is not hardened for internet exposure. Keep the bearer token private and avoid exposing the device beyond networks you trust.
