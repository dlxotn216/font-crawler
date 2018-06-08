package io.crscube.runner;

import io.crscube.logic.FontCrawlingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * @author Lee Tae Su
 * @version 1.0
 * @project font.crawler
 * @since 2018-06-08
 */
@Component
public class CrawlerApplicationRunner implements ApplicationRunner {
	@Autowired
	private FontCrawlingService fontCrawlingService;
	
	@Override
	public void run(ApplicationArguments applicationArguments) throws Exception {
		fontCrawlingService.crawling(Arrays.asList(
				"http://localhost:8080/fonts/meta/notosanskr.css",
					"http://localhost:8080/fonts/meta/notosansjp.css",
					"http://localhost:8080/fonts/meta/notosanstc.css"));
	}
}
