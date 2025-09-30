package com.agui;

import com.agui.core.agent.AgentSubscriber;
import com.agui.core.agent.AgentSubscriberParams;
import com.agui.core.agent.RunAgentParameters;
import com.agui.core.message.BaseMessage;
import com.agui.core.message.UserMessage;
import com.agui.http.HttpAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.command.annotation.Command;

import java.util.Scanner;
import java.util.concurrent.CountDownLatch;


@Command(group = "Chat")
public class ChatCommand {

    @Autowired
    private HttpAgent agent;


    @Command(command = "chat with agent", description = "Chat with Spring AI Agent")
    public void chat() {
        System.out.println("Entering chat mode. Type 'exit' to quit.");
        Scanner scanner = new Scanner(System.in);
        String input;

        while (true) {
            System.out.print("You: ");
            input = scanner.nextLine();

            if ("exit".equalsIgnoreCase(input.trim())) {
                System.out.println("Exiting chat...");
                break;
            }

            processChatMessage(input);
        }
    }

    private void processChatMessage(String message) {
        var userMessage = new UserMessage();
        userMessage.setContent(message);
        this.agent.addMessage(userMessage);

        // Create loading animation
        LoadingAnimation loadingAnimation = new LoadingAnimation("Agent is thinking", LoadingAnimation.AnimationType.SPINNER, 150);

        // Use CountDownLatch to wait for completion
        CountDownLatch latch = new CountDownLatch(1);

        // Start loading animation
        loadingAnimation.start();

        this.agent.runAgent(RunAgentParameters.builder()
                .messages(this.agent.getMessages())
                .build(), new AgentSubscriber() {
            @Override
            public void onNewMessage(BaseMessage systemMessage) {
                loadingAnimation.stop();
                System.out.println("Agent: " + systemMessage.getContent());
                latch.countDown();
            }
            @Override
            public void onRunFailed(AgentSubscriberParams params, Throwable error) {
                loadingAnimation.stop();
                System.err.println("Error: " + error.getMessage());
                latch.countDown();
            }

            @Override
            public void onRunFinalized(AgentSubscriberParams params) {
                loadingAnimation.stop();
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            loadingAnimation.stop();
            System.err.println("Processing interrupted");
        }
    }
}
