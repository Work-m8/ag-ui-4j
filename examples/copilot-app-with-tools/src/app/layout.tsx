import { CopilotKit } from "@copilotkit/react-core";
import "@copilotkit/react-ui/styles.css";


export default function RootLayout({ children }: {children: React.ReactNode}) {
  return (
    <html lang="en">
      <body>
        <CopilotKit
          runtimeUrl="/api/copilotkit"
          agent="agent"
          publicApiKey="ck_pub_f91794d9c3fac9108e0050b5976b5a35"
        >
          {children}
        </CopilotKit>
      </body>
    </html>
  );
}