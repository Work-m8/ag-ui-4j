package io.workm8.agui4j.example;

import io.workm8.agui4j.core.agent.RunAgentInput;
import io.workm8.agui4j.core.agent.RunAgentParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
public class AgUiController {

    private final AgUiService agUiService;

    @Autowired
    public AgUiController(
        final AgUiService agUiService
    ) {
        this.agUiService = agUiService;
    }

    @PostMapping(value = "/sse/{agentId}")
    public ResponseEntity<SseEmitter> streamData(@PathVariable("agentId") final String agentId, @RequestBody() final AgUiParameters agUiParameters) {
        var parameters = RunAgentParameters.builder()
            .runId(agUiParameters.getRunId())
            .tools(agUiParameters.getTools())
            .context(agUiParameters.getContext())
            .forwardedProps(agUiParameters.getForwardedProps())
            .build();

        var input = new RunAgentInput(
            agUiParameters.getThreadId(),
            agUiParameters.getRunId(),
            agUiParameters.getState(),
            agUiParameters.getMessages(),
            agUiParameters.getTools(),
            agUiParameters.getContext(),
            agUiParameters.getForwardedProps()
        );

        SseEmitter emitter = this.agUiService.streamEvents(parameters, input);

        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noCache())
            .body(emitter);
    }

}
