package fr.fxjavadevblog.suc;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static picocli.CommandLine.Command;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.json.JSONObject;
import org.quartz.CronExpression;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.yaml.snakeyaml.Yaml;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

@SuppressWarnings("unused")
@TopCommand
@Command(mixinStandardHelpOptions = true, version = "1.0.0")

@Slf4j
public class ApplicationCommand implements Runnable {
	
	
    Scheduler scheduler; 
	

	@Option(names = { "-c", "--check" }, paramLabel = "CHECKS FILE", description = "Checks file", required = true)
	File checksFile;

	private Map<String, Object> checks;

	@Override
	public void run() 
	{	
		 SchedulerFactory schedFact = new org.quartz.impl.StdSchedulerFactory();
		 try {
			scheduler = schedFact.getScheduler();
			log.info("Scheduler [OK]");
		} catch (SchedulerException e) {
            log.error("Scheduler not started:  {}", e.getMessage());
		}
				 
		try 
		{
			checks = new Yaml().load(new FileInputStream(checksFile));
			checks.forEach(this::check);
		} catch (FileNotFoundException e) 
		{
			log.error(e.getMessage());
		}	
	}

	private void check(String checkKey, Object checkConfiguration) 
	{
		if (!(checkConfiguration instanceof Map)) throw new RuntimeException("Bad configuration");
		
		@SuppressWarnings("unchecked")
		Map <String, Object> configuration = (Map<String, Object>) checkConfiguration;
		
		CronExpression cronExpression;
		try {
			cronExpression = new CronExpression(configuration.get("cron-expression").toString());
			String cronAsText = this.translateCron(cronExpression);
			
			CheckRule rule = CheckRule.builder()
					.name(checkKey)
					.cronExpression(cronExpression)
					.method("GET")
					.acceptedCode(Integer.parseInt(configuration.get("accepted-code").toString()))
					.timeOutInSeconds(Integer.parseInt(configuration.get("timeout").toString()))
					.url(configuration.get("url").toString())
					.build();

			rule.run();
			
												
			
			
		} catch (ParseException e) {
			log.error("Cron Expression Error for {}" , checkKey);
			log.error("Expression error : {}", e.getMessage());
		}
	
		
	}

	private String translateCron(CronExpression cronExpression) {
		CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
		CronParser parser = new CronParser(cronDefinition);
		CronDescriptor descriptor = CronDescriptor.instance(Locale.UK);
		return descriptor.describe(parser.parse(cronExpression.getCronExpression()));
	}	
}
