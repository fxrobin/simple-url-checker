package fr.fxjavadevblog.suc;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;

import lombok.Builder;
import lombok.Getter;


@Builder
@Getter
public class ScopedCredentials {
	
	private AuthScope authScope;

	private Credentials credentials;

}
