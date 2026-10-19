
package com.kalaazu;

import com.kalaazu.event.StartServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
    basePackages = "com.kalaazu",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = {
            "com\\.kalaazu\\.ui\\..*",
            "com\\.kalaazu\\.Launcher"
        }
    )
)
public class HeadlessLauncher {

    public static void main(String[] args) {
        ConfigurableApplicationContext context =
            SpringApplication.run(
                HeadlessLauncher.class, args
            );

        KalaazuConfig config =
            context.getBean(KalaazuConfig.class);

        if (config.isAutoStart()) {
            context.publishEvent(new StartServer());
        }
    }
}
