package fr.fxjavadevblog.suc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.TriggerBuilder;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.mailer.MailerBuilder;
import org.simplejavamail.MailException;
import org.yaml.snakeyaml.Yaml;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;


@SuppressWarnings("unused")
@TopCommand
@Command(mixinStandardHelpOptions = true, version = "1.0.0")

@Slf4j
public class ApplicationCommand implements Runnable {

	final static Map <String, Statistics> statistics = new HashMap<>();
	
	private Scheduler scheduler;
	
	public static Mailer mailer;

	@Option(names = { "-c", "--check" }, 
			paramLabel = "CHECKS FILE",
			description = "Checks file", 
			required = true)
	private File checksFile;
	
	@Option(names = { "-s", "--smtp-address" }, 
			paramLabel = "ADDRESS",
			description = "SMTP Server Address. Default: localhost")
	private String smtpServer;
	
	
	@Option(names = { "-p", "--smtp-port" }, 
			paramLabel = "PORT",
			description = "SMTP Server port. Default: 25")
	private int smtpPort = 25;
	
	
	@Option(names = { "-t", "--smtp-transport-strategy" }, 
			paramLabel = "STRATEGY",
			description = "Connection Strategy for SMTP. Default: SMTP")
	private TransportStrategy transportStrategy =  TransportStrategy.SMTP;
	
	
	@Option(names = { "-l", "--smtp-login" }, 
			paramLabel = "LOGIN",
			description = "Login to connect to the SMTP server.")
	private String login;
	
	@Option(names = { "-d", "--smtp-password" }, 
			paramLabel = "PASSWORD",
			description = "Password to connect to the SMTP server.")
	private String password;
	
	@Option(names = { "-f", "--smtp-from" }, 
			paramLabel = "FROM",
			description = "<From> address for generated emails.")
	private String from;
	
	private Map<String, Object> checks;

	@Override
	public void run() {
		
		try 
		{			
			initScheduler();		
			initMailer();		
			initRules();
			startScheduler();
			waitKeyPressed();
			shutdownScheduler();				
			displayStastistics();

		} catch (SchedulerException e) {
			log.error("Scheduler not started:  {}", e.getMessage());
		} catch (IOException e) {
			log.error(e.getMessage());
		}
		
	}
	

	private void initScheduler() throws SchedulerException {
		SchedulerFactory schedFact = new org.quartz.impl.StdSchedulerFactory();
		scheduler = schedFact.getScheduler();
		log.info("Scheduler [READY]");
	}
	
	private void startScheduler() throws SchedulerException {
		scheduler.start();
		log.info("Scheduler [ON]");		
	}

	private void shutdownScheduler() throws SchedulerException {
		scheduler.shutdown(true);	
		log.info("Scheduler [OFF]");
	}
	

	private void initMailer() {
		mailer = MailerBuilder
		          .withSMTPServer(smtpServer, smtpPort, login, password)		
		          .withTransportStrategy(transportStrategy)			          
		          .buildMailer();
		try {
			mailer.testConnection();		
			log.info("SMTP Mailer {}:{} [READY]", mailer.getServerConfig().getHost(), 
					                              mailer.getServerConfig().getPort());
		}
		catch (RuntimeException ex)
		{
			log.error("Cannot connect to mail server");
			mailer = null;
		}
	}

	private void waitKeyPressed() throws IOException {
		log.info("PRESS <ENTER> TO STOP THE SCHEDULER");
		System.in.read();
		log.info("<ENTER> has been pressed. Shutdown requested.");
	}

	
	private void initRules() throws FileNotFoundException {
		checks = new Yaml().load(new FileInputStream(checksFile));
		checks.forEach(this::scheduleJob);
		log.info("Rules [LOADED]");
	}


	private static void displayStastistics() {
		log.info("Statistics :");	
		statistics.forEach((k, v) -> {
			
			double successRatio = (double) v.getSuccessCounter() / v.getRequestCounter() * 100;
			log.info("- {} : {}/{} {}%", k, v.getSuccessCounter(), v.getRequestCounter(), successRatio);
			
		});
	}

	private void scheduleJob(String checkKey, Object checkConfiguration) {

		if (!(checkConfiguration instanceof Map))
			throw new RuntimeException("Bad configuration");

		@SuppressWarnings("unchecked")
		Map<String, Object> configuration = (Map<String, Object>) checkConfiguration;
		
		configuration.put("configuration.from", this.from);

		try {
			CronExpression cronExpression = new CronExpression((String) configuration.get("cron-expression"));

			// Parse some expression and ask descriptor for description
			String cronDescription = this.translateCron(cronExpression);

			JobDetail jobDetail = JobBuilder
					.newJob(CheckRule.class)
					.setJobData(new JobDataMap(configuration))
					.withIdentity(checkKey)
					.build();

			CronTrigger trigger = TriggerBuilder
					.newTrigger()
					.withIdentity("trigger-" + checkKey)					
					.withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
					.forJob(jobDetail)
					.startNow()
					.build();

			scheduler.scheduleJob(jobDetail, trigger);

			log.info("Job and Trigger added to scheduler : {} / {}", jobDetail.getKey(), cronDescription);

		} catch (ParseException | SchedulerException e) {
			log.error(e.getMessage());
		}

	}

	private String translateCron(CronExpression cronExpression) {
		CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
		CronParser parser = new CronParser(cronDefinition);
		CronDescriptor descriptor = CronDescriptor.instance(Locale.UK);
		return descriptor.describe(parser.parse(cronExpression.getCronExpression()));
	}
}
