"use client" // only necessary if you are using Next.js with the App Router.
import { CopilotSidebar, Markdown } from "@copilotkit/react-ui";
import { useCopilotAction } from "@copilotkit/react-core"

export default function Page() {
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