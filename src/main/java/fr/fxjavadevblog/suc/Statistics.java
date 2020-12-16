package fr.fxjavadevblog.suc;

import lombok.Getter;

class Statistics
{	
	@Getter
	private long requestCounter = 0L;
	
	@Getter
	private long successCounter = 0L;
	
	void incrementSuccess() 
	{
		requestCounter++;
		successCounter++;
	}
	
	void incrementError()
	{
		requestCounter++;
	}		
}