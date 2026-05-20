package org.example.memory;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "agent.memory")
public class AgentMemoryProperties {

    private String path = "data/agent-memory/memory-store.json";
}
