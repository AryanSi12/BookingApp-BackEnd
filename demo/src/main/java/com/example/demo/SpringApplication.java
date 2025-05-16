package com.example.demo;

import io.github.cdimascio.dotenv.Dotenv;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class SpringApplication {

	public static void main(String[] args) {
		String redisUrl = System.getenv("REDIS_URL");
		RedisURI redisURI = RedisURI.create(redisUrl);
		RedisClient client = RedisClient.create(redisURI);

		try (var connection = client.connect()) {
			RedisCommands<String, String> commands = connection.sync();
			commands.set("foo", "bar");
			String value = commands.get("foo");
			System.out.println("foo = " + value);
		}

		client.shutdown();
		org.springframework.boot.SpringApplication.run(SpringApplication.class, args);
	}

}
