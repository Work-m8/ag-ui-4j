"use client";
import { CopilotSidebar } from "@copilotkit/react-ui";
import { useCoAgent } from "@copilotkit/react-core";

export default function Page() {
  const { state } = useCoAgent<{ language: string }>({
    name: "agent",
    initialState: { language: "english" },
  });

  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-8">
      <h1 className="text-3xl font-bold mb-4">ADK-Java + CopilotKit</h1>
      <p className="text-lg mb-2">
        This example connects a CopilotKit frontend to a Google ADK-Java backend
        via the AG-UI protocol.
      </p>
      <p className="text-sm text-gray-500">Agent state language: {state.language}</p>

      <CopilotSidebar
        instructions="You are an AI assistant powered by Google ADK on a Java backend. Help the user with their questions."
        labels={{
          title: "ADK-Java Assistant",
          initial: "Hi! I'm running on a Google ADK-Java backend. How can I help?",
        }}
      />
    </main>
  );
}
