package fr.fxjavadevblog.suc;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.util.Timeout;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.email.EmailBuilder;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("unused")
@Slf4j
@NoArgsConstructor
public class CheckRule implements Job {

	private String name;

	private String url;

	private String method;

	private String proxyUrl;

	private int acceptedCode;

	private int timeOutInSeconds;

	private String email;

	private String from;

	private ScopedCredentials scopedCredentials;

	private Map<String, String> headers = new HashMap<>();

	@Getter
	private Statistics statistics;
	
	
	/**
	 * start endpoint for Quartz.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {

		// let's convert the dataMap into instance fields.

		JobDataMap dataMap = context.getJobDetail().getJobDataMap();
		String currentName = context.getJobDetail().getKey().getName();

		this.name = currentName;
		this.statistics = this.setUpStatistics(currentName);
		this.method = dataMap.getString("method");
		this.acceptedCode = dataMap.getInt("accepted-code");
		this.timeOutInSeconds = dataMap.getInt("timeout");
		this.from = dataMap.getString("configuration.from");
		this.url = dataMap.getString("url");
		this.proxyUrl = dataMap.getString("proxy-url");
		this.headers = (Map<String, String>) dataMap.get("headers");
		this.email = dataMap.getString("email");
		this.scopedCredentials = this.extractCredentials(dataMap);

		this.run();
	}
	
	/**
	 * extract Credentials from the configuration and build a ScopedCredential object. 
	 * 
	 * @param dataMap
	 * @return
	 */
	private ScopedCredentials extractCredentials(JobDataMap dataMap) {

		String login = dataMap.getString("login");
		String password = dataMap.getString("password");

		if (StringUtils.isNotEmpty(login) && StringUtils.isNotEmpty(password)) {
			password = password.startsWith("$") ? System.getenv(password) : password;

			try {
				HttpHost host = HttpHost.create(this.url);
				UsernamePasswordCredentials creds = new UsernamePasswordCredentials(login, password.toCharArray());
				return ScopedCredentials.builder()
										.authScope(new AuthScope(host))
										.credentials(creds).build();
			} catch (URISyntaxException e) {
				log.error("URL is not correct to build credentials for. URL : {}", this.url);
			}
		}
		return null;
	}
	

	private Statistics setUpStatistics(String currentName) {
		// let's create or get statistics for the job.
		Statistics stats = ApplicationCommand.statistics.get(currentName);

		if (stats == null) {
			stats = new Statistics();
			ApplicationCommand.statistics.put(currentName, stats);
		}
		return stats;
	}
	
    /**
     * real execution of the job
     */
	public void run() {

		try {

			Request request = this.prepareRequest();
			CloseableHttpClient client = SslCertificateCarelessHttpClientBuilder.build();
			Executor executor = Executor.newInstance(client);
			this.addCredentials(executor);
			HttpResponse response = executor.execute(request).returnResponse();
			this.analyseResponse(response);
			client.close();

		} catch (IOException | URISyntaxException e) {
			log.error(e.getMessage());
		}
	}
	
	private Request prepareRequest() throws URISyntaxException {
		Request request = Request.create(Method.valueOf(method), URI.create(url));
		request.connectTimeout(Timeout.ofSeconds(timeOutInSeconds));
		Optional.ofNullable(headers).ifPresent(h -> h.forEach(request::addHeader));
		Optional.ofNullable(proxyUrl).filter(StringUtils::isNotEmpty).ifPresent(request::viaProxy);
		return request;
	}

	private void addCredentials(Executor executor) {
		if (scopedCredentials != null)
			executor.auth(scopedCredentials.getAuthScope(), scopedCredentials.getCredentials());
	}

	private void analyseResponse(HttpResponse response) {
		
		String logMessage = constructLogMessage(response);
		
		if (acceptedCode == response.getCode()) {		
			log.info(logMessage);
			statistics.incrementSuccess();
		} 
		else 
		{
			log.error(logMessage);
			statistics.incrementError();						
			this.sendNotification(logMessage);
		}
	}
	
	private String constructLogMessage(HttpResponse response)
	{
		return String.format("%s %s : %s %s", response.getCode(), name, method, url);
	}

	private void sendNotification(String content) {
		if (ApplicationCommand.mailer != null && StringUtils.isNotBlank(email)) {
			log.info("Sending notification to {}", email);

			Email msg = EmailBuilder.startingBlank().to(this.email).from(this.from)
					.withSubject(String.format("Simple URL Checker: an error has occured (%s)", name))
					.withPlainText(content).buildEmail();
			ApplicationCommand.mailer.sendMail(msg);

		}
	}


}
