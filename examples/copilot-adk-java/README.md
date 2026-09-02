# CopilotKit + ADK-Java Example

A [CopilotKit](https://copilotkit.ai) frontend connected to a [Google ADK](https://google.github.io/adk-docs/) Java backend via the [AG-UI protocol](https://docs.ag-ui.com).

## Prerequisites

- Node.js 18+
- Java 21+
- A Google ADK-compatible model API key (e.g. Gemini)

## Running

### 1. Start the ADK-Java backend

```bash
cd ../adk-java-example
# Set your API key
export GOOGLE_API_KEY=your-key-here
../../mvnw spring-boot:run
```

The backend will start on `http://localhost:8080` with a `POST /chat` endpoint.

### 2. Start the CopilotKit frontend

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) and use the chat sidebar to interact with the ADK-Java agent.

## Configuration

| Variable   | Default                      | Description                  |
|------------|------------------------------|------------------------------|
| `HTTP_URL` | `http://localhost:8080/chat`  | ADK-Java backend endpoint    |
