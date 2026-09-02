import {
  CopilotRuntime,
  ExperimentalEmptyAdapter,
  copilotRuntimeNextJSAppRouterEndpoint,
} from "@copilotkit/runtime";

import { HttpAgent } from "@ag-ui/client";
import { NextRequest } from "next/server";

const HTTP_URL = process.env.HTTP_URL || "http://localhost:8080/chat";

const serviceAdapter = new ExperimentalEmptyAdapter();

const httpAgent = new HttpAgent({
  url: HTTP_URL,
});

const runtime = new CopilotRuntime({
  agents: {
    agent: httpAgent,
  },
});

export const POST = async (req: NextRequest) => {
  const { handleRequest } = copilotRuntimeNextJSAppRouterEndpoint({
    runtime,
    serviceAdapter,
    endpoint: "/api/copilotkit",
  });

  return handleRequest(req);
};
