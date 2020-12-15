package fr.fxjavadevblog.suc;

import java.io.IOException;
import java.util.List;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.util.Timeout;
import org.quartz.CronExpression;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class CheckRule implements Runnable {

	String name;
	CronExpression cronExpression;
	String url;
	String method;
	int acceptedCode;
	int timeOutInSeconds;

	public void run() {
		HttpResponse response;
		try {
			response = Request.get(url).connectTimeout(Timeout.ofSeconds(timeOutInSeconds)).execute().returnResponse();
			
			String test = (acceptedCode == response.getCode()) ? "OK :-)" : "KO :-(";

			if (acceptedCode == response.getCode()) {
				log.info("{} {} : {} {} = {} ", test, name, method, url, response.getCode());
			} else {
				log.error("{} {} : {} {} = {} ", test, name, method, url, response.getCode());
			}

		} catch (IOException e) {
			log.error(e.getMessage());
		}

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
