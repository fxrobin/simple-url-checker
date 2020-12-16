package fr.fxjavadevblog.suc;

import java.io.File;
import java.io.FileInputStream;
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
import org.simplejavamail.mailer.MailerBuilder;
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
	
	
	@Option(names = { "-starttls", "--use-starttls" }, 
			paramLabel = "STARTTTLS",
			description = "use STARTTLS. Default: true")
	private boolean useStartTls = true;
	
	
	@Option(names = { "-login", "--smtp-login" }, 
			paramLabel = "SMTP_LOGIN",
			description = "Login to connect to the SMTP server.")
	private String login;
	
	@Option(names = { "-password", "--smtp-password" }, 
			paramLabel = "SMTP_PASSWORD",
			description = "Password to connect to the SMTP server.")
	private String password;
	

	private Map<String, Object> checks;

	@Override
	public void run() {
		SchedulerFactory schedFact = new org.quartz.impl.StdSchedulerFactory();
		try {
			scheduler = schedFact.getScheduler();
			log.info("Scheduler [READY]");
			
			
			mailer = MailerBuilder
			          .withSMTPServer(smtpServer, smtpPort)			          			        
			          .buildMailer();			
			mailer.testConnection();		
			log.info("SMTP Mailer {}:{} [READY]", mailer.getServerConfig().getHost(), mailer.getServerConfig().getPort());
			

			checks = new Yaml().load(new FileInputStream(checksFile));
			checks.forEach(this::check);
			log.info("Rules [LOADED]");

			scheduler.start();
			log.info("Scheduler [ON]");
			log.info("PRESS <ENTER> TO STOP THE SCHEDULER");

			System.in.read();

			scheduler.shutdown(true);

			log.info("<ENTER> has been pressed. Shutdown requested.");
			log.info("Scheduler [OFF]");
				
			displayStastistics();
			
			log.info("Bye.");

		} catch (SchedulerException e) {
			log.error("Scheduler not started:  {}", e.getMessage());
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private static void displayStastistics() {
		
		log.info("Statistics :");
		
		statistics.forEach((k, v) -> {
			
			double successRatio = (double) v.getSuccessCounter() / v.getRequestCounter() * 100;
			log.info("- {} : {}/{} {}%", k, v.getSuccessCounter(), v.getRequestCounter(), successRatio);
			
		});

		
	}

	private void check(String checkKey, Object checkConfiguration) {

		if (!(checkConfiguration instanceof Map))
			throw new RuntimeException("Bad configuration");

		@SuppressWarnings("unchecked")
		Map<String, Object> configuration = (Map<String, Object>) checkConfiguration;

		CronExpression cronExpression;
		try {
			cronExpression = new CronExpression((String) configuration.get("cron-expression"));

			// Parse some expression and ask descriptor for description
			String description = CronDescriptor.instance(Locale.UK)
					.describe(new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ))
							.parse(cronExpression.getCronExpression()));

			JobDetail jobDetail = JobBuilder.newJob(CheckRule.class).setJobData(new JobDataMap(configuration))
					.withIdentity(checkKey).build();

			CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity("trigger-" + checkKey).startNow()
					.withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)).forJob(jobDetail).build();

			scheduler.scheduleJob(jobDetail, trigger);

			log.info("Job and Trigger added to scheduler : {} / {}", jobDetail.getKey(), description);

		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SchedulerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private String translateCron(CronExpression cronExpression) {
		CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
		CronParser parser = new CronParser(cronDefinition);
		CronDescriptor descriptor = CronDescriptor.instance(Locale.UK);
		return descriptor.describe(parser.parse(cronExpression.getCronExpression()));
	}
}
