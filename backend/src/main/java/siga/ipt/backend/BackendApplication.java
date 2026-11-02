package siga.ipt.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		var context = SpringApplication.run(BackendApplication.class, args);
		// Compose runs one instance to update the schema before starting replicas.
		// Keep a web context (on an ephemeral port) so normal security beans load.
		if (context.getEnvironment().getProperty("app.initialize-schema", Boolean.class, false)) {
			context.close();
		}
	}

}
