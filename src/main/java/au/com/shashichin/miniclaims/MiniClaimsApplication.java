package au.com.shashichin.miniclaims;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MiniClaimsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniClaimsApplication.class, args);
    }

    @Bean
    OpenAPI miniClaimsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("mini-claims")
                        .version("0.1.0")
                        .description(
                                "Week 1 mini-claims API shaped like Guidewire ClaimCenter "
                                        + "Cloud API claims intake. Not ClaimCenter."));
    }
}
