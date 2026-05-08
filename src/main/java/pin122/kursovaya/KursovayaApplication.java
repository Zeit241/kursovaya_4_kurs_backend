package pin122.kursovaya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа Spring Boot-приложения клиники: веб-API, планировщик задач и асинхронное выполнение.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class KursovayaApplication {

	/**
	 * Запускает встроенный контейнер и контекст приложения.
	 *
	 * @param args аргументы командной строки, передаваемые в Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(KursovayaApplication.class, args);
	}

}
