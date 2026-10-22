package app.api;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiApplication {

	public static void main(String[] args) {
		// PostgreSQL installations may omit the legacy Asia/Saigon timezone alias.
		// pgJDBC sends the JVM default timezone during connection startup.
		if ("Asia/Saigon".equals(TimeZone.getDefault().getID())) {
			TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
		}
		SpringApplication.run(ApiApplication.class, args);
	}

}
