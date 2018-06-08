package io.crscube.logic;

import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.buf.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Lee Tae Su
 * @version 1.0
 * @project font.crawler
 * @since 2018-06-08
 * <p>
 * Google의 Early access로 부터 폰트파일을 크롤링한다
 * <p>
 * 단, Early access 내에 선언 된 형식은 아래와 같아야 하며
 * font의 url은 유효한 주소이어야 한다
 * <p>
 * font-face {
 * 		font-family: 'Noto Sans KR';
 * 		font-style: normal;
 * 		font-weight: 100;
 * 		src: local('Noto Sans KR Thin'), local('NotoSansKR-Thin'), url(https://fonts.gstatic.com/s/notosanskr/v5/Pby6FmXiEBPT4ITbgNA5CgmOsk7lyJcsuA.woff2) format('woff2');
 * 		unicode-range: U+3131-318E, U+3200-321C, U+3260-327B, U+AC00-D7AF;
 * }
 * font-face {
 * 		font-family: 'Noto Sans KR';
 * 		font-style: normal;
 * 		font-weight: 100;
 * 		src: local('Noto Sans KR Thin'), local('NotoSansKR-Thin'), url(https://fonts.gstatic.com/s/notosanskr/v5/Pby6FmXiEBPT4ITbgNA5CgmOsk7vyJc.woff2) format('woff2');
 		* unicode-range: U+0000-00FF, U+0131, U+0152-0153, U+02BB-02BC, U+02C6, U+02DA, U+02DC, U+2000-206F, U+2074, U+20AC, U+2122, U+2191, U+2193, U+2212, U+2215, U+FEFF, U+FFFD;
 * }
 * ........
 */
@Slf4j
@Service
public class FontCrawlingService {
	
	@Value("${app.thread_pool_size}")
	private int threadPoolSize;
	
	@Value("${app.default_save_root_path}")
	private String saveRootPath;
	
	private ExecutorService executor;
	
	private RestTemplate restTemplate;

	@PostConstruct
	public void init() {
		executor = Executors.newFixedThreadPool(threadPoolSize);
		
		restTemplate = new RestTemplate();
		restTemplate.getMessageConverters().add(new ByteArrayHttpMessageConverter());
	}
	
	/**
	 * <code>targetUrls</code>로 부터 크롤링을 진행한다
	 * <p>
	 * targetUrls의 요소는 font-face가 선언 된 css파일이어야 하며
	 * HttpMethod.GET을 통해 다운로드 받을 수 있는 유효한 주소이어야 한다
	 *
	 * @param targetUrls 크롤링을 수행 할 url 목록
	 */
	public void crawling(List<String> targetUrls) {
		List<CompletableFuture> futures = targetUrls.stream()
				.map(this::getDocumentFromUrl)
				.filter(Optional::isPresent)
				.map(document -> document.get().text())
				.flatMap(document -> Stream.of(document.split("url")))
				.map(mapped -> mapped.split("\\(")[1])                        //(http://fonts.gstatic.com....)에서 '(' 제거
				.map(mapped -> mapped.split("\\)")[0])                        // http://fonts.gstatic.com....)에서 ')' 제거
				.filter(this::isValidFontUrl)
				.map(url ->
						CompletableFuture.supplyAsync(() -> findFont(url), executor)
								.thenAccept((res) -> saveFont(url, res))
				).collect(Collectors.toList());
		
		futures.forEach(CompletableFuture::join);
		log.info("====================finished==========================");
	}
	
	/**
	 * jsoup을 통해 <code>url</code>로 부터 문서를 조회한다
	 * <p>
	 * Jsoup에서 connect 메소드 호출 시 발생할 수 있는 IOException을
	 * Wrapping 하여 크롤링이 중단되지 않도록 한다
	 *
	 * @param url 문서 URL
	 * @return Optional of Document
	 */
	private Optional<Document> getDocumentFromUrl(String url) {
		try {
			return Optional.ofNullable(Jsoup.connect(url).get());
		} catch (IOException e) {
			return Optional.empty();
		}
	}
	
	/**
	 * split 하여 파싱한 결과<code>parsed</code>에서 유효한 Font url인지 조사한다
	 * Early access에 존재하는 font의 url 형식은 아래 유형 중 하나이다
	 * <p>
	 * https://xxx
	 * //fonts.xxx
	 *
	 * @param parsed 파싱 된 결과
	 * @return boolean
	 */
	private boolean isValidFontUrl(String parsed) {
		return parsed.contains("https://") || parsed.contains("//fonts.");
	}
	
	/**
	 * <code>url</code>로 부터 font 파일을 다운로드 받는다
	 *
	 * @param url font url
	 * @return ResponseEntity
	 */
	private ResponseEntity<byte[]> findFont(String url) {
		if(!url.startsWith("https"))
			url = "https:" + url;
		
		log.info("find font from -> " + url);
		return restTemplate.getForEntity(url, byte[].class);
	}
	
	/**
	 * <code>url</code>로부터 다운받은 font파일<code>res</code>를 지정된 위치에 저장한다
	 *
	 * @param url 다운로드 한 url로 파일 이름 추출 시 사용된다.
	 * @param res 다운로드 받은 Response
	 */
	private void saveFont(String url, ResponseEntity<byte[]> res) {
		log.info("save file call -> " + url);
		
		String directoryPath = getDirectoryPath(url);
		if(!Files.exists(Paths.get(directoryPath))) {
			log.info("mrdirs for path [" + directoryPath + "] result -> " + new File(directoryPath).mkdirs());
		}
		try {
			Files.write(Paths.get(directoryPath + "/" + getFilName(url)), res.getBody());
		} catch (IOException e) {
			log.error("파일 저장 중 Exception [" + e.getMessage() + "]", e);
		}
	}
	
	/**
	 * url로 부터 font 파일이 저장 될 디렉토리 경로를 반환한다
	 * 
	 * 만약 url이 아래와 같다면
	 * https://fonts.gstatic.com/s/notosansjp/v14/-F62fjtqLzI2JPCgQBnw7HFYwQgP-FVthw.woff2
	 * 
	 * 아래 String이 반환된다
	 * <code>saveRootPath</code>/s/notosansjp/v14
	 * 
	 * @param url 다운로드 한 url
	 * @return 실제 저장 될 디렉토리 경로
	 */
	private String getDirectoryPath(String url) {
		String fontURI = url.split(".com")[1];    //fontURI maybe /s/notosansjp/v14/-F62fjtqLzI2JPCgQBnw7HFYwQgP-FVthw.woff2
		String[] splits = fontURI.split("/");
		
		return saveRootPath + "/" + StringUtils.join(Arrays.asList(splits).subList(0, splits.length - 1), '/');
	}
	
	/**
	 * <code>url</code>로 부터 파일 명을 추출한다
	 * <p>
	 * 아래와 같은 url인 경우 [filename.woff2]가 추출된다.
	 * https://test.abc.com/parent/child/filename.woff2
	 * <p>
	 * 만약 추출에 실패한 경우 "default"로 시장하는 랜덤 문자열을 파일명으로 반환한다
	 *
	 * @param url 파일명을 추출할 url
	 * @return 추출 된 파일 명
	 */
	private String getFilName(String url) {
		String[] splits = url.split("/");
		if(splits.length > 0)
			return splits[splits.length - 1];
		else
			return "default" + url.substring(1, 10);
	}
	
}
