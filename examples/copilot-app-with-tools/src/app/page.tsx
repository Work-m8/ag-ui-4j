"use client" // only necessary if you are using Next.js with the App Router.
import { CopilotSidebar, Markdown } from "@copilotkit/react-ui";
import { useCopilotAction } from "@copilotkit/react-core"

export default function Page() {
     useCopilotAction({
        name: "writeEssay",
        available: "remote",
        description: "Writes an essay and takes the draft as an argument.",
        parameters: [
          { name: "draft", type: "string", description: "The draft of the essay", required: true },
        ],
        renderAndWaitForResponse: ({ args, respond, status }) => {
          return (
            <div>
              <Markdown content={args.draft || 'Preparing your draft...'} />

              <div className={`flex gap-4 pt-4 ${status !== "executing" ? "hidden" : ""}`}>
                <button
                  onClick={() => respond?.("CANCEL")}
                  disabled={status !== "executing"}
                  className="border p-2 rounded-xl w-full"
                >
                  Try Again
                </button>
                <button
                  onClick={() => respond?.("SEND")}
                  disabled={status !== "executing"}
                  className="bg-blue-500 text-white p-2 rounded-xl w-full"
                >
                  Approve Draft
                </button>
              </div>
            </div>
          );
        },
      });
  useCopilotAction({
    name: "sayHello",
    description: "Say hello to the user",
    parameters: [
      {
        name: "name",
        type: "string",
        description: "The name of the user to say hello to",
        required: true,
      },
    ],
    handler: async ({ name }) => {
      alert(`Hello, ${name}!`);
    },
  });

  return (
    <main>
      <h1>Your App</h1>
      <CopilotSidebar />
    </main>
  );
}