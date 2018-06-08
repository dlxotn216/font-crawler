package io.crscube.logic;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Created by Lee Tae Su on 2018-06-08.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class FontCrawlingServiceTest {
	@Autowired
	private FontCrawlingService fontCrawlingService;
	
	@Test
	public void GoogleEarlyAccessCrawlingTest(){
		fontCrawlingService.crawling(Arrays.asList("https://fonts.googleapis.com/earlyaccess/notosanskr.css",
				"https://fonts.googleapis.com/earlyaccess/notosansjp.css",
				"http://fonts.googleapis.com/earlyaccess/notosanstc.css"));
	}
}