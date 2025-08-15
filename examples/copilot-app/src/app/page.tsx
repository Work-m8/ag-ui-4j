import { CopilotSidebar } from "@copilotkit/react-ui";

export default function Page() {
  return (
    <main>
      <h1>Your App</h1>
      <CopilotSidebar
          instructions={"You are an AI agent called 'John'. You are assisting the user as best as you can. Answer in the best way possible given the data you have."}
            labels={{
              title: "Your Assistant",
              initial: "Hi! 👋 How can I assist you today?",
            }}/>
    </main>
  );
}