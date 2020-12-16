package fr.fxjavadevblog.suc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.util.Timeout;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.email.EmailBuilder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("unused")
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckRule implements Job {

	private String name;

	private String url;

	private String method;

	private String proxyUrl;

	private int acceptedCode;

	private int timeOutInSeconds;

	private String email;

	@Builder.Default
	private Map<String, String> headers = new HashMap<>();

	@Getter
	private Statistics statistics;

	public void run() {
		try {
			Request request = this.prepareRequest();
			HttpResponse response = request.execute().returnResponse();
			this.analyseResponse(response);
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private void analyseResponse(HttpResponse response) {
		if (acceptedCode == response.getCode()) {
			statistics.incrementSuccess();
			log.info("{} {} : {} {}", response.getCode(), name, method, url);
		} else {
			statistics.incrementError();
			this.sendNotification();
			log.error("{} {} : {} {}", response.getCode(), name, method, url);
		}
	}

	private void sendNotification() {
		
		log.info("Sending notification to {}", email);
		
		if (StringUtils.isNotBlank(email)) {

			
			Email msg = EmailBuilder.startingBlank()
				    .to("fxrobin@zaclys.net")
				    .from("robin@localhost")
				    .withSubject("hey")
				    .withPlainText("We should meet up! ;)")
				    .buildEmail();				
			
			ApplicationCommand.mailer.sendMail(msg);
		}

	}

	private Request prepareRequest() {
		Request request;

		method = (method == null) ? "GET" : method;

		if ("HEAD".equalsIgnoreCase(method))
			request = Request.head(url);
		else
			request = Request.get(url);

		request.connectTimeout(Timeout.ofSeconds(timeOutInSeconds));

		if (headers != null)
			headers.forEach(request::addHeader);

		if (StringUtils.isNotEmpty(proxyUrl))
			request.viaProxy(proxyUrl);
		return request;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {

		// let's convert the dataMap into instance fields.

		JobDataMap dataMap = context.getJobDetail().getJobDataMap();
		String currentName = context.getJobDetail().getKey().getName();

		Statistics stats = this.setUpStatistics(currentName);

		CheckRule.builder().name(currentName).statistics(stats).method(dataMap.getString("method"))
				.acceptedCode(dataMap.getInt("accepted-code")).timeOutInSeconds(dataMap.getInt("timeout"))
				.url(dataMap.getString("url")).headers((Map<String, String>) dataMap.get("headers"))
				.proxyUrl(dataMap.getString("proxy-url")).email(dataMap.getString("email")).build().run(); // now that's
																											// it's
																											// converted,
																											// let's run
																											// it.
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

}

//cron-expression: "* */1 * * * ? *"
//url: https://www.google.com
//method: HEAD
//follow-redirect: true
//accepted-codes:
//   -200
//   -202
//   -304
//mail-recipient: fxrobin@zaclys.net
